# CDN HTTP/2 ClassLoader Proposal

**Status**: Proposed  
**Date**: 2025-12-27  
**Author**: JARZ Team

## Executive Summary

Replace AWS SDK-based S3 streaming with a zero-dependency CDN + HTTP/2 architecture using JDK 21+ built-in `java.net.http.HttpClient` with virtual threads. Cloud-agnostic design works with AWS CloudFront, Azure Front Door, and Google Cloud CDN.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Current: S3 SDK Streaming                         │
├─────────────────────────────────────────────────────────────────────┤
│  JVM → AWS SDK v2 (~50MB) → SigV4 Auth → S3 API → Range Requests    │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Proposed: CDN HTTP/2 (Cloud-Agnostic)             │
├─────────────────────────────────────────────────────────────────────┤
│  JVM → HttpClient (JDK) → HTTP/2 → CDN Edge → Object Storage Origin │
│                                                                      │
│  Benefits:                                                           │
│  • Zero external dependencies                                        │
│  • HTTP/2 multiplexing (parallel blocks on single connection)        │
│  • Virtual threads for non-blocking I/O                              │
│  • Edge caching (hot classes served from global POPs)                │
│  • Flat-rate pricing option (AWS CloudFront)                         │
│  • Works with any CDN: CloudFront, Front Door, Cloud CDN             │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Benefits

| Aspect | S3 SDK | CDN + HttpClient |
|--------|--------|-------------------------|
| Dependencies | ~50MB (AWS SDK v2) | **0** (JDK built-in) |
| Connection model | HTTP/1.1 per-request | **HTTP/2 multiplexed** |
| Threading | Platform threads | **Virtual threads** |
| Pricing | Per-request ($0.0004/1K) | **Flat-rate option** |
| Latency | Regional storage (~50ms) | **Edge-cached (~5ms)** |
| Auth complexity | SigV4 signing | Simple signed URLs/cookies |
| Cold start impact | SDK initialization | **Minimal** |

## Implementation

### Core ClassLoader

```java
public class CdnJarzClassLoader extends ClassLoader implements AutoCloseable {
    private final HttpClient httpClient;
    private final String cdnBaseUrl;
    private final JarzV2Index index;
    
    public CdnJarzClassLoader(String cdnBaseUrl) {
        this.cdnBaseUrl = cdnBaseUrl;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.index = fetchIndex();
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String path = name.replace('.', '/') + ".class";
        BlockLocation block = index.locateBlock(path);
        
        byte[] blockData = fetchBlock(block);
        byte[] classBytes = extractEntry(blockData, path);
        
        return defineClass(name, classBytes, 0, classBytes.length);
    }
    
    private byte[] fetchBlock(BlockLocation block) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(cdnBaseUrl + "/app.jarz"))
            .header("Range", "bytes=" + block.offset() + "-" + block.endOffset())
            .GET()
            .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
    }
    
    // Parallel block prefetch using virtual threads
    public void prefetchBlocks(List<String> classNames) {
        List<BlockLocation> blocks = classNames.stream()
            .map(index::locateBlock)
            .distinct()
            .toList();
        
        // HTTP/2 multiplexing + virtual threads = parallel fetches on single connection
        blocks.parallelStream().forEach(this::fetchBlock);
    }
}
```

### HTTP/2 Multiplexing Advantage

```
HTTP/1.1 (S3 SDK):
┌──────┐    ┌──────┐    ┌──────┐
│Conn 1│    │Conn 2│    │Conn 3│   (3 TCP connections)
│Block1│    │Block2│    │Block3│
└──────┘    └──────┘    └──────┘

HTTP/2 (CDN):
┌─────────────────────────────────┐
│        Single Connection         │
│  Stream 1: Block1               │
│  Stream 2: Block2  (multiplexed)│
│  Stream 3: Block3               │
└─────────────────────────────────┘
```

## CDN Configuration Examples

### AWS CloudFront

```yaml
# CloudFormation snippet
CloudFrontDistribution:
  Type: AWS::CloudFront::Distribution
  Properties:
    DistributionConfig:
      Origins:
        - DomainName: !Sub "${JarzBucket}.s3.${AWS::Region}.amazonaws.com"
          Id: S3Origin
          S3OriginConfig:
            OriginAccessIdentity: !Sub "origin-access-identity/cloudfront/${OAI}"
      DefaultCacheBehavior:
        TargetOriginId: S3Origin
        ViewerProtocolPolicy: https-only
        CachePolicyId: !Ref JarzCachePolicy
        OriginRequestPolicyId: !Ref RangeRequestPolicy
      HttpVersion: http2and3  # HTTP/2 + HTTP/3 (QUIC)
      PriceClass: PriceClass_All

JarzCachePolicy:
  Type: AWS::CloudFront::CachePolicy
  Properties:
    CachePolicyConfig:
      Name: JarzBlockCaching
      DefaultTTL: 86400  # 24 hours (JARZ archives are immutable)
      MaxTTL: 31536000   # 1 year
      MinTTL: 0
      ParametersInCacheKeyAndForwardedToOrigin:
        HeadersConfig:
          HeaderBehavior: whitelist
          Headers:
            - Range  # Cache range requests separately
```

