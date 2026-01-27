package net.jarz.streaming.framework.detectors.spark;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Apache Spark classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class SparkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("sql")) return "spark-sql";
        if (className.contains("streaming")) return "spark-streaming";
        if (className.contains("mllib")) return "spark-mllib";
        if (className.contains("graphx")) return "spark-graphx";
        return "spark-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("spark");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
