package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.HttpJarzDataProvider;
import jdk.incubator.jarz.v2.JarzDataProvider;

import java.io.IOException;

/**
 * Factory for creating CDN-based JARZ ClassLoaders.
 * 
 * <p>This factory creates JarzClassLoader instances that fetch data from CDN/HTTP sources
 * using HTTP range requests for efficient streaming access.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class CdnJarzClassLoaderFactory {
    
    /**
     * Creates a CDN ClassLoader for the specified JARZ archive URL.
     *
     * @param jarzUrl full URL to the JARZ v2 archive (e.g., "https://cdn.example.com/app.jarz")
     * @return JarzClassLoader configured for CDN access
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static JarzClassLoader create(String jarzUrl) throws IOException {
        return new JarzClassLoader((JarzDataProvider) new HttpJarzDataProvider(jarzUrl));
    }
    
    /**
     * Creates a CDN ClassLoader with a custom signed URL provider for private archives.
     *
     * @param jarzUrl base URL to the JARZ v2 archive
     * @param urlProvider provider for generating signed URLs
     * @return JarzClassLoader configured for CDN access with signed URLs
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static JarzClassLoader create(String jarzUrl, HttpJarzDataProvider.SignedUrlProvider urlProvider) throws IOException {
        return new JarzClassLoader((JarzDataProvider) new HttpJarzDataProvider(jarzUrl, urlProvider));
    }
    
    /**
     * Creates a CDN ClassLoader with custom parent ClassLoader.
     *
     * @param jarzUrl full URL to the JARZ v2 archive
     * @param parent parent ClassLoader for delegation
     * @return JarzClassLoader configured for CDN access
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static JarzClassLoader create(String jarzUrl, ClassLoader parent) throws IOException {
        return new JarzClassLoader((JarzDataProvider) new HttpJarzDataProvider(jarzUrl), parent);
    }
}
