# Semantic Dependency Graph Clustering

**Advanced class relationship analysis for optimal JARZ block composition.**

## Overview

Semantic dependency graph clustering analyzes actual class relationships (imports, method calls, field references) to create blocks where classes have high inter-dependency. This approach is particularly effective for multi-framework JARs where simple framework detection is insufficient.

## Core Concept

**Principle**: Classes that reference each other heavily should be grouped together as they share constant pools, method signatures, and type references, leading to better compression.

**Advantage**: Works with actual code relationships rather than naming conventions or heuristics.

## Algorithm Architecture

### 1. Dependency Analysis

```java
public class ClassDependencyAnalyzer {
    
    public static class ClassDependencies {
        private final String className;
        private final Set<String> imports = new HashSet<>();
        private final Set<String> methodCalls = new HashSet<>();
        private final Set<String> fieldReferences = new HashSet<>();
        private final Set<String> annotations = new HashSet<>();
        private final Set<String> typeReferences = new HashSet<>();
        private final Set<String> constantReferences = new HashSet<>();
        
        public ClassDependencies(String className) {
            this.className = className;
        }
        
        public double calculateSimilarity(ClassDependencies other) {
            // Weighted Jaccard similarity across dependency types
            double importSim = jaccardSimilarity(imports, other.imports) * 0.25;
            double methodSim = jaccardSimilarity(methodCalls, other.methodCalls) * 0.30;
            double fieldSim = jaccardSimilarity(fieldReferences, other.fieldReferences) * 0.20;
            double typeSim = jaccardSimilarity(typeReferences, other.typeReferences) * 0.15;
            double annotationSim = jaccardSimilarity(annotations, other.annotations) * 0.05;
            double constantSim = jaccardSimilarity(constantReferences, other.constantReferences) * 0.05;
            
            return importSim + methodSim + fieldSim + typeSim + annotationSim + constantSim;
        }
        
        private double jaccardSimilarity(Set<String> set1, Set<String> set2) {
            if (set1.isEmpty() && set2.isEmpty()) return 1.0;
            
            Set<String> intersection = new HashSet<>(set1);
            intersection.retainAll(set2);
            
            Set<String> union = new HashSet<>(set1);
            union.addAll(set2);
            
            return (double) intersection.size() / union.size();
        }
    }
    
    public ClassDependencies analyzeDependencies(String className, byte[] classData) {
        ClassDependencies deps = new ClassDependencies(className);
        
        try {
            ClassReader reader = new ClassReader(classData);
            DependencyVisitor visitor = new DependencyVisitor(deps);
            reader.accept(visitor, ClassReader.SKIP_DEBUG);
        } catch (Exception e) {
            // Log error but continue with empty dependencies
            log.warn("Failed to analyze dependencies for {}: {}", className, e.getMessage());
        }
        
        return deps;
    }
    
    private static class DependencyVisitor extends ClassVisitor {
        private final ClassDependencies dependencies;
        
        public DependencyVisitor(ClassDependencies dependencies) {
            super(Opcodes.ASM9);
            this.dependencies = dependencies;
        }
        
        @Override
        public void visit(int version, int access, String name, String signature, 
                         String superName, String[] interfaces) {
            // Add superclass and interfaces as type references
            if (superName != null && !superName.equals("java/lang/Object")) {
                dependencies.typeReferences.add(superName.replace('/', '.'));
            }
            
            if (interfaces != null) {
                for (String iface : interfaces) {
                    dependencies.typeReferences.add(iface.replace('/', '.'));
                }
            }
        }
        
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            dependencies.annotations.add(Type.getType(descriptor).getClassName());
            return null;
        }
        
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, 
                                     String signature, Object value) {
            // Add field type as type reference
            Type fieldType = Type.getType(descriptor);
            if (fieldType.getSort() == Type.OBJECT) {
                dependencies.typeReferences.add(fieldType.getClassName());
            }
            
            return null;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, 
                                       String signature, String[] exceptions) {
            // Add method parameter and return types
            Type methodType = Type.getType(descriptor);
            
            // Return type
            Type returnType = methodType.getReturnType();
            if (returnType.getSort() == Type.OBJECT) {
                dependencies.typeReferences.add(returnType.getClassName());
            }
            
            // Parameter types
            for (Type paramType : methodType.getArgumentTypes()) {
                if (paramType.getSort() == Type.OBJECT) {
                    dependencies.typeReferences.add(paramType.getClassName());
                }
            }
            
            // Exception types
            if (exceptions != null) {
                for (String exception : exceptions) {
                    dependencies.typeReferences.add(exception.replace('/', '.'));
                }
            }
            
            return new MethodDependencyVisitor(dependencies);
        }
    }
    
    private static class MethodDependencyVisitor extends MethodVisitor {
        private final ClassDependencies dependencies;
        
        public MethodDependencyVisitor(ClassDependencies dependencies) {
            super(Opcodes.ASM9);
            this.dependencies = dependencies;
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, 
                                  String descriptor, boolean isInterface) {
            String ownerClass = owner.replace('/', '.');
            dependencies.methodCalls.add(ownerClass + "." + name);
            dependencies.typeReferences.add(ownerClass);
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String ownerClass = owner.replace('/', '.');
            dependencies.fieldReferences.add(ownerClass + "." + name);
            dependencies.typeReferences.add(ownerClass);
        }
        
        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof String) {
                dependencies.constantReferences.add("STRING:" + value);
            } else if (value instanceof Type) {
                Type type = (Type) value;
                if (type.getSort() == Type.OBJECT) {
                    dependencies.typeReferences.add(type.getClassName());
                }
            }
        }
    }
}
```

