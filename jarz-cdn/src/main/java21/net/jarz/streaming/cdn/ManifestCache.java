/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.cdn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

/**
 * Cache for shared Manifest instances using flyweight pattern.
 * 
 * <p>This cache reduces memory overhead by sharing Manifest instances
 * across multiple ClassLoaders that use the same JARZ URL.
 * 
 * <p>Memory savings: ~1KB per ClassLoader when multiple ClassLoaders
 * reference the same JARZ file.
 * 
 * @since 1.0
 */
final class ManifestCache {
    
    private static final ConcurrentHashMap<String, Manifest> cache = new ConcurrentHashMap<>(4);
    
    /**
     * Returns a shared Manifest for the given JARZ URL.
     * 
     * <p>Multiple calls with the same URL will return the same Manifest
     * instance, reducing memory overhead through the flyweight pattern.
     * 
     * @param jarzUrl the JARZ URL
     * @param manifestBytes the manifest bytes to parse if not cached
     * @return shared Manifest instance, or null if manifestBytes is null
     * @throws IOException if manifest parsing fails
     */
    static Manifest getManifest(String jarzUrl, byte[] manifestBytes) throws IOException {
        if (manifestBytes == null) {
            return null;
        }
        
        return cache.computeIfAbsent(jarzUrl, url -> {
            try {
                return new Manifest(new ByteArrayInputStream(manifestBytes));
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse manifest for " + url, e);
            }
        });
    }
    
    /**
     * Returns the number of cached Manifest instances.
     * Used for testing and monitoring.
     */
    static int getCacheSize() {
        return cache.size();
    }
    
    /**
     * Clears the cache. Used for testing.
     */
    static void clearCache() {
        cache.clear();
    }
}
