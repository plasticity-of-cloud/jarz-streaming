# ClassLoader Hierarchy Migration Guide

**Migration Guide for JARZ ClassLoader Hierarchy Refactor**  
**Version**: 1.0  
**Date**: January 14, 2026  
**Author**: Plasticity.Cloud

## Overview

The JARZ ClassLoader hierarchy has been refactored to provide unified Main-Class support across all ClassLoader implementations. This guide covers breaking changes, migration steps, and compatibility information.

## What Changed

### Before Refactor
```
JarzClassLoader (abstract)
├── JarzApplicationClassLoader (Main-Class support)
├── S3JarzClassLoader (library loading only)
├── CdnJarzClassLoader (library loading only)
└── EcrJarzClassLoader (library loading only)

SimpleJarzClassLoader (test-only class)
```

### After Refactor
```
JarzClassLoader (concrete, Main-Class support)
├── JarzApplicationClassLoader (local files + Main-Class validation)
├── S3JarzClassLoader (S3 streaming + inherited Main-Class)
├── CdnJarzClassLoader (CDN streaming + inherited Main-Class)
└── EcrJarzClassLoader (ECR streaming + inherited Main-Class)

SimpleJarzClassLoader (removed)
```

## Breaking Changes

### 1. SimpleJarzClassLoader Removed

**Impact**: Test-only class removed  
**Affected**: Internal tests only  
**Migration**: Use `JarzApplicationClassLoader` instead

```java
// Before (test code)
try (SimpleJarzClassLoader loader = new SimpleJarzClassLoader(jarzFile)) {
    Class<?> clazz = loader.loadClass("com.example.MyClass");
}

// After (test code)
try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
    Class<?> clazz = loader.loadClass("com.example.MyClass");
}
```

**Note**: Ensure test JARZ files have Main-Class in manifest for `JarzApplicationClassLoader` compatibility.

### 2. JarzApplicationClassLoader Simplified

**Impact**: Internal implementation simplified  
**Affected**: No public API changes  
**Migration**: No code changes required

The `JarzApplicationClassLoader` now inherits Main-Class functionality from the base class but maintains the same public API.

## New Capabilities

### 1. Streaming ClassLoaders Can Run Applications

All streaming ClassLoaders now support Main-Class functionality:

```java
// S3 ClassLoader can now run applications
try (S3JarzClassLoader loader = new S3JarzClassLoader(s3Client, bucket, "app.jarz")) {
    if (loader.hasMainClass()) {
        String mainClassName = loader.getMainClassName();
        Class<?> mainClass = loader.loadClass(mainClassName);
        // Execute main method
    }
}

// CDN ClassLoader can now run applications  
try (CdnJarzClassLoader loader = new CdnJarzClassLoader("https://cdn.example.com/app.jarz")) {
    if (loader.hasMainClass()) {
        String mainClassName = loader.getMainClassName();
        Class<?> mainClass = loader.loadClass(mainClassName);
        // Execute main method
    }
}

// ECR ClassLoader can now run applications
try (EcrJarzClassLoader loader = new EcrJarzClassLoader("org.example", "myapp", "1.0.0")) {
    if (loader.hasMainClass()) {
        String mainClassName = loader.getMainClassName();
        Class<?> mainClass = loader.loadClass(mainClassName);
        // Execute main method
    }
}
```

### 2. Unified Main-Class API

All ClassLoaders now inherit the same Main-Class methods:

```java
// Available on ALL ClassLoader implementations
public boolean hasMainClass()           // Check if Main-Class exists
public String getMainClassName()        // Get Main-Class name (or null)
```

## Compatibility Matrix

