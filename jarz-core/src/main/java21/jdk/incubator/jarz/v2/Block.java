package jdk.incubator.jarz.v2;

import java.util.*;

/**
 * Represents a block of classes in JARZ v2 format.
 * Classes within a block share ZSTD compression context.
 */
public final class Block {
    
    private final int id;
    private final List<ClassEntry> entries = new ArrayList<>();
    private int uncompressedSize = 0;
    
    public Block(int id) {
        this.id = id;
    }
    
    public void add(String className, byte[] classData) {
        entries.add(new ClassEntry(className, classData, uncompressedSize));
        uncompressedSize += 2 + className.length() + 4 + classData.length; // nameLen + name + size + data
    }
    
    public int id() { return id; }
    public int size() { return uncompressedSize; }
    public boolean isEmpty() { return entries.isEmpty(); }
    public int entryCount() { return entries.size(); }
    public List<ClassEntry> entries() { return Collections.unmodifiableList(entries); }
    
    /**
     * Serialize block contents for compression.
     * Format: [nameLen(2)][name][size(4)][classData] repeated
     */
    public byte[] serialize() {
        byte[] result = new byte[uncompressedSize];
        int pos = 0;
        
        for (ClassEntry entry : entries) {
            byte[] nameBytes = entry.className().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            
            // Name length (2 bytes, big-endian)
            result[pos++] = (byte) (nameBytes.length >> 8);
            result[pos++] = (byte) nameBytes.length;
            
            // Name
            System.arraycopy(nameBytes, 0, result, pos, nameBytes.length);
            pos += nameBytes.length;
            
            // Data size (4 bytes, big-endian)
            int dataLen = entry.classData().length;
            result[pos++] = (byte) (dataLen >> 24);
            result[pos++] = (byte) (dataLen >> 16);
            result[pos++] = (byte) (dataLen >> 8);
            result[pos++] = (byte) dataLen;
            
            // Data
            System.arraycopy(entry.classData(), 0, result, pos, dataLen);
            pos += dataLen;
        }
        
        return result;
    }
    
    public record ClassEntry(String className, byte[] classData, int offsetInBlock) {}
}
