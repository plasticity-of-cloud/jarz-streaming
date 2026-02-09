/**
 * Enhanced Block Clustering Algorithm for JARZ v2
 * Improves compression ratios by optimizing class grouping strategies
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
package net.jarz.streaming.v2.enhanced;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.DependencyGraph;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced block assigner that uses multiple clustering strategies
 * to optimize compression ratios for different JAR types.
 * 
 * <p>This class automatically detects JAR characteristics and applies
 * the most appropriate clustering strategy:
 * 
 * <ul>
 * <li><strong>Framework JARs</strong>: Groups by functional modules (Flink streaming, Spark SQL)</li>
 * <li><strong>Library JARs</strong>: Groups by package hierarchy for similar bytecode patterns</li>
 * <li><strong>Application JARs</strong>: Groups by dependency strength using graph analysis</li>
 * <li><strong>Utility JARs</strong>: Simple size-based grouping for small libraries</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
 * List<Block> blocks = assigner.assignBlocks(classFiles, dependencyGraph);
 * }</pre>
 * 
 * @see Block
 * @see DependencyGraph
 */
public class EnhancedBlockAssigner {
    
    private static final int DEFAULT_BLOCK_SIZE = 64 * 1024; // 64KB target
    private static final int MAX_BLOCK_SIZE = 256 * 1024;    // 256KB max
    private static final int MIN_CLASSES_PER_BLOCK = 50;     // Increased from 30 for better compression
    private static final int OPTIMAL_CLASSES_PER_BLOCK = 80; // Increased from 50 for better compression
    private static final int MAX_CLASSES_PER_BLOCK = 150;    // Increased from 100 for better compression
    
    private final FrameworkDetectorRegistry frameworkRegistry;
    
    /**
     * Creates a new EnhancedBlockAssigner with framework detection.
     */
    public EnhancedBlockAssigner() {
        this.frameworkRegistry = new FrameworkDetectorRegistry();
    }
    
    /**
     * Assigns classes to blocks using enhanced clustering strategies.
     * 
     * <p>The method analyzes the JAR characteristics and automatically
     * selects the optimal clustering strategy for maximum compression.
     * 
     * @param classFiles Map of class names to bytecode
     * @param dependencyGraph Dependency relationships between classes
     * @return List of optimally clustered blocks
     * @throws IllegalArgumentException if classFiles is null or empty
     */
    public List<Block> assignBlocks(Map<String, byte[]> classFiles, DependencyGraph dependencyGraph) {
        if (classFiles == null || classFiles.isEmpty()) {
            throw new IllegalArgumentException("Class files cannot be null or empty");
        }
        
        // Strategy 1: Analyze JAR characteristics
        JarCharacteristics characteristics = analyzeJar(classFiles, dependencyGraph);
        
        // Strategy 2: Choose optimal clustering approach
        switch (characteristics.getType()) {
            case FRAMEWORK_JAR:
                return clusterByFrameworkPatterns(classFiles, dependencyGraph);
            case LIBRARY_JAR:
                return clusterByPackageHierarchy(classFiles, dependencyGraph);
            case APPLICATION_JAR:
                return clusterByDependencyStrength(classFiles, dependencyGraph);
            case UTILITY_JAR:
                return clusterBySize(classFiles);
            default:
                return clusterByHybridStrategy(classFiles, dependencyGraph);
        }
    }
    
