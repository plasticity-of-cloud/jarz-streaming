package net.jarz.streaming.framework.detectors;

import net.jarz.streaming.framework.FrameworkDetectorRegistry;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for framework detectors with ServiceLoader.
 */
class FrameworkDetectorIntegrationTest {
    
    @Test
    void testServiceLoaderDiscovery() {
        FrameworkDetectorRegistry registry = new FrameworkDetectorRegistry();
        
        // Test Flink detection
        assertEquals("flink-streaming", registry.detectFramework("org.apache.flink.streaming.api.StreamExecutionEnvironment"));
        assertEquals("flink-table", registry.detectFramework("org.apache.flink.table.api.TableEnvironment"));
        
        // Test Spark detection
        assertEquals("spark-sql", registry.detectFramework("org.apache.spark.sql.SparkSession"));
        assertEquals("spark-core", registry.detectFramework("org.apache.spark.SparkContext"));
        
        // Test Spring detection
        assertEquals("spring-boot", registry.detectFramework("org.springframework.boot.SpringApplication"));
        assertEquals("spring-web", registry.detectFramework("org.springframework.web.bind.annotation.RestController"));
        
        // Test Hadoop detection
        assertEquals("hadoop-hdfs", registry.detectFramework("org.apache.hadoop.hdfs.DistributedFileSystem"));
        assertEquals("hadoop-mapreduce", registry.detectFramework("org.apache.hadoop.mapreduce.Job"));
        
        // Test Cloud SDK detection
        assertEquals("aws-s3", registry.detectFramework("com.amazonaws.services.s3.AmazonS3Client"));
        assertEquals("aws-lambda", registry.detectFramework("software.amazon.awssdk.services.lambda.LambdaClient"));
        assertEquals("azure-storage", registry.detectFramework("com.azure.storage.blob.BlobServiceClient"));
        assertEquals("azure-keyvault", registry.detectFramework("com.azure.keyvault.secrets.SecretClient"));
        assertEquals("gcp-storage", registry.detectFramework("com.google.cloud.storage.Storage"));
        assertEquals("gcp-bigquery", registry.detectFramework("com.google.cloud.bigquery.BigQuery"));
        assertEquals("oci-objectstorage", registry.detectFramework("com.oracle.bmc.objectstorage.ObjectStorageClient"));
        assertEquals("oci-database", registry.detectFramework("com.oracle.bmc.database.DatabaseClient"));
        
        // Test fallback to package prefix
        assertEquals("com", registry.detectFramework("com.example.MyClass"));
    }
}
