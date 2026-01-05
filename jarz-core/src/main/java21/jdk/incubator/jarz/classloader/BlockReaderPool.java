package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.BlockReader;
import jdk.incubator.jarz.v2.JarzDataProvider;

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
    
    private static final ConcurrentHashMap<Path, PooledBlockReader> pool = new ConcurrentHashMap<>();
    
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
        return pool.compute(jarzFile, (path, existing) -> {
            if (existing != null) {
                existing.incrementRef();
                return existing;
            } else {
                try {
                    return new PooledBlockReader(new BlockReader(path));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create BlockReader for " + path, e);
                }
            }
        }).getBlockReader();
    }
    
    /**
     * Acquires a BlockReader for the specified JARZ data provider.
     * 
     * <p>Creates a new BlockReader instance for the data provider.
     * Note: Data provider-based readers are not pooled since they don't
     * have a file path for pooling key.
     * 
     * @param dataProvider the JARZ data provider
     * @return new BlockReader instance
     * @throws IOException if the JARZ data cannot be read
     */
    public static BlockReader acquire(JarzDataProvider dataProvider) throws IOException {
        return new BlockReader(dataProvider);
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
        PooledBlockReader pooled = pool.computeIfPresent(jarzFile, (path, existing) -> {
            int newCount = existing.decrementRef();
            return newCount > 0 ? existing : null;
        });
        
        // If removed from pool (ref count reached 0), close the BlockReader
        if (pooled == null) {
            PooledBlockReader removed = pool.remove(jarzFile);
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
        
        PooledBlockReader(BlockReader blockReader) {
            this.blockReader = blockReader;
            this.refCount = new AtomicInteger(1);
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
    }
}
