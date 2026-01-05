# JARZ v2 Format Enhancement Requirements

## Archive-Level Integrity Checking

### Current Status
- ✅ ZSTD block-level checksums (automatic)
- ❌ Archive-level integrity checking (missing)

### Problem
JARZ v2 currently lacks ZIP-equivalent archive integrity:
- Header corruption not detected
- Index corruption not detected  
- Footer corruption not detected
- No overall archive validation

### Required Implementation
Add **CRC32 archive checksum** to reserved header space:

```
Header Layout (32 bytes):
- Magic: 4 bytes
- Version: 2 bytes  
- Flags: 2 bytes
- Block count: 4 bytes
- Dictionary size: 4 bytes
- Archive CRC32: 4 bytes ← NEW
- Reserved: 12 bytes
```

### CRC32 Coverage
Calculate CRC32 over:
1. Header (excluding CRC32 field itself)
2. Dictionary data
3. All compressed blocks
4. Block index
5. Class index

### Implementation Tasks
- [x] Add CRC32 calculation during BlockWriter.close()
- [x] Add CRC32 verification during BlockReader initialization
- [x] Update JarzV2Format constants
- [x] Add integrity validation tests
- [x] Document CRC32 algorithm and coverage

### Benefits
- ZIP-equivalent integrity checking
- Early detection of archive corruption
- Better error messages for corrupted files
- Production reliability for critical applications

### Priority
**High** - Essential for production JDK inclusion and community acceptance.

---
*Created: 2026-01-02*
*Status: ✅ **COMPLETE** - Implemented 2026-01-03*

## Implementation Details

### CRC32 Coverage
The implementation calculates CRC32 over:
1. Header (excluding CRC32 field itself)
2. Dictionary data (if present)
3. All compressed blocks
4. Block index
5. Class index
6. Footer

### Header Layout (32 bytes)
```
- Magic: 4 bytes
- Version: 2 bytes  
- Flags: 2 bytes (includes FLAG_HAS_CRC32 = 0x0004)
- Block count: 4 bytes
- Dictionary size: 4 bytes
- Archive CRC32: 4 bytes ← NEW
- Reserved: 12 bytes
```

### Usage
- **BlockWriter**: Automatically calculates and writes CRC32 during close()
- **BlockReader**: Verifies CRC32 when using JarzDataProvider constructor
- **Legacy Path constructor**: Does not verify CRC32 (for backward compatibility)

### Testing
- ✅ Archive integrity validation with corruption detection
- ✅ Dictionary support with CRC32 verification
- ✅ Proper error messages for CRC32 mismatches
