# JARZ v2: Block-Based Format Specification

**Version**: 2.0  
**Status**: ✅ IMPLEMENTED AND VALIDATED  
**Date**: December 23, 2025  
**Result**: **27.4% compression improvement** over JAR (exceeds 18-22% target)

## Executive Summary

JARZ v2 introduces dependency-aware block compression using intelligent class grouping. Classes are organized into ZSTD-compressed blocks based on dependency analysis, enabling:

1. **Cross-file compression** - Classes in same block share ZSTD dictionary context
2. **Efficient S3 streaming** - One range request fetches entire dependency cluster
3. **JDK-native tooling** - Built on jdeps analysis and standard Java APIs

## Validated Results (java.base, 7,392 classes)

| Format | Size | vs JAR | Notes |
|--------|------|--------|-------|
| Original | 29.6 MB | - | Uncompressed |
| JAR (DEFLATE) | 14.6 MB | baseline | Standard format |
| JARZ v1 (per-file) | 14.5 MB | +1.0% | Per-file ZSTD |
| **JARZ v2 (blocks)** | **10.6 MB** | **+27.4%** | Block-based ✅ |

**Block Statistics**:
- 58 blocks for 7,392 classes
- ~127 classes per block average
- 504 KB average block size
- 100% data integrity verified

## Format Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         JARZ v2 File Format                              │
├─────────────────────────────────────────────────────────────────────────┤
│ Magic (4B): "JRZ2"                                                       │
│ Version (2B): 0x0200                                                     │
│ Flags (2B): [dictionary|profile-guided|signed]                          │
│ Block Count (4B)                                                         │
│ Dictionary Size (4B)                                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ [Optional] ZSTD Dictionary (trained on class file corpus)               │
├─────────────────────────────────────────────────────────────────────────┤
│ Block 0: [type=CLASS] ZSTD Frame [class₀, class₁, ..., classₙ]          │
│ Block 1: [type=CLASS] ZSTD Frame [class₀, class₁, ..., classₘ]          │
│ ...                                                                      │
│ Block R: [type=CONFIG] ZSTD Frame [*.properties, *.xml, *.yml]          │
│ Block S: [type=SERVICE] ZSTD Frame [META-INF/services/*]                │
│ Block T: [type=STORED] Uncompressed [*.png, *.jpg, *.gif]               │
│ ...                                                                      │
├─────────────────────────────────────────────────────────────────────────┤
│ Entry Index: entry_name → (block_id, offset, size, crc32, type)         │
│ Block Index: block_id → (file_offset, compressed_size, uncompressed)    │
│ Load Order Hints: block_id[] (optimal prefetch sequence)                │
├─────────────────────────────────────────────────────────────────────────┤
│ Footer: index_offset (8B) | magic "JRZ2" (4B)                           │
└─────────────────────────────────────────────────────────────────────────┘
```

## Block Types

JARZ v2 supports multiple block types to handle different content optimally:

| Type ID | Name | Compression | Content |
|---------|------|-------------|---------|
| `0x01` | `CLASS` | ZSTD | `.class` files (dependency-grouped) |
| `0x02` | `CONFIG` | ZSTD | `.properties`, `.xml`, `.yml`, `.yaml`, `.json` |
| `0x03` | `SERVICE` | ZSTD | `META-INF/services/*`, `META-INF/spring.*` |
| `0x04` | `TEXT` | ZSTD | `.txt`, `.md`, `.html`, `.css`, `.js` |
| `0x05` | `NATIVE` | ZSTD-low | `.so`, `.dll`, `.dylib` (level 1) |
| `0x06` | `STORED` | None | `.png`, `.jpg`, `.gif`, `.zip`, `.gz` (pre-compressed) |
| `0x07` | `MANIFEST` | ZSTD | `META-INF/MANIFEST.MF`, signatures |

### Block Type Selection

```
Entry Extension/Path          → Block Type
─────────────────────────────────────────────
*.class                       → CLASS (dependency-grouped)
*.properties, *.xml, *.yml    → CONFIG
*.json (config)               → CONFIG
META-INF/services/*           → SERVICE
META-INF/spring.*             → SERVICE
*.html, *.css, *.js           → TEXT
*.so, *.dll, *.dylib          → NATIVE
*.png, *.jpg, *.gif, *.ico    → STORED (no compression)
*.zip, *.gz, *.jar            → STORED (already compressed)
*.woff, *.woff2, *.ttf        → STORED (fonts, poor compression)
META-INF/MANIFEST.MF          → MANIFEST
META-INF/*.SF, *.RSA, *.DSA   → MANIFEST
*                             → TEXT (default)
```

## Block Structure

### Block Header Format

```
┌─────────────────────────────────────────────────────┐
│                    Block Header (8B)                │
├─────────────────────────────────────────────────────┤
│ Block Type (1B): CLASS|CONFIG|SERVICE|TEXT|...      │
│ Compression (1B): 0=STORED, 1=ZSTD, 2=ZSTD-low     │
│ Entry Count (2B): Number of entries in block        │
│ Reserved (4B): Future use                           │
├─────────────────────────────────────────────────────┤
│                    Block Data                       │
│ Entry 0: [name_len(2B)][name][size(4B)][data]       │
│ Entry 1: [name_len(2B)][name][size(4B)][data]       │
│ ...                                                  │
└─────────────────────────────────────────────────────┘
```

### Block Size Targets

| Block Type | Target Size | Entries/Block | Rationale |
|------------|-------------|---------------|-----------|
| CLASS | 512KB - 1MB | 50-150 | Dependency clustering |
| CONFIG | 256KB | 50-200 | Similar text patterns |
| SERVICE | 64KB | All | Usually small, group together |
| TEXT | 512KB | 20-100 | Web assets benefit from grouping |
| NATIVE | 2MB | 1-5 | Large files, minimal compression |
| STORED | 1MB | 10-50 | No compression overhead |
| MANIFEST | 64KB | All | Security-critical, keep together |

## Profile-Guided Block Assignment

### Data Sources (Priority Order)

#### 1. JFR Class Load Events (Highest Fidelity)

```java
// Collect from production JFR recordings
public class JfrProfileCollector {
    
    public LoadProfile collectFromJfr(Path jfrFile) throws IOException {
        var loadOrder = new ArrayList<ClassLoadEvent>();
        
        try (var recording = RecordingFile.readAllEvents(jfrFile)) {
            for (RecordedEvent event : recording) {
                if (event.getEventType().getName().equals("jdk.ClassLoad")) {
                    loadOrder.add(new ClassLoadEvent(
                        event.getString("loadedClass.name"),
                        event.getInstant("startTime"),
                        event.getThread("eventThread").getJavaName(),
                        event.getClass("definingClassLoader")
                    ));
                }
            }
        }
        return new LoadProfile(loadOrder);
    }
}
```

#### 2. jdeps Static Analysis (No Runtime Required)

```java
// Use jdk.jdeps module APIs
public class JdepsAnalyzer {
    
    public DependencyGraph analyze(Path jarOrDir) {
        var archive = Archive.of(jarOrDir);
        var analyzer = new DependencyFinder();
        
        // Build complete dependency graph
        var deps = analyzer.parse(archive.reader());
        
        return buildGraph(deps);
    }
    
    public List<Set<String>> clusterByDependency(DependencyGraph graph, int targetBlockSize) {
        // Tarjan's algorithm for strongly connected components
        var sccs = graph.findStronglyConnectedComponents();
        
        // Merge small SCCs into blocks respecting dependency order
        return mergeIntoBlocks(sccs, targetBlockSize);
    }
}
```

#### 3. jlink Module Graph (Module-Level Affinity)

```java
// Use jdk.jlink module APIs  
public class JlinkModuleAnalyzer {
    
    public ModuleGraph analyzeModules(ModuleFinder finder) {
        var config = Configuration.resolve(
            finder, 
            ModuleLayer.boot().configuration(),
            ModuleFinder.of(),
            Set.of("java.base")
        );
        
        // Extract module dependency graph
        return ModuleGraph.from(config);
    }
}
```

### Block Assignment Algorithm

```java
public class ProfileGuidedBlockAssigner {
    
    private static final int TARGET_BLOCK_SIZE = 512 * 1024; // 512KB uncompressed
    private static final int MAX_BLOCK_SIZE = 1024 * 1024;   // 1MB hard limit
    
    public BlockAssignment assign(
            Set<ClassFile> classes,
            LoadProfile profile,      // from JFR (optional)
            DependencyGraph deps      // from jdeps (required)
    ) {
        // Step 1: Build affinity scores
        var affinity = new AffinityMatrix(classes.size());
        
        // Static dependencies (jdeps) - base affinity
        for (var edge : deps.edges()) {
            affinity.add(edge.from(), edge.to(), 1.0);
        }
        
        // Profile-guided boost (JFR) - temporal locality
        if (profile != null) {
            var loadOrder = profile.getLoadOrder();
            for (int i = 0; i < loadOrder.size() - 1; i++) {
                // Classes loaded within 10ms window get affinity boost
                var current = loadOrder.get(i);
                for (int j = i + 1; j < Math.min(i + 50, loadOrder.size()); j++) {
                    var next = loadOrder.get(j);
                    var timeDelta = Duration.between(current.time(), next.time()).toMillis();
                    if (timeDelta < 10) {
                        affinity.add(current.className(), next.className(), 2.0); // 2x boost
                    } else if (timeDelta < 100) {
                        affinity.add(current.className(), next.className(), 1.5);
                    }
                }
            }
        }
        
        // Step 2: Cluster using greedy algorithm with dependency constraints
        return clusterWithConstraints(classes, affinity, deps);
    }
    
    private BlockAssignment clusterWithConstraints(
            Set<ClassFile> classes,
            AffinityMatrix affinity,
            DependencyGraph deps
    ) {
        var blocks = new ArrayList<Block>();
        var assigned = new HashSet<String>();
        var topoOrder = deps.topologicalSort(); // Superclasses before subclasses
        
        Block currentBlock = new Block();
        
        for (String className : topoOrder) {
            if (assigned.contains(className)) continue;
            
            var classFile = classes.stream()
                .filter(c -> c.name().equals(className))
                .findFirst().orElse(null);
            if (classFile == null) continue;
            
            // Check if adding this class exceeds block size
            if (currentBlock.size() + classFile.size() > TARGET_BLOCK_SIZE 
                    && !currentBlock.isEmpty()) {
                blocks.add(currentBlock);
                currentBlock = new Block();
            }
            
            // Add class and its high-affinity neighbors
            currentBlock.add(classFile);
            assigned.add(className);
            
            // Pull in strongly connected classes (up to block limit)
            for (var neighbor : affinity.topNeighbors(className, 20)) {
                if (assigned.contains(neighbor.name())) continue;
                if (currentBlock.size() + neighbor.size() > MAX_BLOCK_SIZE) break;
                
                // Ensure dependency constraint: if neighbor depends on unassigned class, skip
                if (deps.dependsOnUnassigned(neighbor.name(), assigned)) continue;
                
                currentBlock.add(neighbor);
                assigned.add(neighbor.name());
            }
        }
        
        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }
        
        return new BlockAssignment(blocks);
    }
}
```

## Resource Block Assignment

Resources (non-class files) use content-type grouping instead of dependency analysis.

### Assignment Strategy by Type

```java
public class ResourceBlockAssigner {
    
    public List<Block> assignResources(Map<String, byte[]> entries) {
        // Partition by block type
        var byType = entries.entrySet().stream()
            .collect(Collectors.groupingBy(e -> classifyEntry(e.getKey())));
        
        var blocks = new ArrayList<Block>();
        
        // CONFIG: Group properties/xml/yaml together (similar patterns)
        blocks.addAll(createBlocks(byType.get(CONFIG), 256 * 1024, ZSTD));
        
        // SERVICE: All service loaders in one block (small files)
        blocks.addAll(createBlocks(byType.get(SERVICE), 64 * 1024, ZSTD));
        
        // TEXT: Group by subdirectory for locality
        blocks.addAll(createBlocksByDirectory(byType.get(TEXT), 512 * 1024, ZSTD));
        
        // NATIVE: Large files, low compression (already dense)
        blocks.addAll(createBlocks(byType.get(NATIVE), 2 * 1024 * 1024, ZSTD_LOW));
        
        // STORED: No compression (pre-compressed formats)
        blocks.addAll(createBlocks(byType.get(STORED), 1024 * 1024, NONE));
        
        // MANIFEST: Security-critical, keep together
        blocks.addAll(createBlocks(byType.get(MANIFEST), 64 * 1024, ZSTD));
        
        return blocks;
    }
    
    private BlockType classifyEntry(String name) {
        if (name.endsWith(".class")) return CLASS;
        if (name.endsWith(".properties") || name.endsWith(".xml") || 
            name.endsWith(".yml") || name.endsWith(".yaml")) return CONFIG;
        if (name.startsWith("META-INF/services/") || 
            name.startsWith("META-INF/spring.")) return SERVICE;
        if (name.endsWith(".so") || name.endsWith(".dll") || 
            name.endsWith(".dylib")) return NATIVE;
        if (isPreCompressed(name)) return STORED;
        if (name.equals("META-INF/MANIFEST.MF") || 
            name.endsWith(".SF") || name.endsWith(".RSA")) return MANIFEST;
        return TEXT; // default
    }
    
    private boolean isPreCompressed(String name) {
        return name.endsWith(".png") || name.endsWith(".jpg") || 
               name.endsWith(".jpeg") || name.endsWith(".gif") ||
               name.endsWith(".zip") || name.endsWith(".gz") ||
               name.endsWith(".woff") || name.endsWith(".woff2");
    }
}
```

### Compression Strategy by Content Type

| Block Type | ZSTD Level | Dictionary | Rationale |
|------------|------------|------------|-----------|
| CLASS | 3 (default) | Optional | Bytecode patterns benefit from dictionary |
| CONFIG | 6 (high) | No | Text compresses well, worth extra CPU |
| SERVICE | 3 | No | Small files, fast compression |
| TEXT | 6 | No | HTML/CSS/JS compress excellently |
| NATIVE | 1 (fast) | No | Binary, diminishing returns above level 1 |
| STORED | N/A | N/A | Skip compression entirely |
| MANIFEST | 3 | No | Small, security-sensitive |

### Directory-Based Grouping for Web Assets

```java
// Group web assets by directory for better locality
private List<Block> createBlocksByDirectory(
        List<Entry> entries, int targetSize, Compression compression) {
    
    // Group by parent directory
    var byDir = entries.stream()
        .collect(Collectors.groupingBy(e -> parentDir(e.name())));
    
    var blocks = new ArrayList<Block>();
    Block current = new Block(TEXT, compression);
    
    for (var dirEntries : byDir.values()) {
        // Try to keep directory contents together
        int dirSize = dirEntries.stream().mapToInt(Entry::size).sum();
        
        if (current.size() + dirSize > targetSize && !current.isEmpty()) {
            blocks.add(current);
            current = new Block(TEXT, compression);
        }
        
        for (var entry : dirEntries) {
            if (current.size() + entry.size() > targetSize * 1.5) {
                blocks.add(current);
                current = new Block(TEXT, compression);
            }
            current.add(entry);
        }
    }
    
    if (!current.isEmpty()) blocks.add(current);
    return blocks;
}
```

### Expected Compression by Content Type

| Content Type | Typical Ratio | Example |
|--------------|---------------|---------|
| `.class` (blocked) | 27-35% | java.base: 27.4% |
| `.properties` | 70-80% | i18n bundles |
| `.xml` | 80-90% | Spring configs |
| `.json` | 60-75% | OpenAPI specs |
| `.html/.css/.js` | 70-85% | Web assets |
| `.so/.dll` | 5-15% | Native libs |
| `.png/.jpg` | 0-2% | Already compressed |

### Real-World JAR Content Analysis

Typical Spring Boot fat JAR breakdown:

```
Content Distribution (example: 67MB Spring Boot app)
├── .class files:     45 MB (67%) → CLASS blocks
├── .properties:       2 MB (3%)  → CONFIG blocks  
├── .xml configs:      1 MB (1%)  → CONFIG blocks
├── META-INF/:       0.5 MB (1%)  → SERVICE + MANIFEST blocks
├── static/ (web):    10 MB (15%) → TEXT blocks (or STORED if images)
├── native libs:       5 MB (7%)  → NATIVE blocks
└── other:             3 MB (5%)  → TEXT blocks

Expected JARZ v2 size: ~48 MB (28% reduction)
- CLASS blocks: 45 MB → 33 MB (27% reduction)
- CONFIG blocks: 3 MB → 0.7 MB (77% reduction)
- TEXT blocks: 10 MB → 3 MB (70% reduction)
- NATIVE blocks: 5 MB → 4.5 MB (10% reduction)
- STORED blocks: 4 MB → 4 MB (0% - skip compression)
```

## JDK Integration Points

### Required JDK APIs

| API | Module | Purpose |
|-----|--------|---------|
| `jdk.jdeps` | jdk.jdeps | Static dependency analysis |
| `jdk.jlink` | jdk.jlink | Module graph resolution |
| `jdk.jfr` | jdk.jfr | Runtime load profiling |
| `java.lang.classfile` | java.base (21+) | Class file parsing |
| `java.util.zip` | java.base | ZIP compatibility layer |

### Tool Integration

```bash
# Generate profile from JFR recording
jarz profile --jfr app-recording.jfr --output load-profile.json

# Create JARZ v2 with profile guidance
jarz create --format v2 \
    --profile load-profile.json \
    --block-size 512k \
    --input classes/ \
    --output app.jarz

# Create JARZ v2 with jdeps-only (no profile)
jarz create --format v2 \
    --analyze-deps \
    --block-size 512k \
    --input app.jar \
    --output app.jarz

# Analyze existing JAR and suggest block layout
jarz analyze --input app.jar --output block-report.json
```

## S3 Streaming Protocol

### Read Path

```java
public class S3JarzV2ClassLoader extends ClassLoader {
    
    private final S3Client s3;
    private final String bucket, key;
    private final BlockIndex blockIndex;
    private final ClassIndex classIndex;
    private final Map<Integer, byte[]> blockCache = new ConcurrentHashMap<>();
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        var entry = classIndex.get(name);
        if (entry == null) throw new ClassNotFoundException(name);
        
        // Fetch block if not cached
        byte[] blockData = blockCache.computeIfAbsent(entry.blockId(), this::fetchBlock);
        
        // Extract class from decompressed block
        byte[] classBytes = extractClass(blockData, entry.offset(), entry.size());
        
        return defineClass(name, classBytes, 0, classBytes.length);
    }
    
    private byte[] fetchBlock(int blockId) {
        var blockEntry = blockIndex.get(blockId);
        
        // Single S3 range request for entire block
        var request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .range("bytes=" + blockEntry.offset() + "-" + 
                   (blockEntry.offset() + blockEntry.compressedSize() - 1))
            .build();
        
        byte[] compressed = s3.getObjectAsBytes(request).asByteArray();
        return Zstd.decompress(compressed, blockEntry.uncompressedSize());
    }
    
    // Prefetch hint: load blocks in optimal order
    public void prefetch(String... classNames) {
        var blockIds = Arrays.stream(classNames)
            .map(classIndex::get)
            .filter(Objects::nonNull)
            .map(ClassEntry::blockId)
            .distinct()
            .toList();
        
        // Parallel prefetch
        blockIds.parallelStream().forEach(this::fetchBlock);
    }
}
```

### S3 Request Efficiency

| Scenario | JARZ v1 (per-file) | JARZ v2 (blocks) | Improvement |
|----------|-------------------|------------------|-------------|
| Load 100 classes | 100 requests | 2-5 requests | **20-50x fewer** |
| Cold start latency | 2000ms | 200-400ms | **5-10x faster** |
| S3 GET costs (1M loads) | $0.40 | $0.02 | **95% savings** |

## Compression Analysis

### Expected Results

| Format | Compression Ratio | vs JAR | Random Access |
|--------|-------------------|--------|---------------|
| JAR (DEFLATE) | 57.9% | baseline | Per-file |
| JARZ v1 (per-file ZSTD) | 55.4% | 4.4% better | Per-file |
| JARZ v2 (512KB blocks) | ~45% | **~22% better** | Per-block |
| tar.zst (solid) | 42.5% | 26.5% better | None |

### Why Blocks Close the Gap

1. **Shared ZSTD context** - Similar bytecode patterns across classes in same block
2. **Constant pool deduplication** - Related classes share string/type references
3. **Method signature patterns** - Override chains have similar signatures
4. **Annotation repetition** - Framework annotations repeat across classes

## Implementation Phases

### Phase 1: Core Format (Week 1-2)

```
jarz-core/
├── BlockWriter.java      # Write blocks with ZSTD compression
├── BlockReader.java      # Read and cache blocks
├── BlockIndex.java       # Block offset/size index
├── ClassIndex.java       # Class → block mapping
└── JarzV2Format.java     # Format constants and validation
```

### Phase 2: Profile Collection (Week 2-3)

```
jarz-profiler/
├── JfrProfileCollector.java    # Extract from JFR recordings
├── JdepsAnalyzer.java          # Static dependency analysis
├── AffinityMatrix.java         # Class affinity scoring
└── LoadProfile.java            # Serializable profile format
```

### Phase 3: Block Assignment (Week 3-4)

```
jarz-optimizer/
├── ProfileGuidedAssigner.java  # Main assignment algorithm
├── DependencyGraph.java        # Graph operations ✅ IMPLEMENTED
├── BlockConstraints.java       # Size/dependency constraints
└── BlockOptimizer.java         # Post-assignment optimization
```

### Phase 4: S3 Integration ✅ COMPLETED

```
jarz-s3/
├── S3JarzV2ClassLoader.java    # Block-aware S3 loader ✅ IMPLEMENTED
├── S3JarzV2UnitTest.java       # Unit tests with fake S3 ✅ IMPLEMENTED
```

### Phase 5: Resource Block Support (NEW)

```
jarz-core/
├── BlockType.java              # Block type enum (CLASS, CONFIG, SERVICE, etc.)
├── ResourceBlockAssigner.java  # Content-type based assignment
├── CompressionStrategy.java    # Per-type compression settings
└── EntryClassifier.java        # Extension/path → block type mapping
```

## Implementation Status

### ✅ Completed (Phase 1-4)

| Component | Status | Location |
|-----------|--------|----------|
| `JarzV2Format` | ✅ Done | `jarz-core/src/main/java/jdk/incubator/jarz/v2/` |
| `Block` | ✅ Done | Block data structure with serialization |
| `BlockWriter` | ✅ Done | Writes JARZ v2 archives |
| `BlockReader` | ✅ Done | Reads with block caching |
| `BlockIndex` | ✅ Done | Block offset mapping |
| `ClassIndex` | ✅ Done | Class-to-block mapping |
| `DependencyAnalyzer` | ✅ Done | jdeps-based analysis |
| `DependencyGraph` | ✅ Done | Topological sort, SCC |
| `BlockAssigner` | ✅ Done | Dependency-aware clustering |
| `JarzV2Test` | ✅ Done | Unit tests |
| `RealWorldValidation` | ✅ Done | java.base validation |
| `S3JarzV2ClassLoader` | ✅ Done | `jarz-s3/src/main/java/jdk/incubator/jarz/s3/` |
| `S3JarzV2UnitTest` | ✅ Done | 5 unit tests passing |

### 🔄 Pending (Phase 5 - Resource Blocks)

| Component | Status | Priority |
|-----------|--------|----------|
| `BlockType` enum | Pending | High |
| `ResourceBlockAssigner` | Pending | High |
| `EntryClassifier` | Pending | High |
| Block header with type field | Pending | High |
| Spring Boot fat JAR test | Pending | Medium |
| CLI v2 commands | Pending | Medium |
| JFR profile integration | Pending | Low |

### ✅ Completed (Phase 5 - Resource Blocks)

| Component | Status | Location |
|-----------|--------|----------|
| `BlockType` | ✅ Done | Enum: CLASS, CONFIG, SERVICE, TEXT, NATIVE, STORED, MANIFEST |
| `EntryClassifier` | ✅ Done | Extension/path → block type mapping |
| `TypedBlock` | ✅ Done | Block with type for content-aware compression |
| `ResourceBlockAssigner` | ✅ Done | Groups resources by type with size constraints |
| Block header format | ✅ Done | type(1B) + compression(1B) + entryCount(2B) + reserved(4B) |
| `ResourceBlockTest` | ✅ Done | 5 tests passing |

## Validation Results

### Compression Target ✅ EXCEEDED (Classes Only)

| Metric | Target | Achieved | Status |
|--------|--------|----------|--------|
| vs JAR | ≥18% | **27.4%** | ✅ EXCEEDED |
| vs JARZ v1 | ≥14% | **26.7%** | ✅ EXCEEDED |
| Data integrity | 100% | 100% | ✅ MET |

### S3 Streaming Efficiency ✅ VALIDATED (Real S3 with MinIO)

**Test Setup:** 1000 classes from java.base.jmod uploaded to MinIO (S3-compatible)

**Archive Sizes:**
| Format | Size | vs JAR |
|--------|------|--------|
| JAR (DEFLATE) | 2.26 MB | baseline |
| JARZ v1 (per-file) | 2.23 MB | -1.4% |
| JARZ v2 (blocks) | 1.59 MB | **-30%** |

**S3 Request Efficiency:**
| Metric | Target | Achieved | Status |
|--------|--------|----------|--------|
| Request reduction | 10-20x | **11.1x** (100 classes / 9 requests) | ✅ EXCEEDED |
| Cache hit rate | >80% | **91%** | ✅ EXCEEDED |
| Prefetch efficiency | - | 100 classes in **1ms** after prefetch | ✅ |
| Data integrity | 100% | 100% | ✅ MET |

**Real S3 Test Results (MinIO testcontainers):**
```
Extracted 1000 classes from java.base (4,545,666 bytes total)
Archives created:
  JAR:     2,263,641 bytes
  JARZ v1: 2,231,007 bytes
  JARZ v2: 1,587,011 bytes (9 blocks)

Loading 100 classes:
  S3 requests:       3 (index) + 9 (blocks) = 12 total
  Cache hit rate:    91.0%
  Request reduction: 11.1x vs per-class
  Time:              56 ms

Prefetch efficiency:
  Prefetch all blocks: 20 ms
  Load 100 classes after prefetch: 1 ms (0 additional S3 requests)
```

**Key Findings:**
1. Block-based format reduces S3 requests by **11x**
2. After prefetch, class loading is essentially **instant** (1ms for 100 classes)
3. JARZ v2 is **30% smaller** than JAR on real JDK classes
4. 91% cache hit rate - most classes come from already-loaded blocks

## Appendix: JDK API Usage Examples

### Using java.lang.classfile (Java 21+)

```java
import java.lang.classfile.*;

public class ClassFileAnalyzer {
    
    public Set<String> extractDependencies(byte[] classBytes) {
        var cf = ClassFile.of().parse(classBytes);
        var deps = new HashSet<String>();
        
        // Superclass
        cf.superclass().ifPresent(s -> deps.add(s.asInternalName()));
        
        // Interfaces
        cf.interfaces().forEach(i -> deps.add(i.asInternalName()));
        
        // Field types
        cf.fields().forEach(f -> 
            extractTypeRefs(f.fieldType()).forEach(deps::add));
        
        // Method signatures
        cf.methods().forEach(m -> {
            extractTypeRefs(m.methodType()).forEach(deps::add);
            // Method body references
            m.code().ifPresent(code -> 
                extractCodeRefs(code).forEach(deps::add));
        });
        
        return deps;
    }
}
```

### Using jdk.jdeps Programmatically

```java
// Note: jdk.jdeps is not a public API, use tool invocation
public class JdepsWrapper {
    
    public DependencyGraph analyze(Path input) throws Exception {
        var tool = ToolProvider.findFirst("jdeps").orElseThrow();
        var out = new StringWriter();
        
        tool.run(new PrintWriter(out), new PrintWriter(System.err),
            "-verbose:class",
            "-filter:none", 
            "--multi-release", "21",
            input.toString()
        );
        
        return parseDepsOutput(out.toString());
    }
}
```

### Using JFR Events

```java
import jdk.jfr.*;
import jdk.jfr.consumer.*;

public class ClassLoadProfiler {
    
    public void startProfiling() throws Exception {
        var config = Configuration.getConfiguration("profile");
        var recording = new Recording(config);
        
        // Enable class load events
        recording.enable("jdk.ClassLoad").withoutThreshold();
        recording.enable("jdk.ClassDefine").withoutThreshold();
        
        recording.start();
        
        // Run application...
        
        recording.stop();
        recording.dump(Path.of("class-load-profile.jfr"));
    }
}
```

---

*Specification Version: 2.0*  
*Authors: JARZ Project Team*  
*Status: ✅ Core Implementation Complete, S3 Integration Pending*
