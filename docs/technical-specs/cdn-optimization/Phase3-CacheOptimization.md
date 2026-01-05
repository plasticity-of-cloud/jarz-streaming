# CDN ClassLoader Phase 3: Cache Optimization

## Objective
Implement shared BlockCache pools for CDN ClassLoaders accessing the same JARZ URLs, eliminating the largest memory overhead component (512KB per ClassLoader).

## Current Problem
Each CDN ClassLoader creates its own BlockCache:
```java
// Each ClassLoader allocates separate 512KB cache
this.blockCache = new BlockCache(cacheSize);  // Default: 64 blocks × 8KB = 512KB
```

**Impact**: 512KB per ClassLoader, even when accessing the same CDN JARZ file.

**Enterprise Impact**:
- **Spark**: 1000 ClassLoaders × 512KB = 512MB cache overhead
- **JEE**: 500 ClassLoaders × 512KB = 256MB cache overhead

## Solution Design

### Shared BlockCache Pool
```java
public class BlockCachePool {
    private static final ConcurrentHashMap<String, PoolEntry> cachePool = new ConcurrentHashMap<>();
    private static final int DEFAULT_SHARED_CACHE_SIZE = 256; // Larger shared cache
    
    static class PoolEntry {
        final BlockCache cache;
        final AtomicInteger refCount;
        final String urlKey;
        
        PoolEntry(BlockCache cache, String urlKey) {
            this.cache = cache;
            this.refCount = new AtomicInteger(1);
            this.urlKey = urlKey;
        }
        
        void incrementRef() {
            refCount.incrementAndGet();
        }
        
        boolean decrementRef() {
            return refCount.decrementAndGet() == 0;
        }
    }
    
    public static BlockCache acquire(String jarzUrl, int requestedSize) {
        String urlKey = normalizeUrl(jarzUrl);
        
        return cachePool.compute(urlKey, (key, existing) -> {
            if (existing != null) {
                existing.incrementRef();
                return existing;
            } else {
                // Create larger shared cache for better hit rates
                int sharedSize = Math.max(requestedSize, DEFAULT_SHARED_CACHE_SIZE);
                BlockCache cache = new BlockCache(sharedSize);
                return new PoolEntry(cache, key);
            }
        }).cache;
    }
    
    public static void release(String jarzUrl) {
        String urlKey = normalizeUrl(jarzUrl);
        
        cachePool.computeIfPresent(urlKey, (key, entry) -> {
            if (entry.decrementRef()) {
                // Last reference - remove from pool
                return null;
            }
            return entry;
        });
    }
    
    private static String normalizeUrl(String jarzUrl) {
        // Remove query parameters and fragments for cache key
        try {
            URI uri = URI.create(jarzUrl);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return jarzUrl; // Fallback to original URL
        }
    }
}
```

### Enhanced BlockCache for Sharing
```java
public class SharedBlockCache extends BlockCache {
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicInteger activeUsers = new AtomicInteger(0);
    
    public SharedBlockCache(int maxBlocks) {
        super(maxBlocks);
    }
    
    @Override
    public byte[] get(int blockId) {
        byte[] result = super.get(blockId);
        if (result != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return result;
    }
    
    public void addUser() {
        activeUsers.incrementAndGet();
    }
    
    public void removeUser() {
        activeUsers.decrementAndGet();
    }
    
    public CacheStats getStats() {
        long totalRequests = hits.get() + misses.get();
        double hitRate = totalRequests > 0 ? (double) hits.get() / totalRequests : 0.0;
        
        return new CacheStats(
            hits.get(),
            misses.get(),
            hitRate,
            activeUsers.get(),
            size(),
            getMemoryUsage()
        );
    }
}
```

## Implementation Strategy

### Updated CDN ClassLoader
```java
public class CdnJarzClassLoader extends ClassLoader implements AutoCloseable {
    private final String jarzUrl;
    private final BlockCache sharedBlockCache;  // From pool
    private final HttpClient sharedHttpClient;
    
    public CdnJarzClassLoader(String jarzUrl, SignedUrlProvider signedUrlProvider, int cacheSize) {
        super(ClassLoader.getSystemClassLoader());
        this.jarzUrl = Objects.requireNonNull(jarzUrl, "jarzUrl must not be null");
        this.sharedHttpClient = HttpClientPool.acquire(jarzUrl);
        this.sharedBlockCache = BlockCachePool.acquire(jarzUrl, cacheSize);
        this.signedUrlProvider = signedUrlProvider;
        
        // Track cache usage
        if (sharedBlockCache instanceof SharedBlockCache) {
            ((SharedBlockCache) sharedBlockCache).addUser();
        }
    }
    
    @Override
    public void close() throws IOException {
        if (sharedBlockCache instanceof SharedBlockCache) {
            ((SharedBlockCache) sharedBlockCache).removeUser();
        }
        
        BlockCachePool.release(jarzUrl);
        HttpClientPool.release(jarzUrl);
    }
}
```

### Cache-Aware Block Loading
```java
private byte[] fetchBlock(BlockLocation location) throws IOException {
    int blockId = location.blockId();
    
    // Check shared cache first
    byte[] cachedBlock = sharedBlockCache.get(blockId);
    if (cachedBlock != null) {
        return cachedBlock;
    }
    
    // Fetch from CDN with connection pooling
    byte[] blockData = fetchBlockFromCdn(blockId);
    
    // Store in shared cache for other ClassLoaders
    sharedBlockCache.put(blockId, blockData);
    
    return blockData;
}
```

