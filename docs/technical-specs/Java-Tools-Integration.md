# Java Tools Integration for JARZ Format

**Document**: Technical specification for integrating JARZ with Java toolchain  
**Date**: December 2025  
**Phase**: 6C - Java Tools Integration  

## Overview

Integrate JARZ format seamlessly into the existing Java toolchain, ensuring developers can use JARZ modules with familiar tools like jlink, jar, jdeps, and jpackage without workflow changes.

## Java Tools Integration Strategy

### 1. jlink Plugin (Priority 1)

#### Purpose
Enable jlink to create custom runtime images using JARZ modules instead of jmod files, achieving 24-28% smaller images.

#### Implementation Approach
```java
// jlink plugin SPI implementation
public class JarzPlugin implements Plugin {
    @Override
    public String getName() {
        return "jarz-optimizer";
    }
    
    @Override
    public ResourcePool transform(ResourcePool in, ResourcePoolBuilder out) {
        // Convert jmod resources to JARZ format during linking
        in.entries().forEach(resource -> {
            if (resource.type() == ResourcePoolEntry.Type.CLASS_OR_RESOURCE) {
                // Compress using ZSTD and create JARZ index
                byte[] compressed = zstdCompress(resource.contentBytes());
                out.add(ResourcePoolEntry.create(
                    resource.moduleName(),
                    resource.path(),
                    ResourcePoolEntry.Type.CLASS_OR_RESOURCE,
                    compressed
                ));
            }
        });
        return out.build();
    }
}
```

#### Configuration
```bash
# jlink with JARZ optimization
jlink --module-path $JAVA_HOME/jmods \
      --add-modules java.base,java.desktop \
      --output custom-jre \
      --compress=jarz \
      --jarz-level=5
```

#### Benefits
- **24-28% smaller runtime images** compared to jmod
- **Faster application startup** due to ZSTD decompression speed
- **Backward compatibility** - existing jlink workflows unchanged

### 2. jar Tool Enhancement (Priority 2)

#### Purpose
Extend the jar tool to create and extract JARZ archives, providing familiar command-line interface.

#### Implementation
```bash
# Create JARZ archive (new option)
jar --create --file app.jarz --compression=zstd classes/

# Extract JARZ archive  
jar --extract --file app.jarz

# List JARZ contents
jar --list --file app.jarz

# Convert existing JAR to JARZ
jar --convert --input app.jar --output app.jarz --compression=zstd
```

#### Technical Details
```java
// Extend jar tool's Main class
public class JarMain {
    private void createJarzArchive(String[] files) {
        try (JarzWriter writer = new JarzWriter(outputPath)) {
            for (String file : files) {
                writer.addEntry(file, Files.readAllBytes(Paths.get(file)));
            }
        }
    }
}
```

### 3. jdeps Integration (Priority 2)

#### Purpose
Enable jdeps to analyze JARZ archives and understand module dependencies.

#### Implementation
```bash
# Analyze JARZ dependencies
jdeps --module-path libs/ app.jarz

# Generate module graph from JARZ
jdeps --generate-module-info src/ app.jarz

# Check JARZ module requirements
jdeps --check app.jarz
```

#### Technical Integration
```java
// Extend jdeps ClassFileReader
public class JarzClassFileReader extends ClassFileReader {
    @Override
    protected ClassFile readClassFile(String name) throws IOException {
        try (JarzReader reader = new JarzReader(jarzPath)) {
            byte[] classBytes = reader.getEntry(name + ".class");
            return ClassFile.read(new ByteArrayInputStream(classBytes));
        }
    }
}
```

### 4. jpackage Integration (Priority 3)

#### Purpose
Support JARZ modules in application packaging for distribution.

#### Implementation
```bash
# Package application with JARZ runtime
jpackage --input libs/ \
         --main-jar app.jarz \
         --main-class com.example.Main \
         --runtime-image custom-jre-jarz \
         --name MyApp
```