    /**
     * Framework JARs (Spring, Flink, Spark) - cluster by functional modules with optimal block sizes.
     * 
     * <p>Groups classes by framework-specific modules but ensures blocks are large enough
     * for optimal ZSTD compression. Small framework groups are merged together.
     */
    private List<Block> clusterByFrameworkPatterns(Map<String, byte[]> classFiles, DependencyGraph graph) {
        Map<String, List<String>> moduleGroups = new HashMap<>();
        
        for (String className : classFiles.keySet()) {
            String module = extractFrameworkModule(className);
            moduleGroups.computeIfAbsent(module, k -> new ArrayList<>()).add(className);
        }
        
        // Sort framework groups by size (largest first)
        List<Map.Entry<String, List<String>>> sortedGroups = moduleGroups.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toList());
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        // For large homogeneous frameworks (like Guava with 1961 "com" classes),
        // treat as a single framework and create optimal blocks
        if (sortedGroups.size() == 1 || 
            (sortedGroups.size() == 2 && sortedGroups.get(1).getValue().size() < 20)) { // Reduced threshold
            // Single dominant framework - use simple optimal blocking
            List<String> allClasses = new ArrayList<>(classFiles.keySet());
            allClasses.sort(String::compareTo);
            return createOptimalBlocks(allClasses, classFiles, 0, "single-framework");
        }
        
        // Multiple significant frameworks - group by framework but ensure optimal sizes
        List<String> allClasses = new ArrayList<>();
        
        // Process large framework groups first with aggressive grouping
        for (Map.Entry<String, List<String>> entry : sortedGroups) {
            String framework = entry.getKey();
            List<String> classes = entry.getValue();
            
            if (classes.size() >= MIN_CLASSES_PER_BLOCK) { // Use MIN instead of OPTIMAL for more aggressive grouping
                // Large framework: create dedicated blocks with better compression
                classes.sort(String::compareTo);
                
                // For very large frameworks, use larger blocks for better compression
                int targetBlockSize = classes.size() > 500 ? MAX_CLASSES_PER_BLOCK : OPTIMAL_CLASSES_PER_BLOCK;
                blocks.addAll(createOptimalBlocks(classes, classFiles, blockId, framework, targetBlockSize));
                blockId += (classes.size() + targetBlockSize - 1) / targetBlockSize;
            } else {
                // Small framework: add to mixed pool
                allClasses.addAll(classes);
            }
        }
        
        // Create mixed blocks from small frameworks
        if (!allClasses.isEmpty()) {
            allClasses.sort(String::compareTo);
            blocks.addAll(createOptimalBlocks(allClasses, classFiles, blockId, "mixed"));
        }
        
