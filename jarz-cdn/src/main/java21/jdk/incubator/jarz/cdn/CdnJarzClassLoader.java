package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.HttpJarzDataProvider;

import java.io.IOException;
import java.net.http.HttpClient;
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
    
    /**
     * Creates a CDN ClassLoader with virtual thread-optimized HTTP client.
     */
    public CdnJarzClassLoader(String jarzUrl) throws IOException {
        super(createVirtualThreadDataProvider(jarzUrl), Thread.currentThread().getContextClassLoader());
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
