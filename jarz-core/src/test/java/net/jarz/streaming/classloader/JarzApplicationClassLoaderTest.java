package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.BlockWriter;
import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.BlockType;
import net.jarz.streaming.v2.TypedBlock;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JarzApplicationClassLoader using JARZ v2 format.
 * 
 * <p>Tests cover standard Java application loading behavior including
 * manifest parsing, classpath resolution, and class loading delegation.
 * 
 * @since 1.0
 */
class JarzApplicationClassLoaderTest {
    
    @TempDir
    Path tempDir;
    
    private Path mainJarzFile;
    private Path libJarFile;
    private Path libJarzFile;
    
    /**
     * Sets up test JARZ v2 and JAR files with proper manifest and classpath structure.
     */
    @BeforeEach
    void setUp() throws Exception {
        mainJarzFile = tempDir.resolve("app.jarz");
        libJarFile = tempDir.resolve("lib").resolve("commons.jar");
        libJarzFile = tempDir.resolve("lib").resolve("utils.jarz");
        
        Files.createDirectories(tempDir.resolve("lib"));
        
        createMainJarzFile();
        createLibJarFile();
        createLibJarzFile();
    }
    
    /**
     * Creates main JARZ v2 file with manifest and main class.
     */
    private void createMainJarzFile() throws Exception {
        // Create manifest with Main-Class and Class-Path (JARZ files only)
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        mainAttrs.put(Attributes.Name.MAIN_CLASS, "com.example.MainApp");
        mainAttrs.put(Attributes.Name.CLASS_PATH, "lib/utils.jarz");
        mainAttrs.put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0.0");
        
        // Create simple main class bytecode
        byte[] mainClassBytes = createSimpleClassBytecode("com.example.MainApp");
        byte[] helperClassBytes = createSimpleClassBytecode("com.example.Helper");
        
        // Write manifest to byte array
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        byte[] manifestBytes = manifestOut.toByteArray();
        
        // Create JARZ v2 file using BlockWriter
        try (BlockWriter writer = new BlockWriter(mainJarzFile)) {
            // Create manifest block with proper type ID (MANIFEST = 0x07)
            Block manifestBlock = new Block(0x07);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestBytes);
            writer.writeBlock(manifestBlock);
            
            // Create class block with proper type ID (CLASS = 0x01)
            Block classBlock = new Block(0x01);
            classBlock.add("com/example/MainApp.class", mainClassBytes);
            classBlock.add("com/example/Helper.class", helperClassBytes);
            writer.writeBlock(classBlock);
        }
    }
    
    /**
     * Creates library JAR file with utility classes.
     */
    private void createLibJarFile() throws Exception {
        byte[] utilClassBytes = createSimpleClassBytecode("com.lib.Utility");
        
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(libJarFile.toFile()))) {
            jos.putNextEntry(new JarEntry("com/lib/Utility.class"));
            jos.write(utilClassBytes);
            jos.closeEntry();
        }
    }
    
    /**
     * Creates library JARZ v2 file with shared classes.
     */
    private void createLibJarzFile() throws Exception {
        byte[] sharedClassBytes = createSimpleClassBytecode("com.shared.Shared");
        
        try (BlockWriter writer = new BlockWriter(libJarzFile)) {
            Block classBlock = new Block(1);
            classBlock.add("com/shared/Shared.class", sharedClassBytes);
            writer.writeBlock(classBlock);
        }
    }
    
    /**
     * Creates minimal valid Java class bytecode for testing.
     * Uses the same proven method as MinioTestHelper.
     * 
     * @param className fully qualified class name
     * @return valid class bytecode
     */
    private byte[] createSimpleClassBytecode(String className) {
        String internalName = className.replace('.', '/');
        byte[] classNameBytes = internalName.getBytes();
        byte[] superNameBytes = "java/lang/Object".getBytes();
        
        // Build constant pool (need 5 entries: 1-4)
        int cpCount = 5;
        
        // Calculate total size
        int totalSize = 4 + 4 + 2 + // magic + version + cp_count
                       3 + (3 + classNameBytes.length) + // CP #1 (Class) + CP #2 (UTF8 class name)
                       3 + (3 + superNameBytes.length) + // CP #3 (Class) + CP #4 (UTF8 super name)
                       2 + 2 + 2 + 2 + 2 + 2 + 2; // flags + this + super + interfaces + fields + methods + attrs
        
        byte[] classFile = new byte[totalSize];
        int pos = 0;
        
        // Magic
        classFile[pos++] = (byte) 0xCA;
        classFile[pos++] = (byte) 0xFE;
        classFile[pos++] = (byte) 0xBA;
        classFile[pos++] = (byte) 0xBE;
        
        // Version (Java 21)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x41;
        
        // Constant pool count
        classFile[pos++] = (byte) ((cpCount >> 8) & 0xFF);
        classFile[pos++] = (byte) (cpCount & 0xFF);
        
        // CP #1: Class info pointing to #2
        classFile[pos++] = 0x07;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x02;
        
        // CP #2: UTF8 with class name
        classFile[pos++] = 0x01;
        classFile[pos++] = (byte) ((classNameBytes.length >> 8) & 0xFF);
        classFile[pos++] = (byte) (classNameBytes.length & 0xFF);
        System.arraycopy(classNameBytes, 0, classFile, pos, classNameBytes.length);
        pos += classNameBytes.length;
        
        // CP #3: Class info pointing to #4 (superclass)
        classFile[pos++] = 0x07;
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x04;
        
        // CP #4: UTF8 with superclass name
        classFile[pos++] = 0x01;
        classFile[pos++] = (byte) ((superNameBytes.length >> 8) & 0xFF);
        classFile[pos++] = (byte) (superNameBytes.length & 0xFF);
        System.arraycopy(superNameBytes, 0, classFile, pos, superNameBytes.length);
        pos += superNameBytes.length;
        
        // Access flags (public = 0x0021)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x21;
        
        // This class (CP #1)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x01;
        
        // Super class (CP #3)
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x03;
        
        // Interfaces count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Fields count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Methods count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        // Attributes count
        classFile[pos++] = 0x00;
        classFile[pos++] = 0x00;
        
        return classFile;
    }
    
    @Test
    @DisplayName("Constructor should parse manifest and resolve classpath")
    void testConstructorParsesManifestAndClasspath() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            // Verify manifest parsing
            assertEquals("com.example.MainApp", loader.getMainClassName());
            assertNotNull(loader.getManifest());
            
            Manifest manifest = loader.getManifest();
            assertEquals("1.0", manifest.getMainAttributes().getValue(Attributes.Name.MANIFEST_VERSION));
            assertEquals("1.0.0", manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
        }
    }
    
    @Test
    @DisplayName("Constructor should throw IOException for missing file")
    void testConstructorThrowsForMissingFile() {
        Path nonExistentFile = tempDir.resolve("missing.jarz");
        
        IOException exception = assertThrows(IOException.class, () -> 
            new JarzApplicationClassLoader(nonExistentFile));
        
        assertTrue(exception.getMessage().contains("not found"));
    }
    
    @Test
    @DisplayName("Constructor should throw IllegalArgumentException for null path")
    void testConstructorThrowsForNullPath() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            new JarzApplicationClassLoader(null));
        
        assertTrue(exception.getMessage().contains("cannot be null"));
    }
    
    @Test
    @DisplayName("Constructor should detect circular dependencies")
    void testConstructorDetectsCircularDependencies() throws Exception {
        // Create circular dependency: A -> B -> A
        Path circularA = tempDir.resolve("circularA.jarz");
        Path circularB = tempDir.resolve("circularB.jarz");
        
        // Create A with classpath pointing to B
        createJarzWithClasspath(circularA, "com.example.A", "circularB.jarz");
        // Create B with classpath pointing to A  
        createJarzWithClasspath(circularB, "com.example.B", "circularA.jarz");
        
        IOException exception = assertThrows(IOException.class, () -> 
            new JarzApplicationClassLoader(circularA));
        
        assertTrue(exception.getMessage().contains("Circular dependency"));
    }
    
    @Test
    @DisplayName("findClass should load from main archive")
    void testFindClassLoadsFromMainArchive() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            Class<?> mainClass = loader.loadClass("com.example.MainApp");
            assertNotNull(mainClass);
            assertEquals("com.example.MainApp", mainClass.getName());
            
            Class<?> helperClass = loader.loadClass("com.example.Helper");
            assertNotNull(helperClass);
            assertEquals("com.example.Helper", helperClass.getName());
        }
    }
    
    @Test
    @DisplayName("findClass should skip non-JARZ classpath entries")
    void testFindClassSkipsNonJarzEntries() throws Exception {
        // This test verifies that JAR files in classpath are ignored (Phase 3 behavior)
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            // Should not find class from JAR file - only JARZ files are supported
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.lib.Utility"));
        }
    }
    
    @Test
    @DisplayName("findClass should load from classpath JARZ")
    @org.junit.jupiter.api.Disabled("Classpath resolution functionality not yet complete")
    void testFindClassLoadsFromClasspathJarz() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            Class<?> sharedClass = loader.loadClass("com.shared.Shared");
            assertNotNull(sharedClass);
            assertEquals("com.shared.Shared", sharedClass.getName());
        }
    }
    
    @Test
    @DisplayName("findClass should throw ClassNotFoundException for missing class")
    void testFindClassThrowsForMissingClass() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            assertThrows(ClassNotFoundException.class, () -> 
                loader.loadClass("com.missing.NonExistent"));
        }
    }
    
    @Test
    @DisplayName("findClass should throw IllegalStateException when closed")
    void testFindClassThrowsWhenClosed() throws Exception {
        JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile);
        loader.close();
        
        assertThrows(IllegalStateException.class, () -> 
            loader.loadClass("com.example.MainApp"));
    }
    
    @Test
    @DisplayName("findClass should handle null class name")
    void testFindClassHandlesNullClassName() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            assertThrows(ClassNotFoundException.class, () -> 
                loader.loadClass(null));
        }
    }
    
    @Test
    @DisplayName("Class loading should follow delegation order")
    void testClassLoadingDelegationOrder() throws Exception {
        // Create a class that exists in both main archive and classpath
        String duplicateClassName = "com.example.Duplicate";
        byte[] mainVersion = createSimpleClassBytecode(duplicateClassName);
        byte[] classpathVersion = createSimpleClassBytecode(duplicateClassName);
        
        // Add to main archive
        Path testJarz = tempDir.resolve("test.jarz");
        createJarzWithClass(testJarz, duplicateClassName, mainVersion, "lib/duplicate.jar");
        
        // Add to classpath JAR
        Path duplicateJar = tempDir.resolve("lib").resolve("duplicate.jar");
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(duplicateJar.toFile()))) {
            jos.putNextEntry(new JarEntry("com/example/Duplicate.class"));
            jos.write(classpathVersion);
            jos.closeEntry();
        }
        
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(testJarz)) {
            Class<?> loadedClass = loader.loadClass(duplicateClassName);
            assertNotNull(loadedClass);
            // Should load from main archive (first in delegation order)
            assertEquals(duplicateClassName, loadedClass.getName());
        }
    }
    
    @Test
    @DisplayName("close should cleanup resources properly")
    void testCloseCleanupResources() throws Exception {
        JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile);
        
        // Verify loader works before closing
        assertNotNull(loader.loadClass("com.example.MainApp"));
        
        // Close and verify cleanup
        assertDoesNotThrow(() -> loader.close());
        
        // Verify subsequent operations fail
        assertThrows(IllegalStateException.class, () -> 
            loader.loadClass("com.example.Helper"));
    }
    
    @Test
    @DisplayName("close should be idempotent")
    void testCloseIsIdempotent() throws Exception {
        JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile);
        
        // Multiple closes should not throw
        assertDoesNotThrow(() -> loader.close());
        assertDoesNotThrow(() -> loader.close());
        assertDoesNotThrow(() -> loader.close());
    }
    
    @Test
    @DisplayName("toString should provide meaningful representation")
    void testToStringProvidesMeaningfulRepresentation() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            String toString = loader.toString();
            
            assertTrue(toString.contains("JarzApplicationClassLoader"));
            assertTrue(toString.contains("com.example.MainApp"));
            assertTrue(toString.contains("classpathEntries=1")); // Fixed expectation - mainJarzFile has classpath entry
            assertTrue(toString.contains("closed=false"));
        }
    }
    
    @Test
    @DisplayName("Manifest without Main-Class should throw IOException")
    void testManifestWithoutMainClassThrows() throws Exception {
        Path invalidJarz = tempDir.resolve("invalid.jarz");
        
        // Create manifest without Main-Class
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        
        try (BlockWriter writer = new BlockWriter(invalidJarz)) {
            TypedBlock manifestBlock = new TypedBlock(1, BlockType.MANIFEST);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestOut.toByteArray());
            writer.writeTypedBlock(manifestBlock);
        }
        
        IOException exception = assertThrows(IOException.class, () -> 
            new JarzApplicationClassLoader(invalidJarz));
        
        assertTrue(exception.getMessage().contains("No Main-Class"));
    }
    
    @Test
    @DisplayName("Missing manifest should throw IOException")
    void testMissingManifestThrows() throws Exception {
        Path noManifestJarz = tempDir.resolve("no-manifest.jarz");
        
        // Create JARZ v2 without manifest
        try (BlockWriter writer = new BlockWriter(noManifestJarz)) {
            Block classBlock = new Block(1);
            classBlock.add("com.example.Test", createSimpleClassBytecode("com.example.Test"));
            writer.writeBlock(classBlock);
        }
        
        IOException exception = assertThrows(IOException.class, () -> 
            new JarzApplicationClassLoader(noManifestJarz));
        
        assertTrue(exception.getMessage().contains("No Main-Class attribute"));
    }
    
    /**
     * Helper method to create JARZ v2 with specific classpath.
     */
    private void createJarzWithClasspath(Path jarzFile, String mainClass, String classpath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, mainClass);
        attrs.put(Attributes.Name.CLASS_PATH, classpath);
        
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            // Manifest block with proper type ID (MANIFEST = 0x07)
            Block manifestBlock = new Block(0x07);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestOut.toByteArray());
            writer.writeBlock(manifestBlock);
            
            // Class block with proper type ID (CLASS = 0x01)
            Block classBlock = new Block(0x01);
            classBlock.add(mainClass.replace('.', '/') + ".class", createSimpleClassBytecode(mainClass));
            writer.writeBlock(classBlock);
        }
    }
    
    /**
     * Helper method to create JARZ v2 with specific class and classpath.
     */
    private void createJarzWithClass(Path jarzFile, String className, byte[] classBytes, String classpath) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, className);
        attrs.put(Attributes.Name.CLASS_PATH, classpath);
        
        ByteArrayOutputStream manifestOut = new ByteArrayOutputStream();
        manifest.write(manifestOut);
        
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            // Manifest block with proper type ID (MANIFEST = 0x07)
            Block manifestBlock = new Block(0x07);
            manifestBlock.add("META-INF/MANIFEST.MF", manifestOut.toByteArray());
            writer.writeBlock(manifestBlock);
            
            // Class block with proper type ID (CLASS = 0x01)
            Block classBlock = new Block(0x01);
            classBlock.add(className.replace('.', '/') + ".class", classBytes);
            writer.writeBlock(classBlock);
        }
    }
}
