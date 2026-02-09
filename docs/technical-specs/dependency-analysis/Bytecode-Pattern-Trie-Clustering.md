# Bytecode Pattern Trie Clustering

**Binary prefix tree analysis of bytecode instruction patterns for optimal class grouping.**

## Overview

Bytecode pattern trie clustering groups classes by analyzing their bytecode instruction sequences using a binary prefix tree (trie) structure. Classes with similar instruction patterns share bytecode sequences, constant pool entries, and method signatures, leading to superior ZSTD compression ratios.

## Core Concept

**Principle**: Extract bytecode instruction patterns from class files and group classes with similar instruction sequences using a trie data structure.

**Advantage**: Works at the lowest level of class similarity - actual bytecode instructions - providing the most accurate grouping for compression.

## Algorithm Architecture

### 1. Instruction Pattern Extraction

```java
public class InstructionPatternExtractor {
    
    public static class InstructionPattern {
        private final byte[] pattern;
        private final int[] opcodeFrequency;
        private final Set<String> constantPoolRefs;
        
        public InstructionPattern(byte[] classData) {
            this.pattern = extractInstructionSequence(classData);
            this.opcodeFrequency = calculateOpcodeFrequency(classData);
            this.constantPoolRefs = extractConstantPoolReferences(classData);
        }
        
        private byte[] extractInstructionSequence(byte[] classData) {
            try {
                ClassReader reader = new ClassReader(classData);
                InstructionExtractor extractor = new InstructionExtractor();
                reader.accept(extractor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return extractor.getInstructionPattern();
            } catch (Exception e) {
                return new byte[0]; // Empty pattern for invalid classes
            }
        }
        
        private int[] calculateOpcodeFrequency(byte[] classData) {
            int[] frequency = new int[256]; // All possible opcodes
            
            try {
                ClassReader reader = new ClassReader(classData);
                OpcodeFrequencyVisitor visitor = new OpcodeFrequencyVisitor(frequency);
                reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Exception e) {
                // Return empty frequency array
            }
            
            return frequency;
        }
        
        public double calculateSimilarity(InstructionPattern other) {
            // Combine multiple similarity metrics
            double patternSim = calculatePatternSimilarity(other);
            double frequencySim = calculateFrequencySimilarity(other);
            double constantSim = calculateConstantPoolSimilarity(other);
            
            return patternSim * 0.5 + frequencySim * 0.3 + constantSim * 0.2;
        }
        
        private double calculatePatternSimilarity(InstructionPattern other) {
            return longestCommonSubsequence(this.pattern, other.pattern) / 
                   (double) Math.max(this.pattern.length, other.pattern.length);
        }
        
        private double calculateFrequencySimilarity(InstructionPattern other) {
            double dotProduct = 0.0;
            double norm1 = 0.0;
            double norm2 = 0.0;
            
            for (int i = 0; i < 256; i++) {
                dotProduct += opcodeFrequency[i] * other.opcodeFrequency[i];
                norm1 += opcodeFrequency[i] * opcodeFrequency[i];
                norm2 += other.opcodeFrequency[i] * other.opcodeFrequency[i];
            }
            
            return norm1 > 0 && norm2 > 0 ? dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2)) : 0.0;
        }
    }
    
    private static class InstructionExtractor extends ClassVisitor {
        private final List<Byte> instructions = new ArrayList<>();
        
        public InstructionExtractor() {
            super(Opcodes.ASM9);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                       String signature, String[] exceptions) {
            return new MethodInstructionVisitor();
        }
        
        public byte[] getInstructionPattern() {
            // Limit pattern size for performance
            int maxSize = Math.min(instructions.size(), 64);
            byte[] pattern = new byte[maxSize];
            
            for (int i = 0; i < maxSize; i++) {
                pattern[i] = instructions.get(i);
            }
            
            return pattern;
        }
        
        private class MethodInstructionVisitor extends MethodVisitor {
            public MethodInstructionVisitor() {
                super(Opcodes.ASM9);
            }
            
            @Override
            public void visitInsn(int opcode) {
                instructions.add((byte) opcode);
            }
            
            @Override
            public void visitIntInsn(int opcode, int operand) {
                instructions.add((byte) opcode);
            }
            
            @Override
            public void visitVarInsn(int opcode, int var) {
                instructions.add((byte) opcode);
            }
            
            @Override
            public void visitTypeInsn(int opcode, String type) {
                instructions.add((byte) opcode);
            }
            
            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                instructions.add((byte) opcode);
            }
            
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, 
                                      String descriptor, boolean isInterface) {
                instructions.add((byte) opcode);
            }
        }
    }
}
```

