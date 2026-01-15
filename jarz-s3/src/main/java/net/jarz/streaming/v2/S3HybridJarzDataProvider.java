package net.jarz.streaming.v2;

import net.jarz.streaming.internal.JarzLogger;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * S3 data provider with local index optimization.
 * Uses local index for class location, streams blocks from S3.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class S3HybridJarzDataProvider implements JarzDataProvider {
    
    private static final JarzLogger logger = JarzLogger.getLogger(S3HybridJarzDataProvider.class);
    
    private final S3Client s3Client;
    private final String bucket;
    private final String key;
    private final Path localIndexPath;
    private final S3JarzDataProvider remoteProvider;
    private volatile JarzLocalIndex localIndex;
    private volatile boolean localIndexChecked = false;
    
    public S3HybridJarzDataProvider(S3Client s3Client, String bucket, String key, Path localIndexPath) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.key = key;
        this.localIndexPath = localIndexPath;
        this.remoteProvider = new S3JarzDataProvider(s3Client, bucket, key);
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        return remoteProvider.readBytes(offset, length);
    }
    
    @Override
    public long getFileSize() throws IOException {
        return remoteProvider.getFileSize();
    }
    
    /**
     * Check for class in local index first, avoiding network requests.
     */
    public boolean hasClass(String className) throws IOException {
        JarzLocalIndex index = getLocalIndex();
        if (index != null) {
            return index.hasClass(className);
        }
        // Fall back to remote index check would require BlockReader access
        // For now, return false to indicate unknown
        return false;
    }
    
    /**
     * Get class location from local index if available.
     */
    public JarzLocalIndex.ClassEntry getClassEntry(String className) throws IOException {
        JarzLocalIndex index = getLocalIndex();
        if (index != null) {
            return index.getClassEntry(className);
        }
        return null; // Fall back to remote index
    }
    
    /**
     * Check if local index is available.
     */
    public boolean hasLocalIndex() throws IOException {
        return getLocalIndex() != null;
    }
    
    private JarzLocalIndex getLocalIndex() throws IOException {
        if (!localIndexChecked) {
            synchronized (this) {
                if (!localIndexChecked) {
                    localIndex = loadLocalIndex();
                    localIndexChecked = true;
                }
            }
        }
        return localIndex;
    }
    
    private JarzLocalIndex loadLocalIndex() {
        try {
            if (Files.exists(localIndexPath)) {
                logger.debug("Loading local index from: {0}", localIndexPath);
                return JarzLocalIndex.load(localIndexPath);
            }
        } catch (IOException e) {
            // Log warning but continue with remote fallback
            logger.warning("Failed to load local index from {0}, falling back to remote index: {1}", 
                          localIndexPath, e.getMessage());
        }
        return null;
    }
    
    @Override
    public void close() throws IOException {
        remoteProvider.close();
    }
}
