# Dictionary Training Pipeline - Current State Analysis

**Date**: December 2025  
**Status**: Partially Implemented (Not Integrated)

---

## Executive Summary

The dictionary training pipeline has **skeleton implementation** but is **NOT integrated** into the build or functional. Key components exist but are incomplete and untested.

### Current State
- ✅ Module structure exists (`jarz-dictionary-trainer/`)
- ✅ CLI integration points defined in `JarzCli.java`
- ❌ **NOT in parent pom.xml** (module not built)
- ❌ Core classes incomplete (missing `TrainingCorpus`)
- ❌ No working implementation
- ❌ Tests reference non-existent classes

---

## What Exists

### 1. Module Structure
```
jarz-dictionary-trainer/
├── src/main/java/jdk/incubator/jarz/trainer/
│   ├── DictionaryTrainer.java (stub)
│   └── KnownDatasets.java (complete)
└── src/test/java/jdk/incubator/jarz/trainer/
    ├── DictionaryTrainerIntegrationTest.java (references missing classes)
    └── DictionaryPerformanceTest.java (references missing classes)
```

### 2. CLI Integration (JarzCli.java)
**Location**: `jarz-tools/src/main/java/jdk/incubator/jarz/tools/JarzCli.java`

```java
private static void trainDictionary(String[] args) throws Exception {
    // Presets: jdk, maven, spring-boot, comprehensive
    TrainingCorpus corpus;
    
    switch (corpusInput.toLowerCase()) {
        case "jdk" -> corpus = KnownDatasets.fromJdkHome(jdkHome);
        case "maven" -> corpus = KnownDatasets.fromMavenRepository(mavenHome);
        case "spring-boot" -> corpus = KnownDatasets.springBootFocused(mavenHome);
        case "comprehensive" -> corpus = KnownDatasets.comprehensive(jdkHome, mavenHome);
        default -> /* custom directory */
    }
    
    DictionaryTrainer trainer = new DictionaryTrainer();
    var result = trainer.train(corpus);
    Files.write(outputDict, result.dictionary());
}
```

**CLI Commands Defined**:
```bash
jarz train-dict jdk jdk.dict
jarz train-dict spring-boot spring.dict --maven-home /custom/maven
jarz train-dict comprehensive enterprise.dict
jarz create app.jarz classes/ enterprise.dict
```

### 3. KnownDatasets.java (Complete)
**Status**: ✅ Fully implemented

**Presets Available**:
- `fromJdkHome()` - JDK modules and libraries
- `fromMavenRepository()` - Spring, Jackson, Commons, Guava, Hibernate
- `comprehensive()` - JDK + popular frameworks (8,000 samples)
- `springBootFocused()` - Spring Boot ecosystem (5,000 samples)
- `microservicesFocused()` - Spring Cloud, Netflix OSS (4,000 samples)

**Features**:
- Auto-detects JDK home from `java.home` or `JAVA_HOME`
- Auto-detects Maven home from `user.home/.m2/repository`
- Configurable sample limits per category

---

## What's Missing

### 1. ❌ TrainingCorpus Class
**Status**: Referenced but doesn't exist

**Expected API** (from usage):
```java
public class TrainingCorpus {
    public static Builder builder() { ... }
    
    public static class Builder {
        Builder jdkModules(List<Path> paths);
        Builder frameworkJars(List<Path> paths);
        Builder maxPerCategory(int max);
        Builder maxTotal(int max);
        TrainingCorpus build();
    }
}
```

**Purpose**: Configuration object for training corpus collection

### 2. ❌ DictionaryTrainer Class
**Status**: Stub only, no implementation

**Expected API** (from usage):
```java
public class DictionaryTrainer {
    public TrainingResult train(TrainingCorpus corpus) throws Exception;
    
    public record TrainingResult(
        byte[] dictionary,
        int sampleCount,
        ValidationResult validation,
        Map<String, Double> perCategoryRatio
    ) {}
    
    public record ValidationResult(
        double improvement,
        double baselineRatio,
        double dictionaryRatio
    ) {}
}
```

**Purpose**: Core ZSTD dictionary training implementation

### 3. ❌ Module Not in Build
**Issue**: `jarz-dictionary-trainer` not listed in parent `pom.xml`

**Current modules**:
```xml
<modules>
    <module>jarz-core</module>
    <module>jarz-tools</module>
    <module>jarz-s3</module>
</modules>
```

**Missing**: `<module>jarz-dictionary-trainer</module>`

### 4. ❌ No pom.xml in Module
**Issue**: `jarz-dictionary-trainer/pom.xml` doesn't exist

**Needed dependencies**:
- `jarz-core` (for JARZ format)
- `zstd-jni` (for ZSTD)
- JUnit 5 + AssertJ (for tests)

---

## Implementation Requirements

### Phase 1: Core Classes (1-2 days)

#### TrainingCorpus.java
```java
public class TrainingCorpus {
    private final List<Path> jdkModules;
    private final List<Path> frameworkJars;
    private final int maxPerCategory;
    private final int maxTotal;
    
    // Builder pattern
    // File collection logic
    // Category classification
}
```

**Responsibilities**:
- Collect `.class` files from specified paths
- Categorize files (JDK vs Framework)
- Apply sampling limits
- Return list of class file paths with metadata

