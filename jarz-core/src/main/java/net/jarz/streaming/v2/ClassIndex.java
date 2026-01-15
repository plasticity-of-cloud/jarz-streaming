package net.jarz.streaming.v2;

import java.util.*;

/**
 * Index mapping class names to block locations.
 */
public final class ClassIndex {
    
    private final Map<String, Entry> entries = new HashMap<>();
    
    public void add(Entry entry) {
        entries.put(entry.className(), entry);
    }
    
    public Entry get(String className) {
        return entries.get(className);
    }
    
    public int size() {
        return entries.size();
    }
    
    public Set<String> classNames() {
        return entries.keySet();
    }
    
    public Collection<Entry> getEntries() {
        return entries.values();
    }
    
    public static final class Entry {
        private final String className;
        private final int blockId;
        private final int offsetInBlock;
        private final int size;
        
        public Entry(String className, int blockId, int offsetInBlock, int size) {
            this.className = className;
            this.blockId = blockId;
            this.offsetInBlock = offsetInBlock;
            this.size = size;
        }
        
        public String className() { return className; }
        public int blockId() { return blockId; }
        public int offsetInBlock() { return offsetInBlock; }
        public int size() { return size; }
    }
}
