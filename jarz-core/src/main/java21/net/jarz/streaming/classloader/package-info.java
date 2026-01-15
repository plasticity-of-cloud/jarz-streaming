/**
 * ClassLoader implementations for JARZ compressed archives.
 * 
 * <p>This package provides ClassLoader implementations that can load classes
 * from JARZ (ZSTD-compressed) archives while maintaining full compatibility
 * with standard JAR files and Java ClassLoader delegation model.
 * 
 * <p>The primary implementation is {@link JarzApplicationClassLoader}, which
 * provides drop-in replacement functionality for standard JAR files with
 * manifest-based classpath resolution.
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Full JAR compatibility with MANIFEST.MF support</li>
 *   <li>Class-Path attribute resolution for dependencies</li>
 *   <li>Proper Java ClassLoader delegation model</li>
 *   <li>Thread-safe implementation with caching</li>
 *   <li>Security-aware with ProtectionDomain support</li>
 * </ul>
 * 
 * @since 1.0
 */
package net.jarz.streaming.classloader;
