/*
 * Copyright (c) 2024, Plasticity.Cloud. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 */

package net.jarz.streaming.internal;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Internal logging utility for JARZ components using JDK's System.Logger.
 * 
 * <p>This class provides a centralized logging interface for all JARZ modules,
 * using the standard {@link System.Logger} API introduced in Java 9 (JEP 264).
 * The logger automatically adapts to the available logging framework or falls
 * back to {@code java.util.logging} when no external framework is present.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * private static final JarzLogger logger = JarzLogger.getLogger(MyClass.class);
 * 
 * logger.info("Processing JARZ archive: {0}", archivePath);
 * logger.debug("Block {0} decompressed: {1} bytes", blockIndex, size);
 * logger.error("Failed to read JARZ header", exception);
 * }</pre>
 * 
 * <h2>Log Levels</h2>
 * <ul>
 * <li>{@code ERROR} - Critical errors that prevent operation</li>
 * <li>{@code WARNING} - Non-fatal issues that should be addressed</li>
 * <li>{@code INFO} - General operational information</li>
 * <li>{@code DEBUG} - Detailed diagnostic information</li>
 * <li>{@code TRACE} - Very detailed execution flow information</li>
 * </ul>
 * 
 * @author Plasticity.Cloud
 * @since 1.0
 * @see System.Logger
 */
public final class JarzLogger {
    
    private final Logger logger;
    
    private JarzLogger(Logger logger) {
        this.logger = logger;
    }
    
    /**
     * Creates a logger for the specified class.
     * 
     * @param clazz the class requesting the logger, must not be null
     * @return a JarzLogger instance for the class
     * @throws IllegalArgumentException if clazz is null
     * @since 1.0
     */
    public static JarzLogger getLogger(Class<?> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }
        return new JarzLogger(System.getLogger(clazz.getName()));
    }
    
    /**
     * Creates a logger with the specified name.
     * 
     * @param name the logger name, must not be null
     * @return a JarzLogger instance with the specified name
     * @throws IllegalArgumentException if name is null
     * @since 1.0
     */
    public static JarzLogger getLogger(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Logger name cannot be null");
        }
        return new JarzLogger(System.getLogger(name));
    }
    
    /**
     * Logs an error message.
     * 
     * @param message the message to log
     * @since 1.0
     */
    public void error(String message) {
        logger.log(Level.ERROR, message);
    }
    
    /**
     * Logs an error message with parameters.
     * 
     * @param message the message template with {0}, {1}, etc. placeholders
     * @param params the parameters to substitute
     * @since 1.0
     */
    public void error(String message, Object... params) {
        logger.log(Level.ERROR, message, params);
    }
    
    /**
     * Logs an error message with an exception.
     * 
     * @param message the message to log
     * @param throwable the exception to log
     * @since 1.0
     */
    public void error(String message, Throwable throwable) {
        logger.log(Level.ERROR, message, throwable);
    }
    
    /**
     * Logs a warning message.
     * 
     * @param message the message to log
     * @since 1.0
     */
    public void warning(String message) {
        logger.log(Level.WARNING, message);
    }
    
    /**
     * Logs a warning message with parameters.
     * 
     * @param message the message template with {0}, {1}, etc. placeholders
     * @param params the parameters to substitute
     * @since 1.0
     */
    public void warning(String message, Object... params) {
        logger.log(Level.WARNING, message, params);
    }
    
    /**
     * Logs an info message.
     * 
     * @param message the message to log
     * @since 1.0
     */
    public void info(String message) {
        logger.log(Level.INFO, message);
    }
    
    /**
     * Logs an info message with parameters.
     * 
     * @param message the message template with {0}, {1}, etc. placeholders
     * @param params the parameters to substitute
     * @since 1.0
     */
    public void info(String message, Object... params) {
        logger.log(Level.INFO, message, params);
    }
    
    /**
     * Logs a debug message.
     * 
     * @param message the message to log
     * @since 1.0
     */
    public void debug(String message) {
        logger.log(Level.DEBUG, message);
    }
    
    /**
     * Logs a debug message with parameters.
     * 
     * @param message the message template with {0}, {1}, etc. placeholders
     * @param params the parameters to substitute
     * @since 1.0
     */
    public void debug(String message, Object... params) {
        logger.log(Level.DEBUG, message, params);
    }
    
    /**
     * Logs a trace message.
     * 
     * @param message the message to log
     * @since 1.0
     */
    public void trace(String message) {
        logger.log(Level.TRACE, message);
    }
    
    /**
     * Logs a trace message with parameters.
     * 
     * @param message the message template with {0}, {1}, etc. placeholders
     * @param params the parameters to substitute
     * @since 1.0
     */
    public void trace(String message, Object... params) {
        logger.log(Level.TRACE, message, params);
    }
    
    /**
     * Checks if error level logging is enabled.
     * 
     * @return true if error logging is enabled
     * @since 1.0
     */
    public boolean isErrorEnabled() {
        return logger.isLoggable(Level.ERROR);
    }
    
    /**
     * Checks if warning level logging is enabled.
     * 
     * @return true if warning logging is enabled
     * @since 1.0
     */
    public boolean isWarningEnabled() {
        return logger.isLoggable(Level.WARNING);
    }
    
    /**
     * Checks if info level logging is enabled.
     * 
     * @return true if info logging is enabled
     * @since 1.0
     */
    public boolean isInfoEnabled() {
        return logger.isLoggable(Level.INFO);
    }
    
    /**
     * Checks if debug level logging is enabled.
     * 
     * @return true if debug logging is enabled
     * @since 1.0
     */
    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.DEBUG);
    }
    
    /**
     * Checks if trace level logging is enabled.
     * 
     * @return true if trace logging is enabled
     * @since 1.0
     */
    public boolean isTraceEnabled() {
        return logger.isLoggable(Level.TRACE);
    }
}
