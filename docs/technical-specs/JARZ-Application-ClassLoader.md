# JARZ Application ClassLoader Specification

**Document**: JARZ Application ClassLoader  
**Version**: 1.0  
**Date**: December 2025  
**Status**: Design Phase

## Overview

The **JarzApplicationClassLoader** implements standard Java application loading behavior for JARZ archives, enabling `java -jarz MyApp.uber.jarz` with full manifest compatibility and classpath resolution.

## Standard Java Behavior

When executing `java -jar MyApp.jar`, the JVM follows this sequence:

1. **Read MANIFEST.MF** from `META-INF/MANIFEST.MF`
2. **Extract Main-Class** attribute for application entry point
3. **Parse Class-Path** attribute for additional JARs (e.g., `lib/commons-lang3.jar lib/jackson-core.jar`)
4. **Build composite classpath** from main JAR + Class-Path entries
5. **Load classes** with delegation: main JAR → classpath JARs → parent ClassLoader

## JARZ Application ClassLoader Design

### Architecture

```
JarzApplicationClassLoader
├── Main JARZ Reader (BlockReader/JarzReader)
├── Classpath Loaders (JAR/JARZ files from Class-Path)
├── Manifest Parser (META-INF/MANIFEST.MF)
└── Resource Resolution (main → classpath → parent)
```

### Class Loading Hierarchy

```java
JarzApplicationClassLoader extends SecureClassLoader {
    // 1. Main JARZ archive
    private final BlockReader mainJarzReader;
    
    // 2. Classpath entries from MANIFEST.MF Class-Path
    private final List<ClassLoader> classpathLoaders;
    
    // 3. Parsed manifest
    private final Manifest manifest;
}
```

## Implementation Specification

### 1. Initialization Sequence

```java
public JarzApplicationClassLoader(Path jarzFile, ClassLoader parent) throws IOException {
    super(parent);
    
    // Step 1: Open main JARZ archive
    this.mainJarzReader = new BlockReader(jarzFile);
    
    // Step 2: Read and parse MANIFEST.MF
    this.manifest = readManifest();
    
    // Step 3: Resolve Class-Path entries
    this.classpathLoaders = resolveClassPath(jarzFile.getParent());
    
    // Step 4: Cache Main-Class for launcher
    this.mainClassName = manifest.getMainAttributes().getValue("Main-Class");
}
```

### 2. Manifest Reading

```java
private Manifest readManifest() throws IOException {
    // Read from MANIFEST block (BlockType.MANIFEST)
    byte[] manifestData = mainJarzReader.readResource("META-INF/MANIFEST.MF");
    if (manifestData == null) {
        throw new IOException("No MANIFEST.MF found in JARZ archive");
    }
    
    return new Manifest(new ByteArrayInputStream(manifestData));
}
```

### 3. Class-Path Resolution

```java
private List<ClassLoader> resolveClassPath(Path baseDir) throws IOException {
    String classPath = manifest.getMainAttributes().getValue("Class-Path");
    if (classPath == null || classPath.trim().isEmpty()) {
        return List.of();
    }
    
    List<ClassLoader> loaders = new ArrayList<>();
    
    for (String entry : classPath.split("\\s+")) {
        Path entryPath = baseDir.resolve(entry);
        
        if (Files.exists(entryPath)) {
            if (entry.endsWith(".jarz") || entry.endsWith(".jarz")) {
                // Nested JARZ ClassLoader
                loaders.add(new JarzApplicationClassLoader(entryPath, this));
            } else if (entry.endsWith(".jar")) {
                // Standard JAR ClassLoader
                loaders.add(new URLClassLoader(new URL[]{entryPath.toUri().toURL()}, this));
            }
        } else {
            System.err.println("Warning: Class-Path entry not found: " + entry);
        }
    }
    
    return loaders;
}
```

### 4. Class Loading Strategy

```java
@Override
protected Class<?> findClass(String name) throws ClassNotFoundException {
    // Step 1: Try main JARZ archive
    byte[] classData = mainJarzReader.readClass(name.replace('.', '/'));
    if (classData != null) {
        return defineClass(name, classData, 0, classData.length);
    }
    
    // Step 2: Try Class-Path entries in order
    for (ClassLoader classpathLoader : classpathLoaders) {
        try {
            return classpathLoader.loadClass(name);
        } catch (ClassNotFoundException ignored) {
            // Continue to next classpath entry
        }
    }
    
    // Step 3: Not found
    throw new ClassNotFoundException(name);
}
```

### 5. Resource Loading Strategy

```java
@Override
public URL findResource(String name) {
    // Step 1: Try main JARZ archive
    if (mainJarzReader.hasResource(name)) {
        return createJarzURL(name);
    }
    
    // Step 2: Try Class-Path entries
    for (ClassLoader classpathLoader : classpathLoaders) {
        URL resource = classpathLoader.getResource(name);
        if (resource != null) {
            return resource;
        }
    }
    
    return null;
}
```

## Command Line Integration

### JVM Launcher Integration

```java
// New JVM option: -jarz (equivalent to -jar for JARZ files)
public class JarzLauncher {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -jarz <jarz-file> [args...]");
            System.exit(1);
        }
        
        Path jarzFile = Paths.get(args[0]);
        String[] appArgs = Arrays.copyOfRange(args, 1, args.length);
        
        // Create JARZ application ClassLoader
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile, 
                ClassLoader.getSystemClassLoader())) {
            
            // Set as context ClassLoader
            Thread.currentThread().setContextClassLoader(loader);
            
            // Load and invoke main class
            String mainClassName = loader.getMainClassName();
            Class<?> mainClass = loader.loadClass(mainClassName);
            Method mainMethod = mainClass.getMethod("main", String[].class);
            
            mainMethod.invoke(null, (Object) appArgs);
        }
    }
}
```

