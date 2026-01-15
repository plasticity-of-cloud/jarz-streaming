package net.jarz.streaming.v2;

import net.jarz.streaming.classloader.JarzApplicationClassLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ManifestProcessor Class-Path updating functionality.
 */
class ManifestProcessorTest {
    
    @Test
    void testProcessManifestWithClassPath() throws Exception {
        // Create manifest with Class-Path containing .jar files
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.example.Main");
        attrs.putValue("Class-Path", "lib/commons-lang3-3.12.0.jar lib/jackson-core-2.13.0.jar");
        
        // Convert to bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        byte[] originalData = out.toByteArray();
        
        // Process the manifest
        byte[] processedData = ManifestProcessor.processManifest(originalData);
        
        // Verify the Class-Path was updated
        Manifest processedManifest = new Manifest(new ByteArrayInputStream(processedData));
        String updatedClassPath = processedManifest.getMainAttributes().getValue("Class-Path");
        
        assertEquals("lib/commons-lang3-3.12.0.jarz lib/jackson-core-2.13.0.jarz", updatedClassPath);
        
        // Verify other attributes remain unchanged
        assertEquals("1.0", processedManifest.getMainAttributes().getValue("Manifest-Version"));
        assertEquals("com.example.Main", processedManifest.getMainAttributes().getValue("Main-Class"));
    }
    
    @Test
    void testProcessManifestWithoutClassPath() throws Exception {
        // Create manifest without Class-Path
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.example.Main");
        
        // Convert to bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        byte[] originalData = out.toByteArray();
        
        // Process the manifest
        byte[] processedData = ManifestProcessor.processManifest(originalData);
        
        // Should return the same data since no Class-Path to update
        assertArrayEquals(originalData, processedData);
    }
    
    @Test
    void testProcessManifestWithMixedClassPath() throws Exception {
        // Create manifest with mixed .jar and non-.jar entries
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Class-Path", "lib/commons-lang3-3.12.0.jar config/ lib/jackson-core-2.13.0.jar");
        
        // Convert to bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        byte[] originalData = out.toByteArray();
        
        // Process the manifest
        byte[] processedData = ManifestProcessor.processManifest(originalData);
        
        // Verify only .jar files were updated
        Manifest processedManifest = new Manifest(new ByteArrayInputStream(processedData));
        String updatedClassPath = processedManifest.getMainAttributes().getValue("Class-Path");
        
        assertEquals("lib/commons-lang3-3.12.0.jarz config/ lib/jackson-core-2.13.0.jarz", updatedClassPath);
    }
    
    @Test
    void testIsManifestFile() {
        assertTrue(ManifestProcessor.isManifestFile("META-INF/MANIFEST.MF"));
        assertFalse(ManifestProcessor.isManifestFile("META-INF/services/com.example.Service"));
        assertFalse(ManifestProcessor.isManifestFile("com/example/Main.class"));
        assertFalse(ManifestProcessor.isManifestFile("config.properties"));
    }
    
    @Test
    void testProcessManifestWithModuleAttributes() throws Exception {
        // Create manifest with Java 9+ module system attributes containing .jar files
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.example.Main");
        attrs.putValue("Class-Path", "lib/commons-lang3-3.12.0.jar lib/jackson-core-2.13.0.jar");
        attrs.putValue("Add-Exports", "java.base/sun.nio.ch=lib/netty-transport-4.1.jar");
        attrs.putValue("Add-Opens", "java.base/java.lang=lib/reflection-utils-1.0.jar");
        attrs.putValue("Add-Reads", "my.module=lib/dependency-1.2.jar");
        
        // Convert to bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        byte[] originalData = out.toByteArray();
        
        // Process the manifest
        byte[] processedData = ManifestProcessor.processManifest(originalData);
        
        // Verify all attributes were updated
        Manifest processedManifest = new Manifest(new ByteArrayInputStream(processedData));
        Attributes processedAttrs = processedManifest.getMainAttributes();
        
        assertEquals("lib/commons-lang3-3.12.0.jarz lib/jackson-core-2.13.0.jarz", 
                     processedAttrs.getValue("Class-Path"));
        assertEquals("java.base/sun.nio.ch=lib/netty-transport-4.1.jarz", 
                     processedAttrs.getValue("Add-Exports"));
        assertEquals("java.base/java.lang=lib/reflection-utils-1.0.jarz", 
                     processedAttrs.getValue("Add-Opens"));
        assertEquals("my.module=lib/dependency-1.2.jarz", 
                     processedAttrs.getValue("Add-Reads"));
        
        // Verify other attributes remain unchanged
        assertEquals("1.0", processedAttrs.getValue("Manifest-Version"));
        assertEquals("com.example.Main", processedAttrs.getValue("Main-Class"));
    }
    
    @Test
    void testProcessManifestWithComplexModuleExpressions() throws Exception {
        // Create manifest with complex module expressions
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue("Add-Exports", "java.base/sun.nio.ch=lib/netty-4.1.jar,lib/async-http-1.0.jar");
        attrs.putValue("Add-Opens", "java.base/java.lang=ALL-UNNAMED lib/reflection-utils-1.0.jar");
        
        // Convert to bytes
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        manifest.write(out);
        byte[] originalData = out.toByteArray();
        
        // Process the manifest
        byte[] processedData = ManifestProcessor.processManifest(originalData);
        
        // Verify complex expressions were updated correctly
        Manifest processedManifest = new Manifest(new ByteArrayInputStream(processedData));
        Attributes processedAttrs = processedManifest.getMainAttributes();
        
        assertEquals("java.base/sun.nio.ch=lib/netty-4.1.jarz,lib/async-http-1.0.jarz", 
                     processedAttrs.getValue("Add-Exports"));
        assertEquals("java.base/java.lang=ALL-UNNAMED lib/reflection-utils-1.0.jarz", 
                     processedAttrs.getValue("Add-Opens"));
    }
    
    @Test
    void testJarToJarzConversionWithManifest(@TempDir Path tempDir) throws Exception {
        Path jarFile = tempDir.resolve("test.jar");
        
        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MAIN_CLASS, "com.example.Main");
        attrs.putValue("Class-Path", "lib/dependency1.jar lib/dependency2.jar");
        
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            // Add a dummy class file
            JarEntry classEntry = new JarEntry("com/example/Main.class");
            jos.putNextEntry(classEntry);
            jos.write("dummy class content".getBytes());
            jos.closeEntry();
        }
        
        // Convert JAR to JARZ
        Path jarzFile = tempDir.resolve("test.jarz");
        JarToJarzConverter.ConversionResult result = JarToJarzConverter.convert(jarFile, jarzFile);
        
        assertNotNull(result);
        assertTrue(Files.exists(jarzFile));
        
        // Verify the converted JARZ has updated manifest
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(jarzFile)) {
            Manifest convertedManifest = loader.getManifest();
            String classPath = convertedManifest.getMainAttributes().getValue("Class-Path");
            
            assertEquals("lib/dependency1.jarz lib/dependency2.jarz", classPath);
            assertEquals("com.example.Main", convertedManifest.getMainAttributes().getValue("Main-Class"));
        }
    }
}
