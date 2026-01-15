package net.jarz.streaming.v2;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.io.IOException;

/**
 * S3 JARZ Data Provider for Java 11+ compatibility.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class S3JarzDataProvider implements JarzDataProvider {
    
    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    private volatile Long cachedFileSize;
    
    public S3JarzDataProvider(S3Client s3Client, String bucket, String key) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .range("bytes=" + offset + "-" + (offset + length - 1))
                .build();
            
            return s3Client.getObject(request).readAllBytes();
        } catch (Exception e) {
            throw new IOException("Failed to read S3 range: " + e.getMessage(), e);
        }
    }
    
    @Override
    public long getFileSize() throws IOException {
        if (cachedFileSize != null) {
            return cachedFileSize;
        }
        
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
            
            cachedFileSize = s3Client.headObject(request).contentLength();
            return cachedFileSize;
        } catch (Exception e) {
            throw new IOException("Failed to get S3 object size: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void close() throws IOException {
        // S3Client is managed externally
    }
}
