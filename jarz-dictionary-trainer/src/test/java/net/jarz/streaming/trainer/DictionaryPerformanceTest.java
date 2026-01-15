package net.jarz.streaming.trainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance tests for dictionary training to validate compression improvements.
 */
class DictionaryPerformanceTest {
    
    @Test
    void measureCompressionImprovement(@TempDir Path tempDir) throws Exception {
        // Create test corpus with known patterns
        Path corpusDir = tempDir.resolve("corpus");
        Files.createDirectories(corpusDir);
        
        // Generate classes with common bytecode patterns
        for (int i = 0; i < 500; i++) {
            Path classFile = corpusDir.resolve("TestClass" + i + ".class");
            Files.write(classFile, generateBytecodeWithPatterns(i));
        }
        
        TrainingCorpus corpus = TrainingCorpus.builder()
            .jdkModules(List.of(corpusDir))
            .maxTotal(500)
            .build();
        
        DictionaryTrainer trainer = new DictionaryTrainer();
        
        long startTime = System.currentTimeMillis();
        DictionaryTrainer.TrainingResult result = trainer.train(corpus);
        long trainingTime = System.currentTimeMillis() - startTime;
        
        // Validate performance metrics
        assertThat(result.validation().improvement()).isGreaterThan(0.10); // At least 10% improvement
        assertThat(result.dictionary().length).isLessThanOrEqualTo(32 * 1024); // Within size limit
        assertThat(trainingTime).isLessThan(30_000); // Complete within 30 seconds
        
        System.out.println("Performance Results:");
        System.out.println("- Training time: " + trainingTime + "ms");
        System.out.println("- Samples processed: " + result.sampleCount());
        System.out.println("- Dictionary size: " + result.dictionary().length + " bytes");
        System.out.println("- Compression improvement: " + (result.validation().improvement() * 100) + "%");
        System.out.println("- Baseline ratio: " + (result.validation().baselineRatio() * 100) + "%");
        System.out.println("- Dictionary ratio: " + (result.validation().dictionaryRatio() * 100) + "%");
    }
    
    @Test
    void validateDifferentCorpusSizes(@TempDir Path tempDir) throws Exception {
        int[] corpusSizes = {100, 500, 1000, 2000};
        
        for (int size : corpusSizes) {
            Path corpusDir = tempDir.resolve("corpus_" + size);
            Files.createDirectories(corpusDir);
            
            // Generate corpus of specified size
            for (int i = 0; i < size; i++) {
                Path classFile = corpusDir.resolve("Class" + i + ".class");
                Files.write(classFile, generateBytecodeWithPatterns(i));
            }
            
            TrainingCorpus corpus = TrainingCorpus.builder()
                .jdkModules(List.of(corpusDir))
                .maxTotal(size)
                .build();
            
            DictionaryTrainer trainer = new DictionaryTrainer();
            
            long startTime = System.currentTimeMillis();
            DictionaryTrainer.TrainingResult result = trainer.train(corpus);
            long trainingTime = System.currentTimeMillis() - startTime;
            
            System.out.printf("Corpus size %d: %.1f%% improvement in %dms%n", 
                size, result.validation().improvement() * 100, trainingTime);
            
            // Larger corpus should generally provide better compression
            if (size >= 500) {
                assertThat(result.validation().improvement()).isGreaterThan(0.08);
            }
            
            // Training time should scale reasonably
            assertThat(trainingTime).isLessThan(size * 100); // Rough linear scaling
        }
    }
    
