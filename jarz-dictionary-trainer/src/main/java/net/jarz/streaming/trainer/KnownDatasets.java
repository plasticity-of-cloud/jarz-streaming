package net.jarz.streaming.trainer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Known datasets for dictionary training.
 * Provides paths to common JDK installations and popular framework JARs.
 */
public final class KnownDatasets {
    
    /**
     * Create training corpus from JDK installation.
     */
    public static TrainingCorpus fromJdkHome(String jdkHome) {
        Path jdkPath = Paths.get(jdkHome);
        
        return TrainingCorpus.builder()
            .jdkModules(List.of(
                jdkPath.resolve("jmods"),           // JDK modules
                jdkPath.resolve("lib"),             // JDK libraries
                jdkPath.resolve("lib/modules")      // Module images
            ))
            .maxPerCategory(2000)
            .maxTotal(5000)
            .build();
    }
    
    /**
     * Create training corpus from Maven local repository.
     */
    public static TrainingCorpus fromMavenRepository(String mavenHome) {
        Path mavenRepo = Paths.get(mavenHome, ".m2", "repository");
        
        return TrainingCorpus.builder()
            .frameworkJars(List.of(
                // Spring Framework
                mavenRepo.resolve("org/springframework"),
                // Jackson JSON
                mavenRepo.resolve("com/fasterxml/jackson"),
                // Apache Commons
                mavenRepo.resolve("org/apache/commons"),
                // Google Guava
                mavenRepo.resolve("com/google/guava"),
                // Hibernate
                mavenRepo.resolve("org/hibernate"),
                // Logback
                mavenRepo.resolve("ch/qos/logback"),
                // JUnit
                mavenRepo.resolve("org/junit"),
                // Mockito
                mavenRepo.resolve("org/mockito")
            ))
            .maxPerCategory(500)
            .maxTotal(3000)
            .build();
    }
    
    /**
     * Create comprehensive training corpus combining JDK and popular frameworks.
     */
    public static TrainingCorpus comprehensive(String jdkHome, String mavenHome) {
        Path jdkPath = Paths.get(jdkHome);
        Path mavenRepo = Paths.get(mavenHome, ".m2", "repository");
        
        return TrainingCorpus.builder()
            .jdkModules(List.of(
                jdkPath.resolve("jmods"),
                jdkPath.resolve("lib")
            ))
            .frameworkJars(List.of(
                // Core frameworks (high priority)
                mavenRepo.resolve("org/springframework/spring-core"),
                mavenRepo.resolve("org/springframework/spring-context"),
                mavenRepo.resolve("org/springframework/boot/spring-boot"),
                mavenRepo.resolve("com/fasterxml/jackson/core/jackson-core"),
                mavenRepo.resolve("com/fasterxml/jackson/core/jackson-databind"),
                
                // Utility libraries
                mavenRepo.resolve("com/google/guava/guava"),
                mavenRepo.resolve("org/apache/commons/commons-lang3"),
                mavenRepo.resolve("org/apache/commons/commons-collections4"),
                
                // Logging
                mavenRepo.resolve("ch/qos/logback/logback-classic"),
                mavenRepo.resolve("org/slf4j/slf4j-api"),
                
                // Testing
                mavenRepo.resolve("org/junit/jupiter/junit-jupiter-api"),
                mavenRepo.resolve("org/mockito/mockito-core"),
                
                // Data access
                mavenRepo.resolve("org/hibernate/hibernate-core"),
                mavenRepo.resolve("org/springframework/data/spring-data-jpa")
            ))
            .maxPerCategory(1000)
            .maxTotal(8000)
            .build();
    }
    
    /**
     * Create Spring Boot focused training corpus.
     */
    public static TrainingCorpus springBootFocused(String mavenHome) {
        Path mavenRepo = Paths.get(mavenHome, ".m2", "repository");
        
        return TrainingCorpus.builder()
            .frameworkJars(List.of(
                // Spring Boot starters
                mavenRepo.resolve("org/springframework/boot/spring-boot-starter"),
                mavenRepo.resolve("org/springframework/boot/spring-boot-starter-web"),
                mavenRepo.resolve("org/springframework/boot/spring-boot-starter-data-jpa"),
                mavenRepo.resolve("org/springframework/boot/spring-boot-starter-security"),
                mavenRepo.resolve("org/springframework/boot/spring-boot-starter-test"),
                
                // Spring Framework core
                mavenRepo.resolve("org/springframework/spring-core"),
                mavenRepo.resolve("org/springframework/spring-beans"),
                mavenRepo.resolve("org/springframework/spring-context"),
                mavenRepo.resolve("org/springframework/spring-web"),
                mavenRepo.resolve("org/springframework/spring-webmvc"),
                
                // Common dependencies
                mavenRepo.resolve("com/fasterxml/jackson"),
                mavenRepo.resolve("org/hibernate/hibernate-core"),
                mavenRepo.resolve("org/apache/tomcat/embed")
            ))
            .maxPerCategory(800)
            .maxTotal(5000)
            .build();
    }
    
    /**
     * Create microservices focused training corpus.
     */
    public static TrainingCorpus microservicesFocused(String mavenHome) {
        Path mavenRepo = Paths.get(mavenHome, ".m2", "repository");
        
        return TrainingCorpus.builder()
            .frameworkJars(List.of(
                // Spring Cloud
                mavenRepo.resolve("org/springframework/cloud"),
                // Netflix OSS
                mavenRepo.resolve("com/netflix/eureka"),
                mavenRepo.resolve("com/netflix/hystrix"),
                // Micrometer metrics
                mavenRepo.resolve("io/micrometer/micrometer-core"),
                // Resilience4j
                mavenRepo.resolve("io/github/resilience4j"),
                // OpenFeign
                mavenRepo.resolve("org/springframework/cloud/spring-cloud-openfeign-core"),
                // Config
                mavenRepo.resolve("org/springframework/cloud/spring-cloud-config-client")
            ))
            .maxPerCategory(600)
            .maxTotal(4000)
            .build();
    }
    
    /**
     * Get default JDK home from system properties.
     */
    public static String getDefaultJdkHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            return javaHome;
        }
        
        // Fallback to JAVA_HOME environment variable
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null) {
            return javaHomeEnv;
        }
        
        throw new IllegalStateException("Cannot determine JDK home. Set JAVA_HOME or java.home");
    }
    
    /**
     * Get default Maven home from system properties.
     */
    public static String getDefaultMavenHome() {
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            return userHome;
        }
        
        throw new IllegalStateException("Cannot determine user home directory");
    }
}
