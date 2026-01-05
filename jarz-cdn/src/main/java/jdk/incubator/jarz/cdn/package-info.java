/**
 * Cloud-agnostic CDN ClassLoader for JARZ v2 archives.
 *
 * <p>This package provides a zero-dependency ClassLoader that streams JARZ v2 blocks
 * from any CDN using the JDK 21+ {@link java.net.http.HttpClient} with HTTP/2 multiplexing
 * and virtual threads.
 *
 * <h2>Supported CDN Providers</h2>
 * <ul>
 *   <li>AWS CloudFront (with S3 origin)</li>
 *   <li>Azure Front Door (with Blob Storage origin)</li>
 *   <li>Google Cloud CDN (with GCS origin)</li>
 *   <li>Any HTTP/2-capable CDN with range request support</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Zero external dependencies (JDK built-in HttpClient)</li>
 *   <li>HTTP/2 multiplexing for parallel block fetches on single connection</li>
 *   <li>Virtual threads for non-blocking I/O</li>
 *   <li>LRU block caching with configurable size</li>
 *   <li>Edge caching reduces latency to ~5ms for hot classes</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // AWS CloudFront
 * try (CdnJarzClassLoader loader = new CdnJarzClassLoader("https://d1234.cloudfront.net/app.jarz")) {
 *     Class<?> clazz = loader.loadClass("com.example.MyClass");
 * }
 *
 * // Azure Front Door
 * try (CdnJarzClassLoader loader = new CdnJarzClassLoader("https://myapp.azurefd.net/app.jarz")) {
 *     Class<?> clazz = loader.loadClass("com.example.MyClass");
 * }
 *
 * // Google Cloud CDN
 * try (CdnJarzClassLoader loader = new CdnJarzClassLoader("https://myapp.cdn.googleapis.com/app.jarz")) {
 *     Class<?> clazz = loader.loadClass("com.example.MyClass");
 * }
 * }</pre>
 *
 * @since 1.0
 * @see jdk.incubator.jarz.cdn.CdnJarzClassLoader
 */
package jdk.incubator.jarz.cdn;
