#!/bin/bash

# JARZ CloudFront Distribution Deployment Script
# Production-ready deployment with flat-rate pricing plans

set -euo pipefail

# Configuration
DOMAIN_NAME="${1:-}"
PRICING_PLAN="${2:-Business}"
REGION="us-east-1"
BUCKET_NAME="jarz-distribution-$(date +%s)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "${BLUE}[$(date +'%Y-%m-%d %H:%M:%S')]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
    exit 1
}

success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Validate inputs
if [[ -z "$DOMAIN_NAME" ]]; then
    error "Usage: $0 <domain-name> [pricing-plan]
    
Available pricing plans:
- Free (default for testing)
- Pro ($15/month)
- Business ($200/month) - Recommended
- Premium ($1000/month)

Example: $0 jarz.io Business"
fi

# Validate pricing plan
case "$PRICING_PLAN" in
    Free|Pro|Business|Premium)
        log "Using $PRICING_PLAN pricing plan"
        ;;
    *)
        error "Invalid pricing plan: $PRICING_PLAN. Use: Free, Pro, Business, or Premium"
        ;;
esac

log "🚀 Starting JARZ CloudFront Distribution Deployment"
log "Domain: $DOMAIN_NAME"
log "Pricing Plan: $PRICING_PLAN"
log "Region: $REGION"

# Check AWS CLI
if ! command -v aws &> /dev/null; then
    error "AWS CLI not found. Please install and configure AWS CLI."
fi

# Check AWS credentials
if ! aws sts get-caller-identity &> /dev/null; then
    error "AWS credentials not configured. Run 'aws configure' first."
fi

# Step 1: Create S3 bucket with security
log "📦 Creating S3 bucket: $BUCKET_NAME"
aws s3 mb "s3://$BUCKET_NAME" --region "$REGION"

# Enable versioning
log "📝 Enabling S3 versioning"
aws s3api put-bucket-versioning \
    --bucket "$BUCKET_NAME" \
    --versioning-configuration Status=Enabled

# Block public access
log "🔒 Blocking public access to S3 bucket"
aws s3api put-public-access-block \
    --bucket "$BUCKET_NAME" \
    --public-access-block-configuration \
    BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Configure CORS for range requests
log "🌐 Configuring S3 CORS for JARZ streaming"
cat > s3-cors-config.json << 'EOF'
{
  "CORSRules": [
    {
      "AllowedHeaders": ["Range", "If-Range", "If-Modified-Since", "Authorization"],
      "AllowedMethods": ["GET", "HEAD"],
      "AllowedOrigins": ["*"],
      "ExposeHeaders": ["Content-Range", "Content-Length", "Accept-Ranges", "ETag", "Last-Modified"],
      "MaxAgeSeconds": 3600
    }
  ]
}
EOF

aws s3api put-bucket-cors \
    --bucket "$BUCKET_NAME" \
    --cors-configuration file://s3-cors-config.json

# Step 2: Request SSL certificate
log "🔐 Requesting SSL certificate for $DOMAIN_NAME"
CERT_ARN=$(aws acm request-certificate \
    --domain-name "$DOMAIN_NAME" \
    --subject-alternative-names "*.$DOMAIN_NAME" \
    --validation-method DNS \
    --region "$REGION" \
    --query 'CertificateArn' \
    --output text)

success "Certificate requested: $CERT_ARN"
warn "⚠️  Please validate the certificate via DNS before the distribution becomes fully functional"

# Step 3: Create Origin Access Control (OAC)
log "🔑 Creating Origin Access Control"
OAC_ID=$(aws cloudfront create-origin-access-control \
    --origin-access-control-config '{
        "Name": "jarz-oac-'$(date +%s)'",
        "Description": "Origin Access Control for JARZ distribution",
        "OriginAccessControlOriginType": "s3",
        "SigningBehavior": "always",
        "SigningProtocol": "sigv4"
    }' \
    --query 'OriginAccessControl.Id' \
    --output text)

success "Origin Access Control created: $OAC_ID"

# Step 4: Create CloudFront distribution with flat-rate plan
log "🌍 Creating CloudFront distribution with $PRICING_PLAN plan"

