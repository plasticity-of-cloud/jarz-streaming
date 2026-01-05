# Python Wheel Block Compression Proposal

**Concept**: Apply JARZ v2 block-based compression pattern to Python wheels for improved distribution efficiency.

## Problem Statement

Current Python wheels use ZIP compression, which has limitations:
- **Poor compression ratio**: DEFLATE achieves only 60-70% compression on Python bytecode
- **Slow decompression**: 3-5x slower than modern algorithms like ZSTD
- **No streaming support**: Must download entire wheel for pip installs
- **No cross-file compression**: Each file compressed independently

## Proposed Solution: PYWZ (Python Wheel ZSTD)

### Block-Based Architecture

```
.pywz Format:
┌─────────────────────────────────────────────────────────────────┐
│ Header → Dictionary → Block 0 → Block 1 → ... → Index → Footer  │
│                                                                  │
│ Block Assignment Strategies:                                     │
│ - Block 0: Core package modules (.py/.pyc)                      │
│ - Block 1: Third-party dependencies                             │
│ - Block 2: Native libraries (.so/.dll)                          │
│ - Block 3: Data files (JSON, CSV, etc.)                         │
│ - Block 4: Documentation/metadata                               │
└─────────────────────────────────────────────────────────────────┘
```

### Import-Aware Block Assignment

```python
# Analyze import relationships using AST
import ast

def analyze_imports(python_files):
    """Build import dependency graph for intelligent block assignment"""
    graph = DependencyGraph()
    for file in python_files:
        tree = ast.parse(file.read())
        imports = extract_imports(tree)
        graph.add_dependencies(file.name, imports)
    return graph

def assign_blocks(files, graph):
    """Group frequently co-imported modules into same blocks"""
    assigner = BlockAssigner(target_size=512*1024)  # 512KB blocks
    return assigner.assign_blocks(files, graph)
```

## Expected Benefits

### Compression Improvements
- **25-40% size reduction** over current ZIP-based wheels
- **3-5x faster decompression** than DEFLATE
- **Cross-file compression** for related modules with shared patterns

### Distribution Efficiency
- **PyPI bandwidth savings**: Faster pip installs
- **Container image optimization**: Smaller Python base images  
- **Edge deployment**: More packages fit in constrained environments

### Streaming Installation
```bash
# Install only needed submodules
pip install numpy --modules="core,linalg"
# Skip documentation and examples
pip install tensorflow --exclude="docs,examples"
```

## Real-World Impact Estimates

| Package | Current Size | Estimated PYWZ | Savings |
|---------|-------------|----------------|---------|
| numpy | 15 MB | 10-11 MB | 27-33% |
| tensorflow | 500 MB | 350-375 MB | 25-30% |
| pandas | 40 MB | 28-32 MB | 20-30% |

**Ecosystem Impact**: 20-30% reduction across PyPI would save petabytes of bandwidth annually.

## Implementation Challenges

### Ecosystem Integration
- **pip/setuptools compatibility**: Requires wheel format updates
- **Import system**: Python expects individual files, need transparent decompression
- **Platform support**: Cross-platform wheel compatibility
- **Backwards compatibility**: Gradual migration path needed

### Technical Considerations
- **Import performance**: Block decompression overhead on first import
- **Memory management**: Intelligent caching of decompressed blocks
- **Build tooling**: Integration with existing wheel build processes

## Migration Strategy

### Phase 1: Proof of Concept
- Implement PYWZ format specification
- Create conversion tools: `.whl` → `.pywz`
- Benchmark compression ratios on popular packages

### Phase 2: Tooling Integration
- pip plugin for PYWZ support
- setuptools/wheel integration
- PyPI infrastructure updates

### Phase 3: Ecosystem Adoption
- Community feedback and iteration
- Performance optimization
- Gradual rollout to major packages

## Technical Specification

### File Format
```
PYWZ Header (32 bytes):
- Magic: "PYWZ" (4 bytes)
- Version: 2 (4 bytes)  
- Block count (4 bytes)
- Dictionary size (4 bytes)
- Index offset (8 bytes)
- Reserved (8 bytes)

Block Structure:
- Compressed size (4 bytes)
- Uncompressed size (4 bytes)
- Entry count (4 bytes)
- ZSTD compressed data
```

### Block Assignment Algorithm
```python
class PythonBlockAssigner:
    def assign_blocks(self, files, import_graph):
        """Assign Python files to blocks based on import relationships"""
        blocks = []
        
        # Block 0: Core modules (most imported)
        core_modules = import_graph.get_most_imported(threshold=0.3)
        blocks.append(Block(0, core_modules))
        
        # Block 1: Related modules (import each other)
        clusters = import_graph.find_clusters()
        for cluster in clusters:
            blocks.append(Block(len(blocks), cluster))
            
        # Block 2: Standalone files
        remaining = files - assigned_files
        blocks.append(Block(len(blocks), remaining))
        
        return blocks
```

## Next Steps

1. **Prototype implementation** based on JARZ v2 codebase
2. **Benchmark against popular wheels** (numpy, requests, django)
3. **Community engagement** with Python packaging team
4. **PEP proposal** for wheel format extension

---

**Status**: Proposal  
**Created**: 2025-12-24  
**Based on**: JARZ v2 block compression success (27.4% improvement over JAR)
