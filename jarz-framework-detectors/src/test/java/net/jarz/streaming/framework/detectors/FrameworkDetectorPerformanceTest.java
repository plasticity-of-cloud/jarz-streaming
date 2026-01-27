package net.jarz.streaming.framework.detectors;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for framework detector registry.
 */
class FrameworkDetectorPerformanceTest {
    
    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }
    
    @Test
    void testDetectionPerformance() {
        String[] testClasses = {
            "org.apache.flink.streaming.api.StreamExecutionEnvironment",
            "org.apache.spark.sql.SparkSession", 
            "org.springframework.boot.SpringApplication",
            "org.apache.hadoop.hdfs.DistributedFileSystem",
            "com.example.MyClass",
            "java.lang.String",
            "javax.servlet.http.HttpServlet"
        };
        
        // Warmup
        for (int i = 0; i < 1000; i++) {
            for (String className : testClasses) {
                registry.detectFramework(className);
            }
        }
        
        // Performance test
        long startTime = System.nanoTime();
        int iterations = 10000;
        
        for (int i = 0; i < iterations; i++) {
            for (String className : testClasses) {
                registry.detectFramework(className);
            }
        }
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        double avgTimePerDetection = (double) totalTime / (iterations * testClasses.length);
        
        System.out.printf("Framework detection performance:%n");
        System.out.printf("Total detections: %d%n", iterations * testClasses.length);
        System.out.printf("Total time: %.2f ms%n", totalTime / 1_000_000.0);
        System.out.printf("Average time per detection: %.3f μs%n", avgTimePerDetection / 1000.0);
        
        // Assert reasonable performance (less than 10 microseconds per detection)
        assertTrue(avgTimePerDetection < 10_000, 
            "Framework detection too slow: " + (avgTimePerDetection / 1000.0) + " μs");
    }
    
    @Test
    void testMemoryUsage() {
        // Test that registry doesn't leak memory
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Create multiple registries
        for (int i = 0; i < 100; i++) {
            FrameworkDetectorRegistry testRegistry = new FrameworkDetectorRegistry();
            testRegistry.detectFramework("org.apache.flink.streaming.api.StreamExecutionEnvironment");
        }
        
        runtime.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;
        
        System.out.printf("Memory usage test:%n");
        System.out.printf("Memory before: %d bytes%n", memoryBefore);
        System.out.printf("Memory after: %d bytes%n", memoryAfter);
        System.out.printf("Memory used: %d bytes%n", memoryUsed);
        
        // Assert reasonable memory usage (less than 1MB for 100 registries)
        assertTrue(memoryUsed < 1_000_000, 
            "Excessive memory usage: " + memoryUsed + " bytes");
    }
}
