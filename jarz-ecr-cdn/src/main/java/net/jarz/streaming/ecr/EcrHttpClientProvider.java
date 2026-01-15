package net.jarz.streaming.ecr;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * HTTP client provider optimized for Amazon ECR API calls.
 * 
 * <p>This provider creates HTTP/1.1 clients specifically configured for ECR's
 * OCI Distribution API endpoints, with appropriate timeouts and connection pooling
 * for range request operations.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
public final class EcrHttpClientProvider {
    
    private static volatile HttpClient instance;
    
    private EcrHttpClientProvider() {
        // Utility class
    }
    
    /**
     * Gets a shared HTTP client instance optimized for ECR operations.
     * 
     * <p>The client is configured with:
     * <ul>
     * <li>HTTP/1.1 protocol (ECR compatibility)</li>
     * <li>30-second connect timeout</li>
     * <li>Virtual thread executor (Java 21+)</li>
     * <li>Automatic redirect following</li>
     * </ul>
     * 
     * @return shared HTTP client instance
     */
    public static HttpClient getClient() {
        if (instance == null) {
            synchronized (EcrHttpClientProvider.class) {
                if (instance == null) {
                    instance = createClient();
                }
            }
        }
        return instance;
    }
    
    private static HttpClient createClient() {
        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)  // ECR compatibility
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(Executors.newVirtualThreadPerTaskExecutor())  // Java 21+
            .build();
    }
}
