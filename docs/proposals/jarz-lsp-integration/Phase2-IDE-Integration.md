# Phase 2: IDE Integration

**User Experience: IDE Plugin Development**

## Objective

Develop IDE plugins for VS Code, IntelliJ IDEA, and Eclipse that provide seamless JARZ dependency management through intuitive user interfaces and automated workflows.

## Target IDEs

### Primary Targets
1. **VS Code** - Largest Java developer base, LSP-native
2. **IntelliJ IDEA** - Premium Java IDE, advanced features
3. **Eclipse IDE** - Traditional Java IDE, JDT integration

### Secondary Targets
4. **Neovim** - Developer productivity focus
5. **Emacs** - LSP integration via lsp-java

## VS Code Extension

### Core Features
```typescript
// Extension activation
export function activate(context: vscode.ExtensionContext) {
    // Register JARZ dependency commands
    registerCommands(context);
    
    // Initialize JARZ language client
    initializeJarzLanguageClient(context);
    
    // Setup dependency tree view
    setupDependencyTreeView(context);
}

function registerCommands(context: vscode.ExtensionContext) {
    const commands = [
        vscode.commands.registerCommand('jarz.addDependency', addDependency),
        vscode.commands.registerCommand('jarz.removeDependency', removeDependency),
        vscode.commands.registerCommand('jarz.refreshDependencies', refreshDependencies),
        vscode.commands.registerCommand('jarz.searchMaven', searchMavenCentral)
    ];
    
    commands.forEach(cmd => context.subscriptions.push(cmd));
}
```

### Dependency Management UI
```typescript
class JarzDependencyProvider implements vscode.TreeDataProvider<Dependency> {
    
    getChildren(element?: Dependency): Dependency[] {
        if (!element) {
            return this.loadProjectDependencies();
        }
        return element.transitiveDependencies;
    }
    
    getTreeItem(element: Dependency): vscode.TreeItem {
        return {
            label: `${element.artifactId} ${element.version}`,
            tooltip: `${element.groupId}:${element.artifactId}:${element.version}`,
            contextValue: 'jarz-dependency',
            iconPath: new vscode.ThemeIcon('package')
        };
    }
}
```

### Configuration Schema
```json
{
  "contributes": {
    "configuration": {
      "title": "JARZ",
      "properties": {
        "jarz.cdnUrl": {
          "type": "string",
          "default": "https://cdn.jarz.io",
          "description": "JARZ CDN endpoint for dependency streaming"
        },
        "jarz.cacheSize": {
          "type": "string",
          "default": "100MB",
          "description": "Maximum local cache size for JARZ classes"
        },
        "jarz.offlineMode": {
          "type": "boolean",
          "default": false,
          "description": "Enable offline mode using cached dependencies"
        }
      }
    },
    "commands": [
      {
        "command": "jarz.addDependency",
        "title": "Add JARZ Dependency",
        "icon": "$(add)"
      }
    ],
    "views": {
      "explorer": [
        {
          "id": "jarzDependencies",
          "name": "JARZ Dependencies",
          "when": "jarz.hasJarzProject"
        }
      ]
    }
  }
}
```

## IntelliJ IDEA Plugin

### Plugin Architecture
```kotlin
class JarzPlugin : DumbAware, ApplicationComponent {
    
    override fun initComponent() {
        // Register JARZ project service
        ServiceManager.getService(JarzProjectService::class.java)
        
        // Setup dependency synchronization
        setupDependencySync()
    }
}

class JarzProjectService : ProjectService {
    
    fun addDependency(coordinates: String): Boolean {
        val dependency = parseMavenCoordinates(coordinates)
        return streamJarzDependency(dependency)
    }
    
    private fun streamJarzDependency(dependency: MavenDependency): Boolean {
        val jarzUrl = buildJarzUrl(dependency)
        val classLoader = JarzStreamingClassLoader(jarzUrl)
        
        // Register with IntelliJ's module system
        ModuleRootManager.getInstance(module).addOrderEntry(
            JarzLibraryOrderEntry(dependency, classLoader)
        )
        
        return true
    }
}
```

### UI Integration
```kotlin
class JarzDependencyDialog : DialogWrapper(true) {
    
    private val searchField = JBTextField()
    private val resultsTable = JBTable()
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Search Maven Central:") {
                cell(searchField)
                button("Search") { searchDependencies() }
            }
            row {
                scrollPane(resultsTable)
            }
        }
    }
    
    private fun searchDependencies() {
        val query = searchField.text
        val results = MavenCentralAPI.search(query)
        
        resultsTable.model = DependencyTableModel(results)
    }
}
```

## Eclipse IDE Plugin

### Plugin Integration
```java
public class JarzEclipsePlugin extends AbstractUIPlugin {
    
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        
        // Register JARZ nature and builder
        registerProjectNature();
        
        // Setup classpath container
        setupClasspathContainer();
        
        // Initialize UI components
        initializeUI();
    }
    
    private void setupClasspathContainer() {
        JavaCore.setClasspathContainerInitializer(
            JarzClasspathContainer.CONTAINER_ID,
            new JarzClasspathContainerInitializer()
        );
    }
}
```

### Project Configuration
```java
public class JarzProjectPropertyPage extends PropertyPage {
    
    private TableViewer dependencyViewer;
    private Button addButton, removeButton;
    
    @Override
    protected Control createContents(Composite parent) {
        Composite composite = new Composite(parent, SWT.NONE);
        
        // Dependency table
        dependencyViewer = new TableViewer(composite, SWT.BORDER | SWT.FULL_SELECTION);
        dependencyViewer.setContentProvider(new JarzDependencyContentProvider());
        dependencyViewer.setLabelProvider(new JarzDependencyLabelProvider());
        
        // Action buttons
        addButton = new Button(composite, SWT.PUSH);
        addButton.setText("Add Dependency");
        addButton.addSelectionListener(new AddDependencyListener());
        
        return composite;
    }
}
```

