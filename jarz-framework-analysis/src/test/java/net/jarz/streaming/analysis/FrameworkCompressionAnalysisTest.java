package net.jarz.streaming.analysis;

import net.jarz.streaming.v2.JarToJarzConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive analysis of popular Java frameworks converted to JARZ format.
 * 
 * <p>This test analyzes compression efficiency, block organization, and space savings
 * for widely-used Java libraries including Guava, Commons Lang3, Jackson, and others.
 * 
 * <p>Run with: {@code mvn test -pl jarz-framework-analysis}
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class FrameworkCompressionAnalysisTest {

    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#0.0");
    private static final DecimalFormat SIZE_FORMAT = new DecimalFormat("#,###");
    
    private static final String[] FRAMEWORKS = {
        "guava-33.0.0-jre.jar",
        "commons-lang3-3.14.0.jar", 
        "jackson-core-2.16.1.jar",
        "log4j-api-2.20.0.jar",
        "slf4j-api-2.0.9.jar",
        "logback-classic-1.4.14.jar",
        "httpclient5-5.3.jar"
    };

    @BeforeAll
    static void verifyFrameworkJars() {
        System.out.println("🔍 Verifying framework JARs are available...");
        for (String jarName : FRAMEWORKS) {
            Path jarPath = Paths.get("target/test-jars/" + jarName);
            Assumptions.assumeTrue(Files.exists(jarPath), 
                "Framework JAR not found: " + jarName + ". Run 'mvn generate-test-resources' first.");
        }
        System.out.println("✅ All framework JARs available for analysis\n");
    }

    @Test
    void analyzeFrameworkCompression() throws IOException {
        System.out.println("🚀 JARZ Framework Compression Analysis");
        System.out.println("=====================================\n");
        
        List<AnalysisResult> results = new ArrayList<>();
        
        for (String jarName : FRAMEWORKS) {
            AnalysisResult result = analyzeFramework(jarName);
            results.add(result);
            displayFrameworkAnalysis(result);
        }
        
        displaySummaryReport(results);
    }

    private AnalysisResult analyzeFramework(String jarName) throws IOException {
        Path jarPath = Paths.get("target/test-jars/" + jarName);
        
        if (!Files.exists(jarPath)) {
            System.out.println("⚠️  Skipping " + jarName + " (not found)");
            return null;
        }
        
        System.out.println("📦 Analyzing: " + jarName);
        
        // Get original JAR size
        long originalSize = Files.size(jarPath);
        
        // Read classpath for dependency resolution
        Path classpathFile = Paths.get("target/test-classpath.txt");
        String classpath = "";
        if (Files.exists(classpathFile)) {
            classpath = Files.readString(classpathFile).trim();
        }
        
        // Convert to JARZ with classpath
        JarToJarzConverter.ConversionResult conversion = convertWithClasspath(jarPath, classpath);
        long jarzSize = Files.size(conversion.getJarzFile());
        
        // Calculate metrics
        long spaceSaved = originalSize - jarzSize;
        double reductionPercent = ((double) spaceSaved / originalSize) * 100;
        
        AnalysisResult result = new AnalysisResult(
            jarName,
            originalSize,
            jarzSize,
            spaceSaved,
            reductionPercent,
            conversion.getBlockCount(),
            conversion.getTotalEntries()
        );
        
        // Clean up temp file
        Files.deleteIfExists(conversion.getJarzFile());
        
        return result;
    }
    
    private JarToJarzConverter.ConversionResult convertWithClasspath(Path jarPath, String classpath) throws IOException {
        // Set system property for classpath if available
        if (!classpath.isEmpty()) {
            System.setProperty("jarz.analysis.classpath", classpath);
        }
        
        try {
            return JarToJarzConverter.convertToTemp(jarPath);
        } finally {
            // Clean up system property
            System.clearProperty("jarz.analysis.classpath");
        }
    }

    private void displayFrameworkAnalysis(AnalysisResult result) {
        if (result == null) return;
        
        System.out.println("   Original JAR:  " + formatSize(result.originalSize));
        System.out.println("   JARZ Archive:  " + formatSize(result.jarzSize));
        System.out.println("   Space Saved:   " + formatSize(result.spaceSaved) + 
                          " (" + PERCENT_FORMAT.format(result.reductionPercent) + "% reduction)");
        System.out.println("   Blocks:        " + result.blockCount);
        System.out.println("   Entries:       " + SIZE_FORMAT.format(result.totalEntries));
        System.out.println();
    }

    private void displaySummaryReport(List<AnalysisResult> results) {
        System.out.println("📊 SUMMARY REPORT");
        System.out.println("=================\n");
        
        // Filter out null results
        results = results.stream().filter(Objects::nonNull).collect(Collectors.toList());
        
        if (results.isEmpty()) {
            System.out.println("⚠️  No frameworks analyzed");
            return;
        }
        
        // Calculate totals
        long totalOriginal = results.stream().mapToLong(r -> r.originalSize).sum();
        long totalJarz = results.stream().mapToLong(r -> r.jarzSize).sum();
        long totalSaved = totalOriginal - totalJarz;
        double avgReduction = results.stream().mapToDouble(r -> r.reductionPercent).average().orElse(0);
        
        System.out.println("📈 Overall Statistics:");
        System.out.println("   Frameworks Analyzed: " + results.size());
        System.out.println("   Total JAR Size:      " + formatSize(totalOriginal));
        System.out.println("   Total JARZ Size:     " + formatSize(totalJarz));
        System.out.println("   Total Space Saved:   " + formatSize(totalSaved) + 
                          " (" + PERCENT_FORMAT.format(((double)totalSaved/totalOriginal)*100) + "%)");
        System.out.println("   Average Reduction:   " + PERCENT_FORMAT.format(avgReduction) + "%");
        System.out.println();
        
        // Top performers
        results.sort((a, b) -> Double.compare(b.reductionPercent, a.reductionPercent));
        
        System.out.println("🏆 Top Compression Performers:");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            AnalysisResult result = results.get(i);
            System.out.println("   " + (i+1) + ". " + result.frameworkName + 
                              " - " + PERCENT_FORMAT.format(result.reductionPercent) + "% reduction");
        }
        System.out.println();
        
        // Largest space savers
        results.sort((a, b) -> Long.compare(b.spaceSaved, a.spaceSaved));
        
        System.out.println("💾 Largest Space Savers:");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            AnalysisResult result = results.get(i);
            System.out.println("   " + (i+1) + ". " + result.frameworkName + 
                              " - " + formatSize(result.spaceSaved) + " saved");
        }
        System.out.println();
        
        System.out.println("🎯 Conclusion:");
        System.out.println("   JARZ format provides significant compression benefits");
        System.out.println("   across popular Java frameworks with an average " + 
                          PERCENT_FORMAT.format(avgReduction) + "% reduction.");
        System.out.println("   Total bandwidth savings: " + formatSize(totalSaved) + 
                          " across analyzed frameworks.");
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return SIZE_FORMAT.format(bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return SIZE_FORMAT.format(bytes / (1024 * 1024)) + " MB";
        return SIZE_FORMAT.format(bytes / (1024 * 1024 * 1024)) + " GB";
    }

    private static class AnalysisResult {
        final String frameworkName;
        final long originalSize;
        final long jarzSize;
        final long spaceSaved;
        final double reductionPercent;
        final int blockCount;
        final int totalEntries;

        AnalysisResult(String frameworkName, long originalSize, long jarzSize, 
                      long spaceSaved, double reductionPercent, int blockCount, int totalEntries) {
            this.frameworkName = frameworkName;
            this.originalSize = originalSize;
            this.jarzSize = jarzSize;
            this.spaceSaved = spaceSaved;
            this.reductionPercent = reductionPercent;
            this.blockCount = blockCount;
            this.totalEntries = totalEntries;
        }
    }
}