# Generate distribution configuration
cat > cloudfront-distribution-config.json << EOF
{
  "CallerReference": "jarz-dist-$(date +%s)",
  "Aliases": {
    "Quantity": 1,
    "Items": ["cdn.$DOMAIN_NAME"]
  },
  "DefaultRootObject": "index.html",
  "Comment": "JARZ Distribution CDN - $PRICING_PLAN Plan",
  "Enabled": true,
  "Origins": {
    "Quantity": 1,
    "Items": [
      {
        "Id": "jarz-s3-origin",
        "DomainName": "$BUCKET_NAME.s3.amazonaws.com",
        "OriginAccessControlId": "$OAC_ID",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "jarz-s3-origin",
    "ViewerProtocolPolicy": "redirect-to-https",
    "MinTTL": 0,
    "DefaultTTL": 86400,
    "MaxTTL": 31536000,
    "ForwardedValues": {
      "QueryString": false,
      "Cookies": {"Forward": "none"}
    },
    "TrustedSigners": {
      "Enabled": false,
      "Quantity": 0
    },
    "Compress": true
  },
  "CacheBehaviors": {
    "Quantity": 1,
    "Items": [
      {
        "PathPattern": "*.jarz",
        "TargetOriginId": "jarz-s3-origin",
        "ViewerProtocolPolicy": "https-only",
        "MinTTL": 0,
        "DefaultTTL": 31536000,
        "MaxTTL": 31536000,
        "ForwardedValues": {
          "QueryString": false,
          "Cookies": {"Forward": "none"},
          "Headers": {
            "Quantity": 3,
            "Items": ["Range", "If-Range", "If-Modified-Since"]
          }
        },
        "TrustedSigners": {
          "Enabled": false,
          "Quantity": 0
        },
        "Compress": false
      }
    ]
  },
  "ViewerCertificate": {
    "CloudFrontDefaultCertificate": false,
    "ACMCertificateArn": "$CERT_ARN",
    "SSLSupportMethod": "sni-only",
    "MinimumProtocolVersion": "TLSv1.2_2021"
  },
  "PriceClass": "PriceClass_All"
}
EOF

# Create distribution
DISTRIBUTION_OUTPUT=$(aws cloudfront create-distribution \
    --distribution-config file://cloudfront-distribution-config.json)

DISTRIBUTION_ID=$(echo "$DISTRIBUTION_OUTPUT" | jq -r '.Distribution.Id')
DISTRIBUTION_DOMAIN=$(echo "$DISTRIBUTION_OUTPUT" | jq -r '.Distribution.DomainName')

success "CloudFront distribution created: $DISTRIBUTION_ID"
log "Distribution domain: $DISTRIBUTION_DOMAIN"

# Step 5: Subscribe to flat-rate pricing plan
log "💳 Subscribing to $PRICING_PLAN pricing plan"
aws cloudfront put-distribution-pricing-plan \
    --distribution-id "$DISTRIBUTION_ID" \
    --pricing-plan "$PRICING_PLAN" || warn "Pricing plan subscription may need manual setup in console"

# Step 6: Update S3 bucket policy for OAC
log "🔐 Updating S3 bucket policy for Origin Access Control"
cat > s3-bucket-policy.json << EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipal",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::$BUCKET_NAME/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::$(aws sts get-caller-identity --query Account --output text):distribution/$DISTRIBUTION_ID"
        }
      }
    }
  ]
}
EOF

aws s3api put-bucket-policy \
    --bucket "$BUCKET_NAME" \
    --policy file://s3-bucket-policy.json

# Step 7: Create Route 53 records (if hosted zone exists)
log "🌐 Setting up DNS records"
HOSTED_ZONE_ID=$(aws route53 list-hosted-zones-by-name \
    --dns-name "$DOMAIN_NAME" \
    --query "HostedZones[?Name=='$DOMAIN_NAME.'].Id" \
    --output text | cut -d'/' -f3 || echo "")

if [[ -n "$HOSTED_ZONE_ID" ]]; then
    log "Found hosted zone: $HOSTED_ZONE_ID"
    
    cat > route53-change-batch.json << EOF
{
  "Comment": "Create A record for cdn.$DOMAIN_NAME",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "cdn.$DOMAIN_NAME",
        "Type": "A",
        "AliasTarget": {
          "DNSName": "$DISTRIBUTION_DOMAIN",
          "EvaluateTargetHealth": false,
          "HostedZoneId": "Z2FDTNDATAQYW2"
        }
      }
    }
  ]
}
EOF

    aws route53 change-resource-record-sets \
        --hosted-zone-id "$HOSTED_ZONE_ID" \
        --change-batch file://route53-change-batch.json
    
    success "DNS record created for cdn.$DOMAIN_NAME"
else
    warn "No hosted zone found for $DOMAIN_NAME. Please create DNS records manually."
fi

# Step 8: Create CloudWatch alarms
log "📊 Setting up CloudWatch monitoring"
aws cloudwatch put-metric-alarm \
    --alarm-name "JARZ-Distribution-Errors-$DISTRIBUTION_ID" \
    --alarm-description "High error rate for JARZ distribution" \
    --metric-name "4xxErrorRate" \
    --namespace "AWS/CloudFront" \
    --statistic "Average" \
    --period 300 \
    --threshold 5.0 \
    --comparison-operator "GreaterThanThreshold" \
    --evaluation-periods 2 \
    --dimensions Name=DistributionId,Value="$DISTRIBUTION_ID" || warn "CloudWatch alarm creation failed"

# Step 9: Upload test file
log "📤 Uploading test JARZ file"
echo "Test JARZ content for validation" > test.jarz
aws s3 cp test.jarz "s3://$BUCKET_NAME/test.jarz"
rm test.jarz

# Cleanup temporary files
rm -f s3-cors-config.json cloudfront-distribution-config.json s3-bucket-policy.json route53-change-batch.json

success "✅ JARZ CloudFront Distribution Deployment Complete!"

echo ""
echo "📋 Deployment Summary:"
echo "  Domain: cdn.$DOMAIN_NAME"
echo "  Distribution ID: $DISTRIBUTION_ID"
echo "  S3 Bucket: $BUCKET_NAME"
echo "  Pricing Plan: $PRICING_PLAN"
echo "  Certificate ARN: $CERT_ARN"
echo ""
echo "🔗 Next Steps:"
echo "  1. Validate SSL certificate via DNS (check ACM console)"
echo "  2. Wait for distribution deployment (~15-20 minutes)"
echo "  3. Test: curl -I https://cdn.$DOMAIN_NAME/test.jarz"
echo "  4. Upload JARZ files: aws s3 cp myapp.jarz s3://$BUCKET_NAME/"
echo ""
echo "📊 Monitoring:"
echo "  CloudWatch: https://console.aws.amazon.com/cloudwatch/home?region=$REGION"
echo "  CloudFront: https://console.aws.amazon.com/cloudfront/home"
echo ""
echo "💰 Pricing Plan: $PRICING_PLAN"
echo "  No overage charges - predictable monthly costs"
echo "  Includes WAF, DDoS protection, and S3 credits"
