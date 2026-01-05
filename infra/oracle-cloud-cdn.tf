# Oracle Cloud CDN Infrastructure for JARZ ClassLoader
# Terraform configuration for Oracle Cloud Infrastructure (OCI)

terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 5.0"
    }
  }
}

variable "compartment_id" {
  description = "OCID of the compartment where resources will be created"
  type        = string
}

variable "bucket_name" {
  description = "Name for the Object Storage bucket to store JARZ files"
  type        = string
  default     = "jarz-archives"
}

variable "cdn_display_name" {
  description = "Display name for the CDN distribution"
  type        = string
  default     = "JARZ ClassLoader CDN"
}

variable "custom_domain" {
  description = "Custom domain name for the CDN (optional)"
  type        = string
  default     = ""
}

# Object Storage Namespace (automatically determined)
data "oci_objectstorage_namespace" "current" {
  compartment_id = var.compartment_id
}

# Object Storage Bucket for JARZ archives
resource "oci_objectstorage_bucket" "jarz_bucket" {
  compartment_id = var.compartment_id
  name           = var.bucket_name
  namespace      = data.oci_objectstorage_namespace.current.namespace
  
  access_type    = "NoPublicAccess"
  storage_tier   = "Standard"
  versioning     = "Enabled"
  
  # Enable auto-tiering for cost optimization
  auto_tiering = "InfrequentAccess"
  
  # CORS configuration for CDN access
  # Note: OCI Object Storage CORS is configured separately via API/CLI
}

# WAF Policy for CDN protection
resource "oci_waas_waas_policy" "jarz_cdn_policy" {
  compartment_id = var.compartment_id
  display_name   = "${var.cdn_display_name} WAF Policy"
  domain         = var.custom_domain != "" ? var.custom_domain : "${var.bucket_name}.${data.oci_objectstorage_namespace.current.namespace}.compat.objectstorage.${data.oci_identity_region_subscriptions.current.region_subscriptions[0].region_name}.oraclecloud.com"

  # Origin configuration pointing to Object Storage
  origins {
    label = "object-storage-origin"
    uri   = "https://${data.oci_objectstorage_namespace.current.namespace}.compat.objectstorage.${data.oci_identity_region_subscriptions.current.region_subscriptions[0].region_name}.oraclecloud.com"
    
    custom_headers {
      name  = "Host"
      value = "${var.bucket_name}.${data.oci_objectstorage_namespace.current.namespace}.compat.objectstorage.${data.oci_identity_region_subscriptions.current.region_subscriptions[0].region_name}.oraclecloud.com"
    }
  }

  # Policy configuration optimized for JARZ files
  policy_config {
    # Enable TLS 1.2+
    tls_protocols = ["TLS_V1_2", "TLS_V1_3"]
    
    # Enable HTTP/2
    is_https_enabled = true
    is_https_forced  = true
    
    # Optimize for range requests
    is_origin_compression_enabled = false  # JARZ files are already compressed
    is_response_buffering_enabled = false  # Enable streaming
    
    # Cache configuration
    is_cache_control_respected = true
    
    # Health check configuration
    health_checks {
      is_enabled                = true
      method                   = "GET"
      path                     = "/health"
      headers                  = {}
      expected_response_codes  = ["200"]
      is_response_text_check_enabled = false
    }
  }

  # Caching rules optimized for JARZ files
  waf_config {
    # Rate limiting
    access_rules {
      name   = "allow-range-requests"
      action = "ALLOW"
      criteria {
        condition = "URL_IS"
        value     = "*.jarz"
      }
    }
    
    # Block malicious requests
    access_rules {
      name   = "block-malicious"
      action = "BLOCK"
      criteria {
        condition = "USER_AGENT_IS"
        value     = "malicious-bot"
      }
    }
  }
}

# Get current region information
data "oci_identity_region_subscriptions" "current" {
  tenancy_id = var.compartment_id
}

# IAM Policy for CDN access to Object Storage
resource "oci_identity_policy" "jarz_cdn_policy" {
  compartment_id = var.compartment_id
  name           = "jarz-cdn-object-storage-policy"
  description    = "Policy allowing CDN to access JARZ Object Storage bucket"
  
  statements = [
    "Allow service objectstorage-${data.oci_identity_region_subscriptions.current.region_subscriptions[0].region_name} to manage object-family in compartment id ${var.compartment_id}",
    "Allow any-user to read objects in compartment id ${var.compartment_id} where request.principal.type='waasdomain'"
  ]
}

# Pre-authenticated request for programmatic access (optional)
resource "oci_objectstorage_preauthrequest" "jarz_upload_par" {
  namespace    = data.oci_objectstorage_namespace.current.namespace
  bucket       = oci_objectstorage_bucket.jarz_bucket.name
  name         = "jarz-upload-par"
  access_type  = "ObjectWrite"
  time_expires = timeadd(timestamp(), "8760h") # 1 year
  
  # Allow uploads to any object in the bucket
  object_name = "*"
}

# Outputs
output "bucket_name" {
  description = "Object Storage bucket name for JARZ files"
  value       = oci_objectstorage_bucket.jarz_bucket.name
}

output "bucket_namespace" {
  description = "Object Storage namespace"
  value       = data.oci_objectstorage_namespace.current.namespace
}

output "cdn_domain" {
  description = "CDN domain name for JARZ files"
  value       = oci_waas_waas_policy.jarz_cdn_policy.domain
}

output "cdn_cname" {
  description = "CDN CNAME for custom domain setup"
  value       = oci_waas_waas_policy.jarz_cdn_policy.cname
}

output "jarz_url_base" {
  description = "Base URL for JARZ files"
  value       = "https://${oci_waas_waas_policy.jarz_cdn_policy.domain}"
}

output "upload_par_url" {
  description = "Pre-authenticated request URL for uploading JARZ files"
  value       = "https://${data.oci_objectstorage_namespace.current.namespace}.compat.objectstorage.${data.oci_identity_region_subscriptions.current.region_subscriptions[0].region_name}.oraclecloud.com${oci_objectstorage_preauthrequest.jarz_upload_par.access_uri}"
  sensitive   = true
}

output "example_usage" {
  description = "Example Java code for using this Oracle Cloud CDN"
  value = <<-EOT
    // Java code example for Oracle Cloud CDN:
    String jarzUrl = "https://${oci_waas_waas_policy.jarz_cdn_policy.domain}/app.jarz";
    try (CdnJarzClassLoader loader = new CdnJarzClassLoader(jarzUrl)) {
        Class<?> clazz = loader.loadClass("com.example.MyClass");
    }
  EOT
}
