package net.jarz.streaming.v2;

import net.jarz.streaming.internal.JarzLogger;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optimized CDN data provider that caches JARZ metadata locally.
 * 
 * Reduces CDN requests from 6 to 3 (50% improvement):
 * - Cached: Header, Footer, Index
 * - Streamed: Block data only
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class OptimizedCdnJarzDataProvider implements JarzDataProvider {
    
    private static final JarzLogger logger = JarzLogger.getLogger(OptimizedCdnJarzDataProvider.class);
    
    private final String jarzUrl;
    private final Path localIndexPath;
    private final HttpJarzDataProvider remoteProvider;
    private volatile OptimizedJarzLocalIndex localIndex;
    private volatile boolean localIndexChecked = false;
    
    // Cached metadata
    private volatile byte[] cachedHeader;
    private volatile byte[] cachedFooter;
    private volatile byte[] cachedIndex;
    
    public OptimizedCdnJarzDataProvider(String jarzUrl, Path localIndexPath) {
        this.jarzUrl = jarzUrl;
        this.localIndexPath = localIndexPath;
        this.remoteProvider = new HttpJarzDataProvider(jarzUrl);
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        // Always stream block data from CDN (no caching for blocks)
        return remoteProvider.readBytes(offset, length);
    }
    
    @Override
    public byte[] readFooter() throws IOException {
        if (cachedFooter == null) {
            OptimizedJarzLocalIndex index = getLocalIndex();
            if (index != null) {
                cachedFooter = index.getJarzFooter();
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
        OptimizedJarzLocalIndex index = getLocalIndex();
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
            OptimizedJarzLocalIndex index = getLocalIndex();
            if (index != null) {
                cachedHeader = index.getJarzHeader();
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
            OptimizedJarzLocalIndex index = getLocalIndex();
            if (index != null) {
                cachedIndex = index.getJarzIndex();
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
     * Check if local index is available and valid.
     */
    public boolean hasLocalIndex() throws IOException {
        return getLocalIndex() != null;
    }
    
    /**
     * Get cache hit statistics.
     */
    public CacheStats getCacheStats() {
        boolean headerCached = cachedHeader != null && localIndex != null;
        boolean footerCached = cachedFooter != null && localIndex != null;
        boolean indexCached = cachedIndex != null && localIndex != null;
        
        int cacheHits = (headerCached ? 1 : 0) + (footerCached ? 1 : 0) + (indexCached ? 1 : 0);
        int totalRequests = (cachedHeader != null ? 1 : 0) + (cachedFooter != null ? 1 : 0) + (cachedIndex != null ? 1 : 0);
        
        return new CacheStats(cacheHits, totalRequests, localIndex != null);
    }
    
    private OptimizedJarzLocalIndex getLocalIndex() throws IOException {
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
    
    private OptimizedJarzLocalIndex loadLocalIndex() {
        try {
            if (Files.exists(localIndexPath)) {
                OptimizedJarzLocalIndex index = OptimizedJarzLocalIndex.load(localIndexPath);
                
                // Validate index is still fresh (24 hours)
                if (index.isValid(24 * 60 * 60 * 1000)) {
                    logger.debug("Loading optimized local index from: {0}", localIndexPath);
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
    
    /**
     * Cache statistics for monitoring optimization effectiveness.
     */
    public static class CacheStats {
        private final int cacheHits;
        private final int totalRequests;
        private final boolean hasLocalIndex;
        
        public CacheStats(int cacheHits, int totalRequests, boolean hasLocalIndex) {
            this.cacheHits = cacheHits;
            this.totalRequests = totalRequests;
            this.hasLocalIndex = hasLocalIndex;
        }
        
        public int getCacheHits() { return cacheHits; }
        public int getTotalRequests() { return totalRequests; }
        public boolean hasLocalIndex() { return hasLocalIndex; }
        public double getCacheHitRatio() { 
            return totalRequests > 0 ? (double) cacheHits / totalRequests : 0.0; 
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{hits=%d, total=%d, ratio=%.1f%%, hasIndex=%s}", 
                               cacheHits, totalRequests, getCacheHitRatio() * 100, hasLocalIndex);
        }
    }
}
