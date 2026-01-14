package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test dependency-aware multi-block conversion with offline ClassLoader.
 * This isolates conversion issues from CDN streaming issues.
 */
class DependencyAwareConversionTest {

    @Test
    void testLog4j2DependencyAwareConversion() throws Exception {
        // Use log4j-api JAR which contains SimpleLogger
        Path log4j2Jar = Paths.get("target/test-jars/log4j-api-2.20.0.jar");
        Assumptions.assumeTrue(Files.exists(log4j2Jar), 
            "log4j-api JAR not found. Run 'mvn generate-test-resources' first.");

        System.out.println("Testing dependency-aware conversion: " + log4j2Jar);
        System.out.println("Original JAR size: " + Files.size(log4j2Jar) + " bytes");

        // Step 1: Convert JAR to JARZ v2 with dependency-aware multi-block grouping
        Path jarzFile = Files.createTempFile("test-log4j2", ".jarz");
        
        try {
            // Analyze dependencies using jdeps
            DependencyAnalyzer analyzer = new DependencyAnalyzer();
            DependencyGraph graph = analyzer.analyze(log4j2Jar);
            
            // Extract class files from JAR
            Map<String, byte[]> classFiles = new HashMap<>();
            Map<String, byte[]> resourceFiles = new HashMap<>();
            
            try (JarFile jarFile = new JarFile(log4j2Jar.toFile())) {
                for (JarEntry entry : jarFile.stream().collect(Collectors.toList())) {
                    if (!entry.isDirectory()) {
                        byte[] content = jarFile.getInputStream(entry).readAllBytes();
                        if (entry.getName().endsWith(".class")) {
                            classFiles.put(entry.getName(), content);
                        } else {
                            resourceFiles.put(entry.getName(), content);
                        }
                    }
                }
            }
            
            // Assign classes to blocks based on dependencies
            BlockAssigner assigner = new BlockAssigner();
            List<Block> blocks = assigner.assignBlocks(classFiles, graph);
            
            // Add resources to appropriate blocks
            if (!resourceFiles.isEmpty()) {
                Block resourceBlock = new Block(blocks.size());
                for (Map.Entry<String, byte[]> entry : resourceFiles.entrySet()) {
                    resourceBlock.add(entry.getKey(), entry.getValue());
                }
                blocks.add(resourceBlock);
            }
            
            // Add manifest with Main-Class for JarzApplicationClassLoader compatibility
            Block manifestBlock = new Block(blocks.size());
            String manifestContent = "Manifest-Version: 1.0\n" +
                                   "Main-Class: org.apache.logging.log4j.LogManager\n\n";
            manifestBlock.add("META-INF/MANIFEST.MF", manifestContent.getBytes());
            blocks.add(manifestBlock);
            
            // Write multi-block JARZ v2
            try (BlockWriter blockWriter = new BlockWriter(jarzFile)) {
                for (Block block : blocks) {
                    blockWriter.writeBlock(block);
                }
            }
            
            int totalEntries = classFiles.size() + resourceFiles.size();
            System.out.println("Converted " + totalEntries + " entries to JARZ v2 (" + blocks.size() + " blocks)");
            System.out.println("JARZ size: " + Files.size(jarzFile) + " bytes");
            
            // Step 2: Test offline ClassLoader with converted JARZ
            try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
                // Test loading the same classes as CDN test
                System.out.println("Testing class loading from offline JARZ...");
                
                Class<?> simpleLoggerClass = loader.loadClass("org.apache.logging.log4j.simple.SimpleLogger");
                Class<?> logManagerClass = loader.loadClass("org.apache.logging.log4j.LogManager");
                Class<?> levelClass = loader.loadClass("org.apache.logging.log4j.Level");
                
                // Verify classes are loaded correctly
                assertThat(simpleLoggerClass).isNotNull();
                assertThat(simpleLoggerClass.getName()).isEqualTo("org.apache.logging.log4j.simple.SimpleLogger");
                assertThat(simpleLoggerClass.getClassLoader()).isEqualTo(loader);
                
                assertThat(logManagerClass).isNotNull();
                assertThat(logManagerClass.getName()).isEqualTo("org.apache.logging.log4j.LogManager");
                
                assertThat(levelClass).isNotNull();
                assertThat(levelClass.getName()).isEqualTo("org.apache.logging.log4j.Level");
                
                System.out.println("✅ All classes loaded successfully from offline JARZ!");
                System.out.println("✅ Dependency-aware multi-block conversion working correctly");
            }
            
        } finally {
            Files.deleteIfExists(jarzFile);
        }
    }
}
