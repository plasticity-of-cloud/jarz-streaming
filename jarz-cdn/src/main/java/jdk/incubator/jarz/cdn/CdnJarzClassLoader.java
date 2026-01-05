package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.HttpJarzDataProvider;
import jdk.incubator.jarz.v2.HttpJarzDataProvider;
import jdk.incubator.jarz.v2.CdnHybridJarzDataProvider;
import jdk.incubator.jarz.v2.JarzDataProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CDN-based JARZ ClassLoader using HTTP range requests.
 * 
 * <p>This ClassLoader extends the unified JarzClassLoader with HTTP data provider
 * for efficient streaming access to JARZ archives hosted on CDNs or S3.
 * 
 * <p>Supports local index optimization for instant class location without network requests.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class CdnJarzClassLoader extends JarzClassLoader {
    
    /**
     * Interface for providing signed URLs for private resources.
     */
    public interface SignedUrlProvider {
        String signUrl(String originalUrl) throws IOException;
    }
    
    /**
     * Creates CDN ClassLoader with local index optimization.
     * 
     * @param jarzUrl URL to JARZ archive
     * @param localIndexPath path to local index file (optional)
     */
    public CdnJarzClassLoader(String jarzUrl, Path localIndexPath) throws IOException {
        super(new CdnHybridJarzDataProvider(jarzUrl, localIndexPath), Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates CDN ClassLoader with local index optimization and custom parent.
     */
    public CdnJarzClassLoader(String jarzUrl, Path localIndexPath, ClassLoader parent) throws IOException {
        super(new CdnHybridJarzDataProvider(jarzUrl, localIndexPath), parent);
    }
    
    /**
     * Creates a CDN ClassLoader for the specified JARZ archive URL (backward compatibility).
     *
     * @param jarzUrl full URL to the JARZ v2 archive (e.g., "https://cdn.example.com/app.jarz")
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public CdnJarzClassLoader(String jarzUrl) throws IOException {
        super(new HttpJarzDataProvider(jarzUrl)); // Use HttpJarzDataProvider constructor
    }
    
    /**
     * Creates a CDN ClassLoader with a custom signed URL provider for private archives (backward compatibility).
     *
     * @param jarzUrl base URL to the JARZ v2 archive
     * @param urlProvider provider for generating signed URLs
     * @param cacheSize ignored (maintained for API compatibility)
     * @throws IOException if the JARZ URL cannot be accessed
     */
    public CdnJarzClassLoader(String jarzUrl, SignedUrlProvider urlProvider, int cacheSize) throws IOException {
        super(new HttpJarzDataProvider(jarzUrl, new SignedUrlProviderAdapter(urlProvider)));
    }
    
    /**
     * Creates a CDN ClassLoader with HttpJarzDataProvider.
     */
    private CdnJarzClassLoader(HttpJarzDataProvider dataProvider) throws IOException {
        super((JarzDataProvider) dataProvider);
    }
    
    /**
     * Adapter to convert old SignedUrlProvider to new HttpJarzDataProvider.SignedUrlProvider.
     */
    private static class SignedUrlProviderAdapter implements HttpJarzDataProvider.SignedUrlProvider {
        private final SignedUrlProvider delegate;
        
        public SignedUrlProviderAdapter(SignedUrlProvider delegate) {
            this.delegate = delegate;
        }
        
        @Override
        public String signUrl(String originalUrl) throws IOException {
            return delegate.signUrl(originalUrl);
        }
    }
}
