package net.jarz.streaming.internal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for JarzLogger functionality.
 */
class JarzLoggerTest {
    
    @Test
    void testLoggerCreation() {
        JarzLogger logger = JarzLogger.getLogger(JarzLoggerTest.class);
        assertNotNull(logger);
        
        // Test that we can call logging methods without exceptions
        logger.info("Test info message");
        logger.debug("Test debug message with parameter: {0}", "value");
        logger.error("Test error message", new RuntimeException("test"));
        
        // Test level checks
        assertTrue(logger.isInfoEnabled() || !logger.isInfoEnabled()); // Always true, just testing method exists
    }
    
    @Test
    void testLoggerWithName() {
        JarzLogger logger = JarzLogger.getLogger("test.logger");
        assertNotNull(logger);
        
        logger.warning("Test warning message");
        logger.trace("Test trace message");
    }
    
    @Test
    void testNullValidation() {
        assertThrows(IllegalArgumentException.class, () -> JarzLogger.getLogger((Class<?>) null));
        assertThrows(IllegalArgumentException.class, () -> JarzLogger.getLogger((String) null));
    }
}
