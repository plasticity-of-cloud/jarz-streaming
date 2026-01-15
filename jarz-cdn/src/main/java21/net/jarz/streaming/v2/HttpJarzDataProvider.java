package net.jarz.streaming.v2;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLContext;

/**
 * Java 21+ HTTP JARZ Data Provider with virtual threads optimization.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class HttpJarzDataProvider implements JarzDataProvider {
    
    public interface SignedUrlProvider {
        String signUrl(String originalUrl) throws IOException;
    }
    
    private final HttpClient httpClient;
    private final String baseUrl;
    private final SignedUrlProvider urlProvider;
    private volatile Long cachedFileSize;
    private volatile byte[] cachedFooter;
    
    public HttpJarzDataProvider(String url) {
        this(url, null);
    }
    
    public HttpJarzDataProvider(String url, SignedUrlProvider urlProvider) {
        this.baseUrl = url;
        this.urlProvider = urlProvider;
        
        // Create SSL context that trusts self-signed certificates for testing
        SSLContext sslContext = createTrustAllSSLContext();
        
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .sslContext(sslContext)
                .build();
    }
    
    private static SSLContext createTrustAllSSLContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            
            // Create a trust manager that accepts all certificates (for testing only)
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) { }
                }
            };
            
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            return sslContext;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
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
                .build();
        
        try {
            HttpResponse<byte[]> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 206 || response.statusCode() == 200) {
                return response.body();
            } else {
                throw new IOException("HTTP " + response.statusCode() + " for range request");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        } catch (Exception e) {
            throw new IOException("HTTP request failed", e);
        }
    }
    
    @Override
    public byte[] readFooter() throws IOException {
        if (cachedFooter != null) {
            return cachedFooter;
        }
        
        // Use suffix range to get last 16 bytes (footer) without HEAD request
        cachedFooter = readBytes(-JarzV2Format.FOOTER_SIZE, JarzV2Format.FOOTER_SIZE);
        return cachedFooter;
    }
    
    @Override
    public long getFileSize() throws IOException {
        if (cachedFileSize != null) {
            return cachedFileSize;
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
        // HttpClient will be garbage collected with virtual thread executor
    }
}
