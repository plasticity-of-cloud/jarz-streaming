package jdk.incubator.jarz.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JARZ v2 block assignment and dependency-based grouping.
 * Tests the BlockAssigner's ability to group related classes efficiently.
 */
class BlockAssignmentTest {

    @Test
    void testBasicBlockAssignment(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Create classes of varying sizes
        classes.put("com/example/SmallClass", generateClassData(1000));
        classes.put("com/example/MediumClass", generateClassData(5000));
        classes.put("com/example/LargeClass", generateClassData(15000));
        classes.put("com/example/HugeClass", generateClassData(50000));
        
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        // Use default production block sizes from BlockAssigner
        BlockAssigner assigner = new BlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        assertThat(blocks).isNotEmpty();
        
        // Verify no block exceeds maximum size
        for (Block block : blocks) {
            assertThat(block.size()).isLessThanOrEqualTo(JarzV2Format.MAX_BLOCK_SIZE);
        }
        
        // Verify all classes are assigned
        int totalClasses = blocks.stream().mapToInt(Block::entryCount).sum();
        assertThat(totalClasses).isEqualTo(classes.size());
    }
    
    @Test
    void testDependencyBasedGrouping(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/base/BaseService", generateClassData(5000));
        classes.put("com/base/ServiceImpl", generateClassData(4000));
        classes.put("com/base/ServiceHelper", generateClassData(3000));
        classes.put("com/other/UnrelatedClass", generateClassData(4000));
        classes.put("com/other/AnotherUnrelated", generateClassData(3500));
        
        // Build dependency graph
        DependencyGraph graph = new DependencyGraph();
        graph.addEdge("com/base/ServiceImpl", "com/base/BaseService");
        graph.addEdge("com/base/ServiceHelper", "com/base/BaseService");
        graph.addClass("com/other/UnrelatedClass");
        graph.addClass("com/other/AnotherUnrelated");
        
        BlockAssigner assigner = new BlockAssigner(50000, 100000); // Large enough for all
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Find which blocks contain our classes
        Map<String, Integer> classToBlock = new HashMap<>();
        for (Block block : blocks) {
            for (Block.ClassEntry entry : block.entries()) {
                classToBlock.put(entry.className(), block.id());
            }
        }
        
        // Related classes should be in the same block
        int baseBlock = classToBlock.get("com/base/BaseService");
        assertThat(classToBlock.get("com/base/ServiceImpl"))
            .as("ServiceImpl should be in same block as BaseService")
            .isEqualTo(baseBlock);
        assertThat(classToBlock.get("com/base/ServiceHelper"))
            .as("ServiceHelper should be in same block as BaseService")
            .isEqualTo(baseBlock);
    }
    
    @Test
    void testPackageBasedGrouping(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Classes from same package
        for (int i = 0; i < 5; i++) {
            classes.put("com/example/service/Service" + i, generateClassData(3000));
        }
        
        // Classes from different package
        for (int i = 0; i < 5; i++) {
            classes.put("com/example/util/Util" + i, generateClassData(3000));
        }
        
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        BlockAssigner assigner = new BlockAssigner(); // Use default production limits
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Verify package-based grouping
        Map<String, Integer> classToBlock = new HashMap<>();
        for (Block block : blocks) {
            for (Block.ClassEntry entry : block.entries()) {
                classToBlock.put(entry.className(), block.id());
            }
        }
        
        // All service classes should be in same block (if they fit)
        Integer serviceBlock = classToBlock.get("com/example/service/Service0");
        for (int i = 1; i < 5; i++) {
            String className = "com/example/service/Service" + i;
            if (classToBlock.containsKey(className)) {
                assertThat(classToBlock.get(className))
                    .as("Service classes should be grouped together")
                    .isEqualTo(serviceBlock);
            }
        }
    }
    
