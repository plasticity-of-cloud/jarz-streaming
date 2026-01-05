package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.S3JarzDataProvider;
import jdk.incubator.jarz.v2.S3HybridJarzDataProvider;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;

/**
 * S3-based JARZ ClassLoader using range requests.
 * 
 * <p>This ClassLoader extends the unified JarzClassLoader with S3 data provider
 * for efficient streaming access to JARZ archives stored in S3.
 * 
 * <p>Supports local index optimization for instant class location without network requests.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class S3JarzClassLoader extends JarzClassLoader {
    
    /**
     * Creates S3 ClassLoader with local index optimization.
     * 
     * @param s3Client S3 client for block streaming
     * @param bucket S3 bucket name
     * @param key JARZ file key
     * @param localIndexPath path to local index file (optional)
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, Path localIndexPath) throws IOException {
        super(new S3HybridJarzDataProvider(s3Client, bucket, key, localIndexPath), Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates S3 ClassLoader with local index optimization and custom parent.
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, Path localIndexPath, ClassLoader parent) throws IOException {
        super(new S3HybridJarzDataProvider(s3Client, bucket, key, localIndexPath), parent);
    }
    
    /**
     * Creates an S3 ClassLoader for the specified JARZ archive (backward compatibility).
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key) throws IOException {
        super(new S3JarzDataProvider(s3Client, bucket, key), Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates an S3 ClassLoader with custom parent ClassLoader (backward compatibility).
     */
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, ClassLoader parent) throws IOException {
        super(new S3JarzDataProvider(s3Client, bucket, key), parent);
    }
}