### 2. Binary Prefix Tree Implementation

```java
public class BytecodePatternTrie {
    private final TrieNode root = new TrieNode();
    
    private static class TrieNode {
        private final Map<Byte, TrieNode> children = new HashMap<>();
        private final Set<String> classes = new HashSet<>();
        private double totalSimilarity = 0.0;
        private int comparisonCount = 0;
        
        public void addSimilarity(double similarity) {
            totalSimilarity += similarity;
            comparisonCount++;
        }
        
        public double getAverageSimilarity() {
            return comparisonCount > 0 ? totalSimilarity / comparisonCount : 0.0;
        }
    }
    
    public void insert(InstructionPattern pattern, String className) {
        TrieNode current = root;
        
        for (byte instruction : pattern.pattern) {
            current = current.children.computeIfAbsent(instruction, k -> new TrieNode());
            current.classes.add(className);
        }
    }
    
    public List<List<String>> extractClusters(int minClusterSize) {
        List<List<String>> clusters = new ArrayList<>();
        extractClustersRecursive(root, clusters, minClusterSize, new ArrayList<>());
        return clusters;
    }
    
    private void extractClustersRecursive(TrieNode node, List<List<String>> clusters, 
                                        int minClusterSize, List<Byte> currentPath) {
        // If this node has enough classes, consider it a cluster
        if (node.classes.size() >= minClusterSize) {
            // Check if this is a good stopping point
            if (shouldCreateCluster(node, minClusterSize)) {
                clusters.add(new ArrayList<>(node.classes));
                return; // Don't recurse further
            }
        }
        
        // Recurse to children
        for (Map.Entry<Byte, TrieNode> entry : node.children.entrySet()) {
            currentPath.add(entry.getKey());
            extractClustersRecursive(entry.getValue(), clusters, minClusterSize, currentPath);
            currentPath.remove(currentPath.size() - 1);
        }
    }
    
    private boolean shouldCreateCluster(TrieNode node, int minClusterSize) {
        // Create cluster if:
        // 1. Node has sufficient classes
        // 2. Children don't have significantly more classes
        // 3. Average similarity is above threshold
        
        if (node.classes.size() < minClusterSize) return false;
        
        int maxChildSize = node.children.values().stream()
            .mapToInt(child -> child.classes.size())
            .max()
            .orElse(0);
        
        // If largest child has >80% of classes, continue recursion
        if (maxChildSize > node.classes.size() * 0.8) return false;
        
        // Check similarity threshold
        return node.getAverageSimilarity() > 0.3;
    }
}
```

### 3. Clustering Algorithm

```java
public class BytecodePatternClusterer {
    private final InstructionPatternExtractor extractor;
    private final double similarityThreshold;
    
    public BytecodePatternClusterer(double similarityThreshold) {
        this.extractor = new InstructionPatternExtractor();
        this.similarityThreshold = similarityThreshold;
    }
    
    public List<Block> clusterByBytecodePatterns(Map<String, byte[]> classFiles, 
                                               DependencyGraph graph) {
        // Extract patterns for all classes
        Map<String, InstructionPattern> patterns = extractAllPatterns(classFiles);
        
        // Build trie
        BytecodePatternTrie trie = buildPatternTrie(patterns);
        
        // Extract clusters
        List<List<String>> clusters = trie.extractClusters(MIN_CLASSES_PER_BLOCK);
        
        // Refine clusters using similarity
        List<List<String>> refinedClusters = refineClusters(clusters, patterns);
        
        // Create blocks
        return createBlocksFromClusters(refinedClusters, classFiles);
    }
    
    private Map<String, InstructionPattern> extractAllPatterns(Map<String, byte[]> classFiles) {
        return classFiles.entrySet().parallelStream()
            .collect(Collectors.toConcurrentMap(
                Map.Entry::getKey,
                entry -> new InstructionPattern(entry.getValue())
            ));
    }
    
    private BytecodePatternTrie buildPatternTrie(Map<String, InstructionPattern> patterns) {
        BytecodePatternTrie trie = new BytecodePatternTrie();
        
        for (Map.Entry<String, InstructionPattern> entry : patterns.entrySet()) {
            trie.insert(entry.getValue(), entry.getKey());
        }
        
        return trie;
    }
    
    private List<List<String>> refineClusters(List<List<String>> clusters, 
                                            Map<String, InstructionPattern> patterns) {
        List<List<String>> refined = new ArrayList<>();
        
        for (List<String> cluster : clusters) {
            if (cluster.size() <= MAX_CLASSES_PER_BLOCK) {
                refined.add(cluster);
            } else {
                // Split large clusters using similarity
                refined.addAll(splitClusterBySimilarity(cluster, patterns));
            }
        }
        
        return refined;
    }
    
    private List<List<String>> splitClusterBySimilarity(List<String> cluster, 
                                                      Map<String, InstructionPattern> patterns) {
        // Use hierarchical clustering to split large clusters
        HierarchicalClusterer hierarchical = new HierarchicalClusterer(similarityThreshold);
        return hierarchical.cluster(cluster, patterns, MAX_CLASSES_PER_BLOCK);
    }
}
```

