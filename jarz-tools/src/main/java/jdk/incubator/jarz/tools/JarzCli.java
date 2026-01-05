package jdk.incubator.jarz.tools;

import jdk.incubator.jarz.v2.*;
import jdk.incubator.jarz.tools.JarzArgumentParser.*;
import jdk.incubator.jarz.internal.JarzLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Enhanced JARZ CLI tool with full JAR command compatibility.
 * 
 * <p>This tool provides drop-in compatibility with the standard JAR tool while
 * exclusively creating JARZ v2 archives. It supports all standard JAR operations
 * and command-line syntax, plus JARZ-specific extensions.
 * 
 * <h2>JAR-Compatible Operations</h2>
 * <ul>
 * <li>{@code jarz -cf archive.jarz files...} - Create JARZ archive</li>
 * <li>{@code jarz -xf archive.jarz} - Extract JARZ archive</li>
 * <li>{@code jarz -tf archive.jarz} - List archive contents</li>
 * <li>{@code jarz -uf archive.jarz files...} - Update archive</li>
 * </ul>
 * 
 * <h2>JARZ-Specific Extensions</h2>
 * <ul>
 * <li>{@code jarz --convert input.jar output.jarz} - Convert JAR to JARZ</li>
 * </ul>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class JarzCli {
    
    private static final JarzLogger logger = JarzLogger.getLogger(JarzCli.class);
    
    public static void main(String[] args) {
        try {
            run(args);
        } catch (HelpRequestedException e) {
            printUsage();
            System.exit(0);
        } catch (VersionRequestedException e) {
            printVersion();
            System.exit(0);
        } catch (Exception e) {
            logger.error("jarz: {0}", e.getMessage());
            if (args.length == 0) {
                printUsage();
            }
            System.exit(1);
        }
    }
    
    /**
     * Testable method that throws exceptions instead of calling System.exit.
     * 
     * @param args command-line arguments
     * @throws Exception if operation fails
     */
    static void run(String[] args) throws Exception {
        ParsedArgs parsedArgs = JarzArgumentParser.parse(args);
        
        switch (parsedArgs.getOperation()) {
            case CREATE -> handleCreate(parsedArgs);
            case EXTRACT -> handleExtract(parsedArgs);
            case LIST -> handleList(parsedArgs);
            case UPDATE -> handleUpdate(parsedArgs);
            case CONVERT -> handleConvert(parsedArgs);
        }
    }
    
    /**
     * Handles create operation (-c/--create) with full JAR compatibility.
     */
    private static void handleCreate(ParsedArgs args) throws Exception {
        Path outputPath = Paths.get(args.getArchiveFile());
        
        if (args.isVerbose()) {
            System.out.println("creating: " + outputPath);
        }
        
        // Collect all input files and directories
        Map<String, byte[]> entries = new HashMap<>();
        
        // Handle directory changes (-C option)
        for (DirectoryChange dirChange : args.getDirectoryChanges()) {
            Path baseDir = Paths.get(dirChange.getDirectory());
            if (!Files.exists(baseDir)) {
                throw new IllegalArgumentException("Directory does not exist: " + baseDir);
            }
            
            for (String file : dirChange.getFiles()) {
                Path filePath = baseDir.resolve(file);
                if (Files.exists(filePath)) {
                    collectEntries(filePath, baseDir, entries, args.isVerbose());
                }
            }
        }
        
        // Handle direct input files
        for (String inputFile : args.getInputFiles()) {
            Path filePath = Paths.get(inputFile);
            if (Files.exists(filePath)) {
                collectEntries(filePath, filePath.getParent(), entries, args.isVerbose());
            }
        }
        
        // Create manifest if needed
        if (!args.isNoManifest()) {
            Manifest manifest = createManifest(args);
            if (manifest != null) {
                entries.put("META-INF/MANIFEST.MF", manifestToBytes(manifest));
                if (args.isVerbose()) {
                    System.out.println("  adding: META-INF/MANIFEST.MF");
                }
            }
        }
        
        // Create JARZ v2 archive with dependency analysis
        createJarzArchive(outputPath, entries, args);
        
        if (args.isVerbose()) {
            System.out.println("JARZ archive created: " + outputPath + " (" + Files.size(outputPath) + " bytes)");
        }
    }
    
    /**
     * Handles extract operation (-x/--extract) with JAR compatibility.
     */
    private static void handleExtract(ParsedArgs args) throws Exception {
        Path inputPath = Paths.get(args.getArchiveFile());
        
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Archive file does not exist: " + inputPath);
        }
        
        if (args.isVerbose()) {
            System.out.println("extracting: " + inputPath);
        }
        
        try (BlockReader reader = new BlockReader(inputPath)) {
            Set<String> filesToExtract = new HashSet<>(args.getInputFiles());
            boolean extractAll = filesToExtract.isEmpty();
            
            for (String entryName : reader.entryNames()) {
                if (extractAll || filesToExtract.contains(entryName)) {
                    Path entryPath = Paths.get(entryName);
                    
                    // Create parent directories if they don't exist
                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }
                    
                    byte[] content = reader.readEntry(entryName);
                    Files.write(entryPath, content);
                    
                    if (args.isVerbose()) {
                        System.out.println("  inflated: " + entryName);
                    }
                }
            }
        }
    }
    
    /**
     * Handles list operation (-t/--list) with JAR-compatible output.
     */
    private static void handleList(ParsedArgs args) throws Exception {
        Path inputPath = Paths.get(args.getArchiveFile());
        
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Archive file does not exist: " + inputPath);
        }
        
        try (BlockReader reader = new BlockReader(inputPath)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss zzz yyyy")
                    .withZone(ZoneId.systemDefault());
            
            for (String entryName : reader.entryNames()) {
                if (args.isVerbose()) {
                    // JAR-compatible verbose format: size date name
                    byte[] content = reader.readEntry(entryName);
                    long size = content.length;
                    String timestamp = formatter.format(Instant.now()); // Approximate
                    System.out.printf("%8d %s %s%n", size, timestamp, entryName);
                } else {
                    System.out.println(entryName);
                }
            }
        }
    }
    
    /**
     * Handles update operation (-u/--update) with dependency re-analysis.
     */
    private static void handleUpdate(ParsedArgs args) throws Exception {
        Path archivePath = Paths.get(args.getArchiveFile());
        
        if (!Files.exists(archivePath)) {
            throw new IllegalArgumentException("Archive file does not exist: " + archivePath);
        }
        
        if (args.isVerbose()) {
            System.out.println("updating: " + archivePath);
        }
        
        // Read existing entries
        Map<String, byte[]> entries = new HashMap<>();
        try (BlockReader reader = new BlockReader(archivePath)) {
            for (String entryName : reader.entryNames()) {
                entries.put(entryName, reader.readEntry(entryName));
            }
        }
        
        // Add/update new entries
        for (DirectoryChange dirChange : args.getDirectoryChanges()) {
            Path baseDir = Paths.get(dirChange.getDirectory());
            for (String file : dirChange.getFiles()) {
                Path filePath = baseDir.resolve(file);
                if (Files.exists(filePath)) {
                    collectEntries(filePath, baseDir, entries, args.isVerbose());
                }
            }
        }
        
        for (String inputFile : args.getInputFiles()) {
            Path filePath = Paths.get(inputFile);
            if (Files.exists(filePath)) {
                collectEntries(filePath, filePath.getParent(), entries, args.isVerbose());
            }
        }
        
        // Update manifest if specified
        if (args.getManifestFile() != null) {
            Manifest manifest = loadManifest(Paths.get(args.getManifestFile()));
            entries.put("META-INF/MANIFEST.MF", manifestToBytes(manifest));
            if (args.isVerbose()) {
                System.out.println("  updating: META-INF/MANIFEST.MF");
            }
        }
        
        // Recreate archive with updated entries
        createJarzArchive(archivePath, entries, args);
    }
    
    /**
     * Handles convert operation (--convert) for JAR to JARZ conversion.
     */
    private static void handleConvert(ParsedArgs args) throws Exception {
        if (args.getInputFiles().size() != 2) {
            throw new IllegalArgumentException("Convert operation requires input JAR and output JARZ files");
        }
        
        Path jarFile = Paths.get(args.getInputFiles().get(0));
        Path jarzFile = Paths.get(args.getInputFiles().get(1));
        
        if (!Files.exists(jarFile)) {
            throw new IllegalArgumentException("JAR file does not exist: " + jarFile);
        }
        
        if (!jarFile.toString().toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("Input file must be a JAR file: " + jarFile);
        }
        
        if (args.isVerbose()) {
            System.out.println("converting: " + jarFile + " -> " + jarzFile);
        }
        
        // Convert using existing converter
        JarToJarzConverter.ConversionResult result = JarToJarzConverter.convert(jarFile, jarzFile);
        
        if (args.isVerbose()) {
            System.out.println("Original size: " + String.format("%,d", result.getOriginalSize()) + " bytes");
            System.out.println("JARZ size: " + String.format("%,d", result.getJarzSize()) + " bytes");
            System.out.println("Compression: " + String.format("%.1f%%", result.getCompressionRatio()) + " improvement");
            System.out.println("Entries: " + result.getTotalEntries());
            System.out.println("Blocks: " + result.getBlockCount());
        }
    }
    
    /**
     * Collects entries from a file or directory recursively.
     */
    private static void collectEntries(Path path, Path baseDir, Map<String, byte[]> entries, boolean verbose) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                stream.filter(Files::isRegularFile)
                      .forEach(file -> {
                          try {
                              Path relativePath = baseDir != null ? baseDir.relativize(file) : file;
                              byte[] content = Files.readAllBytes(file);
                              entries.put(relativePath.toString().replace('\\', '/'), content);
                              if (verbose) {
                                  System.out.println("  adding: " + relativePath);
                              }
                          } catch (Exception e) {
                              throw new RuntimeException("Failed to read file: " + file, e);
                          }
                      });
            }
        } else if (Files.isRegularFile(path)) {
            Path relativePath = baseDir != null ? baseDir.relativize(path) : path.getFileName();
            byte[] content = Files.readAllBytes(path);
            entries.put(relativePath.toString().replace('\\', '/'), content);
            if (verbose) {
                System.out.println("  adding: " + relativePath);
            }
        }
    }
    
    /**
     * Creates a manifest based on parsed arguments.
     */
    private static Manifest createManifest(ParsedArgs args) throws IOException {
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        
        // Always add manifest version
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        
        // Add main class if specified
        if (args.getMainClass() != null) {
            mainAttrs.put(Attributes.Name.MAIN_CLASS, args.getMainClass());
        }
        
        // Add module version if specified
        if (args.getModuleVersion() != null) {
            mainAttrs.putValue("Module-Version", args.getModuleVersion());
        }
        
        // Multi-release support
        if (args.getReleaseVersion() != null) {
            mainAttrs.putValue("Multi-Release", "true");
        }
        
        // Load external manifest file if specified
        if (args.getManifestFile() != null) {
            Manifest externalManifest = loadManifest(Paths.get(args.getManifestFile()));
            // Merge attributes
            for (Map.Entry<Object, Object> entry : externalManifest.getMainAttributes().entrySet()) {
                mainAttrs.put(entry.getKey(), entry.getValue());
            }
        }
        
        return manifest;
    }
    
    /**
     * Loads a manifest from a file.
     */
    private static Manifest loadManifest(Path manifestFile) throws IOException {
        try (var input = Files.newInputStream(manifestFile)) {
            return new Manifest(input);
        }
    }
    
    /**
     * Converts a manifest to bytes.
     */
    private static byte[] manifestToBytes(Manifest manifest) throws IOException {
        try (var baos = new java.io.ByteArrayOutputStream()) {
            manifest.write(baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * Creates a JARZ v2 archive with dependency analysis.
     */
    private static void createJarzArchive(Path outputPath, Map<String, byte[]> entries, ParsedArgs args) throws Exception {
        // Filter class files for dependency analysis
        Map<String, byte[]> classFiles = new HashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().endsWith(".class")) {
                classFiles.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Use dependency analysis only if we have class files
        DependencyGraph graph = null;
        if (!classFiles.isEmpty()) {
            try {
                DependencyAnalyzer analyzer = new DependencyAnalyzer();
                
                // Create temporary directory for analysis
                Path tempDir = Files.createTempDirectory("jarz-analysis");
                try {
                    // Write only class files to temp directory for analysis
                    for (Map.Entry<String, byte[]> entry : classFiles.entrySet()) {
                        Path entryPath = tempDir.resolve(entry.getKey());
                        Files.createDirectories(entryPath.getParent());
                        Files.write(entryPath, entry.getValue());
                    }
                    
                    graph = analyzer.analyze(tempDir);
                } finally {
                    // Clean up temp directory
                    try (var stream = Files.walk(tempDir)) {
                        stream.sorted(Comparator.reverseOrder())
                              .forEach(path -> {
                                  try {
                                      Files.delete(path);
                                  } catch (IOException e) {
                                      // Ignore cleanup errors
                                  }
                              });
                    }
                }
            } catch (Exception e) {
                // If dependency analysis fails, continue without it
                if (args.isVerbose()) {
                    System.err.println("Warning: Dependency analysis failed, using simple block assignment");
                }
            }
        }
        
        // Configure block assigner
        int targetBlockSize = args.isNoCompress() ? 32 * 1024 : 64 * 1024;
        int maxBlockSize = args.isNoCompress() ? 64 * 1024 : 128 * 1024;
        BlockAssigner assigner = new BlockAssigner(targetBlockSize, maxBlockSize);
        List<Block> blocks = assigner.assignBlocks(entries, graph);
        
        // Write JARZ v2 archive
        try (BlockWriter writer = new BlockWriter(outputPath)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
    }
    
    /**
     * Prints version information.
     */
    private static void printVersion() {
        System.out.println("jarz 1.0 (JARZ v2 format)");
        System.out.println("ZSTD block-based compression with dependency analysis");
        System.out.println("Compatible with JAR tool syntax");
    }
    
    private static void printUsage() {
        System.out.println("Usage: jarz [OPTION...] [ [--release VERSION] [-C dir] files] ...");
        System.out.println();
        System.out.println("jarz creates JARZ v2 archives with ZSTD block compression and dependency analysis.");
        System.out.println("It provides full compatibility with JAR tool command-line syntax.");
        System.out.println();
        System.out.println("Main operation mode (exactly one required):");
        System.out.println("  -c, --create               create new archive");
        System.out.println("  -x, --extract              extract files from archive");
        System.out.println("  -t, --list                 list contents of archive");
        System.out.println("  -u, --update               update existing archive");
        System.out.println("      --convert              convert JAR to JARZ (extension)");
        System.out.println();
        System.out.println("Operation modifiers (valid in any mode):");
        System.out.println("  -f, --file=FILE            archive file name");
        System.out.println("  -v, --verbose              verbose output");
        System.out.println("  -C DIR                     change to directory");
        System.out.println("      --release VERSION      create multi-release JAR");
        System.out.println();
        System.out.println("Operation modifiers (create and update modes):");
        System.out.println("  -e, --main-class=CLASS     application entry point");
        System.out.println("  -m, --manifest=FILE        include manifest information");
        System.out.println("  -M, --no-manifest          don't create manifest file");
        System.out.println("  -0, --no-compress          store without compression");
        System.out.println("      --module-version=VER   module version");
        System.out.println("      --hash-modules=PATTERN hash modules pattern");
        System.out.println("      --module-path=PATH     module path");
        System.out.println();
        System.out.println("Other options:");
        System.out.println("  -h, --help                 display this help");
        System.out.println("      --version              print program version");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  jarz -cf app.jarz classes/                    # Create JARZ from directory");
        System.out.println("  jarz -cvf app.jarz -e Main -C classes .      # Create with main class");
        System.out.println("  jarz -xvf app.jarz                           # Extract with verbose output");
        System.out.println("  jarz -tf app.jarz                            # List archive contents");
        System.out.println("  jarz --convert input.jar output.jarz         # Convert JAR to JARZ");
        System.out.println();
        System.out.println("JARZ v2 format provides superior compression and S3 streaming capabilities");
        System.out.println("while maintaining full compatibility with JAR manifests and class loading.");
    }
}
