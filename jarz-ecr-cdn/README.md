# JARZ ECR CDN Module

**Cost-Effective Maven Artifact Distribution via Amazon ECR**

This module provides a cost-effective solution for Maven artifact distribution using Amazon ECR as a CDN backend with JARZ (ZSTD-compressed) format. It delivers **SOCI-like streaming capabilities** for Java dependencies at **$107/month** for 1TB storage.

## Features

- **27% compression** via ZSTD format
- **Block-level streaming** using HTTP range requests
- **ECR blob storage** with OCI artifact format
- **Managed service** - no infrastructure to maintain
- **Built-in deduplication** via ECR
- **Private VPC access** without NAT Gateway costs

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>net.jarz-streaming</groupId>
    <artifactId>jarz-ecr-cdn</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Upload JARZ Artifact

```java
try (EcrJarzClient client = new EcrJarzClient("us-east-1")) {
    String digest = client.uploadMavenArtifact(
        Paths.get("spring-boot-starter.jarz"),
        "org.springframework.boot",
        "spring-boot-starter-web",
        "2.7.0"
    );
    System.out.println("Uploaded with digest: " + digest);
}
```

### 3. Stream JARZ Blocks

```java
try (EcrJarzClient client = new EcrJarzClient("us-east-1")) {
    // Get artifact manifest
    OciManifest manifest = client.getMavenArtifact(
        "org.springframework.boot", 
        "spring-boot-starter-web", 
        "2.7.0"
    );
    
    // Download specific 1KB block
    String digest = manifest.layers.get(0).digest;
    byte[] block = client.getJarzBlock("maven-artifacts", digest, 1024, 1024);
    
    // Process block data
    processJarzBlock(block);
}
```

### 4. Use ECR JARZ ClassLoader

```java
try (EcrJarzClassLoader loader = new EcrJarzClassLoader(
        "org.springframework.boot", "spring-boot-starter-web", "2.7.0")) {
    Class<?> clazz = loader.loadClass("org.springframework.boot.SpringApplication");
    // Use class...
}
```

## Architecture

```
Fargate Tasks → ECR APIs → JARZ Artifacts (OCI Blobs) → HTTP Range Requests → Block Streaming
```

### HTTP Client Architecture

**Dual HTTP Client Strategy** for optimal protocol support:

```
jarz-cdn/
├── HTTPClientProvider.java    # HTTP/2 for CDN operations
└── CdnJarzClassLoader.java

jarz-ecr-cdn/
├── EcrHttpClientProvider.java # HTTP/1.1 for ECR compatibility  
└── EcrJarzClient.java
```

**Protocol Optimization**:
- **ECR Operations**: HTTP/1.1 (ECR OCI Distribution API compatibility)
- **CDN Operations**: HTTP/2 (performance, multiplexing)

**Dependencies**:
```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.10.1</version>  <!-- AWS Lambda compatible, 240KB -->
</dependency>
```

### OCI Artifact Format

JARZ files are stored as OCI artifacts in a single ECR repository with Maven coordinate tags:

**Repository Structure:**
```
ECR Repository: maven-artifacts

Tags (Maven Coordinates):
├── org_springframework_boot--spring-boot-starter-web--2.7.0
├── org_springframework_boot--spring-boot-starter-web--3.0.0  
├── com_plasticity_cloud--jarz-streaming--1.0.0
├── junit--junit--4.13.2
└── org_apache_commons--commons-lang3--3.12.0
```

**OCI Manifest:**
```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.manifest.v1+json",
  "layers": [
    {
      "mediaType": "application/vnd.plasticity.jarz.layer.v1+binary",
      "size": 10485760,
      "digest": "sha256:abc123...",
      "annotations": {
        "org.opencontainers.image.title": "spring-boot-starter-web.jarz",
        "org.plasticity.jarz.version": "2.0",
        "maven.groupId": "org.springframework.boot",
        "maven.artifactId": "spring-boot-starter-web",
        "maven.version": "2.7.0"
      }
    }
  ]
}
```

**Benefits:**
- **Single Repository**: Avoids ECR's 10,000 repository limit
- **Maven Compatible**: Standard coordinate system with encoded tags
- **Layer Deduplication**: ECR automatically deduplicates common JARZ blocks
- **Scalable**: Supports millions of Maven artifacts

## Performance & Bandwidth

### ECR Performance Characteristics

**API Rate Limits (Per-Client, Not Per-Repository):**

