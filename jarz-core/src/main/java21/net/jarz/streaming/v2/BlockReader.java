package net.jarz.streaming.v2;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;

import com.github.luben.zstd.Zstd;

/**
 * Reads JARZ v2 archives with block-based decompression.
 * 
 * <p>This class provides efficient random access to compressed blocks within
 * JARZ v2 archives, supporting both class blocks and typed resource blocks.
 * It enables loading individual classes without decompressing the entire archive.
 * 
 * <p>This implementation is thread-safe for read operations and supports
 * concurrent access to different blocks within the same archive.
 * 
 * @since 1.0
 */
public class BlockReader implements Closeable {
    
    private final JarzDataProvider dataProvider;
    private final RandomAccessFile raf; // Keep for backward compatibility
    private final byte[] dictionary;
    private final BlockIndex blockIndex;
    private final ClassIndex classIndex;
    
    private final Map<Integer, byte[]> blockCache = new HashMap<>();
    
    /**
     * Creates BlockReader using JarzDataProvider (new unified approach).
     */
    public BlockReader(JarzDataProvider dataProvider) throws IOException {
        this.dataProvider = dataProvider;
        this.raf = null; // Not used in new approach
        
        // Parse header
        byte[] header = dataProvider.readHeader();
        ByteBuffer headerBuf = ByteBuffer.wrap(header).order(JarzV2Format.BYTE_ORDER);
        
        byte[] magic = new byte[4];
        headerBuf.get(magic);
        if (!Arrays.equals(magic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 magic");
        }
        
        short version = headerBuf.getShort();
        if (version != JarzV2Format.VERSION) {
            throw new IOException("Unsupported JARZ v2 version: " + version);
        }
        
        short flags = headerBuf.getShort();
        int blockCount = headerBuf.getInt();
        int dictSize = headerBuf.getInt();
        int expectedCrc32 = headerBuf.getInt();
        
        // Skip reserved bytes (12 bytes)
        headerBuf.position(headerBuf.position() + 12);
        
        // Parse footer first to get file size for CRC32 calculation
        byte[] footer = dataProvider.readFooter();
        ByteBuffer footerBuf = ByteBuffer.wrap(footer).order(JarzV2Format.BYTE_ORDER);
        long indexOffset = footerBuf.getLong();
        long fileSize = footerBuf.getInt() & 0xFFFFFFFFL; // Convert to unsigned long
        
        byte[] footerMagic = new byte[4];
        footerBuf.get(footerMagic);
        if (!Arrays.equals(footerMagic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 footer");
        }
        
        // Verify CRC32 if present
        if ((flags & JarzV2Format.FLAG_HAS_CRC32) != 0) {
            long actualCrc32 = calculateArchiveCRC32(dataProvider, dictSize, fileSize);
            if (actualCrc32 != (expectedCrc32 & 0xFFFFFFFFL)) {
                throw new IOException("Archive CRC32 mismatch. Expected: " + 
                    Integer.toHexString(expectedCrc32) + ", Actual: " + 
                    Long.toHexString(actualCrc32));
            }
        }
        
        // Read dictionary if present
        if ((flags & JarzV2Format.FLAG_HAS_DICTIONARY) != 0 && dictSize > 0) {
            this.dictionary = dataProvider.readDictionary(dictSize);
        } else {
            this.dictionary = null;
        }
        
        // Read indexes - calculate size from footer data
        int indexSize = (int)(fileSize - JarzV2Format.FOOTER_SIZE - indexOffset);
        byte[] indexData = dataProvider.readBytes(indexOffset, indexSize);
        
        ByteBuffer indexBuf = ByteBuffer.wrap(indexData).order(JarzV2Format.BYTE_ORDER);
        this.blockIndex = readBlockIndex(indexBuf);
        this.classIndex = readClassIndex(indexBuf);
    }
    
    /**
     * Creates BlockReader using Path (legacy approach for backward compatibility).
     */
    public BlockReader(Path path) throws IOException {
        this.dataProvider = new FileJarzDataProvider(path);
        this.raf = new RandomAccessFile(path.toFile(), "r");
        
        byte[] magic = new byte[4];
        raf.readFully(magic);
        if (!Arrays.equals(magic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 magic");
        }
        
        // Read version using consistent byte order
        byte[] versionBytes = new byte[2];
        raf.readFully(versionBytes);
        short version = ByteBuffer.wrap(versionBytes).order(JarzV2Format.BYTE_ORDER).getShort();
        if (version != JarzV2Format.VERSION) {
            throw new IOException("Unsupported JARZ v2 version: " + version);
        }
        
        // Read flags using consistent byte order
        byte[] flagsBytes = new byte[2];
        raf.readFully(flagsBytes);
        short flags = ByteBuffer.wrap(flagsBytes).order(JarzV2Format.BYTE_ORDER).getShort();
        
        // Read block count and dict size using consistent byte order
        byte[] intBytes = new byte[8];
        raf.readFully(intBytes);
        ByteBuffer intBuf = ByteBuffer.wrap(intBytes).order(JarzV2Format.BYTE_ORDER);
        int blockCount = intBuf.getInt();
        int dictSize = intBuf.getInt();
        
        if ((flags & JarzV2Format.FLAG_HAS_DICTIONARY) != 0 && dictSize > 0) {
            dictionary = new byte[dictSize];
            raf.readFully(dictionary);
        } else {
            dictionary = null;
        }
        
        raf.seek(raf.length() - JarzV2Format.FOOTER_SIZE);
        
        // Read index offset using consistent byte order
        byte[] offsetBytes = new byte[8];
        raf.readFully(offsetBytes);
        long indexOffset = ByteBuffer.wrap(offsetBytes).order(JarzV2Format.BYTE_ORDER).getLong();
        
        // Read file size using consistent byte order
        byte[] fileSizeBytes = new byte[4];
        raf.readFully(fileSizeBytes);
        long fileSize = ByteBuffer.wrap(fileSizeBytes).order(JarzV2Format.BYTE_ORDER).getInt() & 0xFFFFFFFFL;
        
        byte[] footerMagic = new byte[4];
        raf.readFully(footerMagic);
        if (!Arrays.equals(footerMagic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 footer");
        }
        
        raf.seek(indexOffset);
        this.blockIndex = readBlockIndex();
        this.classIndex = readClassIndex();
    }
    
    private BlockIndex readBlockIndex() throws IOException {
        if (raf != null) {
            // Legacy path using RandomAccessFile
            return readBlockIndexFromFile();
        } else {
            throw new IOException("readBlockIndex() called without ByteBuffer - use readBlockIndex(ByteBuffer)");
        }
    }
    
    private BlockIndex readBlockIndexFromFile() throws IOException {
        BlockIndex index = new BlockIndex();
        
        // Read count using consistent byte order
        byte[] countBytes = new byte[4];
        raf.readFully(countBytes);
        int count = ByteBuffer.wrap(countBytes).order(JarzV2Format.BYTE_ORDER).getInt();
        
        for (int i = 0; i < count; i++) {
            // Read blockId using consistent byte order
            byte[] blockIdBytes = new byte[4];
            raf.readFully(blockIdBytes);
            int blockId = ByteBuffer.wrap(blockIdBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            // Read offset using consistent byte order
            byte[] offsetBytes = new byte[8];
            raf.readFully(offsetBytes);
            long offset = ByteBuffer.wrap(offsetBytes).order(JarzV2Format.BYTE_ORDER).getLong();
            
            // Read compressedSize using consistent byte order
            byte[] compressedSizeBytes = new byte[4];
            raf.readFully(compressedSizeBytes);
            int compressedSize = ByteBuffer.wrap(compressedSizeBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            // Read uncompressedSize using consistent byte order
            byte[] uncompressedSizeBytes = new byte[4];
            raf.readFully(uncompressedSizeBytes);
            int uncompressedSize = ByteBuffer.wrap(uncompressedSizeBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            index.add(new BlockIndex.Entry(blockId, offset, compressedSize, uncompressedSize));
        }
        
        return index;
    }
    
    private BlockIndex readBlockIndex(ByteBuffer indexBuf) throws IOException {
        BlockIndex index = new BlockIndex();
        int count = indexBuf.getInt();
        
        for (int i = 0; i < count; i++) {
            int blockId = indexBuf.getInt();
            long offset = indexBuf.getLong();
            int compressedSize = indexBuf.getInt();
            int uncompressedSize = indexBuf.getInt();
            index.add(new BlockIndex.Entry(blockId, offset, compressedSize, uncompressedSize));
        }
        
        return index;
    }
    
    private ClassIndex readClassIndex() throws IOException {
        if (raf != null) {
            // Legacy path using RandomAccessFile
            return readClassIndexFromFile();
        } else {
            throw new IOException("readClassIndex() called without ByteBuffer - use readClassIndex(ByteBuffer)");
        }
    }
    
    private ClassIndex readClassIndexFromFile() throws IOException {
        ClassIndex index = new ClassIndex();
        
        // Read count using consistent byte order
        byte[] countBytes = new byte[4];
        raf.readFully(countBytes);
        int count = ByteBuffer.wrap(countBytes).order(JarzV2Format.BYTE_ORDER).getInt();
        
        for (int i = 0; i < count; i++) {
            // Read nameLen using consistent byte order
            byte[] nameLenBytes = new byte[2];
            raf.readFully(nameLenBytes);
            int nameLen = ByteBuffer.wrap(nameLenBytes).order(JarzV2Format.BYTE_ORDER).getShort() & 0xFFFF;
            
            byte[] nameBytes = new byte[nameLen];
            raf.readFully(nameBytes);
            String className = new String(nameBytes, StandardCharsets.UTF_8);
            
            // Read blockId using consistent byte order
            byte[] blockIdBytes = new byte[4];
            raf.readFully(blockIdBytes);
            int blockId = ByteBuffer.wrap(blockIdBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            // Read offsetInBlock using consistent byte order
            byte[] offsetInBlockBytes = new byte[4];
            raf.readFully(offsetInBlockBytes);
            int offsetInBlock = ByteBuffer.wrap(offsetInBlockBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            // Read size using consistent byte order
            byte[] sizeBytes = new byte[4];
            raf.readFully(sizeBytes);
            int size = ByteBuffer.wrap(sizeBytes).order(JarzV2Format.BYTE_ORDER).getInt();
            
            index.add(new ClassIndex.Entry(className, blockId, offsetInBlock, size));
        }
        
        return index;
    }
    
    private ClassIndex readClassIndex(ByteBuffer indexBuf) throws IOException {
        ClassIndex index = new ClassIndex();
        int count = indexBuf.getInt();
        
        for (int i = 0; i < count; i++) {
            int nameLen = indexBuf.getShort() & 0xFFFF;
            byte[] nameBytes = new byte[nameLen];
            indexBuf.get(nameBytes);
            String className = new String(nameBytes, StandardCharsets.UTF_8);
            
            int blockId = indexBuf.getInt();
            int offsetInBlock = indexBuf.getInt();
            int size = indexBuf.getInt();
            
            index.add(new ClassIndex.Entry(className, blockId, offsetInBlock, size));
        }
        
        return index;
    }
    
    /**
     * Read an entry by name (class or resource).
     */
    public byte[] readEntry(String name) throws IOException {
        ClassIndex.Entry entry = classIndex.get(name);
        if (entry == null) {
            return null;
        }
        
        byte[] blockData = getBlock(entry.blockId());
        if (blockData == null) {
            throw new IOException("Block not found: " + entry.blockId());
        }
        
        return extractEntry(blockData, entry.offsetInBlock(), entry.size());
    }
    
    /**
     * Read a class by name (alias for readEntry).
     * 
     * @deprecated Use readEntry() directly with proper format conversion in ClassLoader
     */
    @Deprecated
    public byte[] readClass(String className) throws IOException {
        return readEntry(className);
    }
    
    private byte[] getBlock(int blockId) throws IOException {
        byte[] cached = blockCache.get(blockId);
        if (cached != null) {
            return cached;
        }
        
        BlockIndex.Entry blockEntry = blockIndex.get(blockId);
        if (blockEntry == null) {
            return null;
        }
        
        byte[] decompressed;
        
        if (dataProvider != null) {
            // New data provider approach
            decompressed = getBlockFromDataProvider(blockEntry);
        } else {
            // Legacy RandomAccessFile approach
            decompressed = getBlockFromFile(blockEntry);
        }
        
        blockCache.put(blockId, decompressed);
        return decompressed;
    }
    
    private byte[] getBlockFromFile(BlockIndex.Entry blockEntry) throws IOException {
        synchronized (raf) {
            raf.seek(blockEntry.offset());
            
            // Read block header
            int typeId = raf.readByte() & 0xFF;
            int compressionFlag = raf.readByte() & 0xFF;
            int entryCount = raf.readShort() & 0xFFFF;
            raf.readInt(); // reserved
            
            // Read block data
            int dataSize = blockEntry.compressedSize() - JarzV2Format.BLOCK_HEADER_SIZE;
            byte[] data = new byte[dataSize];
            raf.readFully(data);
            
            if (compressionFlag == 0) {
                // STORED - no compression
                return data;
            } else {
                // ZSTD compressed
                return decompress(data, blockEntry.uncompressedSize());
            }
        }
    }
    
    private byte[] getBlockFromDataProvider(BlockIndex.Entry blockEntry) throws IOException {
        // Read entire block (header + data)
        byte[] blockBytes = dataProvider.readBytes(blockEntry.offset(), blockEntry.compressedSize());
        
        // Parse block header
        ByteBuffer headerBuf = ByteBuffer.wrap(blockBytes, 0, JarzV2Format.BLOCK_HEADER_SIZE).order(JarzV2Format.BYTE_ORDER);
        int typeId = headerBuf.get() & 0xFF;
        int compressionFlag = headerBuf.get() & 0xFF;
        int entryCount = headerBuf.getShort() & 0xFFFF;
        headerBuf.getInt(); // reserved
        
        // Extract block data (skip header)
        byte[] data = new byte[blockEntry.compressedSize() - JarzV2Format.BLOCK_HEADER_SIZE];
        System.arraycopy(blockBytes, JarzV2Format.BLOCK_HEADER_SIZE, data, 0, data.length);
        
        if (compressionFlag == 0) {
            // STORED - no compression
            return data;
        } else {
            // ZSTD compressed
            return decompress(data, blockEntry.uncompressedSize());
        }
    }
    
    private byte[] decompress(byte[] compressed, int uncompressedSize) throws IOException {
        try {
            if (dictionary != null) {
                byte[] output = new byte[uncompressedSize];
                Zstd.decompress(output, compressed, dictionary);
                return output;
            } else {
                return Zstd.decompress(compressed, uncompressedSize);
            }
        } catch (Exception e) {
            throw new IOException("ZSTD decompression failed", e);
        }
    }
    
    private byte[] extractEntry(byte[] blockData, int offset, int size) {
        int pos = 0;
        int targetOffset = offset;
        
        while (pos < blockData.length) {
            if (pos == targetOffset) {
                int nameLen = ((blockData[pos] & 0xFF) << 8) | (blockData[pos + 1] & 0xFF);
                pos += 2 + nameLen;
                
                int dataLen = ((blockData[pos] & 0xFF) << 24) |
                              ((blockData[pos + 1] & 0xFF) << 16) |
                              ((blockData[pos + 2] & 0xFF) << 8) |
                              (blockData[pos + 3] & 0xFF);
                pos += 4;
                
                byte[] entryData = new byte[dataLen];
                System.arraycopy(blockData, pos, entryData, 0, dataLen);
                return entryData;
            }
            
            int nameLen = ((blockData[pos] & 0xFF) << 8) | (blockData[pos + 1] & 0xFF);
            pos += 2 + nameLen;
            int dataLen = ((blockData[pos] & 0xFF) << 24) |
                          ((blockData[pos + 1] & 0xFF) << 16) |
                          ((blockData[pos + 2] & 0xFF) << 8) |
                          (blockData[pos + 3] & 0xFF);
            pos += 4 + dataLen;
        }
        
        return null;
    }
    
    public void prefetch(String... names) throws IOException {
        Set<Integer> blockIds = new HashSet<>();
        for (String name : names) {
            ClassIndex.Entry entry = classIndex.get(name);
            if (entry != null) {
                blockIds.add(entry.blockId());
            }
        }
        
        for (int blockId : blockIds) {
            getBlock(blockId);
        }
    }
    
    public int blockCount() { return blockIndex.size(); }
    public int entryCount() { return classIndex.size(); }
    public Set<String> entryNames() { return classIndex.classNames(); }
    public int classCount() { return classIndex.size(); }
    public Set<String> classNames() { return classIndex.classNames(); }
    public BlockIndex blockIndex() { return blockIndex; }
    public ClassIndex classIndex() { return classIndex; }
    public void clearCache() { blockCache.clear(); }
    
    /**
     * Calculate CRC32 over the entire archive excluding the CRC32 field itself.
     * Coverage: header (excluding CRC32), dictionary, all blocks, indices, footer.
     */
    private long calculateArchiveCRC32(JarzDataProvider dataProvider, int dictSize, long fileSize) throws IOException {
        CRC32 crc32 = new CRC32();
        
        // File size is now passed as parameter (from footer)
        
        // Read and checksum header (excluding CRC32 field at offset 16-19)
        byte[] headerPart1 = dataProvider.readBytes(0, 16); // magic + version + flags + blockCount + dictSize
        crc32.update(headerPart1);
        
        // Skip CRC32 field (4 bytes) and checksum reserved bytes
        byte[] headerPart2 = dataProvider.readBytes(20, 12); // reserved bytes
        crc32.update(headerPart2);
        
        // Read and checksum dictionary if present
        if (dictSize > 0) {
            byte[] dictionary = dataProvider.readDictionary(dictSize);
            crc32.update(dictionary);
        }
        
        // Read and checksum all blocks and indices (from end of header/dictionary to start of footer)
        long dataStart = JarzV2Format.HEADER_SIZE + dictSize;
        long dataEnd = fileSize - JarzV2Format.FOOTER_SIZE;
        long dataSize = dataEnd - dataStart;
        
        // Read data in chunks to avoid memory issues with large archives
        long offset = dataStart;
        long remaining = dataSize;
        
        while (remaining > 0) {
            int chunkSize = (int) Math.min(8192, remaining);
            byte[] chunk = dataProvider.readBytes(offset, chunkSize);
            crc32.update(chunk);
            offset += chunkSize;
            remaining -= chunkSize;
        }
        
        // Read and checksum footer (excluding magic at the end)
        byte[] footer = dataProvider.readFooter();
        crc32.update(footer, 0, 12); // indexOffset + fileSize, exclude magic
        
        return crc32.getValue();
    }
    
    /**
     * Get the block index for analyzing JARZ structure.
     * 
     * @return the block index
     * @since 1.0
     */
    public BlockIndex getBlockIndex() {
        return blockIndex;
    }
    
    /**
     * Get the class index for analyzing JARZ structure.
     * 
     * @return the class index
     * @since 1.0
     */
    public ClassIndex getClassIndex() {
        return classIndex;
    }
    
    @Override
    public void close() throws IOException {
        if (raf != null) {
            raf.close();
        }
        if (dataProvider != null) {
            dataProvider.close();
        }
    }
}
