package net.jarz.streaming.analysis;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import net.jarz.streaming.v2.enhanced.EnhancedBlockAssigner;
import net.jarz.streaming.v2.BlockWriter;
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

/**
 * Debug analysis to understand block creation patterns.
 * 
 * @author Plasticity.Cloud
 * @since 1.1
 */
public class BlockCreationDebugTest {

    @TempDir
    Path tempDir;
    
    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }

    @Test
    void debugBlockCreation() throws IOException {
        System.out.println("=== BLOCK CREATION DEBUG ANALYSIS ===\n");
        
        Path testJarsDir = Paths.get("target/test-jars");
        if (!Files.exists(testJarsDir)) {
            System.out.println("⚠️  Test JARs not found. Run 'mvn pre-integration-test' first.");
            return;
        }
        
        // Focus on one large JAR for detailed analysis
        Optional<Path> guavaJar = Files.list(testJarsDir)
            .filter(p -> p.toString().contains("guava") && !p.toString().contains("empty"))
            .findFirst();
            
        if (guavaJar.isEmpty()) {
            // Try jackson-databind as alternative
            guavaJar = Files.list(testJarsDir)
                .filter(p -> p.toString().contains("jackson-databind"))
                .findFirst();
        }
            
        if (guavaJar.isEmpty()) {
            System.out.println("⚠️  No suitable large JAR found.");
            return;
        }
        
        Path jarPath = guavaJar.get();
        System.out.printf("🔍 Analyzing: %s\n", jarPath.getFileName());
        
        Map<String, byte[]> classFiles = loadClassFiles(jarPath);
        System.out.printf("   Total classes: %d\n", classFiles.size());
        
        // Analyze framework distribution
        Map<String, Integer> frameworkDist = analyzeFrameworkDistribution(classFiles);
        System.out.printf("   Framework distribution: %s\n", frameworkDist);
        
        // Test default strategy
        int defaultBlocks = testDefaultStrategy(jarPath, classFiles);
        System.out.printf("   Default strategy: %d blocks\n", defaultBlocks);
        
        // Test framework-aware strategy with debugging
        int frameworkBlocks = testFrameworkAwareStrategyWithDebug(jarPath, classFiles);
        System.out.printf("   Framework-aware: %d blocks\n", frameworkBlocks);
        
        System.out.printf("\n📊 Block count comparison: Default=%d, Framework-aware=%d\n", 
            defaultBlocks, frameworkBlocks);
            
        if (frameworkBlocks > defaultBlocks) {
            System.out.println("❌ Framework-aware creates MORE blocks - this hurts compression!");
            System.out.println("   Solution: Increase minimum block sizes or merge small framework groups");
        } else {
            System.out.println("✅ Framework-aware creates fewer or equal blocks - good for compression");
        }
    }
    
    private Map<String, Integer> analyzeFrameworkDistribution(Map<String, byte[]> classFiles) {
        Map<String, Integer> distribution = new HashMap<>();
        
        for (String className : classFiles.keySet()) {
            String framework = registry.detectFramework(className);
            distribution.merge(framework, 1, Integer::sum);
        }
        
        return distribution.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }
    
    private int testDefaultStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".default.jarz");
        
        List<String> sortedClasses = new ArrayList<>(classFiles.keySet());
        sortedClasses.sort(String::compareTo);
        
        int blockSize = 50; // Fixed block size
        int blockCount = 0;
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            for (int i = 0; i < sortedClasses.size(); i += blockSize) {
                Block block = new Block(blockCount);
                int end = Math.min(i + blockSize, sortedClasses.size());
                
                for (int j = i; j < end; j++) {
                    String className = sortedClasses.get(j);
                    block.add(className, classFiles.get(className));
                }
                
                if (block.entryCount() > 0) {
                    writer.writeBlock(block);
                    blockCount++;
                }
            }
        }
        
        return blockCount;
    }
    
    private int testFrameworkAwareStrategyWithDebug(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".framework.jarz");
        
        System.out.printf("   Debug: Creating EnhancedBlockAssigner...\n");
        DependencyGraph graph = new DependencyGraph();
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        
        System.out.printf("   Debug: Calling assignBlocks with %d classes...\n", classFiles.size());
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        System.out.printf("   Framework-aware block details:\n");
        int totalClasses = 0;
        for (int i = 0; i < Math.min(blocks.size(), 10); i++) {
            Block block = blocks.get(i);
            System.out.printf("     Block %d: %d classes\n", i, block.entryCount());
            totalClasses += block.entryCount();
        }
        if (blocks.size() > 10) {
            System.out.printf("     ... and %d more blocks\n", blocks.size() - 10);
            for (int i = 10; i < blocks.size(); i++) {
                totalClasses += blocks.get(i).entryCount();
            }
        }
        System.out.printf("   Total classes in blocks: %d (expected: %d)\n", totalClasses, classFiles.size());
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        return blocks.size();
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
}
