#!/bin/bash

# Continue CloudFront deployment with validated certificate
set -euo pipefail

DOMAIN_NAME="jarz-streaming.net"
BUCKET_NAME="jarz-distribution-1768174356"
CERT_ARN="arn:aws:acm:us-east-1:864899852480:certificate/0539b099-8316-4ab7-a94b-dae1fdf9ebef"
OAC_ID="E2BO51BFN5FY38"
REGION="us-east-1"

echo "🌍 Creating CloudFront distribution with Free plan"

# Generate distribution configuration
cat > cloudfront-distribution-config.json << EOF
{
  "CallerReference": "jarz-dist-$(date +%s)",
  "Aliases": {
    "Quantity": 1,
    "Items": ["cdn.$DOMAIN_NAME"]
  },
  "DefaultRootObject": "index.html",
  "Comment": "JARZ Distribution CDN - Free Plan",
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

echo "✅ CloudFront distribution created: $DISTRIBUTION_ID"
echo "📍 Distribution domain: $DISTRIBUTION_DOMAIN"

# Update S3 bucket policy for OAC
echo "🔐 Updating S3 bucket policy for Origin Access Control"
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

# Create Route 53 records
echo "🌐 Setting up DNS records"
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
    --hosted-zone-id "Z01031062X0ZXD2WCNTTS" \
    --change-batch file://route53-change-batch.json

echo "✅ DNS record created for cdn.$DOMAIN_NAME"

# Upload test file
echo "📤 Uploading test JARZ file"
echo "Test JARZ content for validation" > test.jarz
aws s3 cp test.jarz "s3://$BUCKET_NAME/test.jarz"
rm test.jarz

echo ""
echo "🎉 JARZ CloudFront Distribution Deployment Complete!"
echo ""
echo "📋 Deployment Summary:"
echo "  Domain: cdn.$DOMAIN_NAME"
echo "  Distribution ID: $DISTRIBUTION_ID"
echo "  S3 Bucket: $BUCKET_NAME"
echo "  Pricing Plan: Free (1M requests, 100GB transfer)"
echo ""
echo "🔗 Next Steps:"
echo "  1. Wait for distribution deployment (~15-20 minutes)"
echo "  2. Test: curl -I https://cdn.$DOMAIN_NAME/test.jarz"
echo "  3. Upload JARZ files: aws s3 cp myapp.jarz s3://$BUCKET_NAME/"
echo ""
echo "💰 Free Tier Limits:"
echo "  - 1M requests per month"
echo "  - 100GB data transfer per month"
echo "  - No overage charges (requests blocked after limit)"
