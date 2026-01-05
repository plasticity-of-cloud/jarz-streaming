# Phase 10: FFM API + Dictionary Training Integration

**Status**: Proposed  
**Priority**: High (Java 25+ Compatibility)  
**Estimated Effort**: 3-4 weeks  
**Target**: Java 25 LTS (FFM) + Java 26+ (Valhalla Preview)

## Overview

Replace JNI-based ZSTD integration with modern FFM API and showcase Project Valhalla capabilities, positioning JARZ as a demonstration of next-generation Java features.

## Strategic Value

### Why FFM API + Valhalla?

**1. FFM API (Foreign Function & Memory) - Production Ready**
- Finalized in Java 22 (JEP 454), stable in Java 25 LTS
- **10-100x faster than JNI** for native calls
- **Zero-copy memory access** - direct ByteBuffer manipulation
- **Type-safe** - compile-time checking vs JNI's runtime crashes
- **Pure Java** - no C glue code needed

**2. Project Valhalla - Future-Proof**
- **Value types** - flat memory layouts (like C structs)
- **Primitive classes** - `List<int>` without boxing overhead
- **Expected in Java 26-27** (2026-2027 timeframe)
- **Perfect for compression buffers** - zero-allocation data structures

**3. Platform Native ZSTD Support**
- **Windows 11**: Built-in ZSTD support
- **Linux**: libzstd in all major distros
- **macOS**: Available via system libraries
- **No bundling needed** - leverage OS-provided libraries

## Technical Benefits

### FFM API Advantages

```java
// No JNI glue code - direct native calls
MethodHandle zstd_compress = linker.downcallHandle(
    FunctionDescriptor.of(JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG)
);

// Zero-copy memory access
MemorySegment input = MemorySegment.ofArray(classBytes);
MemorySegment output = arena.allocate(maxCompressedSize);
long compressedSize = (long) zstd_compress.invoke(output, input, input.byteSize());
```

### Valhalla Value Types (Future)

```java
// Flat memory layout - no object overhead
value class IndexEntry {
    long offset;
    int compressedSize;
    int uncompressedSize;
    // 16 bytes flat, not 40+ bytes with object headers
}

// Primitive collections - no boxing
List<int> offsets = new ArrayList<>();  // Direct int storage
```

## Implementation Plan

### Phase 10A: FFM-Based Dictionary Training (2-3 weeks)

**Deliverables**:
- [ ] FFM bindings to platform ZSTD libraries (Windows/Linux/macOS)
- [ ] Dictionary training implementation using FFM API
- [ ] Dictionary-based compression/decompression
- [ ] Performance benchmarks: FFM vs zstd-jni vs pure Java ZSTD
- [ ] Platform-specific library loading and fallback mechanisms
- [ ] Documentation: FFM API best practices

**Expected Results**:
- 10-15% additional compression improvement with dictionaries
- 2-10x faster native calls compared to JNI
- Zero-copy memory operations
- Cleaner, safer code without JNI complexity

### Phase 10B: Valhalla Preview Integration (1-2 weeks)

**Deliverables**:
- [ ] Value classes for compression metadata structures
- [ ] Primitive collections for index data
- [ ] Flat memory layouts for dictionary storage
- [ ] Performance comparison: Java 25 vs Java 26 preview
- [ ] Documentation: Valhalla benefits showcase
- [ ] Migration guide for value types

**Expected Results**:
- 20-40% memory reduction for metadata structures
- Improved cache locality with flat layouts
- Demonstration of future Java capabilities
- Educational value for Valhalla adoption

## Performance Comparison Matrix

| Approach | Java Version | Performance | Complexity | Innovation |
|----------|--------------|-------------|------------|------------|
| zstd-jni | Java 8+ | Baseline | Medium (JNI) | Standard |
| FFM API | Java 25 LTS | **2-10x faster** | Low (pure Java) | **Modern** |
| FFM + Valhalla | Java 26+ | **5-20x faster** | Low | **Cutting-edge** |

## Project Positioning

**Narrative**: "JARZ demonstrates next-generation Java capabilities"

- **Java 25 LTS baseline**: Production-ready FFM API
- **Valhalla preview**: Future-proof with value types
- **Real-world use case**: Not toy benchmarks, actual JDK compression
- **Platform integration**: Leverage OS-native ZSTD libraries

## Module Structure

```
jarz-dictionary-trainer/
├── src/main/java/
│   ├── jdk.incubator.jarz.dictionary/
│   │   ├── ZstdFFMWrapper.java          # FFM bindings
│   │   ├── DictionaryTrainer.java       # Training implementation
│   │   └── PlatformLibraryLoader.java   # OS-specific loading
│   └── jdk.incubator.jarz.dictionary.valhalla/  # Java 26+ only
│       ├── IndexEntry.java              # Value class
│       └── PrimitiveCollections.java    # Primitive lists
└── src/test/java/
    ├── FFMPerformanceTest.java
    └── ValhallaComparisonTest.java
```

## Risk Assessment

### Low Risk
- FFM API is stable and production-ready in Java 25
- Platform ZSTD libraries are mature and widely available
- Fallback to pure Java ZSTD (zstd-jni) always available

### Medium Risk
- Valhalla timeline uncertain (likely Java 26-27)
- Preview features may change syntax
- **Mitigation**: Keep Valhalla as optional showcase, not core dependency

## Success Criteria

- [ ] FFM-based dictionary training achieves 10-15% additional compression
- [ ] FFM native calls are 2-10x faster than JNI equivalents
- [ ] Zero-copy memory operations validated in benchmarks
- [ ] Platform ZSTD libraries load successfully on Windows/Linux/macOS
- [ ] Valhalla value types demonstrate 20-40% memory reduction
- [ ] Complete documentation of FFM and Valhalla best practices

## Dependencies

**Required**:
- Java 25+ (for stable FFM API)
- Platform ZSTD libraries (Windows 11, Linux libzstd, macOS)

**Optional**:
- Java 26+ early access (for Valhalla preview features)

## Documentation Deliverables

- [ ] FFM API integration guide
- [ ] Platform-specific ZSTD library setup
- [ ] Performance benchmarks and analysis
- [ ] Valhalla migration guide
- [ ] Best practices for FFM and value types

## Timeline

**Week 1-2**: FFM bindings and dictionary training  
**Week 3**: Performance benchmarking and optimization  
**Week 4**: Valhalla preview integration and documentation

**Total**: 3-4 weeks

## Next Steps

1. Create FFM-based ZSTD wrapper for Java 25
2. Implement dictionary training with FFM
3. Add Valhalla preview module (Java 26 early access)
4. Document performance comparisons across all approaches

---

**Rationale**: This positions JARZ as a showcase for modern Java capabilities, demonstrating FFM API for production use and Valhalla for future optimization. The project becomes both a practical compression library and an educational resource for next-generation Java features.

*Created: December 2025*  
*Status: Awaiting approval for Phase 10 implementation*
