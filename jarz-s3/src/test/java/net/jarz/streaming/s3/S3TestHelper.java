package net.jarz.streaming.s3;

import net.jarz.streaming.v2.BlockWriter;
import net.jarz.streaming.v2.Block;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Random;

/**
 * Helper for S3 testing with multiple backends (LocalStack, MinIO, real S3).
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class S3TestHelper implements AutoCloseable {
    
    public enum TestProfile {
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
            case MINIO:
                return builder
                    .endpointOverride(URI.create(config.getProperty("test.profile.minio.endpoint")))
                    .region(Region.of(config.getProperty("test.profile.minio.region")))
                    .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                            config.getProperty("test.profile.minio.access-key"),
                            config.getProperty("test.profile.minio.secret-key")
                        )
                    ))
                    .forcePathStyle(true)
                    .build();
                    
            case S3:
                return builder
                    .region(Region.of(config.getProperty("test.profile.s3.region", "us-east-1")))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
                    
            default:
                throw new IllegalArgumentException("Unknown profile: " + profile);
        }
    }
    
    private String getBucketName() {
        switch (profile) {
            case MINIO:
                return config.getProperty("test.profile.minio.bucket");
            case S3:
                return config.getProperty("test.profile.s3.bucket");
            default:
                throw new IllegalArgumentException("Unknown profile: " + profile);
        }
    }
    
    private void createBucketIfNotExists() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
    
    public String uploadJarzFile(Path jarzFile, String key) throws IOException {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build(),
            RequestBody.fromFile(jarzFile)
        );
        return key;
    }
    
    public void deleteObject(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build());
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
    
    public boolean isEnabled() {
        String enabledKey = "test.profile." + profile.name().toLowerCase() + ".enabled";
        return Boolean.parseBoolean(config.getProperty(enabledKey, "false"));
    }
    
    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
    }
}
