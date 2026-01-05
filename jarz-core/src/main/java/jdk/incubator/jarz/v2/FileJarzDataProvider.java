package jdk.incubator.jarz.v2;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

/**
 * Local file implementation using RandomAccessFile.
 * Direct replacement for current BlockReader file access.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class FileJarzDataProvider implements JarzDataProvider {
    private final RandomAccessFile raf;
    private final long fileSize;
    
    public FileJarzDataProvider(Path filePath) throws IOException {
        this.raf = new RandomAccessFile(filePath.toFile(), "r");
        this.fileSize = raf.length();
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > fileSize) {
            throw new IOException("Invalid read range: offset=" + offset + ", length=" + length + ", fileSize=" + fileSize);
        }
        
        byte[] buffer = new byte[length];
        raf.seek(offset);
        raf.readFully(buffer);
        return buffer;
    }
    
    @Override
    public long getFileSize() throws IOException {
        return fileSize;
    }
    
    @Override
    public void close() throws IOException {
        raf.close();
    }
}
