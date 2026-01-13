# JARZ Migration Guide for Java Applications

**Complete guide for migrating existing Java applications to use JARZ compressed archives**

This guide assumes you have already converted your JAR files to JARZ format using the [JARZ CLI tools](JARZ-CLI-User-Guide.md).

## Overview

JARZ provides multiple ClassLoader implementations for different deployment scenarios:

| ClassLoader | Use Case | Benefits |
|-------------|----------|----------|
| `JarzClassLoader` | Basic JARZ loading | Drop-in JAR replacement |
| `JarzApplicationClassLoader` | Application with manifest | Main-Class and Class-Path support |
| `CdnJarzClassLoader` | CDN/HTTP streaming | Zero dependencies, HTTP/2 streaming |
| `S3JarzClassLoader` | AWS S3 streaming | Range requests, cost optimization |

## 1. Local JARZ Applications

### Basic JARZ Loading with JarzClassLoader

**Use Case**: Replace URLClassLoader with JARZ support

```java
import jdk.incubator.jarz.classloader.JarzClassLoader;
import java.nio.file.Paths;

// Before: JAR loading
URLClassLoader jarLoader = new URLClassLoader(new URL[]{
    Paths.get("app.jar").toUri().toURL()
});

// After: JARZ loading
try (JarzClassLoader jarzLoader = new JarzClassLoader(Paths.get("app.jarz"))) {
    Class<?> clazz = jarzLoader.loadClass("com.example.MyClass");
    Object instance = clazz.getDeclaredConstructor().newInstance();
    // Use instance...
}
```

### Application ClassLoader with Manifest Support

**Use Case**: Run applications with Main-Class and Class-Path manifest entries

```java
import jdk.incubator.jarz.classloader.JarzApplicationClassLoader;
import java.nio.file.Paths;

// Load application JARZ with manifest support
try (JarzApplicationClassLoader appLoader = new JarzApplicationClassLoader(Paths.get("myapp.jarz"))) {
    // Get main class from manifest
    String mainClassName = appLoader.getMainClassName();
    if (mainClassName != null) {
        Class<?> mainClass = appLoader.loadClass(mainClassName);
        
        // Invoke main method
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }
}
```

### Multiple JARZ Dependencies

**Use Case**: Application with multiple JARZ libraries

```java
import jdk.incubator.jarz.classloader.JarzApplicationClassLoader;
import java.nio.file.Paths;

// Parent-first delegation with multiple JARZ files
ClassLoader parent = ClassLoader.getSystemClassLoader();

try (JarzApplicationClassLoader libLoader = new JarzApplicationClassLoader(
        Paths.get("lib/commons.jarz"), parent);
     JarzApplicationClassLoader appLoader = new JarzApplicationClassLoader(
        Paths.get("app.jarz"), libLoader)) {
    
    // Load application class with library dependencies
    Class<?> appClass = appLoader.loadClass("com.example.Application");
    appClass.getDeclaredConstructor().newInstance();
}
```

## 2. CDN Streaming Applications

### Basic CDN Streaming

**Use Case**: Stream JARZ from CDN without downloading entire archive

```java
import jdk.incubator.jarz.cdn.CdnJarzClassLoader;

// Stream from CloudFront CDN
String cdnUrl = "https://d1234567890.cloudfront.net/app.jarz";

try (CdnJarzClassLoader cdnLoader = new CdnJarzClassLoader(cdnUrl)) {
    // Classes are loaded on-demand via HTTP/2 range requests
    Class<?> clazz = cdnLoader.loadClass("com.example.Service");
    Object service = clazz.getDeclaredConstructor().newInstance();
}
```

### CDN with Custom HTTP Configuration

**Use Case**: Configure timeouts, headers, and connection pooling

```java
import jdk.incubator.jarz.cdn.CdnJarzClassLoader;
import jdk.incubator.jarz.cdn.HttpConfig;
import java.time.Duration;

// Configure HTTP client
HttpConfig config = HttpConfig.builder()
    .connectTimeout(Duration.ofSeconds(10))
    .readTimeout(Duration.ofSeconds(30))
    .maxConcurrentRequests(50)
    .addHeader("Authorization", "Bearer " + token)
    .addHeader("User-Agent", "MyApp/1.0")
    .build();

try (CdnJarzClassLoader cdnLoader = new CdnJarzClassLoader(cdnUrl, config)) {
    Class<?> clazz = cdnLoader.loadClass("com.example.SecureService");
}
```

### CDN with Index Bundle (Optimized)

**Use Case**: Pre-cache frequently used classes for faster startup

```java
import jdk.incubator.jarz.cdn.CdnJarzClassLoader;

// CDN with separate index bundle for optimization
String baseUrl = "https://d1234567890.cloudfront.net/";
String jarzUrl = baseUrl + "app.jarz";
String indexUrl = baseUrl + "app.jarz.index.bundle";

try (CdnJarzClassLoader cdnLoader = new CdnJarzClassLoader(jarzUrl, indexUrl)) {
    // Index bundle pre-loads critical classes (Main-Class, startup dependencies)
    // Reduces cold start time by 60-80%
    Class<?> mainClass = cdnLoader.loadClass("com.example.Application");
    
    // Additional classes loaded on-demand
    Class<?> serviceClass = cdnLoader.loadClass("com.example.BusinessService");
}
```

