# JARZ Demo Scenario: EMR Cold Start Improvement

**Technical Demonstration Guide**

---

## Demo Overview

This demo compares EMR Spark executor cold start times between traditional JAR-based deployment and JARZ streaming deployment.

**Goal**: Demonstrate 6x faster cold start (75s → 13s)

---

## Environment Setup

### Prerequisites

```bash
# AWS Resources
- EMR 7.x cluster (m5.xlarge instances)
- S3 bucket for JARZ archives
- CloudFront distribution (Free tier sufficient)
- ECR repository for container images
```

### Test Application

Simple Spark job that loads common dependencies:
- Spark SQL operations
- AWS SDK (S3 read/write)
- Jackson JSON processing
- SLF4J logging

---

## Scenario A: Traditional JAR Deployment (Baseline)

### Container Image

```dockerfile
# Traditional EMR image
FROM public.ecr.aws/emr-serverless/spark/emr-7.0.0:latest

# Add application JARs (typical enterprise app)
COPY lib/*.jar /usr/lib/spark/jars/

# Image size: ~3.2 GB
# JAR count: 460+
# Total JAR size: ~880 MB
```

### Measurement Points

```
Timeline:
─────────────────────────────────────────────────────────────────►
│                                                                 │
│  T0: Executor requested                                         │
│  T1: Image pull complete              (~45s for 3.2GB)          │
│  T2: Container started                (~2s)                     │
│  T3: JVM initialized                  (~3s)                     │
│  T4: Classpath scanned (460 JARs)     (~8s)                     │
│  T5: First Spark task executed        (~5s)                     │
│  ─────────────────────────────────────────────────────────────  │
│  Total cold start: ~63-75s                                      │
│                                                                 │
```

### Demo Commands (Baseline)

```bash
# 1. Start EMR cluster with traditional image
aws emr create-cluster \
  --name "JARZ-Demo-Baseline" \
  --release-label emr-7.0.0 \
  --applications Name=Spark \
  --instance-type m5.xlarge \
  --instance-count 3

# 2. Submit test job and measure cold start
aws emr add-steps \
  --cluster-id j-XXXXX \
  --steps Type=Spark,Name="ColdStartTest",Args=[...] \
  --step-concurrency-level 1

# 3. Check CloudWatch metrics for timing
# - ExecutorLaunchTime
# - TaskStartDelay
```

---

## Scenario B: JARZ Streaming Deployment

### Container Image (Thin)

```dockerfile
# Thin EMR image with JARZ ClassLoader
FROM public.ecr.aws/emr-serverless/spark/emr-7.0.0:latest

# Remove bundled JARs (will stream from CDN)
RUN rm -rf /usr/lib/spark/jars/aws-*.jar \
           /usr/lib/spark/jars/hadoop-*.jar \
           /usr/lib/spark/jars/jackson-*.jar

# Add only JARZ ClassLoader (5MB)
COPY jarz-cdn.jar /usr/lib/spark/jars/

# Add bootstrap script
COPY jarz-bootstrap.sh /usr/lib/spark/

# Image size: ~650 MB (80% smaller)
```

### JARZ Bootstrap Configuration

```bash
#!/bin/bash
# jarz-bootstrap.sh

# Configure JARZ ClassLoader
export JARZ_CDN_URL="https://d1234abcd.cloudfront.net"
export JARZ_ARCHIVES="hadoop-core.jarz,spark-core.jarz,aws-sdk.jarz"
export JARZ_CACHE_SIZE=128  # blocks

# Set JVM to use JARZ ClassLoader
export SPARK_SUBMIT_OPTS="
  -Djarz.cdn.url=${JARZ_CDN_URL}
  -Djarz.archives=${JARZ_ARCHIVES}
  -Djarz.prefetch=true
"
```

### Measurement Points

```
Timeline:
─────────────────────────────────────────────────────────────────►
│                                                                 │
│  T0: Executor requested                                         │
│  T1: Image pull complete              (~8s for 650MB)           │
│  T2: Container started                (~1s)                     │
│  T3: JVM initialized                  (~1s)                     │
│  T4: JARZ index fetched (CDN)         (~0.1s)                   │
│  T5: First classes streamed           (~2s, only needed)        │
│  T6: First Spark task executed        (~1s)                     │
│  ─────────────────────────────────────────────────────────────  │
│  Total cold start: ~13s                                         │
│                                                                 │
```

### Demo Commands (JARZ)

