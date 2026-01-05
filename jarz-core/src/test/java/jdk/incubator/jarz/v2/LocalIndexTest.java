package jdk.incubator.jarz.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for local index functionality.
 */
public class LocalIndexTest {
    
    @Test
    public void testLocalIndexSaveAndLoad(@TempDir Path tempDir) throws IOException {
        // Create a local index
        String originalUrl = "https://example.com/app.jarz";
        long originalSize = 12345L;
        JarzLocalIndex index = new JarzLocalIndex(originalUrl, originalSize);
        
        // Add some class entries
        JarzLocalIndex.ClassEntry entry1 = new JarzLocalIndex.ClassEntry(0, 100, 500, 10, 200);
        JarzLocalIndex.ClassEntry entry2 = new JarzLocalIndex.ClassEntry(1, 600, 300, 20, 150);
        
        index.addClassEntry("com/example/MyClass", entry1);
        index.addClassEntry("com/example/OtherClass", entry2);
        
        // Save to file
        Path indexFile = tempDir.resolve("test.jarz.index");
        index.save(indexFile);
        
        // Load from file
        JarzLocalIndex loadedIndex = JarzLocalIndex.load(indexFile);
        
        // Verify loaded index
        assertThat(loadedIndex.getOriginalJarzUrl()).isEqualTo(originalUrl);
        assertThat(loadedIndex.getOriginalJarzSize()).isEqualTo(originalSize);
        assertThat(loadedIndex.hasClass("com/example/MyClass")).isTrue();
        assertThat(loadedIndex.hasClass("com/example/OtherClass")).isTrue();
        assertThat(loadedIndex.hasClass("com/example/NonExistent")).isFalse();
        
        JarzLocalIndex.ClassEntry loadedEntry1 = loadedIndex.getClassEntry("com/example/MyClass");
        assertThat(loadedEntry1.blockId).isEqualTo(0);
        assertThat(loadedEntry1.blockOffset).isEqualTo(100);
        assertThat(loadedEntry1.blockSize).isEqualTo(500);
        assertThat(loadedEntry1.entryOffset).isEqualTo(10);
    }
    
    @Test
    public void testEmptyLocalIndex(@TempDir Path tempDir) throws IOException {
        // Create empty index
        JarzLocalIndex index = new JarzLocalIndex("test.jarz", 1000);
        
        // Save and load
        Path indexFile = tempDir.resolve("empty.jarz.index");
        index.save(indexFile);
        
        JarzLocalIndex loadedIndex = JarzLocalIndex.load(indexFile);
        
        // Verify empty index
        assertThat(loadedIndex.getOriginalJarzUrl()).isEqualTo("test.jarz");
        assertThat(loadedIndex.getOriginalJarzSize()).isEqualTo(1000);
        assertThat(loadedIndex.hasClass("any/class")).isFalse();
        assertThat(loadedIndex.getClassEntry("any/class")).isNull();
    }
}
