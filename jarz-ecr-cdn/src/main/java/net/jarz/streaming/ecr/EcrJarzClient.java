package net.jarz.streaming.ecr;

import com.google.gson.Gson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * HTTP client for Amazon ECR operations with JARZ artifact support.
 * 
 * <p>This client provides ECR-specific operations including authentication,
 * OCI artifact upload/download, and HTTP range requests for JARZ streaming.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class EcrJarzClient {
    
    private final HttpClient httpClient;
    private final Gson gson;
    private final String region;
    private final String accountId;
    
    /**
     * Creates ECR client for specified region and account.
     * 
     * @param region AWS region (e.g., "us-east-1")
     * @param accountId AWS account ID
     */
    public EcrJarzClient(String region, String accountId) {
        this.httpClient = EcrHttpClientProvider.getClient();
        this.gson = new Gson();
        this.region = region;
        this.accountId = accountId;
    }
    
    /**
     * Gets ECR authentication token for Docker operations.
     * 
     * @return base64-encoded authentication token
     * @throws IOException if authentication fails
     */
    public String getAuthToken() throws IOException {
        // Use AWS CLI or SDK for token - simplified for now
        String awsCliCommand = String.format(
            "aws ecr get-login-password --region %s", region);
        
        // In production, use AWS SDK or instance metadata
        throw new UnsupportedOperationException(
            "Use AWS SDK or CLI: " + awsCliCommand);
    }
    
    /**
     * Downloads JARZ block using HTTP range request.
     * 
     * @param repository ECR repository name
     * @param digest blob digest (sha256:...)
     * @param offset byte offset in blob
     * @param length number of bytes to read
     * @return requested byte range
     * @throws IOException if range request fails
     */
    public byte[] getJarzBlock(String repository, String digest, 
                              long offset, long length) throws IOException {
        
        String blobUrl = String.format(
            "https://%s.dkr.ecr.%s.amazonaws.com/v2/%s/blobs/%s",
            accountId, region, repository, digest);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(blobUrl))
            .header("Range", String.format("bytes=%d-%d", offset, offset + length - 1))
            .header("Authorization", "Bearer " + getAuthToken())
            .GET()
            .build();
        
        try {
            HttpResponse<byte[]> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 206) { // Partial Content
                return response.body();
            } else if (response.statusCode() == 200) { // Full content
                byte[] fullBody = response.body();
                if (offset + length <= fullBody.length) {
                    byte[] range = new byte[(int) length];
                    System.arraycopy(fullBody, (int) offset, range, 0, (int) length);
                    return range;
                }
            }
            
            throw new IOException("Range request failed: " + response.statusCode());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
    
    /**
     * Gets OCI manifest for JARZ artifact.
     * 
     * @param repository ECR repository name  
     * @param tag artifact tag or digest
     * @return OCI manifest
     * @throws IOException if manifest retrieval fails
     */
    public OciManifest getManifest(String repository, String tag) throws IOException {
        String manifestUrl = String.format(
            "https://%s.dkr.ecr.%s.amazonaws.com/v2/%s/manifests/%s",
            accountId, region, repository, tag);
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(manifestUrl))
            .header("Accept", "application/vnd.oci.image.manifest.v1+json")
            .header("Authorization", "Bearer " + getAuthToken())
            .GET()
            .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return gson.fromJson(response.body(), OciManifest.class);
            }
            
            throw new IOException("Manifest request failed: " + response.statusCode());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
    
    /**
     * Uploads JARZ artifact using Maven coordinates.
     * 
     * @param jarzFile path to JARZ file
     * @param groupId Maven group ID (e.g., "com.plasticity.cloud")
     * @param artifactId Maven artifact ID (e.g., "jarz-streaming") 
     * @param version Maven version (e.g., "1.0.0")
     * @return artifact digest
     * @throws IOException if upload fails
     */
    public String uploadMavenArtifact(Path jarzFile, String groupId, 
                                     String artifactId, String version) throws IOException {
        String repository = MavenEcrMapper.getEcrRepository();
        String tag = MavenEcrMapper.toEcrTag(groupId, artifactId, version);
        
        return uploadJarzArtifact(jarzFile, repository, tag);
    }
    
    /**
     * Downloads JARZ artifact using Maven coordinates.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @return OCI manifest for the artifact
     * @throws IOException if download fails
     */
    public OciManifest getMavenArtifact(String groupId, String artifactId, 
                                       String version) throws IOException {
        String repository = MavenEcrMapper.getEcrRepository();
        String tag = MavenEcrMapper.toEcrTag(groupId, artifactId, version);
        
        return getManifest(repository, tag);
    }
    public static class OciManifest {
        public int schemaVersion;
        public String mediaType;
        public Config config;
        public List<Layer> layers;
        
        public static class Config {
            public String mediaType;
            public String digest;
            public long size;
        }
        
        public static class Layer {
            public String mediaType;
            public String digest;
            public long size;
        }
    }
}