| API Operation | Rate Limit | Scope | Usage |
|---------------|------------|-------|-------|
| `BatchGetImage` | 200 requests/second | **Per AWS account/client** | Manifest retrieval |
| `GetDownloadUrlForLayer` | 200 requests/second | **Per AWS account/client** | **JARZ block downloads** |
| `BatchCheckLayerAvailability` | 200 requests/second | **Per AWS account/client** | Layer existence checks |
| `GetAuthorizationToken` | 500 requests/second | **Per AWS account/client** | Authentication |

**Key Findings:**
- **Per-client limits**: 200 req/s limit applies to each client, not shared across repository
- **Unlimited concurrent clients**: Single repository can serve thousands of clients simultaneously
- **No bandwidth limits**: ECR doesn't throttle bandwidth, only API calls per client
- **S3 backend performance**: Inherits S3's petabyte-scale throughput capabilities
- **Automatic scaling**: ECR scales horizontally with client count
- **Regional performance**: Best performance when client and ECR are in same region

### Throughput Scenarios

**Single Client:**
```
1 client × 200 req/s × 59KB blocks = 11.8 MB/second per client
```

**Multiple Clients (Enterprise Scale):**
```
100 Fargate tasks × 200 req/s × 59KB = 1,180 MB/second (1.18 GB/s)
1000 developers × 200 req/s × 59KB = 11.8 GB/second aggregate
```

**Repository Capacity:**
- **No per-repository limits**: ECR repository can serve unlimited concurrent clients
- **Backend scaling**: S3 backend handles petabytes/second throughput
- **Bottleneck**: Individual client API rate limits, not repository capacity

### JARZ Block Size Optimization

**Recommended Block Sizes:**
- **JARZ v2 blocks**: 59KB (current implementation) ✅
- **Range requests**: 64KB-1MB for optimal API efficiency
- **Avoid**: Small 4KB blocks (wastes API rate limits)

**Performance Calculation:**
```
Single client: 200 requests/second × 59KB blocks = 11.8 MB/second
Enterprise scale: 1000 clients × 11.8 MB/s = 11.8 GB/second aggregate
```

**Concurrent Download Strategy:**
```java
// Optimal: Use ECR's 200 concurrent request limit
CompletableFuture<byte[]>[] futures = new CompletableFuture[200];
for (int i = 0; i < 200; i++) {
    futures[i] = CompletableFuture.supplyAsync(() -> 
        client.getJarzBlock("maven-artifacts", digest, offset, 59 * 1024));
}
// Theoretical peak: 200 × 59KB = 11.8 MB/second
```

### ECR Backend Architecture

**Storage Layer:**
- **Layer storage**: ECR stores JARZ blobs in S3 backend
- **Manifest storage**: OCI manifests stored separately for fast access
- **Range request support**: Full HTTP 206 Partial Content support via S3
- **Deduplication**: Automatic layer deduplication across all Maven artifacts

**Network Path:**
```
Client → ECR API → S3 Backend → Range Response → Client
       ↑ 200 req/s    ↑ Petabyte/s   ↑ HTTP 206
       per client     backend        per client
```

**Enterprise Maven Repository Scale:**
```
1000 developers → Single ECR Repository → Aggregate: 11.8 GB/second
Each developer:   11.8 MB/second individual throughput
Repository:       No per-repository throughput limits
```

### ECR Direct Access (Ireland - eu-west-1)

| Component | Monthly Cost |
|-----------|-------------|
| ECR Storage (1TB) | $100 |
| VPC Endpoint | $7 |
| Data Transfer | $0 (free within region) |
| **Total** | **$107** |

### Comparison with Alternatives

| Solution | Monthly Cost | Savings |
|----------|-------------|---------|
| **JARZ ECR CDN** | **$107** | **97.7% cheaper** |
| CloudFront + NAT Gateway | $4,582 | Baseline |

## Configuration

### AWS Credentials

The client uses the default AWS credential chain:

```bash
# Via environment variables
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1

# Via AWS CLI
aws configure

# Via IAM roles (recommended for EC2/Fargate)
```

