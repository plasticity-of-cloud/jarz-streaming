package jdk.incubator.jarz.cdn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.security.ProtectionDomain;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ProtectionDomainFactory flyweight pattern.
 */
class ProtectionDomainFactoryTest {

    @AfterEach
    void tearDown() {
        ProtectionDomainFactory.clearCache();
    }

    @Test
    void sameUrlReturnsSameProtectionDomain() throws Exception {
        URL url = new URL("https://cdn.example.com/app.jarz");
        
        ProtectionDomain pd1 = ProtectionDomainFactory.getProtectionDomain(url);
        ProtectionDomain pd2 = ProtectionDomainFactory.getProtectionDomain(url);
        
        assertThat(pd1).isSameAs(pd2);
        assertThat(ProtectionDomainFactory.getCacheSize()).isEqualTo(1);
    }

    @Test
    void differentUrlsReturnDifferentProtectionDomains() throws Exception {
        URL url1 = new URL("https://cdn.example.com/app1.jarz");
        URL url2 = new URL("https://cdn.example.com/app2.jarz");
        
        ProtectionDomain pd1 = ProtectionDomainFactory.getProtectionDomain(url1);
        ProtectionDomain pd2 = ProtectionDomainFactory.getProtectionDomain(url2);
        
        assertThat(pd1).isNotSameAs(pd2);
        assertThat(ProtectionDomainFactory.getCacheSize()).isEqualTo(2);
    }

    @Test
    void clearCacheRemovesAllEntries() throws Exception {
        URL url = new URL("https://cdn.example.com/app.jarz");
        ProtectionDomainFactory.getProtectionDomain(url);
        
        assertThat(ProtectionDomainFactory.getCacheSize()).isEqualTo(1);
        
        ProtectionDomainFactory.clearCache();
        
        assertThat(ProtectionDomainFactory.getCacheSize()).isEqualTo(0);
    }
}
