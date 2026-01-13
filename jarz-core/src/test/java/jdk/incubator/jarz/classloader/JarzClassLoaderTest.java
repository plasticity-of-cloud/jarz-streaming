package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.Block;
import jdk.incubator.jarz.v2.BlockWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JarzClassLoader base functionality.
 */
class JarzClassLoaderTest {
    
    @TempDir
    Path tempDir;
    
    private Path libraryJarzFile;
    
    @BeforeEach
    void setUp() throws Exception {
        libraryJarzFile = tempDir.resolve("library.jarz");
        createLibraryJarz();
    }
    
    private void createLibraryJarz() throws Exception {
        try (BlockWriter writer = new BlockWriter(libraryJarzFile)) {
            // Create a library JARZ without Main-Class (optional manifest)
            Block classBlock = new Block(1);
            classBlock.add("com.library.Utils", createSimpleClassBytecode("com.library.Utils"));
            writer.writeBlock(classBlock);
            
            // Optional manifest without Main-Class
            Block manifestBlock = new Block(2);
            String manifestContent = "Manifest-Version: 1.0\n" +
                                   "Implementation-Title: Test Library\n" +
                                   "Implementation-Version: 1.0\n\n";
            manifestBlock.add("META-INF/MANIFEST.MF", manifestContent.getBytes());
            writer.writeBlock(manifestBlock);
        }
    }
    
    private byte[] createSimpleClassBytecode(String className) {
        // Minimal valid class bytecode for testing
        return new byte[]{
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
            0x00, 0x00, 0x00, 0x3D, // version
            0x00, 0x0D, // constant pool count
            // Minimal constant pool and class structure
            0x07, 0x00, 0x02, // Class info
            0x01, 0x00, (byte) className.length(), // UTF8 info
        };
    }
    
    @Test
    @DisplayName("JarzClassLoader should work without Main-Class")
    void testLibraryLoading() throws Exception {
        try (SimpleJarzClassLoader loader = new SimpleJarzClassLoader(libraryJarzFile)) {
            assertNotNull(loader.getManifest());
            // Should not throw - library loading doesn't require Main-Class
        }
    }
    
    @Test
    @DisplayName("JarzClassLoader should handle missing manifest gracefully")
    void testMissingManifestAllowed() throws Exception {
        Path noManifestJarz = tempDir.resolve("no-manifest.jarz");
        
        try (BlockWriter writer = new BlockWriter(noManifestJarz)) {
            Block classBlock = new Block(1);
            classBlock.add("com.library.Test", createSimpleClassBytecode("com.library.Test"));
            writer.writeBlock(classBlock);
        }
        
        // Should not throw - library loading allows missing manifest
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(noManifestJarz)) {
            assertNotNull(loader.getManifest());
        }
    }
    
    @Test
    @DisplayName("JarzClassLoader toString should be meaningful")
    void testToString() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(libraryJarzFile)) {
            String toString = loader.toString();
            assertTrue(toString.contains("JarzApplicationClassLoader"));
            assertTrue(toString.contains("closed=false"));
        }
    }
}
