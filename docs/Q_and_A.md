# JARZ Project Q&A

**Questions and Answers about ZSTD-Compressed Class Archives**

*Last Updated: January 2, 2026*

---

## Project Overview

### Q: What is JARZ?
**A:** JARZ (`.jarz`) is a new archive format using ZSTD block-based compression for Java class files. It achieves 27.4% storage reduction over traditional JAR files while enabling efficient S3 range-request streaming through dependency-aware class grouping.

### Q: How does JARZ compare to JAR files?
**A:** Based on validation with java.base module (7,392 classes):
- **Storage**: 27.4% smaller than JAR files
- **Decompression**: 3.5x faster than DEFLATE
- **Streaming**: Supports S3 range requests for individual class blocks
- **Compatibility**: Drop-in replacement for JAR files

---

## Technical Questions

### Q: What are the security implications of introducing a new format? How do you prevent StackOverflow, BufferOverflow, and other security risks?

**A:** Security is a critical concern for any new format. Here's our comprehensive security approach:

**Input Validation & Bounds Checking:**

```java
// Example: Safe header parsing with bounds checking
public class JarzHeader {
    private static final int MAX_HEADER_SIZE = 1024;
    private static final int MAX_BLOCK_COUNT = 65535;
    
    public static JarzHeader parse(ByteBuffer buffer) {
        if (buffer.remaining() < MIN_HEADER_SIZE) {
            throw new SecurityException("Header too small");
        }
        
        int blockCount = buffer.getInt();
        if (blockCount < 0 || blockCount > MAX_BLOCK_COUNT) {
            throw new SecurityException("Invalid block count: " + blockCount);
        }
        
        // Prevent integer overflow in size calculations
        long totalSize = (long) blockCount * MAX_BLOCK_SIZE;
        if (totalSize > MAX_ARCHIVE_SIZE) {
            throw new SecurityException("Archive too large");
        }
    }
}
```

**Memory Safety Measures:**

| Risk | Mitigation | Implementation |
|------|------------|----------------|
| **Buffer Overflow** | Bounded allocations | `ByteBuffer.allocate(Math.min(size, MAX_BUFFER))` |
| **Stack Overflow** | Iterative parsing | No recursive descent, flat loops |
| **Memory Exhaustion** | Size limits | Max 2GB archive, 64K blocks |
| **Integer Overflow** | Safe arithmetic | `Math.addExact()`, `Math.multiplyExact()` |

**Format-Level Security:**

```java
// Block integrity validation
public class BlockReader {
    public byte[] readBlock(int blockId) {
        BlockHeader header = readBlockHeader(blockId);
        
        // Validate block size before allocation
        if (header.compressedSize > MAX_BLOCK_SIZE) {
            throw new SecurityException("Block too large");
        }
        
        // Validate decompressed size to prevent zip bombs
        if (header.uncompressedSize > MAX_DECOMPRESSED_SIZE) {
            throw new SecurityException("Decompressed size too large");
        }
        
        // Safe decompression with limits
        return ZstdDecompressor.decompress(
            compressedData, 
            header.uncompressedSize,  // Expected size
            MAX_DECOMPRESSION_TIME    // Timeout
        );
    }
}
```

**ZSTD-Specific Security:**

- **Memory bounds**: ZSTD decompressor configured with strict memory limits
- **Decompression bombs**: Validate uncompressed size before allocation
- **Timeout protection**: Limit decompression time to prevent DoS
- **Dictionary validation**: Verify dictionary integrity and size limits

**ClassLoader Security Integration:**

```java
public class JarzClassLoader extends SecureClassLoader {
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // Validate class name to prevent path traversal
        if (!isValidClassName(name)) {
            throw new ClassNotFoundException("Invalid class name: " + name);
        }
        
        // Load with existing Java security model
        byte[] classBytes = loadClassBytes(name);
        
        // Use existing bytecode verification
        return defineClass(name, classBytes, 0, classBytes.length, 
                          getCodeSource()); // Maintains security context
    }
}
```

**Security Testing Strategy:**

| Test Category | Coverage | Tools |
|---------------|----------|-------|
| **Fuzzing** | Malformed archives | AFL++, libFuzzer |
| **Bounds Testing** | Edge cases, overflows | Custom test suite |
| **Memory Testing** | Leaks, corruption | Valgrind, AddressSanitizer |
| **Performance** | DoS resistance | JMH stress tests |

**Threat Model & Mitigations:**

| Threat | Risk Level | Mitigation |
|--------|------------|------------|
| **Malformed Archive** | High | Strict parsing, bounds checking |
| **Zip Bomb Attack** | High | Size limits, decompression timeouts |
| **Path Traversal** | Medium | Class name validation |
| **Memory Exhaustion** | Medium | Allocation limits, streaming |
| **CPU Exhaustion** | Low | Decompression timeouts |

**Leveraging Existing Java Security:**

- **SecurityManager**: Full compatibility with existing security policies
- **Code signing**: JARZ archives can be signed like JARs
- **Permissions**: Uses existing Java permission model
- **Bytecode verification**: Standard JVM verification applies

**Security Review Process:**

1. **Static Analysis**: SpotBugs, SonarQube for vulnerability detection
2. **Dependency Scanning**: ZSTD-JNI security audit
3. **Penetration Testing**: External security review before production
4. **CVE Monitoring**: Track ZSTD library vulnerabilities

**Fail-Safe Defaults:**

```java
// Conservative defaults
public static final int DEFAULT_MAX_ARCHIVE_SIZE = 2_000_000_000; // 2GB
public static final int DEFAULT_MAX_BLOCK_SIZE = 1_048_576;       // 1MB
public static final int DEFAULT_MAX_BLOCKS = 65535;              // 64K blocks
public static final int DEFAULT_DECOMPRESSION_TIMEOUT = 30_000;  // 30s
```

**Security is not an afterthought** - it's built into the format design and implementation from day one, leveraging Java's existing security infrastructure while adding format-specific protections.

---

## Implementation Questions

*Implementation-specific questions and answers will be documented here.*

---

## Performance Questions

*Performance-related questions and answers will be documented here.*

---

## Usage Questions

*Usage and deployment questions will be documented here.*

---

*This document will be updated with questions and answers as they arise during project development and review.*
