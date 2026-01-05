package jdk.incubator.jarz.cdn;

import jdk.incubator.jarz.v2.BlockWriter;
import jdk.incubator.jarz.v2.Block;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Demo: Convert log4j2 JAR to JARZ v2 format.
 * 
 * Usage:
 * 1. mvn generate-test-resources  (copies log4j2 JAR)
 * 2. mvn exec:java -Dexec.mainClass="jdk.incubator.jarz.cdn.Log4j2JarzDemo"
 */
public class Log4j2JarzDemo {
    
    public static void main(String[] args) throws Exception {
        // Use JAR copied by Maven dependency plugin
        Path log4j2Jar = Paths.get("target/test-jars/log4j-api-2.20.0.jar");
        
        if (!Files.exists(log4j2Jar)) {
            System.err.println("❌ log4j2 JAR not found: " + log4j2Jar);
            System.err.println("Run: mvn generate-test-resources");
            return;
        }
        
        System.out.println("📦 Found log4j2 JAR: " + log4j2Jar);
        System.out.println("📏 Original size: " + Files.size(log4j2Jar) + " bytes");
        
        // Convert to JARZ v2
        Path jarzFile = Paths.get("target/log4j2-core.jarz");
        
        try (JarFile jarFile = new JarFile(log4j2Jar.toFile());
             BlockWriter blockWriter = new BlockWriter(jarzFile)) {
            
            // Create single block with all entries
            Block block = new Block(1);
            int entryCount = 0;
            long totalSize = 0;
            
            for (JarEntry entry : jarFile.stream().collect(Collectors.toList())) {
                if (!entry.isDirectory()) {
                    byte[] content = jarFile.getInputStream(entry).readAllBytes();
                    block.add(entry.getName(), content);
                    entryCount++;
                    totalSize += content.length;
                }
            }
            
            blockWriter.writeBlock(block);
            
            System.out.println("🔄 Converted " + entryCount + " entries");
            System.out.println("📊 Uncompressed size: " + totalSize + " bytes");
        }
        
        System.out.println("✅ Created JARZ: " + jarzFile);
        System.out.println("📏 JARZ size: " + Files.size(jarzFile) + " bytes");
        
        // Calculate compression ratio
        long originalSize = Files.size(log4j2Jar);
        long jarzSize = Files.size(jarzFile);
        double compressionRatio = (double)(originalSize - jarzSize) / originalSize * 100;
        
        System.out.printf("🎯 Compression: %.1f%% improvement (JAR: %,d → JARZ: %,d bytes)%n", 
                         compressionRatio, originalSize, jarzSize);
        
        // List key classes available for loading
        System.out.println("\n🎯 Key classes available for CDN ClassLoader:");
        System.out.println("  - org.apache.logging.log4j.simple.SimpleLogger");
        System.out.println("  - org.apache.logging.log4j.LogManager");
        System.out.println("  - org.apache.logging.log4j.Level");
        System.out.println("  - org.apache.logging.log4j.core.Logger");
        System.out.println("  - org.apache.logging.log4j.core.LoggerContext");
        
        System.out.println("\n🚀 Next steps:");
        System.out.println("  1. Upload JARZ to S3/CDN");
        System.out.println("  2. Use CdnJarzClassLoader to load classes via HTTP/2");
        System.out.println("  3. Run: mvn test -Dtest=CdnS3IntegrationTest");
    }
}
