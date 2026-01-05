package jdk.incubator.jarz.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ManifestCache flyweight pattern.
 */
class ManifestCacheTest {
    
    @BeforeEach
    @AfterEach
    void cleanup() {
        ManifestCache.clearCache();
    }
    
    @Test
    void testCacheSize() {
        assertEquals(0, ManifestCache.getCacheSize());
    }
    
    @Test
    void testClearCache() {
        assertEquals(0, ManifestCache.getCacheSize());
        ManifestCache.clearCache();
        assertEquals(0, ManifestCache.getCacheSize());
    }
}
