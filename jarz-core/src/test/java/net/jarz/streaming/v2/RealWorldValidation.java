package net.jarz.streaming.v2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Real-world validation test for JARZ v2 block compression.
 * Tests against actual JDK classes extracted from java.base module.
 */
public class RealWorldValidation {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== JARZ v2 Real-World Validation ===\n");
        
        // Extract classes from java.base module
        Path javaHome = Path.of(System.getProperty("java.home"));
        Path modulesFile = javaHome.resolve("lib/modules");
        
        if (!Files.exists(modulesFile)) {
            System.err.println("Cannot find JDK modules file: " + modulesFile);
            System.exit(1);
        }
        
        // Use jimage to extract java.base classes
        Path tempDir = Files.createTempDirectory("jarz-v2-validation");
        Path extractDir = tempDir.resolve("extracted");
        Files.createDirectories(extractDir);
        
        System.out.println("Extracting java.base classes...");
        ProcessBuilder pb = new ProcessBuilder(
            javaHome.resolve("bin/jimage").toString(),
            "extract",
            "--dir=" + extractDir,
            modulesFile.toString()
        );
        pb.inheritIO();
        int exitCode = pb.start().waitFor();
        if (exitCode != 0) {
            System.err.println("jimage extract failed");
            System.exit(1);
        }
        
        // Load java.base classes
        Path javaBaseDir = extractDir.resolve("java.base");
        Map<String, byte[]> classes = new HashMap<>();
        long totalOriginalSize = 0;
        
        try (var walk = Files.walk(javaBaseDir)) {
            var classFiles = walk.filter(p -> p.toString().endsWith(".class")).toList();
            System.out.printf("Found %d class files%n", classFiles.size());
            
            for (Path classFile : classFiles) {
                String className = javaBaseDir.relativize(classFile).toString()
                    .replace(File.separatorChar, '/')
                    .replace(".class", "");
                byte[] data = Files.readAllBytes(classFile);
                classes.put(className, data);
                totalOriginalSize += data.length;
            }
        }
        
        System.out.printf("Total original size: %,d bytes (%.1f MB)%n%n", 
            totalOriginalSize, totalOriginalSize / 1024.0 / 1024.0);
        
        // Test: JARZ v2 (block compression with dependency analysis)
        Path jarzV2File = tempDir.resolve("java.base-v2.jarz");
        System.out.println("Creating JARZ v2 (block-based ZSTD)...");
        long v2Start = System.currentTimeMillis();
        
        // Analyze dependencies
        DependencyAnalyzer analyzer = new DependencyAnalyzer();
        DependencyGraph graph = analyzer.analyzeClassFiles(javaBaseDir);
        System.out.printf("Dependency graph: %d classes, analyzing relationships...%n", graph.size());
        
        // Assign blocks
        BlockAssigner assigner = new BlockAssigner(512 * 1024, 1024 * 1024); // 512KB target
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        System.out.printf("Created %d blocks%n", blocks.size());
        
        // Write v2 archive
        try (BlockWriter writer = new BlockWriter(jarzV2File, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        long v2Time = System.currentTimeMillis() - v2Start;
        long v2Size = Files.size(jarzV2File);
        
        // Test 3: Standard JAR (DEFLATE)
        Path jarFile = tempDir.resolve("java.base.jar");
        System.out.println("Creating standard JAR (DEFLATE)...");
        long jarStart = System.currentTimeMillis();
        
        try (var jos = new JarOutputStream(new FileOutputStream(jarFile.toFile()))) {
            jos.setLevel(9);
            for (var entry : classes.entrySet()) {
                JarEntry je = new JarEntry(entry.getKey() + ".class");
                jos.putNextEntry(je);
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }
        
        long jarTime = System.currentTimeMillis() - jarStart;
        long jarSize = Files.size(jarFile);
        
        // Results
        System.out.println("\n=== COMPRESSION RESULTS ===\n");
        
        System.out.printf("%-20s %15s %10s %10s %12s%n", 
            "Format", "Size", "Ratio", "vs JAR", "Time");
        System.out.println("-".repeat(70));
        
        double jarRatio = (double) jarSize / totalOriginalSize * 100;
        double v2Ratio = (double) v2Size / totalOriginalSize * 100;
        
        double v2VsJar = (jarSize - v2Size) / (double) jarSize * 100;
        
        System.out.printf("%-20s %,15d %9.1f%% %10s %10dms%n", 
            "Original", totalOriginalSize, 100.0, "-", 0);
        System.out.printf("%-20s %,15d %9.1f%% %10s %10dms%n", 
            "JAR (DEFLATE)", jarSize, jarRatio, "baseline", jarTime);
        System.out.printf("%-20s %,15d %9.1f%% %+9.1f%% %10dms%n", 
            "JARZ v2 (blocks)", v2Size, v2Ratio, v2VsJar, v2Time);
        
        System.out.println("\n=== KEY METRICS ===\n");
        System.out.printf("JARZ v2 vs JAR:     %+.1f%% (target: 18-22%%)%n", v2VsJar);
        System.out.printf("Block count:        %d blocks%n", blocks.size());
        System.out.printf("Avg block size:     %.1f KB%n", 
            blocks.stream().mapToInt(Block::size).average().orElse(0) / 1024.0);
        
        // Verify data integrity
        System.out.println("\n=== DATA INTEGRITY CHECK ===\n");
        System.out.print("Verifying JARZ v2 data integrity... ");
        
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            int verified = 0;
            int failed = 0;
            
            for (var entry : classes.entrySet()) {
                byte[] original = entry.getValue();
                byte[] read = reader.readClass(entry.getKey());
                
                if (read == null) {
                    failed++;
                } else if (!Arrays.equals(original, read)) {
                    failed++;
                } else {
                    verified++;
                }
            }
            
            if (failed == 0) {
                System.out.printf("PASSED (%d classes verified)%n", verified);
            } else {
                System.out.printf("FAILED (%d passed, %d failed)%n", verified, failed);
            }
        }
        
        // Cleanup
        System.out.println("\nCleaning up temporary files...");
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        
        System.out.println("\nValidation complete.");
    }
}
