package jdk.incubator.jarz;

import jdk.incubator.jarz.v2.BlockWriter;
import jdk.incubator.jarz.v2.BlockReader;
import jdk.incubator.jarz.v2.Block;
import jdk.incubator.jarz.v2.BlockType;
import jdk.incubator.jarz.v2.TypedBlock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for complete JARZ v2 workflows.
 */
class JarzV2IntegrationTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testCompleteWorkflow() throws Exception {
        Path jarzFile = tempDir.resolve("app.jarz");
        
        // Create test data
        byte[] class1 = createClassFile("Class1", 10_000);
        byte[] class2 = createClassFile("Class2", 20_000);
        byte[] class3 = createClassFile("Class3", 15_000);
        
        // Write JARZ v2
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block classBlock = new Block(1);
            classBlock.add("com.example.Class1", class1);
            classBlock.add("com.example.Class2", class2);
            classBlock.add("com.example.Class3", class3);
            writer.writeBlock(classBlock);
        }
        
        // Verify file exists
        assertThat(jarzFile).exists();
        
        // Read and verify using BlockReader
        try (BlockReader reader = new BlockReader(jarzFile)) {
            byte[] read1 = reader.readClass("com.example.Class1");
            byte[] read2 = reader.readClass("com.example.Class2");
            byte[] read3 = reader.readClass("com.example.Class3");
            
            assertThat(read1).isEqualTo(class1);
            assertThat(read2).isEqualTo(class2);
            assertThat(read3).isEqualTo(class3);
        }
    }
    
    @Test
    void testLargeArchive() throws Exception {
        Path jarzFile = tempDir.resolve("large.jarz");
        
        // Create 1000 classes
        List<String> names = new ArrayList<>();
        List<byte[]> data = new ArrayList<>();
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            // Create multiple blocks for better compression
            for (int blockId = 0; blockId < 10; blockId++) {
                Block classBlock = new Block(blockId + 1);
                
                for (int i = 0; i < 100; i++) {
                    int classIndex = blockId * 100 + i;
                    String className = String.format("com.example.Class%04d", classIndex);
                    byte[] classData = createClassFile("Class" + classIndex, 5000 + (classIndex % 1000));
                    
                    names.add(className);
                    data.add(classData);
                    classBlock.add(className, classData);
                }
                
                writer.writeBlock(classBlock);
            }
        }
        
        // Verify all entries
        try (BlockReader reader = new BlockReader(jarzFile)) {
            for (int i = 0; i < 1000; i++) {
                byte[] read = reader.readClass(names.get(i));
                assertThat(read).isEqualTo(data.get(i));
            }
        }
    }
    
    @Test
    void testRandomAccess() throws Exception {
        Path jarzFile = tempDir.resolve("random.jarz");
        
        // Create archive
        byte[] class1 = createClassFile("Class1", 10_000);
        byte[] class2 = createClassFile("Class2", 20_000);
        byte[] class3 = createClassFile("Class3", 15_000);
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block classBlock = new Block(1);
            classBlock.add("a.Class1", class1);
            classBlock.add("b.Class2", class2);
            classBlock.add("c.Class3", class3);
            writer.writeBlock(classBlock);
        }
        
        // Read in random order
        try (BlockReader reader = new BlockReader(jarzFile)) {
            byte[] read3 = reader.readClass("c.Class3");
            byte[] read1 = reader.readClass("a.Class1");
            byte[] read2 = reader.readClass("b.Class2");
            
            assertThat(read1).isEqualTo(class1);
            assertThat(read2).isEqualTo(class2);
            assertThat(read3).isEqualTo(class3);
        }
    }
    
    @Test
    void testEmptyEntries() throws Exception {
        Path jarzFile = tempDir.resolve("empty.jarz");
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block classBlock = new Block(1);
            classBlock.add("Empty1", new byte[0]);
            classBlock.add("Empty2", new byte[0]);
            writer.writeBlock(classBlock);
        }
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.readClass("Empty1")).isEmpty();
            assertThat(reader.readClass("Empty2")).isEmpty();
        }
    }
    
    @Test
    void testMixedBlockTypes() throws Exception {
        Path jarzFile = tempDir.resolve("mixed.jarz");
        
        byte[] classData = createClassFile("TestClass", 5000);
        byte[] manifestData = "Manifest-Version: 1.0\nMain-Class: TestClass\n".getBytes();
        byte[] resourceData = "This is a resource file".getBytes();
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            // Manifest block
            TypedBlock manifestBlock = new TypedBlock(1, BlockType.MANIFEST);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestData);
            writer.writeTypedBlock(manifestBlock);
            
            // Class block
            Block classBlock = new Block(2);
            classBlock.add("TestClass", classData);
            writer.writeBlock(classBlock);
            
            // Resource block
            TypedBlock resourceBlock = new TypedBlock(3, BlockType.CONFIG);
            resourceBlock.add("config.properties", resourceData);
            writer.writeTypedBlock(resourceBlock);
        }
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.readEntry("META-INF/MANIFEST.MF")).isEqualTo(manifestData);
            assertThat(reader.readClass("TestClass")).isEqualTo(classData);
            assertThat(reader.readEntry("config.properties")).isEqualTo(resourceData);
        }
    }
    
    @Test
    void testConcurrentReads() throws Exception {
        Path jarzFile = tempDir.resolve("concurrent.jarz");
        
        // Create archive
        byte[] testData = createClassFile("Test", 50_000);
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block classBlock = new Block(1);
            for (int i = 0; i < 100; i++) {
                classBlock.add("Class" + i, testData);
            }
            writer.writeBlock(classBlock);
        }
        
        // Multiple readers
        List<Thread> threads = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        
        for (int t = 0; t < 10; t++) {
            Thread thread = new Thread(() -> {
                try (BlockReader reader = new BlockReader(jarzFile)) {
                    for (int i = 0; i < 100; i++) {
                        byte[] data = reader.readClass("Class" + i);
                        assertThat(data).isEqualTo(testData);
                    }
                } catch (Exception e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertThat(errors).isEmpty();
    }
    
    @Test
    void testCompressionEfficiency() throws Exception {
        Path jarzFile = tempDir.resolve("compressed.jarz");
        
        // Create repetitive data that should compress well
        byte[] repetitiveData = new byte[100_000];
        for (int i = 0; i < repetitiveData.length; i++) {
            repetitiveData[i] = (byte) (i % 256);
        }
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block classBlock = new Block(1);
            for (int i = 0; i < 10; i++) {
                classBlock.add("RepetitiveClass" + i, repetitiveData);
            }
            writer.writeBlock(classBlock);
        }
        
        // Verify compression worked
        long compressedSize = Files.size(jarzFile);
        long uncompressedSize = repetitiveData.length * 10;
        
        // Should achieve significant compression on repetitive data
        assertThat(compressedSize).isLessThan(uncompressedSize / 10);
        
        // Verify data integrity
        try (BlockReader reader = new BlockReader(jarzFile)) {
            for (int i = 0; i < 10; i++) {
                byte[] read = reader.readClass("RepetitiveClass" + i);
                assertThat(read).isEqualTo(repetitiveData);
            }
        }
    }
    
    private byte[] createClassFile(String name, int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        
        byte[] nameBytes = name.getBytes();
        System.arraycopy(nameBytes, 0, data, 4, Math.min(nameBytes.length, size - 4));
        
        for (int i = 4 + nameBytes.length; i < size; i++) {
            data[i] = (byte) ((i * 31 + name.hashCode()) % 256);
        }
        
        return data;
    }
}