### 2. Similarity Matrix Construction

```java
public class SemanticSimilarityMatrix {
    private final Map<String, ClassDependencies> dependencies;
    private final double[][] similarityMatrix;
    private final List<String> classNames;
    
    public SemanticSimilarityMatrix(Map<String, ClassDependencies> dependencies) {
        this.dependencies = dependencies;
        this.classNames = new ArrayList<>(dependencies.keySet());
        this.similarityMatrix = buildSimilarityMatrix();
    }
    
    private double[][] buildSimilarityMatrix() {
        int size = classNames.size();
        double[][] matrix = new double[size][size];
        
        // Parallel computation for large matrices
        IntStream.range(0, size).parallel().forEach(i -> {
            ClassDependencies deps1 = dependencies.get(classNames.get(i));
            
            for (int j = i; j < size; j++) {
                ClassDependencies deps2 = dependencies.get(classNames.get(j));
                
                double similarity = (i == j) ? 1.0 : deps1.calculateSimilarity(deps2);
                matrix[i][j] = similarity;
                matrix[j][i] = similarity; // Symmetric matrix
            }
        });
        
        return matrix;
    }
    
    public double getSimilarity(String class1, String class2) {
        int index1 = classNames.indexOf(class1);
        int index2 = classNames.indexOf(class2);
        
        if (index1 == -1 || index2 == -1) return 0.0;
        
        return similarityMatrix[index1][index2];
    }
    
    public List<String> getMostSimilarClasses(String className, int count) {
        int index = classNames.indexOf(className);
        if (index == -1) return Collections.emptyList();
        
        return IntStream.range(0, classNames.size())
            .filter(i -> i != index)
            .boxed()
            .sorted((i, j) -> Double.compare(similarityMatrix[index][j], similarityMatrix[index][i]))
            .limit(count)
            .map(classNames::get)
            .collect(Collectors.toList());
    }
}
```

### 3. Community Detection Algorithm

