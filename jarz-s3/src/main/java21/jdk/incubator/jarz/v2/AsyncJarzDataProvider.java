package jdk.incubator.jarz.v2;

import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * Async JARZ Data Provider interface for Java 21+ virtual thread optimizations.
 * 
 * <p>Extends the base JarzDataProvider with async methods that leverage virtual threads
 * for concurrent I/O operations, significantly improving performance for network-based
 * streaming (S3, CDN, ECR).
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public interface AsyncJarzDataProvider extends JarzDataProvider {
    
    /**
     * Async version of readBytes using virtual threads.
     * 
     * @param offset byte offset in the JARZ file
     * @param length number of bytes to read
     * @return CompletableFuture with byte array
     */
    CompletableFuture<byte[]> readBytesAsync(long offset, int length);
    
    /**
     * Async version of getFileSize.
     * 
     * @return CompletableFuture with file size
     */
    CompletableFuture<Long> getFileSizeAsync();
    
    /**
     * Prefetch multiple blocks concurrently for performance optimization.
     * 
     * @param ranges list of block ranges to prefetch
     * @return CompletableFuture that completes when prefetching is done
     */
    CompletableFuture<Void> prefetchBlocks(List<BlockRange> ranges);
    
    /**
     * Block range specification for prefetching.
     */
    record BlockRange(long offset, int length) {}
}