    @Test
    void compareDifferentPatternTypes(@TempDir Path tempDir) throws Exception {
        // Test different types of bytecode patterns
        String[] patternTypes = {"uniform", "varied", "repetitive"};
        
        for (String patternType : patternTypes) {
            Path corpusDir = tempDir.resolve("corpus_" + patternType);
            Files.createDirectories(corpusDir);
            
            for (int i = 0; i < 300; i++) {
                Path classFile = corpusDir.resolve("Class" + i + ".class");
                byte[] classBytes;
                switch (patternType) {
                    case "uniform":
                        classBytes = generateUniformBytecode(i);
                        break;
                    case "varied":
                        classBytes = generateVariedBytecode(i);
                        break;
                    case "repetitive":
                        classBytes = generateRepetitiveBytecode(i);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown pattern type");
                }
                Files.write(classFile, classBytes);
            }
            
            TrainingCorpus corpus = TrainingCorpus.builder()
                .jdkModules(List.of(corpusDir))
                .maxTotal(300)
                .build();
            
            DictionaryTrainer trainer = new DictionaryTrainer();
            DictionaryTrainer.TrainingResult result = trainer.train(corpus);
            
            System.out.printf("Pattern type '%s': %.1f%% improvement%n", 
                patternType, result.validation().improvement() * 100);
            
            // Repetitive patterns should compress best
            if ("repetitive".equals(patternType)) {
                assertThat(result.validation().improvement()).isGreaterThan(0.15);
            }
        }
    }
    
    private byte[] generateBytecodeWithPatterns(int seed) {
        ByteBuffer buf = ByteBuffer.allocate(2000);
        
        // Standard class file header
        buf.putInt(0xCAFEBABE);
        buf.putShort((short) 0);
        buf.putShort((short) 61);
        buf.putShort((short) 30); // CP count
        
        // Common constant pool patterns
        String[] commonStrings = {
            "java/lang/Object", "java/lang/String", "java/util/List",
            "Code", "LineNumberTable", "LocalVariableTable", "SourceFile"
        };
        
        for (String str : commonStrings) {
            buf.put((byte) 1); // UTF8
            buf.putShort((short) str.length());
            buf.put(str.getBytes());
        }
        
        // Fill remaining CP with references
        while (buf.position() < 500) {
            buf.put((byte) 7); // Class
            buf.putShort((short) (1 + (seed % 7)));
        }
        
        // Common bytecode sequences
        byte[][] commonSequences = {
            {0x2A, (byte) 0xB7, 0x00, 0x01}, // aload_0, invokespecial
            {0x2A, (byte) 0xB4, 0x00, 0x02}, // aload_0, getfield
            {0x03, (byte) 0xAC}, // iconst_0, ireturn
            {(byte) 0xBB, 0x00, 0x03, 0x59, (byte) 0xB7, 0x00, 0x04} // new, dup, invokespecial
        };
        
        // Fill with common patterns
        while (buf.remaining() > 10) {
            byte[] sequence = commonSequences[seed % commonSequences.length];
            if (buf.remaining() >= sequence.length) {
                buf.put(sequence);
            } else {
                break;
            }
        }
        
        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }
    
    private byte[] generateUniformBytecode(int seed) {
        // All classes have very similar structure
        return generateBytecodeWithPatterns(seed % 5); // Limited variation
    }
    
    private byte[] generateVariedBytecode(int seed) {
        // Classes have different structures
        return generateBytecodeWithPatterns(seed * 17); // More variation
    }
    
    private byte[] generateRepetitiveBytecode(int seed) {
        ByteBuffer buf = ByteBuffer.allocate(1500);
        
        buf.putInt(0xCAFEBABE);
        buf.putShort((short) 0);
        buf.putShort((short) 61);
        buf.putShort((short) 10);
        
        // Highly repetitive constant pool
        String commonString = "java/lang/Object";
        for (int i = 1; i < 10; i++) {
            buf.put((byte) 1);
            buf.putShort((short) commonString.length());
            buf.put(commonString.getBytes());
        }
        
        // Highly repetitive bytecode
        byte[] pattern = {0x2A, (byte) 0xB7, 0x00, 0x01, (byte) 0xB1};
        while (buf.remaining() > pattern.length) {
            buf.put(pattern);
        }
        
        byte[] result = new byte[buf.position()];
        buf.rewind();
        buf.get(result);
        return result;
    }
}
