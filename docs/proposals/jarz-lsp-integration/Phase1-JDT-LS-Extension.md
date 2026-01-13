# Phase 1: JDT-LS Extension Development

**Foundation: Eclipse JDT Language Server Integration**

## Objective

Develop Eclipse JDT Language Server extension that replaces traditional JAR ClassLoaders with JARZ streaming ClassLoaders, enabling on-demand class loading from compressed archives.

## Technical Architecture

### Core Components

#### 1. JARZ ClassLoader Factory
```java
public class JarzClassLoaderFactory {
    
    public static URLClassLoader createProjectClassLoader(IJavaProject project) {
        String[] classPathEntries = JavaRuntime.computeDefaultRuntimeClassPath(project);
        List<URL> urlList = new ArrayList<>();
        
        for (String entry : classPathEntries) {
            if (entry.endsWith(".jarz")) {
                urlList.add(new JarzStreamingURL(entry));
            } else {
                urlList.add(Paths.get(entry).toUri().toURL());
            }
        }
        
        return new JarzLanguageServerClassLoader(
            urlList.toArray(new URL[0]), 
            project.getClass().getClassLoader()
        );
    }
}
```

#### 2. JDT-LS Extension Plugin
```java
public class JarzLanguageServerPlugin extends AbstractUIPlugin {
    
    @Override
    public void start(BundleContext context) throws Exception {
        // Register JARZ ClassLoader factory
        ClassLoaderRegistry.register("jarz", JarzClassLoaderFactory.class);
        
        // Hook into classpath resolution
        JavaCore.addClasspathVariableInitializer("JARZ_STREAMING", 
                                                new JarzClasspathInitializer());
        
        // Register command handlers
        registerCommandHandlers();
    }
    
    private void registerCommandHandlers() {
        CommandRegistry.register("jarz.streamDependency", new StreamDependencyHandler());
        CommandRegistry.register("jarz.refreshClasspath", new RefreshClasspathHandler());
    }
}
```

#### 3. Streaming URL Handler
```java
public class JarzStreamingURL extends URL {
    
    public JarzStreamingURL(String jarzPath) throws MalformedURLException {
        super("jarz", null, -1, jarzPath, new JarzURLStreamHandler());
    }
    
    private static class JarzURLStreamHandler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL url) throws IOException {
            return new JarzURLConnection(url);
        }
    }
}
```

### Integration Points

#### 1. Project Classpath Initialization
```java
public class JarzClasspathInitializer extends ClasspathVariableInitializer {
    
    @Override
    public void initialize(String variable) {
        if ("JARZ_STREAMING".equals(variable)) {
            // Initialize JARZ streaming classpath
            IPath jarzPath = detectJarzDependencies();
            JavaCore.setClasspathVariable("JARZ_STREAMING", jarzPath, null);
        }
    }
    
    private IPath detectJarzDependencies() {
        // Scan for .jarz files in project
        // Return path to JARZ streaming root
    }
}
```

#### 2. Command Handler Integration
```java
@Component
public class JarzCommandHandler implements DelegateCommandHandler {
    
    @Override
    public Object executeCommand(String commandId, List<Object> arguments, 
                               IProgressMonitor monitor) throws Exception {
        
        switch (commandId) {
            case "jarz.addDependency":
                return addJarzDependency((String) arguments.get(0));
            case "jarz.removeDependency":
                return removeJarzDependency((String) arguments.get(0));
            case "jarz.listDependencies":
                return listJarzDependencies();
            default:
                return null;
        }
    }
}
```

## Implementation Plan

### Milestone 1: Core ClassLoader (Week 1-2)
- [ ] Implement `JarzLanguageServerClassLoader`
- [ ] Create `JarzStreamingURL` and URL handler
- [ ] Integrate with existing JARZ core library
- [ ] Unit tests for ClassLoader functionality

### Milestone 2: JDT-LS Integration (Week 3-4)
- [ ] Develop JDT-LS plugin bundle
- [ ] Implement classpath variable initializer
- [ ] Register extension points and command handlers
- [ ] Integration tests with sample Java project

