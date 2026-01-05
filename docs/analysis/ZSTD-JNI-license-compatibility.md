# zstd-jni License Compatibility Analysis

**Document**: License compatibility assessment for JDK integration  
**Date**: January 2026  
**Status**: Analysis Complete  

## Executive Summary

**zstd-jni library uses BSD 2-Clause License, which is FULLY COMPATIBLE with OpenJDK's GPLv2+Classpath Exception and our JARZ project for JDK integration purposes.**

## License Details

### zstd-jni Library
- **License**: BSD 2-Clause License ("Simplified BSD License")
- **Repository**: https://github.com/luben/zstd-jni
- **Version**: 1.5.7-6 (current in JARZ project)
- **License File**: https://github.com/luben/zstd-jni/blob/master/LICENSE

### Underlying ZSTD Library
- **License**: BSD 3-Clause License OR GPL-2.0 (dual licensed)
- **Repository**: https://github.com/facebook/zstd
- **Note**: zstd-jni uses the BSD-licensed version

### OpenJDK
- **License**: GNU General Public License version 2 with Classpath Exception (GPLv2+CPE)
- **Classpath Exception**: Allows linking with libraries under different licenses
- **JDK Integration**: Permits inclusion of BSD licensed code

## Compatibility Analysis

### Legal Framework

**BSD 2-Clause → GPLv2+Classpath Exception**: ✅ **FULLY COMPATIBLE**

1. **BSD License Characteristics**:
   - **Permissive license** - allows use, modification, distribution
   - **No copyleft requirements** - doesn't impose license restrictions on derivative works
   - **Commercial use allowed** - suitable for enterprise deployment
   - **Attribution required** - must retain copyright notice

2. **Classpath Exception Provision**: 
   - OpenJDK's Classpath Exception allows linking with code under different licenses
   - BSD libraries can be included in GPLv2+CPE projects without restriction
   - No license contamination or compatibility issues

3. **Industry Precedent**:
   - Many BSD-licensed libraries used with OpenJDK (e.g., ASM bytecode library)
   - Standard practice in Java ecosystem
   - No known compatibility issues in production deployments

## Technical Integration Scenarios

### Scenario 1: Runtime Dependency (Current JARZ Implementation)
```xml
<dependency>
    <groupId>com.github.luben</groupId>
    <artifactId>zstd-jni</artifactId>
    <version>1.5.7-6</version>
</dependency>
```
**Status**: ✅ **COMPATIBLE** - Runtime classpath dependency

### Scenario 2: JDK Integration (Future JEP Submission)
```java
// Potential JDK integration
module java.base {
    requires com.github.luben.zstdjni; // BSD licensed
}
```
**Status**: ✅ **COMPATIBLE** - Module system integration allowed

### Scenario 3: Native Library Bundling
- **zstd-jni includes native libraries** (Windows .dll, Linux .so, macOS .dylib)
- **Native libraries are BSD licensed** (from Facebook's ZSTD project)
- **Status**: ✅ **COMPATIBLE** - Native bundling allowed

## Compatibility Matrix

| Integration Type | BSD 2-Clause | GPLv2+CPE | JARZ Project | Result |
|------------------|---------------|-----------|--------------|---------|
| **Runtime dependency** | ✅ | ✅ | ✅ | **Compatible** |
| **Module integration** | ✅ | ✅ | ✅ | **Compatible** |
| **Native bundling** | ✅ | ✅ | ✅ | **Compatible** |
| **Source modification** | ✅ | ✅ | ✅ | **Compatible** |
| **Commercial use** | ✅ | ✅ | ✅ | **Compatible** |

## Attribution Requirements

### BSD 2-Clause Requirements
```
Copyright (c) 2015-present, Luben Karavelov
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
   this list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.
```

### JARZ Project Compliance
- ✅ **Copyright notice preserved** in dependency declarations
- ✅ **License file included** in Maven/Gradle builds
- ✅ **Attribution maintained** in distribution packages
- ✅ **No additional restrictions** imposed

## Risk Assessment

| Risk Category | Level | Mitigation |
|---------------|-------|------------|
| **License compatibility** | ✅ None | BSD is permissive, no conflicts |
| **Attribution compliance** | 🟡 Low | Maintain copyright notices |
| **Patent concerns** | ✅ None | BSD includes implicit patent grant |
| **Copyleft contamination** | ✅ None | BSD is non-copyleft |
| **Commercial restrictions** | ✅ None | BSD allows commercial use |

## Recommendations

### For JARZ Project
1. ✅ **Continue using zstd-jni** - No license barriers
2. ✅ **Maintain attribution** - Include license in distributions
3. ✅ **Document dependency** - Clear license documentation

### For JDK Integration
1. ✅ **No license barriers** for JEP submission
2. ✅ **Standard integration process** - Follow normal JDK procedures
3. ✅ **Attribution handling** - Include in JDK license documentation

## Conclusion

**zstd-jni (BSD 2-Clause) is FULLY COMPATIBLE with OpenJDK (GPLv2+CPE) and poses no legal barriers for:**
- Current JARZ project development
- Enterprise deployment
- Future JDK integration via JEP process
- Commercial use and distribution

The permissive BSD license actually makes integration easier than copyleft alternatives, requiring only attribution maintenance without imposing additional restrictions.

---

**Legal Disclaimer**: This analysis is for informational purposes. Consult legal counsel for definitive license compliance guidance in specific deployment scenarios.
