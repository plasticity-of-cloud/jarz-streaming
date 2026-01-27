package net.jarz.streaming.framework;

import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Registry for framework detectors using ServiceLoader discovery.
 * 
 * <p>Automatically discovers and loads all FrameworkDetector implementations
 * from the classpath, sorted by priority (highest first).
 * 
 * @since 1.0
 * @author Plasticity.Cloud
 */
public class FrameworkDetectorRegistry {
    
    private final List<FrameworkDetector> detectors;
    
    /**
     * Creates a new registry with automatic ServiceLoader discovery.
     */
    public FrameworkDetectorRegistry() {
        this.detectors = ServiceLoader.load(FrameworkDetector.class)
            .stream()
            .map(ServiceLoader.Provider::get)
            .sorted((a, b) -> Integer.compare(b.priority(), a.priority()))
            .collect(Collectors.toList());
    }
    
    /**
     * Detects the framework module for the given class name.
     * 
     * <p>Tries each detector in priority order until one can handle the class.
     * If no detector can handle it, returns the package prefix as fallback.
     * 
     * @param className the fully qualified class name
     * @return the framework module identifier or package prefix
     */
    public String detectFramework(String className) {
        return detectors.stream()
            .filter(d -> d.canHandle(className))
            .findFirst()
            .map(d -> d.detectModule(className))
            .orElse(getPackagePrefix(className));
    }
    
    /**
     * Extracts package prefix from class name as fallback.
     */
    private String getPackagePrefix(String className) {
        int lastDot = className.lastIndexOf('.');
        if (lastDot > 0) {
            String packageName = className.substring(0, lastDot);
            int firstDot = packageName.indexOf('.');
            return firstDot > 0 ? packageName.substring(0, firstDot) : packageName;
        }
        return "default";
    }
}
