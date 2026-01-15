package net.jarz.streaming.v2;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A block with an associated type for content-aware compression.
 */
public final class TypedBlock {
    
    private final int id;
    private final BlockType type;
    private final List<Entry> entries = new ArrayList<>();
    private int uncompressedSize = 0;
    
    public TypedBlock(int id, BlockType type) {
        this.id = id;
        this.type = type;
    }
    
    public void add(String name, byte[] data) {
        entries.add(new Entry(name, data, uncompressedSize));
        uncompressedSize += 2 + name.getBytes(StandardCharsets.UTF_8).length + 4 + data.length;
    }
    
    public int id() { return id; }
    public BlockType type() { return type; }
    public int size() { return uncompressedSize; }
    public boolean isEmpty() { return entries.isEmpty(); }
    public int entryCount() { return entries.size(); }
    public List<Entry> entries() { return Collections.unmodifiableList(entries); }
    
    /**
     * Serialize block contents.
     * Format: [nameLen(2)][name][size(4)][data] repeated
     */
    public byte[] serialize() {
        byte[] result = new byte[uncompressedSize];
        int pos = 0;
        
        for (Entry entry : entries) {
            byte[] nameBytes = entry.name().getBytes(StandardCharsets.UTF_8);
            
            result[pos++] = (byte) (nameBytes.length >> 8);
            result[pos++] = (byte) nameBytes.length;
            
            System.arraycopy(nameBytes, 0, result, pos, nameBytes.length);
            pos += nameBytes.length;
            
            int dataLen = entry.data().length;
            result[pos++] = (byte) (dataLen >> 24);
            result[pos++] = (byte) (dataLen >> 16);
            result[pos++] = (byte) (dataLen >> 8);
            result[pos++] = (byte) dataLen;
            
            System.arraycopy(entry.data(), 0, result, pos, dataLen);
            pos += dataLen;
        }
        
        return result;
    }
    
    public static final class Entry {
        private final String name;
        private final byte[] data;
        private final int offsetInBlock;
        
        public Entry(String name, byte[] data, int offsetInBlock) {
            this.name = name;
            this.data = data;
            this.offsetInBlock = offsetInBlock;
        }
        
        public String name() { return name; }
        public byte[] data() { return data; }
        public int offsetInBlock() { return offsetInBlock; }
    }
}
