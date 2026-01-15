package net.jarz.streaming.ecr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EcrJarzClassLoader.
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 */
class EcrJarzClassLoaderTest {
    
    private static final String TEST_GROUP_ID = "org.springframework.boot";
    private static final String TEST_ARTIFACT_ID = "spring-boot-starter-web";
    private static final String TEST_VERSION = "2.7.0";
    
    @BeforeEach
    void setUp() {
        // Set default environment variables for testing
        if (System.getenv("AWS_REGION") == null) {
            System.setProperty("AWS_REGION", "us-east-1");
        }
    }
    
    @Test
    void testConstructorWithMavenCoordinates() throws IOException {
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)) {
            assertNotNull(loader);
            assertEquals(TEST_GROUP_ID + ":" + TEST_ARTIFACT_ID + ":" + TEST_VERSION, loader.getMavenCoordinates());
            assertEquals("maven-artifacts", loader.getEcrRepository());
            assertEquals("org_springframework_boot--spring-boot-starter-web--2.7.0", loader.getEcrTag());
        }
    }
    
    @Test
    void testConstructorWithParent() throws IOException {
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION, parent)) {
            assertNotNull(loader);
            assertEquals(parent, loader.getParent());
        }
    }
    
    @Test
    void testGetCurrentJarzUrl() throws IOException {
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)) {
            String expectedUrl = "maven-artifacts:org_springframework_boot--spring-boot-starter-web--2.7.0";
            assertEquals(expectedUrl, loader.getCurrentJarzUrl());
        }
    }
    
    @Test
    void testCreateChildLoader() throws IOException {
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)) {
            String childCoordinates = "junit:junit:4.13.2";
            try (EcrJarzClassLoader childLoader = (EcrJarzClassLoader) loader.createChildLoader(childCoordinates)) {
                assertNotNull(childLoader);
                assertEquals("junit:junit:4.13.2", childLoader.getMavenCoordinates());
                assertEquals("junit--junit--4.13.2", childLoader.getEcrTag());
            }
        }
    }
    
    @Test
    void testInvalidMavenCoordinatesThrowsException() throws IOException {
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)) {
            assertThrows(IllegalArgumentException.class, () -> {
                loader.createChildLoader("invalid-coordinates");
            });
        }
    }
    
    @EnabledIfEnvironmentVariable(named = "ECR_INTEGRATION_TEST", matches = "true")
    @Test
    void testLoadClassFromEcr() throws IOException, ClassNotFoundException {
        // This test requires actual ECR setup and valid Maven artifact
        try (EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION)) {
            // Try to load a known Spring Boot class
            Class<?> clazz = loader.loadClass("org.springframework.boot.SpringApplication");
            assertNotNull(clazz);
            assertEquals("org.springframework.boot.SpringApplication", clazz.getName());
        }
    }
    
    @Test
    @DisplayName("EcrJarzClassLoader should inherit Main-Class support from base class")
    void testEcrMainClassInheritance() {
        // Test that ECR ClassLoader has inherited Main-Class methods
        // Note: This test verifies API inheritance without requiring actual ECR access
        
        try {
            // Verify methods are available (will fail during construction due to no ECR access, but that's expected)
            EcrJarzClassLoader loader = new EcrJarzClassLoader(TEST_GROUP_ID, TEST_ARTIFACT_ID, TEST_VERSION);
            
            // If we get here (unlikely without ECR setup), verify inherited methods exist
            assertNotNull(loader.getClass().getMethod("hasMainClass"), "Should inherit hasMainClass()");
            assertNotNull(loader.getClass().getMethod("getMainClassName"), "Should inherit getMainClassName()");
            
            loader.close();
        } catch (Exception e) {
            // Expected when ECR is not available - just verify the methods exist on the class
            try {
                assertNotNull(EcrJarzClassLoader.class.getMethod("hasMainClass"), "Should inherit hasMainClass()");
                assertNotNull(EcrJarzClassLoader.class.getMethod("getMainClassName"), "Should inherit getMainClassName()");
                System.out.println("✅ EcrJarzClassLoader inherits Main-Class methods from base class");
            } catch (NoSuchMethodException nsme) {
                fail("EcrJarzClassLoader should inherit Main-Class methods from base class");
            }
        }
    }
}