## 3. AWS S3 Streaming Applications

### Basic S3 Streaming

**Use Case**: Stream JARZ from S3 with range requests

```java
import jdk.incubator.jarz.s3.S3JarzClassLoader;
import software.amazon.awssdk.services.s3.S3Client;

// Create S3 client
S3Client s3Client = S3Client.builder()
    .region(Region.US_EAST_1)
    .build();

// Stream from S3 bucket
try (S3JarzClassLoader s3Loader = new S3JarzClassLoader(
        s3Client, "my-app-bucket", "releases/v1.2.3/app.jarz")) {
    
    // Classes loaded via S3 GetObject range requests
    Class<?> clazz = s3Loader.loadClass("com.example.CloudService");
    Object service = clazz.getDeclaredConstructor().newInstance();
}
```

### S3 with Custom Configuration

**Use Case**: Configure S3 client with custom settings

```java
import jdk.incubator.jarz.s3.S3JarzClassLoader;
import jdk.incubator.jarz.s3.S3Config;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.RequestPayer;

// Configure S3 settings
S3Config s3Config = S3Config.builder()
    .requestPayer(RequestPayer.BUCKET_OWNER)
    .serverSideEncryption("AES256")
    .maxConcurrentRequests(25)
    .retryAttempts(3)
    .build();

S3Client s3Client = S3Client.builder()
    .region(Region.US_WEST_2)
    .build();

try (S3JarzClassLoader s3Loader = new S3JarzClassLoader(
        s3Client, "encrypted-bucket", "app.jarz", s3Config)) {
    
    Class<?> clazz = s3Loader.loadClass("com.example.EncryptedService");
}
```

### S3 with Index Bundle (Production Optimized)

**Use Case**: Production deployment with index optimization

```java
import jdk.incubator.jarz.s3.S3JarzClassLoader;
import software.amazon.awssdk.services.s3.S3Client;

S3Client s3Client = S3Client.create();

// S3 with index bundle for optimal performance
try (S3JarzClassLoader s3Loader = new S3JarzClassLoader(
        s3Client, 
        "production-bucket", 
        "apps/myapp/v2.1.0/app.jarz",
        "apps/myapp/v2.1.0/app.jarz.index.bundle")) {
    
    // Index bundle contains:
    // - Main-Class and startup dependencies
    // - Frequently accessed utility classes
    // - Framework initialization classes
    
    // Fast startup - critical classes pre-cached
    Class<?> mainClass = s3Loader.loadClass("com.example.Application");
    
    // On-demand loading for business logic
    Class<?> businessClass = s3Loader.loadClass("com.example.BusinessLogic");
}
```

## 4. Advanced Migration Patterns

### Hybrid Local + Streaming

**Use Case**: Local core libraries with streaming application code

```java
import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.cdn.CdnJarzClassLoader;

// Local core libraries (cached)
try (JarzClassLoader coreLoader = new JarzClassLoader(Paths.get("core-libs.jarz"));
     CdnJarzClassLoader appLoader = new CdnJarzClassLoader(
        "https://cdn.example.com/app-v1.2.3.jarz", coreLoader)) {
    
    // Core libraries loaded locally (fast)
    Class<?> coreClass = coreLoader.loadClass("com.example.core.Database");
    
    // Application code streamed from CDN (always latest)
    Class<?> appClass = appLoader.loadClass("com.example.app.Controller");
}
```

### Multi-Region Failover

**Use Case**: Primary and backup regions for high availability

```java
import jdk.incubator.jarz.s3.S3JarzClassLoader;
import software.amazon.awssdk.services.s3.S3Client;

// Primary region
S3Client primaryS3 = S3Client.builder().region(Region.US_EAST_1).build();
S3Client backupS3 = S3Client.builder().region(Region.US_WEST_2).build();

S3JarzClassLoader loader = null;
try {
    // Try primary region first
    loader = new S3JarzClassLoader(primaryS3, "primary-bucket", "app.jarz");
    Class<?> clazz = loader.loadClass("com.example.Service");
} catch (Exception e) {
    // Failover to backup region
    if (loader != null) loader.close();
    loader = new S3JarzClassLoader(backupS3, "backup-bucket", "app.jarz");
    Class<?> clazz = loader.loadClass("com.example.Service");
}
```

### Container Platform Integration

**Use Case**: Kubernetes/ECS with streaming JARZ

