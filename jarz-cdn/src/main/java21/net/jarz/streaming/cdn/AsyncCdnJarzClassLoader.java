package net.jarz.streaming.cdn;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Async API extensions for CDN JARZ ClassLoader with virtual threads and backpressure.
 *
 * <p>This interface provides non-blocking, reactive-style operations for high-throughput
 * scenarios where traditional blocking I/O would be inefficient.
 *
 * @since 1.0
 */
public interface AsyncCdnJarzClassLoader extends AutoCloseable {

    /**
     * Asynchronously loads a class without blocking the calling thread.
     *
     * @param className fully qualified class name
     * @return CompletableFuture that completes with the loaded Class
     */
    CompletableFuture<Class<?>> loadClassAsync(String className);

    /**
     * Asynchronously loads multiple classes in parallel with backpressure control.
     *
     * @param classNames list of class names to load
     * @param maxConcurrency maximum number of concurrent loads
     * @return CompletableFuture that completes with a map of class name to Class
     */
    CompletableFuture<Map<String, Class<?>>> loadClassesAsync(List<String> classNames, int maxConcurrency);

    /**
     * Creates a reactive stream of class loading operations with backpressure.
     *
     * @param classNames list of class names to load
     * @return Flow.Publisher that emits ClassLoadResult items
     */
    Flow.Publisher<ClassLoadResult> loadClassesReactive(List<String> classNames);

    /**
     * Asynchronously prefetches blocks for the specified classes.
     *
     * @param classNames list of class names to prefetch
     * @return CompletableFuture that completes when prefetch is done
     */
    CompletableFuture<Void> prefetchAsync(List<String> classNames);

    /**
     * Gets real-time cache statistics.
     *
     * @return current cache statistics
     */
    CacheStats getCacheStats();

    /**
     * Result of an async class loading operation.
     */
    record ClassLoadResult(String className, Class<?> clazz, Throwable error, long loadTimeMs) {
        public boolean isSuccess() {
            return error == null;
        }
    }

    /**
     * Enhanced cache statistics with performance metrics.
     */
    record CacheStats(
            int cachedBlocks,
            long memoryUsage,
            int totalBlocks,
            long hits,
            long misses,
            double hitRatio,
            long avgLoadTimeMs,
            int activeRequests
    ) {}
}
