# Migration from Aircompressor to zstd-jni

**Migration Date**: 2025-12-17  
**Target**: Replace `io.airlift:aircompressor` with `com.github.luben:zstd-jni:1.5.7-6`  
**Reason**: Enable compression level control to achieve 24-28% improvement target

## Current State Analysis

### Dependencies
- **Current**: `io.airlift:aircompressor:0.27`
- **Target**: `com.github.luben:zstd-jni:1.5.7-6`

### Affected Files
1. `jarz-core/src/main/java/jdk/incubator/jarz/JarzWriter.java`
2. `jarz-core/src/main/java/jdk/incubator/jarz/JarzReader.java`
3. `jarz-s3/src/main/java/jdk/incubator/jarz/s3/S3JarzClassLoader.java`
4. `jarz-dictionary-trainer/src/main/java/jdk/incubator/jarz/trainer/DictionaryTrainer.java`
5. Test files in `/tests/` directory

### Current Limitation
Aircompressor's `ZstdCompressor` uses fixed compression level (~3), preventing optimization for better compression ratios.

## Migration Steps

### 1. Update Dependencies

**Parent POM** (`pom.xml`):
```xml
<!-- REMOVE -->
<dependency>
    <groupId>io.airlift</groupId>
    <artifactId>aircompressor</artifactId>
    <version>${aircompressor.version}</version>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>com.github.luben</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>1.5.7-6</version>
</dependency>
```

**Properties Update**:
```xml
<!-- REMOVE -->
<aircompressor.version>0.27</aircompressor.version>

<!-- ADD -->
<zstd-jni.version>1.5.7-6</zstd-jni.version>
```

### 2. Code Changes

#### JarzWriter.java
```java
// BEFORE
import io.airlift.compress.zstd.ZstdCompressor;

private final ZstdCompressor compressor;

public JarzWriter(Path path, byte[] dictionary, int compressionLevel) throws IOException {
    this.compressor = new ZstdCompressor();
    // compressionLevel parameter ignored - aircompressor limitation
}

private byte[] compress(byte[] data) throws IOException {
    int maxLen = compressor.maxCompressedLength(data.length);
    byte[] output = new byte[maxLen];
    int len = compressor.compress(data, 0, data.length, output, 0, maxLen);
    byte[] result = new byte[len];
    System.arraycopy(output, 0, result, 0, len);
    return result;
}
```

```java
// AFTER
import com.github.luben.zstd.Zstd;

private final int compressionLevel;

public JarzWriter(Path path, byte[] dictionary, int compressionLevel) throws IOException {
    this.compressionLevel = compressionLevel;
    // No compressor instance needed
}

private byte[] compress(byte[] data) throws IOException {
    if (dictionary != null) {
        return Zstd.compress(data, dictionary, compressionLevel);
    } else {
        return Zstd.compress(data, compressionLevel);
    }
}
```

#### JarzReader.java
```java
// BEFORE
import io.airlift.compress.zstd.ZstdDecompressor;

private final ZstdDecompressor decompressor;

public JarzReader(Path path) throws IOException {
    this.decompressor = new ZstdDecompressor();
}

private byte[] decompress(byte[] compressed, int originalSize) {
    byte[] output = new byte[originalSize];
    decompressor.decompress(compressed, 0, compressed.length, output, 0, originalSize);
    return output;
}
```

```java
// AFTER
import com.github.luben.zstd.Zstd;

// No decompressor field needed

public JarzReader(Path path) throws IOException {
    // Remove decompressor initialization
}

private byte[] decompress(byte[] compressed, int originalSize) {
    if (dictionary != null) {
        return Zstd.decompress(compressed, dictionary, originalSize);
    } else {
        return Zstd.decompress(compressed, originalSize);
    }
}
```

### 3. Compression Level Strategy

**Default Levels**:
- **Level 9**: Target for 28% improvement (based on command-line testing)
- **Level 3**: Fallback for speed-critical operations
- **Level 1**: Fastest compression for real-time scenarios

