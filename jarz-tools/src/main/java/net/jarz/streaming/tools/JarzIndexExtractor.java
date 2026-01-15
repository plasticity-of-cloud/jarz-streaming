package net.jarz.streaming.tools;

import net.jarz.streaming.v2.*;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * CLI tool for generating local index files from JARZ archives.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzIndexExtractor {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java JarzIndexExtractor <jarz-file-or-url> <output-index-path>");
            System.err.println("Examples:");
            System.err.println("  java JarzIndexExtractor app.jarz app.jarz.index");
            System.err.println("  java JarzIndexExtractor https://cdn.example.com/app.jarz app.jarz.index");
            System.exit(1);
        }
        
        String jarzSource = args[0];
        Path outputPath = Paths.get(args[1]);
        
        try {
            extractIndex(jarzSource, outputPath);
            System.out.println("Local index extracted to: " + outputPath);
        } catch (Exception e) {
            System.err.println("Failed to extract index: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void extractIndex(String jarzSource, Path outputPath) throws IOException {
        JarzDataProvider provider = createDataProvider(jarzSource);
        
        try (provider; BlockReader reader = new BlockReader(provider)) {
            JarzLocalIndex index = new JarzLocalIndex(jarzSource, provider.getFileSize());
            
            // Extract class entries from BlockReader
            ClassIndex classIndex = reader.classIndex();
            BlockIndex blockIndex = reader.blockIndex();
            
            for (String className : classIndex.classNames()) {
                ClassIndex.Entry classEntry = classIndex.get(className);
                if (classEntry != null) {
                    BlockIndex.Entry blockEntry = blockIndex.get(classEntry.blockId());
                    if (blockEntry != null) {
                        JarzLocalIndex.ClassEntry localEntry = new JarzLocalIndex.ClassEntry(
                            classEntry.blockId(),
                            blockEntry.offset(),
                            blockEntry.compressedSize(),
                            classEntry.offsetInBlock(),
                            classEntry.size()
                        );
                        index.addClassEntry(className, localEntry);
                    }
                }
            }
            
            index.save(outputPath);
            System.out.println("Created local index for " + jarzSource + 
                             " (size: " + provider.getFileSize() + " bytes, " + 
                             classIndex.size() + " classes)");
        }
    }
    
    private static JarzDataProvider createDataProvider(String jarzSource) throws IOException {
        if (jarzSource.startsWith("http://") || jarzSource.startsWith("https://")) {
            return new HttpJarzDataProvider(jarzSource);
        } else {
            return new FileJarzDataProvider(Paths.get(jarzSource));
        }
    }
}
