# JMODZ Tool Specification

**Date**: 2025-12-17  
**Status**: Design Phase  
**Replaces**: jlink plugin approach

## Overview

`jmodz` is a ZSTD-compressed equivalent of the `jmod` tool, providing full feature parity with enhanced compression capabilities.

## Design Rationale

### Why jmodz vs jlink plugin?

1. **Feature Completeness**: jmod has 5 commands, we need equivalent functionality
2. **Naming Convention**: Follows JDK pattern (jmod → jmodz)
3. **Integration Path**: Easier JDK integration than custom jlink plugins
4. **Toolchain Compatibility**: Drop-in replacement for existing workflows

### File Format

- **Extension**: `.jmodz` (ZSTD-compressed module archive)
- **Compatibility**: Can coexist with `.jmod` files
- **Compression**: 24-28% size reduction over ZIP-based jmod

## Command Interface

### jmodz create
```bash
jmodz create [options] jmodz-file

Options:
  --class-path <path>       Application jar files|dir containing classes
  --cmds <path>             Location of native commands
  --config <path>           Location of user-editable config files  
  --dry-run                 Dry run of jmodz file creation
  --exclude <pattern-list>  Exclude files matching the supplied comma separated pattern list
  --hash-modules <regex-pattern> Compute and record hashes
  --header-files <path>     Location of header files
  --help                    Print this help message
  --legal-notices <path>    Location of legal notices
  --libs <path>             Location of native libraries
  --main-class <class-name> Main class
  --man-pages <path>        Location of man pages
  --module-version <module-version> Module version
  --target-platform <target-platform> Target platform
  --warn-if-resolved <reason> Hint for a tool to issue a warning
  --compression-level <1-22> ZSTD compression level (default: 3)
```

### jmodz extract
```bash
jmodz extract [options] jmodz-file

Options:
  --dir <dir>               Target directory for extraction
  --help                    Print this help message
```

### jmodz list
```bash
jmodz list [options] jmodz-file

Options:
  --help                    Print this help message
  --compression-stats       Show compression statistics
```

### jmodz describe
```bash
jmodz describe [options] jmodz-file

Options:
  --help                    Print this help message
  --compression-info        Include compression details
```

### jmodz hash
```bash
jmodz hash [options] jmodz-file

Options:
  --module-path <module-path> Module path
  --hash-modules <regex-pattern> Compute and record hashes
  --help                    Print this help message
```

### jmodz convert
```bash
# Convert jmod to jmodz
jmodz convert --to-jmodz [--compression-level <level>] input.jmod output.jmodz

# Convert jmodz to jmod  
jmodz convert --to-jmod input.jmodz output.jmod
```

## Implementation Architecture

```
jmodz-tool/
├── src/main/java/jdk/tools/jmodz/
│   ├── JmodzTool.java           # Main entry point
│   ├── CreateCommand.java       # jmodz create
│   ├── ExtractCommand.java      # jmodz extract  
│   ├── ListCommand.java         # jmodz list
│   ├── DescribeCommand.java     # jmodz describe
│   ├── HashCommand.java         # jmodz hash
│   ├── ConvertCommand.java      # jmodz convert
│   └── JmodzFormat.java         # ZSTD compression logic
└── resources/
    └── META-INF/services/
        └── java.util.spi.ToolProvider
```

## Integration Strategy

### Phase 1: Standalone Tool
- Build jmodz as independent executable
- Full jmod command compatibility
- ZSTD compression integration

### Phase 2: JDK Integration  
- Add jmodz to JDK tools
- Module path support for .jmodz files
- jlink integration for .jmodz inputs

### Phase 3: Ecosystem Support
- Maven/Gradle plugin support
- IDE integration
- Build system compatibility

## File Format Specification

### .jmodz Structure
```
┌─────────────────────────────────────────────────────────────────┐
│                        .jmodz Format                             │
├─────────────────────────────────────────────────────────────────┤
│ Header (32B) → Module Info → ZSTD Frames → Index → Footer (8B)  │
│                                                                  │
│ Compatibility:                                                   │
│ - Same module-info.class as jmod                                │
│ - Same directory structure when extracted                        │
│ - ZSTD compression instead of ZIP                               │
└─────────────────────────────────────────────────────────────────┘
```

### Compression Benefits
- **Size**: 24-28% smaller than .jmod files
- **Speed**: 3.5x faster decompression than ZIP
- **Compatibility**: Same module metadata and structure

## Migration Path

### For Existing Projects
```bash
# Current workflow
jmod create --class-path classes/ mymodule.jmod

# New workflow  
jmodz create --class-path classes/ mymodule.jmodz

# Conversion
jmodz convert --to-jmodz mymodule.jmod mymodule.jmodz
```

### For JDK Builds
```bash
# Module path with mixed formats
java --module-path lib/mymodule.jmodz:lib/other.jmod --module mymodule/Main

# jlink with jmodz inputs
jlink --module-path $JAVA_HOME/jmods:lib/mymodule.jmodz --add-modules mymodule --output custom-jre
```

## Success Criteria

1. **Feature Parity**: All jmod commands implemented
2. **Performance**: 24-28% size reduction, 3.5x faster decompression  
3. **Compatibility**: Drop-in replacement for jmod in build scripts
4. **Integration**: Works with existing Java toolchain
5. **Adoption**: Can be used in real JDK module builds

## Next Steps

1. Rename jarz-jlink-plugin → jmodz-tool
2. Implement all 6 command interfaces
3. Add ZSTD compression to existing JARZ format
4. Test with real JDK modules (java.base, java.logging)
5. Validate toolchain integration

---

**References**:
- [jmod Tool Documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jmod.html)
- [JARZ Format Specification](./JEP-ZSTD-ClassLoader.md)
- [Java Tools Integration](./Java-Tools-Integration.md)