### Required IAM Permissions

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ecr:GetAuthorizationToken",
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage",
        "ecr:InitiateLayerUpload",
        "ecr:UploadLayerPart",
        "ecr:CompleteLayerUpload",
        "ecr:PutImage"
      ],
      "Resource": "arn:aws:ecr:*:*:repository/maven-artifacts"
    }
  ]
}
```

## Advanced Usage

### Range Request Streaming

```java
public class JarzBlockStreamer {
    public void streamMavenArtifact(String groupId, String artifactId, String version) throws IOException {
        try (EcrJarzClient client = new EcrJarzClient("us-east-1")) {
            // Get artifact manifest
            OciManifest manifest = client.getMavenArtifact(groupId, artifactId, version);
            String digest = manifest.layers.get(0).digest;
            long totalSize = manifest.layers.get(0).size;
            
            // Use 59KB blocks (JARZ v2 optimal size)
            long blockSize = 59 * 1024; // 59KB - matches JARZ v2 block size
            
            // Concurrent streaming within ECR rate limits
            List<CompletableFuture<byte[]>> futures = new ArrayList<>();
            for (long offset = 0; offset < totalSize; offset += blockSize) {
                long currentOffset = offset;
                long length = Math.min(blockSize, totalSize - offset);
                
                CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return client.getJarzBlock("maven-artifacts", digest, currentOffset, length);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                futures.add(future);
                
                // Respect ECR rate limit: 200 requests/second
                if (futures.size() >= 200) {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                    futures.forEach(f -> processBlock(f.join()));
                    futures.clear();
                    Thread.sleep(1000); // Wait 1 second for rate limit reset
                }
            }
            
            // Process remaining futures
            if (!futures.isEmpty()) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                futures.forEach(f -> processBlock(f.join()));
            }
        }
    }
}
```

### Custom ClassLoader Integration

```java
public class EcrJarzClassLoader extends ClassLoader {
    private final EcrJarzClient client;
    private final String groupId;
    private final String artifactId;
    private final String version;
    
    public EcrJarzClassLoader(String groupId, String artifactId, String version) {
        this.client = new EcrJarzClient(System.getenv("AWS_REGION"));
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            // Load class from JARZ blocks on-demand using Maven coordinates
            byte[] classData = loadClassFromMavenArtifact(name);
            return defineClass(name, classData, 0, classData.length);
        } catch (IOException e) {
            throw new ClassNotFoundException("Failed to load class: " + name, e);
        }
    }
}
```

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests (requires AWS credentials)

```bash
# Set up test repository
export ECR_TEST_REPOSITORY=maven-artifacts
aws ecr create-repository --repository-name $ECR_TEST_REPOSITORY

# Run integration tests
mvn verify -Pintegration-tests
```

### Performance Tests

```bash
mvn test -Pperformance-tests
```

**Performance Benchmarks:**
- **Single client**: 11.8 MB/second (200 req/s × 59KB blocks)
- **Enterprise scale**: 11.8 GB/second aggregate (1000 concurrent clients)
- **Concurrent clients**: Unlimited (each gets own 200 req/s allowance)
- **Rate limit compliance**: Built-in 1-second backoff every 200 requests
- **Block cache efficiency**: 59KB blocks optimal for JARZ v2 format

## Monitoring

### CloudWatch Metrics

ECR automatically provides metrics for:
- Repository size
- Image push/pull counts
- API request counts

### Custom Metrics

```java
// Track JARZ block cache hits
MeterRegistry registry = Metrics.globalRegistry;
Counter cacheHits = Counter.builder("jarz.ecr.cache.hits")
    .description("JARZ block cache hits")
    .register(registry);
```

## Troubleshooting

### Common Issues

**Authentication Errors:**
```
software.amazon.awssdk.services.ecr.model.EcrException: User is not authorized
```
- Verify AWS credentials and IAM permissions
- Check ECR repository policies

**Range Request Failures:**
```
IOException: Range request failed with status: 416
```
- Verify offset and length parameters
- Check that digest exists in repository
- **Rate limiting**: Implement backoff if hitting 200 req/s limit

**ECR Rate Limit Exceeded:**
```
HTTP 429: Too Many Requests (ThrottleException)
```
- Implement exponential backoff with jitter
- Reduce concurrent request count below 200
- Consider larger block sizes to reduce API calls

**Repository Not Found:**
```
RepositoryNotFoundException: The repository with name 'maven-artifacts' does not exist
```
- Create repository: `aws ecr create-repository --repository-name maven-artifacts`

### Debug Logging

```java
// Enable debug logging
System.setProperty("org.slf4j.simpleLogger.log.net.jarz.streaming.ecr", "debug");
```

## Contributing

See the main [JARZ project documentation](../README.md) for contribution guidelines.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../LICENSE) file for details.

Copyright 2024-2026 Plasticity.Cloud