## Performance Optimizations

### 1. Pattern Caching

```java
public class PatternCache {
    private final Map<String, InstructionPattern> cache = new ConcurrentHashMap<>();
    private final MessageDigest md5;
    
    public PatternCache() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    public InstructionPattern getPattern(String className, byte[] classData) {
        String hash = calculateHash(classData);
        return cache.computeIfAbsent(hash, k -> new InstructionPattern(classData));
    }
    
    private String calculateHash(byte[] data) {
        synchronized (md5) {
            md5.reset();
            byte[] hash = md5.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        }
    }
}
```

### 2. Parallel Trie Construction

```java
public class ParallelTrieBuilder {
    private final int numThreads;
    
    public BytecodePatternTrie buildTrie(Map<String, InstructionPattern> patterns) {
        BytecodePatternTrie trie = new BytecodePatternTrie();
        
        // Partition patterns by thread
        List<List<Map.Entry<String, InstructionPattern>>> partitions = 
            partitionPatterns(patterns, numThreads);
        
        // Build trie sections in parallel
        List<CompletableFuture<Void>> futures = partitions.stream()
            .map(partition -> CompletableFuture.runAsync(() -> {
                for (Map.Entry<String, InstructionPattern> entry : partition) {
                    synchronized (trie) {
                        trie.insert(entry.getValue(), entry.getKey());
                    }
                }
            }))
            .collect(Collectors.toList());
        
        // Wait for completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return trie;
    }
}
```

## Expected Performance

### Compression Improvements
- **Homogeneous JARs**: 8-12% improvement
- **Framework-specific JARs**: 6-10% improvement  
- **Mixed JARs**: 3-7% improvement

### Computational Complexity
- **Pattern extraction**: O(n) where n = number of classes
- **Trie construction**: O(n * p) where p = average pattern length
- **Cluster extraction**: O(n log n)

### Memory Usage
- **Pattern storage**: ~1KB per class pattern
- **Trie structure**: ~2-3x pattern storage
- **Total overhead**: ~3-4KB per class

## Configuration

```java
@ConfigurationProperties("jarz.bytecode.clustering")
public class BytecodeClusteringConfig {
    
    /**
     * Maximum instruction pattern length
     */
    private int maxPatternLength = 64;
    
    /**
     * Similarity threshold for clustering
     */
    private double similarityThreshold = 0.3;
    
    /**
     * Enable pattern caching
     */
    private boolean enableCaching = true;
    
    /**
     * Number of parallel threads for trie construction
     */
    private int parallelThreads = Runtime.getRuntime().availableProcessors();
}
```

## Integration Example

```java
// Integration with EnhancedBlockAssigner
private List<Block> clusterForHomogeneousJars(Map<String, byte[]> classFiles, 
                                            DependencyGraph graph) {
    // Check if JAR is suitable for bytecode clustering
    if (isHomogeneousJar(classFiles)) {
        BytecodePatternClusterer clusterer = new BytecodePatternClusterer(0.3);
        return clusterer.clusterByBytecodePatterns(classFiles, graph);
    }
    
    // Fall back to other strategies
    return clusterByFrameworkPatterns(classFiles, graph);
}

private boolean isHomogeneousJar(Map<String, byte[]> classFiles) {
    // Heuristics for detecting homogeneous JARs
    FrameworkDetectorRegistry detector = new FrameworkDetectorRegistry();
    Map<String, Integer> frameworks = new HashMap<>();
    
    for (String className : classFiles.keySet()) {
        String framework = detector.detectFramework(className);
        frameworks.merge(framework, 1, Integer::sum);
    }
    
    // Homogeneous if >80% classes belong to same framework
    int totalClasses = classFiles.size();
    return frameworks.values().stream()
        .anyMatch(count -> count > totalClasses * 0.8);
}
```

## Conclusion

Bytecode pattern trie clustering provides the deepest level of class similarity analysis by examining actual bytecode instructions. While computationally intensive, it delivers superior compression improvements for homogeneous JARs where classes share similar implementation patterns.
