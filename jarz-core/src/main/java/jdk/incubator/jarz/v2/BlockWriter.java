package jdk.incubator.jarz.v2;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.CRC32;

import com.github.luben.zstd.Zstd;

/**
 * Writes JARZ v2 archives with block-based compression.
 * 
 * <p>This class creates JARZ v2 archives by organizing content into compressed blocks
 * based on content type and dependency relationships. It supports both class blocks
 * and typed resource blocks with appropriate compression levels for each content type.
 * 
 * <p>This implementation is not thread-safe. Each BlockWriter instance should be
 * used by a single thread during archive creation.
 * 
 * @since 1.0
 */
public class BlockWriter implements Closeable {
    
    private final RandomAccessFile raf;
    private final int defaultCompressionLevel;
    private final byte[] dictionary;
    private final CRC32 archiveCrc32 = new CRC32();
    
    private final List<BlockIndex.Entry> blockEntries = new ArrayList<>();
    private final Map<String, ClassIndex.Entry> classEntries = new HashMap<>();
    
    private long currentOffset;
    private int blockCount = 0;
    
    public BlockWriter(Path path, int compressionLevel, byte[] dictionary) throws IOException {
        this.raf = new RandomAccessFile(path.toFile(), "rw");
        this.defaultCompressionLevel = compressionLevel;
        this.dictionary = dictionary;
        
        writeHeader();
        
        if (dictionary != null) {
            raf.write(dictionary);
        }
        
        currentOffset = raf.getFilePointer();
    }
    
    public BlockWriter(Path path, int compressionLevel) throws IOException {
        this(path, compressionLevel, null);
    }
    
    public BlockWriter(Path path) throws IOException {
        this(path, BlockType.CLASS.compressionLevel(), null);
    }
    
    private void writeHeader() throws IOException {
        raf.write(JarzV2Format.MAGIC);
        
        // Write version using consistent byte order
        ByteBuffer versionBuf = ByteBuffer.allocate(2).order(JarzV2Format.BYTE_ORDER);
        versionBuf.putShort(JarzV2Format.VERSION);
        raf.write(versionBuf.array());
        
        // Write flags using consistent byte order
        short flags = JarzV2Format.FLAG_HAS_CRC32; // Always include CRC32
        if (dictionary != null) flags |= JarzV2Format.FLAG_HAS_DICTIONARY;
        ByteBuffer flagsBuf = ByteBuffer.allocate(2).order(JarzV2Format.BYTE_ORDER);
        flagsBuf.putShort(flags);
        raf.write(flagsBuf.array());
        
        // Write block count, dictionary size, and CRC32 placeholder using consistent byte order
        ByteBuffer intBuf = ByteBuffer.allocate(12).order(JarzV2Format.BYTE_ORDER);
        intBuf.putInt(0); // Block count placeholder
        intBuf.putInt(dictionary != null ? dictionary.length : 0);
        intBuf.putInt(0); // CRC32 placeholder - will be updated in close()
        raf.write(intBuf.array());
        
        // Write reserved bytes (12 bytes of zeros)
        raf.write(new byte[12]);
    }
    
    /**
     * Write a class block (legacy method for compatibility).
     */
    public void writeBlock(Block block) throws IOException {
        if (block.isEmpty()) return;
        
        byte[] uncompressed = block.serialize();
        byte[] compressed = compress(uncompressed, defaultCompressionLevel);
        
        long blockOffset = currentOffset;
        
        // Write block header (8 bytes): type(1) + compression(1) + entryCount(2) + reserved(4)
        raf.writeByte(BlockType.CLASS.id());
        raf.writeByte(1); // ZSTD compression
        raf.writeShort(block.entryCount());
        raf.writeInt(0); // reserved
        
        raf.write(compressed);
        currentOffset = raf.getFilePointer();
        
        blockEntries.add(new BlockIndex.Entry(
            block.id(), blockOffset, compressed.length + 8, uncompressed.length
        ));
        
        for (Block.ClassEntry entry : block.entries()) {
            classEntries.put(entry.className(), new ClassIndex.Entry(
                entry.className(), block.id(), entry.offsetInBlock(), entry.classData().length
            ));
        }
        
        blockCount++;
    }
    
    /**
     * Write a typed block (resources).
     */
    public void writeTypedBlock(TypedBlock block) throws IOException {
        if (block.isEmpty()) return;
        
        byte[] uncompressed = block.serialize();
        byte[] data;
        byte compressionFlag;
        
        if (block.type().shouldCompress()) {
            data = compress(uncompressed, block.type().compressionLevel());
            compressionFlag = 1; // ZSTD
        } else {
            data = uncompressed;
            compressionFlag = 0; // STORED
        }
        
        long blockOffset = currentOffset;
        
        // Write block header
        raf.writeByte(block.type().id());
        raf.writeByte(compressionFlag);
        raf.writeShort(block.entryCount());
        raf.writeInt(0); // reserved
        
        raf.write(data);
        currentOffset = raf.getFilePointer();
        
        blockEntries.add(new BlockIndex.Entry(
            block.id(), blockOffset, data.length + 8, uncompressed.length
        ));
        
        for (TypedBlock.Entry entry : block.entries()) {
            classEntries.put(entry.name(), new ClassIndex.Entry(
                entry.name(), block.id(), entry.offsetInBlock(), entry.data().length
            ));
        }
        
        blockCount++;
    }
    
