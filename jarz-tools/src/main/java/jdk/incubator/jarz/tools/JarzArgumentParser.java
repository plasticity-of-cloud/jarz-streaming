package jdk.incubator.jarz.tools;

import java.util.*;

/**
 * JAR-compatible command-line argument parser for JARZ CLI tool.
 * 
 * <p>This parser supports all standard JAR tool command-line options and flags,
 * plus JARZ-specific extensions. It follows the same syntax patterns as the
 * standard JAR tool for maximum compatibility.
 * 
 * <h2>Supported Operations</h2>
 * <ul>
 * <li>{@code -c, --create} - Create JARZ archive</li>
 * <li>{@code -x, --extract} - Extract JARZ archive</li>
 * <li>{@code -t, --list} - List archive contents</li>
 * <li>{@code -u, --update} - Update existing archive</li>
 * <li>{@code --convert} - Convert JAR to JARZ (JARZ-specific)</li>
 * </ul>
 * 
 * <h2>Supported Options</h2>
 * <ul>
 * <li>{@code -f, --file} - Archive file name</li>
 * <li>{@code -v, --verbose} - Verbose output</li>
 * <li>{@code -m, --manifest} - Include manifest file</li>
 * <li>{@code -e, --main-class} - Set main class</li>
 * <li>{@code -C} - Change directory</li>
 * <li>{@code --release} - Multi-release JAR version</li>
 * <li>{@code --module-version} - Module version</li>
 * <li>{@code --hash-modules} - Hash modules pattern</li>
 * <li>{@code --module-path} - Module path</li>
 * <li>{@code -0, --no-compress} - Store without compression</li>
 * <li>{@code -M, --no-manifest} - Don't create manifest</li>
 * </ul>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public final class JarzArgumentParser {
    
    /**
     * Parsed command-line arguments with all options and parameters.
     */
    public static final class ParsedArgs {
        private final Operation operation;
        private final String archiveFile;
        private final boolean verbose;
        private final String manifestFile;
        private final String mainClass;
        private final List<String> inputFiles;
        private final List<DirectoryChange> directoryChanges;
        private final String releaseVersion;
        private final String moduleVersion;
        private final String hashModulesPattern;
        private final String modulePath;
        private final boolean noCompress;
        private final boolean noManifest;
        
        public ParsedArgs(Operation operation, String archiveFile, boolean verbose,
                         String manifestFile, String mainClass, List<String> inputFiles,
                         List<DirectoryChange> directoryChanges, String releaseVersion,
                         String moduleVersion, String hashModulesPattern, String modulePath,
                         boolean noCompress, boolean noManifest) {
            this.operation = operation;
            this.archiveFile = archiveFile;
            this.verbose = verbose;
            this.manifestFile = manifestFile;
            this.mainClass = mainClass;
            this.inputFiles = List.copyOf(inputFiles);
            this.directoryChanges = List.copyOf(directoryChanges);
            this.releaseVersion = releaseVersion;
            this.moduleVersion = moduleVersion;
            this.hashModulesPattern = hashModulesPattern;
            this.modulePath = modulePath;
            this.noCompress = noCompress;
            this.noManifest = noManifest;
        }
        
        public Operation getOperation() { return operation; }
        public String getArchiveFile() { return archiveFile; }
        public boolean isVerbose() { return verbose; }
        public String getManifestFile() { return manifestFile; }
        public String getMainClass() { return mainClass; }
        public List<String> getInputFiles() { return inputFiles; }
        public List<DirectoryChange> getDirectoryChanges() { return directoryChanges; }
        public String getReleaseVersion() { return releaseVersion; }
        public String getModuleVersion() { return moduleVersion; }
        public String getHashModulesPattern() { return hashModulesPattern; }
        public String getModulePath() { return modulePath; }
        public boolean isNoCompress() { return noCompress; }
        public boolean isNoManifest() { return noManifest; }
    }
    
    /**
     * Supported operations matching JAR tool functionality.
     */
    public enum Operation {
        CREATE,    // -c, --create
        EXTRACT,   // -x, --extract  
        LIST,      // -t, --list
        UPDATE,    // -u, --update
        CONVERT,   // --convert (JARZ-specific)
        TREE       // --tree (JARZ-specific: show block structure)
    }
    
    /**
     * Directory change specification for -C option.
     */
    public static final class DirectoryChange {
        private final String directory;
        private final List<String> files;
        
        public DirectoryChange(String directory, List<String> files) {
            this.directory = directory;
            this.files = List.copyOf(files);
        }
        
        public String getDirectory() { return directory; }
        public List<String> getFiles() { return files; }
    }
    
    /**
     * Parses command-line arguments using JAR-compatible syntax.
     * 
     * @param args command-line arguments
     * @return parsed arguments
     * @throws IllegalArgumentException if arguments are invalid
     */
    public static ParsedArgs parse(String[] args) throws IllegalArgumentException {
        if (args.length == 0) {
            throw new IllegalArgumentException("No arguments provided");
        }
        
        ArgumentIterator iter = new ArgumentIterator(args);
        Operation operation = null;
        String archiveFile = null;
        boolean verbose = false;
        String manifestFile = null;
        String mainClass = null;
        List<String> inputFiles = new ArrayList<>();
        List<DirectoryChange> directoryChanges = new ArrayList<>();
        String releaseVersion = null;
        String moduleVersion = null;
        String hashModulesPattern = null;
        String modulePath = null;
        boolean noCompress = false;
        boolean noManifest = false;
        
        while (iter.hasNext()) {
            String arg = iter.next();
            
            if (arg.startsWith("-") && !arg.equals("-")) {
                // Handle combined flags like -cvf or individual flags
                if (arg.startsWith("--")) {
                    // Long options
                    switch (arg) {
                        case "--create":
                            operation = Operation.CREATE;
                            break;
                        case "--extract":
                            operation = Operation.EXTRACT;
                            break;
                        case "--list":
                            operation = Operation.LIST;
                            break;
                        case "--update":
                            operation = Operation.UPDATE;
                            break;
                        case "--convert":
                            operation = Operation.CONVERT;
                            break;
                        case "--tree":
                            operation = Operation.TREE;
                            break;
                        case "--verbose":
                            verbose = true;
                            break;
                        case "--no-compress":
                            noCompress = true;
                            break;
                        case "--no-manifest":
                            noManifest = true;
                            break;
                        case "--file":
                            archiveFile = iter.nextRequired("--file");
                            break;
                        case "--manifest":
                            manifestFile = iter.nextRequired("--manifest");
                            break;
                        case "--main-class":
                            mainClass = iter.nextRequired("--main-class");
                            break;
                        case "--release":
                            releaseVersion = iter.nextRequired("--release");
                            break;
                        case "--module-version":
                            moduleVersion = iter.nextRequired("--module-version");
                            break;
                        case "--hash-modules":
                            hashModulesPattern = iter.nextRequired("--hash-modules");
                            break;
                        case "--module-path":
                            modulePath = iter.nextRequired("--module-path");
                            break;
                        case "--help":
                            throw new HelpRequestedException();
                        case "--version":
                            throw new VersionRequestedException();
                        default: {
                            if (arg.startsWith("--file=")) {
                                archiveFile = arg.substring(7);
                            } else if (arg.startsWith("--manifest=")) {
                                manifestFile = arg.substring(11);
                            } else if (arg.startsWith("--main-class=")) {
                                mainClass = arg.substring(13);
                            } else if (arg.startsWith("--release=")) {
                                releaseVersion = arg.substring(10);
                            } else if (arg.startsWith("--module-version=")) {
                                moduleVersion = arg.substring(17);
                            } else if (arg.startsWith("--hash-modules=")) {
                                hashModulesPattern = arg.substring(15);
                            } else if (arg.startsWith("--module-path=")) {
                                modulePath = arg.substring(14);
                            } else {
                                throw new IllegalArgumentException("Unknown option: " + arg);
                            }
                        }
                    }
                } else {
                    // Short options (can be combined like -cvf)
                    String flags = arg.substring(1);
                    for (int i = 0; i < flags.length(); i++) {
                        char flag = flags.charAt(i);
                        switch (flag) {
                            case 'c':
                                operation = Operation.CREATE;
                                break;
                            case 'x':
                                operation = Operation.EXTRACT;
                                break;
                            case 't':
                                operation = Operation.LIST;
                                break;
                            case 'u':
                                operation = Operation.UPDATE;
                                break;
                            case 'v':
                                verbose = true;
                                break;
                            case '0':
                                noCompress = true;
                                break;
                            case 'M':
                                noManifest = true;
                                break;
                            case 'f': {
                                // -f can be anywhere in combined flags, get next arg
                                archiveFile = iter.nextRequired("-f");
                                break;
                            }
                            case 'm': {
                                // -m can be anywhere in combined flags, get next arg  
                                manifestFile = iter.nextRequired("-m");
                                break;
                            }
                            case 'e': {
                                // -e can be anywhere in combined flags, get next arg
                                mainClass = iter.nextRequired("-e");
                                break;
                            }
                            case 'C': {
                                // -C can be anywhere in combined flags, get next arg
                                String dir = iter.nextRequired("-C");
                                List<String> files = new ArrayList<>();
                                // Collect files until next option or end
                                while (iter.hasNext() && !iter.peek().startsWith("-")) {
                                    files.add(iter.next());
                                }
                                directoryChanges.add(new DirectoryChange(dir, files));
                                break;
                            }
                            case 'h':
                                throw new HelpRequestedException();
                            default:
                                throw new IllegalArgumentException("Unknown flag: -" + flag);
                        }
                    }
                }
            } else {
                // Input file or directory
                inputFiles.add(arg);
            }
        }
        
        // Validation
        if (operation == null) {
            throw new IllegalArgumentException("No operation specified (use -c, -x, -t, -u, --convert, or --tree)");
        }
        
        if (archiveFile == null && (operation == Operation.CREATE || operation == Operation.EXTRACT || 
                                   operation == Operation.LIST || operation == Operation.UPDATE)) {
            throw new IllegalArgumentException("Archive file must be specified with -f option");
        }
        
        return new ParsedArgs(operation, archiveFile, verbose, manifestFile, mainClass,
                             inputFiles, directoryChanges, releaseVersion, moduleVersion,
                             hashModulesPattern, modulePath, noCompress, noManifest);
    }
    
    /**
     * Iterator helper for parsing arguments.
     */
    private static class ArgumentIterator {
        private final String[] args;
        private int index = 0;
        
        ArgumentIterator(String[] args) {
            this.args = args;
        }
        
        boolean hasNext() {
            return index < args.length;
        }
        
        String next() {
            if (!hasNext()) {
                throw new IllegalArgumentException("Unexpected end of arguments");
            }
            return args[index++];
        }
        
        String peek() {
            if (!hasNext()) {
                return null;
            }
            return args[index];
        }
        
        String nextRequired(String option) {
            if (!hasNext()) {
                throw new IllegalArgumentException("Option " + option + " requires an argument");
            }
            return next();
        }
    }
    
    /**
     * Exception thrown when help is requested.
     */
    public static class HelpRequestedException extends RuntimeException {
        public HelpRequestedException() {
            super("Help requested");
        }
    }
    
    /**
     * Exception thrown when version is requested.
     */
    public static class VersionRequestedException extends RuntimeException {
        public VersionRequestedException() {
            super("Version requested");
        }
    }
}
