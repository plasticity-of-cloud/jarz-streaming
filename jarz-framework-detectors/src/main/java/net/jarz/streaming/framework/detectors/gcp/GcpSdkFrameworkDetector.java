package net.jarz.streaming.framework.detectors.gcp;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Google Cloud Java SDK classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class GcpSdkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("storage")) return "gcp-storage";
        if (className.contains("bigquery")) return "gcp-bigquery";
        if (className.contains("pubsub")) return "gcp-pubsub";
        if (className.contains("firestore")) return "gcp-firestore";
        if (className.contains("compute")) return "gcp-compute";
        if (className.contains("functions")) return "gcp-functions";
        if (className.contains("logging")) return "gcp-logging";
        return "gcp-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("google.cloud") || className.contains("googleapis");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