**Constructor Updates**:
```java
public JarzWriter(Path path) throws IOException {
    this(path, null, 9); // Default to level 9 for better compression
}

public JarzWriter(Path path, byte[] dictionary) throws IOException {
    this(path, dictionary, 9); // Default to level 9
}
```

### 4. Error Handling Updates

**zstd-jni Exception Handling**:
```java
private byte[] compress(byte[] data) throws IOException {
    try {
        if (dictionary != null) {
            return Zstd.compress(data, dictionary, compressionLevel);
        } else {
            return Zstd.compress(data, compressionLevel);
        }
    } catch (Exception e) {
        throw new IOException("ZSTD compression failed", e);
    }
}

private byte[] decompress(byte[] compressed, int originalSize) throws IOException {
    try {
        if (dictionary != null) {
            return Zstd.decompress(compressed, dictionary, originalSize);
        } else {
            return Zstd.decompress(compressed, originalSize);
        }
    } catch (Exception e) {
        throw new IOException("ZSTD decompression failed", e);
    }
}
```

### 5. Dictionary Support Enhancement

**Dictionary Training** (DictionaryTrainer.java):
```java
// BEFORE
private final ZstdCompressor compressor = new ZstdCompressor();

// AFTER
// No compressor instance needed

private byte[] trainZstdDictionary(List<byte[]> samples, int dictSize) {
    return Zstd.trainFromBuffer(samples, dictSize);
}
```

### 6. Performance Validation

**Expected Improvements**:
- **Current (aircompressor level ~3)**: 4.4% improvement over ZIP
- **Target (zstd-jni level 9)**: 28.1% improvement over ZIP
- **Compression ratio gain**: ~24% better compression

**Validation Test**:
```java
@Test
void validateCompressionImprovement() throws IOException {
    byte[] testData = Files.readAllBytes(Paths.get("java.base-extracted"));
    
    // Test different levels
    byte[] level3 = Zstd.compress(testData, 3);
    byte[] level9 = Zstd.compress(testData, 9);
    
    double level3Ratio = (double) level3.length / testData.length;
    double level9Ratio = (double) level9.length / testData.length;
    
    System.out.printf("Level 3: %.1f%% compression\n", (1 - level3Ratio) * 100);
    System.out.printf("Level 9: %.1f%% compression\n", (1 - level9Ratio) * 100);
    
    // Verify level 9 achieves target improvement
    assertThat(level9Ratio).isLessThan(level3Ratio * 0.76); // 24% better
}
```

## Migration Checklist

- [ ] Update parent POM dependencies
- [ ] Update all module POM files
- [ ] Migrate JarzWriter.java
- [ ] Migrate JarzReader.java  
- [ ] Migrate S3JarzClassLoader.java
- [ ] Migrate DictionaryTrainer.java
- [ ] Update test files
- [ ] Run compression validation tests
- [ ] Verify round-trip integrity
- [ ] Performance benchmark comparison
- [ ] Update documentation

## Risk Mitigation

1. **Compatibility**: zstd-jni uses same ZSTD format - existing archives remain readable
2. **Performance**: JNI overhead minimal for large data blocks (>1KB)
3. **Platform Support**: zstd-jni includes native libraries for all major platforms
4. **Fallback**: Keep aircompressor dependency temporarily during transition

## Expected Outcomes

- **Compression Improvement**: 24-28% better compression ratios
- **Flexibility**: Configurable compression levels (1-22)
- **Performance**: Comparable or better compression speed
- **Dictionary Support**: Enhanced dictionary training capabilities
- **Target Achievement**: Reach 24-28% improvement goal with level 9

## Rollback Plan

If issues arise:
1. Revert POM changes
2. Restore original import statements
3. Remove compression level parameters
4. Test with original aircompressor implementation

**Migration Priority**: High - Required to achieve compression improvement targets
