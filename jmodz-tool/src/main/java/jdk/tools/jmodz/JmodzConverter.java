package jdk.tools.jmodz;

import net.jarz.streaming.v2.BlockWriter;
import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.Block;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Converts between jmod and jmodz formats using ZSTD compression
 */
public class JmodzConverter {

    /**
     * Convert .jmod file to .jmodz file with ZSTD compression
     * @return original uncompressed size in bytes
     */
    public long convertJmodToJmodz(Path jmodFile, Path jmodzFile, int compressionLevel) throws IOException {
        long originalCompressedSize = Files.size(jmodFile);
        long totalUncompressedSize = 0;
        
        // Create temporary JARZ file (jmodz uses JARZ v2 format internally)
        Path tempJarz = Files.createTempFile("jmodz-", ".jarz");
        
        try {
            // Extract jmod entries UNCOMPRESSED and compress with ZSTD
            try (ZipFile zipFile = new ZipFile(jmodFile.toFile());
                 BlockWriter writer = new BlockWriter(tempJarz)) {
                
                var entries = zipFile.stream().filter(entry -> !entry.isDirectory()).collect(Collectors.toList());
                Block block = new Block(0);
                
                for (var entry : entries) {
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        byte[] data = is.readAllBytes();
                        totalUncompressedSize += data.length;
                        block.add(entry.getName(), data);
                    }
                }
                writer.writeBlock(block);
            }
            
            // Move temp file to final location
            Files.move(tempJarz, jmodzFile, StandardCopyOption.REPLACE_EXISTING);
            
        } finally {
            Files.deleteIfExists(tempJarz);
        }
        
        return totalUncompressedSize; // Return uncompressed size for fair comparison
    }

    /**
     * Convert .jmodz file back to .jmod file
     */
    public void convertJmodzToJmod(Path jmodzFile, Path jmodFile) throws IOException {
        try (BlockReader reader = new BlockReader(jmodzFile);
             ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(jmodFile))) {
            
            for (String entryName : reader.entryNames()) {
                ZipEntry zipEntry = new ZipEntry(entryName);
                zos.putNextEntry(zipEntry);
                
                byte[] data = reader.readEntry(entryName);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }
}