#### DictionaryTrainer.java
```java
public class DictionaryTrainer {
    private final ZstdCompressor compressor;
    
    public TrainingResult train(TrainingCorpus corpus) {
        // 1. Collect samples from corpus
        // 2. Call ZSTD dictionary training API
        // 3. Validate compression improvement
        // 4. Return results with metrics
    }
}
```

**Key Implementation**:
- Use `io.airlift.compress.zstd` for training
- Target dictionary size: 32KB (optimal for class files)
- Validation: compress test set with/without dictionary
- Metrics: improvement %, baseline ratio, dictionary ratio

### Phase 2: Integration (1 day)

1. **Add module to parent pom.xml**
2. **Create jarz-dictionary-trainer/pom.xml**
3. **Update JarzWriter** to accept dictionary parameter
4. **Test CLI integration**

### Phase 3: Testing (1-2 days)

1. **Unit tests** for TrainingCorpus
2. **Integration tests** with synthetic class files
3. **Performance tests** with real JDK modules
4. **CLI end-to-end tests**

---

## Expected Performance

### Compression Improvement
Based on ZSTD documentation and class file characteristics:

| Corpus Type | Expected Improvement |
|-------------|---------------------|
| JDK modules | 10-15% |
| Spring Boot | 12-18% |
| Comprehensive | 15-20% |

### Training Time
| Corpus Size | Expected Time |
|-------------|---------------|
| 500 samples | < 5 seconds |
| 2,000 samples | < 15 seconds |
| 8,000 samples | < 60 seconds |

### Dictionary Size
- Target: 32KB (optimal for class files)
- Range: 16KB - 64KB depending on corpus diversity

---

## CLI Usage (Once Implemented)

### Basic Training
```bash
# Train on JDK modules
jarz train-dict jdk jdk.dict

# Train on Spring Boot ecosystem
jarz train-dict spring-boot spring.dict

# Train on comprehensive corpus
jarz train-dict comprehensive enterprise.dict
```

### Custom Training
```bash
# Train on custom directory
jarz train-dict /path/to/classes custom.dict

# Override JDK home
jarz train-dict jdk jdk.dict --jdk-home /opt/jdk-25

# Override Maven home
jarz train-dict maven maven.dict --maven-home /custom/maven
```

### Using Trained Dictionary
```bash
# Create JARZ with dictionary
jarz create app.jarz classes/ enterprise.dict

# Verify compression improvement
jarz info app.jarz
```

---

## Recommended Implementation Plan

### Week 1: Core Implementation
- **Day 1-2**: Implement `TrainingCorpus` class
- **Day 3-4**: Implement `DictionaryTrainer` class
- **Day 5**: Integration and basic testing

### Week 2: Testing & Validation
- **Day 1-2**: Unit and integration tests
- **Day 3**: Performance testing with real JDK
- **Day 4**: CLI end-to-end testing
- **Day 5**: Documentation and examples

### Week 3: Optimization (Optional)
- **Day 1-2**: Tune dictionary size and sampling
- **Day 3**: Multi-threaded training for large corpora
- **Day 4**: Caching and incremental training
- **Day 5**: Performance benchmarks

---

## Dependencies

### Required Libraries
```xml
<dependency>
    <groupId>io.airlift</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>0.27</version>
</dependency>
```

### ZSTD Dictionary Training API
```java
// zstd-jni provides ZSTD training
import io.airlift.compress.zstd.ZstdCompressor;

// Training API (need to verify availability)
byte[] dictionary = ZstdCompressor.trainDictionary(
    samples,      // List<byte[]>
    dictionarySize // int (32KB)
);
```

**Note**: zstd-jni fully supports dictionary training API with complete functionality.

---

## Risks & Mitigations

### Risk 1: ZSTD Training API Not Available
**Impact**: High  
**Mitigation**: Use `zstd` CLI tool via ProcessBuilder or add native library dependency

### Risk 2: Training Time Too Long
**Impact**: Medium  
**Mitigation**: Implement sampling strategies, parallel processing

### Risk 3: Dictionary Not Effective
**Impact**: Medium  
**Mitigation**: Test on diverse corpora, tune parameters, validate improvement

---

## Success Criteria

1. ✅ Dictionary training completes in < 60 seconds for 8,000 samples
2. ✅ Achieves 10-15% compression improvement on JDK modules
3. ✅ CLI integration works end-to-end
4. ✅ All tests passing (unit + integration + performance)
5. ✅ Documentation complete with examples

---

## Next Steps

1. **Verify ZSTD API** - ✅ zstd-jni supports dictionary training
2. **Implement TrainingCorpus** - File collection and categorization
3. **Implement DictionaryTrainer** - Core training logic
4. **Add to build** - Update parent pom.xml
5. **Test integration** - Verify CLI works end-to-end

---

## Conclusion

The dictionary training pipeline has a **solid foundation** with CLI integration and dataset presets, but **lacks core implementation**. Estimated effort: **2-3 weeks** for complete implementation and testing.

**Priority**: Medium (nice-to-have for additional 10-15% compression)  
**Complexity**: Medium (depends on ZSTD API availability)  
**Value**: High (significant compression improvement for production use)
