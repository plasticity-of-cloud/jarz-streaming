package jdk.incubator.jarz.v2;

/**
 * Block types for different content in JARZ v2 archives.
 * 
 * <p>Each block type defines the compression level and strategy appropriate
 * for its content type, optimizing both compression ratio and decompression speed.
 * 
 * <p>Compression level can be configured via system property {@code jarz.compression.level}
 * (range 3-11, default 3) for CLASS, CONFIG, SERVICE, TEXT, and MANIFEST blocks.
 * NATIVE blocks always use level 1, STORED blocks are never compressed.
 * 
 * @since 1.0
 */
public enum BlockType {
    CLASS(0x01, 3, true),      // .class files, dependency-grouped
    CONFIG(0x02, 6, true),     // .properties, .xml, .yml, .yaml, .json
    SERVICE(0x03, 3, true),    // META-INF/services/*, META-INF/spring.*
    TEXT(0x04, 6, true),       // .html, .css, .js
    NATIVE(0x05, 1, true),     // .so, .dll, .dylib (low compression)
    STORED(0x06, 0, false),    // .png, .jpg, .gif, .zip (no compression)
    MANIFEST(0x07, 3, true);   // META-INF/MANIFEST.MF, signatures
    
    public static final int MIN_COMPRESSION_LEVEL = 3;
    public static final int MAX_COMPRESSION_LEVEL = 11;
    public static final int DEFAULT_COMPRESSION_LEVEL = 3;
    
    private final int id;
    private final int defaultCompressionLevel;
    private final boolean compress;
    
    BlockType(int id, int compressionLevel, boolean compress) {
        this.id = id;
        this.defaultCompressionLevel = compressionLevel;
        this.compress = compress;
    }
    
    public int id() { return id; }
    
    public int compressionLevel() { 
        // NATIVE always uses level 1, STORED never compresses
        if (this == NATIVE) return 1;
        if (this == STORED) return 0;
        
        // Use configured level for main content types (read dynamically for testing)
        return getConfiguredCompressionLevel();
    }
    
    public boolean shouldCompress() { return compress; }
    
    private static int getConfiguredCompressionLevel() {
        String property = System.getProperty("jarz.compression.level");
        if (property == null) {
            return DEFAULT_COMPRESSION_LEVEL;
        }
        
        try {
            int level = Integer.parseInt(property);
            if (level >= MIN_COMPRESSION_LEVEL && level <= MAX_COMPRESSION_LEVEL) {
                return level;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to default
        }
        
        // Invalid value, use default
        return DEFAULT_COMPRESSION_LEVEL;
    }
    
    public static BlockType fromId(int id) {
        for (BlockType t : values()) {
            if (t.id == id) return t;
        }
        return TEXT; // default
    }
}
