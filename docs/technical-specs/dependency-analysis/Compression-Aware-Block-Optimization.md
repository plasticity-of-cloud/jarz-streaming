# Compression-Aware Block Optimization

**Real-time compression feedback system for optimal JARZ block composition.**

## Overview

This document details the implementation of compression-aware clustering, the primary algorithm for achieving 5-15% compression improvements in JARZ v2. Unlike heuristic-based approaches, this system uses actual ZSTD compression ratios as feedback to optimize block composition dynamically.

## Core Concept

**Principle**: Measure actual compression performance of different block compositions and select the optimal arrangement.

**Advantage**: Direct optimization based on compression reality rather than assumptions about class relationships.

## Algorithm Architecture

### 1. Compression Feedback Loop

```java
public class CompressionOptimizer {
    private final ZstdCompressor compressor;
    private final CompressionCache cache;
    
    public OptimizationResult optimizeBlocks(List<String> classes, 
                                           Map<String, byte[]> classFiles) {
        // Test multiple block composition strategies
        List<BlockStrategy> strategies = generateStrategies(classes.size());
        
        OptimizationResult best = null;
        double bestRatio = 0;
        
        for (BlockStrategy strategy : strategies) {
            List<Block> blocks = strategy.createBlocks(classes, classFiles);
            double ratio = measureCompressionRatio(blocks);
            
            if (ratio > bestRatio) {
                bestRatio = ratio;
                best = new OptimizationResult(blocks, ratio, strategy);
            }
        }
        
        return best;
    }
    
    private double measureCompressionRatio(List<Block> blocks) {
        long originalSize = 0;
        long compressedSize = 0;
        
        for (Block block : blocks) {
            byte[] blockData = serializeBlock(block);
            
            // Check cache first
            String blockHash = calculateHash(blockData);
            Double cachedRatio = cache.get(blockHash);
            
            if (cachedRatio != null) {
                originalSize += blockData.length;
                compressedSize += (long) (blockData.length / cachedRatio);
            } else {
                byte[] compressed = compressor.compress(blockData);
                originalSize += blockData.length;
                compressedSize += compressed.length;
                
                // Cache result
                cache.put(blockHash, (double) blockData.length / compressed.length);
            }
        }
        
        return (double) originalSize / compressedSize;
    }
}
```

### 2. Block Strategy Generation

```java
public class BlockStrategyGenerator {
    
    public List<BlockStrategy> generateStrategies(int totalClasses) {
        List<BlockStrategy> strategies = new ArrayList<>();
        
        // Fixed size strategies
        int[] blockSizes = {50, 60, 80, 100, 120, 150};
        for (int size : blockSizes) {
            strategies.add(new FixedSizeStrategy(size));
        }
        
        // Adaptive strategies
        strategies.add(new AdaptiveSizeStrategy(0.8, 1.2)); // 80%-120% of optimal
        strategies.add(new GradientSizeStrategy(50, 150));   // Gradient from 50 to 150
        
        // Hybrid strategies
        strategies.add(new FrameworkAwareStrategy());
        strategies.add(new DependencyAwareStrategy());
        
        return strategies;
    }
    
    private static class FixedSizeStrategy implements BlockStrategy {
        private final int blockSize;
        
        public List<Block> createBlocks(List<String> classes, Map<String, byte[]> classFiles) {
            List<Block> blocks = new ArrayList<>();
            
            for (int i = 0; i < classes.size(); i += blockSize) {
                Block block = new Block(i / blockSize);
                int end = Math.min(i + blockSize, classes.size());
                
                for (int j = i; j < end; j++) {
                    String className = classes.get(j);
                    byte[] classData = classFiles.get(className);
                    block.add(className, classData);
                }
                
                blocks.add(block);
            }
            
            return blocks;
        }
    }
    
    private static class AdaptiveSizeStrategy implements BlockStrategy {
        private final double minFactor;
        private final double maxFactor;
        
        public List<Block> createBlocks(List<String> classes, Map<String, byte[]> classFiles) {
            // Analyze class sizes and adapt block sizes accordingly
            int optimalSize = calculateOptimalSize(classes, classFiles);
            
            List<Block> blocks = new ArrayList<>();
            int currentSize = (int) (optimalSize * minFactor);
            
            for (int i = 0; i < classes.size(); ) {
                Block block = new Block(blocks.size());
                int end = Math.min(i + currentSize, classes.size());
                
                for (int j = i; j < end; j++) {
                    String className = classes.get(j);
                    byte[] classData = classFiles.get(className);
                    block.add(className, classData);
                }
                
                blocks.add(block);
                i = end;
                
                // Adapt size for next block
                currentSize = Math.min((int) (currentSize * 1.1), (int) (optimalSize * maxFactor));
            }
            
            return blocks;
        }
    }
}
```

