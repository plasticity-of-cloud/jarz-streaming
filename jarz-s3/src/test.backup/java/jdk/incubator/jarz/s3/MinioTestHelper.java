package jdk.incubator.jarz.s3;

import jdk.incubator.jarz.v2.BlockWriter;
import jdk.incubator.jarz.v2.Block;
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
 */
public class MinioTestHelper {
    
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";
    private static final String BUCKET_NAME = "test-jarz-bucket";
    
    private final MinIOContainer container;
    private S3Client s3Client;
    
    public MinioTestHelper(MinIOContainer container) {
        this.container = container;
    }
    
    public void initialize() {
        // Create S3 client
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(container.getS3URL()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .build();
        
        // Create test bucket
        s3Client.createBucket(CreateBucketRequest.builder()
                .bucket(BUCKET_NAME)
                .build());
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
    
    /**
     * Create and upload a test JARZ with specified number of classes.
     */
    public String createAndUploadTestJarz(int numClasses) throws IOException {
        Path tempJarz = Files.createTempFile("test", ".jarz");
        
        try (BlockWriter writer = new BlockWriter(tempJarz)) {
            Block block = new Block(0);
            for (int i = 0; i < numClasses; i++) {
                String className = "com/example/TestClass" + i + ".class";
                byte[] classBytes = generateTestClassBytes(i);
                block.add(className, classBytes);
            }
            writer.writeBlock(block);
        }
        
        String key = "test-" + System.currentTimeMillis() + ".jarz";
        uploadFile(tempJarz, key);
        Files.deleteIfExists(tempJarz);
        
        return key;
    }
    
    /**
     * Upload a file to MinIO.
     */
    public void uploadFile(Path filePath, String key) throws IOException {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(key)
                        .build(),
                RequestBody.fromFile(filePath));
    }
    
    /**
     * Generate test class bytes for a given class index.
     * Uses a minimal but valid class file structure with correct package.
     */
    public static byte[] generateTestClassBytes(int classIndex) {
        // Class name: com/example/TestClassN
        String className = "com/example/TestClass" + classIndex;
        byte[] classNameBytes = className.getBytes();
        byte[] superNameBytes = "java/lang/Object".getBytes();
        
        // Build constant pool (need 5 entries: 1-4)
        int cpCount = 5;
        
        // Calculate total size
        int totalSize = 4 + 4 + 2 + // magic + version + cp_count
                       3 + (3 + classNameBytes.length) + // CP #1 (Class) + CP #2 (UTF8 class name)
                       3 + (3 + superNameBytes.length) + // CP #3 (Class) + CP #4 (UTF8 super name)
                       2 + 2 + 2 + 2 + 2 + 2 + 2; // flags + this + super + interfaces + fields + methods + attrs
        
        byte[] classFile = new byte[totalSize];
        int pos = 0;
        
        // Magic
        classFile[pos++] = (byte) 0xCA;
        classFile[pos++] = (byte) 0xFE;
        classFile[pos++] = (byte) 0xBA;
        classFile[pos++] = (byte) 0xBE;
        
        // Version (Java 25)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x41;
        
        // Constant pool count
        classFile[pos++] = (byte) ((cpCount >> 8) & 0xFF);
        classFile[pos++] = (byte) (cpCount & 0xFF);
        
        // CP #1: Class info pointing to #2
        classFile[pos++] = 0x07;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x02;
        
        // CP #2: UTF8 with class name
        classFile[pos++] = 0x01;
        classFile[pos++] = (byte) ((classNameBytes.length >> 8) & 0xFF);
        classFile[pos++] = (byte) (classNameBytes.length & 0xFF);
        System.arraycopy(classNameBytes, 0, classFile, pos, classNameBytes.length);
        pos += classNameBytes.length;
        
        // CP #3: Class info pointing to #4 (superclass)
        classFile[pos++] = 0x07;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x04;
        
        // CP #4: UTF8 with superclass name
        classFile[pos++] = 0x01;
        classFile[pos++] = (byte) ((superNameBytes.length >> 8) & 0xFF);
        classFile[pos++] = (byte) (superNameBytes.length & 0xFF);
        System.arraycopy(superNameBytes, 0, classFile, pos, superNameBytes.length);
        pos += superNameBytes.length;
        
        // Access flags (public = 0x0021)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x21;
        
        // This class (CP #1)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x01;
        
        // Super class (CP #3)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x03;
        
        // Interfaces count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Fields count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Methods count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Attributes count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        return classFile;
    }
}
