package jdk.incubator.jarz.v2;

import java.util.Set;

/**
 * Classifies JAR entries into block types based on extension and path.
 */
public final class EntryClassifier {
    
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
        ".properties", ".xml", ".yml", ".yaml", ".json"
    );
    
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
        ".html", ".htm", ".css", ".js", ".txt", ".md"
    );
    
    private static final Set<String> NATIVE_EXTENSIONS = Set.of(
        ".so", ".dll", ".dylib", ".jnilib"
    );
    
    private static final Set<String> PRECOMPRESSED_EXTENSIONS = Set.of(
        ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp",
        ".zip", ".gz", ".bz2", ".xz", ".zst",
        ".woff", ".woff2", ".ttf", ".otf",
        ".jar", ".war", ".ear"
    );
    
    private EntryClassifier() {}
    
    public static BlockType classify(String entryName) {
        if (entryName.endsWith(".class")) {
            return BlockType.CLASS;
        }
        
        // Service loaders and Spring metadata
        if (entryName.startsWith("META-INF/services/") ||
            entryName.startsWith("META-INF/spring.")) {
            return BlockType.SERVICE;
        }
        
        // Manifest and signatures
        if (entryName.equals("META-INF/MANIFEST.MF") ||
            entryName.endsWith(".SF") ||
            entryName.endsWith(".RSA") ||
            entryName.endsWith(".DSA") ||
            entryName.endsWith(".EC")) {
            return BlockType.MANIFEST;
        }
        
        String lower = entryName.toLowerCase();
        
        // Check by extension
        for (String ext : PRECOMPRESSED_EXTENSIONS) {
            if (lower.endsWith(ext)) return BlockType.STORED;
        }
        
        for (String ext : NATIVE_EXTENSIONS) {
            if (lower.endsWith(ext)) return BlockType.NATIVE;
        }
        
        for (String ext : CONFIG_EXTENSIONS) {
            if (lower.endsWith(ext)) return BlockType.CONFIG;
        }
        
        for (String ext : TEXT_EXTENSIONS) {
            if (lower.endsWith(ext)) return BlockType.TEXT;
        }
        
        // Default to TEXT for unknown types
        return BlockType.TEXT;
    }
}