### Usage Examples

```bash
# Standard JAR behavior
java -jar MyApp.jar arg1 arg2

# Equivalent JARZ behavior
java -jarz MyApp.jarz arg1 arg2

# With classpath dependencies
# MANIFEST.MF contains: Class-Path: lib/commons-lang3.jar lib/jackson-core.jarz
java -jarz MyApp.uber.jarz
```

## Manifest Compatibility

### Required Attributes

```properties
# META-INF/MANIFEST.MF
Manifest-Version: 1.0
Main-Class: com.example.MyApplication
Class-Path: lib/commons-lang3.jar lib/jackson-core.jarz lib/spring-boot.jar
```

### Supported Attributes

| Attribute | Support | Notes |
|-----------|---------|-------|
| `Main-Class` | ✅ Full | Application entry point |
| `Class-Path` | ✅ Full | JAR and JARZ files supported |
| `Implementation-Version` | ✅ Full | Standard manifest attribute |
| `Implementation-Vendor` | ✅ Full | Standard manifest attribute |
| `Sealed` | ⚠️ Partial | Package sealing (future enhancement) |
| `Multi-Release` | ❌ Not supported | Java 9+ feature (future) |

## Performance Characteristics

### Memory Usage

- **Main JARZ**: Block-level caching (LRU eviction)
- **Classpath JARs**: Standard JVM ClassLoader caching
- **Manifest**: Cached in memory after first read

### Loading Performance

```
Class Loading Order (fastest → slowest):
1. Main JARZ (block-cached, ZSTD decompression)
2. Classpath JARZ files (nested ClassLoaders)
3. Classpath JAR files (ZIP decompression)
4. Parent ClassLoader (system/bootstrap)
```

### S3 Streaming Support

```java
// S3-based JARZ application
S3JarzApplicationClassLoader loader = new S3JarzApplicationClassLoader(
    s3Client, "my-bucket", "MyApp.uber.jarz");

// Supports Class-Path entries in S3
// Class-Path: s3://my-bucket/lib/commons-lang3.jarz s3://my-bucket/lib/jackson.jar
```

## Security Considerations

### Code Signing

- **MANIFEST block**: Contains signatures (`.SF`, `.RSA`, `.DSA` files)
- **Verification**: Standard Java code signing verification
- **Trust chain**: Maintains JAR-compatible security model

### Sealed Packages

```java
// Package sealing support (future enhancement)
@Override
protected Package definePackage(String name, Manifest man, URL url) {
    // Check package sealing attributes
    return super.definePackage(name, man, url);
}
```

## Error Handling

### Missing Dependencies

```java
// Graceful handling of missing Class-Path entries
if (!Files.exists(classpathEntry)) {
    if (isRequired(classpathEntry)) {
        throw new IOException("Required classpath entry not found: " + classpathEntry);
    } else {
        System.err.println("Warning: Optional classpath entry not found: " + classpathEntry);
    }
}
```

### Circular Dependencies

```java
// Prevent infinite recursion in nested JARZ ClassLoaders
private final Set<Path> loadingStack = new HashSet<>();

private ClassLoader createNestedLoader(Path jarzPath) throws IOException {
    if (loadingStack.contains(jarzPath)) {
        throw new IOException("Circular dependency detected: " + jarzPath);
    }
    
    loadingStack.add(jarzPath);
    try {
        return new JarzApplicationClassLoader(jarzPath, this);
    } finally {
        loadingStack.remove(jarzPath);
    }
}
```

## Implementation Phases

### Phase 1: Core Implementation
- [x] **Document specification** ✅
- [ ] **JarzApplicationClassLoader** - Basic class loading
- [ ] **Manifest parsing** - Main-Class and Class-Path
- [ ] **Classpath resolution** - JAR and JARZ support
- [ ] **Unit tests** - Core functionality

### Phase 2: Command Line Integration
- [ ] **JarzLauncher** - `java -jarz` equivalent
- [ ] **JVM integration** - Native launcher support
- [ ] **Error handling** - User-friendly messages
- [ ] **Integration tests** - Real application scenarios

### Phase 3: Advanced Features
- [ ] **S3JarzApplicationClassLoader** - S3-based applications
- [ ] **Package sealing** - Security enhancement
- [ ] **Multi-Release support** - Java 9+ compatibility
- [ ] **Performance optimization** - Parallel loading

## Success Criteria

- ✅ **Drop-in replacement**: `java -jarz` works identically to `java -jar`
- ✅ **Manifest compatibility**: All standard attributes supported
- ✅ **Mixed classpath**: JAR and JARZ files work together
- ✅ **Performance**: Faster than JAR due to ZSTD decompression
- ✅ **Security**: Maintains Java security model

## Related Documents

- [JARZ v2 Block Format](JARZ-v2-Block-Format.md)
- [Resource Loading Strategy](../analysis/Resource-Loading-Strategy.md)
- [S3 Streaming ClassLoader](S3-Streaming-ClassLoader.md)

---

*This specification enables JARZ archives to be drop-in replacements for JAR files in Java applications, maintaining full compatibility with existing deployment and build processes.*
