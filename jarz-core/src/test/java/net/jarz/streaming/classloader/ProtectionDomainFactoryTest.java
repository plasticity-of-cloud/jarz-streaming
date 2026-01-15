package net.jarz.streaming.classloader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.net.URL;
import java.security.ProtectionDomain;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProtectionDomainFactory flyweight pattern.
 */
class ProtectionDomainFactoryTest {
    
    @BeforeEach
    @AfterEach
    void cleanup() {
        ProtectionDomainFactory.clearCache();
    }
    
    @Test
    void testSameUrlReturnsSameInstance() throws Exception {
        URL url = new URL("file:///test.jarz");
        
        ProtectionDomain pd1 = ProtectionDomainFactory.getProtectionDomain(url);
        ProtectionDomain pd2 = ProtectionDomainFactory.getProtectionDomain(url);
        
        assertSame(pd1, pd2, "Same URL should return same ProtectionDomain instance");
        assertEquals(1, ProtectionDomainFactory.getCacheSize());
    }
    
    @Test
    void testDifferentUrlsReturnDifferentInstances() throws Exception {
        URL url1 = new URL("file:///test1.jarz");
        URL url2 = new URL("file:///test2.jarz");
        
        ProtectionDomain pd1 = ProtectionDomainFactory.getProtectionDomain(url1);
        ProtectionDomain pd2 = ProtectionDomainFactory.getProtectionDomain(url2);
        
        assertNotSame(pd1, pd2, "Different URLs should return different instances");
        assertEquals(2, ProtectionDomainFactory.getCacheSize());
    }
    
    @Test
    void testClearCache() throws Exception {
        URL url = new URL("file:///test.jarz");
        ProtectionDomainFactory.getProtectionDomain(url);
        
        assertEquals(1, ProtectionDomainFactory.getCacheSize());
        
        ProtectionDomainFactory.clearCache();
        assertEquals(0, ProtectionDomainFactory.getCacheSize());
    }
}
