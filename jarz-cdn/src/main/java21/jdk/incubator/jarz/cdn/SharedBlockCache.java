/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package jdk.incubator.jarz.cdn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced BlockCache with sharing capabilities and statistics tracking.
 * 
 * <p>This cache extends the basic BlockCache functionality with:
 * - User tracking for shared usage
 * - Thread-safe hit/miss statistics
 * - Memory usage tracking
 * 
 * @since 1.0
 */
final class SharedBlockCache {
    
    private final Map<Integer, byte[]> cache;
    private final int maxSize;
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicInteger activeUsers = new AtomicInteger(0);
    
    /**
     * Creates a shared BlockCache with the specified maximum number of blocks.
     * 
     * @param maxBlocks maximum number of blocks to cache
     */
    SharedBlockCache(int maxBlocks) {
        this.maxSize = maxBlocks;
        this.cache = new ConcurrentHashMap<>();
    }
    
    /**
     * Gets a block from the cache.
     * 
     * @param blockId the block ID
     * @return block data or null if not cached
     */
    byte[] get(int blockId) {
        byte[] result = cache.get(blockId);
        if (result != null) {
            hits.incrementAndGet();
        } else {
            misses.incrementAndGet();
        }
        return result;
    }
    
    /**
     * Puts a block into the cache.
     * 
     * @param blockId the block ID
     * @param data the block data
     */
    void put(int blockId, byte[] data) {
        if (cache.size() >= maxSize) {
            // Simple eviction: remove first entry (not true LRU, but simple and thread-safe)
            cache.keySet().stream().findFirst().ifPresent(cache::remove);
        }
        cache.put(blockId, data);
    }
    
    /**
     * Clears the cache.
     */
    void clear() {
        cache.clear();
    }
    
    /**
     * Returns the number of cached blocks.
     */
    int size() {
        return cache.size();
    }
    
    /**
     * Adds a user to this shared cache.
     */
    void addUser() {
        activeUsers.incrementAndGet();
    }
    
    /**
     * Removes a user from this shared cache.
     */
    void removeUser() {
        activeUsers.decrementAndGet();
    }
    
    /**
     * Returns cache statistics.
     * 
     * @return current cache statistics
     */
    CacheStats getStats() {
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
    
    /**
     * Returns basic cache statistics compatible with original BlockCache.
     */
    Map<String, Long> getBasicStats() {
        return Map.of(
            "hits", hits.get(),
            "misses", misses.get(),
            "size", (long) cache.size()
        );
    }
    
    /**
     * Estimates memory usage of cached blocks.
     * 
     * @return estimated memory usage in bytes
     */
    long getMemoryUsage() {
        return cache.values().stream().mapToLong(bytes -> bytes.length).sum();
    }
    
    /**
     * Cache statistics record.
     */
    static record CacheStats(
        long hits,
        long misses, 
        double hitRate,
        int activeUsers,
        int cachedBlocks,
        long memoryUsage
    ) {}
}
