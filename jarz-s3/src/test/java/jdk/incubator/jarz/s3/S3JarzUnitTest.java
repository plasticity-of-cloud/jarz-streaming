package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.v2.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for S3JarzClassLoader using a fake S3 implementation.
 */
public class S3JarzUnitTest {
    
    private Path tempDir;
    private Path jarzFile;
    private byte[] jarzBytes;
    private Map<String, byte[]> testClasses;
    
    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("s3-unit-test");
        
        // Use a minimal valid JARZ file for testing
        // In a real test, you'd use actual JARZ bytes
        jarzBytes = createMinimalJarzBytes();
    }
    
    private byte[] createMinimalJarzBytes() throws IOException {
        // Create a proper JARZ file using the existing tools
        Path tempJar = tempDir.resolve("test.jar");
        Path tempJarz = tempDir.resolve("test.jarz");
        
        // Create a minimal JAR file first
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                Files.newOutputStream(tempJar))) {
            
            // Add a simple manifest
            java.util.jar.Manifest manifest = new java.util.jar.Manifest();
            manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
            
            jos.putNextEntry(new java.util.jar.JarEntry("META-INF/MANIFEST.MF"));
            manifest.write(jos);
            jos.closeEntry();
            
            // Add a simple class file
            jos.putNextEntry(new java.util.jar.JarEntry("com/example/Test.class"));
            jos.write(createSimpleClassBytes());
            jos.closeEntry();
        }
        
        // Convert JAR to JARZ using the converter
        JarToJarzConverter converter = new JarToJarzConverter();
        converter.convert(tempJar, tempJarz);
        
        return Files.readAllBytes(tempJarz);
    }
    
    private byte[] createSimpleClassBytes() {
        // Create minimal valid class file bytes
        // This is a very basic class file structure
        return new byte[]{
            (byte)0xCA, (byte)0xFE, (byte)0xBA, (byte)0xBE, // magic
            0x00, 0x00, 0x00, 0x3D, // version (Java 21)
            0x00, 0x05, // constant pool count
            0x01, 0x00, 0x04, 'T', 'e', 's', 't', // UTF8 "Test"
            0x07, 0x00, 0x01, // Class ref to #1
            0x01, 0x00, 0x10, 'j', 'a', 'v', 'a', '/', 'l', 'a', 'n', 'g', '/', 'O', 'b', 'j', 'e', 'c', 't', // UTF8 "java/lang/Object"
            0x07, 0x00, 0x03, // Class ref to #3
            0x00, 0x21, // access flags (public)
            0x00, 0x02, // this class
            0x00, 0x04, // super class
            0x00, 0x00, // interfaces count
            0x00, 0x00, // fields count
            0x00, 0x00, // methods count
            0x00, 0x00  // attributes count
        };
    }
    
    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.delete(path); } catch (IOException e) { /* ignore */ }
                });
        }
    }
    
    @Test
    void testS3ClientCreation() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        
        // Test that we can create the ClassLoader without errors
        assertDoesNotThrow(() -> {
            try (S3JarzClassLoader loader = new S3JarzClassLoader(
                    fakeS3, "test-bucket", "test.jarz")) {
                assertNotNull(loader);
            }
        });
    }
    
    @Test
    void testS3ErrorHandling() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(null); // Will cause errors
        
        assertThrows(Exception.class, () -> {
            new S3JarzClassLoader(fakeS3, "test-bucket", "nonexistent.jarz");
        });
    }
    
    private byte[] createTestClassBytes(String className) {
        // Create minimal valid class file bytes for testing
        // In real tests, you'd use actual compiled class bytes
        return ("// Fake class bytes for " + className).getBytes();
    }
    
    /**
     * Fake S3Client for testing without real S3.
     */
    private static class FakeS3Client implements S3Client {
        private final byte[] jarzData;
        
        public FakeS3Client(byte[] jarzData) {
            this.jarzData = jarzData;
        }
        
        @Override
        public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
            if (jarzData == null) {
                throw new RuntimeException("Simulated S3 error");
            }
            
            String range = request.range();
            byte[] responseData;
            
            if (range != null && range.startsWith("bytes=")) {
                // Parse range request - use long for large file offsets
                String[] parts = range.substring(6).split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? 
                    Long.parseLong(parts[1]) : jarzData.length - 1;
                
                // Validate range is within file bounds
                if (start >= jarzData.length || end >= jarzData.length || start > end) {
                    throw new RuntimeException("Range not satisfiable: " + range + " for file size " + jarzData.length);
                }
                
                responseData = Arrays.copyOfRange(jarzData, (int)start, (int)end + 1);
            } else {
                responseData = jarzData;
            }
            
            return new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(responseData)
            );
        }
        
        @Override
        public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
            if (jarzData == null) {
                throw new RuntimeException("Simulated S3 error");
            }
            
            String range = request.range();
            byte[] responseData;
            
            if (range != null && range.startsWith("bytes=")) {
                // Parse range request - use long for large file offsets
                String[] parts = range.substring(6).split("-");
                long start = Long.parseLong(parts[0]);
                long end = parts.length > 1 && !parts[1].isEmpty() ? 
                    Long.parseLong(parts[1]) : jarzData.length - 1;
                
                // Validate range is within file bounds
                if (start >= jarzData.length || end >= jarzData.length || start > end) {
                    throw new RuntimeException("Range not satisfiable: " + range + " for file size " + jarzData.length);
                }
                
                responseData = Arrays.copyOfRange(jarzData, (int)start, (int)end + 1);
            } else {
                responseData = jarzData;
            }
            
            return ResponseBytes.fromByteArray(
                GetObjectResponse.builder().build(), 
                responseData
            );
        }
        
        @Override
        public HeadObjectResponse headObject(HeadObjectRequest request) {
            if (jarzData == null) {
                throw new RuntimeException("Simulated S3 error");
            }
            return HeadObjectResponse.builder()
                .contentLength((long) jarzData.length)
                .build();
        }
        
        @Override
        public String serviceName() { return "s3"; }
        @Override
        public void close() {}
    }
}
