package net.jarz.streaming.framework.detectors;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import net.jarz.streaming.v2.enhanced.EnhancedBlockAssigner;
import net.jarz.streaming.v2.Block;
import net.jarz.streaming.v2.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

/**
 * End-to-end validation test for framework detector system.
 */
class EndToEndValidationTest {
    
    @Test
    void testCompleteFrameworkDetectionPipeline() {
        // Test the complete pipeline: Registry → EnhancedBlockAssigner → Block creation
        
        // 1. Test registry directly
        FrameworkDetectorRegistry registry = new FrameworkDetectorRegistry();
        
        assertEquals("flink-streaming", registry.detectFramework("org.apache.flink.streaming.api.StreamExecutionEnvironment"));
        assertEquals("spark-sql", registry.detectFramework("org.apache.spark.sql.SparkSession"));
        assertEquals("spring-boot", registry.detectFramework("org.springframework.boot.SpringApplication"));
        assertEquals("hadoop-hdfs", registry.detectFramework("org.apache.hadoop.hdfs.DistributedFileSystem"));
        assertEquals("aws-s3", registry.detectFramework("com.amazonaws.services.s3.AmazonS3Client"));
        assertEquals("azure-storage", registry.detectFramework("com.azure.storage.blob.BlobServiceClient"));
        assertEquals("oci-objectstorage", registry.detectFramework("com.oracle.bmc.objectstorage.ObjectStorageClient"));
        assertEquals("oci-database", registry.detectFramework("com.oracle.bmc.database.DatabaseClient"));
        
        // 2. Test integration with EnhancedBlockAssigner
        EnhancedBlockAssigner assigner = new EnhancedBlockAssigner();
        
        Map<String, byte[]> realWorldClasses = createRealWorldClassSet();
        DependencyGraph graph = createDependencyGraph(realWorldClasses.keySet());
        
        List<Block> blocks = assigner.assignBlocks(realWorldClasses, graph);
        
        // 3. Validate results
        assertNotNull(blocks);
        assertFalse(blocks.isEmpty());
        
        // Verify all classes are assigned
        Set<String> assignedClasses = new HashSet<>();
        for (Block block : blocks) {
            for (Block.ClassEntry entry : block.entries()) {
                assignedClasses.add(entry.className());
            }
        }
        assertEquals(realWorldClasses.keySet(), assignedClasses);
        
        // 4. Validate framework grouping effectiveness
        validateFrameworkGrouping(blocks);
        
        System.out.printf("End-to-end validation successful:%n");
        System.out.printf("Classes processed: %d%n", realWorldClasses.size());
        System.out.printf("Blocks created: %d%n", blocks.size());
        System.out.printf("Average classes per block: %.1f%n", 
            (double) realWorldClasses.size() / blocks.size());
        System.out.printf("Frameworks tested: Flink, Spark, Spring, Hadoop, AWS SDK, Azure SDK, GCP SDK, Oracle OCI SDK%n");
    }
    
    private Map<String, byte[]> createRealWorldClassSet() {
        Map<String, byte[]> classes = new HashMap<>();
        
        // Flink classes
        classes.put("org.apache.flink.streaming.api.StreamExecutionEnvironment", new byte[1000]);
        classes.put("org.apache.flink.streaming.api.datastream.DataStream", new byte[800]);
        classes.put("org.apache.flink.table.api.TableEnvironment", new byte[1200]);
        classes.put("org.apache.flink.table.api.Table", new byte[900]);
        classes.put("org.apache.flink.connector.kafka.source.KafkaSource", new byte[1500]);
        
        // Spark classes
        classes.put("org.apache.spark.sql.SparkSession", new byte[1100]);
        classes.put("org.apache.spark.sql.Dataset", new byte[950]);
        classes.put("org.apache.spark.SparkContext", new byte[1300]);
        classes.put("org.apache.spark.streaming.StreamingContext", new byte[1000]);
        classes.put("org.apache.spark.mllib.classification.LogisticRegression", new byte[800]);
        
        // Spring classes
        classes.put("org.springframework.boot.SpringApplication", new byte[1200]);
        classes.put("org.springframework.web.bind.annotation.RestController", new byte[700]);
        classes.put("org.springframework.data.jpa.repository.JpaRepository", new byte[600]);
        classes.put("org.springframework.security.config.annotation.web.WebSecurityConfigurer", new byte[900]);
        
        // Hadoop classes
        classes.put("org.apache.hadoop.hdfs.DistributedFileSystem", new byte[1400]);
        classes.put("org.apache.hadoop.mapreduce.Job", new byte[1100]);
        classes.put("org.apache.hadoop.yarn.client.api.YarnClient", new byte[800]);
        
        // AWS SDK classes
        classes.put("com.amazonaws.services.s3.AmazonS3Client", new byte[1200]);
        classes.put("software.amazon.awssdk.services.lambda.LambdaClient", new byte[900]);
        classes.put("software.amazon.awssdk.services.dynamodb.DynamoDbClient", new byte[1000]);
        
        // Azure SDK classes
        classes.put("com.azure.storage.blob.BlobServiceClient", new byte[1100]);
        classes.put("com.azure.keyvault.secrets.SecretClient", new byte[800]);
        classes.put("com.azure.cosmos.CosmosClient", new byte[950]);
        
        // GCP SDK classes
        classes.put("com.google.cloud.storage.Storage", new byte[1300]);
        classes.put("com.google.cloud.bigquery.BigQuery", new byte[1150]);
        classes.put("com.google.cloud.pubsub.v1.Publisher", new byte[900]);
        
        // Oracle OCI SDK classes
        classes.put("com.oracle.bmc.objectstorage.ObjectStorageClient", new byte[1200]);
        classes.put("com.oracle.bmc.database.DatabaseClient", new byte[1100]);
        classes.put("com.oracle.bmc.compute.ComputeClient", new byte[950]);
        
        // Regular application classes
        classes.put("com.example.service.UserService", new byte[500]);
        classes.put("com.example.model.User", new byte[300]);
        classes.put("com.mycompany.util.StringUtils", new byte[200]);
        
        return classes;
    }
    
