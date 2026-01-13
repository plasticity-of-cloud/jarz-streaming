package jdk.incubator.jarz.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configurable compression level via system property.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
class CompressionLevelConfigurationTest {
    
    private String originalProperty;
    
    @BeforeEach
    void setUp() {
        // Save original property value
        originalProperty = System.getProperty("jarz.compression.level");
    }
    
    @AfterEach
    void tearDown() {
        // Restore original property value
        if (originalProperty != null) {
            System.setProperty("jarz.compression.level", originalProperty);
        } else {
            System.clearProperty("jarz.compression.level");
        }
    }
    
    @Test
    void testDefaultCompressionLevel() {
        // Clear property to test default
        System.clearProperty("jarz.compression.level");
        
        // All compressible types should use default level (3)
        assertEquals(3, BlockType.CLASS.compressionLevel());
        assertEquals(3, BlockType.CONFIG.compressionLevel());
        assertEquals(3, BlockType.SERVICE.compressionLevel());
        assertEquals(3, BlockType.TEXT.compressionLevel());
        assertEquals(3, BlockType.MANIFEST.compressionLevel());
        assertEquals(3, BlockType.NATIVE.compressionLevel()); // Now uses configured level
    }
    
    @Test
    void testConfiguredCompressionLevel() {
        // Set custom compression level
        System.setProperty("jarz.compression.level", "7");
        
        // All compressible types should use configured level
        assertEquals(7, BlockType.CLASS.compressionLevel());
        assertEquals(7, BlockType.CONFIG.compressionLevel());
        assertEquals(7, BlockType.SERVICE.compressionLevel());
        assertEquals(7, BlockType.TEXT.compressionLevel());
        assertEquals(7, BlockType.MANIFEST.compressionLevel());
        assertEquals(7, BlockType.NATIVE.compressionLevel()); // Now respects system property
        
        // STORED should never compress
        assertEquals(0, BlockType.STORED.compressionLevel());
        assertFalse(BlockType.STORED.shouldCompress());
    }
    
    @Test
    void testNativeLibraryCompressionLevelOverride() {
        // Test that NATIVE type now respects system property
        System.setProperty("jarz.compression.level", "9");
        
        assertEquals(9, BlockType.NATIVE.compressionLevel());
        assertTrue(BlockType.NATIVE.shouldCompress());
        
        // Test with different level
        System.setProperty("jarz.compression.level", "5");
        assertEquals(5, BlockType.NATIVE.compressionLevel());
        assertTrue(BlockType.NATIVE.shouldCompress());
    }
    
    @Test
    void testInvalidCompressionLevelFallsBackToDefault() {
        // Test invalid values fall back to default
        System.setProperty("jarz.compression.level", "invalid");
        
        // All compressible types should fall back to default (3)
        assertEquals(3, BlockType.CLASS.compressionLevel());
        assertEquals(3, BlockType.CONFIG.compressionLevel());
        assertEquals(3, BlockType.SERVICE.compressionLevel());
        assertEquals(3, BlockType.TEXT.compressionLevel());
        assertEquals(3, BlockType.MANIFEST.compressionLevel());
        assertEquals(3, BlockType.NATIVE.compressionLevel());
        
        // Verify compression flags
        assertTrue(BlockType.CLASS.shouldCompress());
        assertTrue(BlockType.CONFIG.shouldCompress());
        assertTrue(BlockType.SERVICE.shouldCompress());
        assertTrue(BlockType.TEXT.shouldCompress());
        assertTrue(BlockType.MANIFEST.shouldCompress());
        assertTrue(BlockType.NATIVE.shouldCompress());
        assertFalse(BlockType.STORED.shouldCompress());
    }
    
    @Test
    void testCompressionLevelBoundaries() {
        // Test minimum valid level
        System.setProperty("jarz.compression.level", "3");
        assertEquals(3, BlockType.NATIVE.compressionLevel());
        
        // Test maximum valid level
        System.setProperty("jarz.compression.level", "11");
        assertEquals(11, BlockType.NATIVE.compressionLevel());
        
        // Test below minimum - should fall back to default
        System.setProperty("jarz.compression.level", "2");
        assertEquals(3, BlockType.NATIVE.compressionLevel());
        
        // Test above maximum - should fall back to default
        System.setProperty("jarz.compression.level", "12");
        assertEquals(3, BlockType.NATIVE.compressionLevel());
        
        // All main content types should still be compressible
        assertTrue(BlockType.CLASS.shouldCompress());
        assertTrue(BlockType.CONFIG.shouldCompress());
        assertTrue(BlockType.SERVICE.shouldCompress());
        assertTrue(BlockType.TEXT.shouldCompress());
        assertTrue(BlockType.MANIFEST.shouldCompress());
        assertTrue(BlockType.NATIVE.shouldCompress());
    }
    
    @Test
    void testStoredBlockTypeNeverCompresses() {
        // Set any compression level
        System.setProperty("jarz.compression.level", "9");
        
        // STORED should never compress regardless of system property
        assertEquals(0, BlockType.STORED.compressionLevel());
        assertFalse(BlockType.STORED.shouldCompress());
        
        // Test with different level
        System.setProperty("jarz.compression.level", "3");
        assertEquals(0, BlockType.STORED.compressionLevel());
        assertFalse(BlockType.STORED.shouldCompress());
    }
    
    @Test
    void testAllBlockTypesWithHighCompressionLevel() {
        // Test all block types with high compression level
        System.setProperty("jarz.compression.level", "11");
        
        assertEquals(11, BlockType.CLASS.compressionLevel());
        assertEquals(11, BlockType.CONFIG.compressionLevel());
        assertEquals(11, BlockType.SERVICE.compressionLevel());
        assertEquals(11, BlockType.TEXT.compressionLevel());
        assertEquals(11, BlockType.MANIFEST.compressionLevel());
        assertEquals(11, BlockType.NATIVE.compressionLevel()); // Key test: NATIVE now uses high compression
        assertEquals(0, BlockType.STORED.compressionLevel());  // Still never compresses
    }
}
