package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.FileJarzDataProvider;

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
 * Application ClassLoader implementation for JARZ archives with Main-Class support and bundle index.
 * 
 * <p>This ClassLoader extends {@link JarzClassLoader} and adds application-specific
 * functionality including Main-Class requirement and validation. It provides
 * drop-in compatibility with standard JAR files for application loading.
 * 
 * <p>Supports bundle index for efficient multi-JARZ class loading with O(1) lookup
 * across multiple JARZ files in the same directory.
 * 
 * <p>This enables {@code java -jarz MyApp.jarz} to work identically to {@code java -jar MyApp.jar}.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzPath)) {
 *     String mainClassName = loader.getMainClassName();
 *     Class<?> mainClass = loader.loadClass(mainClassName);
 *     Method mainMethod = mainClass.getMethod("main", String[].class);
 *     mainMethod.invoke(null, (Object) args);
 * }
 * }</pre>
 * 
 * <h2>Bundle Index Example</h2>
 * <pre>{@code
 * // With bundle index for multi-JARZ support
 * try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(
 *         Paths.get("app.jarz"), 
 *         Paths.get("bundle.index"))) {
 *     // Can load classes from any JARZ file listed in bundle index
 *     Class<?> mainClass = loader.loadClass("kafka.Kafka"); // O(1) lookup
 * }
 * }</pre>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public final class JarzApplicationClassLoader extends JarzClassLoader {
    
    // Application-specific components
    private final String mainClassName;
    private final Path baseDirectory;
    private final Path jarzPath;
    
    /**
     * Creates a new JarzApplicationClassLoader for the specified JARZ archive.
     * 
     * <p>This constructor uses the system ClassLoader as the parent and requires
     * a valid Main-Class attribute in the manifest for application loading.
     * 
     * @param jarzFile path to the JARZ archive file, must not be null and must exist
     * @throws IOException if the JARZ file cannot be read, is corrupted, or lacks Main-Class
     * @throws IllegalArgumentException if jarzFile is null
     * @throws SecurityException if a security manager exists and denies read access
     * @since 1.0
     */
    public JarzApplicationClassLoader(Path jarzFile) throws IOException {
        this(jarzFile, null, getSystemClassLoader());
    }
    
    /**
     * Creates a new JarzApplicationClassLoader with bundle index support.
     * 
     * <p>This constructor enables multi-JARZ class loading with O(1) lookup
     * across multiple JARZ files listed in the bundle index.
     * 
     * @param jarzFile path to the JARZ archive file, must not be null and must exist
     * @param bundleIndexPath path to bundle index file, null to disable bundle index
     * @throws IOException if the JARZ file cannot be read, is corrupted, or lacks Main-Class
     * @throws IllegalArgumentException if jarzFile is null
     * @throws SecurityException if a security manager exists and denies read access
     * @since 1.0
     */
    public JarzApplicationClassLoader(Path jarzFile, Path bundleIndexPath) throws IOException {
        this(jarzFile, bundleIndexPath, getSystemClassLoader());
    }
    
    /**
     * Creates a new JarzApplicationClassLoader with the specified parent ClassLoader and bundle index.
     * 
     * <p>This constructor requires a valid Main-Class attribute in the manifest
     * for application loading scenarios and supports bundle index for multi-JARZ loading.
     * 
     * @param jarzFile path to the JARZ archive file, must not be null and must exist
     * @param bundleIndexPath path to bundle index file, null to disable bundle index
     * @param parent parent ClassLoader for delegation, must not be null
     * @throws IOException if the JARZ file cannot be read, is corrupted, or lacks Main-Class
     * @throws IllegalArgumentException if jarzFile or parent is null
     * @throws SecurityException if a security manager exists and denies read access
     * @since 1.0
     */
    public JarzApplicationClassLoader(Path jarzFile, Path bundleIndexPath, ClassLoader parent) throws IOException {
        super(new FileJarzDataProvider(jarzFile), parent, bundleIndexPath);
        
        this.jarzPath = jarzFile;
        this.baseDirectory = jarzFile.getParent();
        
        // Application ClassLoader requires Main-Class
        String mainClass = manifest.getMainAttributes().getValue("Main-Class");
        if (mainClass == null || mainClass.trim().isEmpty()) {
            throw new IOException("No Main-Class attribute in manifest");
        }
        this.mainClassName = mainClass.trim();
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return jarzPath.getFileName().toString();
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        Path childJarzPath = baseDirectory.resolve(jarzUrl);
        return new JarzApplicationClassLoader(childJarzPath, null); // No bundle index for children
    }
    
    /**
     * Returns the main class name from the manifest.
     * 
     * <p>This method extracts the Main-Class attribute from the JARZ archive's
     * META-INF/MANIFEST.MF file, which specifies the application entry point
     * for {@code java -jarz} execution.
     * 
     * @return the fully qualified main class name, never null
     * @since 1.0
     */
    public String getMainClassName() {
        return mainClassName;
    }
    
    @Override
    public String toString() {
        int classpathEntries = 0;
        
        // Count classpath entries from resolver
        if (classpathResolver != null && classpathResolver.hasEntries()) {
            String classPath = manifest.getMainAttributes().getValue("Class-Path");
            if (classPath != null && !classPath.trim().isEmpty()) {
                for (String entry : classPath.trim().split("\\s+")) {
                    if (!entry.isEmpty() && entry.endsWith(".jarz")) {
                        classpathEntries++;
                    }
                }
            }
        }
        
        return "JarzApplicationClassLoader{" +
               "mainClass=" + mainClassName +
               ", classpathEntries=" + classpathEntries +
               ", closed=" + (blockReader == null) +
               '}';
    }
}
