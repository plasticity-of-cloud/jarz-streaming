package net.jarz.streaming.s3;

import net.jarz.streaming.v2.BlockWriter;
import net.jarz.streaming.v2.Block;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Test helper for MinIO using TestContainers.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class MinioTestHelper implements AutoCloseable {
    
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "test-jarz-bucket";
    
    private final MinIOContainer container;
    private S3Client s3Client;
    
    public MinioTestHelper() {
        this.container = new MinIOContainer(DockerImageName.parse("minio/minio:latest"))
            .withUserName(ACCESS_KEY)
            .withPassword(SECRET_KEY);
    }
    
    public void start() {
        container.start();
        
        // Create S3 client
        s3Client = S3Client.builder()
            .endpointOverride(URI.create(container.getS3URL()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)
            ))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .build();
            
        // Create test bucket
        s3Client.createBucket(CreateBucketRequest.builder()
            .bucket(BUCKET_NAME)
            .build());
    }
    
    public String uploadJarzFile(Path jarzFile, String key) throws IOException {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(key)
                .build(),
            RequestBody.fromFile(jarzFile)
        );
        return key;
    }
    
    public S3Client getS3Client() {
        return s3Client;
    }
    
    public String getBucket() {
        return BUCKET_NAME;
    }
    
    public String getEndpoint() {
        return container.getS3URL();
    }
    
    @Override
    public void close() {
        if (s3Client != null) {
            s3Client.close();
        }
        if (container != null) {
            container.stop();
        }
    }
}