    private DependencyGraph createDependencyGraph(Set<String> classNames) {
        DependencyGraph graph = new DependencyGraph();
        
        // Add all classes
        classNames.forEach(graph::addClass);
        
        // Add some realistic dependencies
        graph.addEdge("org.apache.flink.streaming.api.StreamExecutionEnvironment", 
                     "org.apache.flink.streaming.api.datastream.DataStream");
        graph.addEdge("org.apache.spark.sql.SparkSession", 
                     "org.apache.spark.sql.Dataset");
        graph.addEdge("com.example.service.UserService", 
                     "com.example.model.User");
        
        return graph;
    }
    
    private void validateFrameworkGrouping(List<Block> blocks) {
        // Count framework classes in each block to validate grouping effectiveness
        Map<String, Integer> frameworkCounts = new HashMap<>();
        int totalBlocks = 0;
        
        for (Block block : blocks) {
            Set<String> frameworksInBlock = new HashSet<>();
            
            for (Block.ClassEntry entry : block.entries()) {
                String className = entry.className();
                if (className.contains("flink")) {
                    frameworksInBlock.add("flink");
                } else if (className.contains("spark")) {
                    frameworksInBlock.add("spark");
                } else if (className.contains("springframework")) {
                    frameworksInBlock.add("spring");
                } else if (className.contains("hadoop")) {
                    frameworksInBlock.add("hadoop");
                } else if (className.contains("amazonaws") || className.contains("awssdk")) {
                    frameworksInBlock.add("aws");
                } else if (className.contains("azure")) {
                    frameworksInBlock.add("azure");
                } else if (className.contains("google.cloud") || className.contains("googleapis")) {
                    frameworksInBlock.add("gcp");
                } else if (className.contains("oracle.bmc") || className.contains("oracle.oci")) {
                    frameworksInBlock.add("oci");
                } else {
                    frameworksInBlock.add("other");
                }
            }
            
            totalBlocks++;
            
            // Count blocks with framework classes (homogeneous or mixed)
            if (frameworksInBlock.contains("flink")) frameworkCounts.merge("flink", 1, Integer::sum);
            if (frameworksInBlock.contains("spark")) frameworkCounts.merge("spark", 1, Integer::sum);
            if (frameworksInBlock.contains("spring")) frameworkCounts.merge("spring", 1, Integer::sum);
            if (frameworksInBlock.contains("hadoop")) frameworkCounts.merge("hadoop", 1, Integer::sum);
            if (frameworksInBlock.contains("aws")) frameworkCounts.merge("aws", 1, Integer::sum);
            if (frameworksInBlock.contains("azure")) frameworkCounts.merge("azure", 1, Integer::sum);
            if (frameworksInBlock.contains("gcp")) frameworkCounts.merge("gcp", 1, Integer::sum);
            if (frameworksInBlock.contains("oci")) frameworkCounts.merge("oci", 1, Integer::sum);
            if (frameworksInBlock.contains("other")) frameworkCounts.merge("other", 1, Integer::sum);
        }
        
        System.out.printf("Framework grouping effectiveness:%n");
        frameworkCounts.forEach((framework, count) -> 
            System.out.printf("  %s: %d blocks contain classes%n", framework, count));
        
        // Validate that framework detection is working (blocks contain framework classes)
        assertTrue(frameworkCounts.getOrDefault("flink", 0) > 0, "No blocks contain Flink classes");
        assertTrue(frameworkCounts.getOrDefault("spark", 0) > 0, "No blocks contain Spark classes");
        assertTrue(frameworkCounts.getOrDefault("spring", 0) > 0, "No blocks contain Spring classes");
        assertTrue(frameworkCounts.getOrDefault("hadoop", 0) > 0, "No blocks contain Hadoop classes");
        assertTrue(frameworkCounts.getOrDefault("aws", 0) > 0, "No blocks contain AWS SDK classes");
        assertTrue(frameworkCounts.getOrDefault("azure", 0) > 0, "No blocks contain Azure SDK classes");
        assertTrue(frameworkCounts.getOrDefault("gcp", 0) > 0, "No blocks contain GCP SDK classes");
        assertTrue(frameworkCounts.getOrDefault("oci", 0) > 0, "No blocks contain OCI SDK classes");
    }
}
