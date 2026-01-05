# CDN ClassLoader Phase 1: Lazy Initialization

## Objective
Defer expensive resource allocation (HttpClient, Index, BlockCache) until first actual use, reducing memory overhead for unused or short-lived CDN ClassLoaders.

## Current Problem
CDN ClassLoaders eagerly allocate all resources during construction:
```java
public CdnJarzClassLoader(String jarzUrl, SignedUrlProvider signedUrlProvider, int cacheSize) {
    // Eager allocation - always consumes memory
    this.httpClient = HttpClient.newBuilder()...build();
    this.blockCache = new BlockCache(cacheSize);  // 512KB immediately
    this.concurrencyLimiter = new Semaphore(...);
}
```

**Impact**: ~30KB overhead per ClassLoader even if never used for class loading.

## Solution Design

### Lazy HttpClient Allocation
```java
private volatile HttpClient httpClient;

private HttpClient getHttpClient() {
    if (httpClient == null) {
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_2)
                    .executor(Executors.newVirtualThreadPerTaskExecutor())
                    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                    .build();
            }
        }
    }
    return httpClient;
}
```

### Lazy BlockCache Allocation
```java
private volatile BlockCache blockCache;
private final int cacheSize;

private BlockCache getBlockCache() {
    if (blockCache == null) {
        synchronized (this) {
            if (blockCache == null) {
                blockCache = new BlockCache(cacheSize);
            }
        }
    }
    return blockCache;
}
```

### Lazy Index Loading
```java
private volatile JarzV2Index index;

private JarzV2Index getIndex() throws IOException {
    if (index == null) {
        synchronized (indexLock) {
            if (index == null) {
                index = loadIndexFromCdn();
            }
        }
    }
    return index;
}
```

## Implementation Strategy

### Constructor Changes
```java
public CdnJarzClassLoader(String jarzUrl, SignedUrlProvider signedUrlProvider, int cacheSize) {
    super(ClassLoader.getSystemClassLoader());
    this.jarzUrl = Objects.requireNonNull(jarzUrl, "jarzUrl must not be null");
    this.signedUrlProvider = signedUrlProvider;
    this.cacheSize = cacheSize;  // Store for lazy allocation
    // No eager resource allocation
}
```

### Usage Pattern Updates
```java
@Override
protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
        JarzV2Index idx = getIndex();  // Lazy load
        BlockCache cache = getBlockCache();  // Lazy load
        HttpClient client = getHttpClient();  // Lazy load
        
        // Use resources normally
        return loadClassFromCdn(name, idx, cache, client);
    } catch (IOException e) {
        throw new ClassNotFoundException("Failed to load: " + name, e);
    }
}
```

## Memory Impact

### Before Phase 1
- **Unused ClassLoader**: 540KB (full allocation)
- **Used ClassLoader**: 540KB (same allocation)

### After Phase 1
- **Unused ClassLoader**: ~10KB (minimal overhead)
- **Used ClassLoader**: 540KB (allocated on first use)

### Enterprise Savings
- **Spark**: 1000 unused task ClassLoaders = 530MB savings
- **JEE**: 500 unused web app ClassLoaders = 265MB savings
- **Development**: Significant reduction for test scenarios

## Implementation Risks

### Thread Safety
- **Risk**: Race conditions in lazy initialization
- **Mitigation**: Double-checked locking pattern with volatile fields

### Performance
- **Risk**: Synchronization overhead on first access
- **Mitigation**: One-time cost, subsequent accesses are lock-free

### Memory Visibility
- **Risk**: Visibility issues across threads
- **Mitigation**: Volatile fields ensure proper memory barriers

## Testing Strategy

### Unit Tests
```java
@Test
void testLazyHttpClientAllocation() {
    CdnJarzClassLoader loader = new CdnJarzClassLoader("https://cdn.example.com/app.jarz");
    // Verify HttpClient not allocated yet
    assertNull(getPrivateField(loader, "httpClient"));
    
    // Trigger allocation
    loader.findClass("com.example.Test");
    
    // Verify HttpClient now allocated
    assertNotNull(getPrivateField(loader, "httpClient"));
}
```

### Memory Tests
```java
@Test
void testMemoryUsageBeforeFirstUse() {
    long baseline = getUsedMemory();
    
    CdnJarzClassLoader[] loaders = new CdnJarzClassLoader[100];
    for (int i = 0; i < 100; i++) {
        loaders[i] = new CdnJarzClassLoader("https://cdn.example.com/app" + i + ".jarz");
    }
    
    long afterCreation = getUsedMemory();
    long memoryPerLoader = (afterCreation - baseline) / 100;
    
    // Should be <15KB per unused loader
    assertTrue(memoryPerLoader < 15 * 1024, 
               "Memory per unused loader: " + memoryPerLoader + " bytes");
}
```

## Success Criteria

### Memory Targets
- **Unused ClassLoader**: <15KB memory overhead
- **Used ClassLoader**: Same functionality as before
- **Enterprise scenarios**: 50%+ memory reduction for unused ClassLoaders

### Performance Targets
- **First access**: <10ms additional latency for resource allocation
- **Subsequent access**: No performance regression
- **Thread safety**: No race conditions under concurrent access

### Compatibility Targets
- **API compatibility**: No breaking changes to public interface
- **CDN compatibility**: All supported CDN providers continue working
- **Test coverage**: 100% test success rate maintained

## Implementation Timeline

1. **Week 1**: Implement lazy HttpClient allocation
2. **Week 2**: Implement lazy BlockCache allocation  
3. **Week 3**: Implement lazy Index loading
4. **Week 4**: Comprehensive testing and validation

## Next Phase Integration

Phase 1 provides the foundation for Phase 2 (Resource Pooling) by ensuring resources are only allocated when needed, making pooling more effective and reducing the number of resources that need to be managed.
