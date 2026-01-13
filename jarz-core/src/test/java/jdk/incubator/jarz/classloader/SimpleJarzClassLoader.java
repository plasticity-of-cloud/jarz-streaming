package jdk.incubator.jarz.classloader;

import jdk.incubator.jarz.v2.FileJarzDataProvider;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple concrete implementation of JarzClassLoader for testing purposes.
 * This class provides basic JARZ loading without Main-Class requirements.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class SimpleJarzClassLoader extends JarzClassLoader {
    
    private final Path jarzPath;
    private final Path baseDirectory;
    
    public SimpleJarzClassLoader(Path jarzFile) throws IOException {
        this(jarzFile, null, getSystemClassLoader());
    }
    
    public SimpleJarzClassLoader(Path jarzFile, Path bundleIndexPath) throws IOException {
        this(jarzFile, bundleIndexPath, getSystemClassLoader());
    }
    
    public SimpleJarzClassLoader(Path jarzFile, Path bundleIndexPath, ClassLoader parent) throws IOException {
        super(new FileJarzDataProvider(jarzFile), parent, bundleIndexPath);
        this.jarzPath = jarzFile;
        this.baseDirectory = jarzFile.getParent();
    }
    
    @Override
    protected String getCurrentJarzUrl() {
        return jarzPath.getFileName().toString();
    }
    
    @Override
    protected JarzClassLoader createChildLoader(String jarzUrl) throws IOException {
        Path childJarzPath = baseDirectory.resolve(jarzUrl);
        return new SimpleJarzClassLoader(childJarzPath, null); // No bundle index for children
    }
}
