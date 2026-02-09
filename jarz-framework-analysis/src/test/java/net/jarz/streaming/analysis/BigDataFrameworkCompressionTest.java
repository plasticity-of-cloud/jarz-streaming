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
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive comparison of default vs framework-aware compression strategies
 * focusing on big data frameworks (Flink, Spark, Hadoop) and cloud SDKs.
 * 
 * @author Plasticity.Cloud
 * @since 1.1
 */
public class BigDataFrameworkCompressionTest {

    @TempDir
    Path tempDir;
    
    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }

    @Test
    void compareBigDataFrameworkCompression() throws IOException {
        System.out.println("=== BIG DATA FRAMEWORK COMPRESSION ANALYSIS ===\n");
        
        Path testJarsDir = Paths.get("target/test-jars");
        if (!Files.exists(testJarsDir)) {
            System.out.println("⚠️  Test JARs not found. Run 'mvn pre-integration-test' first.");
            return;
        }
        
        List<Path> testJars = Files.list(testJarsDir)
            .filter(p -> p.toString().endsWith(".jar"))
            .filter(p -> isBigDataFramework(p))
            .sorted((a, b) -> Long.compare(getJarSize(b), getJarSize(a))) // Largest first
            .collect(Collectors.toList());
            
        if (testJars.isEmpty()) {
            System.out.println("⚠️  No big data framework JARs found for analysis.");
            return;
        }
        
        System.out.printf("📊 Analyzing %d big data framework JARs:\n", testJars.size());
        testJars.forEach(jar -> System.out.printf("  • %s (%,d bytes)\n", 
            jar.getFileName(), getJarSize(jar)));
        System.out.println();
        
        ComparisonResults totalResults = new ComparisonResults();
        
        for (Path jarPath : testJars) {
            ComparisonResults jarResults = compareStrategies(jarPath);
            if (jarResults != null) {
                totalResults.add(jarResults);
                printJarComparison(jarPath.getFileName().toString(), jarResults);
            }
        }
        
        if (totalResults.jarCount > 0) {
            printSummaryComparison(totalResults);
            
            // The assertion: framework-aware should be better for big data frameworks
            double improvement = ((totalResults.frameworkAwareRatio - totalResults.defaultRatio) / totalResults.defaultRatio) * 100;
            System.out.printf("\n🎯 Framework-aware improvement: %.1f%%\n", improvement);
            
            if (improvement > 0) {
                System.out.println("✅ Framework-aware strategy shows improvement on big data frameworks!");
            } else {
                System.out.println("⚠️  Framework-aware strategy needs optimization for these frameworks.");
                System.out.println("   Consider: larger minimum block sizes, better framework grouping.");
            }
        }
    }
    
    private boolean isBigDataFramework(Path jarPath) {
        String name = jarPath.getFileName().toString().toLowerCase();
        return name.contains("flink") || name.contains("spark") || 
               name.contains("hadoop") || name.contains("aws") ||
               name.contains("azure") || name.contains("guava") ||
               name.contains("jackson") && name.contains("databind") ||
               name.contains("catalyst"); // Spark Catalyst
    }
    
    private long getJarSize(Path jarPath) {
        try {
            return Files.size(jarPath);
        } catch (IOException e) {
            return 0;
        }
    }
    
    private ComparisonResults compareStrategies(Path jarPath) throws IOException {
        Map<String, byte[]> classFiles = loadClassFiles(jarPath);
        
        if (classFiles.isEmpty()) {
            System.out.printf("⚠️  Skipping %s (no classes)\n", jarPath.getFileName());
            return null;
        }
        
        ComparisonResults results = new ComparisonResults();
        results.jarName = jarPath.getFileName().toString();
        results.originalSize = Files.size(jarPath);
        results.classCount = classFiles.size();
        
        // Analyze framework distribution
        results.frameworkDistribution = analyzeFrameworkDistribution(classFiles);
        
        // Test default strategy
        results.defaultSize = testDefaultStrategy(jarPath, classFiles);
        results.defaultRatio = (double) results.originalSize / results.defaultSize;
        
        // Test framework-aware strategy
        results.frameworkAwareSize = testFrameworkAwareStrategy(jarPath, classFiles);
        results.frameworkAwareRatio = (double) results.originalSize / results.frameworkAwareSize;
        
        return results;
    }
    
    private Map<String, Integer> analyzeFrameworkDistribution(Map<String, byte[]> classFiles) {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (String className : classFiles.keySet()) {
            String framework = registry.detectFramework(className);
            distribution.merge(framework, 1, Integer::sum);
        }
        
        return distribution.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(5) // Top 5 frameworks
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    private long testDefaultStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".default.jarz");
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            List<String> sortedClasses = new ArrayList<>(classFiles.keySet());
            sortedClasses.sort(String::compareTo);
            
            int blockSize = 50; // Fixed block size
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
        
        return Files.size(outputPath);
    }
    
    private long testFrameworkAwareStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".framework.jarz");
        
        DependencyGraph graph = new DependencyGraph();
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        return Files.size(outputPath);
    }
    
    private void printJarComparison(String jarName, ComparisonResults results) {
        System.out.printf("📦 %s\n", jarName);
        System.out.printf("   Original: %,d bytes (%d classes)\n", 
            results.originalSize, results.classCount);
        System.out.printf("   Top frameworks: %s\n", 
            results.frameworkDistribution.entrySet().stream()
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", ")));
        System.out.printf("   Default strategy:      %,d bytes - %.1fx compression\n",
            results.defaultSize, results.defaultRatio);
        System.out.printf("   Framework-aware:       %,d bytes - %.1fx compression\n",
            results.frameworkAwareSize, results.frameworkAwareRatio);
        
        double improvement = ((results.frameworkAwareRatio - results.defaultRatio) / results.defaultRatio) * 100;
        String indicator = improvement > 0 ? "📈" : "📉";
        System.out.printf("   %s Improvement: %.1f%%\n\n", indicator, improvement);
    }
    
    private void printSummaryComparison(ComparisonResults totals) {
        System.out.println("=== SUMMARY COMPARISON ===");
        System.out.printf("Total JARs analyzed: %d\n", totals.jarCount);
        System.out.printf("Total original size: %,d bytes\n", totals.originalSize);
        System.out.printf("Total classes: %,d\n", totals.classCount);
        System.out.println();
        
        System.out.printf("Default strategy average:      %.2fx compression\n",
            totals.defaultRatio / totals.jarCount);
        System.out.printf("Framework-aware average:       %.2fx compression\n",
            totals.frameworkAwareRatio / totals.jarCount);
        
        double overallImprovement = ((totals.frameworkAwareRatio - totals.defaultRatio) / totals.defaultRatio) * 100;
        System.out.printf("Overall improvement: %.1f%%\n", overallImprovement);
        
        long spaceSaved = totals.defaultSize - totals.frameworkAwareSize;
        if (spaceSaved > 0) {
            System.out.printf("💾 Space saved: %,d bytes (%.1f%% reduction)\n", 
                spaceSaved, (double) spaceSaved / totals.defaultSize * 100);
        } else {
            System.out.printf("💾 Space overhead: %,d bytes (%.1f%% increase)\n", 
                -spaceSaved, (double) -spaceSaved / totals.defaultSize * 100);
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
    
    private static class ComparisonResults {
        String jarName;
        long originalSize;
        int classCount;
        Map<String, Integer> frameworkDistribution = new HashMap<>();
        
        long defaultSize;
        double defaultRatio;
        
        long frameworkAwareSize;
        double frameworkAwareRatio;
        
        int jarCount = 1;
        
        void add(ComparisonResults other) {
            this.jarCount += other.jarCount;
            this.originalSize += other.originalSize;
            this.classCount += other.classCount;
            this.defaultSize += other.defaultSize;
            this.defaultRatio += other.defaultRatio;
            this.frameworkAwareSize += other.frameworkAwareSize;
            this.frameworkAwareRatio += other.frameworkAwareRatio;
        }
    }
}