## Memory Impact

### Before Phase 3
- **Per ClassLoader**: 515KB (including 512KB individual cache)
- **1000 ClassLoaders**: 512MB cache overhead

### After Phase 3
- **Per ClassLoader**: 15KB (no individual cache)
- **Shared caches**: ~10MB total (shared across all ClassLoaders per URL)

### Enterprise Savings
- **Spark**: 502MB savings (512MB → 10MB cache overhead)
- **JEE**: 246MB savings (256MB → 10MB cache overhead)
- **Cache efficiency**: Higher hit rates due to larger shared caches

## Cache Efficiency Benefits

### Improved Hit Rates
```java
// Before: 64-block cache per ClassLoader
// After: 256-block shared cache per URL
// Result: 4x larger effective cache size
```

### Memory Utilization
- **Elimination of duplication**: Same blocks cached once instead of per-ClassLoader
- **Better cache sizing**: Larger shared caches have better hit rates
- **Dynamic allocation**: Cache size adapts to actual usage patterns

## Implementation Risks

### Cache Contention
- **Risk**: High contention on popular cache entries
- **Mitigation**: Lock-free cache implementation with ConcurrentHashMap

### Memory Leaks
- **Risk**: Shared caches not properly released
- **Mitigation**: Reference counting with automatic cleanup

### Cache Coherency
- **Risk**: Stale data in shared caches
- **Mitigation**: URL-based cache keys with proper invalidation

## Testing Strategy

### Cache Sharing Tests
```java
@Test
void testBlockCacheSharing() {
    String sameUrl = "https://cdn.example.com/app.jarz";
    
    CdnJarzClassLoader loader1 = new CdnJarzClassLoader(sameUrl);
    CdnJarzClassLoader loader2 = new CdnJarzClassLoader(sameUrl);
    
    BlockCache cache1 = getPrivateField(loader1, "sharedBlockCache");
    BlockCache cache2 = getPrivateField(loader2, "sharedBlockCache");
    
    // Should share the same cache instance
    assertSame(cache1, cache2);
    
    loader1.close();
    loader2.close();
}
```

### Memory Efficiency Tests
```java
@Test
void testSharedCacheMemoryUsage() {
    String sameUrl = "https://cdn.example.com/app.jarz";
    long baseline = getUsedMemory();
    
    // Create 100 ClassLoaders for same URL
    CdnJarzClassLoader[] loaders = new CdnJarzClassLoader[100];
    for (int i = 0; i < 100; i++) {
        loaders[i] = new CdnJarzClassLoader(sameUrl);
    }
    
    long afterCreation = getUsedMemory();
    long totalCacheOverhead = afterCreation - baseline;
    
    // Should be ~2MB (shared cache) not 51MB (individual caches)
    assertTrue(totalCacheOverhead < 5 * 1024 * 1024, 
               "Total cache overhead: " + totalCacheOverhead + " bytes");
    
    for (CdnJarzClassLoader loader : loaders) {
        loader.close();
    }
}
```

### Cache Performance Tests
```java
@Test
void testSharedCacheHitRates() {
    String sameUrl = "https://cdn.example.com/app.jarz";
    
    CdnJarzClassLoader loader1 = new CdnJarzClassLoader(sameUrl);
    CdnJarzClassLoader loader2 = new CdnJarzClassLoader(sameUrl);
    
    // Simulate loader1 populating cache
    try {
        loader1.loadClass("com.example.CommonClass");
    } catch (Exception e) {
        // Expected for mock scenario
    }
    
    // Simulate loader2 benefiting from populated cache
    SharedBlockCache cache = (SharedBlockCache) getPrivateField(loader2, "sharedBlockCache");
    CacheStats statsBefore = cache.getStats();
    
    try {
        loader2.loadClass("com.example.CommonClass");
    } catch (Exception e) {
        // Expected for mock scenario
    }
    
    CacheStats statsAfter = cache.getStats();
    
    // Should show improved hit rate
    assertTrue(statsAfter.hitRate() > statsBefore.hitRate(), 
               "Cache hit rate should improve with sharing");
    
    loader1.close();
    loader2.close();
}
```

## Success Criteria

### Memory Targets
- **Cache overhead reduction**: 512KB → <20KB per ClassLoader
- **Enterprise scenarios**: 500MB+ savings for 1000 ClassLoaders
- **Shared cache efficiency**: <10MB total cache overhead per unique URL

### Performance Targets
- **Cache hit rate improvement**: 50%+ increase due to larger shared caches
- **Memory utilization**: 95%+ reduction in cache memory overhead
- **Access latency**: No degradation in cache access performance

### Compatibility Targets
- **API compatibility**: No breaking changes to cache interface
- **CDN compatibility**: All providers continue working with shared caches
- **Concurrent access**: Thread-safe shared cache operations

## Implementation Timeline

1. **Week 1**: Implement BlockCachePool with reference counting
2. **Week 2**: Enhance BlockCache for sharing and statistics
3. **Week 3**: Integrate with CDN ClassLoader
4. **Week 4**: Performance testing and cache tuning

## Next Phase Integration

Phase 3 provides the foundation for Phase 4 (Flyweight Pattern) by establishing shared resource patterns and demonstrating the benefits of resource sharing, making index and metadata sharing natural extensions of the optimization strategy.
