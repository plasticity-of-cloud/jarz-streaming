package net.jarz.streaming.framework.detectors.oracle;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OciSdkFrameworkDetector.
 */
class OciSdkFrameworkDetectorTest {
    
    private final OciSdkFrameworkDetector detector = new OciSdkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        assertTrue(detector.canHandle("com.oracle.bmc.objectstorage.ObjectStorageClient"));
        assertTrue(detector.canHandle("com.oracle.bmc.database.DatabaseClient"));
        assertTrue(detector.canHandle("com.oracle.oci.compute.ComputeClient"));
        assertFalse(detector.canHandle("com.amazonaws.services.s3.AmazonS3Client"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        assertEquals("oci-objectstorage", detector.detectModule("com.oracle.bmc.objectstorage.ObjectStorageClient"));
        assertEquals("oci-database", detector.detectModule("com.oracle.bmc.database.DatabaseClient"));
        assertEquals("oci-compute", detector.detectModule("com.oracle.bmc.compute.ComputeClient"));
        assertEquals("oci-identity", detector.detectModule("com.oracle.bmc.identity.IdentityClient"));
        assertEquals("oci-loadbalancer", detector.detectModule("com.oracle.bmc.loadbalancer.LoadBalancerClient"));
        assertEquals("oci-networking", detector.detectModule("com.oracle.bmc.networking.NetworkClient"));
        assertEquals("oci-functions", detector.detectModule("com.oracle.bmc.functions.FunctionsManagementClient"));
        assertEquals("oci-containerengine", detector.detectModule("com.oracle.bmc.containerengine.ContainerEngineClient"));
        assertEquals("oci-core", detector.detectModule("com.oracle.bmc.ConfigFileReader"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
