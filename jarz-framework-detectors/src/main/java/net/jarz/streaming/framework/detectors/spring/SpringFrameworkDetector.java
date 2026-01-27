package net.jarz.streaming.framework.detectors.spring;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Spring Framework classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class SpringFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("boot")) return "spring-boot";
        if (className.contains("web")) return "spring-web";
        if (className.contains("data")) return "spring-data";
        if (className.contains("security")) return "spring-security";
        if (className.contains("context")) return "spring-context";
        return "spring-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("springframework");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
