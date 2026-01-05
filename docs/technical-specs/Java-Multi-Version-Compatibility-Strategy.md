# Java Multi-Version Compatibility Strategy

**Document**: Java 11/21 Compatibility Implementation Plan  
**Date**: 2026-01-02  
**Status**: Proposal  
**Target**: Support Java 11+ with enhanced Java 21+ features

## Overview

This document outlines the strategy for implementing Java multi-version compatibility in JARZ, enabling:
- **Java 11+**: Full JARZ functionality with HTTP/2 support
- **Java 21+**: Enhanced performance with virtual threads
- **Single JAR**: Automatic feature selection based on runtime

## Current Status

**✅ Java 21+ Support:**
- Virtual threads in CDN ClassLoader
- HTTP/2 multiplexing with virtual thread pools
- Advanced concurrency patterns

**🔄 Java 11/17 Compatibility:**
- HTTP/2 support available (HttpClient since Java 11)
- Virtual threads not available
- Need fallback to standard thread pools

## Recommended Approach: Multi-Release JAR

### Benefits
- **Single Distribution**: One JAR works on all supported Java versions
- **Automatic Selection**: Runtime automatically chooses best implementation
- **Backward Compatibility**: Java 11+ users get full functionality
- **Forward Compatibility**: Java 21+ users get enhanced performance
- **Standard Approach**: Uses official Java multi-release JAR specification

### Implementation Strategy

#### 1. Directory Structure
```
src/
├── main/
│   ├── java/                           # Java 11+ compatible code (baseline)
│   │   └── jdk/incubator/jarz/cdn/
│   │       ├── CdnJarzClassLoader.java # Standard thread pool version
│   │       └── HttpClientFactory.java  # Interface for HTTP client creation
│   └── java21/                         # Java 21+ specific optimizations
│       └── jdk/incubator/jarz/cdn/
│           └── CdnJarzClassLoader.java # Virtual threads version
└── META-INF/
    └── MANIFEST.MF                     # Multi-Release: true
```

#### 2. Maven Configuration
```xml
<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
    <maven.compiler.release>11</maven.compiler.release>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <release>11</release>
    </configuration>
    <executions>
        <!-- Java 21 specific compilation -->
        <execution>
            <id>compile-java21</id>
            <goals>
                <goal>compile</goal>
            </goals>
            <configuration>
                <release>21</release>
                <compileSourceRoots>
                    <compileSourceRoot>${project.basedir}/src/main/java21</compileSourceRoot>
                </compileSourceRoots>
                <multiReleaseOutput>true</multiReleaseOutput>
            </configuration>
        </execution>
    </executions>
</plugin>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-jar-plugin</artifactId>
    <version>3.3.0</version>
    <configuration>
        <archive>
            <manifestEntries>
                <Multi-Release>true</Multi-Release>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

#### 3. Code Implementation Pattern

**Base Implementation (Java 11+):**
```java
// src/main/java/jdk/incubator/jarz/cdn/CdnJarzClassLoader.java
public class CdnJarzClassLoader extends SecureClassLoader {
    private final ExecutorService executor;
    
    public CdnJarzClassLoader(String baseUrl) {
        // Use standard thread pool for Java 11+
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cdn-jarz-" + System.nanoTime());
            return t;
        });
    }
    
    protected CompletableFuture<byte[]> fetchBlockAsync(int blockId) {
        return CompletableFuture.supplyAsync(() -> fetchBlock(blockId), executor);
    }
}
```

**Enhanced Implementation (Java 21+):**
```java
// src/main/java21/jdk/incubator/jarz/cdn/CdnJarzClassLoader.java
public class CdnJarzClassLoader extends SecureClassLoader {
    private final ExecutorService executor;
    
