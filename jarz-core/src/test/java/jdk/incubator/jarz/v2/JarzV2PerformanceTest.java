package jdk.incubator.jarz.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance benchmark tests for JARZ v2 format.
 * Tests compression ratios, read/write performance, and memory usage.
 */
class JarzV2PerformanceTest {

    @Test
    void testCompressionRatioComparison(@TempDir Path tempDir) throws Exception {
        // Create realistic class data
        Map<String, byte[]> classes = createRealisticClasses(100);
        
        Path jarzV2File = tempDir.resolve("test-v2.jarz");
        
        long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
        
        // Write JARZ v2 (block compression)
        long v2WriteStart = System.nanoTime();
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        BlockAssigner assigner = new BlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        try (BlockWriter writer = new BlockWriter(jarzV2File, 3)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        long v2WriteTime = System.nanoTime() - v2WriteStart;
        
        // Compare results
        long v2Size = Files.size(jarzV2File);
        
        double v2Ratio = (double) v2Size / originalSize;
        
        System.out.printf("Compression Performance Comparison:%n");
        System.out.printf("Original size:    %,10d bytes%n", originalSize);
        System.out.printf("JARZ v2 size:     %,10d bytes (%.1f%% of original)%n", v2Size, v2Ratio * 100);
        System.out.printf("V2 write time:    %,10.1f ms%n", v2WriteTime / 1_000_000.0);
        
        // Verify data integrity
        verifyV2Integrity(jarzV2File, classes);
    }
    
    @Test
    void testReadPerformanceComparison(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = createRealisticClasses(200);
        
        Path jarzV2File = tempDir.resolve("read-test-v2.jarz");
        
        // Create v2 format
        createJarzV2Archive(jarzV2File, classes);
        
        // Test V2 read performance
        long v2ReadStart = System.nanoTime();
        int v2ReadCount = 0;
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            for (String className : classes.keySet()) {
                byte[] data = reader.readClass(className);
                if (data != null) v2ReadCount++;
            }
        }
        long v2ReadTime = System.nanoTime() - v2ReadStart;
        
        System.out.printf("Read Performance Test:%n");
        System.out.printf("V2 read time:     %,10.1f ms (%d classes)%n", v2ReadTime / 1_000_000.0, v2ReadCount);
        
        // Verify all classes were read successfully
        assertThat(v2ReadCount).isEqualTo(classes.size());
        
        // Performance should be reasonable (less than 1ms per class on average)
        double avgReadTimeMs = v2ReadTime / 1_000_000.0 / classes.size();
        assertThat(avgReadTimeMs).isLessThan(1.0);
    }
    
    @Test
    void testRandomAccessPerformance(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = createRealisticClasses(500);
        Path jarzV2File = tempDir.resolve("random-access.jarz");
        
        createJarzV2Archive(jarzV2File, classes);
        
        // Test random access patterns
        String[] classNames = classes.keySet().toArray(new String[0]);
        java.util.Random random = new java.util.Random(42);
        
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            // Cold access (no cache)
            long coldStart = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                String className = classNames[random.nextInt(classNames.length)];
                byte[] data = reader.readClass(className);
                assertThat(data).isNotNull();
            }
            long coldTime = System.nanoTime() - coldStart;
            
