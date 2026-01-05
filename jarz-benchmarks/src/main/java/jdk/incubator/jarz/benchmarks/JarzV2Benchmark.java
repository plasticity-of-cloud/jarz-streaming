package jdk.incubator.jarz.benchmarks;

import jdk.incubator.jarz.v2.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * JMH benchmarks for JARZ v2 block-based compression.
 * Compares JAR, JARZ v1, and JARZ v2 performance.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class JarzV2Benchmark {

    @Param({"100", "500"})
    private int classCount;

    private Map<String, byte[]> classData;
    private Path jarFile;
    private Path jarzV2File;
    private List<String> classNames;

    @Setup
    public void setup() throws IOException {
        // Generate synthetic class data
        classData = new LinkedHashMap<>();
        classNames = new ArrayList<>();
        Random rand = new Random(42);
        
        for (int i = 0; i < classCount; i++) {
            String name = "com/example/Class" + i + ".class";
            byte[] data = generateClassData(rand, 2000 + rand.nextInt(6000));
            classData.put(name, data);
            classNames.add(name);
        }

        // Create JAR
        jarFile = Files.createTempFile("benchmark", ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarFile))) {
            for (var e : classData.entrySet()) {
                jar.putNextEntry(new JarEntry(e.getKey()));
                jar.write(e.getValue());
                jar.closeEntry();
            }
        }

        // Create JARZ v2
        jarzV2File = Files.createTempFile("benchmark", ".jarz");
        List<Block> blocks = createBlocks(classData);
        try (BlockWriter writer = new BlockWriter(jarzV2File)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
    }

    @TearDown
    public void tearDown() throws IOException {
        Files.deleteIfExists(jarFile);
        Files.deleteIfExists(jarzV2File);
    }

    @Benchmark
    public void jarCompress(Blackhole bh) throws IOException {
        Path temp = Files.createTempFile("bench", ".jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(temp))) {
            for (var e : classData.entrySet()) {
                jar.putNextEntry(new JarEntry(e.getKey()));
                jar.write(e.getValue());
                jar.closeEntry();
            }
        }
        bh.consume(Files.size(temp));
        Files.deleteIfExists(temp);
    }

    @Benchmark
    public void jarzV2Compress(Blackhole bh) throws IOException {
        Path temp = Files.createTempFile("bench", ".jarz");
        List<Block> blocks = createBlocks(classData);
        try (BlockWriter writer = new BlockWriter(temp)) {
            for (Block block : blocks) {
                writer.writeBlock(block);
            }
        }
        bh.consume(Files.size(temp));
        Files.deleteIfExists(temp);
    }

    @Benchmark
    public void jarzV2DecompressSingle(Blackhole bh) throws IOException {
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            // Read single class (simulates on-demand loading)
            byte[] data = reader.readClass(classNames.get(0));
            bh.consume(data);
        }
    }

    @Benchmark
    public void jarzV2DecompressBlock(Blackhole bh) throws IOException {
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            // Read 10 classes from same block (simulates block caching benefit)
            for (int i = 0; i < Math.min(10, classCount); i++) {
                byte[] data = reader.readClass(classNames.get(i));
                bh.consume(data);
            }
        }
    }

    @Benchmark
    public void jarzV2DecompressAll(Blackhole bh) throws IOException {
        try (BlockReader reader = new BlockReader(jarzV2File)) {
            for (String name : classNames) {
                byte[] data = reader.readClass(name);
                bh.consume(data);
            }
        }
    }

    private List<Block> createBlocks(Map<String, byte[]> classes) {
        List<Block> blocks = new ArrayList<>();
        Block current = new Block(0);
        int targetSize = 512 * 1024;

        for (var e : classes.entrySet()) {
            if (current.size() + e.getValue().length > targetSize && !current.isEmpty()) {
                blocks.add(current);
                current = new Block(blocks.size());
            }
            current.add(e.getKey(), e.getValue());
        }
        if (!current.isEmpty()) {
            blocks.add(current);
        }
        return blocks;
    }

    private byte[] generateClassData(Random rand, int size) {
        byte[] data = new byte[size];
        // Class file magic
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        // Fill with semi-random data (simulates bytecode patterns)
        for (int i = 4; i < size; i++) {
            if (i % 16 == 0) {
                data[i] = (byte) rand.nextInt(256);
            } else {
                data[i] = (byte) ((data[i - 1] + rand.nextInt(8)) % 256);
            }
        }
        return data;
    }
}
