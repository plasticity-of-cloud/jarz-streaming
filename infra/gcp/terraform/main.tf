# JARZ CDN Infrastructure - Google Cloud CDN with GCS origin

variable "project_id" {
  description = "GCP Project ID"
  type        = string
}

variable "bucket_name" {
  description = "GCS bucket name containing JARZ archives"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}

# Backend bucket for GCS origin
resource "google_compute_backend_bucket" "jarz_backend" {
  name        = "jarz-backend-bucket"
  description = "Backend bucket for JARZ archives"
  bucket_name = var.bucket_name
  enable_cdn  = true

  cdn_policy {
    cache_mode        = "CACHE_ALL_STATIC"
    default_ttl       = 86400      # 24 hours
    max_ttl           = 31536000   # 1 year
    client_ttl        = 86400
    negative_caching  = true
    
    cache_key_policy {
      include_http_headers = ["Range"]
    }
  }
}

# URL map
resource "google_compute_url_map" "jarz_url_map" {
  name            = "jarz-url-map"
  default_service = google_compute_backend_bucket.jarz_backend.id
}

# HTTPS proxy
resource "google_compute_target_https_proxy" "jarz_https_proxy" {
  name    = "jarz-https-proxy"
  url_map = google_compute_url_map.jarz_url_map.id
  ssl_certificates = [google_compute_managed_ssl_certificate.jarz_cert.id]
}

# Managed SSL certificate (requires custom domain)
resource "google_compute_managed_ssl_certificate" "jarz_cert" {
  name = "jarz-ssl-cert"
  
  managed {
    domains = ["jarz.${var.project_id}.example.com"]  # Replace with your domain
  }
}

# Global forwarding rule (external IP)
resource "google_compute_global_forwarding_rule" "jarz_forwarding_rule" {
  name       = "jarz-forwarding-rule"
  target     = google_compute_target_https_proxy.jarz_https_proxy.id
  port_range = "443"
  ip_protocol = "TCP"
}

# Reserve static IP
resource "google_compute_global_address" "jarz_ip" {
  name = "jarz-cdn-ip"
}

output "cdn_ip_address" {
  description = "CDN IP address"
  value       = google_compute_global_address.jarz_ip.address
}

output "jarz_base_url" {
  description = "Base URL for JARZ archives (configure DNS to point to cdn_ip_address)"
  value       = "https://jarz.${var.project_id}.example.com"
}
