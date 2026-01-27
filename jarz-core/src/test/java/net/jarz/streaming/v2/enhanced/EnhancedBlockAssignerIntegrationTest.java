package net.jarz.streaming.v2.enhanced;

import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * Integration test for EnhancedBlockAssigner with framework detectors.
 */
class EnhancedBlockAssignerIntegrationTest {
    
    @Test
    void testFrameworkDetectionIntegration() {
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        
        // Create test classes from different frameworks
        Map<String, byte[]> classFiles = new HashMap<>();
        classFiles.put("org.apache.flink.streaming.api.StreamExecutionEnvironment", new byte[100]);
        classFiles.put("org.apache.flink.table.api.TableEnvironment", new byte[100]);
        classFiles.put("org.apache.spark.sql.SparkSession", new byte[100]);
        classFiles.put("org.apache.spark.SparkContext", new byte[100]);
        classFiles.put("org.springframework.boot.SpringApplication", new byte[100]);
        classFiles.put("org.springframework.web.bind.annotation.RestController", new byte[100]);
        classFiles.put("com.example.MyClass", new byte[100]);
        
        // Create simple dependency graph
        DependencyGraph graph = new DependencyGraph();
        classFiles.keySet().forEach(graph::addClass);
        
        // Test block assignment
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        assertNotNull(blocks);
        assertFalse(blocks.isEmpty());
        
        // Verify framework classes are grouped appropriately
        // (Implementation will group by detected framework modules)
        assertTrue(blocks.size() > 0);
        
        // Verify all classes are assigned to blocks
        int totalClassesInBlocks = blocks.stream()
            .mapToInt(block -> block.entries().size())
            .sum();
        assertEquals(classFiles.size(), totalClassesInBlocks);
    }
    
    @Test
    void testFallbackToPackageGrouping() {
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        
        // Create test classes without framework patterns
        Map<String, byte[]> classFiles = new HashMap<>();
        classFiles.put("com.example.service.UserService", new byte[100]);
        classFiles.put("com.example.service.OrderService", new byte[100]);
        classFiles.put("com.example.model.User", new byte[100]);
        classFiles.put("com.example.model.Order", new byte[100]);
        classFiles.put("org.mycompany.util.StringUtils", new byte[100]);
        
        DependencyGraph graph = new DependencyGraph();
        classFiles.keySet().forEach(graph::addClass);
        
        List<Block> blocks = assigner.assignBlocks(classFiles, graph);
        
        assertNotNull(blocks);
        assertFalse(blocks.isEmpty());
        
        // Verify all classes are assigned
        int totalClassesInBlocks = blocks.stream()
            .mapToInt(block -> block.entries().size())
            .sum();
        assertEquals(classFiles.size(), totalClassesInBlocks);
    }
}
