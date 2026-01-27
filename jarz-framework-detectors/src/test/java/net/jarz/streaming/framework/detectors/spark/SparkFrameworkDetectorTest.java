package net.jarz.streaming.framework.detectors.spark;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SparkFrameworkDetector.
 */
class SparkFrameworkDetectorTest {
    
    private final SparkFrameworkDetector detector = new SparkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        assertTrue(detector.canHandle("org.apache.spark.SparkContext"));
        assertTrue(detector.canHandle("org.apache.spark.sql.SparkSession"));
        assertFalse(detector.canHandle("org.apache.flink.streaming.api.StreamExecutionEnvironment"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        assertEquals("spark-sql", detector.detectModule("org.apache.spark.sql.SparkSession"));
        assertEquals("spark-streaming", detector.detectModule("org.apache.spark.streaming.StreamingContext"));
        assertEquals("spark-mllib", detector.detectModule("org.apache.spark.mllib.classification.LogisticRegression"));
        assertEquals("spark-graphx", detector.detectModule("org.apache.spark.graphx.Graph"));
        assertEquals("spark-core", detector.detectModule("org.apache.spark.SparkContext"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
