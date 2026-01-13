# Enhanced JARZ ClassLoader Hierarchy with Bundle Index Support

## Overview

This document describes the enhanced JARZ ClassLoader architecture that consolidates bundle index functionality in the base class, eliminating code duplication and providing consistent multi-JARZ support across all deployment scenarios.

## Architecture

### Class Hierarchy
```
ClassLoader (Java standard)
└── JarzClassLoader (abstract base)
    ├── JarzApplicationClassLoader (local files)
    ├── CdnJarzClassLoader (HTTP/CDN streaming)
    └── S3JarzClassLoader (S3 streaming)
```

### Key Design Principles

1. **DRY (Don't Repeat Yourself)** - Bundle index logic centralized in base class
2. **Consistent API** - All ClassLoaders support bundle index with same constructor pattern
3. **Lazy Loading** - Child ClassLoaders created only when needed
4. **Backward Compatibility** - Bundle index is optional parameter

## Base JarzClassLoader

### Responsibilities
- Bundle index management and class lookup
- Child ClassLoader creation and caching
- Delegation to appropriate child loaders for cross-JARZ class loading
- Standard ClassLoader delegation model compliance

### Key Methods
```java
public abstract class JarzClassLoader extends ClassLoader {
    // Bundle index support
    protected JarzLocalIndex.JarzBundleIndex bundleIndex;
    protected final Map<String, JarzClassLoader> childLoaders = new HashMap<>();
    
    // Constructor with optional bundle index
    protected JarzClassLoader(JarzDataProvider provider, ClassLoader parent, Path bundleIndexPath);
    
    // Enhanced class loading with bundle index lookup
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException;
    
    // Abstract methods for child classes
    protected abstract String getCurrentJarzUrl();
    protected abstract JarzClassLoader createChildLoader(String jarzUrl) throws IOException;
}
```

## Child ClassLoader Implementations

### JarzApplicationClassLoader (Local Files)
```java
public class JarzApplicationClassLoader extends JarzClassLoader {
    // Local file system support
    public JarzApplicationClassLoader(Path jarzFile, Path bundleIndexPath) throws IOException;
    
    // Creates child loaders for other local JARZ files
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException;
}
```

### CdnJarzClassLoader (HTTP/CDN Streaming)
```java
public class CdnJarzClassLoader extends JarzClassLoader {
    // CDN/HTTP streaming support
    public CdnJarzClassLoader(String jarzUrl, Path bundleIndexPath) throws IOException;
    
    // Creates child loaders for other CDN JARZ files
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException;
}
```

### S3JarzClassLoader (S3 Streaming)
```java
public class S3JarzClassLoader extends JarzClassLoader {
    // S3 streaming support
    public S3JarzClassLoader(S3Client s3Client, String bucket, String key, Path bundleIndexPath) throws IOException;
    
    // Creates child loaders for other S3 JARZ files
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException;
}
```

## Bundle Index Integration

### Bundle Index Format
The bundle index (`*.index.bundle`) contains:
- Magic header: `JBDX`
- Version: `1`
- JARZ file mappings: `className → jarzUrl`
- Block location data for each class

### Class Loading Flow
```
1. findClass(className) called
2. Check bundle index: bundleIndex.findJarzForClass(className)
3. If class in different JARZ:
   a. Create/get child ClassLoader for target JARZ
   b. Delegate to child.loadClass(className)
4. If class in current JARZ:
   a. Use existing single-JARZ loading logic
5. If class not found:
   a. Throw ClassNotFoundException
```

### Performance Characteristics
- **O(1) class lookup** across multiple JARZ files
- **Lazy child loader creation** - only when needed
- **Minimal memory overhead** - shared bundle index
- **No sequential JARZ scanning** - direct file targeting

## API Usage Examples

### Local Files with Bundle Index
```java
// Generate bundle index
java -jar jarz-tools.jar JarzBundleIndexGenerator \
    lib/bundle.index \
    app.jarz lib1.jarz lib2.jarz

// Use with bundle index
JarzApplicationClassLoader loader = new JarzApplicationClassLoader(
    Paths.get("lib/app.jarz"), 
    Paths.get("lib/bundle.index")
);

Class<?> mainClass = loader.loadClass("kafka.Kafka"); // O(1) lookup
```

### CDN Streaming with Bundle Index
```java
// Bundle index downloaded locally for fast lookup
CdnJarzClassLoader loader = new CdnJarzClassLoader(
    "https://cdn.example.com/app.jarz",
    Paths.get("bundle.index")
);

Class<?> mainClass = loader.loadClass("kafka.Kafka"); // Direct CDN access
```

### S3 Streaming with Bundle Index
```java
S3JarzClassLoader loader = new S3JarzClassLoader(
    s3Client, "my-bucket", "app.jarz",
    Paths.get("bundle.index")
);

Class<?> mainClass = loader.loadClass("kafka.Kafka"); // Direct S3 access
```

### Backward Compatibility (No Bundle Index)
```java
// Still works without bundle index - falls back to single JARZ loading
JarzApplicationClassLoader loader = new JarzApplicationClassLoader(
    Paths.get("app.jarz"), 
    null // No bundle index
);
```

## Implementation Benefits

### Code Quality
- **Single source of truth** for bundle index logic
- **Reduced maintenance burden** - changes in one place
- **Consistent behavior** across all deployment types
- **Easier testing** - test bundle logic once

### Performance
- **O(1) class lookup** instead of O(n) sequential search
- **Minimal JARZ file opening** - only what's needed
- **Efficient memory usage** - shared bundle index
- **Fast startup** - direct class location

### Developer Experience
- **Consistent API** across all ClassLoader types
- **Drop-in replacement** for existing code
- **Optional bundle index** - works with or without
- **Clear separation of concerns** - each ClassLoader handles its data source

## Migration Path

### Phase 1: Base Class Enhancement
1. Add bundle index support to `JarzClassLoader`
2. Add abstract methods for child loader creation
3. Implement enhanced `findClass()` with bundle index lookup

### Phase 2: Child Class Simplification
1. Remove duplicate bundle index code from `CdnJarzClassLoader`
2. Remove duplicate bundle index code from `S3JarzClassLoader`
3. Implement abstract methods in all child classes

### Phase 3: Testing and Validation
1. Unit tests for bundle index functionality
2. Integration tests for cross-JARZ class loading
3. Performance benchmarks vs sequential search
4. Backward compatibility validation

## Future Enhancements

### Automatic Bundle Index Generation
- Generate bundle index during JARZ conversion
- Auto-detect bundle index in classpath directories
- Integration with Universal Launcher

### Advanced Caching
- LRU cache for child ClassLoaders
- Preload frequently accessed JARZ files
- Memory pressure handling

### Monitoring and Diagnostics
- Class loading metrics and timing
- Bundle index hit/miss ratios
- Child ClassLoader creation tracking

---

**Author**: Plasticity.Cloud  
**Version**: 1.0  
**Date**: 2026-01-11
