package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JARZ v2 ClassLoader implementation.
 * Tests basic class loading functionality with block-based archives.
 * Note: These tests focus on the core BlockReader functionality.
 * Full application ClassLoader tests with manifest support are in JarzApplicationClassLoaderTest.
 */
class JarzV2ClassLoaderTest {

    @Test
    void testBasicBlockReading(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("test-classes.jarz");
        
        // Create test classes
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/example/TestClass", generateTestClass("TestClass"));
        classes.put("com/example/AnotherClass", generateTestClass("AnotherClass"));
        
        // Write JARZ v2 archive (without manifest for basic test)
        createBasicJarzV2Archive(jarzFile, classes);
        
        // Test direct BlockReader access
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.classCount()).isEqualTo(2);
            
            byte[] testClassData = reader.readClass("com/example/TestClass");
            assertThat(testClassData).isNotNull();
            
            byte[] anotherClassData = reader.readClass("com/example/AnotherClass");
            assertThat(anotherClassData).isNotNull();
        }
    }
    
    @Test
    void testResourceReading(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("with-resources.jarz");
        
        Map<String, byte[]> entries = new HashMap<>();
        entries.put("com/example/TestClass", generateTestClass("TestClass"));
        entries.put("config/application.properties", "app.name=test\napp.version=1.0".getBytes());
        
        createBasicJarzV2Archive(jarzFile, entries);
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // Test class reading
            byte[] testClassData = reader.readClass("com/example/TestClass");
            assertThat(testClassData).isNotNull();
            
            // Test resource reading
            byte[] configData = reader.readClass("config/application.properties");
            assertThat(configData).isNotNull();
            
            String configContent = new String(configData);
            assertThat(configContent).contains("app.name=test");
        }
    }
    
    @Test
    void testMultipleBlocks(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("multi-block.jarz");
        
        // Create enough classes to span multiple blocks
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            classes.put("com/example/Class" + i, generateTestClass("Class" + i));
        }
        
        createBasicJarzV2Archive(jarzFile, classes);
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.classCount()).isEqualTo(50);
            
            // Test random access
            for (int i = 0; i < 50; i += 10) {
                byte[] classData = reader.readClass("com/example/Class" + i);
                assertThat(classData).isNotNull();
            }
        }
    }
    
    @Test
    void testNonExistentClass(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("empty.jarz");
        
        createBasicJarzV2Archive(jarzFile, new HashMap<>());
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            byte[] nonExistent = reader.readClass("com/example/NonExistent");
            assertThat(nonExistent).isNull();
        }
    }
    
    private void createBasicJarzV2Archive(Path jarzFile, Map<String, byte[]> entries) throws Exception {
        DependencyGraph graph = new DependencyGraph();
        entries.keySet().forEach(graph::addClass);
        
        BlockAssigner assigner = new BlockAssigner(32 * 1024, 64 * 1024);
        List<Block> blocks = assigner.assignBlocks(entries, graph);
        
        try (BlockWriter writer = new BlockWriter(jarzFile, 3)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
    }
    
    private byte[] generateTestClass(String className) {
        // Generate a minimal but valid class file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            // Class file magic
            baos.write(0xCA);
            baos.write(0xFE);
            baos.write(0xBA);
            baos.write(0xBE);
            
            // Version (Java 21)
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x00);
            baos.write(0x41);
            
            // Fill with pattern
            byte[] pattern = ("CLASS:" + className + ":").getBytes();
            for (int i = 0; i < 100; i++) {
                baos.write(pattern[i % pattern.length]);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test class", e);
        }
        
        return baos.toByteArray();
    }
}
