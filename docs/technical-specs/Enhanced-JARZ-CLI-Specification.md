# Enhanced JARZ CLI with Full JAR Compatibility - Technical Specification

## Problem Statement
The current JARZ CLI tool has basic functionality but lacks full JAR command compatibility. We need to enhance it to support all standard JAR operations while maintaining JARZ v2 format exclusivity and eliminating conversion needs in tests.

## Requirements
- Full JAR command compatibility (all operations: create, extract, list, update, etc.)
- JARZ v2 format only (no JAR creation, no JARZ v1 support)
- Full JAR manifest preservation (Main-Class, Class-Path, multi-release, modules)
- Extended syntax: JAR compatibility + JARZ-specific operations
- Comprehensive testing: compatibility suite + round-trip validation
- Java 11+ compatibility following JDK development standards

## Background
Current implementation has basic create/extract/convert operations but lacks:
- Standard JAR command-line syntax compatibility (-c, -x, -t, -u flags)
- Complete manifest handling and preservation
- Multi-release JAR support
- Module descriptor support
- Comprehensive option parsing
- Professional error handling and validation

## Proposed Solution
Enhance JarzCli to be a drop-in replacement for the JAR tool while exclusively creating JARZ v2 archives. Implement full command-line compatibility with the standard JAR tool syntax while adding JARZ-specific conversion capabilities.

## Task Breakdown

### Task 1: Create JAR-compatible command-line argument parser
- Implement argument parser supporting all JAR tool flags (-c, -x, -t, -u, -f, -v, -m, -e, etc.)
- Add JARZ-specific extensions (convert command, compression options)
- Include comprehensive validation and error handling
- Test: Verify all JAR command patterns parse correctly
- Demo: Command-line help shows full JAR compatibility + JARZ extensions

### Task 2: Implement JAR-compatible create operation (-c/--create)
- Support all JAR create options: manifest files, main class, compression levels
- Handle multi-release JAR creation (--release VERSION)
- Support module descriptor inclusion for modular JARs
- Preserve all manifest attributes during JARZ v2 creation
- Test: Create JARZ archives with various manifest configurations
- Demo: `jarz -cf app.jarz -m manifest.mf -e Main classes/` creates valid JARZ v2

### Task 3: Implement JAR-compatible extract operation (-x/--extract)
- Support selective extraction with file patterns
- Preserve file permissions and timestamps
- Handle directory creation and path resolution
- Support verbose output (-v flag)
- Test: Extract various JARZ archives and verify content integrity
- Demo: `jarz -xvf app.jarz` extracts all files with verbose output

### Task 4: Implement JAR-compatible list operation (-t/--list)
- Display archive contents in JAR-compatible format
- Support verbose listing with sizes and timestamps
- Show manifest information and main class
- Display block structure information (JARZ-specific)
- Test: List various archive types and verify output format
- Demo: `jarz -tvf app.jarz` shows detailed archive contents

### Task 5: Implement JAR-compatible update operation (-u/--update)
- Support adding/updating files in existing JARZ archives
- Preserve existing manifest while allowing updates
- Handle dependency analysis for optimal block restructuring
- Support manifest updates (-m flag)
- Test: Update archives and verify integrity
- Demo: `jarz -uf app.jarz NewClass.class` adds class to existing archive

### Task 6: Add comprehensive manifest handling
- Parse and preserve all standard manifest attributes
- Support Main-Class, Class-Path, Multi-Release, module descriptors
- Handle manifest merging during updates
- Validate manifest syntax and required attributes
- Test: Round-trip various manifest types through JARZ conversion
- Demo: Complex manifests preserved perfectly in JARZ format

### Task 7: Implement JAR-to-JARZ conversion with JAR-compatible syntax
- Add conversion as a standard operation mode like create/extract/list/update
- Support short syntax: `jarz --convert input.jar output.jarz` 
- Integrate with existing flag system (-v for verbose, -f for file specification)
- Seamlessly integrate existing JarToJarzConverter with new argument parsing
- Support conversion-specific options (compression levels, block sizes) as additional flags
- Provide detailed conversion statistics with verbose output
- Test: Convert various JAR types using JAR-compatible command patterns
- Demo: `jarz --convert -vf output.jarz input.jar` converts with verbose output following JAR tool patterns

### Task 8: Create comprehensive test suite
- JAR compatibility tests: verify identical behavior to jar tool
- Round-trip tests: JAR → JARZ → extract → compare
- Manifest preservation tests for all attribute types
- Multi-release and module support validation
- Performance regression tests for large archives
- Test: All tests pass with zero failures
- Demo: Complete test suite validates JAR tool compatibility

## Implementation Status
- [x] Task 1: JAR-compatible command-line argument parser ✅ **COMPLETED**
- [x] Task 2: JAR-compatible create operation (-c/--create) ✅ **COMPLETED**  
- [x] Task 3: JAR-compatible extract operation (-x/--extract) ✅ **COMPLETED**
- [x] Task 4: JAR-compatible list operation (-t/--list) ✅ **COMPLETED**
- [ ] Task 5: JAR-compatible update operation (-u/--update) 🚧 **IN PROGRESS**
- [x] Task 6: Comprehensive manifest handling ✅ **COMPLETED**
- [x] Task 7: JAR-to-JARZ conversion with JAR-compatible syntax ✅ **COMPLETED**
- [ ] Task 8: Comprehensive test suite 🚧 **IN PROGRESS**

## Completed Features

### ✅ Full JAR Command-Line Compatibility
- **Combined flags**: `-cvf`, `-cfm`, `-cfe` work exactly like JAR tool
- **Long options**: `--create`, `--extract`, `--list`, `--convert`
- **All JAR options**: `-f`, `-v`, `-m`, `-e`, `-C`, `-M`, `-0`, `--release`, etc.
- **Help and version**: `--help`, `--version` with professional output

### ✅ JAR-Compatible Operations
- **Create** (`-cf`): Creates JARZ v2 archives with dependency analysis
- **Extract** (`-xf`): Extracts files with proper directory structure
- **List** (`-tf`): Lists contents in JAR-compatible format
- **Convert** (`--convert`): JAR to JARZ conversion with statistics

### ✅ Advanced Features
- **Manifest preservation**: Full support for Main-Class, Class-Path, Multi-Release
- **Verbose output**: JAR-compatible verbose messages with `-v` flag
- **Directory changes**: `-C` option for changing base directory
- **Dependency analysis**: Automatic class dependency analysis for optimal compression
- **Error handling**: Professional error messages with proper exit codes

### ✅ Demonstrated Functionality
```bash
# JAR-compatible create with main class
jarz -cvf app.jarz -e Main -C classes .

# JAR-compatible extract with verbose
jarz -xvf app.jarz  

# JAR-compatible list with verbose
jarz -tvf app.jarz

# JARZ-specific conversion
jarz --convert -v input.jar output.jarz
```

## Next Steps
- Complete update operation implementation
- Enhance test coverage for edge cases
- Add performance regression tests
- Validate round-trip compatibility

## Author
Plasticity.Cloud

## Updated
2026-01-04T20:00:00Z
