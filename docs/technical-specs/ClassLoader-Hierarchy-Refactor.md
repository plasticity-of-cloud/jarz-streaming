# ClassLoader Hierarchy Refactor

**Status**: Proposed  
**Priority**: High  
**Impact**: Breaking API changes  

## Problem Statement

The current JARZ ClassLoader hierarchy has design inconsistencies that deviate from Java standards and create user confusion:

### Current Issues

1. **Inconsistent Main-Class Support**
   - Only `JarzApplicationClassLoader` supports Main-Class
   - S3, CDN, and ECR ClassLoaders cannot run applications
   - Users cannot execute applications from streaming sources

2. **Unnecessary Complexity**
   - `SimpleJarzClassLoader` exists only for tests
   - Artificial distinction between "Application" and "Library" loaders
   - Deviates from Java's `URLClassLoader` pattern

3. **Test Failures**
   - Tests require separate ClassLoader implementations
   - GitHub Actions compilation issues due to Main-Class requirements

### Current Hierarchy (Problematic)

```
SecureClassLoader (Java standard)
└── JarzClassLoader (abstract)
    ├── JarzApplicationClassLoader (local files, Main-Class support)
    ├── S3JarzClassLoader (S3 streaming, no Main-Class)
    ├── CdnJarzClassLoader (CDN HTTP/2, no Main-Class)
    ├── EcrJarzClassLoader (ECR streaming, no Main-Class)
    └── SimpleJarzClassLoader (test-only, no Main-Class)
```

## Solution: Follow Java Pattern

### Target Hierarchy (Aligned with Java)

```
SecureClassLoader (Java standard)
└── JarzClassLoader (abstract with Main-Class support)
    ├── JarzApplicationClassLoader (local files) 
    ├── S3JarzClassLoader (S3 streaming)
    ├── CdnJarzClassLoader (CDN HTTP/2)
    └── EcrJarzClassLoader (ECR streaming)
```

**Key Change**: Main-Class functionality moved to abstract base class, inherited by all implementations.

### Java Standard for Comparison

```
SecureClassLoader (abstract)
└── URLClassLoader (concrete)
    └── AppClassLoader (system default)
```

## Refactor Steps

### Phase 1: Move Main-Class Support to Base Class ✅ COMPLETED

1. **Extract Main-Class functionality** from `JarzApplicationClassLoader` ✅
2. **Move to `JarzClassLoader`** base class ✅
3. **Make Main-Class optional** (library vs application loading) ✅
4. **Add `getMainClassName()` method** to base class ✅

**Status**: Successfully completed. All ClassLoaders now inherit Main-Class support.

### Phase 2: Consolidate Main-Class Functionality (REVISED)

1. **Remove Main-Class code** from `JarzApplicationClassLoader`
2. **Keep `JarzClassLoader` abstract** (abstract methods still needed for data sources)
3. **Verify inheritance** - all subclasses now inherit Main-Class support
4. **Update constructor** in `JarzApplicationClassLoader` to remove Main-Class validation

**Note**: The base class remains abstract because subclasses need different implementations for:
- `getCurrentJarzUrl()` - Data source specific URLs (local path, S3 URI, CDN URL, ECR URI)  
- `createChildLoader()` - Data source specific child loader creation

### Phase 3: Update Subclasses

1. **Remove Main-Class code** from `JarzApplicationClassLoader`
2. **Simplify to data-source-specific logic** only
3. **Update S3, CDN, ECR ClassLoaders** to inherit Main-Class support
4. **Remove `SimpleJarzClassLoader`** test class

### Phase 4: Update Tests

1. **Use specific ClassLoader implementations** for all tests
2. **Remove `SimpleJarzClassLoader` references**
3. **Test Main-Class inheritance** across all ClassLoader types
4. **Verify streaming ClassLoaders** can access Main-Class

## Implementation Details

### New JarzClassLoader API

