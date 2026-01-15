package net.jarz.streaming.v2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Utility for converting JAR files to JARZ v2 format with dependency-aware block grouping.
 * 
 * <p>This utility provides a standardized way to convert JAR files to JARZ v2 format
 * across different components (CLI tools, integration tests, etc.). It handles:
 * <ul>
 * <li>JAR file extraction and content analysis</li>
 * <li>Dependency analysis using jdeps</li>
 * <li>Block assignment based on dependency relationships</li>
 * <li>Multi-block JARZ v2 archive creation</li>
 * </ul>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarToJarzConverter {
    
    /**
     * Conversion result containing statistics and metadata.
     */
    public static class ConversionResult {
        private final Path jarzFile;
        private final long originalSize;
        private final long jarzSize;
        private final int totalEntries;
        private final int blockCount;
        
        public ConversionResult(Path jarzFile, long originalSize, long jarzSize, int totalEntries, int blockCount) {
            this.jarzFile = jarzFile;
            this.originalSize = originalSize;
            this.jarzSize = jarzSize;
            this.totalEntries = totalEntries;
            this.blockCount = blockCount;
        }
        
        public Path getJarzFile() { return jarzFile; }
        public long getOriginalSize() { return originalSize; }
        public long getJarzSize() { return jarzSize; }
        public int getTotalEntries() { return totalEntries; }
        public int getBlockCount() { return blockCount; }
        
        public double getCompressionRatio() {
            return (double)(originalSize - jarzSize) / originalSize * 100;
        }
    }
    
    /**
     * Converts a JAR file to JARZ v2 format with dependency-aware block grouping.
     * 
     * @param jarFile path to the input JAR file
     * @param jarzFile path to the output JARZ file
     * @return conversion result with statistics
     * @throws IOException if conversion fails
     */
    public static ConversionResult convert(Path jarFile, Path jarzFile) throws IOException {
        if (!Files.exists(jarFile)) {
            throw new IOException("JAR file not found: " + jarFile);
        }
        
        long originalSize = Files.size(jarFile);
        
        // Step 1: Analyze dependencies using jdeps
        DependencyAnalyzer analyzer = new DependencyAnalyzer();
        DependencyGraph graph = analyzer.analyze(jarFile);
        
        // Step 2: Extract class files from JAR
        Map<String, byte[]> classFiles = new HashMap<>();
        Map<String, byte[]> resourceFiles = new HashMap<>();
        
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            for (JarEntry entry : jar.stream().collect(Collectors.toList())) {
                if (!entry.isDirectory()) {
                    byte[] content = jar.getInputStream(entry).readAllBytes();
                    if (entry.getName().endsWith(".class")) {
                        classFiles.put(entry.getName(), content);
                    } else {
                        // Process manifest files to update Class-Path entries
                        if (ManifestProcessor.isManifestFile(entry.getName())) {
                            content = ManifestProcessor.processManifest(content);
                        }
                        resourceFiles.put(entry.getName(), content);
                    }
                }
            }
        }
        
        // Step 3: Assign classes to blocks based on dependencies
        BlockAssigner assigner = new BlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        // Step 4: Add resources to appropriate blocks
        if (!resourceFiles.isEmpty()) {
            Block resourceBlock = new Block(blocks.size());
            for (Map.Entry<String, byte[]> entry : resourceFiles.entrySet()) {
                resourceBlock.add(entry.getKey(), entry.getValue());
            }
            blocks.add(resourceBlock);
        }
        
        // Step 5: Write multi-block JARZ v2
        try (BlockWriter blockWriter = new BlockWriter(jarzFile)) {
            for (Block block : blocks) {
                blockWriter.writeBlock(block);
            }
        }
        
        long jarzSize = Files.size(jarzFile);
        int totalEntries = classFiles.size() + resourceFiles.size();
        
        return new ConversionResult(jarzFile, originalSize, jarzSize, totalEntries, blocks.size());
    }
    
    /**
     * Converts a JAR file to a temporary JARZ file.
     * 
     * @param jarFile path to the input JAR file
     * @return conversion result with temporary JARZ file
     * @throws IOException if conversion fails
     */
    public static ConversionResult convertToTemp(Path jarFile) throws IOException {
        String baseName = jarFile.getFileName().toString();
        if (baseName.endsWith(".jar")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        Path tempJarz = Files.createTempFile(baseName, ".jarz");
        return convert(jarFile, tempJarz);
    }
}
