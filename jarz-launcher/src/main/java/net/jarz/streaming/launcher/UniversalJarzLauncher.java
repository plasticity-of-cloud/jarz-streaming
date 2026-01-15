package net.jarz.streaming.launcher;

import net.jarz.streaming.classloader.JarzApplicationClassLoader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * Universal JARZ launcher with auto-discovery capabilities.
 * 
 * <p>This launcher provides a drop-in replacement for {@code java -jar} that works
 * with JARZ files and supports multiple ClassLoader strategies including local,
 * S3, and CDN streaming.
 * 
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * # Auto-discovery from directory
 * jarz-launcher --auto-discover /opt/myapp/jarz server.properties
 * 
 * # Environment-driven launcher
 * export JARZ_PATH="/opt/myapp/jarz"
 * export JARZ_MAIN_CLASS="com.example.Application"
 * jarz-launcher server.properties
 * 
 * # S3 streaming
 * jarz-launcher --s3 s3://bucket/myapp/jarz server.properties
 * 
 * # CDN streaming  
 * jarz-launcher --cdn https://d123.cloudfront.net/myapp server.properties
 * }</pre>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class UniversalJarzLauncher {
    
    private static final String VERSION = "1.0-SNAPSHOT";
    
    public static void main(String[] args) {
        try {
            new UniversalJarzLauncher().launch(args);
        } catch (Exception e) {
            System.err.println("❌ JARZ Launcher failed: " + e.getMessage());
            if (Boolean.parseBoolean(System.getenv("JARZ_DEBUG"))) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    public void launch(String[] args) throws Exception {
        LaunchConfig config = parseArguments(args);
        
        if (config.showHelp) {
            showHelp();
            return;
        }
        
        if (config.showVersion) {
            showVersion();
            return;
        }
        
        System.out.println("🚀 JARZ Universal Launcher v" + VERSION);
        System.out.println("📦 Mode: " + config.mode);
        System.out.println("🎯 Target: " + config.jarzPath);
        
        // Auto-discover main class and JARZ files
        JarzDiscovery discovery = new JarzDiscovery(config);
        DiscoveryResult result = discovery.discover();
        
        System.out.println("🔍 Main class: " + result.mainClass);
        System.out.println("📚 JARZ files: " + result.jarzFiles.size());
        
        // Create appropriate ClassLoader
        ClassLoader classLoader = createClassLoader(config, result);
        
        // Load and invoke main class
        Class<?> mainClassObj = classLoader.loadClass(result.mainClass);
        Method mainMethod = mainClassObj.getMethod("main", String[].class);
        
        System.out.println("✅ Application loaded successfully");
        System.out.println("⚡ Starting application...\n");
        
        mainMethod.invoke(null, (Object) config.applicationArgs);
    }
    
    private LaunchConfig parseArguments(String[] args) {
        LaunchConfig config = new LaunchConfig();
        List<String> appArgs = new ArrayList<>();
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            switch (arg) {
                case "--help":
                case "-h":
                    config.showHelp = true;
                    break;
                case "--version":
                case "-v":
                    config.showVersion = true;
                    break;
                case "--auto-discover":
                case "-a": {
                    if (i + 1 < args.length) {
                        config.jarzPath = args[++i];
                        config.mode = LaunchMode.AUTO_DISCOVER;
                    }
                    break;
                }
                case "--local":
                case "-l": {
                    if (i + 1 < args.length) {
                        config.jarzPath = args[++i];
                        config.mode = LaunchMode.LOCAL;
                    }
                    break;
                }
                case "--s3":
                case "-s": {
                    if (i + 1 < args.length) {
                        config.jarzPath = args[++i];
                        config.mode = LaunchMode.S3;
                    }
                    break;
                }
                case "--cdn":
                case "-c": {
                    if (i + 1 < args.length) {
                        config.jarzPath = args[++i];
                        config.mode = LaunchMode.CDN;
                    }
                    break;
                }
                case "--main-class":
                case "-m": {
                    if (i + 1 < args.length) {
                        config.mainClass = args[++i];
                    }
                    break;
                }
                case "--debug":
                case "-d":
                    config.debug = true;
                    break;
                default:
                    appArgs.add(arg);
                    break;
            }
        }
        
        // Use environment variables if not specified via arguments
        if (config.jarzPath == null) {
            config.jarzPath = System.getenv("JARZ_PATH");
            if (config.jarzPath != null) {
                config.mode = LaunchMode.fromEnvironment();
            }
        }
        
        if (config.mainClass == null) {
            config.mainClass = System.getenv("JARZ_MAIN_CLASS");
        }
        
        config.applicationArgs = appArgs.toArray(new String[0]);
        return config;
    }
    
    private ClassLoader createClassLoader(LaunchConfig config, DiscoveryResult result) throws Exception {
        switch (config.mode) {
            case LOCAL:
            case AUTO_DISCOVER:
                return createLocalClassLoader(result);
            case S3:
                return createS3ClassLoader(config, result);
            case CDN:
                return createCdnClassLoader(config, result);
            default:
                throw new IllegalArgumentException("Unsupported launch mode: " + config.mode);
        }
    }
    
    private ClassLoader createLocalClassLoader(DiscoveryResult result) throws IOException {
        // For simplicity, use the primary JARZ file
        // TODO: Implement composite ClassLoader for multiple JARZ files
        Path primaryJarz = result.jarzFiles.get(0);
        return new JarzApplicationClassLoader(primaryJarz);
    }
    
    private ClassLoader createS3ClassLoader(LaunchConfig config, DiscoveryResult result) throws Exception {
        // TODO: Implement S3 ClassLoader creation
        throw new UnsupportedOperationException("S3 ClassLoader not yet implemented in launcher");
    }
    
    private ClassLoader createCdnClassLoader(LaunchConfig config, DiscoveryResult result) throws Exception {
        // TODO: Implement CDN ClassLoader creation  
        throw new UnsupportedOperationException("CDN ClassLoader not yet implemented in launcher");
    }
    
    private void showHelp() {
        System.out.println(String.format(
            "JARZ Universal Launcher v%s\n" +
            "\n" +
            "USAGE:\n" +
            "    jarz-launcher [OPTIONS] [APPLICATION_ARGS...]\n" +
            "\n" +
            "OPTIONS:\n" +
            "    --auto-discover, -a PATH    Auto-discover JARZ files and main class from PATH\n" +
            "    --local, -l PATH           Use local JARZ files from PATH\n" +
            "    --s3, -s S3_URL            Stream JARZ files from S3 (s3://bucket/prefix)\n" +
            "    --cdn, -c CDN_URL          Stream JARZ files from CDN (https://...)\n" +
            "    --main-class, -m CLASS     Override main class (auto-detected if not specified)\n" +
            "    --debug, -d                Enable debug output\n" +
            "    --help, -h                 Show this help message\n" +
            "    --version, -v              Show version information\n" +
            "\n" +
            "ENVIRONMENT VARIABLES:\n" +
            "    JARZ_PATH                  Default path/URL for JARZ files\n" +
            "    JARZ_MAIN_CLASS           Default main class name\n" +
            "    JARZ_MODE                 Default mode: auto, local, s3, cdn\n" +
            "    JARZ_DEBUG                Enable debug output (true/false)\n" +
            "\n" +
            "EXAMPLES:\n" +
            "    # Auto-discover application from local directory\n" +
            "    jarz-launcher --auto-discover /opt/myapp/jarz server.properties\n" +
            "    \n" +
            "    # Environment-driven launcher (container-friendly)\n" +
            "    export JARZ_PATH=\"/opt/myapp/jarz\"\n" +
            "    export JARZ_MAIN_CLASS=\"com.example.Application\"\n" +
            "    jarz-launcher server.properties\n" +
            "    \n" +
            "    # S3 streaming deployment\n" +
            "    jarz-launcher --s3 s3://artifacts/myapp/v1.0 server.properties\n" +
            "    \n" +
            "    # CDN streaming deployment\n" +
            "    jarz-launcher --cdn https://d123.cloudfront.net/myapp server.properties\n" +
            "\n" +
            "For more information, see: https://github.com/plasticity-of-cloud/jdk-enhancements\n",
            VERSION));
    }
    
    private void showVersion() {
        System.out.println("JARZ Universal Launcher v" + VERSION);
        System.out.println("Java Runtime: " + System.getProperty("java.version"));
        System.out.println("Build: " + System.getProperty("java.vm.name"));
    }
    
    // Configuration classes
    static class LaunchConfig {
        LaunchMode mode = LaunchMode.AUTO_DISCOVER;
        String jarzPath;
        String mainClass;
        String[] applicationArgs = new String[0];
        boolean showHelp = false;
        boolean showVersion = false;
        boolean debug = false;
    }
    
    enum LaunchMode {
        AUTO_DISCOVER, LOCAL, S3, CDN;
        
        static LaunchMode fromEnvironment() {
            String mode = System.getenv("JARZ_MODE");
            if (mode == null) return AUTO_DISCOVER;
            
            switch (mode.toLowerCase()) {
                case "local":
                    return LOCAL;
                case "s3":
                    return S3;
                case "cdn":
                    return CDN;
                default:
                    return AUTO_DISCOVER;
            }
        }
    }
    
    static class DiscoveryResult {
        String mainClass;
        List<Path> jarzFiles;
        
        DiscoveryResult(String mainClass, List<Path> jarzFiles) {
            this.mainClass = mainClass;
            this.jarzFiles = jarzFiles;
        }
    }
}