| ClassLoader Type | Before Refactor | After Refactor | Breaking Changes |
|------------------|-----------------|----------------|------------------|
| `JarzApplicationClassLoader` | ✅ Main-Class support | ✅ Main-Class support | ❌ None |
| `S3JarzClassLoader` | ❌ Library only | ✅ Main-Class support | ❌ None (enhancement) |
| `CdnJarzClassLoader` | ❌ Library only | ✅ Main-Class support | ❌ None (enhancement) |
| `EcrJarzClassLoader` | ❌ Library only | ✅ Main-Class support | ❌ None (enhancement) |
| `SimpleJarzClassLoader` | ✅ Test class | ❌ Removed | ✅ **Breaking** |

## Migration Steps

### For Application Code

**No migration required** - All public APIs remain unchanged.

### For Test Code

1. **Replace SimpleJarzClassLoader usage**:
   ```java
   // Replace this
   new SimpleJarzClassLoader(jarzFile)
   
   // With this  
   new JarzApplicationClassLoader(jarzFile)
   ```

2. **Ensure test JARZ files have Main-Class**:
   ```
   Manifest-Version: 1.0
   Main-Class: com.example.TestClass
   ```

3. **Update test expectations**:
   ```java
   // Update toString assertions
   assertTrue(loader.toString().contains("JarzApplicationClassLoader"));
   ```

### For Streaming Applications

**Enhance existing code** to use new Main-Class capabilities:

```java
// Before: Only library loading
try (S3JarzClassLoader loader = new S3JarzClassLoader(s3, bucket, key)) {
    Class<?> clazz = loader.loadClass("com.example.LibraryClass");
}

// After: Can also run applications
try (S3JarzClassLoader loader = new S3JarzClassLoader(s3, bucket, key)) {
    // Library loading (unchanged)
    Class<?> clazz = loader.loadClass("com.example.LibraryClass");
    
    // NEW: Application execution
    if (loader.hasMainClass()) {
        String mainClass = loader.getMainClassName();
        Class<?> appClass = loader.loadClass(mainClass);
        // Execute main method
    }
}
```

## Validation Steps

### 1. Compile Verification
```bash
mvn clean compile
```

### 2. Test Verification  
```bash
mvn test
```

### 3. Integration Testing
```bash
# Test Main-Class inheritance
mvn test -Dtest=MainClassInheritanceTest

# Test application ClassLoader
mvn test -Dtest=JarzApplicationClassLoaderTest

# Test streaming ClassLoaders
mvn test -Dtest=S3JarzUnitTest,BasicCdnTest
```

## Troubleshooting

### Issue: SimpleJarzClassLoader not found

**Error**: `cannot find symbol: class SimpleJarzClassLoader`

**Solution**: Replace with `JarzApplicationClassLoader` and ensure JARZ has Main-Class:
```java
// Add to manifest
Manifest-Version: 1.0
Main-Class: com.example.YourMainClass
```

### Issue: JarzApplicationClassLoader requires Main-Class

**Error**: `No Main-Class attribute in manifest`

**Solution**: Add Main-Class to your JARZ manifest or use a different ClassLoader for library-only loading.

### Issue: Streaming ClassLoader missing Main-Class methods

**Error**: `cannot find symbol: method hasMainClass()`

**Solution**: Ensure you're using the latest version after the refactor. All ClassLoaders inherit these methods from the base class.

## Benefits

### 1. Unified Architecture
- Consistent API across all ClassLoader types
- Single source of Main-Class functionality
- Reduced code duplication

### 2. Enhanced Streaming Capabilities
- S3 ClassLoaders can run applications directly from S3
- CDN ClassLoaders can run applications from CDN
- ECR ClassLoaders can run applications from container registry

### 3. Simplified Testing
- No more test-only ClassLoader classes
- Consistent test patterns across all implementations
- Better test coverage for Main-Class functionality

## Support

For questions or issues related to this migration:
- **Email**: ecosystem@plasticity.cloud
- **Documentation**: [ClassLoader-Hierarchy-Refactor.md](../technical-specs/ClassLoader-Hierarchy-Refactor.md)
- **Test Examples**: See `MainClassInheritanceTest.java` for comprehensive examples

---

**Migration Guide Version**: 1.0  
**Last Updated**: January 14, 2026  
**Next Review**: March 2026
