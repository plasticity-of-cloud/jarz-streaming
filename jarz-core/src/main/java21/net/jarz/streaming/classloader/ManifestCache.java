/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.BlockReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

/**
 * Cache for shared Manifest instances using flyweight pattern.
 * 
 * <p>This cache reduces memory overhead by sharing Manifest instances
 * across multiple ClassLoaders that use the same JARZ file.
 * 
 * <p>Memory savings: ~1KB per ClassLoader when multiple ClassLoaders
 * reference the same JARZ file.
 * 
 * @since 1.0
 */
final class ManifestCache {
    
    private static final ConcurrentHashMap<Path, Manifest> cache = new ConcurrentHashMap<>(4);
    
    /**
     * Returns a shared Manifest for the given JARZ file.
     * 
     * <p>Multiple calls with the same JARZ file path will return the same Manifest
     * instance, reducing memory overhead through the flyweight pattern.
     * 
     * @param jarzFile the JARZ file path
     * @param reader the BlockReader for reading manifest data
     * @return shared Manifest instance
     * @throws IOException if manifest cannot be read
     */
    static Manifest getManifest(Path jarzFile, BlockReader reader) throws IOException {
        try {
            return cache.computeIfAbsent(jarzFile, path -> {
                try {
                    byte[] manifestData = reader.readEntry("META-INF/MANIFEST.MF");
                    if (manifestData == null) {
                        return new Manifest(); // Empty manifest for library use
                    }
                    return new Manifest(new ByteArrayInputStream(manifestData));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read manifest from " + path, e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
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