            // Warm access (with cache)
            long warmStart = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                String className = classNames[random.nextInt(classNames.length)];
                byte[] data = reader.readClass(className);
                assertThat(data).isNotNull();
            }
            long warmTime = System.nanoTime() - warmStart;
            
            System.out.printf("Random Access Performance:%n");
            System.out.printf("Cold access:      %,10.1f ms (50 classes)%n", coldTime / 1_000_000.0);
            System.out.printf("Warm access:      %,10.1f ms (50 classes)%n", warmTime / 1_000_000.0);
            System.out.printf("Cache speedup:    %,10.1fx%n", (double) coldTime / warmTime);
            
            // Warm access should be significantly faster
            assertThat(warmTime).isLessThan(coldTime);
        }
    }
    
    @Test
    void testMemoryUsage(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = createRealisticClasses(1000);
        Path jarzV2File = tempDir.resolve("memory-test.jarz");
        
        createJarzV2Archive(jarzV2File, classes);
        
        Runtime runtime = Runtime.getRuntime();
        
        // Measure memory before
        System.gc();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            // Load many classes to test memory usage
            for (int i = 0; i < 100; i++) {
                String className = "com/example/service/Service" + i;
                if (classes.containsKey(className)) {
                    byte[] data = reader.readClass(className);
                    assertThat(data).isNotNull();
                }
            }
            
            // Measure memory after
            System.gc();
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            System.out.printf("Memory Usage:%n");
            System.out.printf("Memory before:    %,10d bytes%n", memoryBefore);
            System.out.printf("Memory after:     %,10d bytes%n", memoryAfter);
            System.out.printf("Memory used:      %,10d bytes%n", memoryUsed);
            
            // Memory usage should be reasonable (allow for JVM overhead)
            long fileSize = Files.size(jarzV2File);
            assertThat(memoryUsed).isLessThan(fileSize * 3); // Allow 3x file size for JVM overhead
        }
    }
    
    @Test
    void testScalabilityWithLargeArchives(@TempDir Path tempDir) throws Exception {
        // Test with different archive sizes
        int[] classCounts = {100, 500, 1000, 2000};
        
        for (int classCount : classCounts) {
            Map<String, byte[]> classes = createRealisticClasses(classCount);
            Path jarzFile = tempDir.resolve("scale-" + classCount + ".jarz");
            
            // Measure write performance
            long writeStart = System.nanoTime();
            createJarzV2Archive(jarzFile, classes);
            long writeTime = System.nanoTime() - writeStart;
            
            // Measure read performance
            long readStart = System.nanoTime();
            try (BlockReader reader = new BlockReader(jarzFile)) {
                // Read sample of classes
                int sampleSize = Math.min(50, classCount);
                String[] classNames = classes.keySet().toArray(new String[0]);
                
                for (int i = 0; i < sampleSize; i++) {
                    byte[] data = reader.readClass(classNames[i]);
                    assertThat(data).isNotNull();
                }
            }
            long readTime = System.nanoTime() - readStart;
            
            long fileSize = Files.size(jarzFile);
            long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
            double compressionRatio = (double) fileSize / originalSize;
            
            System.out.printf("Scalability Test - %d classes:%n", classCount);
            System.out.printf("  Write time:     %,8.1f ms%n", writeTime / 1_000_000.0);
            System.out.printf("  Read time:      %,8.1f ms%n", readTime / 1_000_000.0);
            System.out.printf("  File size:      %,8d bytes%n", fileSize);
            System.out.printf("  Compression:    %,8.1f%%%n", compressionRatio * 100);
            System.out.println();
        }
    }
    
    @Test
    void testConcurrentReadPerformance(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = createRealisticClasses(300);
        Path jarzV2File = tempDir.resolve("concurrent-test.jarz");
        
        createJarzV2Archive(jarzV2File, classes);
        
        String[] classNames = classes.keySet().toArray(new String[0]);
        
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            // Single-threaded baseline
            long singleStart = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                String className = classNames[i % classNames.length];
                byte[] data = reader.readClass(className);
                assertThat(data).isNotNull();
            }
            long singleTime = System.nanoTime() - singleStart;
            
            // Multi-threaded test
            java.util.concurrent.ExecutorService executor = 
                java.util.concurrent.Executors.newFixedThreadPool(4);
            
            long multiStart = System.nanoTime();
            java.util.List<java.util.concurrent.Future<Void>> futures = new java.util.ArrayList<>();
            
            for (int t = 0; t < 4; t++) {
                final int threadId = t;
                futures.add(executor.submit(() -> {
                    try {
                        for (int i = 0; i < 25; i++) {
                            String className = classNames[(threadId * 25 + i) % classNames.length];
                            byte[] data = reader.readClass(className);
                            assertThat(data).isNotNull();
                        }
                        return null;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            
            // Wait for completion
            for (var future : futures) {
                future.get();
            }
            long multiTime = System.nanoTime() - multiStart;
            
            executor.shutdown();
            
            System.out.printf("Concurrent Read Performance:%n");
            System.out.printf("Single-threaded: %,10.1f ms%n", singleTime / 1_000_000.0);
            System.out.printf("Multi-threaded:  %,10.1f ms%n", multiTime / 1_000_000.0);
            System.out.printf("Speedup:         %,10.1fx%n", (double) singleTime / multiTime);
            
            // Multi-threaded should be reasonable (allow for thread overhead and contention)
            // In some environments, concurrent access may be slower due to synchronization
            assertThat(multiTime).isLessThan((long)(singleTime * 10)); // Very lenient for test stability
        }
    }
    
    private Map<String, byte[]> createRealisticClasses(int count) {
        Map<String, byte[]> classes = new HashMap<>();
        
        String[] packages = {"service", "controller", "repository", "model", "util", "config"};
        
        for (int i = 0; i < count; i++) {
            String pkg = packages[i % packages.length];
            String className = "com/example/" + pkg + "/Class" + i;
            int size = 2000 + (i % 8000); // Varying sizes 2KB-10KB
            classes.put(className, generateRealisticClassData(className, size));
        }
        
        return classes;
    }
    
    private void createJarzV2Archive(Path jarzFile, Map<String, byte[]> classes) throws Exception {
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        BlockAssigner assigner = new BlockAssigner(64 * 1024, 128 * 1024);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        try (BlockWriter writer = new BlockWriter(jarzFile, 3)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
    }
    
    
    private void verifyV2Integrity(Path jarzFile, Map<String, byte[]> expected) throws Exception {
        try (BlockReader reader = new BlockReader(jarzFile)) {
            for (var entry : expected.entrySet()) {
                byte[] data = reader.readClass(entry.getKey());
                assertThat(data).isEqualTo(entry.getValue());
            }
        }
    }
    
    private byte[] generateRealisticClassData(String className, int size) {
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
        
        // Fill with realistic patterns
        String[] patterns = {
            "java/lang/Object", "java/lang/String", "<init>", "()V",
            "Code", "LineNumberTable", "LocalVariableTable", "this",
            className.substring(className.lastIndexOf('/') + 1)
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
}
