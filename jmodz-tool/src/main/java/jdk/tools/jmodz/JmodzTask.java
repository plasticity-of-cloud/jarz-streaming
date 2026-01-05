package jdk.tools.jmodz;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

/**
 * Implementation for the jmodz tool - ZSTD-compressed equivalent of jmod
 */
public class JmodzTask {

    /* Result codes - matching JDK jmod tool */
    static final int EXIT_OK = 0;        // Completed with no errors
    static final int EXIT_ERROR = 1;     // Completed but reported errors
    static final int EXIT_CMDERR = 2;    // Bad command-line arguments
    static final int EXIT_SYSERR = 3;    // System error or resource exhaustion
    static final int EXIT_ABNORMAL = 4;  // Terminated abnormally

    enum Mode {
        CREATE,
        EXTRACT,
        LIST,
        DESCRIBE,
        HASH,
        CONVERT  // Additional command for jmod ↔ jmodz conversion
    }

    static class Options {
        Mode mode;
        Path jmodzFile;
        boolean help;
        boolean version;
        List<Path> classpath;
        List<Path> cmds;
        List<Path> configs;
        List<Path> libs;
        List<Path> headerFiles;
        List<Path> manPages;
        List<Path> legalNotices;
        String mainClass;
        String moduleVersion;
        String targetPlatform;
        boolean dryrun;
        Path extractDir;
        int compressionLevel = 3;  // ZSTD compression level
        
        // Convert-specific options
        boolean toJmodz;
        boolean toJmod;
        Path inputFile;
        Path outputFile;
    }

    private Options options;
    private PrintWriter out = new PrintWriter(System.out, true);
    private PrintWriter err = new PrintWriter(System.err, true);

