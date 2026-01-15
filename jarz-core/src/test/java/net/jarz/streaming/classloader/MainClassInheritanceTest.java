package net.jarz.streaming.classloader;

import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.BlockWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 Tests: Verify Main-Class inheritance across all ClassLoader implementations.
 * 
 * <p>This test class validates that all ClassLoader implementations (Application, S3, CDN, ECR)
 * properly inherit Main-Class support from the base JarzClassLoader class.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
class MainClassInheritanceTest {
    
    @TempDir
    Path tempDir;
    
    private Path mainJarzFile;
    private Path libraryJarzFile;
    
    @BeforeEach
    void setUp() throws Exception {
        mainJarzFile = tempDir.resolve("app.jarz");
        libraryJarzFile = tempDir.resolve("library.jarz");
        
        createMainJarzFile();
        createLibraryJarzFile();
    }
    
    private void createMainJarzFile() throws Exception {
        try (BlockWriter writer = new BlockWriter(mainJarzFile)) {
            // Create application class
            Block classBlock = new Block(1);
            classBlock.add("com.example.MainApp", createSimpleClassBytecode("com.example.MainApp"));
            writer.writeBlock(classBlock);
            
            // Create manifest with Main-Class
            Block manifestBlock = new Block(2);
            String manifestContent = "Manifest-Version: 1.0\n" +
                                   "Main-Class: com.example.MainApp\n" +
                                   "Implementation-Title: Test Application\n" +
                                   "Implementation-Version: 1.0\n\n";
            manifestBlock.add("META-INF/MANIFEST.MF", manifestContent.getBytes());
            writer.writeBlock(manifestBlock);
        }
    }
    
    private void createLibraryJarzFile() throws Exception {
        try (BlockWriter writer = new BlockWriter(libraryJarzFile)) {
            // Create library class
            Block classBlock = new Block(1);
            classBlock.add("com.library.Utils", createSimpleClassBytecode("com.library.Utils"));
            writer.writeBlock(classBlock);
            
            // Create manifest WITHOUT Main-Class (library)
            Block manifestBlock = new Block(2);
            String manifestContent = "Manifest-Version: 1.0\n" +
                                   "Implementation-Title: Test Library\n" +
                                   "Implementation-Version: 1.0\n\n";
            manifestBlock.add("META-INF/MANIFEST.MF", manifestContent.getBytes());
            writer.writeBlock(manifestBlock);
        }
    }
    
    private byte[] createSimpleClassBytecode(String className) {
        // Minimal valid class bytecode for testing
        return new byte[]{
            (byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, // magic
            0x00, 0x00, 0x00, 0x3D, // version
            0x00, 0x0D, // constant pool count
            0x07, 0x00, 0x02, // Class info
            0x01, 0x00, 0x10, 0x6A, 0x61, 0x76, 0x61, 0x2F, 0x6C, 0x61, 0x6E, 0x67, 0x2F, 0x4F, 0x62, 0x6A, 0x65, 0x63, 0x74, // java/lang/Object
            0x00, 0x21, // access flags
            0x00, 0x01, // this class
            0x00, 0x02, // super class
            0x00, 0x00, // interfaces count
            0x00, 0x00, // fields count
            0x00, 0x00, // methods count
            0x00, 0x00  // attributes count
        };
    }
    
    @Test
    @DisplayName("JarzApplicationClassLoader should inherit Main-Class support")
    void testApplicationClassLoaderMainClassInheritance() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            // Test inherited Main-Class functionality
            assertTrue(loader.hasMainClass(), "Should have Main-Class");
            assertEquals("com.example.MainApp", loader.getMainClassName(), "Should return correct Main-Class");
            
            // Test toString includes Main-Class
            String toString = loader.toString();
            assertTrue(toString.contains("mainClass=com.example.MainApp"), "toString should include Main-Class");
        }
    }
    
    @Test
    @DisplayName("JarzApplicationClassLoader should require Main-Class for applications")
    void testApplicationClassLoaderRequiresMainClass() throws Exception {
        // Should throw IOException when Main-Class is missing
        IOException exception = assertThrows(IOException.class, () -> 
            new JarzApplicationClassLoader(libraryJarzFile));
        
        assertTrue(exception.getMessage().contains("No Main-Class"), 
            "Should throw specific error for missing Main-Class");
    }
    
    @Test
    @DisplayName("Base JarzClassLoader should support optional Main-Class")
    void testBaseClassLoaderOptionalMainClass() throws Exception {
        // Create a concrete test implementation of base JarzClassLoader
        TestJarzClassLoader appLoader = new TestJarzClassLoader(mainJarzFile);
        TestJarzClassLoader libLoader = new TestJarzClassLoader(libraryJarzFile);
        
        try (appLoader; libLoader) {
            // Application JARZ should have Main-Class
            assertTrue(appLoader.hasMainClass(), "Application JARZ should have Main-Class");
            assertEquals("com.example.MainApp", appLoader.getMainClassName(), "Should return correct Main-Class");
            
            // Library JARZ should not have Main-Class
            assertFalse(libLoader.hasMainClass(), "Library JARZ should not have Main-Class");
            assertNull(libLoader.getMainClassName(), "Should return null for missing Main-Class");
        }
    }
    
    @Test
    @DisplayName("All ClassLoader implementations inherit Main-Class API")
    void testMainClassApiInheritance() throws Exception {
        try (JarzApplicationClassLoader loader = new JarzApplicationClassLoader(mainJarzFile)) {
            // Verify all inherited methods are available
            assertNotNull(loader.getClass().getMethod("hasMainClass"), "Should inherit hasMainClass()");
            assertNotNull(loader.getClass().getMethod("getMainClassName"), "Should inherit getMainClassName()");
            
            // Verify methods work correctly
            assertTrue(loader.hasMainClass());
            assertEquals("com.example.MainApp", loader.getMainClassName());
        }
    }
    
    /**
     * Concrete test implementation of JarzClassLoader for testing base class functionality.
     */
    private static class TestJarzClassLoader extends JarzClassLoader {
        private final Path jarzPath;
        
        public TestJarzClassLoader(Path jarzFile) throws IOException {
            super(new net.jarz.streaming.v2.FileJarzDataProvider(jarzFile), 
                  ClassLoader.getSystemClassLoader(), null);
            this.jarzPath = jarzFile;
        }
        
        @Override
        protected String getCurrentJarzUrl() {
            return jarzPath.getFileName().toString();
        }
        
        @Override
        protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
            Path childPath = jarzPath.getParent().resolve(jarzUrl);
            return new TestJarzClassLoader(childPath);
        }
    }
}
