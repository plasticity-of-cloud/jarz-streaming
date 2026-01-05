# JARZ Data Provider Refactor Plan

## Overview
Refactor JARZ ClassLoader architecture to use pluggable data providers, enabling unified local and remote (CDN/S3) access with identical parsing logic.

## Current Architecture Issues

### Duplicated Logic
- `JarzClassLoader` uses `BlockReader` with `RandomAccessFile`
- `CdnJarzClassLoader` reimplements JARZ parsing with HTTP requests
- Different parsing logic leads to inconsistencies and bugs

### JARZ v2 Format Access Patterns

**File Structure:**
```
┌─────────────────────────────────────────────────────────────────┐
│ Header(16) → Dictionary → Block0 → Block1 → ... → Index → Footer(12) │
└─────────────────────────────────────────────────────────────────┘
```

**Required Access Patterns:**
1. **Header Read**: Bytes 0-15 (magic, version, flags, blockCount, dictSize)
2. **Dictionary Read**: After header, size from dictSize field
3. **Footer Read**: Last 12 bytes (indexOffset + magic)
4. **Index Read**: From indexOffset (block index + class index)
5. **Block Read**: Specific byte ranges for compressed blocks
6. **Random Access**: Any offset/length combination

## Proposed Architecture

### 1. JarzDataProvider Interface

```java
/**
 * Abstraction for JARZ data access supporting both local files and remote sources.
 * Provides random access to JARZ archive data with consistent error handling.
 */
public interface JarzDataProvider extends AutoCloseable {
    
    /**
     * Reads bytes from the specified offset.
     * 
     * @param offset starting position in the archive
     * @param length number of bytes to read
     * @return byte array containing the requested data
     * @throws IOException if read fails or offset/length invalid
     */
    byte[] readBytes(long offset, int length) throws IOException;
    
    /**
     * Gets the total size of the JARZ archive.
     * 
     * @return archive size in bytes
     * @throws IOException if size cannot be determined
     */
    long getFileSize() throws IOException;
    
    /**
     * Reads the JARZ header (first 16 bytes).
     * Convenience method for header access.
     * 
     * @return header bytes
     * @throws IOException if header read fails
     */
    default byte[] readHeader() throws IOException {
        return readBytes(0, JarzV2Format.HEADER_SIZE);
    }
    
    /**
     * Reads the JARZ footer (last 12 bytes).
     * Convenience method for footer access.
     * 
     * @return footer bytes containing index offset and magic
     * @throws IOException if footer read fails
     */
    default byte[] readFooter() throws IOException {
        long size = getFileSize();
        return readBytes(size - JarzV2Format.FOOTER_SIZE, JarzV2Format.FOOTER_SIZE);
    }
    
    /**
     * Reads dictionary data after header.
     * 
     * @param dictSize dictionary size from header
     * @return dictionary bytes, or empty array if no dictionary
     * @throws IOException if dictionary read fails
     */
    default byte[] readDictionary(int dictSize) throws IOException {
        if (dictSize <= 0) {
            return new byte[0];
        }
        return readBytes(JarzV2Format.HEADER_SIZE, dictSize);
    }
    
    /**
     * Reads index data from specified offset.
     * 
     * @param indexOffset offset from footer
     * @param indexSize size of index data
     * @return index bytes
     * @throws IOException if index read fails
     */
    default byte[] readIndex(long indexOffset, int indexSize) throws IOException {
        return readBytes(indexOffset, indexSize);
    }
}
```

### 2. Implementation Classes

#### FileJarzDataProvider
```java
/**
 * Local file implementation using RandomAccessFile.
 * Direct replacement for current BlockReader file access.
 */
public class FileJarzDataProvider implements JarzDataProvider {
    private final RandomAccessFile raf;
    private final long fileSize;
    
    public FileJarzDataProvider(Path filePath) throws IOException {
        this.raf = new RandomAccessFile(filePath.toFile(), "r");
        this.fileSize = raf.length();
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset + length > fileSize) {
            throw new IOException("Invalid read range: offset=" + offset + ", length=" + length);
        }
        
        byte[] buffer = new byte[length];
        raf.seek(offset);
        raf.readFully(buffer);
        return buffer;
    }
    
    @Override
    public long getFileSize() throws IOException {
        return fileSize;
    }
    
    @Override
    public void close() throws IOException {
        raf.close();
    }
}
```

