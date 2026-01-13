package jdk.incubator.jarz.v2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Processes manifest files during JAR to JARZ conversion.
 * 
 * <p>Updates Class-Path entries to reference .jarz files instead of .jar files
 * to maintain proper classpath resolution in JARZ format.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class ManifestProcessor {
    
    /**
     * Processes manifest content, updating JAR references to JARZ in module-related attributes.
     * 
     * <p>Updates the following attributes:
     * <ul>
     * <li>Class-Path: Traditional classpath entries</li>
     * <li>Add-Exports: Module exports with JAR references</li>
     * <li>Add-Opens: Module opens with JAR references</li>
     * <li>Add-Reads: Module reads with JAR references</li>
     * </ul>
     * 
     * @param manifestData original manifest bytes
     * @return updated manifest bytes with .jarz references
     * @throws IOException if manifest processing fails
     */
    public static byte[] processManifest(byte[] manifestData) throws IOException {
        if (manifestData == null || manifestData.length == 0) {
            return manifestData;
        }
        
        // Parse the manifest
        Manifest manifest = new Manifest(new ByteArrayInputStream(manifestData));
        Attributes mainAttributes = manifest.getMainAttributes();
        boolean modified = false;
        
        // Update Class-Path attribute if present
        String classPath = mainAttributes.getValue("Class-Path");
        if (classPath != null && !classPath.trim().isEmpty()) {
            String updatedClassPath = updateJarReferences(classPath);
            if (!updatedClassPath.equals(classPath)) {
                mainAttributes.putValue("Class-Path", updatedClassPath);
                modified = true;
            }
        }
        
        // Update Java 9+ module system attributes
        String[] moduleAttributes = {"Add-Exports", "Add-Opens", "Add-Reads"};
        for (String attrName : moduleAttributes) {
            String attrValue = mainAttributes.getValue(attrName);
            if (attrValue != null && !attrValue.trim().isEmpty()) {
                String updatedValue = updateJarReferences(attrValue);
                if (!updatedValue.equals(attrValue)) {
                    mainAttributes.putValue(attrName, updatedValue);
                    modified = true;
                }
            }
        }
        
        if (modified) {
            // Write updated manifest back to bytes
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            manifest.write(out);
            return out.toByteArray();
        }
        
        // No changes needed
        return manifestData;
    }
    
    /**
     * Updates attribute values to replace .jar with .jarz extensions.
     * 
     * <p>Handles various formats:
     * <ul>
     * <li>Class-Path: space-separated JAR paths</li>
     * <li>Add-Exports: module/package=target-module format</li>
     * <li>Add-Opens: module/package=target-module format</li>
     * <li>Add-Reads: source-module=target-module format</li>
     * </ul>
     * 
     * @param attributeValue original attribute value
     * @return updated attribute value with .jarz extensions
     */
    private static String updateJarReferences(String attributeValue) {
        // Split by whitespace and process each entry
        String[] entries = attributeValue.trim().split("\\s+");
        StringBuilder updated = new StringBuilder();
        
        for (int i = 0; i < entries.length; i++) {
            String entry = entries[i];
            
            // Replace .jar extension with .jarz in any part of the entry
            // This handles both simple paths and complex module expressions
            entry = entry.replaceAll("\\.jar\\b", ".jarz");
            
            updated.append(entry);
            if (i < entries.length - 1) {
                updated.append(" ");
            }
        }
        
        return updated.toString();
    }
    
    /**
     * Checks if the given entry is a manifest file that should be processed.
     * 
     * @param entryName the entry name
     * @return true if this is a manifest file
     */
    public static boolean isManifestFile(String entryName) {
        return "META-INF/MANIFEST.MF".equals(entryName);
    }
}
