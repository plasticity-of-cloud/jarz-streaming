package net.jarz.streaming.framework;

/**
 * Interface for detecting framework modules from class names.
 * 
 * <p>Implementations should be registered via ServiceLoader to enable
 * automatic discovery by the FrameworkDetectorRegistry.
 * 
 * @since 1.0
 * @author Plasticity.Cloud
 */
public interface FrameworkDetector {
    
    /**
     * Detects the framework module for the given class name.
     * 
     * @param className the fully qualified class name
     * @return the framework module identifier (e.g., "flink-streaming", "spark-sql")
     */
    String detectModule(String className);
    
    /**
     * Checks if this detector can handle the given class name.
     * 
     * @param className the fully qualified class name
     * @return true if this detector can process the class name
     */
    boolean canHandle(String className);
    
    /**
     * Returns the priority of this detector for conflict resolution.
     * Higher priority detectors are checked first.
     * 
     * @return priority value (higher = more priority)
     */
    int priority();
}
