package jdk.incubator.jarz.s3;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests with real AWS S3.
 * Enable with: -Djarz.test.real-s3=true -Djarz.test.s3-bucket=my-test-bucket
 */
@EnabledIfSystemProperty(named = "jarz.test.real-s3", matches = "true")
class RealS3IntegrationTest {
    
    private static S3Client s3Client;
    private static String testBucket;
    private static List<String> uploadedKeys = new ArrayList<>();
    
    @BeforeAll
    static void setUpClass() {
        testBucket = System.getProperty("jarz.test.s3-bucket");
        Assumptions.assumeTrue(testBucket != null && !testBucket.isEmpty(),
            "S3 bucket name must be provided via -Djarz.test.s3-bucket=bucket-name");
        
        s3Client = S3Client.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_EAST_1)
                .build();
        
        // Create bucket if it doesn't exist
        try {
            s3Client.createBucket(CreateBucketRequest.builder()
                    .bucket(testBucket)
                    .build());
        } catch (Exception e) {
            // Bucket might already exist
        }
    }
    
    @AfterAll
    static void tearDownClass() {
        if (s3Client != null && testBucket != null) {
            // Clean up uploaded objects
            for (String key : uploadedKeys) {
                try {
                    s3Client.deleteObject(DeleteObjectRequest.builder()
                            .bucket(testBucket)
                            .key(key)
                            .build());
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }
            
            // Try to delete bucket if empty
            try {
                var objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(testBucket)
                        .build());
                
                if (objects.contents().isEmpty()) {
                    s3Client.deleteBucket(DeleteBucketRequest.builder()
                            .bucket(testBucket)
                            .build());
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            
            s3Client.close();
        }
    }
    
    @Test
    void performanceTestWithRealS3() throws Exception {
        // Create helper for real S3
        RealS3TestHelper realS3Helper = new RealS3TestHelper(s3Client, testBucket);
        String realS3Key = realS3Helper.createAndUploadTestJarz(100);
        uploadedKeys.add(realS3Key);
        
        try (S3JarzV2ClassLoader loader = new S3JarzV2ClassLoader(
                s3Client, 
                testBucket, 
                realS3Key)) {
            
            // Measure cold start performance
            long coldStartTime = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                loader.loadClass("com.example.TestClass" + i);
            }
            long coldStartDuration = System.currentTimeMillis() - coldStartTime;
            
            // Measure warm start performance
            long warmStartTime = System.currentTimeMillis();
            for (int i = 0; i < 20; i++) {
                loader.loadClass("com.example.TestClass" + i); // Should be cached
            }
            long warmStartDuration = System.currentTimeMillis() - warmStartTime;
            
            System.out.println("Real S3 Performance:");
            System.out.println("- Cold start (20 classes): " + coldStartDuration + "ms");
            System.out.println("- Warm start (20 classes): " + warmStartDuration + "ms");
            System.out.println("- Cache speedup: " + (coldStartDuration / Math.max(warmStartDuration, 1)) + "x");
            
            // Real S3 should still be reasonable
            assertThat(coldStartDuration).isLessThan(10_000); // < 10 seconds
            assertThat(warmStartDuration).isLessThan(100); // < 100ms for cached
        }
    }
    
    @Test
    void testRealS3RangeRequests() throws Exception {
        RealS3TestHelper realS3Helper = new RealS3TestHelper(s3Client, testBucket);
        String realS3Key = realS3Helper.createAndUploadTestJarz(200);
        uploadedKeys.add(realS3Key);
        
        try (S3JarzV2ClassLoader loader = new S3JarzV2ClassLoader(
                s3Client, 
                testBucket, 
                realS3Key)) {
            
            // Load scattered classes to test range requests
            List<String> classNames = List.of(
                "com.example.TestClass0",   // Beginning
                "com.example.TestClass100", // Middle
                "com.example.TestClass199"  // End
            );
            
            long startTime = System.currentTimeMillis();
            for (String className : classNames) {
                Class<?> clazz = loader.loadClass(className);
                assertThat(clazz).isNotNull();
            }
            long loadTime = System.currentTimeMillis() - startTime;
            
            System.out.println("Real S3 Range Request Test:");
            System.out.println("- Classes loaded: " + classNames.size());
            System.out.println("- Load time: " + loadTime + "ms");
            System.out.println("- Avg per class: " + (loadTime / classNames.size()) + "ms");
            
            assertThat(loadTime).isLessThan(5000); // Should be reasonable even with real S3
        }
    }
    
    /**
     * Helper for real S3 testing.
     */
    private static class RealS3TestHelper {
        private final S3Client s3Client;
        private final String bucket;
        
        public RealS3TestHelper(S3Client s3Client, String bucket) {
            this.s3Client = s3Client;
            this.bucket = bucket;
        }
        
        public String createAndUploadTestJarz(int numClasses) throws Exception {
            // Reuse MinIO helper's JARZ creation logic
            java.nio.file.Path tempJarz = java.nio.file.Files.createTempFile("real-s3-test", ".jarz");
            
            try (jdk.incubator.jarz.v2.BlockWriter writer = new jdk.incubator.jarz.v2.BlockWriter(tempJarz)) {
                jdk.incubator.jarz.v2.Block block = new jdk.incubator.jarz.v2.Block(0);
                for (int i = 0; i < numClasses; i++) {
                    String className = "com/example/TestClass" + i + ".class";
                    byte[] classBytes = MinioTestHelper.generateTestClassBytes(i);
                    block.add(className, classBytes);
                }
                writer.writeBlock(block);
            }
            
            String key = "real-s3-test-" + System.currentTimeMillis() + ".jarz";
            
            s3Client.putObject(
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromFile(tempJarz));
            
            java.nio.file.Files.deleteIfExists(tempJarz);
            return key;
        }
    }
}
