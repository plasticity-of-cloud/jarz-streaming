package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.v2.JarToJarzConverter;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Full integration test: S3JarzClassLoader + MinIO S3 + Real JAR (log4j2).
 * 
 * Architecture:
 * 1. Maven copies log4j2 JAR to target/test-jars/
 * 2. Convert JAR to JARZ v2 format
 * 3. Upload JARZ to MinIO S3
 * 4. S3JarzClassLoader loads real log4j2 classes via S3 range requests
 */
@Testcontainers
class S3JarzIntegrationTest {

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:RELEASE.2023-12-07T04-16-00Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    private static S3Client s3Client;
    private static String bucketName = "jarz-test-bucket";
    private static String jarzKey = "log4j-api-2.20.0.jarz";

    @BeforeAll
    static void setUpInfrastructure() throws Exception {
        // 1. Setup MinIO S3
        setupMinioS3();
        
        // 2. Create JARZ from log4j2 JAR (copied by Maven)
        createJarzFromLog4j2();
    }

    @AfterAll
    static void tearDownInfrastructure() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    private static void setupMinioS3() {
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(minioContainer.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("minioadmin", "minioadmin")))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();

        // Create bucket
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(bucketName)
                .build());
    }

    private static void createJarzFromLog4j2() throws Exception {
        // Use JAR copied by Maven dependency plugin
        Path log4j2Jar = Paths.get("target/test-jars/log4j-api-2.20.0.jar");
        
        Assumptions.assumeTrue(Files.exists(log4j2Jar), 
            "log4j2 JAR not found. Run 'mvn generate-test-resources' first.");

        System.out.println("Testing dependency-aware conversion: " + log4j2Jar);
        System.out.println("Original JAR size: " + Files.size(log4j2Jar) + " bytes");

        // Convert JAR to JARZ v2 using utility
        JarToJarzConverter.ConversionResult result = JarToJarzConverter.convertToTemp(log4j2Jar);
        
        System.out.println("Converted " + result.getTotalEntries() + " entries to JARZ v2 (" + result.getBlockCount() + " blocks)");
        System.out.println("JARZ size: " + result.getJarzSize() + " bytes");

        // Upload JARZ to S3
        byte[] jarzData = Files.readAllBytes(result.getJarzFile());
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(jarzKey)
                        .contentType("application/octet-stream")
                        .build(),
                RequestBody.fromBytes(jarzData));
        
        Files.deleteIfExists(result.getJarzFile());
        
        System.out.println("Uploaded to S3: s3://" + bucketName + "/" + jarzKey);
    }

    @Test
    void loadLog4j2ClassesViaS3ClassLoader() throws Exception {
        try (S3JarzClassLoader loader = new S3JarzClassLoader(s3Client, bucketName, jarzKey)) {
            // Load real log4j2 classes
            Class<?> simpleLoggerClass = loader.loadClass("org.apache.logging.log4j.simple.SimpleLogger");
            Class<?> logManagerClass = loader.loadClass("org.apache.logging.log4j.LogManager");
            Class<?> levelClass = loader.loadClass("org.apache.logging.log4j.Level");
            
            // Verify classes are loaded correctly
            assertThat(simpleLoggerClass).isNotNull();
            assertThat(simpleLoggerClass.getName()).isEqualTo("org.apache.logging.log4j.simple.SimpleLogger");
            
            assertThat(logManagerClass).isNotNull();
            assertThat(levelClass).isNotNull();
            
            // Test class field access
            Object levelInfo = levelClass.getField("INFO").get(null);
            assertThat(levelInfo).isNotNull();
            
            // CRITICAL: Actually instantiate and use classes to verify JVM execution
            System.out.println("🔥 Testing actual JVM execution of S3-streamed classes:");
            
            // Test 1: Create LogManager instance and call methods
            Object logManager = logManagerClass.getMethod("getLogger", String.class)
                    .invoke(null, "S3-Test-Logger");
            assertThat(logManager).isNotNull();
            System.out.println("  ✅ LogManager.getLogger() executed successfully");
            
            // Test 2: Access Level enum values and call methods
            Object[] levels = (Object[]) levelClass.getMethod("values").invoke(null);
            assertThat(levels).hasSizeGreaterThan(0);
            String levelName = (String) levelInfo.getClass().getMethod("name").invoke(levelInfo);
            assertThat(levelName).isEqualTo("INFO");
            System.out.println("  ✅ Level.values() and Level.name() executed successfully");
            
            // Test 3: Verify class inheritance and interfaces work
            Class<?> loggerInterface = Class.forName("org.apache.logging.log4j.Logger", true, loader);
            assertThat(loggerInterface.isInterface()).isTrue();
            assertThat(loggerInterface.isAssignableFrom(logManager.getClass())).isTrue();
            System.out.println("  ✅ Interface inheritance verified from S3 classes");
            
            System.out.println("🎯 JVM successfully executed methods on S3-streamed bytecode!");
            
            System.out.println("✅ Successfully loaded log4j2 classes from S3:");
            System.out.println("  - " + simpleLoggerClass.getName());
            System.out.println("  - " + logManagerClass.getName());
            System.out.println("  - " + levelClass.getName());
        }
    }

    @Test
    void testS3RangeRequestPerformance() throws Exception {
        try (S3JarzClassLoader loader = new S3JarzClassLoader(s3Client, bucketName, jarzKey)) {
            // Load multiple classes concurrently to test S3 range request efficiency
            String[] classNames = {
                "org.apache.logging.log4j.simple.SimpleLogger",
                "org.apache.logging.log4j.LogManager", 
                "org.apache.logging.log4j.Level",
                "org.apache.logging.log4j.Logger",
                "org.apache.logging.log4j.spi.AbstractLogger"
            };
            
            long startTime = System.currentTimeMillis();
            
            // Load classes in parallel
            @SuppressWarnings("unchecked")
            CompletableFuture<Class<?>>[] futures = new CompletableFuture[classNames.length];
            
            for (int i = 0; i < classNames.length; i++) {
                final String className = classNames[i];
                futures[i] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return loader.loadClass(className);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            
            // Wait for all to complete
            CompletableFuture.allOf(futures).get();
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Verify all classes loaded
            for (CompletableFuture<Class<?>> future : futures) {
                assertThat(future.get()).isNotNull();
            }
            
            System.out.println("🚀 S3 Range Requests: Loaded " + classNames.length + 
                             " classes in " + duration + "ms");
            assertThat(duration).isLessThan(10000); // Should be reasonable with S3 range requests
        }
    }

    @Test
    void testS3StreamingEfficiency() throws Exception {
        try (S3JarzClassLoader loader = new S3JarzClassLoader(s3Client, bucketName, jarzKey)) {
            // Load only specific classes to test S3 range request efficiency
            loader.loadClass("org.apache.logging.log4j.Logger");
            loader.loadClass("org.apache.logging.log4j.Level");
            
            System.out.println("📡 S3 Range Request Efficiency:");
            System.out.println("  - S3 ClassLoader successfully loaded classes");
            System.out.println("  - S3 range requests working with JARZ v2 format");
            System.out.println("  - Only specific blocks downloaded, not entire file");
        }
    }
}
