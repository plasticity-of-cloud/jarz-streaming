package net.jarz.streaming.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for resource block support in JARZ v2.
 */
class ResourceBlockTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testEntryClassifier() {
        // Classes
        assertEquals(BlockType.CLASS, EntryClassifier.classify("com/example/Foo.class"));
        
        // Config
        assertEquals(BlockType.CONFIG, EntryClassifier.classify("application.properties"));
        assertEquals(BlockType.CONFIG, EntryClassifier.classify("config/settings.xml"));
        assertEquals(BlockType.CONFIG, EntryClassifier.classify("application.yml"));
        assertEquals(BlockType.CONFIG, EntryClassifier.classify("data.json"));
        
        // Service loaders
        assertEquals(BlockType.SERVICE, EntryClassifier.classify("META-INF/services/java.sql.Driver"));
        assertEquals(BlockType.SERVICE, EntryClassifier.classify("META-INF/spring.factories"));
        
        // Manifest
        assertEquals(BlockType.MANIFEST, EntryClassifier.classify("META-INF/MANIFEST.MF"));
        assertEquals(BlockType.MANIFEST, EntryClassifier.classify("META-INF/CERT.RSA"));
        
        // Native
        assertEquals(BlockType.NATIVE, EntryClassifier.classify("lib/native.so"));
        assertEquals(BlockType.NATIVE, EntryClassifier.classify("native.dll"));
        
        // Stored (pre-compressed)
        assertEquals(BlockType.STORED, EntryClassifier.classify("images/logo.png"));
        assertEquals(BlockType.STORED, EntryClassifier.classify("archive.zip"));
        assertEquals(BlockType.STORED, EntryClassifier.classify("fonts/font.woff2"));
        
