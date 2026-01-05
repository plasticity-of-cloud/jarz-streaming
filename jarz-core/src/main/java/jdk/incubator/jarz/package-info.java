/**
 * JARZ (ZSTD-compressed Java Archives) core implementation.
 * 
 * <p>This package provides the foundational components for working with JARZ
 * archives, which are Java archives compressed using ZSTD with block-based
 * organization for optimal performance and compression.
 * 
 * <p>JARZ archives provide significant benefits over traditional JAR files:
 * <ul>
 *   <li>25-40% storage reduction through ZSTD compression</li>
 *   <li>3-5x faster decompression compared to DEFLATE</li>
 *   <li>Random access to individual classes without full decompression</li>
 *   <li>S3 range-request streaming for cloud applications</li>
 *   <li>Dependency-aware class grouping for better compression</li>
 * </ul>
 * 
 * <h2>Package Structure</h2>
 * <ul>
 *   <li>{@code jdk.incubator.jarz.classloader} - ClassLoader implementations</li>
 *   <li>{@code jdk.incubator.jarz.v2} - JARZ v2 format implementation</li>
 * </ul>
 * 
 * @since 1.0
 */
package jdk.incubator.jarz;
