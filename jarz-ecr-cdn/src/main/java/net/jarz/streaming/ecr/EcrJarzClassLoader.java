package net.jarz.streaming.ecr;

import net.jarz.streaming.classloader.JarzClassLoader;
import net.jarz.streaming.v2.EcrJarzDataProvider;
import net.jarz.streaming.v2.EcrHybridJarzDataProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ECR-based JARZ ClassLoader using Maven coordinates with bundle index support.
 * 
 * <p>This ClassLoader extends the unified JarzClassLoader with ECR data provider
 * for efficient streaming access to JARZ archives stored as OCI artifacts in ECR.
 * 
 * <p>Supports Maven coordinate resolution and bundle index for O(1) class lookup
 * across multiple ECR-hosted JARZ artifacts.
 * 
 * <p>Uses HTTP/1.1 optimized client for ECR compatibility and respects ECR API
 * rate limits (200 requests/second per client).
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class EcrJarzClassLoader extends JarzClassLoader {
    
    private final EcrJarzClient ecrClient;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String repository;
    private final String tag;
    
    /**
     * Creates ECR ClassLoader with bundle index support.
     * 
     * @param groupId Maven group ID (e.g., "org.springframework.boot")
     * @param artifactId Maven artifact ID (e.g., "spring-boot-starter-web")
     * @param version Maven version (e.g., "2.7.0")
     * @param bundleIndexPath path to bundle index file (optional)
     */
    public EcrJarzClassLoader(String groupId, String artifactId, String version, Path bundleIndexPath) throws IOException {
        this(groupId, artifactId, version, bundleIndexPath, Thread.currentThread().getContextClassLoader());
    }
    
    /**
     * Creates ECR ClassLoader with bundle index support and custom parent.
     */
    public EcrJarzClassLoader(String groupId, String artifactId, String version, Path bundleIndexPath, ClassLoader parent) throws IOException {
        super(bundleIndexPath != null ? 
              new EcrHybridJarzDataProvider(groupId, artifactId, version, bundleIndexPath) : 
              new EcrJarzDataProvider(groupId, artifactId, version), 
              parent, bundleIndexPath);
        
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.repository = MavenEcrMapper.getEcrRepository();
        this.tag = MavenEcrMapper.toEcrTag(groupId, artifactId, version);
        
        // Initialize ECR client with region from environment
        String region = System.getenv("AWS_REGION");
        if (region == null) {
            region = "us-east-1"; // Default region
        }
        String accountId = System.getenv("AWS_ACCOUNT_ID");
        this.ecrClient = new EcrJarzClient(region, accountId);
    }
    
    /**
     * Creates an ECR ClassLoader for the specified Maven artifact (backward compatibility).
     */
    public EcrJarzClassLoader(String groupId, String artifactId, String version) throws IOException {
        this(groupId, artifactId, version, (Path) null);
    }
    
    /**
     * Creates an ECR ClassLoader with custom parent ClassLoader (backward compatibility).
     */
    public EcrJarzClassLoader(String groupId, String artifactId, String version, ClassLoader parent) throws IOException {
        this(groupId, artifactId, version, (Path) null, parent);
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return repository + ":" + tag;
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        // Parse Maven coordinates from child URL
        // Format: groupId:artifactId:version
        String[] parts = jarzUrl.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid Maven coordinate format: " + jarzUrl);
        }
        
        return new EcrJarzClassLoader(parts[0], parts[1], parts[2], (Path) null); // No bundle index for children
    }
    
    /**
     * Gets the Maven coordinates for this ClassLoader.
     * 
     * @return Maven coordinates as "groupId:artifactId:version"
     */
    public String getMavenCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }
    
    /**
     * Gets the ECR repository name.
     * 
     * @return ECR repository name
     */
    public String getEcrRepository() {
        return repository;
    }
    
    /**
     * Gets the ECR tag.
     * 
     * @return ECR tag
     */
    public String getEcrTag() {
        return tag;
    }
    
    @Override
    public void close() throws IOException {
        super.close();
        // ECR client doesn't need explicit cleanup (uses shared HTTP client)
    }
}
