package net.jarz.streaming.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test CRC32 archive integrity checking in JARZ v2 format.
 */
public class ArchiveIntegrityTest {

    @TempDir
    Path tempDir;

    @Test
    public void testCRC32IntegrityValidation() throws IOException {
        Path jarzFile = tempDir.resolve("test-integrity.jarz");
        
        // Create a JARZ file with some test data
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            Block block1 = new Block(1);
            block1.add("TestClass", "com.example.TestClass".getBytes());
            writer.writeBlock(block1);
            
            Block block2 = new Block(2);
            block2.add("AnotherClass", "com.example.AnotherClass".getBytes());
            writer.writeBlock(block2);
        }
        
        // Verify the file can be read successfully (CRC32 should match)
        try (BlockReader reader = new BlockReader(new FileJarzDataProvider(jarzFile))) {
            assertEquals(2, reader.classCount());
            assertTrue(reader.classNames().contains("TestClass"));
            assertTrue(reader.classNames().contains("AnotherClass"));
        }
        
        // Corrupt the file by modifying a byte in the middle
        byte[] fileBytes = Files.readAllBytes(jarzFile);
        fileBytes[fileBytes.length / 2] ^= 0xFF; // Flip all bits in middle byte
        Files.write(jarzFile, fileBytes);
        
        // Verify that reading the corrupted file throws an IOException due to CRC32 mismatch
        IOException exception = assertThrows(IOException.class, () -> {
            try (BlockReader reader = new BlockReader(new FileJarzDataProvider(jarzFile))) {
                // Should fail during initialization
            }
        });
        
        assertTrue(exception.getMessage().contains("CRC32 mismatch"), 
                   "Expected CRC32 mismatch error, got: " + exception.getMessage());
    }
    
    @Test
    public void testCRC32WithDictionary() throws IOException {
        Path jarzFile = tempDir.resolve("test-integrity-dict.jarz");
        byte[] dictionary = "test dictionary data".getBytes();
        
        // Create a JARZ file with dictionary
        try (BlockWriter writer = new BlockWriter(jarzFile, 3, dictionary)) {
            Block block = new Block(1);
            block.add("TestClass", "com.example.TestClass".getBytes());
            writer.writeBlock(block);
        }
        
        // Verify the file can be read successfully
        try (BlockReader reader = new BlockReader(new FileJarzDataProvider(jarzFile))) {
            assertEquals(1, reader.classCount());
            assertTrue(reader.classNames().contains("TestClass"));
        }
        
        // Corrupt the dictionary area
        byte[] fileBytes = Files.readAllBytes(jarzFile);
        fileBytes[40] ^= 0xFF; // Corrupt dictionary area
        Files.write(jarzFile, fileBytes);
        
        // Verify that reading the corrupted file throws an IOException
        IOException exception = assertThrows(IOException.class, () -> {
            try (BlockReader reader = new BlockReader(new FileJarzDataProvider(jarzFile))) {
                // Should fail during initialization
            }
        });
        
        assertTrue(exception.getMessage().contains("CRC32 mismatch"), 
                   "Expected CRC32 mismatch error, got: " + exception.getMessage());
    }
}
