package net.jarz.streaming.framework.detectors.aws;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for AWS Java SDK classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class AwsSdkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("s3")) return "aws-s3";
        if (className.contains("ec2")) return "aws-ec2";
        if (className.contains("lambda")) return "aws-lambda";
        if (className.contains("dynamodb")) return "aws-dynamodb";
        if (className.contains("rds")) return "aws-rds";
        if (className.contains("iam")) return "aws-iam";
        if (className.contains("cloudformation")) return "aws-cloudformation";
        if (className.contains("sns")) return "aws-sns";
        if (className.contains("sqs")) return "aws-sqs";
        return "aws-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("amazonaws") || className.contains("software.amazon.awssdk");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
