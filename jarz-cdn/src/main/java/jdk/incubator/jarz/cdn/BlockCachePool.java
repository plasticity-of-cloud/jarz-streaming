/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package jdk.incubator.jarz.cdn;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool for sharing BlockCache instances across CDN ClassLoaders accessing the same JARZ URLs.
 * 
 * <p>This pool reduces memory overhead by sharing BlockCache instances when multiple
 * ClassLoaders access the same CDN JARZ file. Memory savings: ~500KB per ClassLoader
 * when accessing shared URLs.
 * 
 * <p>Thread-safe with reference counting for automatic cleanup.
 * 
 * @since 1.0
 */
final class BlockCachePool {
    
    private static final ConcurrentHashMap<String, PoolEntry> cachePool = new ConcurrentHashMap<>(4);
    private static final int DEFAULT_SHARED_CACHE_SIZE = 256; // Larger shared cache for better hit rates
    
    static class PoolEntry {
        final SharedBlockCache cache;
        final AtomicInteger refCount;
        final String urlKey;
        
        PoolEntry(SharedBlockCache cache, String urlKey) {
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
    
    /**
     * Acquires a shared BlockCache for the given JARZ URL.
     * 
     * @param jarzUrl the CDN JARZ URL
     * @param requestedSize requested cache size (minimum)
     * @return shared BlockCache instance
     */
    static SharedBlockCache acquire(String jarzUrl, int requestedSize) {
        String urlKey = normalizeUrl(jarzUrl);
        
        return cachePool.compute(urlKey, (key, existing) -> {
            if (existing != null) {
                existing.incrementRef();
                return existing;
            } else {
                // Create larger shared cache for better hit rates
                int sharedSize = Math.max(requestedSize, DEFAULT_SHARED_CACHE_SIZE);
                SharedBlockCache cache = new SharedBlockCache(sharedSize);
                return new PoolEntry(cache, key);
            }
        }).cache;
    }
    
    /**
     * Releases a shared BlockCache for the given JARZ URL.
     * 
     * @param jarzUrl the CDN JARZ URL
     */
    static void release(String jarzUrl) {
        String urlKey = normalizeUrl(jarzUrl);
        
        cachePool.computeIfPresent(urlKey, (key, entry) -> {
            if (entry.decrementRef()) {
                // Last reference - remove from pool
                return null;
            }
            return entry;
        });
    }
    
    /**
     * Returns the number of cached BlockCache instances.
     * Used for testing and monitoring.
     */
    static int getCachePoolSize() {
        return cachePool.size();
    }
    
    /**
     * Clears the cache pool. Used for testing.
     */
    static void clearPool() {
        cachePool.clear();
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
