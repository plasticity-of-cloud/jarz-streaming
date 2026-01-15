package net.jarz.streaming.v2;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Tests for JARZ v2 block-based format.
 */
public class JarzV2Test {
    
    private Path tempDir;
    
    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory("jarz-v2-test");
    }
    
    @AfterEach
    void cleanup() throws IOException {
        if (tempDir != null) {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        }
    }
    
    @Test
    void testBasicWriteRead() throws IOException {
        Path jarzFile = tempDir.resolve("test.jarz");
        
        // Create test classes
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/example/ClassA", generateClassData("ClassA", 1000));
        classes.put("com/example/ClassB", generateClassData("ClassB", 2000));
        classes.put("com/example/ClassC", generateClassData("ClassC", 1500));
        
        // Write
        try (BlockWriter writer = new BlockWriter(jarzFile, 9)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        // Read
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertEquals(1, reader.blockCount());
            assertEquals(3, reader.classCount());
            
            for (var entry : classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertNotNull(read, "Class not found: " + entry.getKey());
                assertArrayEquals(entry.getValue(), read);
            }
        }
    }
    
    @Test
    void testMultipleBlocks() throws IOException {
        Path jarzFile = tempDir.resolve("multi-block.jarz");
        
        // Create enough classes to span multiple blocks
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            classes.put("com/example/Class" + i, generateClassData("Class" + i, 10000));
        }
        
        // Assign to blocks
        BlockAssigner assigner = new BlockAssigner(100_000, 200_000); // 100KB target
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        assertTrue(blocks.size() > 1, "Should have multiple blocks");
        
        // Write
        try (BlockWriter writer = new BlockWriter(jarzFile, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        // Read and verify
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertEquals(blocks.size(), reader.blockCount());
            assertEquals(100, reader.classCount());
            
            // Verify all classes readable
            for (var entry : classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertNotNull(read);
                assertArrayEquals(entry.getValue(), read);
            }
        }
    }
    
    @Test
    void testDependencyBasedBlocking() throws IOException {
        // Create classes with dependencies
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/base/BaseClass", generateClassData("BaseClass", 5000));
        classes.put("com/base/SubClassA", generateClassData("SubClassA", 3000));
        classes.put("com/base/SubClassB", generateClassData("SubClassB", 3000));
        classes.put("com/other/Unrelated", generateClassData("Unrelated", 4000));
        
        // Build dependency graph
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com/base/SubClassA", "com/base/BaseClass");
        graph.addEdge("com/base/SubClassB", "com/base/BaseClass");
        graph.addClass("com/other/Unrelated");
        
        // Assign blocks - related classes should be together
        BlockAssigner assigner = new BlockAssigner(50_000, 100_000);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Verify BaseClass and SubClasses are in same block
        int baseBlockId = -1;
        int subABlockId = -1;
        int subBBlockId = -1;
        
        for (Block block : blocks) {
            for (Block.ClassEntry entry : block.entries()) {
                if (entry.className().equals("com/base/BaseClass")) baseBlockId = block.id();
                if (entry.className().equals("com/base/SubClassA")) subABlockId = block.id();
                if (entry.className().equals("com/base/SubClassB")) subBBlockId = block.id();
            }
        }
        
        assertEquals(baseBlockId, subABlockId, "SubClassA should be in same block as BaseClass");
        assertEquals(baseBlockId, subBBlockId, "SubClassB should be in same block as BaseClass");
    }
    
    @Test
    void testCompressionEffectiveness() throws IOException {
        Path jarzV2File = tempDir.resolve("test-v2.jarz");
        
        // Create realistic class data with repetitive patterns
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            classes.put("com/example/service/Service" + i, generateRealisticClassData(i, 8000));
        }
        
        // Write v2 (block compression)
        BlockAssigner assigner = new BlockAssigner();
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        try (BlockWriter writer = new BlockWriter(jarzV2File, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        long v2Size = Files.size(jarzV2File);
        long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
        
        double v2Ratio = (double) v2Size / originalSize * 100;
        
        System.out.printf("Original: %,d bytes%n", originalSize);
        System.out.printf("JARZ v2:  %,d bytes (%.1f%% of original)%n", v2Size, v2Ratio);
        
        // V2 should achieve reasonable compression
        assertTrue(v2Ratio < 80.0, "V2 should compress to less than 80% of original size");
    }
    
    @Test
    void testBlockCache() throws IOException {
        Path jarzFile = tempDir.resolve("cache-test.jarz");
        
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            classes.put("com/example/Class" + i, generateClassData("Class" + i, 5000));
        }
        
        // Write single block
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        // Read multiple times - should use cache
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // First read - loads block
            byte[] first = reader.readClass("com/example/Class0");
            assertNotNull(first);
            
            // Second read - should use cache
            byte[] second = reader.readClass("com/example/Class1");
            assertNotNull(second);
            
            // Clear cache and read again
            reader.clearCache();
            byte[] third = reader.readClass("com/example/Class2");
            assertNotNull(third);
        }
    }
    
    private byte[] generateClassData(String name, int size) {
        byte[] data = new byte[size];
        // Fill with semi-realistic pattern
        byte[] pattern = ("CLASS:" + name + ":").getBytes();
        for (int i = 0; i < size; i++) {
            data[i] = pattern[i % pattern.length];
        }
        return data;
    }
    
    private byte[] generateRealisticClassData(int index, int size) {
        // Generate data with patterns similar to real class files
        byte[] data = new byte[size];
        
        // Magic number
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        
        // Version
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 65; // Java 21
        
        // Fill rest with repetitive patterns (simulates constant pool, methods)
        String[] commonStrings = {
            "java/lang/Object", "java/lang/String", "<init>", "()V",
            "Code", "LineNumberTable", "LocalVariableTable", "this",
            "SourceFile", "Service" + index + ".java"
        };
        
        int pos = 8;
        int stringIdx = 0;
        while (pos < size - 20) {
            byte[] str = commonStrings[stringIdx % commonStrings.length].getBytes();
            int len = Math.min(str.length, size - pos - 2);
            data[pos++] = (byte) (len >> 8);
            data[pos++] = (byte) len;
            System.arraycopy(str, 0, data, pos, len);
            pos += len;
            stringIdx++;
        }
        
        return data;
    }
}
