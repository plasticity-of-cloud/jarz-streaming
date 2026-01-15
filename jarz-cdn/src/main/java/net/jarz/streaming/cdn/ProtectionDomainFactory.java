/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.cdn;

import java.net.URL;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for shared ProtectionDomain instances using flyweight pattern.
 * 
 * <p>This factory reduces memory overhead by sharing ProtectionDomain instances
 * across multiple ClassLoaders that use the same code source URL.
 * 
 * <p>Memory savings: ~2KB per ClassLoader when multiple ClassLoaders
 * reference the same JARZ file or have identical code sources.
 * 
 * @since 1.0
 */
final class ProtectionDomainFactory {
    
    private static final ConcurrentHashMap<URL, ProtectionDomain> domains = new ConcurrentHashMap<>(4);
    
    /**
     * Returns a shared ProtectionDomain for the given code source URL.
     * 
     * <p>Multiple calls with the same URL will return the same ProtectionDomain
     * instance, reducing memory overhead through the flyweight pattern.
     * 
     * @param codeSource the code source URL
     * @return shared ProtectionDomain instance
     */
    static ProtectionDomain getProtectionDomain(URL codeSource) {
        return domains.computeIfAbsent(codeSource, url -> {
            Permissions permissions = new Permissions();
            permissions.add(new AllPermission()); // For now - should be more restrictive
            return new ProtectionDomain(
                new CodeSource(url, (Certificate[]) null),
                permissions
            );
        });
    }
    
    /**
     * Returns the number of cached ProtectionDomain instances.
     * Used for testing and monitoring.
     */
    static int getCacheSize() {
        return domains.size();
    }
    
    /**
     * Clears the cache. Used for testing.
     */
    static void clearCache() {
        domains.clear();
    }
}
