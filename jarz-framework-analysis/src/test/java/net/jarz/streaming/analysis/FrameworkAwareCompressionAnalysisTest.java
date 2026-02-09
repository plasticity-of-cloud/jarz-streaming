package net.jarz.streaming.analysis;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import net.jarz.streaming.v2.enhanced.EnhancedBlockAssigner;
import net.jarz.streaming.v2.BlockWriter;
import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive analysis comparing default vs framework-aware JARZ compression strategies.
 * 
 * @author Plasticity.Cloud
 * @since 1.1
 */
public class FrameworkAwareCompressionAnalysisTest {

    @TempDir
    Path tempDir;
    
    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }

    @Test
    void compareCompressionStrategies() throws IOException {
        System.out.println("=== FRAMEWORK-AWARE COMPRESSION ANALYSIS ===\n");
        
        Path testJarsDir = Paths.get("target/test-jars");
        if (!Files.exists(testJarsDir)) {
            System.out.println("⚠️  Test JARs not found. Run 'mvn pre-integration-test' first.");
            return;
        }
        
        List<Path> testJars = Files.list(testJarsDir)
            .filter(p -> p.toString().endsWith(".jar"))
            .filter(p -> isFrameworkJar(p))
            .limit(10) // Analyze top 10 framework JARs
            .collect(Collectors.toList());
            
        if (testJars.isEmpty()) {
            System.out.println("⚠️  No framework JARs found for analysis.");
            return;
        }
        
        System.out.printf("📊 Analyzing %d framework JARs:\n", testJars.size());
        testJars.forEach(jar -> System.out.println("  • " + jar.getFileName()));
        System.out.println();
        
        AnalysisResults totalResults = new AnalysisResults();
        
        for (Path jarPath : testJars) {
            AnalysisResults jarResults = analyzeJar(jarPath);
            totalResults.add(jarResults);
            printJarAnalysis(jarPath.getFileName().toString(), jarResults);
        }
        
        printSummaryAnalysis(totalResults);
        
        // Verify framework-aware strategy is better
        assertThat(totalResults.enhancedCompressionRatio)
            .as("Framework-aware compression should be better than default")
            .isGreaterThan(totalResults.defaultCompressionRatio);
    }
    
    private boolean isFrameworkJar(Path jarPath) {
        String name = jarPath.getFileName().toString().toLowerCase();
        return name.contains("spring") || name.contains("jackson") || 
               name.contains("guava") || name.contains("commons") ||
               name.contains("log4j") || name.contains("slf4j") ||
               name.contains("logback") || name.contains("httpclient");
    }
    
    private AnalysisResults analyzeJar(Path jarPath) throws IOException {
        AnalysisResults results = new AnalysisResults();
        results.jarName = jarPath.getFileName().toString();
        results.originalSize = Files.size(jarPath);
        
        // Analyze JAR contents
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            results.classCount = (int) jarFile.stream()
                .filter(entry -> entry.getName().endsWith(".class"))
                .count();
                
            // Skip analysis for JARs with no class files
            if (results.classCount == 0) {
                results.detectedFrameworks = new HashSet<>();
                results.defaultJarzSize = results.originalSize; // No compression possible
                results.defaultCompressionRatio = 1.0;
                results.defaultBlocks = 0;
                results.enhancedJarzSize = results.originalSize;
                results.enhancedCompressionRatio = 1.0;
                results.enhancedBlocks = 0;
                return results;
            }
                
            // Detect frameworks in this JAR
            Set<String> frameworks = new HashSet<>();
            jarFile.stream()
                .filter(entry -> entry.getName().endsWith(".class"))
                .map(JarEntry::getName)
                .map(name -> name.replace('/', '.').replace(".class", ""))
                .forEach(className -> {
                    String framework = registry.detectFramework(className);
                    if (!"other".equals(framework)) {
                        frameworks.add(framework);
                    }
                });
            results.detectedFrameworks = frameworks;
        }
        
        // Test default compression strategy
        Path defaultJarz = tempDir.resolve(results.jarName + ".default.jarz");
        convertWithDefaultStrategy(jarPath, defaultJarz);
        results.defaultJarzSize = Files.size(defaultJarz);
        results.defaultCompressionRatio = (double) results.originalSize / results.defaultJarzSize;
        results.defaultBlocks = countBlocks(defaultJarz);
        
        // Test framework-aware compression strategy  
        Path enhancedJarz = tempDir.resolve(results.jarName + ".enhanced.jarz");
        convertWithFrameworkAwareStrategy(jarPath, enhancedJarz);
        results.enhancedJarzSize = Files.size(enhancedJarz);
        results.enhancedCompressionRatio = (double) results.originalSize / results.enhancedJarzSize;
        results.enhancedBlocks = countBlocks(enhancedJarz);
        
        return results;
    }
    
    private void convertWithDefaultStrategy(Path jarPath, Path outputPath) throws IOException {
        // Default strategy: simple alphabetical grouping
        Map<String, byte[]> classFiles = loadClassFiles(jarPath);
        
        // Skip JARs with no class files (metadata-only JARs)
        if (classFiles.isEmpty()) {
            // Create empty JARZ file for consistency
            try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
                // No blocks to write for empty JAR
            }
            return;
        }
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            List<String> sortedClasses = new ArrayList<>(classFiles.keySet());
            sortedClasses.sort(String::compareTo);
            
            // Group into blocks of 50 entries each
            int blockSize = 50;
            int blockId = 0;
            
            for (int i = 0; i < sortedClasses.size(); i += blockSize) {
                Block block = new Block(blockId++);
                int end = Math.min(i + blockSize, sortedClasses.size());
                
                for (int j = i; j < end; j++) {
                    String className = sortedClasses.get(j);
                    block.add(className, classFiles.get(className));
                }
                
                if (block.size() > 0) {
                    writer.writeBlock(block);
                }
            }
        }
    }
    
    private void convertWithFrameworkAwareStrategy(Path jarPath, Path outputPath) throws IOException {
        // Framework-aware strategy: use EnhancedBlockAssigner
        Map<String, byte[]> classFiles = loadClassFiles(jarPath);
        
        // Skip JARs with no class files (metadata-only JARs)
        if (classFiles.isEmpty()) {
            // Create empty JARZ file for consistency
            try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
                // No blocks to write for empty JAR
            }
            return;
        }
        
        DependencyGraph graph = new DependencyGraph(); // Simple empty graph for now
        
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
    }
    
    private Map<String, byte[]> loadClassFiles(Path jarPath) throws IOException {
        Map<String, byte[]> classFiles = new HashMap<>();
        
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            jarFile.stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                .forEach(entry -> {
                    try {
                        String className = entry.getName().replace('/', '.').replace(".class", "");
                        byte[] data = jarFile.getInputStream(entry).readAllBytes();
                        classFiles.put(className, data);
                    } catch (IOException e) {
                        // Skip problematic entries
                    }
                });
        }
        
        return classFiles;
    }
    
    private int countBlocks(Path jarzPath) throws IOException {
        try (BlockReader reader = new BlockReader(jarzPath)) {
            return reader.blockIndex().entries().size();
        }
    }
    
    private void printJarAnalysis(String jarName, AnalysisResults results) {
        System.out.printf("📦 %s\n", jarName);
        System.out.printf("   Original size: %,d bytes (%d classes)\n", 
            results.originalSize, results.classCount);
        System.out.printf("   Detected frameworks: %s\n", 
            results.detectedFrameworks.isEmpty() ? "none" : String.join(", ", results.detectedFrameworks));
        System.out.printf("   Default strategy:    %,d bytes (%d blocks) - %.1fx compression\n",
            results.defaultJarzSize, results.defaultBlocks, results.defaultCompressionRatio);
        System.out.printf("   Framework-aware:     %,d bytes (%d blocks) - %.1fx compression\n",
            results.enhancedJarzSize, results.enhancedBlocks, results.enhancedCompressionRatio);
        
        double improvement = ((results.enhancedCompressionRatio - results.defaultCompressionRatio) / results.defaultCompressionRatio) * 100;
        System.out.printf("   📈 Improvement: %.1f%% better compression\n\n", improvement);
    }
    
    private void printSummaryAnalysis(AnalysisResults totals) {
        System.out.println("=== SUMMARY ANALYSIS ===");
        System.out.printf("Total JARs analyzed: %d\n", totals.jarCount);
        System.out.printf("Total original size: %,d bytes\n", totals.originalSize);
        System.out.printf("Total classes: %,d\n", totals.classCount);
        System.out.printf("Unique frameworks detected: %s\n", 
            String.join(", ", totals.detectedFrameworks));
        System.out.println();
        
        System.out.printf("Default strategy average:    %.2fx compression (%d avg blocks/jar)\n",
            totals.defaultCompressionRatio / totals.jarCount, totals.defaultBlocks / totals.jarCount);
        System.out.printf("Framework-aware average:     %.2fx compression (%d avg blocks/jar)\n",
            totals.enhancedCompressionRatio / totals.jarCount, totals.enhancedBlocks / totals.jarCount);
        
        double overallImprovement = ((totals.enhancedCompressionRatio - totals.defaultCompressionRatio) / totals.defaultCompressionRatio) * 100;
        System.out.printf("🎯 Overall improvement: %.1f%% better compression with framework-aware strategy\n", overallImprovement);
        
        long spaceSaved = totals.defaultJarzSize - totals.enhancedJarzSize;
        System.out.printf("💾 Space saved: %,d bytes (%.1f%% reduction)\n", 
            spaceSaved, (double) spaceSaved / totals.defaultJarzSize * 100);
    }
    
    private static class AnalysisResults {
        String jarName;
        long originalSize;
        int classCount;
        Set<String> detectedFrameworks = new HashSet<>();
        
        long defaultJarzSize;
        double defaultCompressionRatio;
        int defaultBlocks;
        
        long enhancedJarzSize;
        double enhancedCompressionRatio;
        int enhancedBlocks;
        
        int jarCount = 1;
        
        void add(AnalysisResults other) {
            this.jarCount += other.jarCount;
            this.originalSize += other.originalSize;
            this.classCount += other.classCount;
            this.detectedFrameworks.addAll(other.detectedFrameworks);
            this.defaultJarzSize += other.defaultJarzSize;
            this.defaultCompressionRatio += other.defaultCompressionRatio;
            this.defaultBlocks += other.defaultBlocks;
            this.enhancedJarzSize += other.enhancedJarzSize;
            this.enhancedCompressionRatio += other.enhancedCompressionRatio;
            this.enhancedBlocks += other.enhancedBlocks;
        }
    }
}
