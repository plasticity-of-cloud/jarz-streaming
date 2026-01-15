package net.jarz.streaming.ecr;

/**
 * Maven coordinate to ECR repository name converter.
 * 
 * <p>Uses a single ECR repository with encoded tags to avoid
 * the 10,000 repository limit and reduce management overhead.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class MavenEcrMapper {
    
    private static final String MAVEN_REPOSITORY = "maven-artifacts";
    
    /**
     * Gets the single ECR repository name for all Maven artifacts.
     * 
     * @return ECR repository name ("maven-artifacts")
     */
    public static String getEcrRepository() {
        return MAVEN_REPOSITORY;
    }
    
    /**
     * Converts Maven coordinates to ECR tag.
     * 
     * @param groupId Maven group ID (e.g., "com.plasticity.cloud")
     * @param artifactId Maven artifact ID (e.g., "jarz-streaming")
     * @param version Maven version (e.g., "1.0.0")
     * @return ECR tag (e.g., "com.plasticity.cloud--jarz-streaming--1.0.0")
     */
    public static String toEcrTag(String groupId, String artifactId, String version) {
        return groupId.replace(".", "_") + "--" + artifactId + "--" + version;
    }
    
    /**
     * Parses ECR tag back to Maven coordinates.
     * 
     * @param ecrTag ECR tag
     * @return Maven coordinates as [groupId, artifactId, version]
     */
    public static String[] fromEcrTag(String ecrTag) {
        String[] parts = ecrTag.split("--");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid ECR tag format: " + ecrTag);
        }
        
        String groupId = parts[0].replace("_", ".");
        String artifactId = parts[1];
        String version = parts[2];
        return new String[]{groupId, artifactId, version};
    }
}
}
