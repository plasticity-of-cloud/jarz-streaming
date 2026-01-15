package net.jarz.streaming.v2;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assigns non-class entries to blocks based on content type.
 * Groups similar content together for better compression.
 */
public class ResourceBlockAssigner {
    
    private final int configBlockSize;
    private final int textBlockSize;
    private final int nativeBlockSize;
    private final int storedBlockSize;
    
    public ResourceBlockAssigner() {
        this(256 * 1024, 512 * 1024, 2 * 1024 * 1024, 1024 * 1024);
    }
    
    public ResourceBlockAssigner(int configBlockSize, int textBlockSize, 
                                  int nativeBlockSize, int storedBlockSize) {
        this.configBlockSize = configBlockSize;
        this.textBlockSize = textBlockSize;
        this.nativeBlockSize = nativeBlockSize;
        this.storedBlockSize = storedBlockSize;
    }
    
    /**
     * Assign resources to typed blocks.
     * @param entries Map of entry name to data (excluding .class files)
     * @param startBlockId Starting block ID (after class blocks)
     * @return List of typed blocks
     */
    public List<TypedBlock> assign(Map<String, byte[]> entries, int startBlockId) {
        // Partition by block type
        Map<BlockType, List<Map.Entry<String, byte[]>>> byType = entries.entrySet().stream()
            .collect(Collectors.groupingBy(e -> EntryClassifier.classify(e.getKey())));
        
        List<TypedBlock> blocks = new ArrayList<>();
        int blockId = startBlockId;
        
        // SERVICE: All in one block (usually small)
        var serviceEntries = byType.getOrDefault(BlockType.SERVICE, List.of());
        if (!serviceEntries.isEmpty()) {
            blocks.add(createSingleBlock(blockId++, BlockType.SERVICE, serviceEntries));
        }
        
        // MANIFEST: All in one block (security-critical, keep together)
        var manifestEntries = byType.getOrDefault(BlockType.MANIFEST, List.of());
        if (!manifestEntries.isEmpty()) {
            blocks.add(createSingleBlock(blockId++, BlockType.MANIFEST, manifestEntries));
        }
        
        // CONFIG: Group by size
        var configEntries = byType.getOrDefault(BlockType.CONFIG, List.of());
        for (TypedBlock b : createBlocks(blockId, BlockType.CONFIG, configEntries, configBlockSize)) {
            blocks.add(b);
            blockId++;
        }
        
        // TEXT: Group by directory for locality
        var textEntries = byType.getOrDefault(BlockType.TEXT, List.of());
        for (TypedBlock b : createBlocksByDirectory(blockId, BlockType.TEXT, textEntries, textBlockSize)) {
            blocks.add(b);
            blockId++;
        }
        
        // NATIVE: One native library per block for platform-specific streaming
        var nativeEntries = byType.getOrDefault(BlockType.NATIVE, List.of());
        for (TypedBlock b : createNativeBlocks(blockId, nativeEntries)) {
            blocks.add(b);
            blockId++;
        }
        
        // STORED: No compression, group by size
        var storedEntries = byType.getOrDefault(BlockType.STORED, List.of());
        for (TypedBlock b : createBlocks(blockId, BlockType.STORED, storedEntries, storedBlockSize)) {
            blocks.add(b);
            blockId++;
        }
        
        return blocks;
    }
    
    private TypedBlock createSingleBlock(int blockId, BlockType type, 
                                          List<Map.Entry<String, byte[]>> entries) {
        TypedBlock block = new TypedBlock(blockId, type);
        for (var e : entries) {
            block.add(e.getKey(), e.getValue());
        }
        return block;
    }
    
    /**
     * Create blocks for native libraries - one native library per block.
     * This enables platform-specific streaming where clients only download
     * the native libraries they need for their platform.
     */
    private List<TypedBlock> createNativeBlocks(int startId, List<Map.Entry<String, byte[]>> entries) {
        if (entries.isEmpty()) return List.of();
        
        List<TypedBlock> blocks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            TypedBlock block = new TypedBlock(startId + i, BlockType.NATIVE);
            var entry = entries.get(i);
            block.add(entry.getKey(), entry.getValue());
            blocks.add(block);
        }
        return blocks;
    }
    
    private List<TypedBlock> createBlocks(int startId, BlockType type,
                                           List<Map.Entry<String, byte[]>> entries, int targetSize) {
        if (entries.isEmpty()) return List.of();
        
        List<TypedBlock> blocks = new ArrayList<>();
        TypedBlock current = new TypedBlock(startId, type);
        
        for (var e : entries) {
            if (current.size() + e.getValue().length > targetSize && !current.isEmpty()) {
                blocks.add(current);
                current = new TypedBlock(startId + blocks.size(), type);
            }
            current.add(e.getKey(), e.getValue());
        }
        
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        
        return blocks;
    }
    
    private List<TypedBlock> createBlocksByDirectory(int startId, BlockType type,
                                                      List<Map.Entry<String, byte[]>> entries, int targetSize) {
        if (entries.isEmpty()) return List.of();
        
        // Group by parent directory
        Map<String, List<Map.Entry<String, byte[]>>> byDir = entries.stream()
            .collect(Collectors.groupingBy(e -> parentDir(e.getKey())));
        
        List<TypedBlock> blocks = new ArrayList<>();
        TypedBlock current = new TypedBlock(startId, type);
        
        for (var dirEntries : byDir.values()) {
            int dirSize = dirEntries.stream().mapToInt(e -> e.getValue().length).sum();
            
            // If directory fits and would exceed target, start new block
            if (current.size() + dirSize > targetSize && !current.isEmpty()) {
                blocks.add(current);
                current = new TypedBlock(startId + blocks.size(), type);
            }
            
            for (var e : dirEntries) {
                if (current.size() + e.getValue().length > targetSize * 1.5 && !current.isEmpty()) {
                    blocks.add(current);
                    current = new TypedBlock(startId + blocks.size(), type);
                }
                current.add(e.getKey(), e.getValue());
            }
        }
        
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        
        return blocks;
    }
    
    private String parentDir(String path) {
        int idx = path.lastIndexOf('/');
        return idx > 0 ? path.substring(0, idx) : "";
    }
}
