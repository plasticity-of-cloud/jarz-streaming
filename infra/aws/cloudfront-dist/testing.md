# JARZ CloudFront Testing & Validation

## Quick Test Commands

### 1. Basic Connectivity Test
```bash
# Test CDN endpoint
curl -I https://cdn.your-domain.com/test.jarz

# Expected response: HTTP/2 200 with CloudFront headers
```

### 2. Range Request Test (JARZ Streaming)
```bash
# Test range request support
curl -H "Range: bytes=0-1023" https://cdn.your-domain.com/test.jarz

# Expected: HTTP/2 206 Partial Content
```

### 3. Performance Test
```bash
# Download speed test
time curl -o /dev/null https://cdn.your-domain.com/test.jarz

# Range request performance
time curl -H "Range: bytes=0-65535" -o block0.dat https://cdn.your-domain.com/test.jarz
```

## Validation Checklist

### ✅ SSL Certificate
```bash
# Check certificate status
aws acm describe-certificate --certificate-arn YOUR_CERT_ARN --region us-east-1

# Test SSL configuration
openssl s_client -connect cdn.your-domain.com:443 -servername cdn.your-domain.com
```

### ✅ CloudFront Distribution
```bash
# Check distribution status
aws cloudfront get-distribution --id YOUR_DISTRIBUTION_ID

# Wait for "Deployed" status before testing
```

### ✅ DNS Resolution
```bash
# Test DNS resolution
dig cdn.your-domain.com
nslookup cdn.your-domain.com

# Should resolve to CloudFront domain
```

### ✅ S3 Security
```bash
# Test direct S3 access (should be blocked)
curl -I https://your-bucket.s3.amazonaws.com/test.jarz

# Expected: 403 Forbidden (OAC working)
```

### ✅ WAF Protection
```bash
# Test rate limiting (run multiple times quickly)
for i in {1..10}; do curl -I https://cdn.your-domain.com/test.jarz; done

# Should eventually return 403 if rate limit exceeded
```

## Monitoring Commands

### CloudWatch Metrics
```bash
# Check request count
aws cloudwatch get-metric-statistics \
  --namespace AWS/CloudFront \
  --metric-name Requests \
  --dimensions Name=DistributionId,Value=YOUR_DISTRIBUTION_ID \
  --start-time 2026-01-11T00:00:00Z \
  --end-time 2026-01-11T23:59:59Z \
  --period 3600 \
  --statistics Sum

# Check error rate
aws cloudwatch get-metric-statistics \
  --namespace AWS/CloudFront \
  --metric-name 4xxErrorRate \
  --dimensions Name=DistributionId,Value=YOUR_DISTRIBUTION_ID \
  --start-time 2026-01-11T00:00:00Z \
  --end-time 2026-01-11T23:59:59Z \
  --period 3600 \
  --statistics Average
```

### Usage Monitoring
```bash
# Check flat-rate plan usage
aws cloudfront get-distribution-pricing-plan --distribution-id YOUR_DISTRIBUTION_ID
```

## Troubleshooting

### Common Issues

#### 1. 403 Forbidden Errors
**Cause**: Origin Access Control not configured properly
**Fix**: 
```bash
# Update S3 bucket policy
aws s3api put-bucket-policy --bucket YOUR_BUCKET --policy file://s3-bucket-policy.json
```

#### 2. SSL Certificate Issues
**Cause**: Certificate not validated
**Fix**: Check ACM console and complete DNS validation

#### 3. Range Requests Not Working
**Cause**: Headers not forwarded
**Fix**: Verify CloudFront cache behavior forwards Range headers

#### 4. High Latency
**Cause**: Origin Shield not enabled (Premium plan)
**Fix**: Enable Origin Shield in CloudFront console

### Debug Commands
```bash
# Check distribution configuration
aws cloudfront get-distribution-config --id YOUR_DISTRIBUTION_ID

# Check certificate validation
aws acm describe-certificate --certificate-arn YOUR_CERT_ARN --region us-east-1

# Test with verbose output
curl -v -H "Range: bytes=0-1023" https://cdn.your-domain.com/test.jarz
```

## Load Testing

### Simple Load Test
```bash
# Install Apache Bench
sudo apt-get install apache2-utils

# Run load test
ab -n 1000 -c 10 https://cdn.your-domain.com/test.jarz
```

### JARZ-Specific Test
```bash
# Test multiple range requests
for i in {0..9}; do
  start=$((i * 65536))
  end=$(((i + 1) * 65536 - 1))
  curl -H "Range: bytes=$start-$end" -o "block$i.dat" https://cdn.your-domain.com/test.jarz &
done
wait
```

## Success Criteria

- ✅ HTTPS certificate valid and trusted
- ✅ Range requests return HTTP 206
- ✅ Direct S3 access blocked (403)
- ✅ CloudFront headers present in responses
- ✅ DNS resolves to CloudFront domain
- ✅ WAF rules active and blocking malicious requests
- ✅ Flat-rate plan active with usage tracking
