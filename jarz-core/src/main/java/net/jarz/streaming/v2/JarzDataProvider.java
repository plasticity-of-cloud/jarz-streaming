package net.jarz.streaming.v2;

import java.io.IOException;

/**
 * Abstraction for JARZ data access supporting both local files and remote sources.
 * Provides random access to JARZ archive data with consistent error handling.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
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
     * Closes the data provider and releases resources.
     * 
     * @throws IOException if close fails
     */
    @Override
    void close() throws IOException;
    
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
     * Reads the JARZ footer (last 16 bytes).
     * Convenience method for footer access.
     * 
     * @return footer bytes containing index offset, file size, and magic
     * @throws IOException if footer read fails
     */
    default byte[] readFooter() throws IOException {
        long size = getFileSize();
        return readBytes(size - JarzV2Format.FOOTER_SIZE, JarzV2Format.FOOTER_SIZE);
    }
    
    /**
     * Reads the JARZ footer using suffix range (avoids HEAD request).
     * 
     * @return footer bytes containing index offset, file size, and magic
     * @throws IOException if footer read fails
     */
    default byte[] readFooterSuffix() throws IOException {
        // For HTTP providers, this should use bytes=-16 range request
        // For file providers, fall back to regular readFooter
        return readFooter();
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
}
