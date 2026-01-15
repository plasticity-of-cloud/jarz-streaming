package net.jarz.streaming.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Auto-discovery engine for JARZ applications.
 * 
 * <p>Analyzes directories or URLs to find JARZ files and automatically
 * determines the main class and dependency structure.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzDiscovery {
    
    private final UniversalJarzLauncher.LaunchConfig config;
    
    public JarzDiscovery(UniversalJarzLauncher.LaunchConfig config) {
        this.config = config;
    }
    
    /**
     * Discovers JARZ files and main class from the configured path.
     * 
     * @return discovery result with main class and JARZ files
     * @throws IOException if discovery fails
     */
    public UniversalJarzLauncher.DiscoveryResult discover() throws IOException {
        Path jarzPath = Paths.get(config.jarzPath);
        
        if (Files.isDirectory(jarzPath)) {
            return discoverFromDirectory(jarzPath);
        } else if (Files.isRegularFile(jarzPath) && jarzPath.toString().endsWith(".jarz")) {
            return discoverFromSingleFile(jarzPath);
        } else {
            throw new IOException("Invalid JARZ path: " + config.jarzPath);
        }
    }
    
    private UniversalJarzLauncher.DiscoveryResult discoverFromDirectory(Path directory) throws IOException {
        List<Path> jarzFiles = new ArrayList<>();
        
        // Find all JARZ files in directory
        try (Stream<Path> files = Files.walk(directory, 1)) {
            files.filter(path -> path.toString().endsWith(".jarz"))
                 .filter(Files::isRegularFile)
                 .forEach(jarzFiles::add);
        }
        
        if (jarzFiles.isEmpty()) {
            throw new IOException("No JARZ files found in directory: " + directory);
        }
        
        // Sort by priority (main application JARs first)
        jarzFiles.sort(this::compareJarzPriority);
        
        // Determine main class
        String mainClass = config.mainClass;
        if (mainClass == null) {
            mainClass = findMainClass(jarzFiles);
        }
        
        if (mainClass == null) {
            throw new IOException("Could not determine main class. Use --main-class option.");
        }
        
        return new UniversalJarzLauncher.DiscoveryResult(mainClass, jarzFiles);
    }
    
    private UniversalJarzLauncher.DiscoveryResult discoverFromSingleFile(Path jarzFile) throws IOException {
        String mainClass = config.mainClass;
        if (mainClass == null) {
            mainClass = findMainClass(List.of(jarzFile));
        }
        
        if (mainClass == null) {
            throw new IOException("Could not determine main class from: " + jarzFile);
        }
        
        return new UniversalJarzLauncher.DiscoveryResult(mainClass, List.of(jarzFile));
    }
    
    private String findMainClass(List<Path> jarzFiles) {
        // Try to find Main-Class in manifest of each JARZ file
        for (Path jarzFile : jarzFiles) {
            try {
                String mainClass = extractMainClassFromManifest(jarzFile);
                if (mainClass != null) {
                    return mainClass;
                }
            } catch (IOException e) {
                if (config.debug) {
                    System.err.println("Warning: Could not read manifest from " + jarzFile + ": " + e.getMessage());
                }
            }
        }
        
        return null;
    }
    
    private String extractMainClassFromManifest(Path jarzFile) throws IOException {
        // For now, treat JARZ files as JAR files for manifest reading
        // TODO: Implement proper JARZ manifest reading
        try (JarFile jar = new JarFile(jarzFile.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                return manifest.getMainAttributes().getValue("Main-Class");
            }
        }
        return null;
    }
    
    private int compareJarzPriority(Path a, Path b) {
        String nameA = a.getFileName().toString().toLowerCase();
        String nameB = b.getFileName().toString().toLowerCase();
        
        // Prioritize main application JARs
        int priorityA = getJarzPriority(nameA);
        int priorityB = getJarzPriority(nameB);
        
        if (priorityA != priorityB) {
            return Integer.compare(priorityA, priorityB);
        }
        
        // Secondary sort by name
        return nameA.compareTo(nameB);
    }
    
    private int getJarzPriority(String fileName) {
        // Higher priority (lower number) for main application JARs
        if (fileName.contains("server") || fileName.contains("main") || fileName.contains("app")) {
            return 1;
        }
        if (fileName.contains("client") || fileName.contains("core")) {
            return 2;
        }
        // Framework JARs get medium priority
        if (fileName.contains("framework") || fileName.contains("runtime") || fileName.contains("engine")) {
            return 3;
        }
        // Libraries and dependencies get lower priority
        return 10;
    }
}
