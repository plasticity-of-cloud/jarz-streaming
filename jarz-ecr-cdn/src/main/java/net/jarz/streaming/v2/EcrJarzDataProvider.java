package net.jarz.streaming.v2;

import net.jarz.streaming.ecr.EcrJarzClient;
import net.jarz.streaming.ecr.MavenEcrMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * ECR-based JARZ data provider using Maven coordinates.
 * 
 * <p>This provider implements the JarzDataProvider interface for ECR-hosted
 * JARZ artifacts, using HTTP range requests to stream blocks on-demand.
 * 
 * <p>Resolves Maven coordinates to ECR repository and tag, then uses ECR APIs
 * to retrieve JARZ blocks with optimal 59KB block sizes.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public class EcrJarzDataProvider implements JarzDataProvider {
    
    private final EcrJarzClient client;
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String repository;
    private final String tag;
    private volatile String digest; // Lazy-loaded from manifest
    
    /**
     * Creates ECR data provider for Maven artifact.
     * 
     * @param groupId Maven group ID
     * @param artifactId Maven artifact ID  
     * @param version Maven version
     */
    public EcrJarzDataProvider(String groupId, String artifactId, String version) throws IOException {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.repository = MavenEcrMapper.getEcrRepository();
        this.tag = MavenEcrMapper.toEcrTag(groupId, artifactId, version);
        
        // Initialize ECR client
        String region = System.getenv("AWS_REGION");
        if (region == null) {
            region = "us-east-1";
        }
        String accountId = System.getenv("AWS_ACCOUNT_ID");
        this.client = new EcrJarzClient(region, accountId);
    }
    
    @Override
    public byte[] readBlock(long offset, int length) throws IOException {
        ensureDigest();
        return client.getJarzBlock(repository, digest, offset, length);
    }
    
    @Override
    public InputStream openStream() throws IOException {
        ensureDigest();
        // For full stream, start from offset 0 with large block size
        // This is less efficient than block-based access but needed for compatibility
        return new EcrJarzInputStream(client, repository, digest);
    }
    
    @Override
    public long size() throws IOException {
        ensureDigest();
        EcrJarzClient.OciManifest manifest = client.getManifest(repository, tag);
        return manifest.layers.get(0).size;
    }
    
    @Override
    public void close() throws IOException {
        // ECR client uses shared HTTP client, no cleanup needed
    }
    
    private void ensureDigest() throws IOException {
        if (digest == null) {
            synchronized (this) {
                if (digest == null) {
                    EcrJarzClient.OciManifest manifest = client.getManifest(repository, tag);
                    if (manifest.layers.isEmpty()) {
                        throw new IOException("No layers found in ECR manifest for " + getMavenCoordinates());
                    }
                    this.digest = manifest.layers.get(0).digest;
                }
            }
        }
    }
    
    private String getMavenCoordinates() {
        return groupId + ":" + artifactId + ":" + version;
    }
    
    /**
     * InputStream implementation for ECR JARZ streaming.
     */
    private static class EcrJarzInputStream extends InputStream {
        private final EcrJarzClient client;
        private final String repository;
        private final String digest;
        private long position = 0;
        private byte[] buffer;
        private int bufferPos = 0;
        private int bufferLen = 0;
        private static final int BUFFER_SIZE = 59 * 1024; // 59KB - optimal for ECR
        
        EcrJarzInputStream(EcrJarzClient client, String repository, String digest) {
            this.client = client;
            this.repository = repository;
            this.digest = digest;
        }
        
        @Override
        public int read() throws IOException {
            if (bufferPos >= bufferLen) {
                fillBuffer();
                if (bufferLen == 0) {
                    return -1; // EOF
                }
            }
            return buffer[bufferPos++] & 0xFF;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (bufferPos >= bufferLen) {
                fillBuffer();
                if (bufferLen == 0) {
                    return -1; // EOF
                }
            }
            
            int available = bufferLen - bufferPos;
            int toRead = Math.min(len, available);
            System.arraycopy(buffer, bufferPos, b, off, toRead);
            bufferPos += toRead;
            return toRead;
        }
        
        private void fillBuffer() throws IOException {
            try {
                buffer = client.getJarzBlock(repository, digest, position, BUFFER_SIZE);
                bufferLen = buffer.length;
                bufferPos = 0;
                position += bufferLen;
            } catch (IOException e) {
                // Assume EOF if we can't read more
                bufferLen = 0;
                bufferPos = 0;
            }
        }
    }
}
