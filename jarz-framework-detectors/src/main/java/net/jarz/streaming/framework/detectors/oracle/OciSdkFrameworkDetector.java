package net.jarz.streaming.framework.detectors.oracle;

import net.jarz.streaming.framework.FrameworkDetector;

/**
 * Framework detector for Oracle Cloud Infrastructure (OCI) Java SDK classes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class OciSdkFrameworkDetector implements FrameworkDetector {
    
    @Override
    public String detectModule(String className) {
        if (className.contains("objectstorage")) return "oci-objectstorage";
        if (className.contains("database")) return "oci-database";
        if (className.contains("compute")) return "oci-compute";
        if (className.contains("identity")) return "oci-identity";
        if (className.contains("loadbalancer")) return "oci-loadbalancer";
        if (className.contains("networking") || className.contains("network")) return "oci-networking";
        if (className.contains("functions")) return "oci-functions";
        if (className.contains("containerengine")) return "oci-containerengine";
        return "oci-core";
    }
    
    @Override
    public boolean canHandle(String className) {
        return className.contains("oracle.bmc") || className.contains("com.oracle.oci");
    }
    
    @Override
    public int priority() {
        return 100; // High priority for specific framework
    }
}
