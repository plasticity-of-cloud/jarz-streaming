package jdk.incubator.jarz.s3;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to isolate constructor issue.
 */
public class SimpleConstructorTest {
    
    @Test
    void testJarzClassLoaderConstructor() throws Exception {
        // Create a fake S3 client that returns invalid JARZ data
        S3Client fakeS3 = new FakeS3Client(new byte[1024]); // Invalid JARZ format
        
        // The key test: verify the constructor exists and can be called
        // We expect it to fail on format validation, not on missing constructor
        IOException exception = assertThrows(IOException.class, () -> {
            try (S3JarzClassLoader loader = new S3JarzClassLoader(fakeS3, "test-bucket", "test-key")) {
                // Should not reach here due to invalid format
            }
        });
        
        // Verify it's a format error, not a constructor error
        assertTrue(exception.getMessage().contains("Invalid JARZ v2 magic") || 
                  exception.getMessage().contains("Failed to initialize"),
                  "Expected format validation error, got: " + exception.getMessage());
    }
    
    /**
     * Minimal fake S3 client for testing without real AWS credentials.
     */
    private static class FakeS3Client implements S3Client {
        private final byte[] jarzData;
        
        public FakeS3Client(byte[] jarzData) {
            this.jarzData = jarzData;
        }
        
        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(jarzData)
            );
        }
        
        @Override
        public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
            return ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(),
                jarzData
            );
        }
        
        @Override
        public String serviceName() {
            return "s3";
        }
        
        @Override
        public void close() {
            // no-op
        }
    }
}