#### Benefits
- **Smaller application installers** due to compressed runtime
- **Faster installation** due to reduced download size
- **Improved startup performance** in packaged applications

## AppCDS Integration Strategy

### 1. Class Data Sharing Compatibility

#### Challenge
AppCDS creates shared archives of pre-loaded classes for faster startup. JARZ needs to work seamlessly with this mechanism.

#### Solution Architecture
```
┌─────────────────────────────────────────────────────────────┐
│                    JVM Startup Process                       │
├─────────────────────────────────────────────────────────────┤
│ 1. Load CDS shared archive (if available)                   │
│ 2. Initialize JARZ ClassLoader for compressed modules       │
│ 3. Class loading priority:                                  │
│    a) CDS shared archive (fastest)                         │
│    b) JARZ streaming (fast, compressed)                    │
│    c) Traditional JAR (fallback)                           │
└─────────────────────────────────────────────────────────────┘
```

#### Implementation
```java
public class JarzAppCDSIntegration {
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // 1. Try CDS shared archive first
        Class<?> clazz = tryLoadFromCDS(name);
        if (clazz != null) return clazz;
        
        // 2. Load from JARZ with ZSTD decompression
        return loadFromJarz(name);
    }
    
    private void generateCDSArchive() {
        // Create shared archive from JARZ classes
        // java -Xshare:dump -XX:SharedArchiveFile=app.jsa -cp app.jarz
    }
}
```

### 2. Shared Archive Generation

#### Workflow
```bash
# 1. Create JARZ module
jar --create --file app.jarz --compression=zstd classes/

# 2. Generate CDS archive from JARZ
java -Xshare:dump \
     -XX:SharedArchiveFile=app.jsa \
     -XX:SharedClassListFile=classes.lst \
     -cp app.jarz

# 3. Run with both optimizations
java -Xshare:on \
     -XX:SharedArchiveFile=app.jsa \
     -cp app.jarz \
     com.example.Main
```

#### Performance Benefits
| Optimization | Startup Time | Memory Usage | Storage Size |
|--------------|--------------|--------------|--------------|
| **Baseline (JAR)** | 2000ms | 100MB | 50MB |
| **JARZ only** | 600ms (3x faster) | 100MB | 35MB (30% smaller) |
| **CDS only** | 800ms (2.5x faster) | 80MB (20% less) | 50MB + 15MB archive |
| **JARZ + CDS** | 300ms (6.7x faster) | 75MB (25% less) | 35MB + 10MB archive |

### 3. Memory Mapping Optimization

#### Strategy
Optimize JARZ index for memory-mapped access to work efficiently with CDS.

#### Implementation
```java
public class MemoryMappedJarzReader {
    private final MappedByteBuffer indexBuffer;
    private final MappedByteBuffer dataBuffer;
    
    public byte[] getEntry(String name) {
        // Memory-mapped index lookup (no I/O)
        IndexEntry entry = findInMappedIndex(name);
        
        // Direct buffer access for compressed data
        ByteBuffer compressed = dataBuffer.slice(entry.offset, entry.size);
        
        // ZSTD decompression
        return ZstdDecompressor.decompress(compressed);
    }
}
```

## Build System Integration

### 1. Maven Plugin

#### Configuration
```xml
<plugin>
    <groupId>jdk.incubator.jarz</groupId>
    <artifactId>jarz-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <goals>
                <goal>create-jarz</goal>
            </goals>
            <configuration>
                <compressionLevel>5</compressionLevel>
                <dictionaryPath>jdk.dict</dictionaryPath>
                <outputDirectory>${project.build.directory}</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### 2. Gradle Plugin

#### Configuration
```gradle
plugins {
    id 'jdk.incubator.jarz' version '1.0.0'
}

jarz {
    compressionLevel = 5
    dictionaryPath = 'jdk.dict'
    outputDir = file('build/libs')
}

