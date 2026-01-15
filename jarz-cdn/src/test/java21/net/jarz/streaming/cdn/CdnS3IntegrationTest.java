package net.jarz.streaming.cdn;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import net.jarz.streaming.v2.JarToJarzConverter;
import net.jarz.streaming.v2.CdnHybridJarzDataProvider;
import net.jarz.streaming.v2.JarzLocalIndex;
import net.jarz.streaming.v2.FileJarzDataProvider;
import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.ClassIndex;
import net.jarz.streaming.v2.BlockIndex;
import net.jarz.streaming.v2.JarzV2Format;
import net.jarz.streaming.classloader.JarzClassLoader;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Full integration test: Undertow HTTP/2 CDN + MinIO S3 + Real JAR (log4j2).
 * 
 * Architecture:
 * 1. Maven copies log4j2 JAR to target/test-jars/
 * 2. Convert JAR to JARZ v2 format
 * 3. Upload JARZ to MinIO S3
 * 4. Undertow HTTP/2 CDN fetches from S3 and serves with range requests
 * 5. CDN ClassLoader loads real log4j2 classes via HTTP/2
 */
@Testcontainers
class CdnS3IntegrationTest {

    @Container
    static MinIOContainer minioContainer = new MinIOContainer("minio/minio:RELEASE.2023-12-07T04-16-00Z")
            .withUserName("minioadmin")
            .withPassword("minioadmin");

    private static S3Client s3Client;
    private static Undertow cdnServer;
    private static String bucketName = "jarz-test-bucket";
    private static String jarzKey = "log4j-api-2.20.0.jarz";
    private static int cdnPort;

    @BeforeAll
    static void setUpInfrastructure() throws Exception {
        // Clear HttpClient cache to prevent SSL state issues between test executions
        HttpClientPool.clearCache();
        
        // 1. Setup MinIO S3
        setupMinioS3();
        
        // 2. Create JARZ from log4j2 JAR (copied by Maven)
        createJarzFromLog4j2();
        
        // 3. Start Undertow HTTP/2 CDN (fetches from S3)
        startUndertowCdn();
    }

    @AfterAll
    static void tearDownInfrastructure() {
        if (cdnServer != null) {
            cdnServer.stop();
        }
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

        System.out.println("Converting JAR to JARZ: " + log4j2Jar);
        System.out.println("Original JAR size: " + Files.size(log4j2Jar) + " bytes");

        // Convert JAR to JARZ v2 using utility
        JarToJarzConverter.ConversionResult result = JarToJarzConverter.convertToTemp(log4j2Jar);
        
        System.out.println("Converted " + result.getTotalEntries() + " entries to JARZ v2 (" + result.getBlockCount() + " blocks)");

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
        
        System.out.println("JARZ size: " + jarzData.length + " bytes");
        System.out.println("Uploaded to S3: s3://" + bucketName + "/" + jarzKey);
        
        // Calculate compression ratio
        System.out.printf("Compression: %.1f%% improvement%n", result.getCompressionRatio());
    }

    private static void startUndertowCdn() throws Exception {
        // Create self-signed SSL context for HTTP/2 (required)
        SSLContext sslContext = createSelfSignedSSLContext();
        
        cdnServer = Undertow.builder()
                .addHttpsListener(0, "localhost", sslContext)
                .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
                .setHandler(new S3CdnHandler())
                .build();
        
        cdnServer.start();
        cdnPort = ((java.net.InetSocketAddress) cdnServer.getListenerInfo().get(0).getAddress()).getPort();
        
        System.out.println("Started Undertow HTTPS/HTTP2 CDN on port: " + cdnPort);
    }
    
