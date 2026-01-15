package net.jarz.streaming.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JarzClasspathResolver.
 */
class JarzClasspathResolverTest {
    
    @TempDir
    Path tempDir;
    
    private JarzClasspathResolver resolver;
    
    @AfterEach
    void cleanup() throws IOException {
        if (resolver != null) {
            resolver.close();
        }
    }
    
    @Test
    void testEmptyClasspath() throws IOException {
        Path dummyJarz = tempDir.resolve("dummy.jarz");
        Files.createFile(dummyJarz);
        
        resolver = new JarzClasspathResolver(List.of(), dummyJarz);
        
        assertFalse(resolver.hasEntries());
        assertNull(resolver.findClass("com.example.Test"));
        assertNull(resolver.findResource("test.txt"));
    }
    
    @Test
    void testNonExistentFiles() throws IOException {
        Path nonExistent = tempDir.resolve("missing.jarz");
        Path dummyJarz = tempDir.resolve("dummy.jarz");
        Files.createFile(dummyJarz);
        
        resolver = new JarzClasspathResolver(List.of(nonExistent), dummyJarz);
        
        assertFalse(resolver.hasEntries());
        assertNull(resolver.findClass("com.example.Test"));
    }
    
    @Test
    void testClosedResolver() throws IOException {
        Path dummyJarz = tempDir.resolve("dummy.jarz");
        Files.createFile(dummyJarz);
        
        resolver = new JarzClasspathResolver(List.of(), dummyJarz);
        resolver.close();
        
        assertThrows(IllegalStateException.class, () -> resolver.findClass("test"));
        assertThrows(IllegalStateException.class, () -> resolver.findResource("test.txt"));
    }
    
    @Test
    void testMultipleClose() throws IOException {
        Path dummyJarz = tempDir.resolve("dummy.jarz");
        Files.createFile(dummyJarz);
        
        resolver = new JarzClasspathResolver(List.of(), dummyJarz);
        resolver.close();
        
        // Should not throw
        assertDoesNotThrow(() -> resolver.close());
    }
}