    @Test
    void testBlockSizeLimits(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Create many small classes
        for (int i = 0; i < 100; i++) {
            classes.put("com/example/Class" + i, generateClassData(1000));
        }
        
        DependencyGraph graph = new DependencyGraph();
        classes.keySet().forEach(graph::addClass);
        
        // Small block limits to test size enforcement
        BlockAssigner assigner = new BlockAssigner(5000, 8000); // 5KB target, 8KB max
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Verify size constraints
        for (Block block : blocks) {
            assertThat(block.size())
                .as("Block %d size should not exceed maximum", block.id())
                .isLessThanOrEqualTo(8000);
        }
        
        // Should have multiple blocks due to size constraints
        assertThat(blocks.size()).isGreaterThan(10);
        
        // Verify all classes assigned
        int totalClasses = blocks.stream().mapToInt(Block::entryCount).sum();
        assertThat(totalClasses).isEqualTo(100);
    }
    
    @Test
    void testSingleLargeClass(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        classes.put("com/example/VeryLargeClass", generateClassData(100000)); // 100KB
        
        DependencyGraph graph = new DependencyGraph();
        graph.addClass("com/example/VeryLargeClass");
        
        // Small block limit - class exceeds both target and max
        BlockAssigner assigner = new BlockAssigner(10000, 20000);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Should still create a block for the large class
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).entryCount()).isEqualTo(1);
        assertThat(blocks.get(0).size()).isGreaterThan(20000); // Exceeds max but still assigned
    }
    
    @Test
    void testEmptyClassSet(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        DependencyGraph graph = new DependencyGraph();
        
        BlockAssigner assigner = new BlockAssigner();
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        assertThat(blocks).isEmpty();
    }
    
    @Test
    void testComplexDependencyGraph(@TempDir Path tempDir) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Create a complex dependency hierarchy
        classes.put("com/base/Interface", generateClassData(2000));
        classes.put("com/base/AbstractBase", generateClassData(4000));
        classes.put("com/impl/ConcreteA", generateClassData(3000));
        classes.put("com/impl/ConcreteB", generateClassData(3000));
        classes.put("com/util/Helper", generateClassData(2000));
        classes.put("com/factory/Factory", generateClassData(3000));
        
        DependencyGraph graph = new DependencyGraph();
        
        // Build dependency relationships
        graph.addEdge("com/base/AbstractBase", "com/base/Interface");
        graph.addEdge("com/impl/ConcreteA", "com/base/AbstractBase");
        graph.addEdge("com/impl/ConcreteB", "com/base/AbstractBase");
        graph.addEdge("com/impl/ConcreteA", "com/util/Helper");
        graph.addEdge("com/impl/ConcreteB", "com/util/Helper");
        graph.addEdge("com/factory/Factory", "com/impl/ConcreteA");
        graph.addEdge("com/factory/Factory", "com/impl/ConcreteB");
        
        BlockAssigner assigner = new BlockAssigner(15000, 25000);
        List<Block> blocks = assigner.assignBlocks(classes, graph);
        
        // Verify strongly connected components are grouped
        Map<String, Integer> classToBlock = new HashMap<>();
        for (Block block : blocks) {
            for (Block.ClassEntry entry : block.entries()) {
                classToBlock.put(entry.className(), block.id());
            }
        }
        
        // Base classes should be together
        Integer baseBlock = classToBlock.get("com/base/Interface");
        assertThat(classToBlock.get("com/base/AbstractBase"))
            .as("AbstractBase should be with Interface")
            .isEqualTo(baseBlock);
        
        // Implementation classes should be together (if they fit)
        Integer implBlock = classToBlock.get("com/impl/ConcreteA");
        assertThat(classToBlock.get("com/impl/ConcreteB"))
            .as("ConcreteB should be with ConcreteA")
            .isEqualTo(implBlock);
    }
    
    private byte[] generateClassData(int size) {
        byte[] data = new byte[size];
        
        // Class file magic
        data[0] = (byte) 0xCA;
        data[1] = (byte) 0xFE;
        data[2] = (byte) 0xBA;
        data[3] = (byte) 0xBE;
        
        // Fill with deterministic pattern
        for (int i = 4; i < size; i++) {
            data[i] = (byte) ((i * 31) % 256);
        }
        
        return data;
    }
}
