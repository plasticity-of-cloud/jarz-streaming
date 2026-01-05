/**
 * JARZ v2 block-based format implementation.
 * 
 * <p>This package contains the core implementation of the JARZ v2 format,
 * which uses block-based ZSTD compression with dependency-aware class grouping
 * for optimal compression ratios and efficient random access.
 * 
 * <p>Key components include:
 * <ul>
 *   <li>{@link BlockReader} - Reads compressed blocks from JARZ archives</li>
 *   <li>{@link BlockWriter} - Creates compressed blocks for JARZ archives</li>
 *   <li>{@link BlockType} - Defines different content types and compression levels</li>
 *   <li>{@link DependencyAnalyzer} - Analyzes class dependencies for optimal grouping</li>
 * </ul>
 * 
 * <h2>Format Overview</h2>
 * <p>JARZ v2 uses a seekable block-based format where each block contains
 * related classes compressed together with ZSTD. This enables:
 * <ul>
 *   <li>Random access to individual classes without full decompression</li>
 *   <li>S3 range-request streaming for cloud-native applications</li>
 *   <li>Better compression through dependency-aware grouping</li>
 *   <li>Efficient caching at the block level</li>
 * </ul>
 * 
 * @since 1.0
 */
package jdk.incubator.jarz.v2;
