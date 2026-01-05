# JARZ v2 Format Consistency Issues

## Current Problem
Endianness inconsistency causing "Unsupported JARZ v2 version: 2" errors.

### Root Cause
Mixed byte order handling:
- BlockWriter writes in little-endian (JarzV2Format.BYTE_ORDER)
- Some BlockReader paths still expect big-endian
- Version 0x0200 (512) being read as 2

### Affected Components
- BlockWriter ✅ Fixed (uses JarzV2Format.BYTE_ORDER)
- BlockReader (JarzDataProvider path) ✅ Fixed
- BlockReader (RandomAccessFile path) ✅ Fixed  
- BlockReaderPool ❌ Still failing

### Investigation Needed
1. Verify which BlockReader constructor is actually being called
2. Check if there are cached/compiled inconsistencies
3. Ensure all code paths use JarzV2Format.BYTE_ORDER

### Test Status
- S3JarzIntegrationTest: ❌ Failing (version read as 2)
- CDN integration test: ❓ Unknown status
- Core JARZ tests: ❌ Likely failing

### Priority
**Critical** - Blocks S3 integration test validation and all JARZ functionality.

### Next Steps
1. Debug actual byte values being written/read
2. Verify complete rebuild clears all cached artifacts
3. Test with simple JARZ creation/reading
4. Fix remaining endianness inconsistencies

---
*Created: 2026-01-02*
*Status: Critical Bug*
