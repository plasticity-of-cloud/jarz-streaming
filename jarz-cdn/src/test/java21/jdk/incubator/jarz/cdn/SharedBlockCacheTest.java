package jdk.incubator.jarz.cdn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SharedBlockCache functionality.
 */
class SharedBlockCacheTest {
    
    private SharedBlockCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new SharedBlockCache(4); // Small cache for testing
    }
    
    @Test
    void testBasicCacheOperations() {
        byte[] data1 = "block1".getBytes();
        byte[] data2 = "block2".getBytes();
        
        // Initially empty
        assertNull(cache.get(1));
        assertEquals(0, cache.size());
        
        // Put and get
        cache.put(1, data1);
        assertArrayEquals(data1, cache.get(1));
        assertEquals(1, cache.size());
        
        cache.put(2, data2);
        assertArrayEquals(data2, cache.get(2));
        assertEquals(2, cache.size());
    }
    
    @Test
    void testCacheEviction() {
        byte[] data = new byte[100];
        
        // Fill cache to capacity
        for (int i = 1; i <= 4; i++) {
            cache.put(i, data);
        }
        assertEquals(4, cache.size());
        
        // Add one more - should evict one entry
        cache.put(5, data);
        assertEquals(4, cache.size()); // Size should remain at max
    }
    
    @Test
    void testHitMissStatistics() {
        byte[] data = "test".getBytes();
        
        SharedBlockCache.CacheStats initialStats = cache.getStats();
        assertEquals(0, initialStats.hits());
        assertEquals(0, initialStats.misses());
        assertEquals(0.0, initialStats.hitRate());
        
        // Miss
        cache.get(1);
        SharedBlockCache.CacheStats afterMiss = cache.getStats();
        assertEquals(0, afterMiss.hits());
        assertEquals(1, afterMiss.misses());
        assertEquals(0.0, afterMiss.hitRate());
        
        // Put and hit
        cache.put(1, data);
        cache.get(1);
        SharedBlockCache.CacheStats afterHit = cache.getStats();
        assertEquals(1, afterHit.hits());
        assertEquals(1, afterHit.misses());
        assertEquals(0.5, afterHit.hitRate(), 0.01);
    }
    
    @Test
    void testUserTracking() {
        assertEquals(0, cache.getStats().activeUsers());
        
        cache.addUser();
        assertEquals(1, cache.getStats().activeUsers());
        
        cache.addUser();
        assertEquals(2, cache.getStats().activeUsers());
        
        cache.removeUser();
        assertEquals(1, cache.getStats().activeUsers());
        
        cache.removeUser();
        assertEquals(0, cache.getStats().activeUsers());
    }
    
    @Test
    void testMemoryUsageTracking() {
        byte[] smallData = "small".getBytes();
        byte[] largeData = new byte[1000];
        
        assertEquals(0, cache.getStats().memoryUsage());
        
        cache.put(1, smallData);
        assertTrue(cache.getStats().memoryUsage() > 0);
        long smallMemory = cache.getStats().memoryUsage();
        
        cache.put(2, largeData);
        assertTrue(cache.getStats().memoryUsage() > smallMemory);
    }
    
    @Test
    void testBasicStatsCompatibility() {
        byte[] data = "test".getBytes();
        
        // Test compatibility with original BlockCache stats format
        cache.put(1, data);
        cache.get(1); // hit
        cache.get(2); // miss
        
        var basicStats = cache.getBasicStats();
        assertEquals(1L, basicStats.get("hits"));
        assertEquals(1L, basicStats.get("misses"));
        assertEquals(1L, basicStats.get("size"));
    }
}
