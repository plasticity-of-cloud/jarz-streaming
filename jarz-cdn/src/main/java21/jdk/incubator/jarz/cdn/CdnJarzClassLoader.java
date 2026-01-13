package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.HttpJarzDataProvider;
import jdk.incubator.jarz.v2.CdnHybridJarzDataProvider;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Java 21+ CDN JARZ ClassLoader with virtual threads and enhanced HTTP/2 performance.
 * 
 * <p>This version leverages Java 21 virtual threads for superior concurrency and
 * performance when loading classes from CDN endpoints.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class CdnJarzClassLoader extends JarzClassLoader {
    
    private final String baseUrl;
    private final String jarzUrl;
    
    /**
     * Creates CDN ClassLoader with bundle index support.
     * 
     * @param jarzUrl URL to JARZ archive
     * @param bundleIndexPath path to bundle index file (optional)
     */
    public CdnJarzClassLoader(String jarzUrl, Path bundleIndexPath) throws IOException {
        this(jarzUrl, bundleIndexPath, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates CDN ClassLoader with bundle index support and custom parent.
     */
    public CdnJarzClassLoader(String jarzUrl, Path bundleIndexPath, ClassLoader parent) throws IOException {
        super(bundleIndexPath != null ? 
              new CdnHybridJarzDataProvider(jarzUrl, bundleIndexPath) : 
              createVirtualThreadDataProvider(jarzUrl), 
              parent, 
              bundleIndexPath);
        this.jarzUrl = jarzUrl;
        this.baseUrl = extractBaseUrl(jarzUrl);
    }
    
    /**
     * Creates a CDN ClassLoader for the specified JARZ archive URL (backward compatibility).
     */
    public CdnJarzClassLoader(String jarzUrl) throws IOException {
        this(jarzUrl, (Path) null);
    }
    
    /**
     * Creates a CDN ClassLoader with custom parent ClassLoader (backward compatibility).
     */
    public CdnJarzClassLoader(String jarzUrl, ClassLoader parent) throws IOException {
        this(jarzUrl, (Path) null, parent);
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return jarzUrl;
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        String fullUrl = jarzUrl.startsWith("http") ? jarzUrl : baseUrl + "/" + jarzUrl;
        return new CdnJarzClassLoader(fullUrl, (Path) null); // No bundle index for children
    }
    
    private String extractBaseUrl(String jarzUrl) {
        int lastSlash = jarzUrl.lastIndexOf('/');
        return lastSlash > 0 ? jarzUrl.substring(0, lastSlash) : jarzUrl;
    }
    
    /**
     * Creates HTTP data provider optimized for virtual threads.
     */
    private static HttpJarzDataProvider createVirtualThreadDataProvider(String url) throws IOException {
        return new HttpJarzDataProvider(url);
    }
    
    /**
     * Asynchronously loads a class using virtual threads.
     */
    public CompletableFuture<Class<?>> loadClassAsync(String className) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadClass(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}
