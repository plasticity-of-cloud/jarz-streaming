package net.jarz.streaming.v2;

import net.jarz.streaming.internal.JarzLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CDN data provider with local index optimization.
 * Uses local index for class location, streams blocks from CDN.
 * Enhanced to cache header/footer/index metadata for 50% request reduction.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class CdnHybridJarzDataProvider implements JarzDataProvider {
    
    private static final JarzLogger logger = JarzLogger.getLogger(CdnHybridJarzDataProvider.class);
    
    private final String jarzUrl;
    private final Path localIndexPath;
    private final HttpJarzDataProvider remoteProvider;
    private volatile JarzLocalIndex localIndex;
    private volatile boolean localIndexChecked = false;
    
    // Cached metadata to eliminate network requests
    private volatile byte[] cachedHeader;
    private volatile byte[] cachedFooter;
    private volatile byte[] cachedIndex;
    
    public CdnHybridJarzDataProvider(String jarzUrl, Path localIndexPath) {
        this.jarzUrl = jarzUrl;
        this.localIndexPath = localIndexPath;
        this.remoteProvider = new HttpJarzDataProvider(jarzUrl);
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        return remoteProvider.readBytes(offset, length);
    }
    
    @Override
    public byte[] readFooter() throws IOException {
        if (cachedFooter == null) {
            JarzLocalIndex index = getLocalIndex();
            if (index != null && index.getCachedFooter() != null) {
                cachedFooter = index.getCachedFooter();
                logger.debug("Using cached footer from local index");
            } else {
                cachedFooter = remoteProvider.readFooter();
                logger.debug("Fetched footer from CDN");
            }
        }
        return cachedFooter.clone();
    }
    
    @Override
    public long getFileSize() throws IOException {
        JarzLocalIndex index = getLocalIndex();
        if (index != null) {
            return index.getOriginalJarzSize();
        }
        return remoteProvider.getFileSize();
    }
    
    /**
     * Read header with local cache optimization.
     */
    public byte[] readHeader() throws IOException {
        if (cachedHeader == null) {
            JarzLocalIndex index = getLocalIndex();
            if (index != null && index.getCachedHeader() != null) {
                cachedHeader = index.getCachedHeader();
                logger.debug("Using cached header from local index");
            } else {
                cachedHeader = remoteProvider.readBytes(0, JarzV2Format.HEADER_SIZE);
                logger.debug("Fetched header from CDN");
            }
        }
        return cachedHeader.clone();
    }
    
    /**
     * Read index with local cache optimization.
     */
    public byte[] readIndex() throws IOException {
        if (cachedIndex == null) {
            JarzLocalIndex index = getLocalIndex();
            if (index != null && index.getCachedIndex() != null) {
                cachedIndex = index.getCachedIndex();
                logger.debug("Using cached index from local index");
            } else {
                // Fall back to remote index reading
                byte[] footer = readFooter();
                java.nio.ByteBuffer footerBuf = java.nio.ByteBuffer.wrap(footer).order(JarzV2Format.BYTE_ORDER);
                long indexOffset = footerBuf.getLong();
                long fileSize = getFileSize();
                int indexSize = (int) (fileSize - indexOffset - JarzV2Format.FOOTER_SIZE);
                cachedIndex = remoteProvider.readBytes(indexOffset, indexSize);
                logger.debug("Fetched index from CDN");
            }
        }
        return cachedIndex.clone();
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
                JarzLocalIndex index = JarzLocalIndex.load(localIndexPath);
                
                // Validate index is still fresh (24 hours)
                if (index.isValid(24 * 60 * 60 * 1000)) {
                    logger.debug("Loading enhanced local index from: {0}", localIndexPath);
                    return index;
                } else {
                    logger.debug("Local index expired, will fetch from CDN");
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️ Failed to load local index from " + localIndexPath + 
                             ", falling back to CDN: " + e.getMessage());
        }
        return null;
    }
    
    @Override
    public void close() throws IOException {
        remoteProvider.close();
    }
}