```java
// Container startup with environment-based configuration
public class ContainerMain {
    public static void main(String[] args) throws Exception {
        String jarzUrl = System.getenv("JARZ_URL");
        String indexUrl = System.getenv("JARZ_INDEX_URL");
        
        if (jarzUrl.startsWith("s3://")) {
            // S3 streaming for AWS environments
            String[] parts = jarzUrl.substring(5).split("/", 2);
            String bucket = parts[0];
            String key = parts[1];
            
            S3Client s3 = S3Client.create();
            try (S3JarzClassLoader loader = new S3JarzClassLoader(s3, bucket, key, indexUrl)) {
                runApplication(loader, args);
            }
        } else {
            // CDN streaming for multi-cloud
            try (CdnJarzClassLoader loader = new CdnJarzClassLoader(jarzUrl, indexUrl)) {
                runApplication(loader, args);
            }
        }
    }
    
    private static void runApplication(ClassLoader loader, String[] args) throws Exception {
        Class<?> mainClass = loader.loadClass(System.getenv("MAIN_CLASS"));
        Method main = mainClass.getMethod("main", String[].class);
        main.invoke(null, (Object) args);
    }
}
```

## 5. Performance Optimization

### Index Bundle Creation

**Use Case**: Create optimized index bundles for faster startup

```bash
# Create index bundle with startup classes
java -jar jarz-tools.jar --create-index \
  --input app.jarz \
  --output app.jarz.index.bundle \
  --include-main-class \
  --include-startup-dependencies \
  --max-size 2MB
```

### Memory Optimization

**Use Case**: Minimize ClassLoader memory overhead

```java
import jdk.incubator.jarz.cdn.CdnJarzClassLoader;
import jdk.incubator.jarz.cdn.MemoryConfig;

// Configure memory usage
MemoryConfig memConfig = MemoryConfig.builder()
    .maxCacheSize(50 * 1024 * 1024)  // 50MB cache
    .blockCacheSize(1024)            // 1KB block cache
    .enableWeakReferences(true)      // Allow GC of unused classes
    .build();

try (CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl, memConfig)) {
    // Memory-optimized class loading
    Class<?> clazz = loader.loadClass("com.example.Service");
}
```

## 6. Migration Checklist

### Pre-Migration Validation

- [ ] **Convert JARs to JARZ**: Use `jarz-tools` to convert all JAR dependencies
- [ ] **Test locally**: Verify JARZ files work with `JarzApplicationClassLoader`
- [ ] **Validate manifests**: Ensure Main-Class and Class-Path entries are preserved
- [ ] **Check dependencies**: Verify all transitive dependencies are included

### Streaming Setup

- [ ] **Upload to CDN/S3**: Deploy JARZ files to your distribution platform
- [ ] **Create index bundles**: Generate optimized index files for critical classes
- [ ] **Configure HTTP/S3**: Set up proper caching headers and access policies
- [ ] **Test connectivity**: Verify range request support and performance

### Production Deployment

- [ ] **Update startup scripts**: Replace `java -jar` with JARZ ClassLoader code
- [ ] **Configure monitoring**: Add metrics for class loading performance
- [ ] **Set up failover**: Implement multi-region or CDN fallback strategies
- [ ] **Performance testing**: Validate startup time and memory usage improvements

## 7. Troubleshooting

### Common Issues

**ClassNotFoundException with streaming**:
```java
// Ensure proper parent delegation
try (CdnJarzClassLoader loader = new CdnJarzClassLoader(url, parentClassLoader)) {
    // Parent classes available to child loader
}
```

**Slow startup with CDN**:
```java
// Use index bundle for critical classes
try (CdnJarzClassLoader loader = new CdnJarzClassLoader(jarzUrl, indexUrl)) {
    // Startup classes pre-cached
}
```

**S3 access denied**:
```java
// Verify IAM permissions for GetObject with range requests
{
    "Version": "2012-10-17",
    "Statement": [{
        "Effect": "Allow",
        "Action": ["s3:GetObject"],
        "Resource": "arn:aws:s3:::my-bucket/app.jarz",
        "Condition": {
            "StringEquals": {
                "s3:ExistingObjectTag/Environment": "production"
            }
        }
    }]
}
```

## 8. Best Practices

### Security
- **Use HTTPS**: Always use encrypted connections for streaming
- **Validate signatures**: Implement JARZ signature verification
- **Restrict access**: Use IAM policies and CDN access controls

### Performance
- **Index bundles**: Create optimized index files for startup classes
- **Connection pooling**: Configure HTTP client connection limits
- **Caching**: Enable CDN and browser caching with proper headers

### Monitoring
- **Class loading metrics**: Track loading times and cache hit rates
- **Network usage**: Monitor bandwidth consumption and range request efficiency
- **Error rates**: Alert on ClassNotFoundException and network failures

---

**Next Steps**: 
- Review [JARZ CLI User Guide](JARZ-CLI-User-Guide.md) for JARZ creation
- See [Performance Tuning Guide](JARZ-Performance-Guide.md) for optimization
- Check [Security Best Practices](JARZ-Security-Guide.md) for production deployment

**Author**: Plasticity.Cloud  
**Updated**: 2026-01-09
