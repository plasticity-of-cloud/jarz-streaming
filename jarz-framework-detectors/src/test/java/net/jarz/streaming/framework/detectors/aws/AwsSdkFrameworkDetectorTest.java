package net.jarz.streaming.framework.detectors.aws;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AwsSdkFrameworkDetector.
 */
class AwsSdkFrameworkDetectorTest {
    
    private final AwsSdkFrameworkDetector detector = new AwsSdkFrameworkDetector();
    
    @Test
    void testCanHandle() {
        // AWS SDK v1
        assertTrue(detector.canHandle("com.amazonaws.services.s3.AmazonS3Client"));
        assertTrue(detector.canHandle("com.amazonaws.services.ec2.AmazonEC2Client"));
        
        // AWS SDK v2
        assertTrue(detector.canHandle("software.amazon.awssdk.services.s3.S3Client"));
        assertTrue(detector.canHandle("software.amazon.awssdk.services.lambda.LambdaClient"));
        
        // Non-AWS classes
        assertFalse(detector.canHandle("org.apache.spark.SparkContext"));
        assertFalse(detector.canHandle("com.example.MyClass"));
    }
    
    @Test
    void testDetectModule() {
        // S3 service
        assertEquals("aws-s3", detector.detectModule("com.amazonaws.services.s3.AmazonS3Client"));
        assertEquals("aws-s3", detector.detectModule("software.amazon.awssdk.services.s3.S3Client"));
        
        // EC2 service
        assertEquals("aws-ec2", detector.detectModule("com.amazonaws.services.ec2.AmazonEC2Client"));
        assertEquals("aws-ec2", detector.detectModule("software.amazon.awssdk.services.ec2.Ec2Client"));
        
        // Lambda service
        assertEquals("aws-lambda", detector.detectModule("com.amazonaws.services.lambda.AWSLambdaClient"));
        assertEquals("aws-lambda", detector.detectModule("software.amazon.awssdk.services.lambda.LambdaClient"));
        
        // DynamoDB service
        assertEquals("aws-dynamodb", detector.detectModule("com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient"));
        assertEquals("aws-dynamodb", detector.detectModule("software.amazon.awssdk.services.dynamodb.DynamoDbClient"));
        
        // Core/unknown service
        assertEquals("aws-core", detector.detectModule("com.amazonaws.AmazonWebServiceClient"));
        assertEquals("aws-core", detector.detectModule("software.amazon.awssdk.core.SdkClient"));
    }
    
    @Test
    void testPriority() {
        assertEquals(100, detector.priority());
    }
}
