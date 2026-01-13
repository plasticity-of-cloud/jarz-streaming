# JARZ Testing with Kafka Bundle

## Objective
Test the enhanced JARZ implementation with a real-world Kafka bundle to validate:
- Enhanced ClassLoader hierarchy with bundle index support
- Manifest Class-Path processing (traditional and Java 9+ module system)
- JAR to JARZ conversion with proper manifest updates
- Multi-JARZ loading with O(1) class lookup performance

## Test Scenarios

### 1. Kafka Bundle Conversion
- [ ] Download Kafka distribution (latest stable version)
- [ ] Convert Kafka JARs to JARZ format using CLI tool
- [ ] Verify manifest Class-Path entries are updated (.jar → .jarz)
- [ ] Test module system attributes if present (Add-Exports, Add-Opens, Add-Reads)

### 2. Bundle Index Generation
- [ ] Generate bundle index for Kafka JARZ files
- [ ] Test O(1) class lookup performance vs sequential search
- [ ] Verify child ClassLoader creation and caching

### 3. ClassLoader Hierarchy Testing
- [ ] Test JarzApplicationClassLoader with Kafka main JARZ
- [ ] Verify proper delegation to child loaders for dependencies
- [ ] Test resource loading across multiple JARZ files

### 4. Performance Validation
- [ ] Compare startup time: JAR vs JARZ
- [ ] Measure class loading performance with bundle index
- [ ] Validate memory usage optimization (150KB → <5KB per ClassLoader)

### 5. Integration Testing
- [ ] Run Kafka server with JARZ ClassLoader
- [ ] Test producer/consumer functionality
- [ ] Verify all Kafka features work correctly with JARZ format

## Expected Outcomes
- Successful Kafka bundle conversion with proper manifest updates
- Functional Kafka server running on JARZ ClassLoaders
- Performance improvements in class loading and memory usage
- Validation of enhanced ClassLoader hierarchy implementation

## Success Criteria
- All Kafka JARs convert successfully to JARZ format
- Manifest processing correctly updates all JAR references
- Bundle index enables O(1) class lookup
- Kafka functionality remains intact with JARZ format
- Performance metrics show expected improvements

---
*Updated: 2026-01-12T23:19:00Z*
