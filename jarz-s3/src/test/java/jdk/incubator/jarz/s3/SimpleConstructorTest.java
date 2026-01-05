package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.JarzDataProvider;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test to isolate constructor issue.
 */
public class SimpleConstructorTest {
    
    @Test
    void testJarzClassLoaderConstructor() throws Exception {
        // Create a minimal data provider that will fail during initialization
        // but after the constructor signature is validated
        JarzDataProvider dataProvider = new JarzDataProvider() {
            @Override
            public byte[] readBytes(long offset, int length) throws IOException {
                // Return empty bytes to trigger format validation failure
                return new byte[length];
            }
            
            @Override
            public long getFileSize() throws IOException {
                return 1024;
            }
            
            @Override
            public void close() throws IOException {
                // no-op
            }
        };
        
        // The key test: verify the constructor exists and can be called
        // We expect it to fail on format validation, not on missing constructor
        IOException exception = assertThrows(IOException.class, () -> {
            try (JarzClassLoader loader = new JarzClassLoader(dataProvider, Thread.currentThread().getContextClassLoader())) {
                // Should not reach here due to invalid format
            }
        });
        
        // Verify it's a format error, not a constructor error
        assertTrue(exception.getMessage().contains("Invalid JARZ v2 magic") || 
                  exception.getMessage().contains("Failed to initialize"),
                  "Expected format validation error, got: " + exception.getMessage());
    }
}
