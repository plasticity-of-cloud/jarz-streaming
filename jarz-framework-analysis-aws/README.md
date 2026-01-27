# JARZ Framework Analysis AWS

**Enterprise-grade JARZ analysis for AWS EMR containers**

This module provides comprehensive analysis tools for optimizing AWS EMR (Elastic MapReduce) containers using JARZ compression technology.

## Overview

The JARZ Framework Analysis AWS module enables:
- **EMR Container Optimization**: Analyze and optimize Flink and Spark EMR containers
- **Compression Analysis**: Detailed compression metrics and cost impact analysis
- **Enterprise Reporting**: Comprehensive reports for business decision-making
- **Production Readiness**: Validation tools for production deployment

## Quick Start

### Prerequisites
- Docker installed and running
- Java 21+ for JARZ CLI compilation
- `bc` calculator for compression calculations
- `numfmt` for human-readable size formatting

### Run Complete Analysis
```bash
# Analyze both Flink and Spark EMR containers
./scripts/analyze-emr-combined.sh

# Individual framework analysis
./scripts/analyze-flink-emr.sh
./scripts/analyze-spark-emr.sh
```

### Enhanced Conversion
```bash
# Apply enhanced clustering algorithms
./scripts/enhanced-conversion.sh flink data/flink-emr
./scripts/enhanced-conversion.sh spark data/spark-emr
```

## Directory Structure

```
jarz-framework-analysis-aws/
├── scripts/
│   ├── analyze-flink-emr.sh      # Flink EMR analysis
│   ├── analyze-spark-emr.sh      # Spark EMR analysis
│   ├── analyze-emr-combined.sh   # Combined analysis
│   └── enhanced-conversion.sh    # Enhanced JARZ conversion
├── data/
│   ├── flink-emr/               # Flink analysis data
│   │   ├── original-jars/       # Extracted JAR files
│   │   ├── jarz-converted/      # Standard JARZ conversion
│   │   └── jarz-enhanced/       # Enhanced JARZ conversion
│   └── spark-emr/               # Spark analysis data
│       ├── original-jars/       # Extracted JAR files
│       ├── jarz-converted/      # Standard JARZ conversion
│       └── jarz-enhanced/       # Enhanced JARZ conversion
└── results/                     # Generated analysis reports
    ├── flink-emr-analysis-*.md
    ├── spark-emr-analysis-*.md
    └── emr-combined-analysis-*.md
```

## Analysis Features

### Container Extraction
- **Automated JAR Extraction**: Pulls EMR container images and extracts JAR files
- **Multi-Location Support**: Searches all known EMR JAR locations
- **Symbolic Link Handling**: Properly handles symbolic links and empty files

### JARZ Conversion
- **Framework-Aware Clustering**: Optimized block clustering for Flink/Spark
- **Dependency Analysis**: Enhanced classpath-aware dependency resolution
- **Compression Optimization**: Multiple compression strategies based on JAR characteristics

### Analysis & Reporting
- **Compression Metrics**: Detailed size reduction and compression ratios
- **Component Analysis**: Framework-specific component breakdown
- **Cost Impact**: Enterprise cost savings projections
- **Performance Insights**: Container startup and operational improvements

## Target EMR Images

### Flink EMR 7.11.0
- **Image**: `public.ecr.aws/emr-on-eks/flink/emr-7.11.0-flink:latest`
- **Components**: Flink runtime, Hadoop libraries, AWS integrations
- **Expected Reduction**: 25-30%

### Spark EMR 7.12.0
- **Image**: `public.ecr.aws/emr-on-eks/spark/emr-7.12.0:latest`
- **Components**: Spark core, SQL engine, MLlib, Hadoop, Hudi
- **Expected Reduction**: 20-25%

## Enterprise Benefits

### Storage & Transfer
- **ECR Storage Costs**: 20-30% reduction in container registry fees
- **Container Pull Speed**: Faster image downloads and deployments
- **Multi-Region Efficiency**: Significant bandwidth savings across regions

### Operational Improvements
- **Cold Start Performance**: Faster container initialization
- **CI/CD Acceleration**: Reduced build and deployment times
- **Edge Computing**: Enables EMR workloads on resource-constrained nodes

### Cost Optimization
- **Infrastructure Savings**: Reduced storage, transfer, and compute costs
- **Developer Productivity**: Faster development and testing cycles
- **Scalability**: More efficient auto-scaling and cluster provisioning

## Usage Examples

### Basic Analysis
```bash
# Run Flink analysis
cd /home/ubuntu/projects/pl-cloud/jdk-enhancements/jarz-framework-analysis-aws
./scripts/analyze-flink-emr.sh

# Results will be in:
# - data/flink-emr/compression-report.md
# - results/flink-emr-analysis-YYYYMMDD.md
```

### Enhanced Conversion
```bash
# Apply enhanced clustering for better compression
./scripts/enhanced-conversion.sh flink data/flink-emr

# Compare results:
# - data/flink-emr/jarz-converted/     (standard)
# - data/flink-emr/jarz-enhanced/     (enhanced)
```

### Combined Enterprise Report
```bash
# Generate comprehensive business report
./scripts/analyze-emr-combined.sh

# Enterprise report: results/emr-combined-analysis-YYYYMMDD.md
```

## Integration

### CI/CD Pipeline
```yaml
# GitHub Actions example
- name: EMR JARZ Analysis
  run: |
    cd jarz-framework-analysis-aws
    ./scripts/analyze-emr-combined.sh
    
- name: Upload Results
  uses: actions/upload-artifact@v3
  with:
    name: emr-jarz-analysis
    path: jarz-framework-analysis-aws/results/
```

### Container Optimization
```dockerfile
# Use JARZ-optimized EMR base image
FROM emr-flink-jarz:7.11.0-optimized
COPY --from=jarz-converter /opt/jarz/ /opt/jarz/
ENV JAVA_OPTS="-javaagent:/opt/jarz/jarz-launcher.jar"
```

## Performance Expectations

### Compression Ratios
- **Flink EMR**: 25-30% size reduction
- **Spark EMR**: 20-25% size reduction
- **Combined**: 22-28% average reduction

### Container Improvements
- **Pull Time**: 20-30% faster downloads
- **Startup Time**: 15-25% faster initialization
- **Memory Usage**: 10-20% reduced ClassLoader overhead

## Troubleshooting

### Common Issues
1. **Docker Permission**: Ensure Docker daemon is accessible
2. **JARZ CLI Build**: Run `mvn clean install` in project root
3. **Missing Dependencies**: Install `bc` and `numfmt` utilities

### Debug Mode
```bash
# Enable verbose output
export JARZ_DEBUG=true
./scripts/analyze-flink-emr.sh
```

## Contributing

### Adding New EMR Versions
1. Update image URLs in analysis scripts
2. Add version-specific JAR locations
3. Test with new EMR container images

### Enhancing Analysis
1. Extend framework-specific optimizations
2. Add new compression strategies
3. Improve reporting and metrics

---

**Generated by**: JARZ Framework Analysis AWS  
**Version**: 1.0  
**Last Updated**: January 27, 2026
