package jdk.incubator.jarz.v2;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Hybrid ECR JARZ data provider with bundle index support.
 * 
 * <p>This provider combines ECR streaming with local bundle index for O(1)
 * class lookup across multiple ECR-hosted JARZ artifacts.
 * 
 * <p>The bundle index eliminates sequential search through multiple JARZ files,
 * providing consistent performance regardless of classpath size.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class EcrHybridJarzDataProvider extends EcrJarzDataProvider {
    
    private final Path bundleIndexPath;
    
    /**
     * Creates hybrid ECR data provider with bundle index.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID
     * @param version Maven version
     * @param bundleIndexPath path to bundle index file
     */
    public EcrHybridJarzDataProvider(String groupId, String artifactId, String version, Path bundleIndexPath) throws IOException {
        super(groupId, artifactId, version);
        this.bundleIndexPath = bundleIndexPath;
    }
    
    /**
     * Gets the bundle index path.
     * 
     * @return bundle index path
     */
    public Path getBundleIndexPath() {
        return bundleIndexPath;
    }
}
