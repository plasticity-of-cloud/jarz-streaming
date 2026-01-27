package net.jarz.streaming.framework.detectors.azure;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Azure Java SDK classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class AzureSdkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("storage")) return "azure-storage";
        if (className.contains("keyvault")) return "azure-keyvault";
        if (className.contains("cosmos")) return "azure-cosmos";
        if (className.contains("servicebus")) return "azure-servicebus";
        if (className.contains("eventhubs")) return "azure-eventhubs";
        if (className.contains("identity")) return "azure-identity";
        if (className.contains("resourcemanager")) return "azure-resourcemanager";
        return "azure-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("azure");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
