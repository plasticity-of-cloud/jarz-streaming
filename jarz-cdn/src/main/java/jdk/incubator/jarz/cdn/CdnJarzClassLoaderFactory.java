package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.v2.HttpJarzDataProvider;

import java.io.IOException;

/**
 * Factory for creating CDN-based JARZ ClassLoaders.
 * 
 * <p>This factory creates CdnJarzClassLoader instances that fetch data from CDN/HTTP sources
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
     * @return CdnJarzClassLoader configured for CDN access
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static CdnJarzClassLoader create(String jarzUrl) throws IOException {
        return new CdnJarzClassLoader(jarzUrl);
    }
    
    /**
     * Creates a CDN ClassLoader with a custom signed URL provider for private archives.
     *
     * @param jarzUrl base URL to the JARZ v2 archive
     * @param urlProvider provider for generating signed URLs (currently not used in constructor)
     * @return CdnJarzClassLoader configured for CDN access with signed URLs
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static CdnJarzClassLoader create(String jarzUrl, HttpJarzDataProvider.SignedUrlProvider urlProvider) throws IOException {
        // Note: SignedUrlProvider integration would require constructor changes
        return new CdnJarzClassLoader(jarzUrl);
    }
    
    /**
     * Creates a CDN ClassLoader with custom parent ClassLoader.
     *
     * @param jarzUrl full URL to the JARZ v2 archive
     * @param parent parent ClassLoader for delegation
     * @return CdnJarzClassLoader configured for CDN access
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public static CdnJarzClassLoader create(String jarzUrl, ClassLoader parent) throws IOException {
        return new CdnJarzClassLoader(jarzUrl, parent);
    }
}