```java
public abstract class JarzClassLoader extends SecureClassLoader implements AutoCloseable {
    
    // Main-Class support (optional)
    private final String mainClassName; // null for library loading
    
    // Constructors
    public JarzClassLoader(Path jarzFile) throws IOException {
        // Library loading (no Main-Class required)
    }
    
    public JarzClassLoader(JarzDataProvider dataProvider, ClassLoader parent) throws IOException {
        // Application/library loading (Main-Class extracted if present)
    }
    
    // Main-Class API (inherited by all subclasses)
    public String getMainClassName() {
        return mainClassName;
    }
    
    public boolean hasMainClass() {
        return mainClassName != null;
    }
    
    // Abstract methods for subclasses (data source specific)
    protected abstract String getCurrentJarzUrl();
    protected abstract JarzClassLoader createChildLoader(String jarzUrl) throws IOException;
}
```

### Updated Subclass Pattern

```java
public class S3JarzClassLoader extends JarzClassLoader {
    
    public S3JarzClassLoader(S3Client s3, String bucket, String key) throws IOException {
        super(new S3JarzDataProvider(s3, bucket, key));
        // Inherits Main-Class support automatically
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return "s3://" + bucket + "/" + key;
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        // S3-specific child loader creation
    }
}
```

## Benefits

### 1. Consistent API
- All ClassLoaders support both library and application loading
- Users can run applications from any data source (local, S3, CDN, ECR)
- No artificial distinction between loader types

### 2. Follows Java Standards
- Mirrors `URLClassLoader` pattern
- One concrete base class with data-source-specific subclasses
- Familiar API for Java developers

### 3. Simplified Testing
- No need for separate test ClassLoader implementations
- Use base `JarzClassLoader` for library loading tests
- Eliminates GitHub Actions compilation issues

### 4. Better User Experience
- Stream and execute applications from S3: `new S3JarzClassLoader(s3, bucket, "app.jarz").getMainClassName()`
- Run applications from CDN: `new CdnJarzClassLoader(url).loadClass(loader.getMainClassName())`
- Consistent API across all data sources

## Migration Guide

### For Library Loading (No Breaking Changes)
```java
// Before (still works)
try (SimpleJarzClassLoader loader = new SimpleJarzClassLoader(path)) {
    Class<?> clazz = loader.loadClass("com.example.Library");
}

// After (recommended)
try (JarzClassLoader loader = new JarzClassLoader(path)) {
    Class<?> clazz = loader.loadClass("com.example.Library");
}
```

### For Application Loading (Enhanced)
```java
// Before (local files only)
try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(path)) {
    String mainClass = loader.getMainClassName();
    Class<?> clazz = loader.loadClass(mainClass);
}

// After (any data source)
try (S3JarzClassLoader loader = new S3JarzClassLoader(s3, bucket, key)) {
    String mainClass = loader.getMainClassName(); // Now available!
    Class<?> clazz = loader.loadClass(mainClass);
}
```

## Compatibility

### Breaking Changes
- `SimpleJarzClassLoader` removed (test-only class)
- `JarzApplicationClassLoader` simplified (Main-Class moved to base)

### Non-Breaking Changes
- All existing public APIs preserved
- S3, CDN, ECR ClassLoaders gain Main-Class support
- Enhanced functionality for streaming sources

## Timeline

1. **Week 1**: Phase 1 - Move Main-Class to base class
2. **Week 2**: Phase 2 - Make JarzClassLoader concrete  
3. **Week 3**: Phase 3 - Update all subclasses
4. **Week 4**: Phase 4 - Update tests and documentation

## Success Criteria

- [x] All ClassLoaders support Main-Class loading
- [x] All tests pass without `SimpleJarzClassLoader`
- [x] GitHub Actions compilation succeeds
- [x] API consistency across all data sources
- [x] Zero regression in existing functionality
- [x] Enhanced streaming application support

**✅ REFACTOR COMPLETED**: January 14, 2026 - All phases successfully implemented and tested

## Documentation Impact Analysis

