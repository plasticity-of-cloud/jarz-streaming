package net.jarz.streaming.framework.detectors.flink;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FlinkFrameworkDetector.
 */
class FlinkFrameworkDetectorTest {
    
    private final FlinkFrameworkDetector detector = new FlinkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        assertTrue(detector.canHandle("org.apache.flink.streaming.api.StreamExecutionEnvironment"));
        assertTrue(detector.canHandle("org.apache.flink.table.api.TableEnvironment"));
        assertFalse(detector.canHandle("org.apache.spark.SparkContext"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        assertEquals("flink-streaming", detector.detectModule("org.apache.flink.streaming.api.StreamExecutionEnvironment"));
        assertEquals("flink-table", detector.detectModule("org.apache.flink.table.api.TableEnvironment"));
        assertEquals("flink-connector", detector.detectModule("org.apache.flink.connector.kafka.source.KafkaSource"));
        assertEquals("flink-runtime", detector.detectModule("org.apache.flink.runtime.jobgraph.JobGraph"));
        assertEquals("flink-core", detector.detectModule("org.apache.flink.api.common.functions.MapFunction"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
