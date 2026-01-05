package jdk.incubator.jarz.v2;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Optimized local index that caches JARZ metadata to minimize CDN requests.
 * 
 * Contains: Header + Footer + Index + Metadata
 * Reduces CDN requests from 6 to 3 (50% improvement).
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class OptimizedJarzLocalIndex {
    
    public static final byte[] MAGIC = "JIDX".getBytes(StandardCharsets.UTF_8);
    public static final int VERSION = 2; // Optimized version
    
    private final String originalJarzUrl;
    private final long originalJarzSize;
    private final long timestamp;
    
    // Cached JARZ components
    private final byte[] jarzHeader;
    private final byte[] jarzFooter;
    private final byte[] jarzIndex;
    
    public OptimizedJarzLocalIndex(String originalJarzUrl, long originalJarzSize, 
                                  byte[] jarzHeader, byte[] jarzFooter, byte[] jarzIndex) {
        this.originalJarzUrl = originalJarzUrl;
        this.originalJarzSize = originalJarzSize;
        this.timestamp = System.currentTimeMillis();
        this.jarzHeader = jarzHeader.clone();
        this.jarzFooter = jarzFooter.clone();
        this.jarzIndex = jarzIndex.clone();
    }
    
    /**
     * Create optimized local index from existing JARZ file.
     */
    public static OptimizedJarzLocalIndex createFromJarz(String jarzUrl, Path jarzPath) throws IOException {
        try (FileJarzDataProvider provider = new FileJarzDataProvider(jarzPath)) {
            // Read header (first 32 bytes)
            byte[] header = provider.readBytes(0, JarzV2Format.HEADER_SIZE);
            
            // Read footer 
            byte[] footer = provider.readFooter();
            
            // Extract index offset from footer
            ByteBuffer footerBuf = ByteBuffer.wrap(footer).order(JarzV2Format.BYTE_ORDER);
            long indexOffset = footerBuf.getLong();
            long fileSize = footerBuf.getLong();
            
            // Calculate index size and read index
            int indexSize = (int) (fileSize - indexOffset - JarzV2Format.FOOTER_SIZE);
            byte[] index = provider.readBytes(indexOffset, indexSize);
            
            return new OptimizedJarzLocalIndex(jarzUrl, fileSize, header, footer, index);
        }
    }
    
    /**
     * Save optimized local index to file.
     */
    public void save(Path outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            // Write magic and version
            bos.write(MAGIC);
            
            ByteBuffer header = ByteBuffer.allocate(24).order(JarzV2Format.BYTE_ORDER);
            header.putInt(VERSION);
            header.putLong(originalJarzSize);
            header.putLong(timestamp);
            header.putInt(jarzHeader.length);
            bos.write(header.array());
            
            // Write original URL
            byte[] urlBytes = originalJarzUrl.getBytes(StandardCharsets.UTF_8);
            ByteBuffer urlHeader = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
            urlHeader.putInt(urlBytes.length);
            bos.write(urlHeader.array());
            bos.write(urlBytes);
            
            // Write cached JARZ components
            bos.write(jarzHeader);
            
            ByteBuffer footerHeader = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
            footerHeader.putInt(jarzFooter.length);
            bos.write(footerHeader.array());
            bos.write(jarzFooter);
            
            ByteBuffer indexHeader = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
            indexHeader.putInt(jarzIndex.length);
            bos.write(indexHeader.array());
            bos.write(jarzIndex);
        }
    }
    
    /**
     * Load optimized local index from file.
     */
    public static OptimizedJarzLocalIndex load(Path indexPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(indexPath.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
            // Read and verify magic
            byte[] magic = new byte[4];
            bis.readNBytes(magic, 0, 4);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid optimized local index magic");
            }
            
            // Read header
            byte[] headerBytes = new byte[24];
            bis.readNBytes(headerBytes, 0, 24);
            ByteBuffer header = ByteBuffer.wrap(headerBytes).order(JarzV2Format.BYTE_ORDER);
            
            int version = header.getInt();
            if (version != VERSION) {
                throw new IOException("Unsupported optimized local index version: " + version);
            }
            
            long originalSize = header.getLong();
            long timestamp = header.getLong();
            int jarzHeaderSize = header.getInt();
            
            // Read original URL
            byte[] urlLengthBytes = new byte[4];
            bis.readNBytes(urlLengthBytes, 0, 4);
            int urlLength = ByteBuffer.wrap(urlLengthBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            byte[] urlBytes = new byte[urlLength];
            bis.readNBytes(urlBytes, 0, urlLength);
            String originalUrl = new String(urlBytes, StandardCharsets.UTF_8);
            
            // Read cached JARZ components
            byte[] jarzHeader = new byte[jarzHeaderSize];
            bis.readNBytes(jarzHeader, 0, jarzHeaderSize);
            
            byte[] footerLengthBytes = new byte[4];
            bis.readNBytes(footerLengthBytes, 0, 4);
            int footerLength = ByteBuffer.wrap(footerLengthBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            byte[] jarzFooter = new byte[footerLength];
            bis.readNBytes(jarzFooter, 0, footerLength);
            
            byte[] indexLengthBytes = new byte[4];
            bis.readNBytes(indexLengthBytes, 0, 4);
            int indexLength = ByteBuffer.wrap(indexLengthBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            byte[] jarzIndex = new byte[indexLength];
            bis.readNBytes(jarzIndex, 0, indexLength);
            
            return new OptimizedJarzLocalIndex(originalUrl, originalSize, jarzHeader, jarzFooter, jarzIndex);
        }
    }
    
    // Getters
    public String getOriginalJarzUrl() { return originalJarzUrl; }
    public long getOriginalJarzSize() { return originalJarzSize; }
    public long getTimestamp() { return timestamp; }
    public byte[] getJarzHeader() { return jarzHeader.clone(); }
    public byte[] getJarzFooter() { return jarzFooter.clone(); }
    public byte[] getJarzIndex() { return jarzIndex.clone(); }
    
    /**
     * Check if index is still valid (not expired).
     */
    public boolean isValid(long maxAgeMillis) {
        return (System.currentTimeMillis() - timestamp) < maxAgeMillis;
    }
}
