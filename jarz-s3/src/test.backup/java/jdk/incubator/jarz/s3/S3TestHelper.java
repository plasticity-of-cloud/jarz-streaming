package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.v2.BlockWriter;
import jdk.incubator.jarz.v2.Block;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/**
 * Helper for S3 testing with multiple backends (LocalStack, MinIO, real S3).
 */
public class S3TestHelper implements AutoCloseable {
    
    public enum TestProfile {
        LOCAL,    // LocalStack
        MINIO,    // MinIO
        S3        // Real AWS S3
    }
    
    private final TestProfile profile;
    private final Properties config;
    private final S3Client s3Client;
    private final String bucket;
    
    public S3TestHelper(TestProfile profile) throws IOException {
        this.profile = profile;
        this.config = loadConfig();
        this.s3Client = createS3Client();
        this.bucket = getBucketName();
        
        // Create bucket if it doesn't exist
        createBucketIfNotExists();
    }
    
    private Properties loadConfig() throws IOException {
        Properties props = new Properties();
        try (var stream = getClass().getResourceAsStream("/test-profiles.properties")) {
            if (stream != null) {
                props.load(stream);
            }
        }
        return props;
    }
    
    private S3Client createS3Client() {
        S3ClientBuilder builder = S3Client.builder();
        
        switch (profile) {
            case LOCAL:
                String endpoint = config.getProperty("test.profile.local.s3.endpoint", "http://localhost:4566");
                String region = config.getProperty("test.profile.local.s3.region", "us-east-1");
                String accessKey = config.getProperty("test.profile.local.s3.access-key", "test");
                String secretKey = config.getProperty("test.profile.local.s3.secret-key", "test");
                
                builder.endpointOverride(URI.create(endpoint))
                       .region(Region.of(region))
                       .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create(accessKey, secretKey)))
                       .forcePathStyle(true); // Required for LocalStack
                break;
            case MINIO:
                String minioEndpoint = config.getProperty("test.profile.minio.endpoint", "http://localhost:9000");
                String minioRegion = config.getProperty("test.profile.minio.region", "us-east-1");
                String minioAccessKey = config.getProperty("test.profile.minio.access-key", "minioadmin");
                String minioSecretKey = config.getProperty("test.profile.minio.secret-key", "minioadmin");
                
                builder.endpointOverride(URI.create(minioEndpoint))
                       .region(Region.of(minioRegion))
                       .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create(minioAccessKey, minioSecretKey)))
                       .forcePathStyle(true); // Required for MinIO
                break;
            case S3:
                String s3Region = config.getProperty("test.profile.s3.region", "us-east-1");
                builder.region(Region.of(s3Region));
                // Use default credential chain for real S3
                break;
        }
        
        return builder.build();
    }
    
    private String getBucketName() {
        switch (profile) {
            case LOCAL:
                return config.getProperty("test.profile.local.s3.bucket", "test-jarz-bucket");
            case MINIO:
                return config.getProperty("test.profile.minio.bucket", "test-jarz-bucket");
            case S3:
                return config.getProperty("test.profile.s3.bucket", "jarz-integration-test-" + System.currentTimeMillis());
            default:
                throw new IllegalStateException("Unknown profile: " + profile);
        }
    }
    
    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            CreateBucketRequest.Builder requestBuilder = CreateBucketRequest.builder().bucket(bucket);
            
            // For regions other than us-east-1, need to specify location constraint
            if (profile == TestProfile.S3) {
                String region = config.getProperty("test.profile.s3.region", "us-east-1");
                if (!"us-east-1".equals(region)) {
                    requestBuilder.createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                            .locationConstraint(BucketLocationConstraint.fromValue(region))
                            .build()
                    );
                }
            }
            
            s3Client.createBucket(requestBuilder.build());
            System.out.println("Created test bucket: " + bucket);
        }
    }
    
    /**
     * Upload a JARZ file to S3 for testing.
     */
    public String uploadTestJarz(Path jarzFile) {
        String key = "test-jarz/" + System.currentTimeMillis() + "/" + jarzFile.getFileName();
        
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build(),
                jarzFile
            );
            
            System.out.println("Uploaded JARZ to " + profile + ": " + bucket + "/" + key);
            return key;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload JARZ file", e);
        }
    }
    
    /**
     * Create and upload a test JARZ with synthetic classes.
     */
    public String createAndUploadTestJarz(int classCount) throws IOException {
        Path tempJarz = Files.createTempFile("test-jarz-", ".jarz");
        
        try (BlockWriter writer = new BlockWriter(tempJarz)) {
            Block block = new Block(0);
            for (int i = 0; i < classCount; i++) {
                String className = "com/example/TestClass" + i + ".class";
                byte[] classBytes = generateTestClassBytes(i);
                block.add(className, classBytes);
            }
            writer.writeBlock(block);
        }
        
        String key = uploadTestJarz(tempJarz);
        Files.deleteIfExists(tempJarz);
        return key;
    }
    
    /**
     * Generate synthetic class bytes for testing.
     */
    public static byte[] generateTestClassBytes(int seed) {
        Random random = new Random(seed);
        int size = 1024 + random.nextInt(9216); // 1KB to 10KB
        
        ByteBuffer buf = ByteBuffer.allocate(size);
        
        // Class file magic and version
        buf.putInt(0xCAFEBABE);
        buf.putShort((short) 0);
        buf.putShort((short) 61);
        
        // Simple constant pool
        buf.putShort((short) 10);
        for (int i = 1; i < 10; i++) {
            buf.put((byte) 1); // UTF8
            String str = "TestString" + (seed + i);
            buf.putShort((short) str.length());
            buf.put(str.getBytes());
        }
        
        // Fill rest with deterministic but varied data
        while (buf.remaining() > 0) {
            buf.put((byte) ((seed + buf.position()) % 256));
        }
        
        return buf.array();
    }
    
    /**
     * Clean up test resources.
     */
    public void cleanup() {
        if (profile == TestProfile.S3) {
            // For real S3, clean up test objects
            try {
                ListObjectsV2Response response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix("test-jarz/")
                        .build()
                );
                
                for (S3Object object : response.contents()) {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(object.key())
                        .build());
                }
                
                // Delete bucket if empty
                s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
                
            } catch (Exception e) {
                System.err.println("Failed to cleanup S3 resources: " + e.getMessage());
            }
        }
        
        s3Client.close();
    }
    
    @Override
    public void close() {
        cleanup();
    }
    
    public S3Client getS3Client() {
        return s3Client;
    }
    
    public String getBucket() {
        return bucket;
    }
    
    public TestProfile getProfile() {
        return profile;
    }
    
    /**
     * Check if a test profile is enabled.
     */
    public static boolean isProfileEnabled(TestProfile profile) {
        try {
            Properties props = new Properties();
            try (var stream = S3TestHelper.class.getResourceAsStream("/test-profiles.properties")) {
                if (stream != null) {
                    props.load(stream);
                }
            }
            
            String key;
            switch (profile) {
                case LOCAL:
                    key = "test.profile.local.enabled";
                    break;
                case MINIO:
                    key = "test.profile.minio.enabled";
                    break;
                case S3:
                    key = "test.profile.s3.enabled";
                    break;
                default:
                    throw new IllegalStateException("Unknown profile: " + profile);
            }
            
            return Boolean.parseBoolean(props.getProperty(key, "false"));
            
        } catch (Exception e) {
            return false;
        }
    }
}