## AWS CloudFront Pricing Options

AWS offers **two different** pricing models for CloudFront. Choose based on your usage pattern:

### Option 1: Flat-Rate Pricing Plans (NEW - November 2025)

**Best for:** Unpredictable traffic, DDoS protection, simplicity, no commitment.

| Plan | Monthly Cost | Requests | Data Transfer | Includes |
|------|-------------|----------|---------------|----------|
| Free | $0 | 1M | 100 GB | CDN + WAF + DDoS + DNS |
| Pro | $15 | 10M | 1 TB | + Bot analytics |
| Business | $200 | 100M | 10 TB | + Custom caching rules |
| Premium | $1,000 | 500M | 50 TB | + Origin Shield, failover |

**Key benefits:**
- ✅ **No overages** - even during DDoS attacks or viral traffic
- ✅ **No annual commitment** - upgrade/downgrade anytime
- ✅ **Bundled services** - WAF, Route 53 DNS, CloudWatch Logs, S3 credits included
- ✅ **Simple** - one price, no calculations

**JARZ use case:** Start with Free tier for development, Pro/Business for production.

### Option 2: Security Savings Bundle (Since 2022)

**Best for:** High-volume, predictable usage with maximum discount.

| Commitment | Discount | WAF Included |
|------------|----------|--------------|
| 1-year term | **30% off** pay-as-you-go | Up to 10% of commitment |

**How it works:**
- Commit to monthly spend (e.g., $35K/month)
- Get 30% discount on all CloudFront usage
- Usage beyond commitment charged at standard rates
- Applies to: data transfer, requests, Lambda@Edge, Origin Shield, etc.

**Example calculation:**
```
Monthly CloudFront usage: $50,000 (at standard rates)
With 30% discount: $50,000 × 0.70 = $35,000/month commitment
Savings: $15,000/month = $180,000/year
```

**JARZ use case:** Enterprise deployments with >10 TB/month predictable traffic.

### Pricing Comparison for JARZ Streaming

**Scenario:** 1000 container applications, 100 class loads each/day, 90% cache hit rate

| Pricing Model | Monthly Cost | Notes |
|---------------|-------------|-------|
| Registry Direct (no CDN) | ~$50-100 | Image pulls + data transfer, no caching |
| Flat-Rate Free | $0 | Up to 1M requests, 100 GB |
| Flat-Rate Pro | $15 | Up to 10M requests, 1 TB |
| Security Savings Bundle | Variable | 30% off, requires 1-year commitment |

**Recommendation by usage:**

| Monthly Traffic | Recommended Plan |
|-----------------|------------------|
| < 1M requests, < 100 GB | **Free** (Flat-Rate) |
| 1-10M requests, < 1 TB | **Pro** ($15/month) |
| 10-100M requests, < 10 TB | **Business** ($200/month) |
| > 100M requests, predictable | **Security Savings Bundle** (30% off) |
| > 100M requests, unpredictable | **Premium** ($1,000/month, no overages) |

## Cross-Cloud Comparison

| Feature | AWS CloudFront | Azure Front Door | Google Cloud CDN |
|---------|---------------|------------------|------------------|
| Flat-rate pricing | ✅ Savings Bundle | ❌ Pay-as-you-go | ❌ CUDs (compute) |
| HTTP/2 | ✅ | ✅ | ✅ |
| HTTP/3 (QUIC) | ✅ | ✅ | ✅ |
| Range request caching | ✅ | ✅ | ✅ |
| Origin: Object Storage | ✅ S3 native | ✅ Blob Storage | ✅ GCS |
| Edge locations | 600+ | 192+ | 200+ |

**Recommendation**: AWS CloudFront has the best pricing model for this use case.

### Cloud-Agnostic Usage

The same `CdnJarzClassLoader` works with any CDN:

```java
// AWS CloudFront + S3
new CdnJarzClassLoader("https://d1234.cloudfront.net");

// Azure Front Door + Blob Storage
new CdnJarzClassLoader("https://myapp.azurefd.net");

// Google Cloud CDN + GCS  
new CdnJarzClassLoader("https://myapp.cdn.googleapis.com");
```

The ClassLoader implementation is **cloud-agnostic** - only the CDN URL changes.

## Performance Expectations

| Metric | S3 SDK | CDN HTTP/2 |
|--------|--------|-------------------|
| Cold start overhead | ~500ms (SDK init) | **~50ms** |
| First class load | ~100ms | **~20ms** (edge) |
| Subsequent loads | ~50ms | **~5ms** (cached) |
| Connection overhead | New per request | **Multiplexed** |
| Memory footprint | ~50MB (SDK) | **~5MB** |

