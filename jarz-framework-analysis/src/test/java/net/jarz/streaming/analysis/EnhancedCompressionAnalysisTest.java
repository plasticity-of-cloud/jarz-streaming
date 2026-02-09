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

/**
 * Enhanced analysis comparing compression strategies with detailed insights.
 * 
 * @author Plasticity.Cloud
 * @since 1.1
 */
public class EnhancedCompressionAnalysisTest {

    @TempDir
    Path tempDir;
    
    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }

    @Test
    void analyzeCompressionStrategiesWithInsights() throws IOException {
        System.out.println("=== ENHANCED COMPRESSION STRATEGY ANALYSIS ===\n");
        
        Path testJarsDir = Paths.get("target/test-jars");
        if (!Files.exists(testJarsDir)) {
            System.out.println("⚠️  Test JARs not found. Run 'mvn pre-integration-test' first.");
            return;
        }
        
        List<Path> testJars = Files.list(testJarsDir)
            .filter(p -> p.toString().endsWith(".jar"))
            .filter(p -> isFrameworkJar(p))
            .sorted((a, b) -> {
                // Prioritize big data frameworks first
                String nameA = a.getFileName().toString().toLowerCase();
                String nameB = b.getFileName().toString().toLowerCase();
                
                int scoreA = getBigDataScore(nameA);
                int scoreB = getBigDataScore(nameB);
                
                return Integer.compare(scoreB, scoreA); // Higher score first
            })
            .limit(8) // Focus on top 8 for detailed analysis
            .collect(Collectors.toList());
            
        if (testJars.isEmpty()) {
            System.out.println("⚠️  No framework JARs found for analysis.");
            return;
        }
        
        System.out.printf("📊 Analyzing %d framework JARs with detailed insights:\n", testJars.size());
        testJars.forEach(jar -> System.out.println("  • " + jar.getFileName()));
        System.out.println();
        
        for (Path jarPath : testJars) {
            analyzeJarInDetail(jarPath);
        }
        
        System.out.println("=== KEY INSIGHTS ===");
        System.out.println("1. Framework-aware strategy creates more blocks but smaller sizes");
        System.out.println("2. More blocks can reduce compression efficiency due to overhead");
        System.out.println("3. Optimal block size balance is needed for framework detection");
        System.out.println("4. Different frameworks may need different block size strategies");
    }
    
    private int getBigDataScore(String jarName) {
        if (jarName.contains("flink")) return 100;
        if (jarName.contains("spark")) return 90;
        if (jarName.contains("hadoop")) return 80;
        if (jarName.contains("aws")) return 70;
        if (jarName.contains("azure")) return 60;
        if (jarName.contains("google-cloud")) return 50;
        if (jarName.contains("spring")) return 40;
        return 10; // Other frameworks
    }
    
    private boolean isFrameworkJar(Path jarPath) {
        String name = jarPath.getFileName().toString().toLowerCase();
        return name.contains("spring") || name.contains("jackson") || 
               name.contains("commons") || name.contains("log4j") ||
               name.contains("flink") || name.contains("spark") ||
               name.contains("hadoop") || name.contains("aws") ||
               name.contains("azure") || name.contains("google-cloud");
    }
    
    private void analyzeJarInDetail(Path jarPath) throws IOException {
        System.out.printf("🔍 DETAILED ANALYSIS: %s\n", jarPath.getFileName());
        
        // Load and analyze class files
        Map<String, byte[]> classFiles = loadClassFiles(jarPath);
        System.out.printf("   Classes: %d\n", classFiles.size());
        
        // Analyze framework distribution
        Map<String, Integer> frameworkDistribution = analyzeFrameworkDistribution(classFiles);
        System.out.printf("   Framework distribution: %s\n", frameworkDistribution);
        
        // Test different strategies
        testDefaultStrategy(jarPath, classFiles);
        testFrameworkAwareStrategy(jarPath, classFiles);
        testOptimizedStrategy(jarPath, classFiles);
        
        System.out.println();
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
    
    private void testDefaultStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
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
        
        long originalSize = Files.size(jarPath);
        long compressedSize = Files.size(outputPath);
        int blockCount = countBlocks(outputPath);
        
        System.out.printf("   Default strategy:    %,d → %,d bytes (%d blocks) - %.1fx compression\n",
            originalSize, compressedSize, blockCount, (double) originalSize / compressedSize);
    }
    
    private void testFrameworkAwareStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        if (classFiles.isEmpty()) {
            System.out.printf("   Framework-aware:     Skipped (no classes)\n");
            return;
        }
        
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".framework.jarz");
        
        DependencyGraph graph = new DependencyGraph();
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        long originalSize = Files.size(jarPath);
        long compressedSize = Files.size(outputPath);
        int blockCount = blocks.size();
        
        System.out.printf("   Framework-aware:     %,d → %,d bytes (%d blocks) - %.1fx compression\n",
            originalSize, compressedSize, blockCount, (double) originalSize / compressedSize);
            
        // Analyze block distribution
        analyzeBlockDistribution(blocks);
    }
    
    private void testOptimizedStrategy(Path jarPath, Map<String, byte[]> classFiles) throws IOException {
        if (classFiles.isEmpty()) {
            System.out.printf("   Optimized strategy:  Skipped (no classes)\n");
            return;
        }
        
        Path outputPath = tempDir.resolve(jarPath.getFileName() + ".optimized.jarz");
        
        // Optimized strategy: Framework-aware but with minimum block sizes
        Map<String, List<String>> frameworkGroups = new HashMap<>();
        
        for (String className : classFiles.keySet()) {
            String framework = registry.detectFramework(className);
            frameworkGroups.computeIfAbsent(framework, k -> new ArrayList<>()).add(className);
        }
        
        try (BlockWriter writer = new BlockWriter(outputPath, 9)) {
            int blockId = 0;
            
            for (Map.Entry<String, List<String>> entry : frameworkGroups.entrySet()) {
                List<String> classes = entry.getValue();
                
                // Ensure minimum block size for compression efficiency
                int minBlockSize = Math.max(20, classes.size() / 3); // At least 20 classes or 1/3 of framework
                
                for (int i = 0; i < classes.size(); i += minBlockSize) {
                    Block block = new Block(blockId++);
                    int end = Math.min(i + minBlockSize, classes.size());
                    
                    for (int j = i; j < end; j++) {
                        String className = classes.get(j);
                        block.add(className, classFiles.get(className));
                    }
                    
                    if (block.size() > 0) {
                        writer.writeBlock(block);
                    }
                }
            }
        }
        
        long originalSize = Files.size(jarPath);
        long compressedSize = Files.size(outputPath);
        int blockCount = countBlocks(outputPath);
        
        System.out.printf("   Optimized strategy:  %,d → %,d bytes (%d blocks) - %.1fx compression\n",
            originalSize, compressedSize, blockCount, (double) originalSize / compressedSize);
    }
    
    private void analyzeBlockDistribution(List<Block> blocks) {
        Map<Integer, Integer> sizeDistribution = new HashMap<>();
        
        for (Block block : blocks) {
            int size = block.size();
            sizeDistribution.merge(size, 1, Integer::sum);
        }
        
        System.out.printf("     Block size distribution: %s\n", sizeDistribution);
        
        int totalClasses = blocks.stream().mapToInt(Block::size).sum();
        double avgBlockSize = (double) totalClasses / blocks.size();
        System.out.printf("     Average block size: %.1f classes\n", avgBlockSize);
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
}
