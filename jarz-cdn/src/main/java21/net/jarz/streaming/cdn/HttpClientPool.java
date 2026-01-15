/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.cdn;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool for shared HttpClient instances to reduce memory overhead.
 * 
 * <p>This pool manages shared HttpClient instances across multiple CDN ClassLoaders
 * with the same configuration, reducing memory overhead through resource sharing.
 * 
 * <p>Memory savings: ~50KB per ClassLoader when multiple ClassLoaders
 * use the same HttpClient configuration.
 * 
 * @since 1.0
 */
final class HttpClientPool {
    
    private static final ConcurrentHashMap<HttpClientConfig, SharedHttpClient> clients = new ConcurrentHashMap<>(4);
    
    /**
     * Gets or creates a shared HttpClient for the given configuration.
     * 
     * @param config HttpClient configuration
     * @return shared HttpClient instance
     */
    static HttpClient getOrCreateClient(HttpClientConfig config) {
        SharedHttpClient shared = clients.compute(config, (key, existing) -> {
            if (existing == null) {
                return createSharedClient(key);
            } else {
                existing.addUser();
                return existing;
            }
        });
        return shared.getClient();
    }
    
    /**
     * Releases a reference to the shared HttpClient.
     * 
     * @param config HttpClient configuration
     */
    static void releaseClient(HttpClientConfig config) {
        SharedHttpClient shared = clients.get(config);
        if (shared != null && shared.removeUser() == 0) {
            clients.remove(config, shared);
        }
    }
    
    private static SharedHttpClient createSharedClient(HttpClientConfig config) {
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new SharedHttpClient(client);
    }
    
    /**
     * Returns the number of cached HttpClient instances.
     * Used for testing and monitoring.
     */
    static int getCacheSize() {
        return clients.size();
    }
    
    /**
     * Clears the cache. Used for testing.
     */
    static void clearCache() {
        clients.clear();
    }
    
    /**
     * Wrapper for HttpClient with reference counting.
     */
    private static class SharedHttpClient {
        private final HttpClient client;
        private final AtomicInteger users = new AtomicInteger(1);
        
        SharedHttpClient(HttpClient client) {
            this.client = client;
        }
        
        HttpClient getClient() {
            return client;
        }
        
        void addUser() {
            users.incrementAndGet();
        }
        
        int removeUser() {
            return users.decrementAndGet();
        }
    }
    
    /**
     * HttpClient configuration for cache key.
     */
    record HttpClientConfig(Duration connectTimeout) {}
}
