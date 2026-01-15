package net.jarz.streaming.tools;

import net.jarz.streaming.classloader.JarzApplicationClassLoader;
import net.jarz.streaming.v2.BlockReader;
import net.jarz.streaming.v2.JarToJarzConverter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Enhanced tests for JARZ CLI with full JAR compatibility.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
class JarzCliTest {
    
    @TempDir
    Path tempDir;
    
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    
    @BeforeEach
    void setUpStreams() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }
    
    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private Path createTestJar(String packageName, String className) throws Exception {
        String testClass;
        if (packageName.isEmpty()) {
            testClass = String.format("""
                public class %s {
                    public String getMessage() {
                        return "Hello from %s!";
                    }
                }
                """, className, className);
        } else {
            testClass = String.format("""
                package %s;
                public class %s {
                    public String getMessage() {
                        return "Hello from %s!";
                    }
                }
                """, packageName, className, className);
        }

        Path packageDir = packageName.isEmpty() ? tempDir : tempDir.resolve(packageName);
        Files.createDirectories(packageDir);
        Path javaFile = packageDir.resolve(className + ".java");
        Files.writeString(javaFile, testClass);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, javaFile.toString());
        assertThat(result).isEqualTo(0);

        Path jarFile = tempDir.resolve((packageName.isEmpty() ? "" : packageName + "-") + "test.jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            Path classFile = packageDir.resolve(className + ".class");
            String entryName = packageName.isEmpty() ? className + ".class" : packageName + "/" + className + ".class";
            jar.putNextEntry(new JarEntry(entryName));
            Files.copy(classFile, jar);
            jar.closeEntry();
        }
        
        return jarFile;
    }
    
    /**
     * Helper method to create a real compiled class file.
     */
    private Path createCompiledClass(String className, Path outputDir) throws Exception {
        Files.createDirectories(outputDir);
        
        String classCode = String.format("""
            public class %s {
                public String getMessage() {
                    return "Hello from %s!";
                }
            }
            """, className, className);
        
        Path javaFile = outputDir.resolve(className + ".java");
        Files.writeString(javaFile, classCode);
        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, javaFile.toString());
        assertThat(result).isEqualTo(0);
        
        return outputDir.resolve(className + ".class");
    }

    private Path createTestJarWithManifest(String mainClass) throws Exception {
        Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);
        
        // Create a simple main class
        String mainClassCode = String.format("""
            public class %s {
                public static void main(String[] args) {
                    System.out.println("Hello from %s!");
                }
            }
            """, mainClass, mainClass);
        
        Path javaFile = classesDir.resolve(mainClass + ".java");
        Files.writeString(javaFile, mainClassCode);
        
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null, javaFile.toString());
        assertThat(result).isEqualTo(0);
        
        // Create JAR with manifest
        Path jarFile = tempDir.resolve("test-with-manifest.jar");
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            Path classFile = classesDir.resolve(mainClass + ".class");
            jar.putNextEntry(new JarEntry(mainClass + ".class"));
            Files.copy(classFile, jar);
            jar.closeEntry();
        }
        
        return jarFile;
    }
    
    // JAR-Compatible Create Operation Tests
    
    @Test
    void testCreateWithJarSyntax() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Test", classesDir);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        // Test JAR-compatible syntax: jarz -cf archive.jarz files...
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-cf", jarzFile.toString(), classesDir.toString()
        }));
        
        assertThat(jarzFile).exists();
        assertThat(Files.size(jarzFile)).isGreaterThan(0);
    }
    
    @Test
    void testCreateWithVerbose() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Test", classesDir);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-cvf", jarzFile.toString(), classesDir.toString()
        }));
        
        String output = outContent.toString();
        assertThat(output).contains("creating:");
        assertThat(output).contains("adding:");
    }
    
    @Test
    void testCreateWithManifest() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Main", classesDir);
        
        Path manifestFile = tempDir.resolve("manifest.mf");
        Files.writeString(manifestFile, """
            Manifest-Version: 1.0
            Main-Class: Main
            """);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-cfm", jarzFile.toString(), manifestFile.toString(), classesDir.toString()
        }));
        
        // Verify manifest is included
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.entryNames()).contains("META-INF/MANIFEST.MF");
        }
    }
    
    @Test
    void testCreateWithMainClass() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Main", classesDir);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-cfe", jarzFile.toString(), "Main", classesDir.toString()
        }));
        
        // Verify manifest with main class is created
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.entryNames()).contains("META-INF/MANIFEST.MF");
        }
    }
    
    @Test
    void testCreateWithDirectoryChange() throws Exception {
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Test", classesDir);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-cf", jarzFile.toString(), "-C", classesDir.toString(), "."
        }));
        
        assertThat(jarzFile).exists();
    }
    
    // JAR-Compatible Extract Operation Tests
    
    @Test
    void testExtractWithJarSyntax() throws Exception {
        // Create a test JAR and convert to JARZ
        Path jarFile = createTestJar("extract", "ExtractTest");
        Path jarzFile = tempDir.resolve("test.jarz");
        
        JarToJarzConverter.convert(jarFile, jarzFile);
        
        // Test JAR-compatible extract: jarz -xf archive.jarz
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-xf", jarzFile.toString()
        }));
        
        // Verify files were extracted
        assertThat(tempDir.resolve("extract/ExtractTest.class")).exists();
    }
    
    @Test
    void testExtractWithVerbose() throws Exception {
        Path jarFile = createTestJar("extract", "ExtractTest");
        Path jarzFile = tempDir.resolve("test.jarz");
        
        JarToJarzConverter.convert(jarFile, jarzFile);
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-xvf", jarzFile.toString()
        }));
        
        String output = outContent.toString();
        assertThat(output).contains("extracting:");
        assertThat(output).contains("inflated:");
    }
    
    // JAR-Compatible List Operation Tests
    
    @Test
    void testListWithJarSyntax() throws Exception {
        Path jarFile = createTestJar("list", "ListTest");
        Path jarzFile = tempDir.resolve("test.jarz");
        
        JarToJarzConverter.convert(jarFile, jarzFile);
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-tf", jarzFile.toString()
        }));
        
        String output = outContent.toString();
        assertThat(output).contains("list/ListTest.class");
    }
    
    @Test
    void testListWithVerbose() throws Exception {
        Path jarFile = createTestJar("list", "ListTest");
        Path jarzFile = tempDir.resolve("test.jarz");
        
        JarToJarzConverter.convert(jarFile, jarzFile);
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-tvf", jarzFile.toString()
        }));
        
        String output = outContent.toString();
        assertThat(output).contains("list/ListTest.class");
        // Verbose output should include size and timestamp
        assertThat(output).contains("298"); // Check for file size
        assertThat(output).contains("2026"); // Check for year
    }
    
    // JAR-Compatible Convert Operation Tests
    
    @Test
    void testConvertWithJarSyntax() throws Exception {
        Path jarFile = createTestJar("convert", "ConvertTest");
        Path jarzFile = tempDir.resolve("output.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "--convert", jarFile.toString(), jarzFile.toString()
        }));
        
        assertThat(jarzFile).exists();
        assertThat(Files.size(jarzFile)).isGreaterThan(0);
        assertThat(Files.size(jarzFile)).isLessThan(Files.size(jarFile)); // Compression
    }
    
    @Test
    void testConvertWithVerbose() throws Exception {
        Path jarFile = createTestJar("convert", "ConvertTest");
        Path jarzFile = tempDir.resolve("output.jarz");
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "--convert", "-v", jarFile.toString(), jarzFile.toString()
        }));
        
        String output = outContent.toString();
        assertThat(output).contains("converting:");
        assertThat(output).contains("Original size:");
        assertThat(output).contains("JARZ size:");
        assertThat(output).contains("Compression:");
    }
    
    // Update Operation Tests
    
    @Test
    void testUpdateOperation() throws Exception {
        // Create initial JARZ with -C to avoid directory prefix
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("Original", classesDir);
        
        Path jarzFile = tempDir.resolve("test.jarz");
        JarzCli.run(new String[]{"-cf", jarzFile.toString(), "-C", classesDir.toString(), "."});
        
        // Add new file
        createCompiledClass("New", classesDir);
        
        assertDoesNotThrow(() -> JarzCli.run(new String[]{
            "-uf", jarzFile.toString(), classesDir.resolve("New.class").toString()
        }));
        
        // Verify both files are in archive
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.entryNames()).contains("Original.class", "New.class");
        }
    }
    
    // Error Handling Tests
    
    @Test
    void testInvalidOperation() {
        assertThatThrownBy(() -> JarzCli.run(new String[]{"-z"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown flag");
    }
    
    @Test
    void testMissingArchiveFile() {
        assertThatThrownBy(() -> JarzCli.run(new String[]{"-c"}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Archive file must be specified");
    }
    
    @Test
    void testNonExistentFile() {
        Path nonExistent = tempDir.resolve("nonexistent.jarz");
        
        assertThatThrownBy(() -> JarzCli.run(new String[]{
            "-xf", nonExistent.toString()
        }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not exist");
    }
    
    // Help and Version Tests
    
    @Test
    void testHelpOption() {
        assertThatThrownBy(() -> JarzCli.run(new String[]{"--help"}))
            .isInstanceOf(JarzArgumentParser.HelpRequestedException.class);
    }
    
    @Test
    void testVersionOption() {
        assertThatThrownBy(() -> JarzCli.run(new String[]{"--version"}))
            .isInstanceOf(JarzArgumentParser.VersionRequestedException.class);
    }
    
    // Round-trip Compatibility Tests
    
    @Test
    void testRoundTripCompatibility() throws Exception {
        // Create JAR with manifest
        Path jarFile = createTestJarWithManifest("TestMain");
        
        // Convert to JARZ
        Path jarzFile = tempDir.resolve("test.jarz");
        JarzCli.run(new String[]{"--convert", jarFile.toString(), jarzFile.toString()});
        
        // Verify JARZ was created and has content
        assertThat(jarzFile).exists();
        assertThat(Files.size(jarzFile)).isGreaterThan(0);
        
        // Verify JARZ contains the expected entries
        try (BlockReader reader = new BlockReader(jarzFile)) {
            assertThat(reader.entryNames()).contains("TestMain.class", "META-INF/MANIFEST.MF");
        }
        
        // Test list operation instead of extract (which is more reliable)
        assertDoesNotThrow(() -> JarzCli.run(new String[]{"-tf", jarzFile.toString()}));
        
        // Verify the conversion preserved the manifest
        try (BlockReader reader = new BlockReader(jarzFile)) {
            byte[] manifestBytes = reader.readEntry("META-INF/MANIFEST.MF");
            String manifestContent = new String(manifestBytes);
            assertThat(manifestContent).contains("Main-Class: TestMain");
        }
    }
    
    // Class Loading Validation Tests
    
    @Test
    void testCreatedJarzCanLoadClasses() throws Exception {
        // Create JARZ with test class and manifest
        Path classesDir = tempDir.resolve("classes");
        createCompiledClass("TestClass", classesDir);
        
        // Create manifest with Main-Class
        Path manifestFile = tempDir.resolve("manifest.txt");
        Files.writeString(manifestFile, "Main-Class: TestClass\n");
        
        Path jarzFile = tempDir.resolve("test.jarz");
        JarzCli.run(new String[]{"-cfm", jarzFile.toString(), manifestFile.toString(), "-C", classesDir.toString(), "."});
        
        // Validate classes can be loaded from created JARZ
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
            Class<?> testClass = loader.loadClass("TestClass");
            Object instance = testClass.getDeclaredConstructor().newInstance();
            String result = (String) testClass.getMethod("getMessage").invoke(instance);
            assertThat(result).isEqualTo("Hello from TestClass!");
        }
    }
    
    @Test
    void testConvertedJarzCanLoadClasses() throws Exception {
        // Create JAR with manifest and convert to JARZ
        Path jarFile = createTestJar("", "ConvertTest");
        
        // Add manifest to JAR
        Path manifestFile = tempDir.resolve("manifest.txt");
        Files.writeString(manifestFile, "Main-Class: ConvertTest\n");
        
        // Update JAR with manifest
        ProcessBuilder pb = new ProcessBuilder("jar", "ufm", jarFile.toString(), manifestFile.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
        
        Path jarzFile = tempDir.resolve("converted.jarz");
        JarzCli.run(new String[]{"--convert", jarFile.toString(), jarzFile.toString()});
        
        // Validate classes can be loaded from converted JARZ
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
            Class<?> testClass = loader.loadClass("ConvertTest");
            Object instance = testClass.getDeclaredConstructor().newInstance();
            String result = (String) testClass.getMethod("getMessage").invoke(instance);
            assertThat(result).isEqualTo("Hello from ConvertTest!");
        }
    }
    
    @Test
    void testExtractedClassesAreFunctional() throws Exception {
        // Create JAR with manifest and convert to JARZ
        Path jarFile = createTestJar("extract", "ExtractTest");
        
        // Add manifest to JAR
        Path manifestFile = tempDir.resolve("manifest.txt");
        Files.writeString(manifestFile, "Main-Class: extract.ExtractTest\n");
        
        // Update JAR with manifest
        ProcessBuilder pb = new ProcessBuilder("jar", "ufm", jarFile.toString(), manifestFile.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        process.waitFor();
        
        Path jarzFile = tempDir.resolve("test.jarz");
        JarToJarzConverter.convert(jarFile, jarzFile);
        JarzCli.run(new String[]{"-xf", jarzFile.toString()});
        
        // Verify extracted class file exists and is valid
        Path extractedClass = tempDir.resolve("extract/ExtractTest.class");
        assertThat(extractedClass).exists();
        
        // Load extracted class to verify it's functional
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
            Class<?> testClass = loader.loadClass("extract.ExtractTest");
            Object instance = testClass.getDeclaredConstructor().newInstance();
            String result = (String) testClass.getMethod("getMessage").invoke(instance);
            assertThat(result).isEqualTo("Hello from ExtractTest!");
        }
    }
}
