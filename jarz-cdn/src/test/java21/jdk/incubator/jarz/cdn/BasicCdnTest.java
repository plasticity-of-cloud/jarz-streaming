package jdk.incubator.jarz.cdn;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import jdk.incubator.jarz.classloader.JarzClassLoader;
import jdk.incubator.jarz.v2.JarToJarzConverter;
import org.junit.jupiter.api.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic CDN test with simple compiled class and local CDN server.
 */
public class BasicCdnTest {

    private static Undertow cdnServer;
    private static int cdnPort;
    private static Path jarzFile;

    @BeforeAll
    static void setUp() throws Exception {
        // 1. Create simple test class and compile it
        createTestJar();
        
        // 2. Convert JAR to JARZ
        convertToJarz();
        
        // 3. Start simple CDN server
        startCdnServer();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (cdnServer != null) {
            cdnServer.stop();
        }
        if (jarzFile != null && Files.exists(jarzFile)) {
            Files.deleteIfExists(jarzFile);
        }
    }

    private static void createTestJar() throws Exception {
        // Create a simple test class
        String testClass = """
            package test;
            public class SimpleTest {
                public static String getMessage() {
                    return "Hello from CDN!";
                }
                public int getValue() {
                    return 42;
                }
            }
            """;

        // Write and compile the test class
        Path tempDir = Files.createTempDirectory("basic-cdn-test");
        Path testDir = tempDir.resolve("test");
        Files.createDirectories(testDir);
        Path javaFile = testDir.resolve("SimpleTest.java");
        Files.writeString(javaFile, testClass);

        // Compile
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, javaFile.toString());
        assertEquals(0, result, "Compilation failed");

        // Create JAR
        Path jarFile = tempDir.resolve("basic-test.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            // Add compiled class
            Path classFile = testDir.resolve("SimpleTest.class");
            jar.putNextEntry(new JarEntry("test/SimpleTest.class"));
            Files.copy(classFile, jar);
            jar.closeEntry();
        }

        // Store jar path for conversion
        jarzFile = jarFile;
    }

    private static void convertToJarz() throws Exception {
        // Convert JAR to JARZ
        JarToJarzConverter.ConversionResult result = JarToJarzConverter.convertToTemp(jarzFile);
        
        // Replace jar file with jarz file
        Files.deleteIfExists(jarzFile);
        jarzFile = result.getJarzFile();
        
        System.out.println("Created basic test JARZ: " + jarzFile);
        System.out.println("Size: " + Files.size(jarzFile) + " bytes");
    }

    private static void startCdnServer() throws Exception {
        cdnServer = Undertow.builder()
                .addHttpListener(0, "localhost")
                .setHandler(new BasicCdnHandler())
                .build();
        
        cdnServer.start();
        cdnPort = ((java.net.InetSocketAddress) cdnServer.getListenerInfo().get(0).getAddress()).getPort();
        
        System.out.println("Started basic CDN server on port: " + cdnPort);
    }

    @Test
    void testCdnJarzClassLoaderCreation() throws Exception {
        String cdnUrl = "http://localhost:" + cdnPort + "/basic-test.jarz";
        
        try (CdnJarzClassLoader loader = new CdnJarzClassLoader(cdnUrl)) {
            assertNotNull(loader);
            assertTrue(loader instanceof JarzClassLoader);
            
            // Load and test the simple class
            Class<?> testClass = loader.loadClass("test.SimpleTest");
            assertNotNull(testClass);
            assertEquals("test.SimpleTest", testClass.getName());
            
            // Test static method
            String message = (String) testClass.getMethod("getMessage").invoke(null);
            assertEquals("Hello from CDN!", message);
            
            // Test instance method
            Object instance = testClass.getDeclaredConstructor().newInstance();
            int value = (int) testClass.getMethod("getValue").invoke(instance);
            assertEquals(42, value);
            
            System.out.println("✅ Successfully loaded and executed test class from CDN");
        }
    }
    
    @Test
    void testCdnJarzClassLoaderFactory() throws Exception {
        String cdnUrl = "http://localhost:" + cdnPort + "/basic-test.jarz";
        
        try (JarzClassLoader loader = CdnJarzClassLoaderFactory.create(cdnUrl)) {
            assertNotNull(loader);
            assertTrue(loader instanceof JarzClassLoader);
            
            // Verify we can load the test class
            Class<?> testClass = loader.loadClass("test.SimpleTest");
            assertNotNull(testClass);
            
            System.out.println("✅ Factory method works correctly");
        }
    }

    /**
     * Simple CDN handler that serves the JARZ file with range request support.
     */
    private static class BasicCdnHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            String path = exchange.getRequestPath();
            
            if (!path.equals("/basic-test.jarz")) {
                exchange.setStatusCode(404);
                return;
            }

            byte[] jarzData = Files.readAllBytes(jarzFile);
            String rangeHeader = exchange.getRequestHeaders().getFirst("Range");
            
            if (rangeHeader != null) {
                // Handle range request
                Pattern pattern = Pattern.compile("bytes=(\\d+)-(\\d*)");
                Matcher matcher = pattern.matcher(rangeHeader);
                
                if (matcher.matches()) {
                    int start = Integer.parseInt(matcher.group(1));
                    int end = matcher.group(2).isEmpty() ? jarzData.length - 1 : Integer.parseInt(matcher.group(2));
                    
                    byte[] rangeData = new byte[end - start + 1];
                    System.arraycopy(jarzData, start, rangeData, 0, rangeData.length);
                    
                    exchange.setStatusCode(206);
                    exchange.getResponseHeaders().put(Headers.CONTENT_RANGE, 
                        "bytes " + start + "-" + end + "/" + jarzData.length);
                    exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(rangeData.length));
                    exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(rangeData));
                    return;
                }
            }
            
            // Handle HEAD request
            if ("HEAD".equals(exchange.getRequestMethod().toString())) {
                exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(jarzData.length));
                exchange.setStatusCode(200);
                return;
            }
            
            // Handle full request
            exchange.getResponseHeaders().put(Headers.CONTENT_LENGTH, String.valueOf(jarzData.length));
            exchange.getResponseSender().send(java.nio.ByteBuffer.wrap(jarzData));
        }
    }
}
