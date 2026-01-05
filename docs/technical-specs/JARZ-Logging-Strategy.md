# JARZ Logging Strategy

## Overview

This document defines the logging strategy for JARZ components, replacing ad-hoc `System.out.println()` and `System.err.println()` calls with proper JDK-standard logging.

## Logging Framework

**Use `System.Logger` (JEP 264)** - The standard JDK logging API introduced in Java 9.

### Implementation

- **JarzLogger**: Internal wrapper around `System.Logger` for consistent usage
- **Location**: `jdk.incubator.jarz.internal.JarzLogger`
- **Benefits**: 
  - No external dependencies
  - Automatic framework detection (Log4j, SLF4J, etc.)
  - Falls back to `java.util.logging` when no framework present
  - JDK-standard approach for platform code

## Logging Categories

### 1. CLI Tools (jarz-tools)

**Mixed Approach**: CLI tools need both logging and direct console output.

```java
// Direct console output (user-facing)
System.out.println("creating: " + outputPath);           // ✅ Keep - user feedback
System.out.println("  adding: " + entryName);            // ✅ Keep - verbose mode

// Error logging (diagnostic)
logger.error("Failed to read archive: {0}", e.getMessage());  // ✅ Use logger
logger.warning("Invalid manifest entry: {0}", entryName);     // ✅ Use logger
```

**Rule**: 
- **Keep `System.out`** for user-facing operational feedback
- **Replace `System.err`** with `logger.error()` for diagnostic errors
- **Add `logger.debug()`** for internal diagnostic information

### 2. Core Libraries (jarz-core, jarz-classloader)

**Full Logging**: Library code should never write directly to console.

```java
// Replace ALL System.out/System.err with logging
logger.info("Loading JARZ archive: {0}", archivePath);
logger.debug("Decompressing block {0}: {1} bytes", blockIndex, size);
logger.error("Corrupted JARZ header at offset {0}", offset, exception);
```

### 3. Streaming Components (jarz-s3, jarz-cdn)

**Full Logging**: Network components need structured logging for monitoring.

```java
logger.info("S3 range request: {0}-{1} bytes from {2}", start, end, key);
logger.debug("CDN cache hit for block {0}", blockIndex);
logger.warning("Retry attempt {0} for failed request", retryCount);
```

### 4. Test Code

**Mixed Approach**: Tests can use both logging and console output.

```java
// Test progress (keep System.out for visibility)
System.out.println("Testing compression with " + classCount + " classes");

// Test diagnostics (use logging)
logger.debug("Generated test class: {0}", className);
logger.error("Test setup failed", exception);
```

## Log Levels

| Level | Usage | Examples |
|-------|-------|----------|
| **ERROR** | Critical failures | Archive corruption, I/O failures, security violations |
| **WARNING** | Non-fatal issues | Invalid manifest entries, deprecated features |
| **INFO** | Operational events | Archive creation, class loading, network requests |
| **DEBUG** | Detailed diagnostics | Block decompression, cache operations, dependency analysis |
| **TRACE** | Very detailed flow | Method entry/exit, byte-level operations |

## Implementation Plan

### Phase 1: Core Infrastructure ✅
- [x] Create `JarzLogger` utility class
- [x] Add package documentation
- [x] Update JarzCli main error handling

### Phase 2: Library Components
- [ ] Update `jarz-core` classes (JarzV2Reader, JarzV2Writer, etc.)
- [ ] Update `jarz-classloader` classes
- [ ] Update `jarz-s3` and `jarz-cdn` classes

### Phase 3: CLI Tools
- [ ] Categorize JarzCli output (keep user feedback, log diagnostics)
- [ ] Add debug logging for internal operations
- [ ] Ensure error messages are actionable

### Phase 4: Test Code
- [ ] Update test classes to use logging for diagnostics
- [ ] Keep System.out for test progress visibility

## Configuration

### Default Behavior
- Uses `java.util.logging` when no external framework present
- Default level: `INFO` for production, `DEBUG` for development
- Console output for CLI tools, structured logging for libraries

### External Framework Integration
```bash
# With Log4j2
java -Djava.system.class.loader=org.apache.logging.log4j.jul.LogManager ...

# With SLF4J
java -Djava.util.logging.manager=org.slf4j.bridge.SLF4JBridgeHandler ...
```

### JVM Unified Logging
```bash
# Enable JARZ-specific logging
java -Xlog:jdk.incubator.jarz:jarz.log:time,level,tags

# Debug level for development
java -Xlog:jdk.incubator.jarz:stdout:time,level,tags -Djava.util.logging.level=DEBUG
```

## Benefits

1. **JDK Standards Compliance**: Uses official JDK logging API
2. **Framework Flexibility**: Works with any logging framework
3. **Performance**: Lazy evaluation with `isLoggable()` checks
4. **Maintainability**: Centralized logging configuration
5. **Monitoring**: Structured logs for production monitoring
6. **Debugging**: Detailed diagnostic information when needed

## Migration Examples

### Before (System.out/err)
```java
System.out.println("Processing " + entryCount + " entries");
System.err.println("Failed to read entry: " + e.getMessage());
```

### After (JarzLogger)
```java
private static final JarzLogger logger = JarzLogger.getLogger(MyClass.class);

logger.info("Processing {0} entries", entryCount);
logger.error("Failed to read entry: {0}", e.getMessage(), e);
```

### CLI Tools (Mixed)
```java
// User feedback - keep System.out
System.out.println("creating: " + outputPath);

// Diagnostics - use logger
logger.debug("Archive size: {0} bytes", archiveSize);
logger.error("Invalid archive format", exception);
```

---

**Author**: Plasticity.Cloud  
**Updated**: 2026-01-04T22:44:00Z
