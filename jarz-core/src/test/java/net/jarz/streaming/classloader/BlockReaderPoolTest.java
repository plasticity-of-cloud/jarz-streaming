package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.BlockWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BlockReaderPool functionality.
 */
class BlockReaderPoolTest {
    
    @TempDir
    Path tempDir;
    
    private Path testJarzFile;
    
    @BeforeEach
    void setUp() throws Exception {
        testJarzFile = tempDir.resolve("test.jarz");
        createTestJarz();
        BlockReaderPool.clearPool(); // Start with clean pool
    }
    
    private void createTestJarz() throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.test.Main");
        
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        
        try (BlockWriter writer = new BlockWriter(testJarzFile)) {
            Block manifestBlock = new Block(1);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestOut.toByteArray());
            writer.writeBlock(manifestBlock);
            
            Block classBlock = new Block(2);
            classBlock.add("com.test.Main", createSimpleClassBytecode("com.test.Main"));
            writer.writeBlock(classBlock);
        }
    }
    
    private byte[] createSimpleClassBytecode(String className) {
        return new byte[]{
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE,
            0x00, 0x00, 0x00, 0x3D, 0x00, 0x0D
        };
    }
    
    @Test
    @DisplayName("BlockReader pool should share instances")
    void testBlockReaderPooling() throws Exception {
        assertEquals(0, BlockReaderPool.getPoolSize());
        
        // Create multiple ClassLoaders for same JARZ file
        JarzApplicationClassLoader loader1 = new JarzApplicationClassLoader(testJarzFile);
        assertEquals(1, BlockReaderPool.getPoolSize());
        
        JarzApplicationClassLoader loader2 = new JarzApplicationClassLoader(testJarzFile);
        assertEquals(1, BlockReaderPool.getPoolSize()); // Still 1 - shared
        
        // Close first loader
        loader1.close();
        assertEquals(1, BlockReaderPool.getPoolSize()); // Still 1 - second loader using it
        
        // Close second loader
        loader2.close();
        assertEquals(0, BlockReaderPool.getPoolSize()); // Now 0 - all released
    }
    
    @Test
    @DisplayName("BlockReader pool should handle multiple JARZ files")
    void testMultipleJarzFiles() throws Exception {
        Path secondJarz = tempDir.resolve("second.jarz");
        
        // Create second JARZ file
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.test.Second");
        
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        
        try (BlockWriter writer = new BlockWriter(secondJarz)) {
            Block manifestBlock = new Block(1);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestOut.toByteArray());
            writer.writeBlock(manifestBlock);
            
            Block classBlock = new Block(2);
            classBlock.add("com.test.Second", createSimpleClassBytecode("com.test.Second"));
            writer.writeBlock(classBlock);
        }
        
        JarzApplicationClassLoader loader1 = new JarzApplicationClassLoader(testJarzFile);
        JarzApplicationClassLoader loader2 = new JarzApplicationClassLoader(secondJarz);
        
        assertEquals(2, BlockReaderPool.getPoolSize()); // Two different files
        
        loader1.close();
        loader2.close();
        assertEquals(0, BlockReaderPool.getPoolSize());
    }
}