```java
public class LouvainCommunityDetection {
    private final SemanticSimilarityMatrix similarityMatrix;
    private final double minSimilarityThreshold;
    
    public LouvainCommunityDetection(SemanticSimilarityMatrix matrix, double threshold) {
        this.similarityMatrix = matrix;
        this.minSimilarityThreshold = threshold;
    }
    
    public List<Set<String>> detectCommunities(List<String> classNames, int minCommunitySize) {
        // Initialize each class as its own community
        Map<String, Integer> classToCommunity = new HashMap<>();
        Map<Integer, Set<String>> communities = new HashMap<>();
        
        for (int i = 0; i < classNames.size(); i++) {
            String className = classNames.get(i);
            classToCommunity.put(className, i);
            communities.put(i, new HashSet<>(Collections.singleton(className)));
        }
        
        boolean improved = true;
        int iteration = 0;
        
        while (improved && iteration < 100) {
            improved = false;
            iteration++;
            
            // Shuffle classes for random order processing
            List<String> shuffledClasses = new ArrayList<>(classNames);
            Collections.shuffle(shuffledClasses);
            
            for (String className : shuffledClasses) {
                int currentCommunity = classToCommunity.get(className);
                int bestCommunity = findBestCommunity(className, classToCommunity, communities);
                
                if (bestCommunity != currentCommunity) {
                    // Move class to better community
                    moveClassToCommunity(className, currentCommunity, bestCommunity, 
                                       classToCommunity, communities);
                    improved = true;
                }
            }
            
            // Merge small communities
            mergeSmallCommunities(communities, minCommunitySize, classToCommunity);
        }
        
        // Filter out communities that are too small
        return communities.values().stream()
            .filter(community -> community.size() >= minCommunitySize)
            .collect(Collectors.toList());
    }
    
    private int findBestCommunity(String className, Map<String, Integer> classToCommunity, 
                                 Map<Integer, Set<String>> communities) {
        int currentCommunity = classToCommunity.get(className);
        double currentModularity = calculateModularityGain(className, currentCommunity, communities);
        
        int bestCommunity = currentCommunity;
        double bestModularity = currentModularity;
        
        // Check neighboring communities (classes with high similarity)
        List<String> neighbors = similarityMatrix.getMostSimilarClasses(className, 20);
        Set<Integer> neighborCommunities = neighbors.stream()
            .map(classToCommunity::get)
            .filter(community -> community != currentCommunity)
            .collect(Collectors.toSet());
        
        for (Integer community : neighborCommunities) {
            double modularity = calculateModularityGain(className, community, communities);
            if (modularity > bestModularity) {
                bestModularity = modularity;
                bestCommunity = community;
            }
        }
        
        return bestCommunity;
    }
    
    private double calculateModularityGain(String className, int targetCommunity, 
                                         Map<Integer, Set<String>> communities) {
        Set<String> community = communities.get(targetCommunity);
        if (community == null) return 0.0;
        
        double totalSimilarity = 0.0;
        int connections = 0;
        
        for (String otherClass : community) {
            if (!otherClass.equals(className)) {
                double similarity = similarityMatrix.getSimilarity(className, otherClass);
                if (similarity > minSimilarityThreshold) {
                    totalSimilarity += similarity;
                    connections++;
                }
            }
        }
        
        return connections > 0 ? totalSimilarity / connections : 0.0;
    }
    
    private void moveClassToCommunity(String className, int fromCommunity, int toCommunity,
                                    Map<String, Integer> classToCommunity,
                                    Map<Integer, Set<String>> communities) {
        // Remove from old community
        communities.get(fromCommunity).remove(className);
        if (communities.get(fromCommunity).isEmpty()) {
            communities.remove(fromCommunity);
        }
        
        // Add to new community
        communities.computeIfAbsent(toCommunity, k -> new HashSet<>()).add(className);
        classToCommunity.put(className, toCommunity);
    }
    
    private void mergeSmallCommunities(Map<Integer, Set<String>> communities, 
                                     int minSize, Map<String, Integer> classToCommunity) {
        List<Integer> smallCommunities = communities.entrySet().stream()
            .filter(entry -> entry.getValue().size() < minSize)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        for (Integer smallCommunity : smallCommunities) {
            Set<String> classes = communities.get(smallCommunity);
            
            // Find best community to merge with
            Integer bestTarget = findBestMergeTarget(classes, communities, smallCommunity);
            
            if (bestTarget != null) {
                // Merge communities
                communities.get(bestTarget).addAll(classes);
                for (String className : classes) {
                    classToCommunity.put(className, bestTarget);
                }
                communities.remove(smallCommunity);
            }
        }
    }
    
    private Integer findBestMergeTarget(Set<String> classes, Map<Integer, Set<String>> communities, 
                                      Integer excludeCommunity) {
        double bestSimilarity = 0.0;
        Integer bestTarget = null;
        
        for (Map.Entry<Integer, Set<String>> entry : communities.entrySet()) {
            if (entry.getKey().equals(excludeCommunity)) continue;
            
            double similarity = calculateCommunityToCommunitySimilarity(classes, entry.getValue());
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestTarget = entry.getKey();
            }
        }
        
        return bestTarget;
    }
    
    private double calculateCommunityToCommunitySimilarity(Set<String> community1, Set<String> community2) {
        double totalSimilarity = 0.0;
        int pairs = 0;
        
        for (String class1 : community1) {
            for (String class2 : community2) {
                totalSimilarity += similarityMatrix.getSimilarity(class1, class2);
                pairs++;
            }
        }
        
        return pairs > 0 ? totalSimilarity / pairs : 0.0;
    }
}
```

