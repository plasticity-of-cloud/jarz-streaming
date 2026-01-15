package net.jarz.streaming.classloader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JarzClassLoader class name format conversion utilities.
 */
class ClassNameFormatTest {
    
    @Test
    @DisplayName("toIndexFormat converts binary name to index format")
    void testToIndexFormat() {
        StringBuilder sb = new StringBuilder();
        
        // Test basic conversion
        JarzClassLoader.toIndexFormat("com.example.MyClass", sb);
        assertEquals("com/example/MyClass.class", sb.toString());
        
        // Test single package
        JarzClassLoader.toIndexFormat("MyClass", sb);
        assertEquals("MyClass.class", sb.toString());
        
        // Test deep package
        JarzClassLoader.toIndexFormat("org.apache.logging.log4j.Level", sb);
        assertEquals("org/apache/logging/log4j/Level.class", sb.toString());
    }
    
    @Test
    @DisplayName("normalizeClassName converts index format to binary name")
    void testNormalizeClassName() {
        StringBuilder sb = new StringBuilder();
        
        // Test basic conversion
        JarzClassLoader.normalizeClassName("com/example/MyClass.class", sb);
        assertEquals("com.example.MyClass", sb.toString());
        
        // Test without .class extension
        JarzClassLoader.normalizeClassName("com/example/MyClass", sb);
        assertEquals("com.example.MyClass", sb.toString());
        
        // Test single class
        JarzClassLoader.normalizeClassName("MyClass.class", sb);
        assertEquals("MyClass", sb.toString());
        
        // Test deep package
        JarzClassLoader.normalizeClassName("org/apache/logging/log4j/Level.class", sb);
        assertEquals("org.apache.logging.log4j.Level", sb.toString());
    }
    
    @Test
    @DisplayName("toResourceFormat returns resource name unchanged")
    void testToResourceFormat() {
        assertEquals("META-INF/MANIFEST.MF", 
                    JarzClassLoader.toResourceFormat("META-INF/MANIFEST.MF"));
        assertEquals("config/application.properties", 
                    JarzClassLoader.toResourceFormat("config/application.properties"));
    }
    
    @Test
    @DisplayName("Round-trip conversion preserves class names")
    void testRoundTripConversion() {
        StringBuilder sb = new StringBuilder();
        String[] testNames = {
            "com.example.MyClass",
            "org.apache.logging.log4j.Level", 
            "MyClass",
            "a.b.c.d.e.VeryDeepClass"
        };
        
        for (String originalName : testNames) {
            // Binary -> Index -> Binary
            JarzClassLoader.toIndexFormat(originalName, sb);
            String indexFormat = sb.toString();
            
            JarzClassLoader.normalizeClassName(indexFormat, sb);
            String backToBinary = sb.toString();
            
            assertEquals(originalName, backToBinary, 
                "Round-trip failed for: " + originalName);
        }
    }
    
    @Test
    @DisplayName("StringBuilder reuse works correctly")
    void testStringBuilderReuse() {
        StringBuilder sb = new StringBuilder();
        
        // First use
        JarzClassLoader.toIndexFormat("com.example.First", sb);
        assertEquals("com/example/First.class", sb.toString());
        
        // Second use should clear and reuse
        JarzClassLoader.toIndexFormat("org.test.Second", sb);
        assertEquals("org/test/Second.class", sb.toString());
        
        // Third use with normalization
        JarzClassLoader.normalizeClassName("another/test/Third.class", sb);
        assertEquals("another.test.Third", sb.toString());
    }
}