        return blocks;
    }
    
    /**
     * Library JARs - cluster by package hierarchy with optimal block sizes.
     * 
     * <p>Groups classes by package prefixes but ensures minimum block sizes
     * for compression efficiency. Small packages are merged together.
     */
    private List<Block> clusterByPackageHierarchy(Map<String, byte[]> classFiles, DependencyGraph graph) {
        // Group by package prefix (2-3 levels deep)
        Map<String, List<String>> packageGroups = classFiles.keySet().stream()
            .collect(Collectors.groupingBy(this::getPackagePrefix));
        
        // Sort packages by size (largest first)
        List<Map.Entry<String, List<String>>> sortedPackages = packageGroups.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toList());
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        // Process large packages first
        for (Map.Entry<String, List<String>> entry : sortedPackages) {
            List<String> classes = entry.getValue();
            
            if (classes.size() >= MIN_CLASSES_PER_BLOCK) {
                // Sort by class name for better compression (similar names together)
                classes.sort(String::compareTo);
                blocks.addAll(createOptimalBlocks(classes, classFiles, blockId, entry.getKey()));
                blockId += (classes.size() + OPTIMAL_CLASSES_PER_BLOCK - 1) / OPTIMAL_CLASSES_PER_BLOCK;
            }
        }
        
        // Merge small packages into mixed blocks
        List<String> smallPackageClasses = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : sortedPackages) {
            if (entry.getValue().size() < MIN_CLASSES_PER_BLOCK) {
                smallPackageClasses.addAll(entry.getValue());
            }
        }
        
        if (!smallPackageClasses.isEmpty()) {
            smallPackageClasses.sort(String::compareTo);
            blocks.addAll(createOptimalBlocks(smallPackageClasses, classFiles, blockId, "mixed"));
        }
        
        return blocks;
    }
    
    /**
     * Application JARs - cluster by dependency strength with optimal block sizes.
     * 
     * <p>Uses strongly connected components to group classes but ensures
     * optimal block sizes for compression efficiency.
     */
    private List<Block> clusterByDependencyStrength(Map<String, byte[]> classFiles, DependencyGraph graph) {
        // Use dependency graph to create strongly connected components
        List<Set<String>> components = findStronglyConnectedComponents(graph);
        
        // Sort components by size (largest first)
        components.sort((a, b) -> Integer.compare(b.size(), a.size()));
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        // Process large components first
        for (Set<String> component : components) {
            if (component.size() >= MIN_CLASSES_PER_BLOCK) {
                List<String> componentClasses = component.stream()
                    .filter(classFiles::containsKey)
                    .sorted() // Sort for consistent ordering
                    .collect(Collectors.toList());
                    
                if (!componentClasses.isEmpty()) {
                    blocks.addAll(createOptimalBlocks(componentClasses, classFiles, blockId, "dependency"));
                    blockId += (componentClasses.size() + OPTIMAL_CLASSES_PER_BLOCK - 1) / OPTIMAL_CLASSES_PER_BLOCK;
                }
            }
        }
        
        // Merge small components into mixed blocks
        List<String> smallComponentClasses = new ArrayList<>();
        for (Set<String> component : components) {
            if (component.size() < MIN_CLASSES_PER_BLOCK) {
                smallComponentClasses.addAll(component.stream()
                    .filter(classFiles::containsKey)
                    .collect(Collectors.toList()));
            }
        }
        
        if (!smallComponentClasses.isEmpty()) {
            smallComponentClasses.sort(String::compareTo);
            blocks.addAll(createOptimalBlocks(smallComponentClasses, classFiles, blockId, "mixed"));
        }
        
        return blocks;
    }
    
    /**
     * Utility JARs - simple size-based clustering with optimal block sizes.
     * 
     * <p>For small utility libraries, uses size-based grouping to create
     * optimally sized blocks for compression efficiency.
     */
    private List<Block> clusterBySize(Map<String, byte[]> classFiles) {
        List<String> allClasses = new ArrayList<>(classFiles.keySet());
        // Sort by class name for consistent ordering
        allClasses.sort(String::compareTo);
        
        return createOptimalBlocks(allClasses, classFiles, 0, "utility");
    }
    
    /**
     * Hybrid strategy combining multiple approaches.
     * 
     * <p>Falls back to package-based clustering if dependency-based
     * clustering produces too few blocks, ensuring reasonable block distribution.
     */
    private List<Block> clusterByHybridStrategy(Map<String, byte[]> classFiles, DependencyGraph graph) {
        // Try dependency-based clustering first
        List<Block> dependencyBlocks = clusterByDependencyStrength(classFiles, graph);
        
        // If dependency clustering creates too few blocks, fall back to package-based
        if (dependencyBlocks.size() < 3) {
            return clusterByPackageHierarchy(classFiles, graph);
        }
        
        return dependencyBlocks;
    }
    
    /**
     * Creates optimally-sized blocks from a list of classes.
     * 
     * <p>This method ensures blocks are sized for optimal ZSTD compression
     * by targeting OPTIMAL_CLASSES_PER_BLOCK classes per block.
     * 
     * @param classes List of class names to group into blocks
     * @param classFiles Map of class names to bytecode
     * @param startBlockId Starting block ID for numbering
     * @param context Context string for debugging (framework name, etc.)
     * @return List of optimally-sized blocks
     */
    private List<Block> createOptimalBlocks(List<String> classes, Map<String, byte[]> classFiles, 
                                          int startBlockId, String context) {
        List<Block> blocks = new ArrayList<>();
        
        if (classes.isEmpty()) {
            return blocks;
        }
        
        int blockId = startBlockId;
        
        // Create blocks with exactly OPTIMAL_CLASSES_PER_BLOCK classes each
        for (int i = 0; i < classes.size(); i += OPTIMAL_CLASSES_PER_BLOCK) {
            Block block = new Block(blockId++);
            int end = Math.min(i + OPTIMAL_CLASSES_PER_BLOCK, classes.size());
            
            // Add classes to block
            for (int j = i; j < end; j++) {
                String className = classes.get(j);
                byte[] classData = classFiles.get(className);
                if (classData != null) {
                    block.add(className, classData);
                }
            }
            
            // Always add the block - we'll ensure it has enough classes
            if (block.entryCount() > 0) {
                blocks.add(block);
            }
        }
        
        // If the last block is too small, merge it with the previous block
        if (blocks.size() > 1) {
            Block lastBlock = blocks.get(blocks.size() - 1);
            if (lastBlock.entryCount() < MIN_CLASSES_PER_BLOCK) {
                Block secondLastBlock = blocks.get(blocks.size() - 2);
                
                // Merge last block into second-to-last block
                for (Block.ClassEntry entry : lastBlock.entries()) {
                    secondLastBlock.add(entry.className(), entry.classData());
                }
                
                // Remove the small last block
                blocks.remove(blocks.size() - 1);
            }
        }
        
        return blocks;
    }
    
    /**
     * Creates optimal blocks with custom target block size for better compression.
     * 
     * @param classes List of class names to group
     * @param classFiles Map of class names to class data
     * @param startBlockId Starting block ID for numbering
     * @param context Context string for debugging (framework name, etc.)
     * @param targetBlockSize Target number of classes per block
     * @return List of optimally-sized blocks
     */
    private List<Block> createOptimalBlocks(List<String> classes, Map<String, byte[]> classFiles, 
                                          int startBlockId, String context, int targetBlockSize) {
        List<Block> blocks = new ArrayList<>();
        
        if (classes.isEmpty()) {
            return blocks;
        }
        
        int blockId = startBlockId;
        
        // Create blocks with custom target size for better compression
        for (int i = 0; i < classes.size(); i += targetBlockSize) {
            Block block = new Block(blockId++);
            int end = Math.min(i + targetBlockSize, classes.size());
            
            // Add classes to block
            for (int j = i; j < end; j++) {
                String className = classes.get(j);
                byte[] classData = classFiles.get(className);
                if (classData != null) {
                    block.add(className, classData);
                }
            }
            
            // Always add the block - we'll ensure it has enough classes
            if (block.entryCount() > 0) {
                blocks.add(block);
            }
        }
        
        // If the last block is too small, merge it with the previous block
        if (blocks.size() > 1) {
            Block lastBlock = blocks.get(blocks.size() - 1);
            if (lastBlock.entryCount() < MIN_CLASSES_PER_BLOCK) {
                Block secondLastBlock = blocks.get(blocks.size() - 2);
                
                // Merge last block into second-to-last block
                for (Block.ClassEntry entry : lastBlock.entries()) {
                    secondLastBlock.add(entry.className(), entry.classData());
                }
                
                // Remove the small last block
                blocks.remove(blocks.size() - 1);
            }
        }
        
        return blocks;
    }
    
    // Helper methods
    
    /**
     * Analyzes JAR characteristics to determine optimal clustering strategy.
     */
    private JarCharacteristics analyzeJar(Map<String, byte[]> classFiles, DependencyGraph graph) {
        int totalClasses = classFiles.size();
        int totalDependencies = graph != null ? calculateTotalEdges(graph) : 0;
        double avgDependenciesPerClass = totalClasses > 0 ? (double) totalDependencies / totalClasses : 0;
        
        // Analyze package distribution
        Set<String> packages = classFiles.keySet().stream()
            .map(this::getPackagePrefix)
            .collect(Collectors.toSet());
        
        // Determine JAR type based on characteristics
        if (totalClasses > 1000 && packages.size() > 50) {
            return new JarCharacteristics(JarType.FRAMEWORK_JAR, totalClasses, avgDependenciesPerClass);
        } else if (avgDependenciesPerClass > 5.0) {
            return new JarCharacteristics(JarType.APPLICATION_JAR, totalClasses, avgDependenciesPerClass);
        } else if (packages.size() > 10) {
            return new JarCharacteristics(JarType.LIBRARY_JAR, totalClasses, avgDependenciesPerClass);
        } else {
            return new JarCharacteristics(JarType.UTILITY_JAR, totalClasses, avgDependenciesPerClass);
        }
    }
    
    /**
     * Extracts framework-specific module patterns from class names.
     */
    private String extractFrameworkModule(String className) {
        return frameworkRegistry.detectFramework(className);
    }
    
    /**
     * Extracts package prefix (2-3 levels) from class name.
     */
    private String getPackagePrefix(String className) {
        String[] parts = className.split("\\.");
        if (parts.length >= 3) {
            return String.join(".", Arrays.copyOf(parts, 3));
        }
        return parts.length > 0 ? parts[0] : "default";
    }
    
    /**
     * Calculates optimal block size based on class characteristics.
     * Now returns consistent optimal size for better compression.
     */
    private int getOptimalBlockSize(List<String> classes, int startIndex) {
        return OPTIMAL_CLASSES_PER_BLOCK; // Consistent optimal size
    }
    
    /**
     * Finds strongly connected components using simplified algorithm.
     * 
     * <p>In practice, this should use Tarjan's or Kosaraju's algorithm
     * for better performance on large graphs.
     */
    private List<Set<String>> findStronglyConnectedComponents(DependencyGraph graph) {
        if (graph == null) {
            return Collections.emptyList();
        }
        
        List<Set<String>> components = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        for (String node : graph.classes()) {
            if (!visited.contains(node)) {
                Set<String> component = new HashSet<>();
                dfs(node, graph, visited, component);
                if (component.size() >= MIN_CLASSES_PER_BLOCK) {
                    components.add(component);
                }
            }
        }
        
        return components;
    }
    
    /**
     * Depth-first search for component discovery.
     */
    private void dfs(String node, DependencyGraph graph, Set<String> visited, Set<String> component) {
        visited.add(node);
        component.add(node);
        
        for (String neighbor : graph.dependencies(node)) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, graph, visited, component);
            }
        }
    }
    
    // Supporting classes
    
    /**
     * Enumeration of JAR types for clustering strategy selection.
     */
    public enum JarType {
        /** Large frameworks like Flink, Spark, Spring */
        FRAMEWORK_JAR,
        /** Standard libraries with package hierarchies */
        LIBRARY_JAR,
        /** Application code with complex dependencies */
        APPLICATION_JAR,
        /** Small utility libraries */
        UTILITY_JAR
    }
    
    /**
     * Characteristics of a JAR file used for strategy selection.
     */
    public static class JarCharacteristics {
        private final JarType type;
        private final int classCount;
        private final double avgDependencies;
        
        /**
         * Creates JAR characteristics.
         * 
         * @param type The detected JAR type
         * @param classCount Total number of classes
         * @param avgDependencies Average dependencies per class
         */
        public JarCharacteristics(JarType type, int classCount, double avgDependencies) {
            this.type = type;
            this.classCount = classCount;
            this.avgDependencies = avgDependencies;
        }
        
        /** @return The JAR type */
        public JarType getType() { return type; }
        
        /** @return The class count */
        public int getClassCount() { return classCount; }
        
        /** @return The average dependencies per class */
        public double getAvgDependencies() { return avgDependencies; }
        
        @Override
        public String toString() {
            return String.format("JarCharacteristics{type=%s, classes=%d, avgDeps=%.2f}", 
                type, classCount, avgDependencies);
        }
    }
    
    /**
     * Helper method to calculate total edges in dependency graph.
     */
    private int calculateTotalEdges(DependencyGraph graph) {
        return graph.classes().stream()
            .mapToInt(cls -> graph.dependencies(cls).size())
            .sum();
    }
}
