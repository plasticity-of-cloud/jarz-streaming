package net.jarz.streaming.v2;

import java.nio.ByteOrder;

/**
 * JARZ v2 Format Constants.
 * Block-based format with profile-guided class clustering.
 */
public final class JarzV2Format {
    
    public static final byte[] MAGIC = {'J', 'R', 'Z', '2'};
    public static final short VERSION = 0x0200;
    
    /**
     * JARZ v2 uses little-endian byte order for all multi-byte fields.
     * This ensures consistent cross-platform behavior and compatibility
     * with modern processors that are predominantly little-endian.
     */
    public static final ByteOrder BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    
    // Flags
    public static final short FLAG_HAS_DICTIONARY = 0x0001;
    public static final short FLAG_PROFILE_GUIDED = 0x0002;
    public static final short FLAG_HAS_CRC32 = 0x0004;
    
    // Block size targets
    public static final int DEFAULT_BLOCK_SIZE = 512 * 1024;  // 512KB
    public static final int MAX_BLOCK_SIZE = 1024 * 1024;     // 1MB
    public static final int MIN_BLOCK_SIZE = 64 * 1024;       // 64KB
    
    // Header size: magic(4) + version(2) + flags(2) + blockCount(4) + dictSize(4) + archiveCrc32(4) + reserved(12) = 32 bytes
    public static final int HEADER_SIZE = 32;
    
    // Footer size: indexOffset(8) + fileSize(4) + magic(4) = 16 bytes
    public static final int FOOTER_SIZE = 16;
    
    // Block header size: typeId(1) + compressionFlag(1) + entryCount(2) + reserved(4) = 8 bytes
    public static final int BLOCK_HEADER_SIZE = 8;
    
    // Index entry sizes
    public static final int BLOCK_INDEX_ENTRY_SIZE = 16; // blockId(4) + offset(8) + compressedSize(4)
    public static final int CLASS_INDEX_ENTRY_SIZE = 20; // blockId(4) + offsetInBlock(4) + size(4) + nameLength(4) + name(variable)
    
    // Local index file constants
    public static final int LOCAL_INDEX_HEADER_SIZE = 16;
    public static final int LOCAL_INDEX_URL_HEADER_SIZE = 4;
    public static final int LOCAL_INDEX_NAME_HEADER_SIZE = 4;
    public static final int LOCAL_INDEX_ENTRY_SIZE = 20;
    
    private JarzV2Format() {}
}
