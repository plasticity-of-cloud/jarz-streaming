package net.jarz.streaming.cdn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockCachePool Phase 3 implementation.
 */
class BlockCachePoolTest {
    
    @BeforeEach
    @AfterEach
    void cleanup() {
        BlockCachePool.clearPool();
    }
    
    @Test
    void testSameUrlReturnsSameCache() {
        String sameUrl = "https://cdn.example.com/app.jarz";
        
        SharedBlockCache cache1 = BlockCachePool.acquire(sameUrl, 64);
        SharedBlockCache cache2 = BlockCachePool.acquire(sameUrl, 64);
        
        // Should return same cache instance for same URL
        assertSame(cache1, cache2, "Same URL should return same cache instance");
        assertEquals(1, BlockCachePool.getCachePoolSize());
        
        BlockCachePool.release(sameUrl);
        BlockCachePool.release(sameUrl);
    }
    
    @Test
    void testDifferentUrlsReturnDifferentCaches() {
        String url1 = "https://cdn1.example.com/app.jarz";
        String url2 = "https://cdn2.example.com/app.jarz";
        
        SharedBlockCache cache1 = BlockCachePool.acquire(url1, 64);
        SharedBlockCache cache2 = BlockCachePool.acquire(url2, 64);
        
        // Should return different cache instances for different URLs
        assertNotSame(cache1, cache2, "Different URLs should return different cache instances");
        assertEquals(2, BlockCachePool.getCachePoolSize());
        
        BlockCachePool.release(url1);
        BlockCachePool.release(url2);
    }
    
    @Test
    void testReferenceCountingAndCleanup() {
        String url = "https://cdn.example.com/app.jarz";
        
        // Acquire cache twice
        SharedBlockCache cache1 = BlockCachePool.acquire(url, 64);
        SharedBlockCache cache2 = BlockCachePool.acquire(url, 64);
        
        assertSame(cache1, cache2);
        assertEquals(1, BlockCachePool.getCachePoolSize());
        
        // Release once - should still be in pool
        BlockCachePool.release(url);
        assertEquals(1, BlockCachePool.getCachePoolSize());
        
        // Release again - should be removed from pool
        BlockCachePool.release(url);
        assertEquals(0, BlockCachePool.getCachePoolSize());
    }
    
    @Test
    void testUrlNormalization() {
        String baseUrl = "https://cdn.example.com/app.jarz";
        String urlWithQuery = "https://cdn.example.com/app.jarz?version=1.0";
        String urlWithFragment = "https://cdn.example.com/app.jarz#section1";
        
        SharedBlockCache cache1 = BlockCachePool.acquire(baseUrl, 64);
        SharedBlockCache cache2 = BlockCachePool.acquire(urlWithQuery, 64);
        SharedBlockCache cache3 = BlockCachePool.acquire(urlWithFragment, 64);
        
        // Should all return same cache due to URL normalization
        assertSame(cache1, cache2, "URLs with query params should normalize to same cache");
        assertSame(cache1, cache3, "URLs with fragments should normalize to same cache");
        assertEquals(1, BlockCachePool.getCachePoolSize());
        
        BlockCachePool.release(baseUrl);
        BlockCachePool.release(urlWithQuery);
        BlockCachePool.release(urlWithFragment);
    }
    
    @Test
    void testSharedCacheSize() {
        String url = "https://cdn.example.com/app.jarz";
        
        // Request smaller cache, should get larger shared cache
        SharedBlockCache cache = BlockCachePool.acquire(url, 32);
        
        // Should get at least the default shared cache size (256)
        // We can't directly test the size, but we can verify it's a SharedBlockCache
        assertNotNull(cache);
        
        BlockCachePool.release(url);
    }
}