```bash
# 1. Upload JARZ archives to S3
aws s3 cp hadoop-core.jarz s3://jarz-demo-bucket/
aws s3 cp spark-core.jarz s3://jarz-demo-bucket/
aws s3 cp aws-sdk.jarz s3://jarz-demo-bucket/

# 2. CloudFront distribution already configured (see infra/)

# 3. Start EMR cluster with thin image
aws emr create-cluster \
  --name "JARZ-Demo-Streaming" \
  --release-label emr-7.0.0 \
  --applications Name=Spark \
  --instance-type m5.xlarge \
  --instance-count 3 \
  --bootstrap-actions Path=s3://jarz-demo-bucket/jarz-bootstrap.sh

# 4. Submit same test job
aws emr add-steps \
  --cluster-id j-YYYYY \
  --steps Type=Spark,Name="ColdStartTest",Args=[...] \
  --step-concurrency-level 1
```

---

## Side-by-Side Comparison

### Visual Timeline

```
Traditional (75s):
├─────────────────────────────────────────────────────────────────────────────┤
│ Image Pull (45s)        │ JVM (3s) │ Scan (8s)  │ Load (12s) │ Task (5s)   │
├─────────────────────────────────────────────────────────────────────────────┤

JARZ (13s):
├─────────────┤
│ Pull │ JVM │ Stream │ Task │
│ (8s) │(1s) │  (3s)  │ (1s) │
├─────────────┤

                                                    ▲
                                                    │
                                              62s saved!
```

### Metrics Comparison

| Metric | Traditional | JARZ | Improvement |
|--------|-------------|------|-------------|
| Image pull | 45s | 8s | **5.6x** |
| JVM startup | 3s | 1s | **3x** |
| Class loading | 20s | 3s | **6.7x** |
| First task | 5s | 1s | **5x** |
| **Total** | **75s** | **13s** | **5.8x** |

### Resource Usage

| Resource | Traditional | JARZ | Savings |
|----------|-------------|------|---------|
| Image size | 3.2 GB | 650 MB | **80%** |
| Network transfer | 880 MB | ~50 MB | **94%** |
| Metaspace memory | 400 MB | 45 MB | **89%** |
| S3 GET requests | 460 | 42 | **91%** |

---

## Scale-Out Scenario

### 100 Executor Scale-Out

```
Traditional:
  - Each executor: 75s cold start
  - Total time to full capacity: ~75s (parallel pull, but image cache cold)
  - ECR bandwidth: 320 GB (100 × 3.2 GB)

JARZ:
  - First executor: 13s (CDN cache miss)
  - Executors 2-100: 8s (CDN cache HIT for JARZ blocks)
  - Total time to full capacity: ~13s
  - Network transfer: 650 MB image + ~5 GB JARZ (shared via CDN)
```

### Spot Interruption Recovery

```
Scenario: 20 Spot executors terminated, need replacement

Traditional:
  - 20 new executors × 75s = 75s recovery (parallel, but slow)
  - Risk: Job timeout, SLA breach

JARZ:
  - 20 new executors × 8s = 8s recovery (CDN cache warm)
  - Benefit: Minimal job disruption
```

---

## Demo Script

### Live Demo Steps (15 minutes)

```
1. [2 min] Show baseline EMR cluster
   - Display image size in ECR
   - Show JAR count in container

2. [3 min] Run baseline cold start test
   - Submit Spark job
   - Show CloudWatch timing metrics
   - Highlight: 75s to first task

3. [2 min] Show JARZ setup
   - Display thin image size (650 MB)
   - Show JARZ archives in S3
   - Show CloudFront distribution

4. [3 min] Run JARZ cold start test
   - Submit same Spark job
   - Show CloudWatch timing metrics
   - Highlight: 13s to first task

5. [3 min] Show scale-out comparison
   - Scale both clusters to 50 executors
   - Compare time to full capacity

6. [2 min] Summary and Q&A
   - Side-by-side metrics
   - Cost implications
   - Next steps
```

---

## Expected Questions & Answers

**Q: Does this require application code changes?**
A: No. JARZ ClassLoader is a drop-in replacement. Same bytecode, same APIs.

**Q: What about class loading performance after cold start?**
A: Identical. Classes are cached in JVM metaspace after first load.

**Q: How does this work with Spark's dynamic class loading?**
A: JARZ supports all standard ClassLoader operations. UDFs, custom serializers work unchanged.

**Q: What's the CDN cost?**
A: CloudFront Free tier (1M requests, 100GB) covers most dev/staging. Production ~$15-200/month.

**Q: Can we use this with EMR Serverless?**
A: Yes, same architecture applies. Even more impactful due to frequent cold starts.

---

## Demo Resources

```
Repository: github.com/plasticity-of-cloud/jdk-enhancements
├── jarz-cdn/                    # CDN ClassLoader module
├── infra/aws/cloudformation/    # CloudFront + S3 setup
└── docs/cloud-specific-proposal/
    ├── 01-Executive-One-Pager.md
    ├── 02-Demo-Scenario.md      # This document
    └── 03-Cost-Analysis.md
```

---

*Prepared: December 2025*
