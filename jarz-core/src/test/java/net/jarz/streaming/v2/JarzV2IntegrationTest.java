package net.jarz.streaming.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for JARZ v2 format with realistic scenarios.
 * Tests end-to-end functionality with real-world class patterns.
 */
class JarzV2IntegrationTest {

    @Test
    void testRealisticApplicationArchive(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("app.jarz");
        
        // Create realistic application structure
        Map<String, byte[]> classes = createRealisticApplication();
        
        // Build dependency graph
        DependencyGraph graph = buildApplicationDependencies(classes.keySet());
        
        // Assign blocks with realistic constraints
        BlockAssigner assigner = new BlockAssigner(64 * 1024, 128 * 1024); // 64KB target, 128KB max
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Write archive
        try (BlockWriter writer = new BlockWriter(jarzFile, 6)) { // Medium compression
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        // Verify archive integrity
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.blockCount()).isEqualTo(blocks.size());
            assertThat(reader.classCount()).isEqualTo(classes.size());
            
            // Verify all classes are readable and correct
            for (var entry : classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertThat(read)
                    .as("Class %s should be readable", entry.getKey())
                    .isNotNull()
                    .isEqualTo(entry.getValue());
            }
        }
        
        // Verify compression effectiveness
        long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
        long compressedSize = Files.size(jarzFile);
        double compressionRatio = (double) compressedSize / originalSize;
        
        assertThat(compressionRatio)
            .as("Compression ratio should be reasonable")
            .isLessThan(0.8); // At least 20% compression
        
