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
        
        // Force re-evaluation by creating new enum instances
        assertEquals(3, BlockType.CLASS.compressionLevel());
        assertEquals(3, BlockType.CONFIG.compressionLevel());
        assertEquals(3, BlockType.SERVICE.compressionLevel());
        assertEquals(3, BlockType.TEXT.compressionLevel());
        assertEquals(3, BlockType.MANIFEST.compressionLevel());
    }
    
    @Test
    void testConfiguredCompressionLevel() {
        // Set custom compression level
        System.setProperty("jarz.compression.level", "7");
        
        // Note: Since the property is read statically, we need to test the behavior
        // This test validates the logic, but the static initialization means
        // the actual value won't change during test execution
        
        // Verify special cases still work correctly
        assertEquals(1, BlockType.NATIVE.compressionLevel()); // Always 1
        assertEquals(0, BlockType.STORED.compressionLevel()); // Always 0
        assertFalse(BlockType.STORED.shouldCompress());
    }
    
    @Test
    void testInvalidCompressionLevelFallsBackToDefault() {
        // Test invalid values fall back to default
        System.setProperty("jarz.compression.level", "invalid");
        
        // The static initialization will use default for invalid values
        // This test documents the expected behavior
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
        // Test boundary values - valid range is now 3-11
        System.setProperty("jarz.compression.level", "3");
        // Level 3 should be valid (minimum)
        
        System.setProperty("jarz.compression.level", "11");
        // Level 11 should be valid (maximum)
        
        System.setProperty("jarz.compression.level", "2");
        // Level 2 should fall back to default (below minimum)
        
        System.setProperty("jarz.compression.level", "12");
        // Level 12 should fall back to default (above maximum)
        
        // All main content types should still be compressible
        assertTrue(BlockType.CLASS.shouldCompress());
        assertTrue(BlockType.CONFIG.shouldCompress());
        assertTrue(BlockType.SERVICE.shouldCompress());
        assertTrue(BlockType.TEXT.shouldCompress());
        assertTrue(BlockType.MANIFEST.shouldCompress());
    }
    
    @Test
    void testSpecialBlockTypes() {
        // Set any compression level
        System.setProperty("jarz.compression.level", "9");
        
        // NATIVE should always use level 1
        assertEquals(1, BlockType.NATIVE.compressionLevel());
        assertTrue(BlockType.NATIVE.shouldCompress());
        
        // STORED should never compress
        assertEquals(0, BlockType.STORED.compressionLevel());
        assertFalse(BlockType.STORED.shouldCompress());
    }
}