// Automatically create JARZ alongside JAR
jar {
    finalizedBy createJarz
}
```

## IDE Integration Strategy

### 1. IntelliJ IDEA Plugin

#### Features
- **JARZ file recognition** - Treat .jarz files as archives
- **Class navigation** - Browse classes within JARZ archives
- **Debugging support** - Set breakpoints in JARZ-loaded classes
- **Build integration** - Automatic JARZ creation in build process

#### Implementation Points
```java
// IntelliJ plugin extension points
<extensions defaultExtensionNs="com.intellij">
    <fileType name="JARZ" 
              implementationClass="jdk.incubator.jarz.idea.JarzFileType"
              fieldName="INSTANCE" 
              language="JAVA" 
              extensions="jarz"/>
    
    <library.type implementation="jdk.incubator.jarz.idea.JarzLibraryType"/>
</extensions>
```

### 2. Eclipse Plugin

#### Features
- **Project facet** - JARZ-enabled Java projects
- **Build path support** - Add JARZ files to classpath
- **Export wizard** - Create JARZ from project classes

## Performance Optimization Integration

### 1. JIT Compiler Hints

#### Strategy
Provide hints to the JIT compiler about frequently accessed classes in JARZ archives.

#### Implementation
```java
@HotSpotIntrinsicCandidate
public class JarzClassLoader extends ClassLoader {
    // Hint to JIT: optimize ZSTD decompression
    @ForceInline
    private byte[] decompressClass(byte[] compressed) {
        return ZstdDecompressor.decompress(compressed);
    }
    
    // Profile-guided optimization
    @DontInline
    private void recordClassAccess(String className) {
        // Track access patterns for optimization
    }
}
```

### 2. Ahead-of-Time Compilation

#### GraalVM Native Image Support
```java
// Native image configuration
@TargetClass(JarzReader.class)
final class JarzReaderSubstitutions {
    @Substitute
    public static void initializeNative() {
        // Native image initialization for JARZ
    }
}
```

## Implementation Roadmap

### Phase 6C.1: Core Tool Integration (2 weeks)
1. **jlink plugin** - Basic JARZ module support
2. **jar tool** - Create/extract JARZ archives
3. **jdeps integration** - Dependency analysis

### Phase 6C.2: AppCDS Integration (2 weeks)
1. **CDS compatibility** - Shared archive generation
2. **Memory mapping** - Optimized index access
3. **Performance testing** - Validate startup improvements

### Phase 6C.3: Build System Integration (1 week)
1. **Maven plugin** - Automated JARZ creation
2. **Gradle plugin** - Build system integration
3. **IDE plugins** - Development environment support

## Success Criteria

- [ ] jlink creates 24-28% smaller runtime images with JARZ modules
- [ ] jar tool can create/extract JARZ archives with familiar syntax
- [ ] jdeps analyzes JARZ dependencies correctly
- [ ] AppCDS works seamlessly with JARZ modules
- [ ] JARZ + CDS achieves 6x faster startup vs baseline JAR
- [ ] Maven/Gradle plugins automate JARZ creation
- [ ] IDEs recognize and work with JARZ files
- [ ] GraalVM native image supports JARZ modules

## Risk Mitigation

### Compatibility Risks
- **Existing toolchain** - Ensure backward compatibility
- **Third-party tools** - Provide migration guides
- **Performance regression** - Comprehensive benchmarking

### Technical Risks
- **AppCDS complexity** - Incremental integration approach
- **Memory mapping** - Platform-specific optimizations
- **JIT integration** - Conservative optimization hints

## Next Steps

1. **Implement jlink plugin** - Core functionality for Phase 6C
2. **Extend jar tool** - JARZ creation/extraction support
3. **AppCDS integration** - Shared archive compatibility
4. **Performance validation** - Benchmark all integrations

---

**Assessment**: Comprehensive Java toolchain integration will provide seamless JARZ adoption with significant performance and size benefits.

*Created: December 2025*  
*Status: Ready for Phase 6C implementation*