    private byte[] compress(byte[] data, int level) {
        if (dictionary != null) {
            byte[] output = new byte[(int) Zstd.compressBound(data.length)];
            long size = Zstd.compress(output, data, dictionary, level);
            return Arrays.copyOf(output, (int) size);
        } else {
            return Zstd.compress(data, level);
        }
    }
    
    @Override
    public void close() throws IOException {
        long indexOffset = currentOffset;
        writeIndices();
        writeFooter(indexOffset);
        
        // Update block count in header using consistent byte order
        raf.seek(8);
        ByteBuffer blockCountBuf = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
        blockCountBuf.putInt(blockCount);
        raf.write(blockCountBuf.array());
        
        // Calculate CRC32 over entire archive (excluding CRC32 field itself)
        long archiveCrc32Value = calculateArchiveCRC32();
        
        // Update CRC32 in header
        raf.seek(16); // Position of CRC32 field in header
        ByteBuffer crc32Buf = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
        crc32Buf.putInt((int) archiveCrc32Value);
        raf.write(crc32Buf.array());
        
        raf.close();
    }
    
    /**
     * Calculate CRC32 over the entire archive excluding the CRC32 field itself.
     * Coverage: header (excluding CRC32), dictionary, all blocks, indices, footer.
     */
    private long calculateArchiveCRC32() throws IOException {
        CRC32 crc32 = new CRC32();
        
        // Get file size
        long fileSize = raf.length();
        
        // Read and checksum header (excluding CRC32 field at offset 16-19)
        raf.seek(0);
        byte[] headerPart1 = new byte[16]; // magic + version + flags + blockCount + dictSize
        raf.readFully(headerPart1);
        crc32.update(headerPart1);
        
        // Skip CRC32 field (4 bytes)
        raf.seek(20);
        byte[] headerPart2 = new byte[12]; // reserved bytes
        raf.readFully(headerPart2);
        crc32.update(headerPart2);
        
        // Read and checksum dictionary if present
        if (dictionary != null) {
            crc32.update(dictionary);
        }
        
        // Read and checksum all blocks and indices (from end of header/dictionary to start of footer)
        long dataStart = JarzV2Format.HEADER_SIZE + (dictionary != null ? dictionary.length : 0);
        long dataEnd = fileSize - JarzV2Format.FOOTER_SIZE;
        long dataSize = dataEnd - dataStart;
        
        raf.seek(dataStart);
        byte[] buffer = new byte[8192];
        long remaining = dataSize;
        
        while (remaining > 0) {
            int toRead = (int) Math.min(buffer.length, remaining);
            raf.readFully(buffer, 0, toRead);
            crc32.update(buffer, 0, toRead);
            remaining -= toRead;
        }
        
        // Read and checksum footer
        raf.seek(fileSize - JarzV2Format.FOOTER_SIZE);
        byte[] footer = new byte[JarzV2Format.FOOTER_SIZE];
        raf.readFully(footer);
        // Update CRC32 with footer data excluding magic (first 12 bytes: indexOffset + fileSize)
        crc32.update(footer, 0, 12);
        
        return crc32.getValue();
    }
    
    private void writeIndices() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(calculateIndexSize());
        buf.order(JarzV2Format.BYTE_ORDER);
        
        buf.putInt(blockEntries.size());
        for (BlockIndex.Entry e : blockEntries) {
            buf.putInt(e.blockId());
            buf.putLong(e.offset());
            buf.putInt(e.compressedSize());
            buf.putInt(e.uncompressedSize());
        }
        
        buf.putInt(classEntries.size());
        for (ClassIndex.Entry e : classEntries.values()) {
            byte[] nameBytes = e.className().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.putShort((short) nameBytes.length);
            buf.put(nameBytes);
            buf.putInt(e.blockId());
            buf.putInt(e.offsetInBlock());
            buf.putInt(e.size());
        }
        
        raf.write(buf.array(), 0, buf.position());
        currentOffset = raf.getFilePointer();
    }
    
    private int calculateIndexSize() {
        int size = 4;
        size += blockEntries.size() * (4 + 8 + 4 + 4);
        
        size += 4;
        for (ClassIndex.Entry e : classEntries.values()) {
            size += 2 + e.className().length() + 4 + 4 + 4;
        }
        
        return size;
    }
    
    private void writeFooter(long indexOffset) throws IOException {
        // Write index offset using consistent byte order
        ByteBuffer footerBuf = ByteBuffer.allocate(12).order(JarzV2Format.BYTE_ORDER);
        footerBuf.putLong(indexOffset);
        
        // Write file size (current position + footer size)
        long fileSize = raf.getFilePointer() + JarzV2Format.FOOTER_SIZE;
        footerBuf.putInt((int) fileSize);
        
        raf.write(footerBuf.array());
        
        // Write magic
        raf.write(JarzV2Format.MAGIC);
    }
}
