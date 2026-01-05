package jdk.incubator.jarz.v2;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Assigns classes to blocks based on dependency analysis.
 * Groups related classes together for better compression.
 */
public class BlockAssigner {
    
    private final int targetBlockSize;
    private final int maxBlockSize;
    
    public BlockAssigner() {
        this(JarzV2Format.DEFAULT_BLOCK_SIZE, JarzV2Format.MAX_BLOCK_SIZE);
    }
    
    public BlockAssigner(int targetBlockSize, int maxBlockSize) {
        this.targetBlockSize = targetBlockSize;
        this.maxBlockSize = maxBlockSize;
    }
    
    /**
     * Assign classes to blocks based on dependency graph.
     * 
     * @param classFiles Map of className -> classData
     * @param graph Dependency graph from DependencyAnalyzer
     * @return List of blocks with assigned classes
     */
    public List<Block> assignBlocks(Map<String, byte[]> classFiles, DependencyGraph graph) {
        List<Block> blocks = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        
        // Get topological order (dependencies first)
        List<String> topoOrder = graph.topologicalSort();
        
        // Add any classes not in graph (no dependencies found)
        for (String className : classFiles.keySet()) {
            if (!topoOrder.contains(className)) {
                topoOrder.add(className);
            }
        }
        
        Block currentBlock = new Block(blocks.size());
        
        for (String className : topoOrder) {
            if (assigned.contains(className)) continue;
            
            byte[] classData = classFiles.get(className);
            if (classData == null) continue;
            
            int entrySize = 2 + className.length() + 4 + classData.length;
            
            // Start new block if current would exceed target
            if (currentBlock.size() + entrySize > targetBlockSize && !currentBlock.isEmpty()) {
                blocks.add(currentBlock);
                currentBlock = new Block(blocks.size());
            }
            
            // Add class to current block
            currentBlock.add(className, classData);
            assigned.add(className);
            
            // Pull in strongly connected classes (dependencies that fit)
            pullRelatedClasses(className, classFiles, graph, assigned, currentBlock);
        }
        
        // Add final block
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }
        
        return blocks;
    }
    
    private void pullRelatedClasses(
            String className,
            Map<String, byte[]> classFiles,
            DependencyGraph graph,
            Set<String> assigned,
            Block block
    ) {
        // Get direct dependencies and dependents
        Set<String> related = new HashSet<>();
        related.addAll(graph.dependencies(className));
        related.addAll(graph.dependents(className));
        
        // Sort by size (smaller first to pack more)
        List<String> sortedRelated = related.stream()
            .filter(c -> !assigned.contains(c))
            .filter(classFiles::containsKey)
            .sorted(Comparator.comparingInt(c -> classFiles.get(c).length))
            .toList();
        
        for (String relatedClass : sortedRelated) {
            byte[] classData = classFiles.get(relatedClass);
            if (classData == null) continue;
            
            int entrySize = 2 + relatedClass.length() + 4 + classData.length;
            
            // Stop if would exceed max block size
            if (block.size() + entrySize > maxBlockSize) {
                break;
            }
            
            block.add(relatedClass, classData);
            assigned.add(relatedClass);
        }
    }
    
    /**
     * Simple assignment without dependency analysis.
     * Groups classes by package prefix.
     */
    public List<Block> assignByPackage(Map<String, byte[]> classFiles) {
        // Group by package
        Map<String, List<String>> byPackage = new TreeMap<>();
        
        for (String className : classFiles.keySet()) {
            int lastSlash = className.lastIndexOf('/');
            String pkg = lastSlash > 0 ? className.substring(0, lastSlash) : "";
            byPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(className);
        }
        
        List<Block> blocks = new ArrayList<>();
        Block currentBlock = new Block(blocks.size());
        
        for (List<String> packageClasses : byPackage.values()) {
            for (String className : packageClasses) {
                byte[] classData = classFiles.get(className);
                int entrySize = 2 + className.length() + 4 + classData.length;
                
                if (currentBlock.size() + entrySize > targetBlockSize && !currentBlock.isEmpty()) {
                    blocks.add(currentBlock);
                    currentBlock = new Block(blocks.size());
                }
                
                currentBlock.add(className, classData);
            }
        }
        
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }
        
        return blocks;
    }
}