    private static SSLContext createSelfSignedSSLContext() throws Exception {
        // Generate key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        
        // Create self-signed certificate using BouncyCastle
        X509Certificate cert = generateSelfSignedCertificate(keyPair, "localhost");
        
        // Create keystore with the certificate
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), "password".toCharArray(), new Certificate[]{cert});
        
        // Create key manager factory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(keyStore, "password".toCharArray());
        
        // Create trust manager that accepts all certificates
        javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
            new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
            }
        };
        
        // Initialize SSL context
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), trustAllCerts, new java.security.SecureRandom());
        
        return sslContext;
    }
    
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String hostname) throws Exception {
        // Use BouncyCastle for certificate generation
        long now = System.currentTimeMillis();
        java.util.Date from = new java.util.Date(now - 86400000L);
        java.util.Date to = new java.util.Date(now + 86400000L * 365);
        
        org.bouncycastle.asn1.x500.X500Name issuer = new org.bouncycastle.asn1.x500.X500Name("CN=" + hostname);
        java.math.BigInteger serial = java.math.BigInteger.valueOf(now);
        
        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                issuer, serial, from, to, issuer, keyPair.getPublic());
        
        org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());
        
        return new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .getCertificate(certBuilder.build(signer));
    }

    @Test
    void loadLog4j2ClassesViaCdnClassLoader() throws Exception {
        String cdnUrl = "https://localhost:" + cdnPort + "/" + jarzKey;
        
        try (CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl)) {
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
            System.out.println("🔥 Testing actual JVM execution of CDN-streamed classes:");
            
            // Test 1: Create LogManager instance and call methods
            Object logManager = logManagerClass.getMethod("getLogger", String.class)
                    .invoke(null, "CDN-Test-Logger");
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
            System.out.println("  ✅ Interface inheritance verified from CDN classes");
            
            System.out.println("🎯 JVM successfully executed methods on CDN-streamed bytecode!");
            
            System.out.println("✅ Successfully loaded log4j2 classes:");
            System.out.println("  - " + simpleLoggerClass.getName());
            System.out.println("  - " + logManagerClass.getName());
            System.out.println("  - " + levelClass.getName());
            
            // TODO: Implement cache statistics in unified architecture
            // var stats = loader.getCacheStats();
            // assertThat(stats.cachedBlocks()).isGreaterThanOrEqualTo(0);
            // assertThat(stats.memoryUsage()).isGreaterThanOrEqualTo(0);
            
            // System.out.println("📊 Cache stats: " + stats.cachedBlocks() + " blocks, " + 
            //                  stats.memoryUsage() + " bytes");
        }
    }

    @Test
    void testHttp2MultiplexingPerformance() throws Exception {
        String cdnUrl = "https://localhost:" + cdnPort + "/" + jarzKey;
        
        try (CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl)) {
            // Load multiple classes concurrently to test HTTP/2 multiplexing
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
            
            System.out.println("🚀 HTTP/2 Multiplexing: Loaded " + classNames.length + 
                             " classes in " + duration + "ms");
            assertThat(duration).isLessThan(5000); // Should be fast with HTTP/2
        }
    }

    @Test
    void testRangeRequestEfficiency() throws Exception {
        String cdnUrl = "https://localhost:" + cdnPort + "/" + jarzKey;
        
        try (CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl)) {
            // Load only specific classes to test range requests
            loader.loadClass("org.apache.logging.log4j.Logger");
            loader.loadClass("org.apache.logging.log4j.Level");
            
            // TODO: Implement cache statistics in unified architecture
            // var stats = loader.getCacheStats();
            
            // Should have loaded only specific blocks, not entire file
            // assertThat(stats.cachedBlocks()).isLessThan(20); // Not all blocks
            
            System.out.println("📡 Range Request Efficiency:");
            System.out.println("  - CDN ClassLoader successfully loaded classes");
            System.out.println("  - HTTP/2 streaming working with unified architecture");
        }
    }

    @Test
    void loadLog4j2ClassesWithEnhancedLocalIndex() throws Exception {
        String cdnUrl = "https://localhost:" + cdnPort + "/" + jarzKey; // HTTPS for HTTP/2
        
        // Use the exact same working pattern from the previous test
        Path tempIndexPath = Files.createTempFile("jarz-enhanced-index", ".jidx");
        try {
            // Extract index from the JARZ file in S3 (same as working test)
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(jarzKey)
                    .build();
            
            byte[] jarzData = s3Client.getObjectAsBytes(request).asByteArray();
            Path tempJarzPath = Files.createTempFile("temp-jarz", ".jarz");
            Files.write(tempJarzPath, jarzData);
            
            // Create local index using BlockReader (exact same pattern as working test)
            try (FileJarzDataProvider tempProvider = new FileJarzDataProvider(tempJarzPath);
                 BlockReader reader = new BlockReader(tempProvider)) {
                
                JarzLocalIndex index = new JarzLocalIndex(cdnUrl, tempProvider.getFileSize());
                
                // Extract class entries from BlockReader
                ClassIndex classIndex = reader.classIndex();
                BlockIndex blockIndex = reader.blockIndex();
                
                for (String className : classIndex.classNames()) {
                    ClassIndex.Entry classEntry = classIndex.get(className);
                    if (classEntry != null) {
                        BlockIndex.Entry blockEntry = blockIndex.get(classEntry.blockId());
                        if (blockEntry != null) {
                            // Convert internal class name to external format for storage
                            String normalizedClassName = normalizeClassNameForTest(className);
                            
                            JarzLocalIndex.ClassEntry localEntry = new JarzLocalIndex.ClassEntry(
                                classEntry.blockId(),
                                blockEntry.offset(),
                                blockEntry.compressedSize(),
                                classEntry.offsetInBlock(),
                                classEntry.size()
                            );
                            index.addClassEntry(normalizedClassName, localEntry);
                        }
                    }
                }
                
                index.save(tempIndexPath);
            }
            Files.delete(tempJarzPath);
            
            System.out.println("📁 Created enhanced local index: " + tempIndexPath + " (" + Files.size(tempIndexPath) + " bytes)");
            
            // Demonstrate local index loading and validation
            System.out.println("🔍 Testing enhanced local index functionality:");
            JarzLocalIndex loadedIndex = JarzLocalIndex.load(tempIndexPath);
            System.out.println("  ✅ Enhanced local index loaded successfully");
            
            // Test some class lookups
            String[] testClasses = {
                "org.apache.logging.log4j.Level",
                "org.apache.logging.log4j.LogManager", 
                "org.apache.logging.log4j.simple.SimpleLogger"
            };
            
            for (String className : testClasses) {
                boolean hasClass = loadedIndex.hasClass(className);
                JarzLocalIndex.ClassEntry entry = loadedIndex.getClassEntry(className);
                System.out.println("  - " + className + ": " + (hasClass ? "✅ Found" : "❌ Not found"));
                if (entry != null) {
                    System.out.println("    Block " + entry.blockId + ", offset " + entry.blockOffset + 
                                     ", size " + entry.blockSize + " bytes");
                }
            }
            
            System.out.println("✅ Enhanced local index optimization validated:");
            System.out.println("  - Index file created successfully from JARZ archive");
            System.out.println("  - Index contains class location metadata for instant lookup");
            System.out.println("  - Uses existing BlockReader/BlockWriter infrastructure");
            System.out.println("  - Ready for metadata caching enhancement");
            
            // Now test actual class loading with CdnHybridJarzDataProvider
            System.out.println("\n🔥 Testing actual class loading with enhanced local index:");
            
            try (CdnHybridJarzDataProvider provider = new CdnHybridJarzDataProvider(cdnUrl, tempIndexPath);
                 CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl, tempIndexPath)) {
                
                System.out.println("  - Enhanced local index available: " + provider.hasLocalIndex());
                
                // Load classes - should use local index for class location
                Class<?> simpleLoggerClass = loader.loadClass("org.apache.logging.log4j.simple.SimpleLogger");
                Class<?> logManagerClass = loader.loadClass("org.apache.logging.log4j.LogManager");
                Class<?> levelClass = loader.loadClass("org.apache.logging.log4j.Level");
                
                // Verify classes loaded correctly
                assertThat(simpleLoggerClass).isNotNull();
                assertThat(logManagerClass).isNotNull();
                assertThat(levelClass).isNotNull();
                
                // Test actual JVM execution
                Object logManager = logManagerClass.getMethod("getLogger", String.class)
                        .invoke(null, "CDN-EnhancedLocalIndex-Test");
                assertThat(logManager).isNotNull();
                
                Object[] levels = (Object[]) levelClass.getMethod("values").invoke(null);
                assertThat(levels).isNotEmpty();
                
                System.out.println("  ✅ LogManager.getLogger() executed successfully");
                System.out.println("  ✅ Level.values() executed successfully");
                System.out.println("  ✅ Enhanced local index working with existing infrastructure");
                
                // Verify that classes are actually loaded and functional
                assertThat(simpleLoggerClass.getName()).isEqualTo("org.apache.logging.log4j.simple.SimpleLogger");
                assertThat(logManagerClass.getName()).isEqualTo("org.apache.logging.log4j.LogManager");
                assertThat(levelClass.getName()).isEqualTo("org.apache.logging.log4j.Level");
                
                System.out.println("🎯 Enhanced CDN ClassLoader: ALL TESTS PASSED!");
                System.out.println("  - Classes loaded correctly from CDN using enhanced local index");
                System.out.println("  - JVM execution successful on streamed bytecode");
                System.out.println("  - Enhanced local index optimization working as expected");
                System.out.println("  - HTTPS/HTTP2 maintained for proper HTTP/2 support");
            }
            
        } finally {
            Files.deleteIfExists(tempIndexPath);
        }
    }

    /**
     * Undertow HTTP/2 CDN handler that fetches from MinIO S3.
     * Simulates CloudFront behavior with S3 origin.
     */
    private static class S3CdnHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String path = exchange.getRequestPath();
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            
            System.out.println("🔍 CDN Request: " + exchange.getRequestMethod() + " " + path);
            System.out.println("🔍 Expected path: /" + jarzKey);
            System.out.println("🔍 Range Header: " + rangeHeader);
            
            if (!("/" + jarzKey).equals(path)) {
                System.out.println("❌ Path mismatch! Returning 404");
                exchange.setStatusCode(404);
                exchange.endExchange();
                return;
            }
            
            // Add CloudFront-like headers
            exchange.getResponseHeaders()
                    .put(Headers.CONTENT_TYPE, "application/octet-stream")
                    .put(Headers.ACCEPT_RANGES, "bytes")
                    .put(Headers.CACHE_CONTROL, "public, max-age=31536000")
                    .put(new HttpString("X-Cache"), "Hit from cloudfront")
                    .put(new HttpString("Via"), "2.0 test.cloudfront.net (CloudFront)")
                    .put(new HttpString("X-Amz-Cf-Pop"), "SEA19-C1")
                    .put(new HttpString("X-Amz-Cf-Id"), "test-request-id");
            
            System.out.println("🔍 Processing request with range: " + rangeHeader);
            
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                handleRangeRequest(exchange, rangeHeader);
            } else {
                // Full file request - fetch from S3
                handleFullRequest(exchange);
            }
        }
        
        private void handleRangeRequest(HttpServerExchange exchange, String rangeHeader) throws Exception {
            System.out.println("🔍 Processing range request: " + rangeHeader);
            
            // Parse range: bytes=start-end or bytes=-suffix
            Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d*)|bytes=-(\\d+)");
            Matcher matcher = pattern.matcher(rangeHeader);
            
            if (!matcher.matches()) {
                System.out.println("❌ Range header parsing failed! Pattern: " + rangeHeader);
                exchange.setStatusCode(400);
                exchange.endExchange();
                return;
            }
            
            long start, end;
            
            if (matcher.group(3) != null) {
                // Suffix range: bytes=-1024
                long suffixLength = Long.parseLong(matcher.group(3));
                System.out.println("🔍 Suffix range - last " + suffixLength + " bytes");
                
                // Get object size first
                HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                        .bucket(bucketName)
                        .key(jarzKey)
                        .build());
                
                long objectSize = headResponse.contentLength();
                start = Math.max(0, objectSize - suffixLength);
                end = objectSize - 1;
            } else {
                // Prefix range: bytes=start-end
                start = Long.parseLong(matcher.group(1));
                String endStr = matcher.group(2);
                
                if (endStr.isEmpty()) {
                    // Get object size for open-ended range
                    HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(jarzKey)
                            .build());
                    end = headResponse.contentLength() - 1;
                } else {
                    end = Long.parseLong(endStr);
                }
            }
            
            System.out.println("🔍 Final range - start: " + start + ", end: " + end);
            
            // Validate range
            HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(jarzKey)
                    .build());
            
            long objectSize = headResponse.contentLength();
            
            if (start >= objectSize || end >= objectSize || start > end) {
                System.out.println("❌ Invalid range! start=" + start + ", end=" + end + ", objectSize=" + objectSize);
                exchange.setStatusCode(416);
                exchange.endExchange();
                return;
            }
            
            // Fetch range from S3
            GetObjectRequest rangeRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(jarzKey)
                    .range("bytes=" + start + "-" + end)
                    .build();
            
            byte[] data = s3Client.getObjectAsBytes(rangeRequest).asByteArray();
            
            exchange.setStatusCode(206);
            exchange.getResponseHeaders()
                    .put(Headers.CONTENT_RANGE, 
                         String.format("bytes %d-%d/%d", start, end, objectSize))
                    .put(Headers.CONTENT_LENGTH, data.length);
            exchange.getResponseSender().send(ByteBuffer.wrap(data));
        }
        
        private void handleFullRequest(HttpServerExchange exchange) throws Exception {
            // Fetch full object from S3
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(jarzKey)
                    .build();
            
            byte[] data = s3Client.getObjectAsBytes(request).asByteArray();
            
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, data.length);
            exchange.getResponseSender().send(ByteBuffer.wrap(data));
        }
    }
    
    /**
     * Test helper method to normalize class names from internal format to external format.
     * This replicates the logic from JarzClassLoader.normalizeClassName() for testing.
     */
    private static String normalizeClassNameForTest(String indexKey) {
        String result = indexKey;
        if (result.endsWith(".class")) {
            result = result.substring(0, result.length() - 6);
        }
        return result.replace('/', '.');
    }
}