### 4. Integration with Block Creation

```java
public class SemanticDependencyClusterer {
    private final ClassDependencyAnalyzer analyzer;
    private final double similarityThreshold;
    
    public SemanticDependencyClusterer(double similarityThreshold) {
        this.analyzer = new ClassDependencyAnalyzer();
        this.similarityThreshold = similarityThreshold;
    }
    
    public List<Block> clusterBySemanticDependencies(Map<String, byte[]> classFiles, 
                                                   DependencyGraph graph) {
        // Analyze dependencies for all classes
        Map<String, ClassDependencies> dependencies = new HashMap<>();
        
        for (Map.Entry<String, byte[]> entry : classFiles.entrySet()) {
            ClassDependencies deps = analyzer.analyzeDependencies(entry.getKey(), entry.getValue());
            dependencies.put(entry.getKey(), deps);
        }
        
        // Build similarity matrix
        SemanticSimilarityMatrix similarityMatrix = new SemanticSimilarityMatrix(dependencies);
        
        // Detect communities
        LouvainCommunityDetection detector = new LouvainCommunityDetection(
            similarityMatrix, similarityThreshold);
        
        List<Set<String>> communities = detector.detectCommunities(
            new ArrayList<>(classFiles.keySet()), MIN_CLASSES_PER_BLOCK);
        
        // Create blocks from communities
        List<Block> blocks = new ArrayList<>();
        int blockId = 0;
        
        // Sort communities by strength (internal similarity)
        communities.sort((c1, c2) -> Double.compare(
            calculateCommunityStrength(c2, similarityMatrix),
            calculateCommunityStrength(c1, similarityMatrix)
        ));
        
        for (Set<String> community : communities) {
            List<String> classes = new ArrayList<>(community);
            classes.sort(String::compareTo); // Deterministic ordering
            
            blocks.addAll(createOptimalBlocks(classes, classFiles, blockId, "semantic"));
            blockId += (classes.size() + OPTIMAL_CLASSES_PER_BLOCK - 1) / OPTIMAL_CLASSES_PER_BLOCK;
        }
        
        return blocks;
    }
    
    private double calculateCommunityStrength(Set<String> community, 
                                            SemanticSimilarityMatrix similarityMatrix) {
        if (community.size() < 2) return 0.0;
        
        double totalSimilarity = 0.0;
        int pairs = 0;
        
        List<String> classes = new ArrayList<>(community);
        for (int i = 0; i < classes.size(); i++) {
            for (int j = i + 1; j < classes.size(); j++) {
                totalSimilarity += similarityMatrix.getSimilarity(classes.get(i), classes.get(j));
                pairs++;
            }
        }
        
        return pairs > 0 ? totalSimilarity / pairs : 0.0;
    }
}
```

## Performance Optimizations

### 1. Sparse Matrix Representation

