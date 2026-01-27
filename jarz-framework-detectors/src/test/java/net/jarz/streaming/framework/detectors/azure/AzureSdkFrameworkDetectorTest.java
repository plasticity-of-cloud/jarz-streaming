package net.jarz.streaming.framework.detectors.azure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AzureSdkFrameworkDetector.
 */
class AzureSdkFrameworkDetectorTest {
    
    private final AzureSdkFrameworkDetector detector = new AzureSdkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        assertTrue(detector.canHandle("com.azure.storage.blob.BlobServiceClient"));
        assertTrue(detector.canHandle("com.azure.keyvault.secrets.SecretClient"));
        assertTrue(detector.canHandle("com.azure.cosmos.CosmosClient"));
        assertFalse(detector.canHandle("com.amazonaws.services.s3.AmazonS3Client"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        assertEquals("azure-storage", detector.detectModule("com.azure.storage.blob.BlobServiceClient"));
        assertEquals("azure-keyvault", detector.detectModule("com.azure.keyvault.secrets.SecretClient"));
        assertEquals("azure-cosmos", detector.detectModule("com.azure.cosmos.CosmosClient"));
        assertEquals("azure-servicebus", detector.detectModule("com.azure.messaging.servicebus.ServiceBusClient"));
        assertEquals("azure-eventhubs", detector.detectModule("com.azure.messaging.eventhubs.EventHubClient"));
        assertEquals("azure-identity", detector.detectModule("com.azure.identity.DefaultAzureCredential"));
        assertEquals("azure-core", detector.detectModule("com.azure.core.http.HttpClient"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
