package jdk.incubator.jarz.tools;

import jdk.incubator.jarz.v2.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI tool for generating bundle index files from multiple JARZ archives.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzBundleIndexGenerator {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("Usage: java JarzBundleIndexGenerator <output-bundle-path> <jarz1> <jarz2> ...");
            System.err.println("Examples:");
            System.err.println("  java JarzBundleIndexGenerator app.jarz.index.bundle app.jarz lib1.jarz lib2.jarz");
            System.err.println("  java JarzBundleIndexGenerator bundle.index https://cdn.example.com/app.jarz lib.jarz");
            System.exit(1);
        }
        
        Path outputPath = Paths.get(args[0]);
        List<String> jarzSources = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            jarzSources.add(args[i]);
        }
        
        try {
            generateBundle(jarzSources, outputPath);
            System.out.println("Bundle index created: " + outputPath);
        } catch (Exception e) {
            System.err.println("Failed to generate bundle index: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public static void generateBundle(List<String> jarzSources, Path outputPath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile());
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            // Write bundle header
            bos.write("JBDX".getBytes(StandardCharsets.UTF_8)); // Magic
            
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            header.putInt(1); // Version
            header.putInt(jarzSources.size()); // JARZ count
            bos.write(header.array());
            
            // Process each JARZ file
            for (String jarzSource : jarzSources) {
                System.out.println("Processing: " + jarzSource);
                
                JarzDataProvider provider = createDataProvider(jarzSource);
                try (provider; BlockReader reader = new BlockReader(provider)) {
                    
                    // Write JARZ URL
                    byte[] urlBytes = jarzSource.getBytes(StandardCharsets.UTF_8);
                    ByteBuffer urlHeader = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    urlHeader.putInt(urlBytes.length);
                    bos.write(urlHeader.array());
                    bos.write(urlBytes);
                    
                    // Write class entries for this JARZ
                    ClassIndex classIndex = reader.classIndex();
                    BlockIndex blockIndex = reader.blockIndex();
                    
                    ByteBuffer entryCountHeader = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                    entryCountHeader.putInt(classIndex.size());
                    bos.write(entryCountHeader.array());
                    
                    for (String className : classIndex.classNames()) {
                        ClassIndex.Entry classEntry = classIndex.get(className);
                        if (classEntry != null) {
                            BlockIndex.Entry blockEntry = blockIndex.get(classEntry.blockId());
                            if (blockEntry != null) {
                                // Write class name
                                byte[] nameBytes = className.getBytes(StandardCharsets.UTF_8);
                                ByteBuffer nameHeader = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
                                nameHeader.putInt(nameBytes.length);
                                bos.write(nameHeader.array());
                                bos.write(nameBytes);
                                
                                // Write class entry data
                                ByteBuffer entryData = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
                                entryData.putInt(classEntry.blockId());
                                entryData.putLong(blockEntry.offset());
                                entryData.putInt(blockEntry.compressedSize());
                                entryData.putInt(classEntry.offsetInBlock());
                                bos.write(entryData.array());
                            }
                        }
                    }
                    
                    System.out.println("  Added " + classIndex.size() + " classes from " + jarzSource);
                }
            }
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
