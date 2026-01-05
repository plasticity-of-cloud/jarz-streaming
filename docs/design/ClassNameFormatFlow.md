# JARZ Class Name Format Flow

This document illustrates how class names are stored, indexed, and retrieved in JARZ archives, using `SampleClass` as an example.

## Overview: The Class Name Transformation Journey

```
JVM Request → JARZ Index Lookup → Block Read → Class Loading
```

## 1. Storage in JARZ Archive

### Physical Storage Structure
```
┌─────────────────────────────────────────────────────────────┐
│                    JARZ Archive File                        │
├─────────────────────────────────────────────────────────────┤
│ Header │ Dictionary │ Block 0 │ Block 1 │ ... │ Index │ Footer │
└─────────────────────────────────────────────────────────────┘
                                 ▲                    ▲
                          Compressed Blocks      Class Index
```

### Block Content (Compressed)
```
Block 1 (ZSTD Compressed):
┌──────────────────────────────────────────────────┐
│  Raw .class file bytes for:                     │
│  • com/example/SampleClass.class                │
│  • com/example/util/Helper.class                │
│  • com/example/data/Model.class                 │
│  [ZSTD compressed bytecode data...]             │
└──────────────────────────────────────────────────┘
```

## 2. Index File Format

### Class Index Entry
```
JARZ Index (JSON format):
{
  "classes": {
    "com/example/SampleClass.class": {    ← Internal format + .class extension
      "blockId": 1,
      "offset": 0,
      "size": 1024,
      "checksum": "abc123..."
    },
    "com/example/util/Helper.class": {
      "blockId": 1,
      "offset": 1024,
      "size": 512,
      "checksum": "def456..."
    }
  }
}
```

## 3. Class Loading Flow

### Step-by-Step Transformation

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Class Loading Flow                               │
└─────────────────────────────────────────────────────────────────────────────┘

1. JVM Request (Binary Name)
   ┌─────────────────────────────┐
   │ "com.example.SampleClass"   │  ← ClassLoader.loadClass(name)
   └─────────────────────────────┘
                 │
                 ▼
2. ClassLoader Processing
   ┌─────────────────────────────┐
   │ JarzClassLoader.findClass() │
   │ receives: name              │
   │ = "com.example.SampleClass" │
   └─────────────────────────────┘
                 │
                 ▼
3. Index Lookup (Current - BROKEN)
   ┌─────────────────────────────┐
   │ BlockReader.readClass()     │
   │ tries multiple formats:     │
   │ • "com.example.SampleClass" │ ← NOT in index
   │ • "com/example/SampleClass.class" │ ← FOUND!
   └─────────────────────────────┘
                 │
                 ▼
4. Block Read
   ┌─────────────────────────────┐
   │ Read Block 1                │
   │ Decompress ZSTD data        │
   │ Extract bytes at offset 0   │
   │ Return .class file bytes    │
   └─────────────────────────────┘
                 │
                 ▼
5. Class Definition
   ┌─────────────────────────────┐
   │ defineClass(                │
   │   "com.example.SampleClass",│ ← Binary name for JVM
   │   classBytes,               │
   │   protectionDomain          │
   │ )                           │
   └─────────────────────────────┘
```

## 4. The Problem: Format Mismatch

### Current Issue
```
JVM Request:     "com.example.SampleClass"     (Binary name - dots)
Index Storage:   "com/example/SampleClass.class" (Internal name + .class)
                          ▲
                    MISMATCH!
```

### Multiple Lookup Attempts (Inefficient)
```
BlockReader.readClass("com.example.SampleClass"):
  1. Try: "com.example.SampleClass"           → NOT FOUND
  2. Try: "com/example/SampleClass.class"     → FOUND! ✓
  3. (Unnecessary extra attempts...)
```

## 5. The Solution: Centralized Normalization

### Proposed Flow
```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Optimized Class Loading Flow                        │
└─────────────────────────────────────────────────────────────────────────────┘

1. JVM Request
   ┌─────────────────────────────┐
   │ "com.example.SampleClass"   │
   └─────────────────────────────┘
                 │
                 ▼
2. ClassLoader Normalization
   ┌─────────────────────────────┐
   │ JarzClassLoader.findClass() │
   │                             │
   │ indexKey = toIndexFormat(   │
   │   "com.example.SampleClass" │
   │ )                           │
   │ = "com/example/SampleClass.class" │
   └─────────────────────────────┘
                 │
                 ▼
3. Direct Index Lookup
   ┌─────────────────────────────┐
   │ BlockReader.readEntry(      │
   │   "com/example/SampleClass.class" │
   │ )                           │
   │ → FOUND immediately! ✓      │
   └─────────────────────────────┘
                 │
                 ▼
4. Block Read & Class Definition
   ┌─────────────────────────────┐
   │ Same as before...           │
   └─────────────────────────────┘
```

### Normalization Methods
```java
// Convert JVM binary name to JARZ index key
protected static String toIndexFormat(String binaryName) {
    return binaryName.replace('.', '/') + ".class";
}

// Convert JARZ index key to JVM binary name  
protected static String normalizeClassName(String indexKey) {
    if (indexKey.endsWith(".class")) {
        indexKey = indexKey.substring(0, indexKey.length() - 6);
    }
    return indexKey.replace('/', '.');
}
```

## 6. Format Summary

| Context | Format | Example |
|---------|--------|---------|
| **JVM Request** | Binary name (dots) | `"com.example.SampleClass"` |
| **JARZ Index** | Internal + .class | `"com/example/SampleClass.class"` |
| **Block Storage** | Raw .class bytes | `[CAFEBABE...]` |
| **Class Definition** | Binary name (dots) | `"com.example.SampleClass"` |

## 7. Benefits of Centralized Approach

### Performance
- **Single lookup** instead of multiple attempts
- **Direct index access** using correct format
- **Eliminates guesswork** in BlockReader

### Maintainability  
- **Centralized conversion** in base ClassLoader
- **Consistent behavior** across all JARZ ClassLoaders
- **Clear separation** of concerns

### Correctness
- **Matches actual index format** used in JARZ files
- **Eliminates format mismatches** between components
- **Follows JDK ClassLoader patterns**
