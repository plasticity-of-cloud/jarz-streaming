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
    
    public record Entry(int blockId, long offset, int compressedSize, int uncompressedSize) {}
}
