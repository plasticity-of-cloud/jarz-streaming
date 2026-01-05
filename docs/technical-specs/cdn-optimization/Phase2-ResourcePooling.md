# CDN ClassLoader Phase 2: Resource Pooling

## Objective
Share HttpClient instances and connection resources across multiple CDN ClassLoaders to reduce memory overhead and improve HTTP/2 connection efficiency.

## Current Problem
Each CDN ClassLoader creates its own HttpClient with dedicated resources:
```java
// Each ClassLoader creates separate HttpClient
this.httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .executor(Executors.newVirtualThreadPerTaskExecutor())  // ~5KB per executor
    .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
    .build();  // ~25KB per HttpClient
```

**Impact**: ~30KB per ClassLoader for HTTP resources that could be shared.

## Solution Design

### HttpClient Pool
```java
public class HttpClientPool {
    private static final ConcurrentHashMap<String, PoolEntry> pool = new ConcurrentHashMap<>();
    private static final int MAX_CLIENTS_PER_HOST = 3;
    
    static class PoolEntry {
        final HttpClient client;
        final AtomicInteger refCount;
        final String hostKey;
        
        PoolEntry(HttpClient client, String hostKey) {
            this.client = client;
            this.refCount = new AtomicInteger(1);
            this.hostKey = hostKey;
        }
    }
    
    public static HttpClient acquire(String jarzUrl) {
        String hostKey = extractHost(jarzUrl);
        return pool.computeIfAbsent(hostKey, key -> {
            HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            return new PoolEntry(client, key);
        }).client;
    }
    
    public static void release(String jarzUrl) {
        String hostKey = extractHost(jarzUrl);
        PoolEntry entry = pool.get(hostKey);
        if (entry != null && entry.refCount.decrementAndGet() == 0) {
            pool.remove(hostKey);
            // HttpClient cleanup handled by GC
        }
    }
}
```

### Connection Pool Optimization
```java
public class CdnConnectionManager {
    private static final ConcurrentHashMap<String, ConnectionPool> connectionPools = new ConcurrentHashMap<>();
    
    static class ConnectionPool {
        final Semaphore connectionLimiter;
        final AtomicInteger activeConnections;
        
        ConnectionPool(int maxConnections) {
            this.connectionLimiter = new Semaphore(maxConnections);
            this.activeConnections = new AtomicInteger(0);
        }
    }
    
    public static ConnectionPool getConnectionPool(String hostKey) {
        return connectionPools.computeIfAbsent(hostKey, 
            key -> new ConnectionPool(10)); // 10 connections per host
    }
}
```

## Implementation Strategy

### Updated CDN ClassLoader
```java
public class CdnJarzClassLoader extends ClassLoader implements AutoCloseable {
    private final String jarzUrl;
    private final HttpClient sharedHttpClient;  // From pool
    private final ConnectionPool connectionPool;  // Shared per host
    
    public CdnJarzClassLoader(String jarzUrl, SignedUrlProvider signedUrlProvider, int cacheSize) {
        super(ClassLoader.getSystemClassLoader());
        this.jarzUrl = Objects.requireNonNull(jarzUrl, "jarzUrl must not be null");
        this.sharedHttpClient = HttpClientPool.acquire(jarzUrl);
        this.connectionPool = CdnConnectionManager.getConnectionPool(extractHost(jarzUrl));
        this.signedUrlProvider = signedUrlProvider;
        this.cacheSize = cacheSize;
    }
    
    @Override
    public void close() throws IOException {
        HttpClientPool.release(jarzUrl);
        // Connection pool cleanup handled automatically
    }
}
```

### HTTP Request Management
```java
private CompletableFuture<byte[]> fetchBlockAsync(int blockId, int offset, int length) {
    return connectionPool.connectionLimiter.acquire()
        .thenCompose(permit -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBlockUrl(blockId)))
                    .header("Range", String.format("bytes=%d-%d", offset, offset + length - 1))
                    .timeout(Duration.ofSeconds(30))
                    .build();
                
                return sharedHttpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(HttpResponse::body);
            } finally {
                connectionPool.connectionLimiter.release();
            }
        });
}
```

## Memory Impact

### Before Phase 2
- **Per ClassLoader**: 540KB (including 30KB HTTP resources)
- **1000 ClassLoaders**: 30MB HTTP overhead