### Milestone 3: Dependency Management (Week 5-6)
- [ ] Implement dependency resolution commands
- [ ] Create project configuration management
- [ ] Add dependency caching mechanisms
- [ ] Performance optimization and memory management

### Milestone 4: Testing & Validation (Week 7-8)
- [ ] Comprehensive test suite
- [ ] Performance benchmarking
- [ ] Compatibility testing with popular libraries
- [ ] Documentation and developer guides

## Technical Specifications

### ClassLoader Hierarchy
```
JarzLanguageServerClassLoader
├── Parent: Project ClassLoader (JDT)
├── URLs: Mixed JAR and JARZ streaming URLs
├── Caching: LRU cache for loaded classes
└── Security: Inherited protection domains
```

### Memory Management
- **Class Cache**: 10MB per project (configurable)
- **Resource Cache**: 5MB per project (configurable)
- **Streaming Buffer**: 1MB per active stream
- **Total Overhead**: <20MB per project vs 500MB+ traditional

### Performance Targets
- **Class Loading**: <10ms per class (vs 50ms+ JAR extraction)
- **Project Setup**: <5 seconds (vs 2+ minutes dependency download)
- **Memory Usage**: <50MB per project (vs 500MB+ traditional)
- **Network Efficiency**: 90% reduction in bandwidth usage

## Configuration

### Plugin Configuration
```xml
<!-- plugin.xml -->
<extension point="org.eclipse.jdt.core.classpathVariableInitializer">
    <classpathVariableInitializer 
        variable="JARZ_STREAMING"
        class="com.plasticity.jarz.lsp.JarzClasspathInitializer"/>
</extension>

<extension point="org.eclipse.jdt.ls.core.delegateCommandHandler">
    <delegateCommandHandler 
        class="com.plasticity.jarz.lsp.JarzCommandHandler"/>
</extension>
```

### Project Configuration
```json
// .jarz-config.json
{
  "dependencies": [
    {
      "groupId": "org.springframework",
      "artifactId": "spring-core",
      "version": "6.0.0",
      "source": "https://cdn.jarz.io/maven2"
    }
  ],
  "caching": {
    "maxClassCacheSize": "10MB",
    "maxResourceCacheSize": "5MB",
    "offlineMode": false
  }
}
```

## Testing Strategy

### Unit Tests
- ClassLoader functionality
- URL stream handling
- Dependency resolution
- Cache management

### Integration Tests
- JDT-LS plugin loading
- Project classpath resolution
- Command execution
- Multi-project scenarios

### Performance Tests
- Class loading benchmarks
- Memory usage profiling
- Network efficiency measurement
- Concurrent access testing

## Deliverables

1. **JDT-LS Extension Bundle** (`jarz-jdtls-extension.jar`)
2. **Installation Guide** for JDT-LS integration
3. **API Documentation** for extension points
4. **Performance Report** with benchmarks
5. **Migration Guide** from traditional JAR dependencies

## Success Criteria

- [ ] Successfully loads classes from JARZ archives
- [ ] Integrates seamlessly with existing JDT-LS installations
- [ ] Achieves 10x performance improvement in project setup
- [ ] Maintains compatibility with existing Java projects
- [ ] Passes all integration tests with popular libraries

## Risk Mitigation

### Technical Risks
- **JDT-LS API Changes**: Pin to specific JDT-LS version, maintain compatibility layer
- **ClassLoader Conflicts**: Implement proper parent delegation and isolation
- **Performance Degradation**: Extensive benchmarking and optimization

### Integration Risks
- **Plugin Loading Issues**: Comprehensive testing across JDT-LS versions
- **Security Restrictions**: Implement proper security policies and permissions
- **Network Failures**: Robust offline fallback and retry mechanisms

---

**Phase Duration**: 8 weeks  
**Team Size**: 2-3 developers  
**Dependencies**: JARZ Core Library, Eclipse JDT-LS  
**Next Phase**: [Phase 2: IDE Integration](Phase2-IDE-Integration.md)
