package net.jarz.streaming.cdn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for HttpClientPool resource pooling.
 */
class HttpClientPoolTest {

    @AfterEach
    void tearDown() {
        HttpClientPool.clearCache();
    }

    @Test
    void sameConfigReturnsSameHttpClient() {
        HttpClientPool.HttpClientConfig config = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(5));
        
        HttpClient client1 = HttpClientPool.getOrCreateClient(config);
        HttpClient client2 = HttpClientPool.getOrCreateClient(config);
        
        assertThat(client1).isSameAs(client2);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(1);
    }

    @Test
    void differentConfigsReturnDifferentHttpClients() {
        HttpClientPool.HttpClientConfig config1 = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(5));
        HttpClientPool.HttpClientConfig config2 = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(10));
        
        HttpClient client1 = HttpClientPool.getOrCreateClient(config1);
        HttpClient client2 = HttpClientPool.getOrCreateClient(config2);
        
        assertThat(client1).isNotSameAs(client2);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(2);
    }

    @Test
    void releaseClientRemovesFromCacheWhenNoUsers() {
        HttpClientPool.HttpClientConfig config = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(5));
        
        HttpClient client = HttpClientPool.getOrCreateClient(config);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(1);
        
        HttpClientPool.releaseClient(config);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(0);
    }

    @Test
    void multipleUsersKeepClientInCache() {
        HttpClientPool.HttpClientConfig config = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(5));
        
        HttpClient client1 = HttpClientPool.getOrCreateClient(config);
        HttpClient client2 = HttpClientPool.getOrCreateClient(config);
        
        assertThat(client1).isSameAs(client2);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(1);
        
        // Release one user - should still be cached
        HttpClientPool.releaseClient(config);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(1);
        
        // Release second user - should be removed
        HttpClientPool.releaseClient(config);
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(0);
    }

    @Test
    void clearCacheRemovesAllEntries() {
        HttpClientPool.HttpClientConfig config1 = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(5));
        HttpClientPool.HttpClientConfig config2 = new HttpClientPool.HttpClientConfig(Duration.ofSeconds(10));
        
        HttpClientPool.getOrCreateClient(config1);
        HttpClientPool.getOrCreateClient(config2);
        
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(2);
        
        HttpClientPool.clearCache();
        
        assertThat(HttpClientPool.getCacheSize()).isEqualTo(0);
    }
}
