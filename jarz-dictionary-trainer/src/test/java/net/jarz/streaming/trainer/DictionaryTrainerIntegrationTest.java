package net.jarz.streaming.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for dictionary training pipeline.
 * Tests with real class files and known datasets.
 */
class DictionaryTrainerIntegrationTest {
    
    @Test
    void trainOnSyntheticJdkClasses(@TempDir Path tempDir) throws Exception {
        // Create mock JDK-like class files
        Path jdkDir = tempDir.resolve("jdk");
        Files.createDirectories(jdkDir);
        
        // Generate realistic class files with common patterns
        for (int i = 0; i < 200; i++) {
            Path classFile = jdkDir.resolve("java/lang/Class" + i + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, generateRealisticClassBytes(i, "java/lang/Object"));
        }
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(jdkDir))
            .maxPerCategory(150)
            .maxTotal(150)
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        assertThat(result.dictionary()).isNotEmpty();
        assertThat(result.dictionary().length).isLessThanOrEqualTo(32 * 1024);
        assertThat(result.sampleCount()).isEqualTo(150);
        assertThat(result.validation().improvement()).isGreaterThan(0.05); // At least 5% improvement
        
        System.out.println("Dictionary size: " + result.dictionary().length + " bytes");
        System.out.println("Compression improvement: " + (result.validation().improvement() * 100) + "%");
    }
    
    @Test
    void trainOnMixedCorpus(@TempDir Path tempDir) throws Exception {
        Path jdkDir = tempDir.resolve("jdk");
        Path frameworkDir = tempDir.resolve("frameworks");
        Files.createDirectories(jdkDir);
        Files.createDirectories(frameworkDir);
        
        // JDK classes (uniform patterns)
        for (int i = 0; i < 100; i++) {
            Path classFile = jdkDir.resolve("java/util/Class" + i + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, generateRealisticClassBytes(i, "java/lang/Object"));
        }
        
        // Framework classes (varied patterns)
        for (int i = 0; i < 100; i++) {
            Path classFile = frameworkDir.resolve("org/springframework/Class" + i + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, generateRealisticClassBytes(i * 10, "org/springframework/core/BaseClass"));
        }
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(jdkDir))
            .frameworkJars(List.of(frameworkDir))
            .maxPerCategory(75)
            .maxTotal(150)
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        assertThat(result.sampleCount()).isEqualTo(150);
        assertThat(result.perCategoryRatio()).containsKeys("JDK", "Framework");
        assertThat(result.validation().improvement()).isGreaterThan(0.08); // Better improvement with diversity
        
        // Verify category-specific performance
        double jdkRatio = result.perCategoryRatio().get("JDK");
        double frameworkRatio = result.perCategoryRatio().get("Framework");
        assertThat(jdkRatio).isBetween(0.3, 0.8);
        assertThat(frameworkRatio).isBetween(0.3, 0.8);
    }
    
    @Test
    @EnabledIfSystemProperty(named = "jarz.test.realJdk", matches = "true")
    void trainOnRealJdkClasses() throws Exception {
        // Only run if real JDK testing is enabled
        String jdkHome = KnownDatasets.getDefaultJdkHome();
        TrainingCorpus corpus = KnownDatasets.fromJdkHome(jdkHome);
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        assertThat(result.dictionary()).isNotEmpty();
        assertThat(result.sampleCount()).isGreaterThan(500);
        assertThat(result.validation().improvement()).isGreaterThan(0.10); // Real classes should compress better
        
        System.out.println("Real JDK training results:");
        System.out.println("- Samples: " + result.sampleCount());
        System.out.println("- Dictionary size: " + result.dictionary().length + " bytes");
        System.out.println("- Improvement: " + (result.validation().improvement() * 100) + "%");
    }
    
    @Test
    @EnabledIfSystemProperty(named = "jarz.test.realMaven", matches = "true")
    void trainOnRealMavenRepository() throws Exception {
        // Only run if real Maven testing is enabled
        String mavenHome = KnownDatasets.getDefaultMavenHome();
        TrainingCorpus corpus = KnownDatasets.springBootFocused(mavenHome);
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        assertThat(result.dictionary()).isNotEmpty();
        assertThat(result.sampleCount()).isGreaterThan(100);
        
        System.out.println("Real Maven repository training results:");
        System.out.println("- Samples: " + result.sampleCount());
        System.out.println("- Dictionary size: " + result.dictionary().length + " bytes");
        System.out.println("- Categories: " + result.perCategoryRatio().keySet());
    }
    
    @Test
    void validateCompressionImprovement(@TempDir Path tempDir) throws Exception {
        Path classDir = tempDir.resolve("classes");
        Files.createDirectories(classDir);
        
        // Create classes with highly repetitive patterns (should compress very well)
        for (int i = 0; i < 300; i++) {
            byte[] classBytes = generateRepetitiveClassBytes(i);
            Files.write(classDir.resolve("Class" + i + ".class"), classBytes);
        }
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(classDir))
            .maxTotal(300)
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        // Repetitive patterns should achieve significant compression improvement
        assertThat(result.validation().improvement()).isGreaterThan(0.15); // At least 15% improvement
        assertThat(result.validation().dictionaryRatio()).isLessThan(0.6); // Better than 60% compression
    }
    
    @Test
    void handleLargeCorpus(@TempDir Path tempDir) throws Exception {
        Path largeDir = tempDir.resolve("large");
        Files.createDirectories(largeDir);
        
        // Create a large number of varied class files
        for (int i = 0; i < 1000; i++) {
            Path classFile = largeDir.resolve("package" + (i / 100) + "/Class" + i + ".class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, generateRealisticClassBytes(i, "java/lang/Object"));
        }
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(largeDir))
            .maxTotal(500) // Limit to reasonable size
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        
        assertThat(result.sampleCount()).isEqualTo(500);
        assertThat(result.dictionary().length).isLessThanOrEqualTo(32 * 1024);
        assertThat(result.validation().validationSamples()).isGreaterThan(50);
    }
    
    @Test
    void handleEmptyOrInvalidInput(@TempDir Path tempDir) throws Exception {
        Path emptyDir = tempDir.resolve("empty");
        Files.createDirectories(emptyDir);
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(emptyDir))
            .maxTotal(100)
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        
        assertThatThrownBy(() -> trainer.train(corpus))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Need at least");
    }
    
    private byte[] generateRealisticClassBytes(int seed, String superClass) {
        ByteBuffer buf = ByteBuffer.allocate(2000 + seed * 10);
        
        // Class file magic and version
        buf.putInt(0xCAFEBABE);
        buf.putShort((short) 0);
        buf.putShort((short) 61); // Java 17
        
        // Constant pool with realistic entries
        buf.putShort((short) 20); // CP count
        
        // Add UTF-8 constants (common in bytecode)
        addUtf8Constant(buf, superClass);
        addUtf8Constant(buf, "Code");
        addUtf8Constant(buf, "LineNumberTable");
        addUtf8Constant(buf, "LocalVariableTable");
        addUtf8Constant(buf, "SourceFile");
        addUtf8Constant(buf, "Class" + seed + ".java");
        
        // Add class references
        for (int i = 7; i < 20; i++) {
            buf.put((byte) 7); // CONSTANT_Class
            buf.putShort((short) (1 + (i % 6))); // Reference to UTF-8
        }
        
        // Rest of class structure
        buf.putShort((short) 0x0021); // public class
        buf.putShort((short) 7); // this_class
        buf.putShort((short) 8); // super_class
        buf.putShort((short) 0); // interfaces_count
        buf.putShort((short) 0); // fields_count
        buf.putShort((short) 0); // methods_count
        buf.putShort((short) 0); // attributes_count
        
        // Fill remaining with patterns common in bytecode
        while (buf.remaining() > 10) {
            // Common bytecode patterns
            buf.put((byte) 0x2A); // aload_0
            buf.put((byte) 0xB7); // invokespecial
            buf.putShort((short) (seed % 100 + 1)); // method reference
            buf.put((byte) 0xB1); // return
        }
        
        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }
    
    private void addUtf8Constant(ByteBuffer buf, String str) {
        buf.put((byte) 1); // CONSTANT_Utf8
        buf.putShort((short) str.length());
        buf.put(str.getBytes());
    }
    
    private byte[] generateRepetitiveClassBytes(int seed) {
        ByteBuffer buf = ByteBuffer.allocate(3000);
        
        buf.putInt(0xCAFEBABE);
        buf.putShort((short) 0);
        buf.putShort((short) 61);
        buf.putShort((short) 50); // Large CP
        
        // Repetitive constant pool entries
        String commonString = "java/lang/Object";
        for (int i = 1; i < 50; i++) {
            if (i % 3 == 1) {
                addUtf8Constant(buf, commonString + seed);
            } else if (i % 3 == 2) {
                buf.put((byte) 7); // Class
                buf.putShort((short) 1);
            } else {
                buf.put((byte) 9); // Fieldref
                buf.putShort((short) 2);
                buf.putShort((short) 1);
            }
        }
        
        // Repetitive bytecode patterns
        byte[] pattern = {0x2A, (byte) 0xB7, 0x00, 0x01, (byte) 0xB1}; // Common method pattern
        while (buf.remaining() > pattern.length) {
            buf.put(pattern);
        }
        
        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }
}
