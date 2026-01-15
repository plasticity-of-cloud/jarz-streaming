/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.cdn;

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
     * Cache statistics class.
     */
    static final class CacheStats {
        private final long hits;
        private final long misses;
        private final double hitRate;
        private final int activeUsers;
        private final int cachedBlocks;
        private final long memoryUsage;
        
        CacheStats(long hits, long misses, double hitRate, int activeUsers, int cachedBlocks, long memoryUsage) {
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.activeUsers = activeUsers;
            this.cachedBlocks = cachedBlocks;
            this.memoryUsage = memoryUsage;
        }
        
        public long hits() { return hits; }
        public long misses() { return misses; }
        public double hitRate() { return hitRate; }
        public int activeUsers() { return activeUsers; }
        public int cachedBlocks() { return cachedBlocks; }
        public long memoryUsage() { return memoryUsage; }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CacheStats)) return false;
            CacheStats other = (CacheStats) obj;
            return hits == other.hits && misses == other.misses && 
                   Double.compare(hitRate, other.hitRate) == 0 &&
                   activeUsers == other.activeUsers && cachedBlocks == other.cachedBlocks &&
                   memoryUsage == other.memoryUsage;
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(hits, misses, hitRate, activeUsers, cachedBlocks, memoryUsage);
        }
        
        @Override
        public String toString() {
            return "CacheStats[hits=" + hits + ", misses=" + misses + ", hitRate=" + hitRate + 
                   ", activeUsers=" + activeUsers + ", cachedBlocks=" + cachedBlocks + 
                   ", memoryUsage=" + memoryUsage + "]";
        }
    }
}
