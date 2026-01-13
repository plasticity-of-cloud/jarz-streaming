# JARZ Infrastructure

Multi-cloud infrastructure for JARZ distribution with CDN support across AWS, Azure, GCP, and Oracle Cloud.

## Structure

```
infra/
├── aws/
│   ├── cloudfront-dist/           # CloudFront flat-rate plans (recommended)
│   ├── cloudformation/            # CloudFormation templates
│   └── terraform/                 # Terraform configurations
├── azure/
│   ├── arm/                       # Azure Resource Manager templates
│   └── terraform/                 # Terraform configurations
├── gcp/                           # Google Cloud Platform
└── oracle-cloud/                  # Oracle Cloud Infrastructure
```

## Quick Start

### AWS CloudFront (Recommended)
```bash
cd infra/aws/cloudfront-dist
./deploy.sh your-domain.com Business
```

### Azure CDN
```bash
cd infra/azure/arm
# See Azure-specific README
```

## Cloud Provider Comparison

| Provider | CDN Service | Flat-Rate Plans | JARZ Optimized |
|----------|-------------|-----------------|----------------|
| **AWS** | CloudFront | ✅ Yes | ✅ Yes |
| **Azure** | Azure CDN | ❌ Pay-per-use | ✅ Yes |
| **GCP** | Cloud CDN | ❌ Pay-per-use | ⚠️ Partial |
| **Oracle** | OCI CDN | ❌ Pay-per-use | ⚠️ Partial |

## AWS CloudFront Features (Recommended)

- **Flat-Rate Plans**: $0-$1000/month with no overages
- **JARZ-Optimized**: Range request support for block streaming
- **Security**: WAF protection, DDoS mitigation, HTTPS-only
- **Performance**: 750+ global edge locations
- **Monitoring**: CloudWatch integration with alerts

## Multi-Cloud Strategy

Choose based on your requirements:
- **Cost Predictability**: AWS CloudFront flat-rate plans
- **Azure Integration**: Azure CDN with existing Azure services
- **Global Reach**: AWS CloudFront (largest edge network)
- **Hybrid Setup**: Multiple providers for redundancy