```java
public class SparseSimilarityMatrix {
    private final Map<String, Map<String, Double>> sparseMatrix = new HashMap<>();
    private final double threshold;
    
    public SparseSimilarityMatrix(double threshold) {
        this.threshold = threshold;
    }
    
    public void setSimilarity(String class1, String class2, double similarity) {
        if (similarity > threshold) {
            sparseMatrix.computeIfAbsent(class1, k -> new HashMap<>()).put(class2, similarity);
            sparseMatrix.computeIfAbsent(class2, k -> new HashMap<>()).put(class1, similarity);
        }
    }
    
    public double getSimilarity(String class1, String class2) {
        Map<String, Double> row = sparseMatrix.get(class1);
        return row != null ? row.getOrDefault(class2, 0.0) : 0.0;
    }
}
```

### 2. Parallel Dependency Analysis

```java
public Map<String, ClassDependencies> analyzeAllDependencies(Map<String, byte[]> classFiles) {
    return classFiles.entrySet().parallelStream()
        .collect(Collectors.toConcurrentMap(
            Map.Entry::getKey,
            entry -> analyzer.analyzeDependencies(entry.getKey(), entry.getValue())
        ));
}
```

## Expected Performance

### Compression Improvements
- **Multi-framework JARs**: 10-15% improvement
- **Complex dependency graphs**: 8-12% improvement
- **Homogeneous JARs**: 5-8% improvement

### Computational Complexity
- **Dependency analysis**: O(n) where n = number of classes
- **Similarity matrix**: O(n²) for dense matrix, O(n*k) for sparse (k = avg connections)
- **Community detection**: O(n log n) with optimizations

### Memory Usage
- **Dense matrix**: O(n²) - suitable for <1000 classes
- **Sparse matrix**: O(n*k) - suitable for larger JARs
- **Streaming analysis**: O(1) per class during analysis

## Configuration

```java
@ConfigurationProperties("jarz.semantic.clustering")
public class SemanticClusteringConfig {
    
    /**
     * Minimum similarity threshold for considering classes related
     */
    private double similarityThreshold = 0.1;
    
    /**
     * Minimum community size for creating blocks
     */
    private int minCommunitySize = 30;
    
    /**
     * Maximum iterations for community detection
     */
    private int maxIterations = 100;
    
    /**
     * Use sparse matrix for large JARs (>1000 classes)
     */
    private boolean useSparseMatrix = true;
    
    /**
     * Enable parallel dependency analysis
     */
    private boolean enableParallelAnalysis = true;
}
```

## Validation

### Test Cases
```java
@Test
public void testSemanticClustering() {
    // Test with known related classes
    Map<String, byte[]> testClasses = Map.of(
        "com.example.UserService", loadClass("UserService"),
        "com.example.User", loadClass("User"),
        "com.example.UserRepository", loadClass("UserRepository"),
        "com.unrelated.MathUtils", loadClass("MathUtils")
    );
    
    SemanticDependencyClusterer clusterer = new SemanticDependencyClusterer(0.1);
    List<Block> blocks = clusterer.clusterBySemanticDependencies(testClasses, null);
    
    // Verify related classes are in same block
    assertThat(findBlockContaining(blocks, "UserService"))
        .isEqualTo(findBlockContaining(blocks, "User"))
        .isEqualTo(findBlockContaining(blocks, "UserRepository"));
    
    // Verify unrelated class is separate
    assertThat(findBlockContaining(blocks, "MathUtils"))
        .isNotEqualTo(findBlockContaining(blocks, "UserService"));
}
```

## Future Enhancements

### 1. Weighted Dependency Analysis
- Weight dependencies by frequency of use
- Consider method call depth and complexity
- Expected improvement: 2-5% additional compression

### 2. Cross-Package Relationship Detection
- Analyze relationships across package boundaries
- Detect architectural patterns (MVC, layered architecture)
- Expected improvement: 3-7% for well-structured applications

### 3. Dynamic Dependency Learning
- Learn dependency patterns from multiple JARs
- Apply learned patterns to new JARs
- Expected improvement: Immediate optimization for similar applications

## Conclusion

Semantic dependency graph clustering provides a sophisticated approach to block optimization by analyzing actual code relationships. While computationally more expensive than framework-aware clustering, it delivers superior compression improvements for complex, multi-framework JARs where simple heuristics are insufficient.