## Implementation Timeline

### Month 1: VS Code Extension
- [ ] **Week 1**: Basic extension structure and LSP integration
- [ ] **Week 2**: Dependency management commands and UI
- [ ] **Week 3**: Configuration and settings integration
- [ ] **Week 4**: Testing, documentation, and marketplace submission

### Month 2: IntelliJ IDEA Plugin
- [ ] **Week 1**: Plugin architecture and service integration
- [ ] **Week 2**: Module system integration and ClassLoader management
- [ ] **Week 3**: UI components and dependency dialogs
- [ ] **Week 4**: Testing, documentation, and JetBrains marketplace submission

### Month 3: Eclipse IDE Plugin
- [ ] **Week 1**: Plugin framework and project nature integration
- [ ] **Week 2**: Classpath container and builder integration
- [ ] **Week 3**: Property pages and UI components
- [ ] **Week 4**: Testing, documentation, and Eclipse marketplace submission

## User Experience Design

### Dependency Addition Workflow
1. **Command Palette**: `Ctrl+Shift+P` → "Add JARZ Dependency"
2. **Search Interface**: Type Maven coordinates or search terms
3. **Version Selection**: Choose from available versions
4. **Instant Integration**: Dependency streams immediately, no download wait
5. **Code Completion**: Classes available instantly in editor

### Visual Indicators
- **Streaming Status**: Progress indicator for active streams
- **Cache Status**: Icon showing cached vs streaming dependencies
- **Version Conflicts**: Warning indicators for version mismatches
- **Network Status**: Offline/online mode indicators

### Performance Monitoring
```typescript
class JarzPerformanceMonitor {
    
    trackDependencyLoad(dependency: string, loadTime: number) {
        // Track loading performance
        telemetry.track('dependency.load', {
            dependency,
            loadTime,
            cacheHit: loadTime < 100
        });
    }
    
    showPerformanceStats() {
        // Display performance statistics in status bar
        const stats = this.calculateStats();
        vscode.window.setStatusBarMessage(
            `JARZ: ${stats.totalDependencies} deps, ${stats.cacheHitRate}% cached`
        );
    }
}
```

## Configuration Management

### Project-Level Configuration
```json
// .vscode/jarz.json
{
  "dependencies": [
    {
      "coordinates": "org.springframework:spring-core:6.0.0",
      "scope": "compile",
      "source": "maven-central"
    }
  ],
  "repositories": [
    {
      "id": "maven-central",
      "url": "https://cdn.jarz.io/maven2",
      "type": "jarz"
    }
  ],
  "settings": {
    "autoRefresh": true,
    "offlineMode": false,
    "cacheTimeout": "24h"
  }
}
```

### Workspace-Level Settings
```json
// settings.json
{
  "jarz.global.cdnUrl": "https://cdn.jarz.io",
  "jarz.global.cacheDirectory": "${workspaceFolder}/.jarz-cache",
  "jarz.global.maxCacheSize": "500MB",
  "jarz.global.networkTimeout": "30s"
}
```

## Testing Strategy

### Automated Testing
- **Unit Tests**: Command handlers, dependency resolution
- **Integration Tests**: LSP communication, ClassLoader integration
- **UI Tests**: Extension activation, command execution
- **Performance Tests**: Load times, memory usage, network efficiency

### Manual Testing
- **User Workflows**: Dependency addition, removal, updates
- **Error Scenarios**: Network failures, invalid dependencies
- **Performance Validation**: Large projects, many dependencies
- **Cross-Platform**: Windows, macOS, Linux compatibility

## Distribution Strategy

### VS Code Marketplace
- **Extension ID**: `plasticity-cloud.jarz`
- **Category**: Programming Languages
- **Keywords**: java, dependencies, streaming, performance
- **Pricing**: Free with premium CDN options

### JetBrains Marketplace
- **Plugin ID**: `com.plasticity.jarz`
- **Compatibility**: IntelliJ IDEA 2023.1+
- **Distribution**: Free plugin with enterprise features

### Eclipse Marketplace
- **Solution ID**: JARZ Streaming Dependencies
- **Category**: Development Tools
- **License**: Apache 2.0

## Success Metrics

### Adoption Metrics
- **Downloads**: 10K+ in first month (VS Code)
- **Active Users**: 5K+ daily active users
- **Retention**: 80%+ weekly retention rate
- **Ratings**: 4.5+ stars average rating

### Performance Metrics
- **Setup Time**: <5 seconds vs 2+ minutes traditional
- **Memory Usage**: <50MB vs 500MB+ traditional
- **Network Efficiency**: 90% bandwidth reduction
- **User Satisfaction**: 90%+ positive feedback

## Risk Mitigation

### Technical Risks
- **IDE API Changes**: Maintain compatibility layers for multiple versions
- **Performance Issues**: Extensive profiling and optimization
- **Network Dependencies**: Robust offline mode and caching

### User Adoption Risks
- **Learning Curve**: Comprehensive documentation and tutorials
- **Migration Complexity**: Automated migration tools from existing projects
- **Enterprise Concerns**: Security audits and compliance documentation

---

**Phase Duration**: 12 weeks  
**Team Size**: 3-4 developers (1 per major IDE)  
**Dependencies**: Phase 1 JDT-LS Extension  
**Next Phase**: [Phase 3: CDN Infrastructure](Phase3-CDN-Infrastructure.md)