### 3. Genetic Algorithm Optimization

```java
public class GeneticBlockOptimizer {
    private static final int POPULATION_SIZE = 20;
    private static final int MAX_GENERATIONS = 50;
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    
    public OptimizationResult optimize(List<String> classes, 
                                     Map<String, byte[]> classFiles,
                                     CompressionOptimizer compressor) {
        // Initialize population
        List<BlockComposition> population = initializePopulation(classes);
        
        OptimizationResult best = null;
        int stagnationCount = 0;
        
        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            // Evaluate fitness
            List<FitnessResult> fitness = evaluateFitness(population, classFiles, compressor);
            
            // Track best solution
            FitnessResult currentBest = fitness.get(0);
            if (best == null || currentBest.fitness > best.compressionRatio) {
                best = new OptimizationResult(currentBest.composition.createBlocks(classFiles), 
                                            currentBest.fitness, currentBest.composition);
                stagnationCount = 0;
            } else {
                stagnationCount++;
            }
            
            // Early termination
            if (stagnationCount > 10) break;
            
            // Evolution
            population = evolvePopulation(population, fitness);
        }
        
        return best;
    }
    
    private List<BlockComposition> initializePopulation(List<String> classes) {
        List<BlockComposition> population = new ArrayList<>();
        Random random = new Random();
        
        for (int i = 0; i < POPULATION_SIZE; i++) {
            // Random block size between 40-160
            int blockSize = 40 + random.nextInt(121);
            population.add(new BlockComposition(classes, blockSize));
        }
        
        return population;
    }
    
    private List<BlockComposition> evolvePopulation(List<BlockComposition> population, 
                                                   List<FitnessResult> fitness) {
        List<BlockComposition> newPopulation = new ArrayList<>();
        
        // Elitism - keep top 20%
        int eliteCount = POPULATION_SIZE / 5;
        for (int i = 0; i < eliteCount; i++) {
            newPopulation.add(fitness.get(i).composition);
        }
        
        // Crossover and mutation
        while (newPopulation.size() < POPULATION_SIZE) {
            BlockComposition parent1 = tournamentSelection(population, fitness);
            BlockComposition parent2 = tournamentSelection(population, fitness);
            
            BlockComposition child = crossover(parent1, parent2);
            child = mutate(child);
            
            newPopulation.add(child);
        }
        
        return newPopulation;
    }
}
```

## Performance Optimizations

### 1. Compression Caching

```java
public class CompressionCache {
    private final Map<String, Double> ratioCache = new ConcurrentHashMap<>();
    private final Map<String, Long> accessTimes = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    
    public Double get(String blockHash) {
        accessTimes.put(blockHash, System.currentTimeMillis());
        return ratioCache.get(blockHash);
    }
    
    public void put(String blockHash, Double ratio) {
        if (ratioCache.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }
        
        ratioCache.put(blockHash, ratio);
        accessTimes.put(blockHash, System.currentTimeMillis());
    }
    
    private void evictOldest() {
        String oldest = accessTimes.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
            
        if (oldest != null) {
            ratioCache.remove(oldest);
            accessTimes.remove(oldest);
        }
    }
}
```

### 2. Parallel Optimization

```java
public class ParallelCompressionOptimizer {
    private final ExecutorService executor;
    private final CompressionOptimizer optimizer;
    
    public OptimizationResult optimizeParallel(List<String> classes, 
                                             Map<String, byte[]> classFiles) {
        List<BlockStrategy> strategies = generateStrategies(classes.size());
        
        // Parallel evaluation of strategies
        List<CompletableFuture<OptimizationResult>> futures = strategies.stream()
            .map(strategy -> CompletableFuture.supplyAsync(() -> 
                evaluateStrategy(strategy, classes, classFiles), executor))
            .collect(Collectors.toList());
        
        // Wait for all results and find best
        return futures.stream()
            .map(CompletableFuture::join)
            .max(Comparator.comparing(result -> result.compressionRatio))
            .orElse(null);
    }
}
```

