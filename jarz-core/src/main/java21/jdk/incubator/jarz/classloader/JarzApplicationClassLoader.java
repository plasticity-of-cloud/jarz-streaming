package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.BlockReader;

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
 * Application ClassLoader implementation for JARZ archives with Main-Class support.
 * 
 * <p>This ClassLoader extends {@link JarzClassLoader} and adds application-specific
 * functionality including Main-Class requirement and validation. It provides
 * drop-in compatibility with standard JAR files for application loading.
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
 * @author Plasticity.Cloud
 * @since 1.0
 */
public final class JarzApplicationClassLoader extends JarzClassLoader {
    
    // Application-specific components
    private final String mainClassName;
    
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
        this(jarzFile, getSystemClassLoader());
    }
    
    /**
     * Creates a new JarzApplicationClassLoader with the specified parent ClassLoader.
     * 
     * <p>This constructor requires a valid Main-Class attribute in the manifest
     * for application loading scenarios.
     * 
     * @param jarzFile path to the JARZ archive file, must not be null and must exist
     * @param parent parent ClassLoader for delegation, must not be null
     * @throws IOException if the JARZ file cannot be read, is corrupted, or lacks Main-Class
     * @throws IllegalArgumentException if jarzFile or parent is null
     * @throws SecurityException if a security manager exists and denies read access
     * @since 1.0
     */
    public JarzApplicationClassLoader(Path jarzFile, ClassLoader parent) throws IOException {
        super(jarzFile, parent);
        
        // Application ClassLoader requires Main-Class
        String mainClass = manifest.getMainAttributes().getValue("Main-Class");
        if (mainClass == null || mainClass.trim().isEmpty()) {
            throw new IOException("No Main-Class attribute in manifest");
        }
        this.mainClassName = mainClass.trim();
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
