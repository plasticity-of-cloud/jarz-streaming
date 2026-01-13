# JARZ CLI User Guide

**Complete guide to using the JARZ command-line interface for creating, managing, and analyzing ZSTD-compressed archives.**

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Command Reference](#command-reference)
- [Examples](#examples)
- [Advanced Usage](#advanced-usage)
- [Troubleshooting](#troubleshooting)

## Installation

### Prerequisites
- Java 21+ (LTS recommended)
- Maven 3.8+ (for building from source)

### Building the CLI
```bash
git clone <repository-url>
cd jdk-enhancements
mvn clean install
```

The CLI JAR will be available at: `jarz-tools/target/jarz-cli.jar`

### Verify Installation
```bash
java -jar jarz-tools/target/jarz-cli.jar --version
```

## Quick Start

### Create a JARZ Archive
```bash
# From a directory of class files
java -jar jarz-cli.jar -cf myapp.jarz -C classes/ .

# From an existing JAR file
java -jar jarz-cli.jar --convert myapp.jar myapp.jarz
```

### Extract a JARZ Archive
```bash
java -jar jarz-cli.jar -xf myapp.jarz
```

### List Archive Contents
```bash
java -jar jarz-cli.jar -tf myapp.jarz
```

### Analyze Archive Structure
```bash
java -jar jarz-cli.jar --tree -f myapp.jarz
```

## Command Reference

### Basic Operations

#### Create Archive (`-c`)
```bash
java -jar jarz-cli.jar -cf <archive.jarz> [options] <files...>
```

**Options:**
- `-C <dir>`: Change to directory before adding files
- `-m <manifest>`: Include manifest file
- `-M`: Do not create manifest file
- `-v`: Verbose output

**Examples:**
```bash
# Create from directory
java -jar jarz-cli.jar -cf app.jarz -C build/classes/ .

# Create with manifest
java -jar jarz-cli.jar -cfm app.jarz META-INF/MANIFEST.MF -C classes/ .

# Create with verbose output
java -jar jarz-cli.jar -cfv app.jarz -C classes/ .
```

#### Extract Archive (`-x`)
```bash
java -jar jarz-cli.jar -xf <archive.jarz> [files...]
```

**Options:**
- `-v`: Verbose output
- `-C <dir>`: Extract to specific directory

**Examples:**
```bash
# Extract all files
java -jar jarz-cli.jar -xf app.jarz

# Extract specific files
java -jar jarz-cli.jar -xf app.jarz com/example/MyClass.class

# Extract with verbose output
java -jar jarz-cli.jar -xfv app.jarz
```

#### List Contents (`-t`)
```bash
java -jar jarz-cli.jar -tf <archive.jarz>
```

**Options:**
- `-v`: Verbose output (shows sizes and dates)

**Examples:**
```bash
# List all files
java -jar jarz-cli.jar -tf app.jarz

# List with details
java -jar jarz-cli.jar -tfv app.jarz
```

#### Update Archive (`-u`)
```bash
java -jar jarz-cli.jar -uf <archive.jarz> [options] <files...>
```

**Examples:**
```bash
# Update with new files
java -jar jarz-cli.jar -uf app.jarz -C classes/ com/example/NewClass.class
```

### Advanced Operations

#### Convert JAR to JARZ (`--convert`)
```bash
java -jar jarz-cli.jar --convert <input.jar> <output.jarz>
```

**Example:**
```bash
java -jar jarz-cli.jar --convert spring-boot-app.jar spring-boot-app.jarz
```

#### Analyze Block Structure (`--tree`)
```bash
java -jar jarz-cli.jar --tree -f <archive.jarz> [--verbose]
```

**Options:**
- `--verbose`: Show detailed class listings per block

**Examples:**
```bash
# Basic block structure
java -jar jarz-cli.jar --tree -f app.jarz

# Detailed structure with class listings
java -jar jarz-cli.jar --tree -f app.jarz --verbose
```

### Global Options

- `--help`: Show help message
- `--version`: Show version information
- `-v, --verbose`: Enable verbose output

## Examples

### Example 1: Spring Boot Application

```bash
# Convert existing Spring Boot JAR
java -jar jarz-cli.jar --convert myapp-1.0.jar myapp-1.0.jarz

# Analyze compression efficiency
java -jar jarz-cli.jar --tree -f myapp-1.0.jarz

# Extract for inspection
java -jar jarz-cli.jar -xf myapp-1.0.jarz
```

### Example 2: Multi-Module Project

```bash
# Create JARZ from compiled classes
java -jar jarz-cli.jar -cfm myproject.jarz META-INF/MANIFEST.MF \
  -C module1/target/classes/ . \
  -C module2/target/classes/ . \
  -C module3/target/classes/ .

# Verify contents
java -jar jarz-cli.jar -tf myproject.jarz | head -20
```

### Example 3: Library Distribution

```bash
# Create library JARZ with manifest
java -jar jarz-cli.jar -cfm mylib-2.1.jarz manifest.txt -C build/classes/ .

# Analyze block organization
java -jar jarz-cli.jar --tree -f mylib-2.1.jarz --verbose

# Compare with original JAR
ls -lh mylib-2.1.jar mylib-2.1.jarz
```

## Advanced Usage

### Compression Analysis

The `--tree` command provides detailed compression analysis:

```bash
java -jar jarz-cli.jar --tree -f app.jarz
```

**Output Interpretation:**
```
📦 Block 0:
   Size: 517,357 bytes (compressed: 145,866 bytes, 71.8% ratio)
   Classes: 156
```

- **Size**: Uncompressed size of block content
- **Compressed**: Actual size in JARZ file
- **Ratio**: Compression percentage (higher = better compression)
- **Classes**: Number of class files in this block

### Block Organization Strategy

JARZ automatically organizes classes into blocks based on:

1. **Dependency relationships**: Related classes grouped together
2. **Package structure**: Classes from same packages
3. **Size optimization**: Balancing compression vs. access patterns

### Performance Considerations

**Optimal Block Sizes:**
- Small blocks: Better for streaming individual classes
- Large blocks: Better compression ratios
- JARZ automatically balances these trade-offs

**Compression Levels:**
- JARZ uses optimized ZSTD compression
- Typically achieves 25-30% better compression than ZIP/DEFLATE
- 3-5x faster decompression than traditional formats

## Troubleshooting

### Common Issues

#### "Invalid JARZ format" Error
```bash
Error: Invalid JARZ v2 magic
```
**Solution**: Ensure file is a valid JARZ archive, not a regular JAR file.

#### "Class not found" During Extraction
```bash
Error: Entry not found: com/example/MyClass.class
```
**Solution**: Use `-tf` to list actual contents and verify class name.

#### Memory Issues with Large Archives
```bash
OutOfMemoryError during creation
```
**Solution**: Increase JVM heap size:
```bash
java -Xmx4g -jar jarz-cli.jar -cf large-app.jarz -C classes/ .
```

### Debugging Options

#### Verbose Output
```bash
java -jar jarz-cli.jar -cfv app.jarz -C classes/ .
```

#### Detailed Analysis
```bash
java -jar jarz-cli.jar --tree -f app.jarz --verbose
```

### Performance Tips

1. **Use appropriate JVM heap size** for large archives
2. **Group related classes** in same directory structure
3. **Use --convert** for existing JAR files rather than recreating
4. **Analyze with --tree** to understand compression efficiency

## Integration Examples

### Maven Integration
```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>exec</goal></goals>
            <configuration>
                <executable>java</executable>
                <arguments>
                    <argument>-jar</argument>
                    <argument>jarz-cli.jar</argument>
                    <argument>--convert</argument>
                    <argument>${project.build.directory}/${project.build.finalName}.jar</argument>
                    <argument>${project.build.directory}/${project.build.finalName}.jarz</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Gradle Integration
```gradle
task createJarz(type: Exec) {
    dependsOn jar
    commandLine 'java', '-jar', 'jarz-cli.jar', '--convert', 
                jar.archiveFile.get().asFile.absolutePath,
                "${buildDir}/libs/${project.name}-${version}.jarz"
}
```

### CI/CD Pipeline
```yaml
# GitHub Actions example
- name: Create JARZ Archive
  run: |
    java -jar jarz-tools/target/jarz-cli.jar --convert \
      target/myapp-${{ github.sha }}.jar \
      target/myapp-${{ github.sha }}.jarz
    
- name: Analyze Compression
  run: |
    java -jar jarz-tools/target/jarz-cli.jar --tree \
      -f target/myapp-${{ github.sha }}.jarz
```

## Best Practices

### Archive Creation
1. **Use meaningful names** for archives
2. **Include proper manifests** for executable JARs
3. **Organize source files** logically before archiving
4. **Test extraction** after creation

### Performance Optimization
1. **Group related classes** together in source structure
2. **Use appropriate compression levels** for your use case
3. **Monitor block organization** with --tree analysis
4. **Consider streaming patterns** for your application

### Deployment
1. **Verify JARZ compatibility** with your runtime environment
2. **Test class loading** from JARZ archives
3. **Monitor decompression performance** in production
4. **Keep original JARs** as backup during migration

---

## See Also

- [JARZ Technical Specification](../technical-specs/JEP-ZSTD-ClassLoader.md)
- [Performance Analysis](../analysis/)
- [Testing Strategy](../testing/Testing-Strategy.md)

---

**Copyright 2024-2026 Plasticity.Cloud**
