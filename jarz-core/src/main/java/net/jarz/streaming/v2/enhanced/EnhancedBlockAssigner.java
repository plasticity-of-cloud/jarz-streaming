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
    private static final int MIN_CLASSES_PER_BLOCK = 5;      // Minimum for compression efficiency
    
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
     * Framework JARs (Spring, Flink, Spark) - cluster by functional modules.
     * 
     * <p>Groups classes by framework-specific modules for optimal compression.
     * For example, Flink streaming classes are grouped together as they share
     * similar bytecode patterns and dependencies.
     */
    private List<Block> clusterByFrameworkPatterns(Map<String, byte[]> classFiles, DependencyGraph graph) {
        Map<String, List<String>> moduleGroups = new HashMap<>();
        
        for (String className : classFiles.keySet()) {
            String module = extractFrameworkModule(className);
            moduleGroups.computeIfAbsent(module, k -> new ArrayList<>()).add(className);
        }
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        for (Map.Entry<String, List<String>> entry : moduleGroups.entrySet()) {
            List<String> classes = entry.getValue();
            
            // Split large modules into multiple blocks
            for (int i = 0; i < classes.size(); i += getOptimalBlockSize(classes, i)) {
                Block block = new Block(blockId++);
                int end = Math.min(i + getOptimalBlockSize(classes, i), classes.size());
                
                for (int j = i; j < end; j++) {
                    String className = classes.get(j);
                    block.add(className, classFiles.get(className));
                }
                
                if (block.size() >= MIN_CLASSES_PER_BLOCK) {
                    blocks.add(block);
                }
            }
        }
        
        return blocks;
    }
    
    /**
     * Library JARs - cluster by package hierarchy for better compression.
     * 
     * <p>Groups classes by package prefixes as classes in the same package
     * typically have similar bytecode patterns and compression characteristics.
     */
    private List<Block> clusterByPackageHierarchy(Map<String, byte[]> classFiles, DependencyGraph graph) {
        // Group by package prefix (2-3 levels deep)
        Map<String, List<String>> packageGroups = classFiles.keySet().stream()
            .collect(Collectors.groupingBy(this::getPackagePrefix));
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        for (Map.Entry<String, List<String>> entry : packageGroups.entrySet()) {
            List<String> classes = entry.getValue();
            
            // Sort by class name for better compression (similar names together)
            classes.sort(String::compareTo);
            
            Block block = new Block(blockId++);
            int currentSize = 0;
            
            for (String className : classes) {
                byte[] classData = classFiles.get(className);
                
                if (currentSize + classData.length > MAX_BLOCK_SIZE && block.size() >= MIN_CLASSES_PER_BLOCK) {
                    blocks.add(block);
                    block = new Block(blockId++);
                    currentSize = 0;
                }
                
                block.add(className, classData);
                currentSize += classData.length;
            }
            
            if (block.size() >= MIN_CLASSES_PER_BLOCK) {
                blocks.add(block);
            }
        }
        
        return blocks;
    }
    
    /**
     * Application JARs - cluster by dependency strength.
     * 
     * <p>Uses strongly connected components to group classes that have
     * high interdependency, which typically results in better compression
     * due to similar usage patterns and bytecode structures.
     */
    private List<Block> clusterByDependencyStrength(Map<String, byte[]> classFiles, DependencyGraph graph) {
        // Use dependency graph to create strongly connected components
        List<Set<String>> components = findStronglyConnectedComponents(graph);
        
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        for (Set<String> component : components) {
            if (component.size() < MIN_CLASSES_PER_BLOCK) {
                continue; // Skip small components
            }
            
            Block block = new Block(blockId++);
            int currentSize = 0;
            
            for (String className : component) {
                if (classFiles.containsKey(className)) {
                    byte[] classData = classFiles.get(className);
                    
                    if (currentSize + classData.length > MAX_BLOCK_SIZE && block.size() >= MIN_CLASSES_PER_BLOCK) {
                        blocks.add(block);
                        block = new Block(blockId++);
                        currentSize = 0;
                    }
                    
                    block.add(className, classData);
                    currentSize += classData.length;
                }
            }
            
            if (block.size() >= MIN_CLASSES_PER_BLOCK) {
                blocks.add(block);
            }
        }
        
        return blocks;
    }
    
    /**
     * Utility JARs - simple size-based clustering.
     * 
     * <p>For small utility libraries, uses simple size-based grouping
     * to create reasonably sized blocks without complex analysis overhead.
     */
    private List<Block> clusterBySize(Map<String, byte[]> classFiles) {
        List<Map.Entry<String, byte[]>> sortedClasses = classFiles.entrySet().stream()
            .sorted(Map.Entry.<String, byte[]>comparingByValue((a, b) -> Integer.compare(b.length, a.length)))
            .collect(Collectors.toList());
        
        List<Block> blocks = new ArrayList<>();
        Block currentBlock = new Block(0);
        int currentSize = 0;
        int blockId = 0;
        
        for (Map.Entry<String, byte[]> entry : sortedClasses) {
            String className = entry.getKey();
            byte[] classData = entry.getValue();
            
            if (currentSize + classData.length > DEFAULT_BLOCK_SIZE && currentBlock.size() >= MIN_CLASSES_PER_BLOCK) {
                blocks.add(currentBlock);
                currentBlock = new Block(++blockId);
                currentSize = 0;
            }
            
            currentBlock.add(className, classData);
            currentSize += classData.length;
        }
        
        if (currentBlock.size() >= MIN_CLASSES_PER_BLOCK) {
            blocks.add(currentBlock);
        }
        
        return blocks;
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
     */
    private int getOptimalBlockSize(List<String> classes, int startIndex) {
        // Dynamic block sizing based on class characteristics
        return Math.min(50, classes.size() - startIndex); // Max 50 classes per block
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
