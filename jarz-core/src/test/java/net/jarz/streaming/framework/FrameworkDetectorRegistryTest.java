package net.jarz.streaming.framework;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FrameworkDetectorRegistry core functionality.
 */
class FrameworkDetectorRegistryTest {
    
    @Test
    void testRegistryWithNoDetectors() {
        FrameworkDetectorRegistry registry = new FrameworkDetectorRegistry();
        
        // Should fallback to package prefix when no detectors available
        String result = registry.detectFramework("com.example.MyClass");
        assertEquals("com", result);
    }
    
    @Test
    void testPackagePrefixFallback() {
        FrameworkDetectorRegistry registry = new FrameworkDetectorRegistry();
        
        // Test various package structures
        assertEquals("com", registry.detectFramework("com.example.test.MyClass"));
        assertEquals("org", registry.detectFramework("org.apache.spark.SparkContext"));
        assertEquals("default", registry.detectFramework("MyClass"));
        assertEquals("default", registry.detectFramework(""));
    }
}
