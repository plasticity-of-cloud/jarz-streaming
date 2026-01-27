package net.jarz.streaming.framework.detectors.flink;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Apache Flink classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class FlinkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("streaming")) return "flink-streaming";
        if (className.contains("table")) return "flink-table";
        if (className.contains("connector")) return "flink-connector";
        if (className.contains("runtime")) return "flink-runtime";
        return "flink-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("flink");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