        System.out.printf("Application archive: %,d bytes -> %,d bytes (%.1f%% compression)%n",
            originalSize, compressedSize, (1 - compressionRatio) * 100);
    }
    
    @Test
    void testMicroserviceArchive(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("microservice.jarz");
        
        // Create microservice with common patterns
        Map<String, byte[]> classes = createMicroserviceClasses();
        
        // Build dependency graph with service patterns
        DependencyGraph graph = buildMicroserviceDependencies(classes.keySet());
        
        // Use smaller blocks for microservice
        BlockAssigner assigner = new BlockAssigner(32 * 1024, 64 * 1024);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Write with dictionary for better compression
        byte[] dictionary = createMicroserviceDictionary();
        try (BlockWriter writer = new BlockWriter(jarzFile, 9, dictionary)) { // High compression
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        // Verify and test performance characteristics
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // Test random access patterns (typical for microservices)
            String[] testClasses = {
                "com/example/controller/UserController",
                "com/example/service/UserService",
                "com/example/repository/UserRepository",
                "com/example/model/User"
            };
            
            for (String className : testClasses) {
                byte[] data = reader.readClass(className);
                assertThat(data).as("Critical class %s should be accessible", className)
                    .isNotNull();
            }
            
            // Test cache efficiency with repeated access
            for (int i = 0; i < 3; i++) {
                for (String className : testClasses) {
                    byte[] data = reader.readClass(className);
                    assertThat(data).isNotNull();
                }
            }
        }
    }
    
    @Test
    void testLibraryArchive(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("library.jarz");
        
        // Create library with utility classes
        Map<String, byte[]> classes = createLibraryClasses();
        
        // Libraries often have fewer dependencies
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        // Larger blocks for libraries (better compression)
        BlockAssigner assigner = new BlockAssigner(128 * 1024, 256 * 1024);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        try (BlockWriter writer = new BlockWriter(jarzFile, 3)) { // Fast compression for libraries
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        // Verify library characteristics
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // Libraries should have good compression due to similar patterns
            long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
            long compressedSize = Files.size(jarzFile);
            double compressionRatio = (double) compressedSize / originalSize;
            
            assertThat(compressionRatio)
                .as("Library should compress well due to similar patterns")
                .isLessThan(0.6); // At least 40% compression
            
            // Test utility class access patterns
            assertThat(reader.readClass("com/example/util/StringUtils0")).isNotNull();
            assertThat(reader.readClass("com/example/util/CollectionUtils0")).isNotNull();
            assertThat(reader.readClass("com/example/util/DateUtils0")).isNotNull();
        }
    }
    
    @Test
    void testLargeApplicationWithManyBlocks(@TempDir Path tempDir) throws Exception {
        Path jarzFile = tempDir.resolve("large-app.jarz");
        
        // Create large application (1000+ classes)
        Map<String, byte[]> classes = new HashMap<>();
        
        // Multiple packages with many classes each
        String[] packages = {
            "com/example/controller", "com/example/service", "com/example/repository",
            "com/example/model", "com/example/util", "com/example/config",
            "com/example/security", "com/example/validation", "com/example/dto"
        };
        
        for (String pkg : packages) {
            for (int i = 0; i < 120; i++) {
                String className = pkg + "/Class" + i;
                classes.put(className, generateRealisticClassData(className, 3000 + (i % 5000)));
            }
        }
        
        // Build complex dependency graph
        DependencyGraph graph = new DependencyGraph();
        for (String className : classes.keySet()) {
            graph.addClass(className);
            
            // Add some cross-package dependencies
            if (className.contains("controller") && Math.random() < 0.3) {
                String service = className.replace("controller", "service").replace("Controller", "Service");
                if (classes.containsKey(service)) {
                    graph.addEdge(className, service);
                }
            }
            
            if (className.contains("service") && Math.random() < 0.4) {
                String repo = className.replace("service", "repository").replace("Service", "Repository");
                if (classes.containsKey(repo)) {
                    graph.addEdge(className, repo);
                }
            }
        }
        
        // Use moderate block sizes
        BlockAssigner assigner = new BlockAssigner(96 * 1024, 192 * 1024);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        System.out.printf("Large application: %d classes in %d blocks%n", classes.size(), blocks.size());
        
        // Write archive
        try (BlockWriter writer = new BlockWriter(jarzFile, 6)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        
        // Verify large archive handling
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.blockCount()).isEqualTo(blocks.size());
            assertThat(reader.classCount()).isEqualTo(classes.size());
            
            // Test random sampling of classes
            String[] sampleClasses = classes.keySet().stream()
                .limit(50)
                .toArray(String[]::new);
            
            for (String className : sampleClasses) {
                byte[] data = reader.readClass(className);
                assertThat(data).isNotNull();
            }
            
            // Test cache behavior with large number of blocks
            reader.clearCache();
            
            // Access classes from different blocks
            for (String pkg : packages) {
                String className = pkg + "/Class0";
                if (classes.containsKey(className)) {
                    byte[] data = reader.readClass(className);
                    assertThat(data).isNotNull();
                }
            }
        }
        
        // Verify file size is reasonable
        long fileSize = Files.size(jarzFile);
        long originalSize = classes.values().stream().mapToLong(b -> b.length).sum();
        double compressionRatio = (double) fileSize / originalSize;
        
        System.out.printf("Large app compression: %,d -> %,d bytes (%.1f%%)%n",
            originalSize, fileSize, compressionRatio * 100);
        
        assertThat(compressionRatio).isLessThan(0.9); // Some compression expected
    }
    
    private Map<String, byte[]> createRealisticApplication() {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Controllers
        classes.put("com/example/controller/UserController", generateRealisticClassData("UserController", 8000));
        classes.put("com/example/controller/OrderController", generateRealisticClassData("OrderController", 7500));
        classes.put("com/example/controller/ProductController", generateRealisticClassData("ProductController", 6000));
        
        // Services
        classes.put("com/example/service/UserService", generateRealisticClassData("UserService", 12000));
        classes.put("com/example/service/OrderService", generateRealisticClassData("OrderService", 15000));
        classes.put("com/example/service/ProductService", generateRealisticClassData("ProductService", 10000));
        classes.put("com/example/service/EmailService", generateRealisticClassData("EmailService", 5000));
        
        // Repositories
        classes.put("com/example/repository/UserRepository", generateRealisticClassData("UserRepository", 4000));
        classes.put("com/example/repository/OrderRepository", generateRealisticClassData("OrderRepository", 4500));
        classes.put("com/example/repository/ProductRepository", generateRealisticClassData("ProductRepository", 3500));
        
        // Models
        classes.put("com/example/model/User", generateRealisticClassData("User", 3000));
        classes.put("com/example/model/Order", generateRealisticClassData("Order", 4000));
        classes.put("com/example/model/Product", generateRealisticClassData("Product", 2500));
        classes.put("com/example/model/OrderItem", generateRealisticClassData("OrderItem", 2000));
        
        // Utilities
        classes.put("com/example/util/StringUtils", generateRealisticClassData("StringUtils", 6000));
        classes.put("com/example/util/DateUtils", generateRealisticClassData("DateUtils", 4000));
        classes.put("com/example/util/ValidationUtils", generateRealisticClassData("ValidationUtils", 5000));
        
        return classes;
    }
    
    private Map<String, byte[]> createMicroserviceClasses() {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Typical microservice structure
        classes.put("com/example/Application", generateRealisticClassData("Application", 2000));
        classes.put("com/example/controller/UserController", generateRealisticClassData("UserController", 6000));
        classes.put("com/example/service/UserService", generateRealisticClassData("UserService", 8000));
        classes.put("com/example/repository/UserRepository", generateRealisticClassData("UserRepository", 3000));
        classes.put("com/example/model/User", generateRealisticClassData("User", 2500));
        classes.put("com/example/dto/UserDto", generateRealisticClassData("UserDto", 1500));
        classes.put("com/example/config/DatabaseConfig", generateRealisticClassData("DatabaseConfig", 3000));
        classes.put("com/example/config/SecurityConfig", generateRealisticClassData("SecurityConfig", 4000));
        
        return classes;
    }
    
    private Map<String, byte[]> createLibraryClasses() {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Utility library structure
        for (int i = 0; i < 20; i++) {
            classes.put("com/example/util/StringUtils" + i, generateRealisticClassData("StringUtils" + i, 4000));
            classes.put("com/example/util/CollectionUtils" + i, generateRealisticClassData("CollectionUtils" + i, 5000));
            classes.put("com/example/util/DateUtils" + i, generateRealisticClassData("DateUtils" + i, 3500));
            classes.put("com/example/math/MathUtils" + i, generateRealisticClassData("MathUtils" + i, 6000));
        }
        
        return classes;
    }
    
    private DependencyGraph buildApplicationDependencies(java.util.Set<String> classNames) {
        DependencyGraph graph = new DependencyGraph();
        
        for (String className : classNames) {
            graph.addClass(className);
            
            // Add typical dependency patterns
            if (className.contains("Controller")) {
                String service = className.replace("Controller", "Service").replace("controller", "service");
                if (classNames.contains(service)) {
                    graph.addEdge(className, service);
                }
            }
            
            if (className.contains("Service")) {
                String repo = className.replace("Service", "Repository").replace("service", "repository");
                if (classNames.contains(repo)) {
                    graph.addEdge(className, repo);
                }
                
                // Services often use models
                String model = className.replace("Service", "").replace("service", "model");
                if (classNames.contains(model)) {
                    graph.addEdge(className, model);
                }
            }
        }
        
        return graph;
    }
    
    private DependencyGraph buildMicroserviceDependencies(java.util.Set<String> classNames) {
        DependencyGraph graph = new DependencyGraph();
        
        for (String className : classNames) {
            graph.addClass(className);
            
            // Microservice dependency patterns
            if (className.contains("Controller")) {
                graph.addEdge(className, "com/example/service/UserService");
            }
            if (className.contains("Service")) {
                graph.addEdge(className, "com/example/repository/UserRepository");
                graph.addEdge(className, "com/example/model/User");
            }
        }
        
        return graph;
    }
    
    private byte[] createMicroserviceDictionary() {
        String patterns = "@RestController\n" +
            "@Service\n" +
            "@Repository\n" +
            "@Entity\n" +
            "@Autowired\n" +
            "@RequestMapping\n" +
            "@GetMapping\n" +
            "@PostMapping\n" +
            "@PathVariable\n" +
            "@RequestBody\n" +
            "org/springframework\n" +
            "javax/persistence\n";
        return patterns.getBytes();
    }
    
    private byte[] generateRealisticClassData(String className, int size) {
        byte[] data = new byte[size];
        
        // Class file magic and version
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        data[4] = 0;
        data[5] = 0;
        data[6] = 0;
        data[7] = 65; // Java 21
        
        // Common patterns based on class type
        String[] patterns;
        if (className.contains("Controller")) {
            patterns = new String[]{"@RestController", "@RequestMapping", "@Autowired", "ResponseEntity", "HttpStatus"};
        } else if (className.contains("Service")) {
            patterns = new String[]{"@Service", "@Transactional", "@Autowired", "BusinessException", "Logger"};
        } else if (className.contains("Repository")) {
            patterns = new String[]{"@Repository", "JpaRepository", "Query", "Param", "EntityManager"};
        } else if (className.contains("Model") || className.contains("Entity")) {
            patterns = new String[]{"@Entity", "@Table", "@Id", "@Column", "@GeneratedValue"};
        } else {
            patterns = new String[]{"java/lang/Object", "java/lang/String", "<init>", "()V", "toString"};
        }
        
        // Fill with patterns
        int pos = 8;
        int patternIdx = 0;
        while (pos < size - 20) {
            byte[] pattern = patterns[patternIdx % patterns.length].getBytes();
            int len = Math.min(pattern.length, size - pos);
            System.arraycopy(pattern, 0, data, pos, len);
            pos += len;
            patternIdx++;
        }
        
        return data;
    }
}
