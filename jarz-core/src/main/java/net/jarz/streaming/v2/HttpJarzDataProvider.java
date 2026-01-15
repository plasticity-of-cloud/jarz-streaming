package net.jarz.streaming.v2;

import net.jarz.streaming.internal.JarzLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import javax.net.ssl.SSLContext;

/**
 * HTTP range request implementation for CDN/S3 access.
 * Uses HTTP Range headers for efficient random access.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class HttpJarzDataProvider implements JarzDataProvider {
    
    private static final JarzLogger logger = JarzLogger.getLogger(HttpJarzDataProvider.class);
    
    /**
     * Interface for providing signed URLs for private resources.
     */
    public interface SignedUrlProvider {
        String signUrl(String originalUrl) throws IOException;
    }
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final SignedUrlProvider urlProvider; // Optional for signed URLs
    private volatile Long cachedFileSize;
    private volatile byte[] cachedFooter;
    
    public HttpJarzDataProvider(String url) {
        this(url, null);
    }
    
    public HttpJarzDataProvider(String url, SignedUrlProvider urlProvider) {
        this.baseUrl = url;
        this.urlProvider = urlProvider;
        
        // Configure HttpClient with SSL context for testing
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10));
        
        // For HTTPS URLs, configure SSL context to accept self-signed certificates (testing only)
        if (url.startsWith("https://localhost")) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new javax.net.ssl.TrustManager[] {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                    }
                }, new java.security.SecureRandom());
                builder.sslContext(sslContext);
            } catch (Exception e) {
                throw new RuntimeException("Failed to configure SSL context for testing", e);
            }
        }
        
        this.httpClient = builder.build();
    }
    
    @Override
    public byte[] readBytes(long offset, int length) throws IOException {
        String url = urlProvider != null ? urlProvider.signUrl(baseUrl) : baseUrl;
        
        String rangeHeader;
        if (offset < 0) {
            // Suffix range: bytes=-length (get last 'length' bytes)
            rangeHeader = "bytes=" + offset;
        } else {
            // Normal range: bytes=offset-end
            rangeHeader = "bytes=" + offset + "-" + (offset + length - 1);
        }
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Range", rangeHeader)
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
                if (offset >= 0 && offset + length <= fullBody.length) {
                    return Arrays.copyOfRange(fullBody, (int)offset, (int)(offset + length));
                } else if (offset < 0) {
                    // Suffix range on full content
                    int start = Math.max(0, fullBody.length + (int)offset);
                    return Arrays.copyOfRange(fullBody, start, fullBody.length);
                }
            }
            
            throw new IOException("HTTP " + response.statusCode() + " for range request: " + url);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted", e);
        }
    }
    
    @Override
    public byte[] readFooter() throws IOException {
        if (cachedFooter != null) {
            logger.debug("readFooter() - using cached footer");
            return cachedFooter;
        }
        
        logger.debug("readFooter() - making suffix range request: bytes=-{0}", JarzV2Format.FOOTER_SIZE);
        
        // Use suffix range to get last 16 bytes (footer) without HEAD request
        cachedFooter = readBytes(-JarzV2Format.FOOTER_SIZE, JarzV2Format.FOOTER_SIZE);
        return cachedFooter;
    }
    
    @Override
    public long getFileSize() throws IOException {
        if (cachedFileSize != null) {
            return cachedFileSize;
        }
        
        if (logger.isTraceEnabled()) {
            logger.trace("HEAD REQUEST TRACE - getFileSize() called from:");
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(8, stack.length); i++) {
                logger.trace("  {0}", stack[i]);
            }
        }
        
        // Get file size from footer instead of HEAD request
        byte[] footer = readFooter();
        ByteBuffer footerBuf = ByteBuffer.wrap(footer).order(JarzV2Format.BYTE_ORDER);
        
        // Skip index offset (8 bytes)
        footerBuf.getLong();
        
        // Read file size (4 bytes)
        cachedFileSize = footerBuf.getInt() & 0xFFFFFFFFL;
        
        return cachedFileSize;
    }
    
    @Override
    public void close() throws IOException {
        // HttpClient doesn't need explicit closing in Java 11+
    }
}