        // Text
        assertEquals(BlockType.TEXT, EntryClassifier.classify("static/index.html"));
        assertEquals(BlockType.TEXT, EntryClassifier.classify("styles.css"));
        assertEquals(BlockType.TEXT, EntryClassifier.classify("app.js"));
    }
    
    @Test
    void testResourceBlockAssigner() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        
        // Config files
        entries.put("application.properties", "key=value".getBytes());
        entries.put("config.xml", "<config/>".getBytes());
        
        // Service loaders
        entries.put("META-INF/services/java.sql.Driver", "com.mysql.Driver".getBytes());
        
        // Manifest
        entries.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0".getBytes());
        
        // Text
        entries.put("static/index.html", "<html></html>".getBytes());
        entries.put("static/app.js", "console.log('hi')".getBytes());
        
        // Stored
        entries.put("images/logo.png", new byte[]{(byte)0x89, 'P', 'N', 'G'});
        
        ResourceBlockAssigner assigner = new ResourceBlockAssigner();
        List<TypedBlock> blocks = assigner.assign(entries, 0);
        
        // Should have blocks for each type present
        assertTrue(blocks.size() >= 4, "Should have at least 4 blocks for different types");
        
        // Verify block types
        Set<BlockType> types = new HashSet<>();
        for (TypedBlock b : blocks) {
            types.add(b.type());
        }
        
        assertTrue(types.contains(BlockType.CONFIG));
        assertTrue(types.contains(BlockType.SERVICE));
        assertTrue(types.contains(BlockType.MANIFEST));
        assertTrue(types.contains(BlockType.TEXT));
        assertTrue(types.contains(BlockType.STORED));
    }
    
    @Test
    void testTypedBlockWriteRead() throws Exception {
        Path jarzFile = tempDir.resolve("test.jarz");
        
        // Create test data
        Map<String, byte[]> testData = new LinkedHashMap<>();
        testData.put("config.properties", "db.url=jdbc:mysql://localhost".getBytes());
        testData.put("messages.properties", "greeting=Hello".getBytes());
        testData.put("META-INF/services/javax.sql.DataSource", "com.example.DS".getBytes());
        testData.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes());
        testData.put("static/index.html", "<html><body>Hello</body></html>".getBytes());
        testData.put("images/icon.png", new byte[]{(byte)0x89, 'P', 'N', 'G', 0, 0, 0, 0});
        
        // Assign to blocks
        ResourceBlockAssigner assigner = new ResourceBlockAssigner();
        List<TypedBlock> blocks = assigner.assign(testData, 0);
        
        // Write
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            for (TypedBlock block : blocks) {
                writer.writeTypedBlock(block);
            }
        }
        
        // Read and verify
        try (BlockReader reader = new BlockReader(jarzFile)) {
            for (var entry : testData.entrySet()) {
                byte[] read = reader.readEntry(entry.getKey());
                assertNotNull(read, "Entry not found: " + entry.getKey());
                assertArrayEquals(entry.getValue(), read, "Data mismatch for: " + entry.getKey());
            }
        }
    }
    
    @Test
    void testMixedClassAndResourceBlocks() throws Exception {
        Path jarzFile = tempDir.resolve("mixed.jarz");
        
        // Class data (fake class bytes)
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("com/example/Main.class", createFakeClass("Main"));
        classes.put("com/example/Util.class", createFakeClass("Util"));
        
        // Resource data
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("application.properties", "app.name=Test".getBytes());
        resources.put("META-INF/MANIFEST.MF", "Main-Class: com.example.Main\n".getBytes());
        
        // Create class blocks (simple assignment without dependency analysis)
        List<Block> classBlocks = new ArrayList<>();
        Block classBlock = new Block(0);
        for (var e : classes.entrySet()) {
            classBlock.add(e.getKey(), e.getValue());
        }
        classBlocks.add(classBlock);
        
        // Create resource blocks
        ResourceBlockAssigner resourceAssigner = new ResourceBlockAssigner();
        List<TypedBlock> resourceBlocks = resourceAssigner.assign(resources, classBlocks.size());
        
        // Write all blocks
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            for (Block b : classBlocks) {
                writer.writeBlock(b);
            }
            for (TypedBlock b : resourceBlocks) {
                writer.writeTypedBlock(b);
            }
        }
        
        // Read and verify all entries
        try (BlockReader reader = new BlockReader(jarzFile)) {
            // Verify classes
            for (var entry : classes.entrySet()) {
                byte[] read = reader.readClass(entry.getKey());
                assertNotNull(read, "Class not found: " + entry.getKey());
                assertArrayEquals(entry.getValue(), read);
            }
            
            // Verify resources
            for (var entry : resources.entrySet()) {
                byte[] read = reader.readEntry(entry.getKey());
                assertNotNull(read, "Resource not found: " + entry.getKey());
                assertArrayEquals(entry.getValue(), read);
            }
        }
    }
    
    @Test
    void testStoredBlockNoCompression() throws Exception {
        Path jarzFile = tempDir.resolve("stored.jarz");
        
        // Create "pre-compressed" data (random bytes that don't compress well)
        byte[] pngData = new byte[1000];
        new Random(42).nextBytes(pngData);
        pngData[0] = (byte)0x89;
        pngData[1] = 'P';
        pngData[2] = 'N';
        pngData[3] = 'G';
        
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("image.png", pngData);
        
        ResourceBlockAssigner assigner = new ResourceBlockAssigner();
        List<TypedBlock> blocks = assigner.assign(resources, 0);
        
        assertEquals(1, blocks.size());
        assertEquals(BlockType.STORED, blocks.get(0).type());
        assertFalse(blocks.get(0).type().shouldCompress());
        
        // Write and read
        try (BlockWriter writer = new BlockWriter(jarzFile)) {
            writer.writeTypedBlock(blocks.get(0));
        }
        
        try (BlockReader reader = new BlockReader(jarzFile)) {
            byte[] read = reader.readEntry("image.png");
            assertArrayEquals(pngData, read);
        }
    }
    
    private byte[] createFakeClass(String name) {
        // Minimal valid class file header
        byte[] data = new byte[100];
        data[0] = (byte)0xCA;
        data[1] = (byte)0xFE;
        data[2] = (byte)0xBA;
        data[3] = (byte)0xBE;
        // Fill rest with name-based pattern
        byte[] nameBytes = name.getBytes();
        System.arraycopy(nameBytes, 0, data, 4, Math.min(nameBytes.length, 96));
        return data;
    }
}
