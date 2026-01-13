# JARZ CloudFront Distribution Setup

Production-ready CloudFront distribution with flat-rate pricing for JARZ file distribution.

## Architecture

```
Internet → CloudFront (cdn.domain.com) → S3 Bucket (jarz-files)
           ↓
         AWS WAF (Security)
           ↓  
       Route 53 (DNS)
```

## Flat-Rate Pricing Plans

### Plan Selection Guide

| Plan | Monthly Cost | Requests | Data Transfer | Best For |
|------|-------------|----------|---------------|----------|
| **Free** | $0 | 1M | 100GB | Development, testing |
| **Pro** | $15 | 10M | 1TB | Small production apps |
| **Business** | $200 | 100M | 10TB | Enterprise applications |
| **Premium** | $1000 | 1B | 100TB | High-traffic platforms |

### Included Features (All Plans)
- Global CDN (750+ edge locations)
- AWS WAF protection
- DDoS protection (AWS Shield)
- Route 53 DNS management
- CloudWatch Logs ingestion
- Free TLS certificates
- S3 storage credits

## JARZ-Specific Optimizations

### Range Request Support
- Forwards `Range`, `If-Range`, `If-Modified-Since` headers
- Enables S3 block-based streaming for JARZ v2 format
- Supports HTTP 206 partial content responses

### Caching Strategy
- **JARZ files (*.jarz)**: 1 year TTL, no compression (already compressed)
- **Index files**: 24 hours TTL
- **Metadata**: 1 hour TTL

### Security
- HTTPS-only for JARZ files
- WAF rules for common attacks
- Origin Access Control (OAC) for S3 security
- TLS 1.2+ minimum

## Deployment

### Prerequisites
- AWS CLI configured
- Domain registered (or available for registration)
- S3 bucket permissions

### One-Click Deployment
```bash
./deploy.sh your-domain.com Business
```

### Manual Steps
1. **Create S3 bucket and configure security**
2. **Request SSL certificate with DNS validation**
3. **Create CloudFront distribution with flat-rate plan**
4. **Configure WAF security rules**
5. **Set up Route 53 DNS records**
6. **Test JARZ file access**

## Configuration Files

- `cloudfront-config.json` - Distribution configuration with flat-rate plan
- `s3-bucket-policy.json` - S3 security policy with OAC
- `waf-config.json` - WAF security rules
- `route53-records.json` - DNS record configuration

## Monitoring & Alerts

- CloudWatch metrics for requests, errors, latency
- Usage alerts at 80% of plan allowance
- Security alerts for blocked attacks
- Performance monitoring for JARZ streaming

## Cost Optimization

- Free data transfer from S3 to CloudFront
- Reduced origin requests through caching
- No overage charges with flat-rate plans
- S3 storage credits included

## Support

- AWS Support included with Business+ plans
- 99.9% uptime SLA (Business+)
- 24/7 monitoring and alerting
