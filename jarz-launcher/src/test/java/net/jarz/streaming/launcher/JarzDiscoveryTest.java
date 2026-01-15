package net.jarz.streaming.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.jar.Attributes;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JARZ auto-discovery functionality.
 */
class JarzDiscoveryTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testDiscoverFromSingleFile() throws Exception {
        // Create a test JARZ file with manifest
        Path jarzFile = tempDir.resolve("test-app.jarz");
        createJarzWithManifest(jarzFile, "com.example.TestApp");
        
        UniversalJarzLauncher.LaunchConfig config = new UniversalJarzLauncher.LaunchConfig();
        config.jarzPath = jarzFile.toString();
        
        JarzDiscovery discovery = new JarzDiscovery(config);
        UniversalJarzLauncher.DiscoveryResult result = discovery.discover();
        
        assertEquals("com.example.TestApp", result.mainClass);
        assertEquals(1, result.jarzFiles.size());
        assertEquals(jarzFile, result.jarzFiles.get(0));
    }
    
    @Test
    void testDiscoverFromDirectory() throws Exception {
        // Create multiple JARZ files
        createJarzWithManifest(tempDir.resolve("kafka-server.jarz"), "kafka.Kafka");
        createJarzWithManifest(tempDir.resolve("kafka-clients.jarz"), null);
        createJarzWithManifest(tempDir.resolve("scala-library.jarz"), null);
        
        UniversalJarzLauncher.LaunchConfig config = new UniversalJarzLauncher.LaunchConfig();
        config.jarzPath = tempDir.toString();
        
        JarzDiscovery discovery = new JarzDiscovery(config);
        UniversalJarzLauncher.DiscoveryResult result = discovery.discover();
        
        assertEquals("kafka.Kafka", result.mainClass);
        assertEquals(3, result.jarzFiles.size());
        
        // Verify priority ordering (server should be first)
        assertTrue(result.jarzFiles.get(0).getFileName().toString().contains("server"));
    }
    
    @Test
    void testOverrideMainClass() throws Exception {
        Path jarzFile = tempDir.resolve("test-app.jarz");
        createJarzWithManifest(jarzFile, "com.example.TestApp");
        
        UniversalJarzLauncher.LaunchConfig config = new UniversalJarzLauncher.LaunchConfig();
        config.jarzPath = jarzFile.toString();
        config.mainClass = "com.example.OverrideApp";
        
        JarzDiscovery discovery = new JarzDiscovery(config);
        UniversalJarzLauncher.DiscoveryResult result = discovery.discover();
        
        assertEquals("com.example.OverrideApp", result.mainClass);
    }
    
    private void createJarzWithManifest(Path jarzFile, String mainClass) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarzFile), manifest)) {
            // Empty JARZ file with just manifest
        }
    }
}
