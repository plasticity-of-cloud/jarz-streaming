# JARZ Testing Strategy

## Overview

Comprehensive testing strategy for JARZ format implementation covering unit tests, integration tests, performance benchmarks, and dictionary training validation.

## Test Categories

### 1. Unit Tests

#### Core Format Tests (`jarz-core`)
- **Format validation**: Magic bytes, version compatibility, header/footer integrity
- **Entry management**: Hash computation, path validation, CRC verification
- **Compression roundtrip**: ZSTD compress/decompress correctness
- **Index operations**: Entry lookup, byte range calculation
- **Edge cases**: Empty files, large files (>2GB), Unicode paths

```java
@Test
void roundtripSingleEntry(@TempDir Path tempDir) throws Exception {
    Path jarzFile = tempDir.resolve("test.jarz");
    byte[] data = "Hello JARZ!".getBytes();
    
    try (JarzWriter writer = new JarzWriter(jarzFile)) {
        writer.addEntry("test.txt", data);
    }
    
    try (JarzReader reader = new JarzReader(jarzFile)) {
        assertThat(reader.read("test.txt")).isEqualTo(data);
    }
}
```

#### FastPFOR Tests (`jarz-fastpfor`)
- **Integer encoding**: Frame-of-reference, bit-packing correctness
- **Vector API**: SIMD acceleration validation
- **Bytecode extraction**: Constant pool parsing, integer stream separation
- **Compression ratios**: Typical bytecode patterns (CP indices, offsets)

```java
@Test
void compressionRatioForTypicalBytecode() {
    int[] values = new int[1000];
    for (int i = 0; i < values.length; i++) {
        values[i] = 100 + (i % 50); // Range 100-149
    }
    
    byte[] encoded = codec.encode(values);
    double ratio = (double) encoded.length / (values.length * 4);
    
    assertThat(ratio).isLessThan(0.3); // Should achieve 70%+ compression
}
```

#### ClassLoader Tests (`jarz-classloader`)
- **Class loading**: Standard class resolution, package validation
- **Resource access**: getResourceAsStream, findResource
- **Security**: Code source, protection domains
- **Byte range**: S3 range request calculation

### 2. Integration Tests

#### Dictionary Training Integration (`jarz-dictionary-trainer`)
- **Corpus collection**: JDK modules, framework JARs, sample diversity
- **Training pipeline**: Sample preprocessing, ZSTD dictionary generation
- **Validation**: Compression improvement measurement, cross-validation
- **Performance**: Training time, memory usage

```java
@Test
void trainOnJdkClasses(@TempDir Path tempDir) throws Exception {
    // Create mock class files
    Path jdkDir = tempDir.resolve("jdk");
    Files.createDirectories(jdkDir);
    
    for (int i = 0; i < 150; i++) {
        Path classFile = jdkDir.resolve("Class" + i + ".class");
        Files.write(classFile, generateMockClassBytes(i));
    }
    
    TrainingCorpus corpus = TrainingCorpus.builder()
        .jdkModules(List.of(jdkDir))
        .maxPerCategory(100)
        .maxTotal(100)
        .build();
    
    DictionaryTrainer trainer = new DictionaryTrainer();
    DictionaryTrainer.TrainingResult result = trainer.train(corpus);
    
    assertThat(result.dictionary()).isNotEmpty();
    assertThat(result.avgCompressionRatio()).isLessThan(0.7);
}
```

#### S3 Streaming Integration (`jarz-s3`)
**Prerequisites**: AWS credentials, test S3 bucket
```bash
export JARZ_S3_TEST_BUCKET=my-test-bucket
export AWS_PROFILE=test-profile
```

- **Range requests**: Index loading, class streaming, parallel fetching
- **Caching**: Local cache behavior, memory management
- **Error handling**: Network failures, missing objects, access denied
- **Cost optimization**: Request count, data transfer measurement

```java
@EnabledIfEnvironmentVariable(named = "JARZ_S3_TEST_BUCKET", matches = ".+")
@Test
void loadClassFromS3(@TempDir Path tempDir) throws Exception {
    // Upload JARZ to S3
    String key = "test-jarz/" + System.currentTimeMillis() + ".jarz";
    s3.putObject(PutObjectRequest.builder().bucket(TEST_BUCKET).key(key).build(), jarzFile);
    
    // Load class from S3
    try (S3JarzClassLoader loader = new S3JarzClassLoader(s3, TEST_BUCKET, key)) {
        Class<?> clazz = loader.loadClass("com.example.TestClass");
        assertThat(clazz.getName()).isEqualTo("com.example.TestClass");
    }
}
```

#### End-to-End Workflow Tests
- **CLI tools**: Create, extract, info, train-dict commands
- **Build integration**: Maven plugin, Gradle plugin
- **Real applications**: Spring Boot, Micronaut, Quarkus

### 3. Performance Tests

#### Compression Benchmarks (`jarz-benchmarks`)
JMH benchmarks comparing ZSTD vs DEFLATE:

```java
@Benchmark
public byte[] zstdDecompress() {
    byte[] output = new byte[classSize];
    zstdDecompressor.decompress(zstdCompressed, 0, zstdCompressed.length, output, 0, classSize);
    return output;
}

@Benchmark
public byte[] deflateDecompress() throws Exception {
    inflater.setInput(deflateCompressed);
    byte[] output = new byte[classSize];
    inflater.inflate(output);
    inflater.reset();
    return output;
}
```

**Target Metrics**:
- ZSTD decompression: 3-5x faster than DEFLATE
- Compression ratio: 25-40% better than ZIP
- Memory usage: <2x original class size
- Throughput: >100MB/s decompression

#### S3 Streaming Benchmarks
- **Cold start simulation**: Time to load first 100 classes
- **Parallel loading**: Concurrent class requests
- **Cache effectiveness**: Hit ratio, memory efficiency
- **Network efficiency**: Bytes transferred vs bytes used

#### ClassLoader Performance
- **Class resolution time**: Compared to URLClassLoader
- **Memory footprint**: Heap usage, off-heap caching
- **Concurrent access**: Multi-threaded class loading

### 4. Compatibility Tests

#### JVM Compatibility
- **OpenJDK versions**: 25+ (Vector API stability requirement)
- **Vendor JVMs**: Oracle JDK 25+, Eclipse Temurin 25+, GraalVM 25+
- **Platforms**: Linux x64, Windows x64, macOS ARM64
- **Vector API**: Full support including GraalVM native image

#### Framework Compatibility
- **Spring Boot**: Auto-configuration, executable JARs
- **Jakarta EE**: Application servers, CDI
- **Build tools**: Maven, Gradle, SBT

#### Security Tests
- **Signed JARs**: Code signing verification
- **Security managers**: Permission checks
- **Classloader isolation**: Parent delegation, sealed packages

### 5. Stress Tests

#### Large Archive Tests
- **Size limits**: 4GB+ archives, 100K+ entries
- **Memory pressure**: Low heap scenarios
- **Concurrent access**: Multiple threads, multiple processes

#### Dictionary Training Stress
- **Large corpora**: 100K+ class files, 10GB+ data
- **Memory constraints**: Training with limited heap
- **Diverse inputs**: Mixed JDK versions, framework combinations

#### S3 Streaming Stress
- **High latency**: Simulated slow networks
- **Request throttling**: S3 rate limiting scenarios
- **Large classes**: Multi-MB class files
- **Concurrent loaders**: Multiple applications, shared S3 objects

## Test Data Management

### Mock Data Generation
```java
private byte[] generateClassLikeData(int size) {
    byte[] data = new byte[size];
    // Class file magic
    data[0] = (byte) 0xCA;
    data[1] = (byte) 0xFE;
    data[2] = (byte) 0xBA;
    data[3] = (byte) 0xBE;
    // Fill with semi-random but compressible data
    Random r = new Random(42);
    for (int i = 4; i < size; i++) {
        data[i] = (byte) (r.nextInt(50)); // Limited range = more compressible
    }
    return data;
}
```

### Real Class File Corpus
- **JDK classes**: Extract from `rt.jar`, module JARs
- **Framework classes**: Spring, Guava, Jackson, Netty
- **Application classes**: Real-world applications, various sizes

### Test Environments
- **Local**: Developer machines, CI/CD
- **Cloud**: AWS EC2, ECS, EKS
- **Container**: Docker, Kubernetes, Fargate

## Continuous Integration

### GitHub Actions Pipeline
```yaml
name: JARZ Tests
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: mvn test

  integration-tests:
    runs-on: ubuntu-latest
    if: github.event_name == 'push'
    env:
      JARZ_S3_TEST_BUCKET: ${{ secrets.S3_TEST_BUCKET }}
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: mvn verify

  benchmarks:
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
      - run: java -jar jarz-benchmarks/target/jarz-benchmarks.jar
```

### Performance Regression Detection
- **Baseline measurements**: Store benchmark results
- **Threshold alerts**: >10% performance degradation
- **Trend analysis**: Performance over time

## Test Metrics & Reporting

### Coverage Targets
- **Unit tests**: >90% line coverage
- **Integration tests**: >80% feature coverage
- **End-to-end**: 100% critical path coverage

### Performance Baselines
| Metric | Target | Measurement |
|--------|--------|-------------|
| Compression ratio | >30% vs ZIP | Archive size comparison |
| Decompression speed | >3x vs DEFLATE | JMH throughput |
| S3 cold start | <500ms for 100 classes | End-to-end timing |
| Memory overhead | <2x class size | Heap profiling |

### Quality Gates
- All unit tests pass
- Integration tests pass (with AWS credentials)
- Performance within 10% of baseline
- No security vulnerabilities
- Code coverage >85%

## Test Execution

### Local Development
```bash
# Unit tests only
mvn test

# All tests (requires AWS setup)
export JARZ_S3_TEST_BUCKET=my-test-bucket
mvn verify

# Benchmarks
mvn package -pl jarz-benchmarks
java -jar jarz-benchmarks/target/jarz-benchmarks.jar
```

### CI/CD Pipeline
- **PR validation**: Unit tests, basic integration
- **Main branch**: Full test suite, performance benchmarks
- **Release**: Stress tests, compatibility matrix

This comprehensive testing strategy ensures JARZ format reliability, performance, and compatibility across diverse environments and use cases.
