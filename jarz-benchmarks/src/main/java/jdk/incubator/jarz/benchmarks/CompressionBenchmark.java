package jdk.incubator.jarz.benchmarks;

import jdk.incubator.jarz.v2.BlockReader;
import jdk.incubator.jarz.v2.BlockWriter;
import jdk.incubator.jarz.v2.Block;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class CompressionBenchmark {

    private byte[] classData;
    private byte[] jarData;
    private byte[] jarzData;
    private Path tempJarz;

    @Setup
    public void setup() throws IOException {
        // Generate synthetic class data for testing
        classData = new byte[8192];
        for (int i = 0; i < classData.length; i++) {
            classData[i] = (byte) (i % 256);
        }

        // Create JAR data
        ByteArrayOutputStream jarOut = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(jarOut)) {
            jar.putNextEntry(new JarEntry("Test.class"));
            jar.write(classData);
            jar.closeEntry();
        }
        jarData = jarOut.toByteArray();

        // Create JARZ data
        tempJarz = Files.createTempFile("benchmark", ".jarz");
        try (BlockWriter writer = new BlockWriter(tempJarz)) {
            Block block = new Block(0);
            block.add("Test.class", classData);
            writer.writeBlock(block);
        }
        jarzData = Files.readAllBytes(tempJarz);
    }

    @TearDown
    public void tearDown() throws IOException {
        if (tempJarz != null) {
            Files.deleteIfExists(tempJarz);
        }
    }

    @Benchmark
    public void jarzDecompress(Blackhole bh) throws IOException {
        try (BlockReader reader = new BlockReader(tempJarz)) {
            byte[] data = reader.readEntry("Test.class");
            bh.consume(data);
        }
    }

    @Benchmark
    public void jarCompress(Blackhole bh) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(out)) {
            jar.putNextEntry(new JarEntry("Test.class"));
            jar.write(classData);
            jar.closeEntry();
        }
        bh.consume(out.toByteArray());
    }

    @Benchmark
    public void jarzCompress(Blackhole bh) throws IOException {
        Path temp = Files.createTempFile("bench", ".jarz");
        try (BlockWriter writer = new BlockWriter(temp)) {
            Block block = new Block(0);
            block.add("Test.class", classData);
            writer.writeBlock(block);
        }
        bh.consume(Files.readAllBytes(temp));
        Files.deleteIfExists(temp);
    }
}
