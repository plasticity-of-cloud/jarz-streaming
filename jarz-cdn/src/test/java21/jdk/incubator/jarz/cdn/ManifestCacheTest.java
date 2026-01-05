package jdk.incubator.jarz.cdn;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ManifestCache flyweight pattern.
 */
class ManifestCacheTest {

    @AfterEach
    void tearDown() {
        ManifestCache.clearCache();
    }

    @Test
    void sameUrlReturnsSameManifest() throws IOException {
        String url = "https://cdn.example.com/app.jarz";
        byte[] manifestBytes = "Manifest-Version: 1.0\nMain-Class: com.example.Main\n\n".getBytes();
        
        Manifest m1 = ManifestCache.getManifest(url, manifestBytes);
        Manifest m2 = ManifestCache.getManifest(url, manifestBytes);
        
        assertThat(m1).isSameAs(m2);
        assertThat(ManifestCache.getCacheSize()).isEqualTo(1);
    }

    @Test
    void differentUrlsReturnDifferentManifests() throws IOException {
        String url1 = "https://cdn.example.com/app1.jarz";
        String url2 = "https://cdn.example.com/app2.jarz";
        byte[] manifestBytes = "Manifest-Version: 1.0\nMain-Class: com.example.Main\n\n".getBytes();
        
        Manifest m1 = ManifestCache.getManifest(url1, manifestBytes);
        Manifest m2 = ManifestCache.getManifest(url2, manifestBytes);
        
        assertThat(m1).isNotSameAs(m2);
        assertThat(ManifestCache.getCacheSize()).isEqualTo(2);
    }

    @Test
    void nullManifestBytesReturnsNull() throws IOException {
        String url = "https://cdn.example.com/app.jarz";
        
        Manifest manifest = ManifestCache.getManifest(url, null);
        
        assertThat(manifest).isNull();
        assertThat(ManifestCache.getCacheSize()).isEqualTo(0);
    }
}
