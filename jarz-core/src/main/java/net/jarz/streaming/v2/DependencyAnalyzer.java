package net.jarz.streaming.v2;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.spi.ToolProvider;
import java.util.stream.*;

/**
 * Analyzes class dependencies using jdeps for block assignment.
 * Uses JDK's built-in jdeps tool for static analysis.
 */
public class DependencyAnalyzer {
    
    private static final Pattern DEP_PATTERN = Pattern.compile(
        "^\\s+(\\S+)\\s+->\\s+(\\S+)\\s+.*$"
    );
    
    /**
     * Analyze dependencies in a JAR or directory.
     */
    public DependencyGraph analyze(Path input) throws IOException {
        return analyze(input, null);
    }
    
    /**
     * Analyze dependencies in a JAR or directory with optional classpath.
     */
    public DependencyGraph analyze(Path input, String classpath) throws IOException {
        try {
            var tool = ToolProvider.findFirst("jdeps")
                .orElseThrow(() -> new IOException("jdeps tool not found"));
            
            var out = new StringWriter();
            var err = new StringWriter();
            
            List<String> args = new ArrayList<>();
            args.add("-verbose:class");
            args.add("-filter:none");
            
            if (classpath != null && !classpath.isEmpty()) {
                // Check if we have modular JARs that need module-path
                List<Path> classpathJars = parseClasspath(classpath);
                List<Path> modularJars = new ArrayList<>();
                List<Path> nonModularJars = new ArrayList<>();
                
                for (Path jar : classpathJars) {
                    if (isModularJar(jar)) {
                        modularJars.add(jar);
                    } else {
                        nonModularJars.add(jar);
                    }
                }
                
                // Use module-path for modular JARs
                if (!modularJars.isEmpty()) {
                    args.add("--module-path");
                    args.add(modularJars.stream()
                        .map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator)));
                }
                
                // Use class-path for non-modular JARs
                if (!nonModularJars.isEmpty()) {
                    args.add("-cp");
                    args.add(nonModularJars.stream()
                        .map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator)));
                }
            }
            
            args.add(input.toString());
            
            int result = tool.run(
                new PrintWriter(out), 
                new PrintWriter(err),
                args.toArray(new String[0])
            );
            
            if (result != 0) {
                // jdeps may return non-zero for warnings, try to parse anyway
                String errStr = err.toString();
                if (!errStr.isEmpty() && !errStr.contains("Warning")) {
                    // If classpath was provided and failed, try without classpath
                    if (classpath != null && !classpath.isEmpty()) {
                        return analyze(input, null);
                    }
                    throw new IOException("jdeps failed: " + errStr);
                }
            }
            
            return parseOutput(out.toString());
            
        } catch (RuntimeException | IOException e) {
            // If classpath was provided and failed, try without classpath
            if (classpath != null && !classpath.isEmpty()) {
                return analyzeWithoutClasspath(input);
            }
            // If no classpath or fallback also failed, use class file analysis
            return analyzeClassFiles(input.getParent() != null ? input.getParent() : input);
        }
    }
    
    private DependencyGraph analyzeWithoutClasspath(Path input) throws IOException {
        try {
            var tool = ToolProvider.findFirst("jdeps")
                .orElseThrow(() -> new IOException("jdeps tool not found"));
            
            var out = new StringWriter();
            var err = new StringWriter();
            
            int result = tool.run(
                new PrintWriter(out), 
                new PrintWriter(err),
                "-verbose:class",
                "-filter:none",
                input.toString()
            );
            
            if (result != 0) {
                String errStr = err.toString();
                if (!errStr.isEmpty() && !errStr.contains("Warning")) {
                    throw new IOException("jdeps failed: " + errStr);
                }
            }
            
            return parseOutput(out.toString());
            
        } catch (Exception e) {
            // Final fallback to class file analysis
            return analyzeClassFiles(input.getParent() != null ? input.getParent() : input);
        }
    }
    
    /**
     * Analyze dependencies from class files directly (fallback if jdeps unavailable).
     */
    public DependencyGraph analyzeClassFiles(Path dir) throws IOException {
        var graph = new DependencyGraph();
        
        try (var walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".class"))
                .forEach(p -> {
                    try {
                        String className = pathToClassName(dir, p);
                        Set<String> deps = extractDependencies(Files.readAllBytes(p));
                        graph.addClass(className);
                        for (String dep : deps) {
                            graph.addEdge(className, dep);
                        }
                    } catch (IOException e) {
                        // Skip problematic files
                    }
                });
        }
        
        return graph;
    }
    
    private DependencyGraph parseOutput(String output) {
        var graph = new DependencyGraph();
        
        for (String line : output.split("\n")) {
            Matcher m = DEP_PATTERN.matcher(line);
            if (m.matches()) {
                String from = m.group(1).replace('.', '/');
                String to = m.group(2).replace('.', '/');
                
                // Skip JDK internal dependencies
                if (!to.startsWith("java/") && !to.startsWith("jdk/") && !to.startsWith("sun/")) {
                    graph.addClass(from);
                    graph.addClass(to);
                    graph.addEdge(from, to);
                }
            }
        }
        
        return graph;
    }
    
    /**
     * Extract dependencies from class file constant pool.
     * Simple implementation - extracts class references.
     */
    private Set<String> extractDependencies(byte[] classData) {
        Set<String> deps = new HashSet<>();
        
        if (classData.length < 10) return deps;
        
        // Skip magic and version
        int pos = 8;
        
        // Constant pool count
        int cpCount = ((classData[pos] & 0xFF) << 8) | (classData[pos + 1] & 0xFF);
        pos += 2;
        
        // Parse constant pool looking for class references
        List<Integer> classRefs = new ArrayList<>();
        List<String> utf8s = new ArrayList<>();
        utf8s.add(null); // Index 0 unused
        
        for (int i = 1; i < cpCount; i++) {
            if (pos >= classData.length) break;
            
            int tag = classData[pos++] & 0xFF;
            switch (tag) {
                case 1: // UTF8
                    int len = ((classData[pos] & 0xFF) << 8) | (classData[pos + 1] & 0xFF);
                    pos += 2;
                    if (pos + len <= classData.length) {
                        utf8s.add(new String(classData, pos, len, java.nio.charset.StandardCharsets.UTF_8));
                    } else {
                        utf8s.add(null);
                    }
                    pos += len;
                    break;
                case 7: // Class
                    int nameIdx = ((classData[pos] & 0xFF) << 8) | (classData[pos + 1] & 0xFF);
                    classRefs.add(nameIdx);
                    pos += 2;
                    utf8s.add(null);
                    break;
                case 3: case 4: // Integer, Float
                    pos += 4;
                    utf8s.add(null);
                    break;
                case 5: case 6: // Long, Double
                    pos += 8;
                    utf8s.add(null);
                    i++; // Takes two slots
                    break;
                case 8: // String
                    pos += 2;
                    utf8s.add(null);
                    break;
                case 9: case 10: case 11: case 12: // Field, Method, Interface, NameAndType
                    pos += 4;
                    utf8s.add(null);
                    break;
                case 15: // MethodHandle
                    pos += 3;
                    utf8s.add(null);
                    break;
                case 16: // MethodType
                    pos += 2;
                    utf8s.add(null);
                    break;
                case 17: case 18: // Dynamic, InvokeDynamic
                    pos += 4;
                    utf8s.add(null);
                    break;
                case 19: case 20: // Module, Package
                    pos += 2;
                    utf8s.add(null);
                    break;
                default:
                    utf8s.add(null);
                    break;
            }
        }
        
        // Resolve class references
        for (int idx : classRefs) {
            if (idx > 0 && idx < utf8s.size()) {
                String name = utf8s.get(idx);
                if (name != null && !name.startsWith("[") && !name.startsWith("java/") 
                        && !name.startsWith("jdk/") && !name.startsWith("sun/")) {
                    deps.add(name);
                }
            }
        }
        
        return deps;
    }
    
    private String pathToClassName(Path base, Path classFile) {
        Path relative = base.relativize(classFile);
        String name = relative.toString();
        return name.substring(0, name.length() - 6).replace(File.separatorChar, '/');
    }
    
    /**
     * Check if a JAR file is modular (contains module-info.class).
     */
    private boolean isModularJar(Path jarPath) throws IOException {
        if (!Files.exists(jarPath) || !jarPath.toString().endsWith(".jar")) {
            return false;
        }
        
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Check for module-info.class in root
            if (jar.getEntry("module-info.class") != null) {
                return true;
            }
            
            // Check for module-info.class in versioned entries (Multi-Release JARs)
            return jar.stream().anyMatch(entry -> 
                entry.getName().matches("META-INF/versions/\\d+/module-info\\.class"));
        }
    }
    
    /**
     * Parse classpath string into individual JAR paths.
     */
    private List<Path> parseClasspath(String classpath) {
        return Arrays.stream(classpath.split(File.pathSeparator))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Paths::get)
            .filter(Files::exists)
            .collect(Collectors.toList());
    }
}