#### HttpJarzDataProvider
```java
/**
 * HTTP range request implementation for CDN/S3 access.
 * Uses HTTP Range headers for efficient random access.
 */
public class HttpJarzDataProvider implements JarzDataProvider {
    private final HttpClient httpClient;
    private final String baseUrl;
    private final SignedUrlProvider urlProvider; // Optional for signed URLs
    private volatile Long cachedFileSize;
    
    public HttpJarzDataProvider(String url) {
        this(url, null);
    }
    
    public HttpJarzDataProvider(String url, SignedUrlProvider urlProvider) {
        this.baseUrl = url;
        this.urlProvider = urlProvider;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        String url = urlProvider != null ? urlProvider.signUrl(baseUrl) : baseUrl;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Range", "bytes=" + offset + "-" + (offset + length - 1))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        
        try {
            HttpResponse<byte[]> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 206) { // Partial Content
                return response.body();
            } else if (response.statusCode() == 200) { // Full content (small files)
                byte[] fullBody = response.body();
                if (offset + length <= fullBody.length) {
                    return Arrays.copyOfRange(fullBody, (int)offset, (int)(offset + length));
                }
            }
            
            throw new IOException("HTTP " + response.statusCode() + " for range request");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    @Override
    public long getFileSize() throws IOException {
        if (cachedFileSize != null) {
            return cachedFileSize;
        }
        
        // HEAD request to get Content-Length
        String url = urlProvider != null ? urlProvider.signUrl(baseUrl) : baseUrl;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(Duration.ofSeconds(10))
                .build();
        
        try {
            HttpResponse<Void> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.discarding());
            
            if (response.statusCode() == 200) {
                String contentLength = response.headers().firstValue("Content-Length")
                        .orElseThrow(() -> new IOException("No Content-Length header"));
                cachedFileSize = Long.parseLong(contentLength);
                return cachedFileSize;
            }
            
            throw new IOException("HTTP " + response.statusCode() + " for HEAD request");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    @Override
    public void close() throws IOException {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
```

### 3. Refactored BlockReader

```java
/**
 * JARZ v2 block reader using pluggable data providers.
 * Supports both local files and remote sources with identical logic.
 */
public class BlockReader implements Closeable {
    private final JarzDataProvider dataProvider;
    private final byte[] dictionary;
    private final BlockIndex blockIndex;
    private final ClassIndex classIndex;
    
    public BlockReader(JarzDataProvider dataProvider) throws IOException {
        this.dataProvider = dataProvider;
        
        // Parse header
        byte[] header = dataProvider.readHeader();
        ByteBuffer headerBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        
        byte[] magic = new byte[4];
        headerBuf.get(magic);
        if (!Arrays.equals(magic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 magic");
        }
        
        short version = headerBuf.getShort();
        if (version != JarzV2Format.VERSION) {
            throw new IOException("Unsupported JARZ v2 version: " + version);
        }
        
        short flags = headerBuf.getShort();
        int blockCount = headerBuf.getInt();
        int dictSize = headerBuf.getInt();
        
        // Read dictionary if present
        if ((flags & JarzV2Format.FLAG_HAS_DICTIONARY) != 0 && dictSize > 0) {
            this.dictionary = dataProvider.readDictionary(dictSize);
        } else {
            this.dictionary = null;
        }
        
        // Parse footer to get index offset
        byte[] footer = dataProvider.readFooter();
        ByteBuffer footerBuf = ByteBuffer.wrap(footer).order(ByteOrder.LITTLE_ENDIAN);
        long indexOffset = footerBuf.getLong();
        
        byte[] footerMagic = new byte[4];
        footerBuf.get(footerMagic);
        if (!Arrays.equals(footerMagic, JarzV2Format.MAGIC)) {
            throw new IOException("Invalid JARZ v2 footer");
        }
        
        // Read and parse indexes
        // Note: Index size calculation needed - read incrementally or use heuristic
        byte[] indexData = readIndexData(indexOffset);
        ByteBuffer indexBuf = ByteBuffer.wrap(indexData).order(ByteOrder.LITTLE_ENDIAN);
        
        this.blockIndex = readBlockIndex(indexBuf);
        this.classIndex = readClassIndex(indexBuf);
    }
    
    // ... rest of BlockReader methods unchanged, using dataProvider instead of raf
}
```