### After Phase 2
- **Per ClassLoader**: 515KB (shared HTTP resources)
- **1000 ClassLoaders**: ~3MB HTTP overhead (shared across hosts)

### Enterprise Savings
- **Spark**: 27MB savings for 1000 ClassLoaders
- **JEE**: 13.5MB savings for 500 ClassLoaders
- **Connection efficiency**: Better HTTP/2 multiplexing

## HTTP/2 Optimization Benefits

### Connection Multiplexing
```java
// Single HTTP/2 connection per host can handle multiple ClassLoaders
// Before: 1000 ClassLoaders = 1000 HTTP connections
// After: 1000 ClassLoaders = ~10 HTTP connections (shared)
```

### Request Efficiency
- **Reduced connection overhead**: Fewer TCP handshakes
- **Better multiplexing**: Multiple requests per connection
- **Improved latency**: Connection reuse reduces setup time

## Implementation Risks

### Connection Limits
- **Risk**: CDN connection limits per client
- **Mitigation**: Configurable connection pool sizes per host

### Resource Cleanup
- **Risk**: HttpClient instances not properly released
- **Mitigation**: Reference counting with automatic cleanup

### Thread Safety
- **Risk**: Concurrent access to shared resources
- **Mitigation**: Thread-safe pools with proper synchronization

## Testing Strategy

### Pool Functionality Tests
```java
@Test
void testHttpClientPooling() {
    String url1 = "https://cdn.example.com/app1.jarz";
    String url2 = "https://cdn.example.com/app2.jarz";  // Same host
    
    HttpClient client1 = HttpClientPool.acquire(url1);
    HttpClient client2 = HttpClientPool.acquire(url2);
    
    // Should return same instance for same host
    assertSame(client1, client2);
    
    HttpClientPool.release(url1);
    HttpClientPool.release(url2);
}
```

### Memory Efficiency Tests
```java
@Test
void testSharedResourceMemoryUsage() {
    long baseline = getUsedMemory();
    
    // Create 100 ClassLoaders for same host
    CdnJarzClassLoader[] loaders = new CdnJarzClassLoader[100];
    for (int i = 0; i < 100; i++) {
        loaders[i] = new CdnJarzClassLoader("https://cdn.example.com/app" + i + ".jarz");
    }
    
    long afterCreation = getUsedMemory();
    long totalOverhead = afterCreation - baseline;
    
    // Should be much less than 100 × 30KB (individual HttpClients)
    assertTrue(totalOverhead < 100 * 10 * 1024, 
               "Total HTTP overhead: " + totalOverhead + " bytes");
}
```

### Connection Efficiency Tests
```java
@Test
void testConnectionPoolLimits() {
    CdnJarzClassLoader loader = new CdnJarzClassLoader("https://cdn.example.com/app.jarz");
    
    // Simulate concurrent requests
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
        futures.add(CompletableFuture.runAsync(() -> {
            try {
                loader.loadClass("com.example.Test" + i);
            } catch (Exception e) {
                // Expected for mock scenario
            }
        }));
    }
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    // Verify connection pool limits respected
    ConnectionPool pool = CdnConnectionManager.getConnectionPool("cdn.example.com");
    assertTrue(pool.activeConnections.get() <= 10, "Connection limit exceeded");
}
```

## Success Criteria

### Memory Targets
- **HTTP overhead reduction**: 30KB → 5KB per ClassLoader
- **Enterprise scenarios**: 25MB+ savings for 1000 ClassLoaders
- **Connection efficiency**: 90%+ reduction in HTTP connections

### Performance Targets
- **Connection reuse**: >80% of requests use existing connections
- **Latency improvement**: 20%+ reduction in average request time
- **Throughput**: No degradation in concurrent request handling

### Compatibility Targets
- **CDN compatibility**: All providers continue working
- **HTTP/2 support**: Full multiplexing capabilities maintained
- **Error handling**: Proper fallback for connection issues

## Implementation Timeline

1. **Week 1**: Implement HttpClientPool with reference counting
2. **Week 2**: Implement connection pool management
3. **Week 3**: Integrate with CDN ClassLoader
4. **Week 4**: Performance testing and optimization

## Next Phase Integration

Phase 2 provides the foundation for Phase 3 (Cache Optimization) by establishing shared resource patterns and reducing the overhead of individual ClassLoader instances, making cache sharing more effective.
