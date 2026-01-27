package net.jarz.streaming.framework.detectors.hadoop;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Apache Hadoop classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class HadoopFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("hdfs")) return "hadoop-hdfs";
        if (className.contains("mapreduce")) return "hadoop-mapreduce";
        if (className.contains("yarn")) return "hadoop-yarn";
        return "hadoop-common";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("hadoop");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
