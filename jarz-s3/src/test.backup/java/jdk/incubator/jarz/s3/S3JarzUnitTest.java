package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.v2.*;
import org.junit.jupiter.api.*;
import software.amazon.awssdk.core.ResponseBytes;
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
        
        // Create test classes
        testClasses = new HashMap<>();
        testClasses.put("com/example/TestClass.class", createTestClassBytes("TestClass"));
        testClasses.put("com/example/AnotherClass.class", createTestClassBytes("AnotherClass"));
        testClasses.put("com/example/util/Helper.class", createTestClassBytes("Helper"));
        
        // Create JARZ file
        jarzFile = tempDir.resolve("test.jarz");
        try (JarzWriter writer = new JarzWriter(jarzFile)) {
            for (Map.Entry<String, byte[]> entry : testClasses.entrySet()) {
                writer.addEntry(entry.getKey(), entry.getValue());
            }
            // Add a manifest
            writer.addEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nMain-Class: com.example.TestClass\n".getBytes());
        }
        
        jarzBytes = Files.readAllBytes(jarzFile);
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
    void testBasicClassLoading() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        
        try (S3JarzClassLoader loader = new S3JarzClassLoader(
                fakeS3, "test-bucket", "test.jarz")) {
            
            // Load a class
            Class<?> clazz = loader.loadClass("com.example.TestClass");
            assertNotNull(clazz);
            assertEquals("com.example.TestClass", clazz.getName());
            
            // Load another class
            Class<?> clazz2 = loader.loadClass("com.example.AnotherClass");
            assertNotNull(clazz2);
            assertEquals("com.example.AnotherClass", clazz2.getName());
        }
    }
    
    @Test
    void testClassNotFound() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        
        try (S3JarzClassLoader loader = new S3JarzClassLoader(
                fakeS3, "test-bucket", "test.jarz")) {
            
            assertThrows(ClassNotFoundException.class, () -> {
                loader.loadClass("com.example.NonExistentClass");
            });
        }
    }
    
    @Test
    void testManifestAccess() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        
        try (S3JarzClassLoader loader = new S3JarzClassLoader(
                fakeS3, "test-bucket", "test.jarz")) {
            
            Manifest manifest = loader.getManifest();
            assertNotNull(manifest);
            assertEquals("com.example.TestClass", 
                manifest.getMainAttributes().getValue("Main-Class"));
        }
    }
    
    @Test
    void testWithCustomParent() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        
        try (S3JarzClassLoader loader = new S3JarzClassLoader(
                fakeS3, "test-bucket", "test.jarz", parent)) {
            
            assertEquals(parent, loader.getParent());
            
            // Should still load JARZ classes
            Class<?> clazz = loader.loadClass("com.example.TestClass");
            assertNotNull(clazz);
        }
    }
    
    @Test
    void testMultipleClassLoaders() throws Exception {
        FakeS3Client fakeS3 = new FakeS3Client(jarzBytes);
        
        try (S3JarzClassLoader loader1 = new S3JarzClassLoader(fakeS3, "bucket1", "test1.jarz");
             S3JarzClassLoader loader2 = new S3JarzClassLoader(fakeS3, "bucket2", "test2.jarz")) {
            
            // Both should work independently
            Class<?> clazz1 = loader1.loadClass("com.example.TestClass");
            Class<?> clazz2 = loader2.loadClass("com.example.TestClass");
            
            assertNotNull(clazz1);
            assertNotNull(clazz2);
            // Classes from different loaders should be different
            assertNotSame(clazz1, clazz2);
        }
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
        public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
            if (jarzData == null) {
                throw new RuntimeException("Simulated S3 error");
            }
            
            String range = request.range();
            byte[] responseData;
            
            if (range != null && range.startsWith("bytes=")) {
                // Parse range request
                String[] parts = range.substring(6).split("-");
                int start = Integer.parseInt(parts[0]);
                int end = parts.length > 1 && !parts[1].isEmpty() ? 
                    Integer.parseInt(parts[1]) : jarzData.length - 1;
                
                responseData = Arrays.copyOfRange(jarzData, start, end + 1);
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
