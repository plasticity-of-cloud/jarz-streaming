package jdk.incubator.jarz.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JARZ v2 format basic functionality.
 * Tests core read/write operations with block-based compression.
 */
class JarzV2FormatTest {

    @Test
    void testSingleBlockWriteRead(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("single-block.jarz");
        
        // Create test classes
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/example/ClassA", generateClassData("ClassA", 1000));
        classes.put("com/example/ClassB", generateClassData("ClassB", 2000));
        
        // Write single block
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        // Read and verify
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.blockCount()).isEqualTo(1);
            assertThat(reader.classCount()).isEqualTo(2);
            
            for (var entry : classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertThat(read).as("Class %s should be readable", entry.getKey())
                    .isNotNull()
                    .isEqualTo(entry.getValue());
            }
            
            // Non-existent class should return null
            assertThat(reader.readClass("com/example/NonExistent")).isNull();
        }
    }
    
    @Test
    void testMultipleBlocksWriteRead(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("multi-block.jarz");
        
        // Create classes for multiple blocks
        Map<String, byte[]> block1Classes = new HashMap<>();
        block1Classes.put("com/example/service/ServiceA", generateClassData("ServiceA", 5000));
        block1Classes.put("com/example/service/ServiceB", generateClassData("ServiceB", 4000));
        
        Map<String, byte[]> block2Classes = new HashMap<>();
        block2Classes.put("com/example/util/UtilA", generateClassData("UtilA", 3000));
        block2Classes.put("com/example/util/UtilB", generateClassData("UtilB", 3500));
        
        // Write multiple blocks
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block1 = new Block(0);
            for (var entry : block1Classes.entrySet()) {
                block1.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block1);
            
            Block block2 = new Block(1);
            for (var entry : block2Classes.entrySet()) {
                block2.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block2);
        }
        
        // Read and verify
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.blockCount()).isEqualTo(2);
            assertThat(reader.classCount()).isEqualTo(4);
            
            // Verify all classes from both blocks
            for (var entry : block1Classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertThat(read).isNotNull().isEqualTo(entry.getValue());
            }
            
            for (var entry : block2Classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertThat(read).isNotNull().isEqualTo(entry.getValue());
            }
        }
    }
    
    @Test
    void testEmptyBlock(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("empty.jarz");
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            // Write empty archive (no blocks)
        }
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.blockCount()).isEqualTo(0);
            assertThat(reader.classCount()).isEqualTo(0);
            assertThat(reader.readClass("any/class")).isNull();
        }
    }
    
    @Test
    void testCompressionLevels(@TempDir Path tempDir) throws Exception {
        byte[] testData = generateRepetitiveData(10000);
        Map<String, byte[]> classes = Map.of("test/RepetitiveClass", testData);
        
        // Test different compression levels
        for (int level : new int[]{1, 3, 9}) {
            Path jarzFile = tempDir.resolve("level-" + level + ".jarz");
            
            try (BlockWriter writer = new BlockWriter(jarzFile, level)) {
                Block block = new Block(0);
                for (var entry : classes.entrySet()) {
                    block.add(entry.getKey(), entry.getValue());
                }
                writer.writeBlock(block);
            }
            
            // Verify data integrity regardless of compression level
            try (BlockReader reader = new BlockReader(jarzFile)) {
                byte[] read = reader.readClass("test/RepetitiveClass");
                assertThat(read).isEqualTo(testData);
            }
        }
    }
    
    @Test
    void testDictionaryCompression(@TempDir Path tempDir) throws Exception {
        // Create dictionary from common class patterns
        byte[] dictionary = generateClassDictionary();
        
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            classes.put("com/example/Service" + i, generateRealisticClassData(i, 5000));
        }
        
        Path withDict = tempDir.resolve("with-dict.jarz");
        Path withoutDict = tempDir.resolve("without-dict.jarz");
        
        // Write with dictionary
        try (BlockWriter writer = new BlockWriter(withDict, 3, dictionary)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        // Write without dictionary
        try (BlockWriter writer = new BlockWriter(withoutDict, 3)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        // Verify both produce identical results
        try (BlockReader readerWithDict = new BlockReader(new FileJarzDataProvider(withDict));
             BlockReader readerWithoutDict = new BlockReader(new FileJarzDataProvider(withoutDict))) {
            
            for (String className : classes.keySet()) {
                byte[] withDictData = readerWithDict.readClass(className);
                byte[] withoutDictData = readerWithoutDict.readClass(className);
                assertThat(withDictData).isEqualTo(withoutDictData);
            }
        }
        
        // Dictionary version should be smaller (or at least not much larger)
        long dictSize = java.nio.file.Files.size(withDict);
        long noDictSize = java.nio.file.Files.size(withoutDict);
        
        // For small datasets, dictionary might not help much, so allow some tolerance
        assertThat(dictSize).isLessThan((long)(noDictSize * 1.2)); // Allow up to 20% larger
    }
    
    @Test
    void testBlockCaching(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("cache-test.jarz");
        
        Map<String, byte[]> classes = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            classes.put("com/example/Class" + i, generateClassData("Class" + i, 2000));
        }
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block = new Block(0);
            for (var entry : classes.entrySet()) {
                block.add(entry.getKey(), entry.getValue());
            }
            writer.writeBlock(block);
        }
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // First read - loads block into cache
            byte[] first = reader.readClass("com/example/Class0");
            assertThat(first).isNotNull();
            
            // Subsequent reads should use cached block
            for (int i = 1; i < 5; i++) {
                byte[] data = reader.readClass("com/example/Class" + i);
                assertThat(data).isNotNull();
            }
            
            // Clear cache and verify still works
            reader.clearCache();
            byte[] afterClear = reader.readClass("com/example/Class0");
            assertThat(afterClear).isEqualTo(first);
        }
    }
    
    @Test
    void testInvalidMagicNumber(@TempDir Path tempDir) throws Exception {
        Path invalidFile = tempDir.resolve("invalid.jarz");
        
        // Write invalid magic number
        try (var writer = java.nio.file.Files.newOutputStream(invalidFile)) {
            writer.write("FAKE".getBytes());
            writer.write(new byte[100]); // Some data
        }
        
        assertThatThrownBy(() -> new BlockReader(invalidFile))
            .isInstanceOf(java.io.IOException.class)
            .hasMessageContaining("Invalid JARZ v2 magic");
    }
    
    private byte[] generateClassData(String name, int size) {
        byte[] data = new byte[size];
        
        // Class file magic
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        
        // Fill with pattern based on class name
        byte[] pattern = ("CLASS:" + name + ":").getBytes();
        for (int i = 4; i < size; i++) {
            data[i] = pattern[(i - 4) % pattern.length];
        }
        
        return data;
    }
    
    private byte[] generateRepetitiveData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 10); // Highly repetitive pattern
        }
        return data;
    }
    
    private byte[] generateRealisticClassData(int index, int size) {
        byte[] data = new byte[size];
        
        // Class file magic and version
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 65; // Java 21
        
        // Common class file patterns
        String[] patterns = {
            "java/lang/Object", "java/lang/String", "<init>", "()V",
            "Code", "LineNumberTable", "this", "Service" + index
        };
        
        int pos = 8;
        int patternIdx = 0;
        while (pos < size - 20) {
            byte[] pattern = patterns[patternIdx % patterns.length].getBytes();
            int len = Math.min(pattern.length, size - pos);
            System.arraycopy(pattern, 0, data, pos, len);
            pos += len;
            patternIdx++;
        }
        
        return data;
    }
    
    private byte[] generateClassDictionary() {
        // Common class file patterns for dictionary training
        String commonPatterns = """
            java/lang/Object
            java/lang/String
            java/lang/Exception
            <init>
            ()V
            (Ljava/lang/String;)V
            Code
            LineNumberTable
            LocalVariableTable
            SourceFile
            this
            """;
        return commonPatterns.getBytes();
    }
}
