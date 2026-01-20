package net.jarz.streaming.s3;

import net.jarz.streaming.v2.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Multi-backend S3 integration tests using test helpers.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
@Testcontainers
class MultiBackendS3IntegrationTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    @EnabledIfSystemProperty(named = "jarz.test.minio", matches = "true")
    void testMinioBackend() throws Exception {
        try (MinioTestHelper minio = new MinioTestHelper()) {
            minio.start();
            
            // Create test JARZ file
            Path jarzFile = createTestJarzFile();
            String key = "test/minio-test.jarz";
            
            // Upload to MinIO
            minio.uploadJarzFile(jarzFile, key);
            
            // Test S3 ClassLoader
            try (S3JarzClassLoader loader = new S3JarzClassLoader(
                    minio.getS3Client(), minio.getBucket(), key)) {
                
                // Test class loading
                Class<?> clazz = loader.loadClass("com.example.TestClass");
                assertThat(clazz).isNotNull();
                assertThat(clazz.getName()).isEqualTo("com.example.TestClass");
            }
        }
    }
    
    @Test
    @EnabledIfSystemProperty(named = "jarz.test.real-s3", matches = "true")
    void testRealS3Backend() throws Exception {
        try (S3TestHelper helper = new S3TestHelper(S3TestHelper.TestProfile.S3)) {
            if (!helper.isEnabled()) {
                return; // Skip if real S3 not configured
            }
            
            // Create test JARZ file
            Path jarzFile = createTestJarzFile();
            String key = "test/real-s3-test.jarz";
            
            // Upload to real S3
            helper.uploadJarzFile(jarzFile, key);
            
            // Test S3 ClassLoader
            try (S3JarzClassLoader loader = new S3JarzClassLoader(
                    helper.getS3Client(), helper.getBucket(), key)) {
                
                // Test class loading
                Class<?> clazz = loader.loadClass("com.example.TestClass");
                assertThat(clazz).isNotNull();
                assertThat(clazz.getName()).isEqualTo("com.example.TestClass");
            }
            
            // Cleanup
            helper.deleteObject(key);
        }
    }
    
    private Path createTestJarzFile() throws IOException {
        Path jarzFile = tempDir.resolve("test.jarz");
        
        // Create simple test class
        Map<String, byte[]> classes = new HashMap<>();
        String className = "com.example.TestClass";
        String classContent = "package com.example;\n" +
            "public class TestClass {\n" +
            "    public String getMessage() {\n" +
            "        return \"Hello from JARZ!\";\n" +
            "    }\n" +
            "}";
        
        // Simple bytecode for test class (minimal valid class file)
        byte[] bytecode = createMinimalBytecode(className);
        classes.put(className.replace('.', '/') + ".class", bytecode);
        
        // Create JARZ v2 file
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block = new Block(1);
            classes.forEach((name, data) -> block.add(name, data));
            writer.writeBlock(block);
        }
        
        return jarzFile;
    }
    
    private byte[] createMinimalBytecode(String className) {
        // Minimal valid Java class bytecode
        // This is a simplified version - in real tests you'd use ASM or compile actual classes
        return new byte[] {
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // Magic number
            0x00, 0x00, 0x00, 0x3D, // Version (Java 21)
            0x00, 0x0D, // Constant pool count
            // Minimal constant pool and class structure
            0x07, 0x00, 0x02, // Class info
            0x01, 0x00, 0x10, // UTF8 for class name
            'c', 'o', 'm', '/', 'e', 'x', 'a', 'm', 'p', 'l', 'e', '/', 'T', 'e', 's', 't',
            // ... (simplified for test purposes)
        };
    }
}