    public CdnJarzClassLoader(String baseUrl) {
        // Use virtual threads for Java 21+
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    protected CompletableFuture<byte[]> fetchBlockAsync(int blockId) {
        return CompletableFuture.supplyAsync(() -> fetchBlock(blockId), executor);
    }
}
```

## Alternative Approaches Considered

### 1. Runtime Detection Pattern
```java
private static final boolean VIRTUAL_THREADS_AVAILABLE = isVirtualThreadsAvailable();

private static boolean isVirtualThreadsAvailable() {
    try {
        Class.forName("java.lang.Thread$Builder");
        return true;
    } catch (ClassNotFoundException e) {
        return false;
    }
}
```

**Pros**: Single source file  
**Cons**: Runtime overhead, reflection usage, complex conditional logic

### 2. Factory Pattern Approach
```java
public interface HttpClientFactory {
    CompletableFuture<byte[]> fetchAsync(String url);
}
```

**Pros**: Clean separation, testable  
**Cons**: Additional complexity, multiple implementations to maintain

### 3. Maven Profile Approach
```xml
<profiles>
    <profile>
        <id>java11</id>
        <activation><jdk>[11,21)</jdk></activation>
    </profile>
    <profile>
        <id>java21</id>
        <activation><jdk>[21,)</jdk></activation>
    </profile>
</profiles>
```

**Pros**: Build-time selection  
**Cons**: Multiple JARs to distribute, user confusion

## Implementation Plan

### Phase 1: Multi-Release JAR Setup
- [ ] Configure Maven for multi-release compilation
- [ ] Set up directory structure for Java 11/21 sources
- [ ] Update MANIFEST.MF with Multi-Release flag
- [ ] Validate JAR structure with `jar --describe-module`

### Phase 2: Code Refactoring
- [ ] Extract common interface for ClassLoader implementations
- [ ] Move current virtual thread code to `java21/` directory
- [ ] Create Java 11 compatible version in `java/` directory
- [ ] Ensure API compatibility between versions

### Phase 3: Testing Strategy
- [ ] Test on Java 11 runtime (standard threads)
- [ ] Test on Java 21 runtime (virtual threads)
- [ ] Validate automatic version selection
- [ ] Performance benchmarks for both versions

### Phase 4: Documentation Updates
- [ ] Update README.md with Java version support matrix
- [ ] Document performance differences between versions
- [ ] Provide migration guide for existing users
- [ ] Update API documentation

## Expected Outcomes

### Java 11+ Users
- ✅ Full JARZ functionality including S3 and CDN streaming
- ✅ HTTP/2 support with standard thread pools
- ✅ 27.4% compression improvement
- ✅ Production-ready stability

### Java 21+ Users
- ✅ All Java 11+ features
- ✅ Enhanced performance with virtual threads
- ✅ Better resource utilization for concurrent operations
- ✅ Improved scalability for high-throughput scenarios

### Performance Impact
- **Java 11**: Baseline performance with standard threads
- **Java 21**: 2-5x better performance for concurrent HTTP operations
- **Memory**: Lower memory overhead with virtual threads (Java 21+)
- **Scalability**: Better handling of thousands of concurrent requests (Java 21+)

## Validation Criteria

### Functional Requirements
- [ ] Single JAR works on both Java 11 and Java 21
- [ ] All JARZ features available on both versions
- [ ] No runtime errors or compatibility issues
- [ ] Automatic selection of appropriate implementation

### Performance Requirements
- [ ] Java 11 performance matches current baseline
- [ ] Java 21 performance shows measurable improvement
- [ ] No performance regression on either version
- [ ] Memory usage within acceptable limits

### Quality Requirements
- [ ] All existing tests pass on both Java versions
- [ ] Code coverage maintained at current levels
- [ ] Documentation updated and accurate
- [ ] Build process reliable and reproducible

## Conclusion

Multi-Release JAR provides the optimal solution for JARZ Java version compatibility, enabling a single distribution that automatically adapts to the runtime environment while maximizing performance on newer Java versions.

This approach aligns with Java ecosystem best practices and provides the best user experience for both current and future Java versions.

---
**Next Steps**: Begin Phase 1 implementation with Maven configuration updates and directory structure setup.
