package net.jarz.streaming.framework;

/**
 * Mock framework detector for testing.
 */
public class MockFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("test")) {
            return "test-module";
        }
        return "mock-default";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("test") || className.contains("mock");
    }
    
    @Override
    public int priority() {
        return 50;
    }
}