### 4. Unified JarzClassLoader

```java
/**
 * Unified JARZ ClassLoader supporting both local and remote sources.
 * Uses JarzDataProvider for consistent data access.
 */
public class JarzClassLoader extends SecureClassLoader implements AutoCloseable {
    private final JarzDataProvider dataProvider;
    private final BlockReader blockReader;
    // ... rest unchanged
    
    /**
     * Creates ClassLoader for local JARZ file.
     */
    public JarzClassLoader(Path jarzFile) throws IOException {
        this(new FileJarzDataProvider(jarzFile));
    }
    
    /**
     * Creates ClassLoader for remote JARZ URL.
     */
    public JarzClassLoader(String jarzUrl) throws IOException {
        this(new HttpJarzDataProvider(jarzUrl));
    }
    
    /**
     * Creates ClassLoader with custom data provider.
     */
    public JarzClassLoader(JarzDataProvider dataProvider) throws IOException {
        super();
        this.dataProvider = dataProvider;
        this.blockReader = new BlockReader(dataProvider);
        // ... initialize manifest, protection domain, etc.
    }
    
    // ... rest of ClassLoader methods unchanged
}
```

## Implementation Steps

### Phase 1: Core Interface (1-2 hours)
1. Create `JarzDataProvider` interface with all required methods
2. Create `FileJarzDataProvider` implementation
3. Add unit tests for file provider

### Phase 2: HTTP Provider (2-3 hours)
1. Create `HttpJarzDataProvider` implementation
2. Handle HTTP range requests, error cases, timeouts
3. Add integration tests with mock HTTP server

### Phase 3: BlockReader Refactor (2-3 hours)
1. Refactor `BlockReader` to use `JarzDataProvider`
2. Replace all `RandomAccessFile` calls with provider calls
3. Ensure identical parsing behavior

### Phase 4: ClassLoader Integration (1-2 hours)
1. Update `JarzClassLoader` constructors
2. Remove old `CdnJarzClassLoader` implementation
3. Update all tests to use new architecture

### Phase 5: Validation (1 hour)
1. Run full test suite
2. Verify local and remote behavior identical
3. Performance testing

## Benefits

### Immediate
- ✅ **Single parsing logic** - No more inconsistencies
- ✅ **Simplified testing** - Mock data provider for unit tests
- ✅ **Bug elimination** - Proven BlockReader logic for all sources

### Future
- ✅ **S3 direct access** - New provider without ClassLoader changes
- ✅ **Memory sources** - In-memory JARZ for testing
- ✅ **Local index optimization** - Hybrid providers (local index + remote blocks)
- ✅ **Caching layers** - Transparent caching providers

## Risk Mitigation

### Compatibility
- Keep existing `JarzClassLoader(Path)` constructor
- All existing local file usage continues working
- Only `CdnJarzClassLoader` users need to change (internal only)

### Performance
- `FileJarzDataProvider` has identical performance to current implementation
- `HttpJarzDataProvider` optimized for range requests
- No additional overhead in common paths

### Testing
- Comprehensive unit tests for each provider
- Integration tests with real HTTP servers
- Performance regression tests

## Success Criteria

1. **Functional**: All existing tests pass with new architecture
2. **Performance**: No regression in local file access performance
3. **Remote**: CDN/S3 access works reliably without SSL issues
4. **Maintainable**: Single source of truth for JARZ parsing logic
5. **Extensible**: Easy to add new data sources in future

---

**Author**: Plasticity.Cloud  
**Date**: 2026-01-02  
**Status**: Ready for Implementation