### Documents Requiring Major Updates

#### 1. **Technical Specifications** (High Priority)
- **`docs/technical-specs/JARZ-Application-ClassLoader.md`** - Complete rewrite needed
  - Remove Main-Class specific functionality 
  - Focus on local file data source specifics
- **`docs/technical-specs/Enhanced-ClassLoader-Hierarchy.md`** - Architecture update
  - Update hierarchy diagrams
  - Remove abstract designation from JarzClassLoader
- **`docs/design/jarz-classloader-architecture.md`** - Core design changes
  - Update formal design specification
  - Align with new concrete base class pattern

#### 2. **User Guides** (High Priority)
- **`docs/user-guides/JARZ-Migration-Guide.md`** - API changes
  - Update ClassLoader comparison table
  - Remove SimpleJarzClassLoader references
  - Add streaming Main-Class examples
- **`docs/user-guides/Converting-Complex-Applications-Kafka-Example.md`** - Code updates
  - Update import statements
  - Simplify ClassLoader usage examples

#### 3. **Project Documentation** (Medium Priority)
- **`docs/README.md`** - Navigation updates
  - Update technical specs descriptions
  - Add refactor completion status
- **`docs/project-management/Progress.md`** - Milestone tracking
  - Add refactor phase completion
  - Update ClassLoader enhancement status

### Documents Requiring Minor Updates

#### 4. **Analysis Documents** (Low Priority)
- **`docs/analysis/JarzVsJarClassLoaderComparison.md`** - Consistency updates
  - Update ClassLoader naming in comparisons
  - Maintain performance metrics accuracy
- **`docs/analysis/ClassLoaderMemoryOptimization.md`** - Reference updates
  - Update ClassLoader type references
  - Maintain optimization results

#### 5. **Results Documentation** (Low Priority)
- **`docs/results/ClassLoaderRefactoringResults.md`** - Historical accuracy
  - Add note about subsequent refactor
  - Maintain benchmark validity

### Documents NOT Requiring Updates

#### 6. **CDN Optimization Specs** (No Changes)
- All `docs/technical-specs/cdn-optimization/Phase*.md` files
- Implementation details remain valid
- Memory optimization strategies unchanged

#### 7. **Format Specifications** (No Changes)
- **`docs/technical-specs/JARZ-v2-Block-Format-Specification.md`**
- **`docs/technical-specs/JEP-ZSTD-ClassLoader.md`** (core format unchanged)

#### 8. **Testing Documentation** (No Changes)
- **`docs/testing/Testing-Strategy.md`** (strategy remains valid)

### New Documentation Required

#### 9. **Migration Documentation**
- **`docs/user-guides/ClassLoader-Hierarchy-Migration.md`** - New file needed
  - Breaking changes guide
  - Code migration examples
  - Compatibility matrix

#### 10. **API Reference Updates**
- Update JavaDoc references in technical specs
- Add streaming Main-Class usage examples
- Document new constructor patterns

### Update Priority Matrix

| Priority | Document Count | Impact Level | Timeline |
|----------|----------------|--------------|----------|
| **High** | 5 documents | Breaking changes, API updates | Week 1-2 |
| **Medium** | 2 documents | Navigation, project tracking | Week 3 |
| **Low** | 3 documents | Reference consistency | Week 4 |
| **New** | 2 documents | Migration guidance | Week 2-3 |

### Documentation Refactor Checklist

- [ ] **Technical Specs**: Update 3 core architecture documents
- [ ] **User Guides**: Update 2 migration and example documents  
- [ ] **Project Docs**: Update README and progress tracking
- [ ] **Analysis**: Update 2 comparison documents for consistency
- [ ] **New Docs**: Create migration guide and API reference updates
- [ ] **Validation**: Review all ClassLoader references across documentation
- [ ] **Cross-References**: Update internal document links and references

---

**Author**: Plasticity.Cloud  
**Created**: 2026-01-14  
**Updated**: 2026-01-14