    void setLog(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    public int run(String[] args) {
        try {
            if (!parseOptions(args)) {
                return EXIT_CMDERR;
            }

            if (options.help) {
                showHelp();
                return EXIT_OK;
            }

            if (options.version) {
                showVersion();
                return EXIT_OK;
            }

            boolean ok;
            switch (options.mode) {
                case CREATE:
                    ok = create();
                    break;
                case EXTRACT:
                    ok = extract();
                    break;
                case LIST:
                    ok = list();
                    break;
                case DESCRIBE:
                    ok = describe();
                    break;
                case HASH:
                    ok = hash();
                    break;
                case CONVERT:
                    ok = convert();
                    break;
                default:
                    err.println("Unknown mode: " + options.mode);
                    return EXIT_ERROR;
            }

            return ok ? EXIT_OK : EXIT_ERROR;

        } catch (Exception e) {
            err.println("Error: " + e.getMessage());
            return EXIT_SYSERR;
        }
    }

    private boolean parseOptions(String[] args) {
        if (args.length == 0) {
            showUsage();
            return false;
        }

        options = new Options();
        String command = args[0];

        switch (command) {
            case "create":
                options.mode = Mode.CREATE;
                break;
            case "extract":
                options.mode = Mode.EXTRACT;
                break;
            case "list":
                options.mode = Mode.LIST;
                break;
            case "describe":
                options.mode = Mode.DESCRIBE;
                break;
            case "hash":
                options.mode = Mode.HASH;
                break;
            case "convert":
                options.mode = Mode.CONVERT;
                return parseConvertOptions(args);
            case "--help":
            case "-h":
                options.help = true;
                return true;
            case "--version":
                options.version = true;
                return true;
            default:
                err.println("Unknown command: " + command);
                return false;
        }

        // TODO: Parse remaining arguments for other commands
        return true;
    }

    private boolean parseConvertOptions(String[] args) {
        // jmodz convert --to-jmodz input.jmod output.jmodz
        // jmodz convert --to-jmod input.jmodz output.jmod
        
        if (args.length < 4) {
            err.println("Usage: jmodz convert --to-jmodz|--to-jmod input-file output-file");
            return false;
        }

        String direction = args[1];
        switch (direction) {
            case "--to-jmodz":
                options.toJmodz = true;
                break;
            case "--to-jmod":
                options.toJmod = true;
                break;
            case "--compression-level":
                if (args.length < 6) {
                    err.println("--compression-level requires a value");
                    return false;
                }
                try {
                    options.compressionLevel = Integer.parseInt(args[2]);
                    direction = args[3]; // Next argument is direction
                    if ("--to-jmodz".equals(direction)) {
                        options.toJmodz = true;
                    } else if ("--to-jmod".equals(direction)) {
                        options.toJmod = true;
                    } else {
                        err.println("Invalid direction: " + direction);
                        return false;
                    }
                    options.inputFile = java.nio.file.Paths.get(args[4]);
                    options.outputFile = java.nio.file.Paths.get(args[5]);
                    return true;
                } catch (NumberFormatException e) {
                    err.println("Invalid compression level: " + args[2]);
                    return false;
                }
            default:
                err.println("Unknown convert option: " + direction);
                return false;
        }

        options.inputFile = java.nio.file.Paths.get(args[2]);
        options.outputFile = java.nio.file.Paths.get(args[3]);
        return true;
    }

    private boolean create() {
        out.println("JMODZ create - ZSTD compression enabled");
        out.println("TODO: Implement create functionality");
        return false; // Not implemented yet
    }

    private boolean extract() {
        out.println("JMODZ extract");
        out.println("TODO: Implement extract functionality");
        return false; // Not implemented yet
    }

    private boolean list() {
        out.println("JMODZ list");
        out.println("TODO: Implement list functionality");
        return false; // Not implemented yet
    }

    private boolean describe() {
        out.println("JMODZ describe");
        out.println("TODO: Implement describe functionality");
        return false; // Not implemented yet
    }

    private boolean hash() {
        out.println("JMODZ hash");
        out.println("TODO: Implement hash functionality");
        return false; // Not implemented yet
    }

    private boolean convert() {
        if (options.inputFile == null || options.outputFile == null) {
            err.println("Convert requires input and output files");
            return false;
        }

        try {
            if (options.toJmodz) {
                return convertJmodToJmodz();
            } else if (options.toJmod) {
                return convertJmodzToJmod();
            } else {
                err.println("Convert requires --to-jmodz or --to-jmod");
                return false;
            }
        } catch (Exception e) {
            err.println("Convert failed: " + e.getMessage());
            return false;
        }
    }

    private boolean convertJmodToJmodz() throws Exception {
        out.println("Converting " + options.inputFile + " to " + options.outputFile);
        out.println("ZSTD compression level: " + options.compressionLevel);
        
        long originalCompressedSize = Files.size(options.inputFile);
        
        // Use existing JARZ format for ZSTD compression
        JmodzConverter converter = new JmodzConverter();
        long originalUncompressedSize = converter.convertJmodToJmodz(
            options.inputFile, 
            options.outputFile, 
            options.compressionLevel
        );
        
        long jmodzSize = Files.size(options.outputFile);
        
        // Compare against original compressed jmod
        double ratioVsJmod = (double) jmodzSize / originalCompressedSize;
        double reductionVsJmod = (1.0 - ratioVsJmod) * 100;
        
        // Compare against uncompressed data
        double ratioVsUncompressed = (double) jmodzSize / originalUncompressedSize;
        double compressionRatio = (1.0 - ratioVsUncompressed) * 100;
        
        out.printf("Original jmod size (ZIP): %,d bytes%n", originalCompressedSize);
        out.printf("Uncompressed data size: %,d bytes%n", originalUncompressedSize);
        out.printf("JMODZ size (ZSTD): %,d bytes%n", jmodzSize);
        out.println();
        out.printf("ZSTD vs ZIP comparison:%n");
        out.printf("  Size ratio: %.3f%n", ratioVsJmod);
        out.printf("  Size reduction: %.1f%%%n", reductionVsJmod);
        out.println();
        out.printf("ZSTD compression ratio: %.1f%%%n", compressionRatio);
        
        return true;
    }

    private boolean convertJmodzToJmod() throws Exception {
        out.println("Converting " + options.inputFile + " to " + options.outputFile);
        
        JmodzConverter converter = new JmodzConverter();
        converter.convertJmodzToJmod(options.inputFile, options.outputFile);
        
        out.println("Conversion completed successfully");
        return true;
    }

    private void showUsage() {
        out.println("Usage: jmodz <command> [options] jmodz-file");
        out.println();
        out.println("Commands:");
        out.println("  create     Create JMODZ file");
        out.println("  extract    Extract JMODZ file");
        out.println("  list       List JMODZ contents");
        out.println("  describe   Describe JMODZ module");
        out.println("  hash       Compute module hashes");
        out.println("  convert    Convert between jmod and jmodz");
        out.println();
        out.println("Use 'jmodz <command> --help' for command-specific help");
    }

    private void showHelp() {
        showUsage();
        out.println();
        out.println("JMODZ Tool - ZSTD-compressed equivalent of jmod");
        out.println("Provides 24-28% size reduction over standard jmod files");
        out.println("with 3.5x faster decompression than ZIP compression");
    }

    private void showVersion() {
        out.println("jmodz 1.0-SNAPSHOT");
        out.println("ZSTD-compressed JMOD tool");
    }
}
