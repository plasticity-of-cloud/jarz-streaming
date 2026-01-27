package net.jarz.streaming.framework.detectors.gcp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GcpSdkFrameworkDetector.
 */
class GcpSdkFrameworkDetectorTest {
    
    private final GcpSdkFrameworkDetector detector = new GcpSdkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        assertTrue(detector.canHandle("com.google.cloud.storage.Storage"));
        assertTrue(detector.canHandle("com.google.cloud.bigquery.BigQuery"));
        assertTrue(detector.canHandle("com.googleapis.services.compute.Compute"));
        assertFalse(detector.canHandle("com.amazonaws.services.s3.AmazonS3Client"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        assertEquals("gcp-storage", detector.detectModule("com.google.cloud.storage.Storage"));
        assertEquals("gcp-bigquery", detector.detectModule("com.google.cloud.bigquery.BigQuery"));
        assertEquals("gcp-pubsub", detector.detectModule("com.google.cloud.pubsub.v1.Publisher"));
        assertEquals("gcp-firestore", detector.detectModule("com.google.cloud.firestore.Firestore"));
        assertEquals("gcp-compute", detector.detectModule("com.googleapis.services.compute.Compute"));
        assertEquals("gcp-functions", detector.detectModule("com.google.cloud.functions.v1.CloudFunctionsServiceClient"));
        assertEquals("gcp-logging", detector.detectModule("com.google.cloud.logging.Logging"));
        assertEquals("gcp-core", detector.detectModule("com.google.cloud.ServiceOptions"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
