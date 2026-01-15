package net.jarz.streaming.cdn;

import net.jarz.streaming.classloader.JarzClassLoader;
import net.jarz.streaming.v2.HttpJarzDataProvider;
import net.jarz.streaming.v2.CdnHybridJarzDataProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CDN-based JARZ ClassLoader using HTTP range requests with bundle index support.
 * 
 * <p>This ClassLoader extends the unified JarzClassLoader with HTTP data provider
 * for efficient streaming access to JARZ archives hosted on CDNs or S3.
 * 
 * <p>Supports bundle index for O(1) class lookup across multiple CDN-hosted JARZ files.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class CdnJarzClassLoader extends JarzClassLoader {
    
    private final String baseUrl;
    private final String jarzUrl;
    
    /**
     * Interface for providing signed URLs for private resources.
     */
    public interface SignedUrlProvider {
        String signUrl(String originalUrl) throws IOException;
    }
    
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
              new HttpJarzDataProvider(jarzUrl), 
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
}
