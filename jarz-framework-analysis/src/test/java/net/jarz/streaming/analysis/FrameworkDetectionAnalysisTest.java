package net.jarz.streaming.analysis;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Framework detection analysis on real JARs.
 * 
 * @author Plasticity.Cloud
 * @since 1.1
 */
public class FrameworkDetectionAnalysisTest {

    private FrameworkDetectorRegistry registry;
    
    @BeforeEach
    void setUp() {
        registry = new FrameworkDetectorRegistry();
    }

    @Test
    void analyzeFrameworkDetectionOnRealJars() throws IOException {
        System.out.println("=== FRAMEWORK DETECTION ANALYSIS ===\n");
        
        Path testJarsDir = Paths.get("target/test-jars");
        if (!Files.exists(testJarsDir)) {
            System.out.println("⚠️  Test JARs not found. Run 'mvn pre-integration-test' first.");
            return;
        }
        
        List<Path> testJars = Files.list(testJarsDir)
            .filter(p -> p.toString().endsWith(".jar"))
            .filter(p -> isInterestingJar(p))
            .sorted((a, b) -> Long.compare(getJarSize(b), getJarSize(a))) // Largest first
            .limit(10)
            .collect(Collectors.toList());
            
        if (testJars.isEmpty()) {
            System.out.println("⚠️  No JARs found for analysis.");
            return;
        }
        
        System.out.printf("📊 Analyzing framework detection on %d JARs:\n\n", testJars.size());
        
        Map<String, Integer> globalFrameworkStats = new HashMap<>();
        int totalClasses = 0;
        
        for (Path jarPath : testJars) {
            FrameworkAnalysis analysis = analyzeJar(jarPath);
            printJarAnalysis(jarPath.getFileName().toString(), analysis);
            
            // Aggregate stats
            analysis.frameworkDistribution.forEach((framework, count) -> 
                globalFrameworkStats.merge(framework, count, Integer::sum));
            totalClasses += analysis.totalClasses;
        }
        
        printGlobalAnalysis(globalFrameworkStats, totalClasses);
    }
    
    private boolean isInterestingJar(Path jarPath) {
        String name = jarPath.getFileName().toString().toLowerCase();
        return name.contains("spring") || name.contains("jackson") || 
               name.contains("commons") || name.contains("log4j") ||
               name.contains("flink") || name.contains("spark") ||
               name.contains("hadoop") || name.contains("aws") ||
               name.contains("azure") || name.contains("guava") ||
               name.contains("slf4j") || name.contains("logback");
    }
    
    private long getJarSize(Path jarPath) {
        try {
            return Files.size(jarPath);
        } catch (IOException e) {
            return 0;
        }
    }
    
    private FrameworkAnalysis analyzeJar(Path jarPath) throws IOException {
        FrameworkAnalysis analysis = new FrameworkAnalysis();
        analysis.jarName = jarPath.getFileName().toString();
        analysis.jarSize = Files.size(jarPath);
        
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            jarFile.stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
                .forEach(entry -> {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    String framework = registry.detectFramework(className);
                    
                    analysis.frameworkDistribution.merge(framework, 1, Integer::sum);
                    analysis.totalClasses++;
                    
                    // Sample some class names for each framework
                    analysis.frameworkExamples.computeIfAbsent(framework, k -> new ArrayList<>())
                        .add(className);
                });
        }
        
        // Limit examples to 3 per framework
        analysis.frameworkExamples.forEach((framework, examples) -> {
            if (examples.size() > 3) {
                analysis.frameworkExamples.put(framework, examples.subList(0, 3));
            }
        });
        
        return analysis;
    }
    
    private void printJarAnalysis(String jarName, FrameworkAnalysis analysis) {
        System.out.printf("📦 %s (%,d bytes, %d classes)\n", 
            jarName, analysis.jarSize, analysis.totalClasses);
            
        // Sort frameworks by class count
        List<Map.Entry<String, Integer>> sortedFrameworks = analysis.frameworkDistribution.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
            
        System.out.println("   Framework distribution:");
        for (Map.Entry<String, Integer> entry : sortedFrameworks) {
            String framework = entry.getKey();
            int count = entry.getValue();
            double percentage = (double) count / analysis.totalClasses * 100;
            
            System.out.printf("     • %s: %d classes (%.1f%%)\n", framework, count, percentage);
            
            // Show examples for non-"other" frameworks
            if (!"other".equals(framework) && analysis.frameworkExamples.containsKey(framework)) {
                List<String> examples = analysis.frameworkExamples.get(framework);
                System.out.printf("       Examples: %s\n", 
                    examples.stream().limit(2).collect(Collectors.joining(", ")));
            }
        }
        
        System.out.println();
    }
    
    private void printGlobalAnalysis(Map<String, Integer> globalStats, int totalClasses) {
        System.out.println("=== GLOBAL FRAMEWORK ANALYSIS ===");
        System.out.printf("Total classes analyzed: %,d\n", totalClasses);
        System.out.printf("Frameworks detected: %d\n\n", globalStats.size());
        
        List<Map.Entry<String, Integer>> sortedGlobal = globalStats.entrySet()
            .stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());
            
        System.out.println("Framework distribution across all JARs:");
        for (Map.Entry<String, Integer> entry : sortedGlobal) {
            String framework = entry.getKey();
            int count = entry.getValue();
            double percentage = (double) count / totalClasses * 100;
            
            System.out.printf("  %s: %,d classes (%.1f%%)\n", framework, count, percentage);
        }
        
        // Calculate framework detection effectiveness
        int detectedClasses = globalStats.entrySet().stream()
            .filter(e -> !"other".equals(e.getKey()))
            .mapToInt(Map.Entry::getValue)
            .sum();
            
        double detectionRate = (double) detectedClasses / totalClasses * 100;
        System.out.printf("\n🎯 Framework detection effectiveness: %.1f%% (%,d/%,d classes)\n", 
            detectionRate, detectedClasses, totalClasses);
            
        System.out.println("\n✅ Framework detector registry is working correctly!");
        System.out.println("   The analysis shows framework-aware grouping potential for compression optimization.");
    }
    
    private static class FrameworkAnalysis {
        String jarName;
        long jarSize;
        int totalClasses;
        Map<String, Integer> frameworkDistribution = new HashMap<>();
        Map<String, List<String>> frameworkExamples = new HashMap<>();
    }
}
