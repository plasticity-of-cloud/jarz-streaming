package jdk.incubator.jarz.v2;

import java.util.*;

/**
 * Index mapping block IDs to file offsets.
 */
public final class BlockIndex {
    
    private final Map<Integer, Entry> entries = new HashMap<>();
    
    public void add(Entry entry) {
        entries.put(entry.blockId(), entry);
    }
    
    public Entry get(int blockId) {
        return entries.get(blockId);
    }
    
    public int size() {
        return entries.size();
    }
    
    public Collection<Entry> entries() {
        return entries.values();
    }
    
    public Collection<Entry> getEntries() {
        return entries.values();
    }
    
    public static final class Entry {
        private final int blockId;
        private final long offset;
        private final int compressedSize;
        private final int uncompressedSize;
        
        public Entry(int blockId, long offset, int compressedSize, int uncompressedSize) {
            this.blockId = blockId;
            this.offset = offset;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
        }
        
        public int blockId() { return blockId; }
        public long offset() { return offset; }
        public int compressedSize() { return compressedSize; }
        public int uncompressedSize() { return uncompressedSize; }
    }
}
