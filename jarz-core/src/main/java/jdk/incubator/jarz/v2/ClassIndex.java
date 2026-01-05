package jdk.incubator.jarz.v2;

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
    
    public record Entry(String className, int blockId, int offsetInBlock, int size) {}
}
