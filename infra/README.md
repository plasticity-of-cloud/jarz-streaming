# JARZ CDN Infrastructure

This folder contains Infrastructure as Code (IaC) templates for deploying CDN infrastructure to serve JARZ archives across different cloud providers.

## Structure

```
infra/
├── aws/
│   ├── cloudformation/
│   │   └── jarz-cdn-stack.yaml      # CloudFront + S3 origin
│   └── terraform/
│       └── main.tf                   # (TODO) Terraform alternative
├── azure/
│   ├── arm/
│   │   └── jarz-cdn-template.json   # Front Door + Blob Storage
│   └── terraform/
│       └── main.tf                   # (TODO) Terraform alternative
└── gcp/
    └── terraform/
        └── main.tf                   # Cloud CDN + GCS
```

## Quick Start

### AWS CloudFront

```bash
aws cloudformation deploy \
  --template-file aws/cloudformation/jarz-cdn-stack.yaml \
  --stack-name jarz-cdn \
  --parameter-overrides BucketName=my-jarz-bucket
```

### Azure Front Door

```bash
az deployment group create \
  --resource-group my-rg \
  --template-file azure/arm/jarz-cdn-template.json \
  --parameters storageAccountName=myjarzaccount frontDoorName=my-jarz-cdn
```

### Google Cloud CDN

```bash
cd gcp/terraform
terraform init
terraform apply -var="project_id=my-project" -var="bucket_name=my-jarz-bucket"
```

## Usage with CdnJarzClassLoader

After deploying, use the output URL with the ClassLoader:

```java
// AWS (from CloudFormation output: JarzBaseUrl)
new CdnJarzClassLoader("https://d1234abcd.cloudfront.net/app.jarz");

// Azure (from ARM output: jarzBaseUrl)
new CdnJarzClassLoader("https://my-jarz-cdn-endpoint.azurefd.net/app.jarz");

// GCP (configure DNS to point to cdn_ip_address)
new CdnJarzClassLoader("https://jarz.my-project.example.com/app.jarz");
```

## Features by Provider

| Feature | AWS CloudFront | Azure Front Door | GCP Cloud CDN |
|---------|---------------|------------------|---------------|
| HTTP/2 | ✅ | ✅ | ✅ |
| HTTP/3 (QUIC) | ✅ | ✅ | ✅ |
| Range request caching | ✅ | ✅ | ✅ |
| Flat-rate pricing | ✅ Savings Bundle | ❌ | ❌ |
| Edge locations | 600+ | 192+ | 200+ |

## Cost Optimization

### AWS CloudFront Security Savings Bundle

For high-volume JARZ streaming, consider the CloudFront Security Savings Bundle:

| Commitment | Monthly Cost | Included |
|------------|--------------|----------|
| 1 TB/month | $35 | Data transfer + requests |
| 10 TB/month | $300 | Data transfer + requests |

This provides predictable costs compared to pay-per-request pricing.
