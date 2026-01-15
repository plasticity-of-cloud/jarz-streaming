package net.jarz.streaming.s3;

import net.jarz.streaming.classloader.JarzClassLoader;
import net.jarz.streaming.v2.S3JarzDataProvider;
import net.jarz.streaming.v2.S3HybridJarzDataProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;

/**
 * S3-based JARZ ClassLoader using range requests with bundle index support.
 * 
 * <p>This ClassLoader extends the unified JarzClassLoader with S3 data provider
 * for efficient streaming access to JARZ archives stored in S3.
 * 
 * <p>Supports bundle index for O(1) class lookup across multiple S3-hosted JARZ files.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class S3JarzClassLoader extends JarzClassLoader {
    
    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    
    /**
     * Creates S3 ClassLoader with bundle index support.
     * 
     * @param s3Client S3 client for block streaming
     * @param bucket S3 bucket name
     * @param key JARZ file key
     * @param bundleIndexPath path to bundle index file (optional)
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, Path bundleIndexPath) throws IOException {
        this(s3Client, bucket, key, bundleIndexPath, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates S3 ClassLoader with bundle index support and custom parent.
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, Path bundleIndexPath, ClassLoader parent) throws IOException {
        super(bundleIndexPath != null ? 
              new S3HybridJarzDataProvider(s3Client, bucket, key, bundleIndexPath) : 
              new S3JarzDataProvider(s3Client, bucket, key), 
              parent, bundleIndexPath);
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
    }
    
    /**
     * Creates an S3 ClassLoader for the specified JARZ archive (backward compatibility).
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key) throws IOException {
        this(s3Client, bucket, key, (Path) null);
    }
    
    /**
     * Creates an S3 ClassLoader with custom parent ClassLoader (backward compatibility).
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, ClassLoader parent) throws IOException {
        this(s3Client, bucket, key, (Path) null, parent);
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return key;
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        return new S3JarzClassLoader(s3Client, bucket, jarzUrl, (Path) null); // No bundle index for children
    }
}
