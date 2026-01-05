package jdk.incubator.jarz.cdn;

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
    static final class ClassLoadResult {
        private final String className;
        private final Class<?> clazz;
        private final Throwable error;
        private final long loadTimeMs;
        
        ClassLoadResult(String className, Class<?> clazz, Throwable error, long loadTimeMs) {
            this.className = className;
            this.clazz = clazz;
            this.error = error;
            this.loadTimeMs = loadTimeMs;
        }
        
        public String className() { return className; }
        public Class<?> clazz() { return clazz; }
        public Throwable error() { return error; }
        public long loadTimeMs() { return loadTimeMs; }
        
        public boolean isSuccess() {
            return error == null;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ClassLoadResult)) return false;
            ClassLoadResult other = (ClassLoadResult) obj;
            return java.util.Objects.equals(className, other.className) &&
                   java.util.Objects.equals(clazz, other.clazz) &&
                   java.util.Objects.equals(error, other.error) &&
                   loadTimeMs == other.loadTimeMs;
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(className, clazz, error, loadTimeMs);
        }
        
        @Override
        public String toString() {
            return "ClassLoadResult[className=" + className + ", clazz=" + clazz + 
                   ", error=" + error + ", loadTimeMs=" + loadTimeMs + "]";
        }
    }

    /**
     * Enhanced cache statistics with performance metrics.
     */
    static final class CacheStats {
        private final int cachedBlocks;
        private final long memoryUsage;
        private final int totalBlocks;
        private final long hits;
        private final long misses;
        private final double hitRatio;
        private final long avgLoadTimeMs;
        private final int activeRequests;

        public CacheStats(int cachedBlocks, long memoryUsage, int totalBlocks,
                         long hits, long misses, double hitRatio,
                         long avgLoadTimeMs, int activeRequests) {
            this.cachedBlocks = cachedBlocks;
            this.memoryUsage = memoryUsage;
            this.totalBlocks = totalBlocks;
            this.hits = hits;
            this.misses = misses;
            this.hitRatio = hitRatio;
            this.avgLoadTimeMs = avgLoadTimeMs;
            this.activeRequests = activeRequests;
        }

        public int cachedBlocks() { return cachedBlocks; }
        public long memoryUsage() { return memoryUsage; }
        public int totalBlocks() { return totalBlocks; }
        public long hits() { return hits; }
        public long misses() { return misses; }
        public double hitRatio() { return hitRatio; }
        public long avgLoadTimeMs() { return avgLoadTimeMs; }
        public int activeRequests() { return activeRequests; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            CacheStats that = (CacheStats) obj;
            return cachedBlocks == that.cachedBlocks &&
                   memoryUsage == that.memoryUsage &&
                   totalBlocks == that.totalBlocks &&
                   hits == that.hits &&
                   misses == that.misses &&
                   Double.compare(that.hitRatio, hitRatio) == 0 &&
                   avgLoadTimeMs == that.avgLoadTimeMs &&
                   activeRequests == that.activeRequests;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(cachedBlocks, memoryUsage, totalBlocks, hits, misses, hitRatio, avgLoadTimeMs, activeRequests);
        }

        @Override
        public String toString() {
            return "CacheStats{" +
                   "cachedBlocks=" + cachedBlocks +
                   ", memoryUsage=" + memoryUsage +
                   ", totalBlocks=" + totalBlocks +
                   ", hits=" + hits +
                   ", misses=" + misses +
                   ", hitRatio=" + hitRatio +
                   ", avgLoadTimeMs=" + avgLoadTimeMs +
                   ", activeRequests=" + activeRequests +
                   '}';
        }
    }
}
