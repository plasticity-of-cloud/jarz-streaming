# Azure CDN for JARZ Distribution

Azure Front Door configuration for JARZ archive streaming with Blob Storage origin.

## Architecture

```
Internet → Azure Front Door → Blob Storage (JARZ files)
           ↓
         WAF Rules
           ↓
       Custom Domain
```

## Features

- **Azure Front Door Standard**: Global CDN with edge locations
- **Blob Storage Origin**: Secure storage for JARZ archives
- **Range Request Support**: Optimized for JARZ block streaming
- **Custom Domain**: Support for cdn.your-domain.com
- **WAF Protection**: Built-in security rules
- **SSL/TLS**: Automatic certificate management

## Deployment

### Prerequisites
- Azure CLI installed and configured
- Resource Group created
- Storage Account with Blob container
- Domain name for custom endpoint

### Quick Deploy
```bash
# Create resource group
az group create --name jarz-rg --location eastus

# Deploy ARM template
az deployment group create \
  --resource-group jarz-rg \
  --template-file jarz-cdn-template.json \
  --parameters storageAccountName=jarzstorageXXX \
               frontDoorName=jarz-cdn \
               containerName=jarz
```

### Manual Steps
1. **Create Storage Account**
2. **Upload JARZ files to Blob container**
3. **Deploy Front Door with ARM template**
4. **Configure custom domain**
5. **Test JARZ file access**

## Configuration

### Storage Account Setup
```bash
# Create storage account
az storage account create \
  --name jarzstorageXXX \
  --resource-group jarz-rg \
  --location eastus \
  --sku Standard_LRS

# Create container
az storage container create \
  --name jarz \
  --account-name jarzstorageXXX \
  --public-access off
```

### Upload JARZ Files
```bash
# Upload JARZ archive
az storage blob upload \
  --file myapp.jarz \
  --container-name jarz \
  --name myapp.jarz \
  --account-name jarzstorageXXX
```

## Testing

### Basic Connectivity
```bash
# Test Front Door endpoint
curl -I https://jarz-cdn-endpoint.azurefd.net/myapp.jarz
```

### Range Request Test
```bash
# Test JARZ block streaming
curl -H "Range: bytes=0-1023" https://jarz-cdn-endpoint.azurefd.net/myapp.jarz
```

## Cost Comparison

| Feature | Azure Front Door | AWS CloudFront |
|---------|------------------|----------------|
| **Pricing Model** | Pay-per-use | Flat-rate available |
| **Data Transfer** | $0.087/GB | Included in plans |
| **Requests** | $0.0075/10K | Included in plans |
| **WAF** | $1.25/policy | Included in plans |

## Limitations

- **No Flat-Rate Plans**: Azure uses pay-per-use pricing
- **Higher Costs**: More expensive than AWS flat-rate plans
- **Less Edge Locations**: Smaller global network than AWS

## Migration to AWS

For cost optimization, consider migrating to AWS CloudFront:

```bash
# Export JARZ files from Azure Blob
az storage blob download-batch \
  --destination ./jarz-files \
  --source jarz \
  --account-name jarzstorageXXX

# Deploy to AWS CloudFront
cd ../aws/cloudfront-dist
./deploy.sh your-domain.com Business

# Upload to S3
aws s3 sync ./jarz-files s3://your-jarz-bucket/
```

## Support

- Azure Front Door documentation
- Azure Blob Storage documentation
- JARZ project documentation in `docs/` folder