## Implementation Phases

### Phase 1: Core HTTP/2 ClassLoader (1 week)
- [x] `CdnJarzClassLoader` with HttpClient ✅
- [ ] Range request handling
- [ ] Block caching (in-memory LRU) ✅ (basic)
- [ ] Unit tests with WireMock

### Phase 2: Virtual Thread Integration (3 days)
- [x] Parallel block prefetch ✅ (basic)
- [ ] Async class loading API
- [ ] Backpressure handling

### Phase 3: CDN Configuration Templates (2 days)
- [x] AWS CloudFormation templates ✅
- [x] Azure ARM templates ✅
- [x] GCP Terraform templates ✅
- [ ] Signed URL/cookie support

### Phase 4: Benchmarks & Validation (3 days)
- [ ] JMH benchmarks vs S3 SDK
- [ ] Real CDN testing
- [ ] Lambda cold start comparison

## Project Structure

### Option 1: Single Module + IaC Folder (Recommended)

The Java code is cloud-agnostic, so a single module suffices. IaC templates are kept separate since they're not Java artifacts.

```
jarz/
├── jarz-core/                    # Core JARZ format (existing)
├── jarz-classloader/             # Local file ClassLoader (existing)
├── jarz-s3/                      # S3 SDK ClassLoader (existing, backward compat)
├── jarz-tools/                   # CLI tools (existing)
│
├── jarz-cdn/                     # Cloud-agnostic CDN ClassLoader
│   ├── src/main/java/
│   │   └── jdk/incubator/jarz/cdn/
│   │       ├── CdnJarzClassLoader.java
│   │       ├── Http2BlockFetcher.java
│   │       ├── BlockCache.java
│   │       └── SignedUrlProvider.java    # Interface
│   └── pom.xml                   # Zero dependencies (JDK only)
│
└── infra/                        # IaC templates (NOT Maven modules)
    ├── README.md
    ├── aws/
    │   ├── cloudformation/
    │   │   └── jarz-cdn-stack.yaml
    │   └── terraform/
    │       └── main.tf
    ├── azure/
    │   ├── arm/
    │   │   └── jarz-cdn-template.json
    │   └── terraform/
    │       └── main.tf
    └── gcp/
        └── terraform/
            └── main.tf
```

**Rationale:**
- Java code is identical for all clouds - only the URL changes
- IaC is not Java, so shouldn't be Maven modules
- Clean separation of concerns

### Option 2: Cloud-Specific Java Helpers (If Needed Later)

If cloud-specific signed URL generation becomes complex, add helper modules:

```
jarz-cdn/
├── jarz-cdn-core/                # Core HttpClient ClassLoader (zero deps)
│   └── CdnJarzClassLoader.java
│   └── SignedUrlProvider.java    # Interface
│
├── jarz-cdn-aws/                 # AWS-specific helpers
│   └── CloudFrontSignedUrlProvider.java
│   └── pom.xml                   # Depends on: jarz-cdn-core
│
├── jarz-cdn-azure/               # Azure-specific helpers
│   └── FrontDoorSasProvider.java
│   └── pom.xml                   # Depends on: jarz-cdn-core
│
└── jarz-cdn-gcp/                 # GCP-specific helpers
    └── CloudCdnSignedUrlProvider.java
    └── pom.xml                   # Depends on: jarz-cdn-core
```

**When to use Option 2:**
- Complex signed URL/cookie generation requiring cloud SDKs
- Cloud-specific authentication flows (IAM roles, managed identities)
- Provider-specific optimizations

**Current recommendation:** Start with Option 1. Add cloud-specific modules only if signed URL complexity warrants it.

## Risk Assessment

| Risk | Mitigation |
|------|------------|
| CDN cache misses | Warm cache with prefetch, long TTLs |
| HTTP/2 not supported | Fallback to HTTP/1.1 (still works) |
| Signed URL expiration | Auto-refresh with signed cookies |
| Regional latency | Use global edge coverage |

## Success Criteria

- [x] Zero external dependencies (JDK only) ✅
- [ ] 10x faster cold start vs S3 SDK
- [ ] 90%+ cache hit rate for hot classes
- [ ] HTTP/2 multiplexing validated
- [ ] Virtual thread integration working
- [x] CloudFormation/ARM/Terraform templates ready ✅

## Conclusion

This architecture eliminates cloud SDK dependencies while providing:
- **Better performance** (edge caching, HTTP/2 multiplexing)
- **Lower costs** (flat-rate pricing option on AWS)
- **Simpler deployment** (zero dependencies)
- **Cloud-agnostic** (same code works with any CDN)

The JDK 21+ `HttpClient` with virtual threads is the perfect fit for JARZ block streaming.

---

*Proposed: 2025-12-27*
