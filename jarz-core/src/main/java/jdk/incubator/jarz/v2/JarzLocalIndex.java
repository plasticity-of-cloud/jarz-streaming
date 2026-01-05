package jdk.incubator.jarz.v2;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Local index file for JARZ archives.
 * Enables instant class location without network requests.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzLocalIndex {
    
    public static final byte[] MAGIC = "JIDX".getBytes(StandardCharsets.UTF_8);
    public static final int VERSION = 2; // Enhanced version with cached metadata
    
    private final Map<String, ClassEntry> classEntries;
    private final String originalJarzUrl;
    private final long originalJarzSize;
    private final long timestamp;
    
    // NEW: Cached JARZ metadata to eliminate network requests
    private final byte[] cachedHeader;    // 32 bytes - format validation
    private final byte[] cachedFooter;    // 16 bytes - index location  
    private final byte[] cachedIndex;     // Variable - class locations
    
    public JarzLocalIndex(String originalJarzUrl, long originalJarzSize) {
        this.originalJarzUrl = originalJarzUrl;
        this.originalJarzSize = originalJarzSize;
        this.timestamp = System.currentTimeMillis();
        this.classEntries = new HashMap<>();
        this.cachedHeader = null;
        this.cachedFooter = null;
        this.cachedIndex = null;
    }
    
    // Enhanced constructor with cached metadata
    public JarzLocalIndex(String originalJarzUrl, long originalJarzSize, 
                         byte[] cachedHeader, byte[] cachedFooter, byte[] cachedIndex) {
        this.originalJarzUrl = originalJarzUrl;
        this.originalJarzSize = originalJarzSize;
        this.timestamp = System.currentTimeMillis();
        this.classEntries = new HashMap<>();
        this.cachedHeader = cachedHeader != null ? cachedHeader.clone() : null;
        this.cachedFooter = cachedFooter != null ? cachedFooter.clone() : null;
        this.cachedIndex = cachedIndex != null ? cachedIndex.clone() : null;
    }
    
    /**
     * Entry for a class in the JARZ archive.
     */
    public static class ClassEntry {
        public final int blockId;
        public final long blockOffset;
        public final int blockSize;
        public final int entryOffset;
        public final int entrySize;
        
        public ClassEntry(int blockId, long blockOffset, int blockSize, int entryOffset, int entrySize) {
            this.blockId = blockId;
            this.blockOffset = blockOffset;
            this.blockSize = blockSize;
            this.entryOffset = entryOffset;
            this.entrySize = entrySize;
        }
    }
    
    /**
     * Add a class entry to the index.
     */
    public void addClassEntry(String className, ClassEntry entry) {
        classEntries.put(className, entry);
    }
    
    /**
     * Check if the index contains a class.
     */
    public boolean hasClass(String className) {
        return classEntries.containsKey(className);
    }
    
    /**
     * Get class entry for the specified class.
     */
    public ClassEntry getClassEntry(String className) {
        return classEntries.get(className);
    }
    
    /**
     * Get the original JARZ URL/path.
     */
    public String getOriginalJarzUrl() {
        return originalJarzUrl;
    }
    
    /**
     * Get the original JARZ size.
     */
    public long getOriginalJarzSize() {
        return originalJarzSize;
    }
    
    /**
     * Get cached header (eliminates header request).
     */
    public byte[] getCachedHeader() {
        return cachedHeader != null ? cachedHeader.clone() : null;
    }
    
    /**
     * Get cached footer (eliminates footer request).
     */
    public byte[] getCachedFooter() {
        return cachedFooter != null ? cachedFooter.clone() : null;
    }
    
    /**
     * Get cached index (eliminates index request).
     */
    public byte[] getCachedIndex() {
        return cachedIndex != null ? cachedIndex.clone() : null;
    }
    
    /**
     * Check if this index has cached metadata.
     */
    public boolean hasCachedMetadata() {
        return cachedHeader != null && cachedFooter != null && cachedIndex != null;
    }
    
    /**
     * Check if index is still valid (not expired).
     */
    public boolean isValid(long maxAgeMillis) {
        return (System.currentTimeMillis() - timestamp) < maxAgeMillis;
    }
    
    /**
     * Create enhanced local index from existing JARZ using BlockReader infrastructure.
     */
    public static JarzLocalIndex createFromJarz(String jarzUrl, Path jarzPath) throws IOException {
        try (FileJarzDataProvider provider = new FileJarzDataProvider(jarzPath);
             BlockReader reader = new BlockReader(provider)) {
            
            // Read metadata using existing infrastructure
            byte[] header = provider.readBytes(0, JarzV2Format.HEADER_SIZE);
            byte[] footer = provider.readFooter();
            
            // Extract indices using BlockReader
            ClassIndex classIndex = reader.classIndex();
            BlockIndex blockIndex = reader.blockIndex();
            
            // Serialize index using BlockWriter format
            byte[] indexData = serializeIndex(classIndex, blockIndex);
            
            // Create enhanced index with cached metadata
            JarzLocalIndex index = new JarzLocalIndex(jarzUrl, provider.getFileSize(), header, footer, indexData);
            
            // Populate class entries with proper name normalization
            for (String className : classIndex.classNames()) {
                ClassIndex.Entry classEntry = classIndex.get(className);
                if (classEntry != null) {
                    BlockIndex.Entry blockEntry = blockIndex.get(classEntry.blockId());
                    if (blockEntry != null) {
                        // Normalize class name: remove .class extension and convert to external format
                        String normalizedClassName = className;
                        if (normalizedClassName.endsWith(".class")) {
                            normalizedClassName = normalizedClassName.substring(0, normalizedClassName.length() - 6);
                        }
                        normalizedClassName = normalizedClassName.replace('/', '.');
                        
                        ClassEntry localEntry = new ClassEntry(
                            classEntry.blockId(),
                            blockEntry.offset(),
                            blockEntry.compressedSize(),
                            classEntry.offsetInBlock(),
                            classEntry.size()
                        );
                        index.addClassEntry(normalizedClassName, localEntry);
                    }
                }
            }
            
            return index;
        }
    }
    
    /**
     * Serialize index using BlockWriter format.
     */
    private static byte[] serializeIndex(ClassIndex classIndex, BlockIndex blockIndex) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Use BlockWriter's index format
        ByteBuffer buf = ByteBuffer.allocate(calculateIndexSize(classIndex, blockIndex));
        buf.order(JarzV2Format.BYTE_ORDER);
        
        // Block index
        buf.putInt(blockIndex.size());
        for (int i = 0; i < blockIndex.size(); i++) {
            BlockIndex.Entry e = blockIndex.get(i);
            buf.putInt(e.blockId());
            buf.putLong(e.offset());
            buf.putInt(e.compressedSize());
            buf.putInt(e.uncompressedSize());
        }
        
        // Class index  
        buf.putInt(classIndex.size());
        for (String className : classIndex.classNames()) {
            ClassIndex.Entry e = classIndex.get(className);
            byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
            buf.putShort((short) nameBytes.length);
            buf.put(nameBytes);
            buf.putInt(e.blockId());
            buf.putInt(e.offsetInBlock());
            buf.putInt(e.size());
        }
        
        return java.util.Arrays.copyOf(buf.array(), buf.position());
    }
    
    private static int calculateIndexSize(ClassIndex classIndex, BlockIndex blockIndex) {
        int size = 4; // Block count
        size += blockIndex.size() * (4 + 8 + 4 + 4); // Block entries
        
        size += 4; // Class count
        for (String className : classIndex.classNames()) {
            size += 2 + className.length() + 4 + 4 + 4; // Class entries
        }
        
        return size;
    }
    
    /**
     * Save the enhanced index to a file.
     */
    public void save(Path outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            // Write header
            bos.write(MAGIC);
            
            ByteBuffer header = ByteBuffer.allocate(28).order(JarzV2Format.BYTE_ORDER);
            header.putInt(VERSION);
            header.putInt(classEntries.size());
            header.putLong(originalJarzSize);
            header.putLong(timestamp);
            header.putInt(hasCachedMetadata() ? 1 : 0); // Has cached metadata flag
            bos.write(header.array());
            
            // Write original URL
            byte[] urlBytes = originalJarzUrl.getBytes(StandardCharsets.UTF_8);
            ByteBuffer urlHeader = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
            urlHeader.putInt(urlBytes.length);
            bos.write(urlHeader.array());
            bos.write(urlBytes);
            
            // Write cached metadata if available
            if (hasCachedMetadata()) {
                // Header
                ByteBuffer headerSize = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
                headerSize.putInt(cachedHeader.length);
                bos.write(headerSize.array());
                bos.write(cachedHeader);
                
                // Footer
                ByteBuffer footerSize = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
                footerSize.putInt(cachedFooter.length);
                bos.write(footerSize.array());
                bos.write(cachedFooter);
                
                // Index
                ByteBuffer indexSize = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
                indexSize.putInt(cachedIndex.length);
                bos.write(indexSize.array());
                bos.write(cachedIndex);
            }
            
            // Write class entries
            for (Map.Entry<String, ClassEntry> entry : classEntries.entrySet()) {
                String className = entry.getKey();
                ClassEntry classEntry = entry.getValue();
                
                // Write class name
                byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
                ByteBuffer nameHeader = ByteBuffer.allocate(4).order(JarzV2Format.BYTE_ORDER);
                nameHeader.putInt(nameBytes.length);
                bos.write(nameHeader.array());
                bos.write(nameBytes);
                
                // Write class entry data
                ByteBuffer entryData = ByteBuffer.allocate(20).order(JarzV2Format.BYTE_ORDER);
                entryData.putInt(classEntry.blockId);
                entryData.putLong(classEntry.blockOffset);
                entryData.putInt(classEntry.blockSize);
                entryData.putInt(classEntry.entryOffset);
                bos.write(entryData.array());
            }
        }
    }
    
    /**
     * Load enhanced index from a file.
     */
    public static JarzLocalIndex load(Path indexPath) throws IOException {
        try (FileInputStream fis = new FileInputStream(indexPath.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
            // Read and verify magic
            byte[] magic = new byte[4];
            bis.readNBytes(magic, 0, 4);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IOException("Invalid local index magic");
            }
            
            // Read header
            byte[] headerBytes = new byte[28];
            bis.readNBytes(headerBytes, 0, 28);
            ByteBuffer header = ByteBuffer.wrap(headerBytes).order(JarzV2Format.BYTE_ORDER);
            
            int version = header.getInt();
            if (version < 1 || version > VERSION) {
                throw new IOException("Unsupported local index version: " + version);
            }
            
            int entryCount = header.getInt();
            long originalSize = header.getLong();
            long timestamp = version >= 2 ? header.getLong() : System.currentTimeMillis();
            boolean hasCachedMetadata = version >= 2 ? header.getInt() == 1 : false;
            
            // Read original URL
            byte[] urlLengthBytes = new byte[4];
            bis.readNBytes(urlLengthBytes, 0, 4);
            int urlLength = ByteBuffer.wrap(urlLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            
            byte[] urlBytes = new byte[urlLength];
            bis.readNBytes(urlBytes, 0, urlLength);
            String originalUrl = new String(urlBytes, StandardCharsets.UTF_8);
            
            // Read cached metadata if available
            byte[] cachedHeader = null;
            byte[] cachedFooter = null;
            byte[] cachedIndex = null;
            
            if (hasCachedMetadata) {
                // Read header
                byte[] headerSizeBytes = new byte[4];
                bis.readNBytes(headerSizeBytes, 0, 4);
                int headerSize = ByteBuffer.wrap(headerSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                cachedHeader = new byte[headerSize];
                bis.readNBytes(cachedHeader, 0, headerSize);
                
                // Read footer
                byte[] footerSizeBytes = new byte[4];
                bis.readNBytes(footerSizeBytes, 0, 4);
                int footerSize = ByteBuffer.wrap(footerSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                cachedFooter = new byte[footerSize];
                bis.readNBytes(cachedFooter, 0, footerSize);
                
                // Read index
                byte[] indexSizeBytes = new byte[4];
                bis.readNBytes(indexSizeBytes, 0, 4);
                int indexSize = ByteBuffer.wrap(indexSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                cachedIndex = new byte[indexSize];
                bis.readNBytes(cachedIndex, 0, indexSize);
            }
            
            JarzLocalIndex index = new JarzLocalIndex(originalUrl, originalSize, cachedHeader, cachedFooter, cachedIndex);
            
            // Read class entries
            for (int i = 0; i < entryCount; i++) {
                // Read class name
                byte[] nameLengthBytes = new byte[4];
                bis.readNBytes(nameLengthBytes, 0, 4);
                int nameLength = ByteBuffer.wrap(nameLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                
                byte[] nameBytes = new byte[nameLength];
                bis.readNBytes(nameBytes, 0, nameLength);
                String className = new String(nameBytes, StandardCharsets.UTF_8);
                
                // Read class entry data
                byte[] entryBytes = new byte[20];
                bis.readNBytes(entryBytes, 0, 20);
                ByteBuffer entryBuf = ByteBuffer.wrap(entryBytes).order(ByteOrder.LITTLE_ENDIAN);
                
                int blockId = entryBuf.getInt();
                long blockOffset = entryBuf.getLong();
                int blockSize = entryBuf.getInt();
                int entryOffset = entryBuf.getInt();
                
                ClassEntry entry = new ClassEntry(blockId, blockOffset, blockSize, entryOffset, 0);
                index.addClassEntry(className, entry);
            }
            
            return index;
        }
    }
    
    /**
     * Load bundle index from a file.
     */
    public static JarzBundleIndex loadBundle(Path bundlePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(bundlePath.toFile());
             BufferedInputStream bis = new BufferedInputStream(fis)) {
            
            // Read and verify magic
            byte[] magic = new byte[4];
            bis.readNBytes(magic, 0, 4);
            if (!java.util.Arrays.equals(magic, "JBDX".getBytes(StandardCharsets.UTF_8))) {
                throw new IOException("Invalid bundle index magic");
            }
            
            // Read header
            byte[] headerBytes = new byte[8];
            bis.readNBytes(headerBytes, 0, 8);
            ByteBuffer header = ByteBuffer.wrap(headerBytes).order(JarzV2Format.BYTE_ORDER);
            
            int version = header.getInt();
            if (version != 1) {
                throw new IOException("Unsupported bundle index version: " + version);
            }
            
            int jarzCount = header.getInt();
            JarzBundleIndex bundleIndex = new JarzBundleIndex();
            
            // Read JARZ entries
            for (int j = 0; j < jarzCount; j++) {
                // Read JARZ URL
                byte[] urlLengthBytes = new byte[4];
                bis.readNBytes(urlLengthBytes, 0, 4);
                int urlLength = ByteBuffer.wrap(urlLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                
                byte[] urlBytes = new byte[urlLength];
                bis.readNBytes(urlBytes, 0, urlLength);
                String jarzUrl = new String(urlBytes, StandardCharsets.UTF_8);
                
                // Read entry count for this JARZ
                byte[] entryCountBytes = new byte[4];
                bis.readNBytes(entryCountBytes, 0, 4);
                int entryCount = ByteBuffer.wrap(entryCountBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                
                JarzLocalIndex jarzIndex = new JarzLocalIndex(jarzUrl, 0); // Size not stored in bundle
                
                // Read class entries
                for (int i = 0; i < entryCount; i++) {
                    // Read class name
                    byte[] nameLengthBytes = new byte[4];
                    bis.readNBytes(nameLengthBytes, 0, 4);
                    int nameLength = ByteBuffer.wrap(nameLengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    
                    byte[] nameBytes = new byte[nameLength];
                    bis.readNBytes(nameBytes, 0, nameLength);
                    String className = new String(nameBytes, StandardCharsets.UTF_8);
                    
                    // Read class entry data
                    byte[] entryBytes = new byte[20];
                    bis.readNBytes(entryBytes, 0, 20);
                    ByteBuffer entryBuf = ByteBuffer.wrap(entryBytes).order(ByteOrder.LITTLE_ENDIAN);
                    
                    int blockId = entryBuf.getInt();
                    long blockOffset = entryBuf.getLong();
                    int blockSize = entryBuf.getInt();
                    int entryOffset = entryBuf.getInt();
                    
                    ClassEntry entry = new ClassEntry(blockId, blockOffset, blockSize, entryOffset, 0);
                    jarzIndex.addClassEntry(className, entry);
                }
                
                bundleIndex.addJarzIndex(jarzUrl, jarzIndex);
            }
            
            return bundleIndex;
        }
    }
    
    /**
     * Bundle index containing multiple JARZ indexes.
     */
    public static class JarzBundleIndex {
        private final Map<String, JarzLocalIndex> jarzIndexes = new HashMap<>();
        
        public void addJarzIndex(String jarzUrl, JarzLocalIndex index) {
            jarzIndexes.put(jarzUrl, index);
        }
        
        public JarzLocalIndex getJarzIndex(String jarzUrl) {
            return jarzIndexes.get(jarzUrl);
        }
        
        public boolean hasClass(String className) {
            return jarzIndexes.values().stream().anyMatch(index -> index.hasClass(className));
        }
        
        public ClassEntry findClass(String className) {
            for (JarzLocalIndex index : jarzIndexes.values()) {
                ClassEntry entry = index.getClassEntry(className);
                if (entry != null) {
                    return entry;
                }
            }
            return null;
        }
        
        public String findJarzForClass(String className) {
            for (Map.Entry<String, JarzLocalIndex> entry : jarzIndexes.entrySet()) {
                if (entry.getValue().hasClass(className)) {
                    return entry.getKey();
                }
            }
            return null;
        }
        
        public Set<String> getJarzUrls() {
            return jarzIndexes.keySet();
        }
        
        public int getTotalClassCount() {
            return jarzIndexes.values().stream().mapToInt(index -> index.classEntries.size()).sum();
        }
    }
}
