package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.BlockReader;
import jdk.incubator.jarz.v2.JarzDataProvider;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

/**
 * Base ClassLoader implementation for JARZ archives.
 * 
 * <p>This ClassLoader provides core functionality for loading classes and resources
 * from JARZ archives. It follows JDK ClassLoader patterns and can be used for
 * library loading scenarios where Main-Class is not required.
 * 
 * <p>For application loading with Main-Class support, use {@link JarzApplicationClassLoader}.
 * 
 * <h2>Thread Safety</h2>
 * <p>This implementation is thread-safe. Multiple threads can safely load classes
 * and resources concurrently.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzClassLoader extends SecureClassLoader implements AutoCloseable {
    
    // Core components - OPTIMIZED: Shared BlockReader via pool
    private final Path jarzFilePath;
    protected final BlockReader blockReader; // Shared via pool, not owned
    protected final Manifest manifest;
    protected final ProtectionDomain protectionDomain;
    protected final URL codeSource;
    
    // Classpath handling
    protected final JarzClasspathResolver classpathResolver;
    
    // Thread-safe caching - OPTIMIZED: Lazy allocation to reduce memory overhead
    private volatile ConcurrentHashMap<String, Class<?>> classCache;
    
    // Thread-local StringBuilder for efficient string operations
    private static final ThreadLocal<StringBuilder> STRING_BUILDER = 
        ThreadLocal.withInitial(() -> new StringBuilder(256));
    
    // State management
    private volatile boolean closed = false;
    
    /**
     * Converts JVM binary name to JARZ index format.
     * 
     * @param binaryName the binary class name (e.g., "com.example.MyClass")
     * @param sb StringBuilder to write result into
     */
    protected static void toIndexFormat(String binaryName, StringBuilder sb) {
        sb.setLength(0);
        for (int i = 0; i < binaryName.length(); i++) {
            char c = binaryName.charAt(i);
            sb.append(c == '.' ? '/' : c);
        }
        sb.append(".class");
    }
    
    /**
     * Converts resource name to JARZ index format.
     * 
     * @param resourceName the resource name (e.g., "META-INF/MANIFEST.MF")
     * @return the resource name (already in correct format)
     */
    protected static String toResourceFormat(String resourceName) {
        return resourceName; // Resources are already in correct format
    }
    
    /**
     * Normalizes a class name from internal format to external format.
     * 
     * @param indexKey the index key (e.g., "com/example/MyClass.class")
     * @param sb StringBuilder to write result into
     */
    protected static void normalizeClassName(String indexKey, StringBuilder sb) {
        sb.setLength(0);
        int end = indexKey.endsWith(".class") ? indexKey.length() - 6 : indexKey.length();
        for (int i = 0; i < end; i++) {
            char c = indexKey.charAt(i);
            sb.append(c == '/' ? '.' : c);
        }
    }
    private final Object closeLock = new Object();
    
    // OPTIMIZATION: Lazy initialization methods to reduce memory overhead
    private ConcurrentHashMap<String, Class<?>> getClassCache() {
        if (classCache == null) {
            synchronized (this) {
                if (classCache == null) {
                    // Minimal initial capacity to reduce memory footprint
                    classCache = new ConcurrentHashMap<>(4, 0.75f, 1);
                }
            }
        }
        return classCache;
    }
    
    /**
     * Creates a new JarzClassLoader for the specified JARZ archive.
     * 
     * @param jarzFile path to the JARZ archive file
     * @throws IOException if the JARZ file cannot be read
     */
    public JarzClassLoader(Path jarzFile) throws IOException {
        this(jarzFile, getSystemClassLoader());
    }
    
    /**
     * Creates a new JarzClassLoader with custom data provider.
     * 
     * @param dataProvider data provider for JARZ access
     * @throws IOException if the JARZ data cannot be read
     */
    public JarzClassLoader(JarzDataProvider dataProvider) throws IOException {
        this(dataProvider, getSystemClassLoader());
    }
    
    /**
     * Creates a new JarzClassLoader with custom data provider and parent ClassLoader.
     * 
     * @param dataProvider data provider for JARZ access
     * @param parent parent ClassLoader for delegation
     * @throws IOException if the JARZ data cannot be read
     */
    public JarzClassLoader(JarzDataProvider dataProvider, ClassLoader parent) throws IOException {
        super(parent);
        
        if (dataProvider == null) {
            throw new IllegalArgumentException("Data provider cannot be null");
        }
        
        try {
            this.jarzFilePath = null; // No local file path for remote sources
            this.blockReader = BlockReaderPool.acquire(dataProvider);
            this.manifest = null; // No manifest caching for data providers
            
            // For remote sources, create a synthetic URL
            this.codeSource = new java.net.URL("file", null, -1, "remote-jarz");
            this.protectionDomain = ProtectionDomainFactory.getProtectionDomain(codeSource);
            this.classpathResolver = null; // No classpath resolution for remote sources
        } catch (Exception e) {
            try { close(); } catch (IOException ignored) {}
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to initialize JARZ ClassLoader", e);
        }
    }
    
    /**
     * Creates a new JarzClassLoader with the specified parent ClassLoader.
     * 
     * @param jarzFile path to the JARZ archive file
     * @param parent parent ClassLoader for delegation
     * @throws IOException if the JARZ file cannot be read
     */
    public JarzClassLoader(Path jarzFile, ClassLoader parent) throws IOException {
        super(parent);
        
        if (jarzFile == null) {
            throw new IllegalArgumentException("JARZ file cannot be null");
        }
        
        if (!Files.exists(jarzFile)) {
            throw new IOException("JARZ file not found: " + jarzFile);
        }
        
        if (!Files.isReadable(jarzFile)) {
            throw new IOException("Cannot read JARZ file: " + jarzFile);
        }
        
        try {
            this.jarzFilePath = jarzFile;
            this.blockReader = BlockReaderPool.acquire(jarzFile);
            this.manifest = ManifestCache.getManifest(jarzFile, blockReader);
            this.codeSource = jarzFile.toUri().toURL();
            this.protectionDomain = ProtectionDomainFactory.getProtectionDomain(codeSource);
            this.classpathResolver = createClasspathResolver(jarzFile.getParent(), jarzFile);
        } catch (Exception e) {
            try { close(); } catch (IOException ignored) {}
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            throw new IOException("Failed to initialize JARZ ClassLoader", e);
        }
    }
    
    protected JarzClasspathResolver createClasspathResolver(Path baseDir, Path currentJarzFile) throws IOException {
        String classPath = manifest.getMainAttributes().getValue("Class-Path");
        if (classPath == null || classPath.trim().isEmpty()) {
            return null;
        }
        
        List<Path> jarzEntries = new ArrayList<>();
        for (String entry : classPath.trim().split("\\s+")) {
            if (entry.isEmpty()) continue;
            
            Path entryPath = baseDir.resolve(entry);
            if (Files.exists(entryPath) && Files.isReadable(entryPath) && entry.endsWith(".jarz")) {
                jarzEntries.add(entryPath);
            }
        }
        
        return jarzEntries.isEmpty() ? null : new JarzClasspathResolver(jarzEntries, currentJarzFile);
    }
    
    /**
     * Returns the parsed manifest from the JARZ archive.
     * 
     * @return the manifest object, never null
     */
    public Manifest getManifest() {
        return manifest;
    }
    
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (name == null) {
            throw new ClassNotFoundException("null class name");
        }
        if (closed) {
            throw new IllegalStateException("ClassLoader closed");
        }
        return super.loadClass(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        if (closed) {
            throw new IllegalStateException("ClassLoader closed");
        }
        
        if (name == null) {
            throw new ClassNotFoundException("null class name");
        }
        
        // Check cache first
        Class<?> cached = getClassCache().get(name);
        if (cached != null) {
            return cached;
        }
        
        // Convert binary name to index format efficiently
        StringBuilder sb = STRING_BUILDER.get();
        toIndexFormat(name, sb);
        String indexKey = sb.toString();
        
        try {
            // Try main JARZ archive - direct lookup with correct format
            byte[] classData = blockReader.readEntry(indexKey);
            if (classData != null) {
                Class<?> clazz = defineClass(name, classData, 0, classData.length, protectionDomain);
                getClassCache().put(name, clazz);
                return clazz;
            }
            
            // Try classpath JARZ files
            if (classpathResolver != null) {
                try {
                    byte[] classpathData = classpathResolver.findClass(name);
                    if (classpathData != null) {
                        Class<?> clazz = defineClass(name, classpathData, 0, classpathData.length, protectionDomain);
                        getClassCache().put(name, clazz);
                        return clazz;
                    }
                } catch (IOException ignored) {
                    // Not found in classpath
                }
            }
            
        } catch (IOException e) {
            throw new ClassNotFoundException("I/O error loading " + name, e);
        }
        
        throw new ClassNotFoundException(name);
    }
    
    @Override
    protected URL findResource(String name) {
        if (closed || name == null) {
            return null;
        }
        
        try {
            // Check main archive
            if (blockReader.readEntry(name) != null) {
                return new URL("jarz", "", -1, name);
            }
            
            // Check classpath
            if (classpathResolver != null) {
                try {
                    byte[] resourceData = classpathResolver.findResource(name);
                    if (resourceData != null) {
                        // For Phase 3, we'll return a simple jarz URL
                        // Full URL handling can be enhanced in future phases
                        return new URL("jarz", "", -1, "classpath:" + name);
                    }
                } catch (IOException ignored) {
                    // Resource not found in classpath
                }
            }
            
        } catch (Exception ignored) {
            // Resource not found
        }
        
        return null;
    }
    
    @Override
    public void close() throws IOException {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }
        
        IOException firstException = null;
        
        if (classCache != null) {
            classCache.clear();
        }
        
        // Close classpath resolver
        if (classpathResolver != null) {
            try {
                classpathResolver.close();
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        
        // Release main BlockReader from pool - OPTIMIZED: Pool management
        if (jarzFilePath != null) {
            try {
                BlockReaderPool.release(jarzFilePath);
            } catch (IOException e) {
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        
        if (firstException != null) {
            throw firstException;
        }
    }
    
    @Override
    public String toString() {
        return "JarzClassLoader{closed=" + closed + '}';
    }
}