## Integration with EnhancedBlockAssigner

```java
// Modified clusterByFrameworkPatterns method
private List<Block> clusterByFrameworkPatterns(Map<String, byte[]> classFiles, DependencyGraph graph) {
    Map<String, List<String>> frameworkGroups = groupByFramework(classFiles);
    List<Block> blocks = new ArrayList<>();
    
    CompressionOptimizer optimizer = new CompressionOptimizer();
    
    for (Map.Entry<String, List<String>> entry : frameworkGroups.entrySet()) {
        List<String> classes = entry.getValue();
        
        if (classes.size() >= MIN_CLASSES_PER_BLOCK * 3) {
            // Large group - use compression-aware optimization
            OptimizationResult result = optimizer.optimizeBlocks(classes, classFiles);
            blocks.addAll(result.blocks);
            
            // Log optimization results
            log.debug("Framework {}: {} classes -> {} blocks, {:.1f}x compression", 
                     entry.getKey(), classes.size(), result.blocks.size(), result.compressionRatio);
        } else {
            // Small group - use existing optimal blocks
            blocks.addAll(createOptimalBlocks(classes, classFiles, blocks.size(), entry.getKey()));
        }
    }
    
    return blocks;
}
```

## Expected Performance Impact

### Compression Improvements
- **Large JARs** (>1000 classes): 12-18% improvement
- **Medium JARs** (200-1000 classes): 8-15% improvement  
- **Small JARs** (<200 classes): 5-10% improvement

### Computational Overhead
- **Block creation time**: 2-3x slower than current implementation
- **Memory usage**: +30-50MB during optimization
- **Caching benefit**: 80% cache hit rate after warmup

### Optimization Strategies
1. **Quick mode**: Test 5 fixed strategies (2-3 seconds)
2. **Standard mode**: Test 10 strategies + adaptive (5-8 seconds)
3. **Genetic mode**: Full genetic algorithm (15-30 seconds)

## Configuration Options

```java
@ConfigurationProperties("jarz.compression.optimization")
public class CompressionOptimizationConfig {
    
    /**
     * Optimization mode: QUICK, STANDARD, GENETIC
     */
    private OptimizationMode mode = OptimizationMode.STANDARD;
    
    /**
     * Minimum classes required to trigger optimization
     */
    private int minClassesForOptimization = 150;
    
    /**
     * Maximum optimization time in seconds
     */
    private int maxOptimizationTime = 10;
    
    /**
     * Enable compression caching
     */
    private boolean enableCaching = true;
    
    /**
     * Parallel optimization threads
     */
    private int parallelThreads = Runtime.getRuntime().availableProcessors();
}
```

## Validation and Testing

### Unit Tests
```java
@Test
public void testCompressionOptimization() {
    Map<String, byte[]> testClasses = loadTestClasses();
    CompressionOptimizer optimizer = new CompressionOptimizer();
    
    OptimizationResult result = optimizer.optimizeBlocks(
        new ArrayList<>(testClasses.keySet()), testClasses);
    
    // Verify improvement over baseline
    double baselineRatio = measureBaselineCompression(testClasses);
    assertThat(result.compressionRatio).isGreaterThan(baselineRatio * 1.05); // 5% minimum
}
```

### Performance Benchmarks
```java
@Benchmark
public void benchmarkOptimization(Blackhole bh) {
    CompressionOptimizer optimizer = new CompressionOptimizer();
    OptimizationResult result = optimizer.optimizeBlocks(testClasses, testClassFiles);
    bh.consume(result);
}
```

## Future Enhancements

### 1. Machine Learning Integration
- Train models on compression patterns
- Predict optimal block sizes without testing
- Expected improvement: 20-30% faster optimization

### 2. Incremental Optimization
- Optimize only changed classes in subsequent builds
- Cache optimization results across builds
- Expected improvement: 90% faster for incremental builds

### 3. Cross-JAR Pattern Learning
- Learn patterns across multiple JARs
- Apply learned patterns to new JARs
- Expected improvement: Immediate optimization for new JARs

## Conclusion

Compression-aware block optimization provides a systematic approach to achieve 5-15% compression improvements by using actual compression performance as the optimization metric. The combination of multiple strategies, caching, and parallel processing ensures both effectiveness and reasonable performance overhead.
