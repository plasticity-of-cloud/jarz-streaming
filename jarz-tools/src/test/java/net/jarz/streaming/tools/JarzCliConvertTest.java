package net.jarz.streaming.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class JarzCliConvertTest {
    
    @Test
    void testConvertCommand(@TempDir Path tempDir) throws Exception {
        // Use the log4j JAR from test resources
        Path jarFile = Paths.get("target/test-jars/log4j-api-2.20.0.jar");
        if (!Files.exists(jarFile)) {
            // Skip test if JAR not available
            return;
        }
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        // Test convert command
        String[] args = {"convert", jarFile.toString(), jarzFile.toString()};
        
        // Should not throw exception
        assertDoesNotThrow(() -> JarzCli.run(args));
        
        // Verify JARZ file was created
        assertTrue(Files.exists(jarzFile));
        assertTrue(Files.size(jarzFile) > 0);
        
        // Verify it's smaller than original (compression)
        assertTrue(Files.size(jarzFile) < Files.size(jarFile));
    }
    
    @Test
    void testConvertCommandWithInvalidJar(@TempDir Path tempDir) throws Exception {
        Path nonExistentJar = tempDir.resolve("nonexistent.jar");
        Path outputJarz = tempDir.resolve("output.jarz");
        
        String[] args = {"convert", nonExistentJar.toString(), outputJarz.toString()};
        
        // Should throw exception for non-existent file
        assertThrows(IllegalArgumentException.class, () -> JarzCli.run(args));
    }
    
    @Test
    void testConvertCommandWithNonJarFile(@TempDir Path tempDir) throws Exception {
        Path textFile = tempDir.resolve("test.txt");
        Files.writeString(textFile, "not a jar file");
        Path outputJarz = tempDir.resolve("output.jarz");
        
        String[] args = {"convert", textFile.toString(), outputJarz.toString()};
        
        // Should throw exception for non-JAR file
        assertThrows(IllegalArgumentException.class, () -> JarzCli.run(args));
    }
}
