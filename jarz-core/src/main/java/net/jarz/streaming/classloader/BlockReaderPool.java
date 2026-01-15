package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.JarzDataProvider;
import net.jarz.streaming.v2.FileJarzDataProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool for sharing BlockReader instances across multiple ClassLoaders.
 * 
 * <p>This pool enables significant memory savings when multiple ClassLoaders
 * access the same JARZ files by sharing BlockReader instances with proper
 * reference counting for safe cleanup.
 * 
 * <p>Thread-safe implementation supports concurrent access from multiple
 * ClassLoaders while ensuring proper resource management.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public final class BlockReaderPool {
    
    private static final ConcurrentHashMap<String, PooledBlockReader> pool = new ConcurrentHashMap<>();
    
    /**
     * Acquire a BlockReader for the specified JARZ file.
     * 
     * <p>If a BlockReader already exists for this file, returns the shared instance
     * and increments the reference count. Otherwise, creates a new BlockReader.
     * 
     * @param jarzFile path to the JARZ file
     * @return shared BlockReader instance
     * @throws IOException if the JARZ file cannot be read
     */
    public static BlockReader acquire(Path jarzFile) throws IOException {
        String key = jarzFile.normalize().toAbsolutePath().toString();
        return pool.compute(key, (k, existing) -> {
            if (existing != null) {
                existing.incrementRef();
                return existing;
            } else {
                try {
                    return new PooledBlockReader(new BlockReader(jarzFile), key);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create BlockReader for " + jarzFile, e);
                }
            }
        }).getBlockReader();
    }
    
    /**
     * Acquire a BlockReader for the specified data provider.
     * 
     * <p>For FileJarzDataProvider, uses file-based pooling. For other providers,
     * creates a unique key based on provider characteristics.
     * 
     * @param dataProvider data provider for JARZ access
     * @return shared BlockReader instance (for file providers) or new instance (for remote providers)
     * @throws IOException if the JARZ data cannot be read
     */
    public static BlockReader acquire(JarzDataProvider dataProvider) throws IOException {
        if (dataProvider instanceof FileJarzDataProvider) {
            // Use file-based pooling for local files
            FileJarzDataProvider fileProvider = (FileJarzDataProvider) dataProvider;
            return acquire(fileProvider.getFilePath());
        } else {
            // For remote providers, create unique instances (no pooling benefit)
            return new BlockReader(dataProvider);
        }
    }
    
    /**
     * Release a BlockReader for the specified JARZ file.
     * 
     * <p>Decrements the reference count and closes the BlockReader if no more
     * references exist. This ensures proper cleanup when all ClassLoaders
     * using a JARZ file are closed.
     * 
     * @param jarzFile path to the JARZ file
     * @throws IOException if the BlockReader cannot be closed
     */
    public static void release(Path jarzFile) throws IOException {
        String key = jarzFile.normalize().toAbsolutePath().toString();
        release(key);
    }
    
    /**
     * Release a BlockReader for the specified data provider.
     * 
     * @param dataProvider data provider that was used to acquire the BlockReader
     * @throws IOException if the BlockReader cannot be closed
     */
    public static void release(JarzDataProvider dataProvider) throws IOException {
        if (dataProvider instanceof FileJarzDataProvider) {
            FileJarzDataProvider fileProvider = (FileJarzDataProvider) dataProvider;
            release(fileProvider.getFilePath());
        }
        // Remote providers don't use pooling, so no release needed
    }
    
    /**
     * Internal release method using string key.
     */
    private static void release(String key) throws IOException {
        PooledBlockReader pooled = pool.computeIfPresent(key, (k, existing) -> {
            int newCount = existing.decrementRef();
            return newCount > 0 ? existing : null;
        });
        
        // If removed from pool (ref count reached 0), close the BlockReader
        if (pooled == null) {
            PooledBlockReader removed = pool.remove(key);
            if (removed != null) {
                removed.close();
            }
        }
    }
    
    /**
     * Get the current number of pooled BlockReaders.
     * 
     * @return number of active BlockReader instances in the pool
     */
    public static int getPoolSize() {
        return pool.size();
    }
    
    /**
     * Clear all pooled BlockReaders (for testing/cleanup).
     * 
     * <p>WARNING: This forcibly closes all BlockReaders regardless of reference counts.
     * Should only be used for testing or emergency cleanup.
     */
    static void clearPool() throws IOException {
        for (PooledBlockReader pooled : pool.values()) {
            pooled.close();
        }
        pool.clear();
    }
    
    /**
     * Wrapper for BlockReader with reference counting.
     */
    private static final class PooledBlockReader {
        private final BlockReader blockReader;
        private final AtomicInteger refCount;
        private final String poolKey;
        
        PooledBlockReader(BlockReader blockReader, String poolKey) {
            this.blockReader = blockReader;
            this.refCount = new AtomicInteger(1);
            this.poolKey = poolKey;
        }
        
        BlockReader getBlockReader() {
            return blockReader;
        }
        
        void incrementRef() {
            refCount.incrementAndGet();
        }
        
        int decrementRef() {
            return refCount.decrementAndGet();
        }
        
        void close() throws IOException {
            blockReader.close();
        }
        
        int getRefCount() {
            return refCount.get();
        }
        
        String getPoolKey() {
            return poolKey;
        }
    }
}
