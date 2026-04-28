/**
 * Classpath resource reader for the embedded `content/` tree shipped inside the opsx JAR.
 * Supports both exploded class directories (during development) and JAR entries (after shadowing).
 *
 * Uses the Java classloader API directly since kotlinx-io has no classpath filesystem support.
 */
package zone.clanker.opsx.cli.init.config

import java.io.File
import java.net.JarURLConnection
import java.util.jar.JarFile

/**
 * Reads the embedded content tree from the classpath (`resources/content/`).
 * Works for both exploded dirs during development and JAR entries after shadowing.
 */
class ResourceTree {
    private val classLoader: ClassLoader = ResourceTree::class.java.classLoader

    /**
     * Read the content of `content/<path>` as a UTF-8 string.
     *
     * @param path path relative to the `content/` prefix (e.g. `"manifest.json"`)
     * @return file contents
     * @throws IllegalStateException if the resource is not found
     */
    fun readText(path: String): String {
        val resourcePath = "content/$path"
        val stream =
            classLoader.getResourceAsStream(resourcePath)
                ?: error("resource not found: $resourcePath")
        return stream.bufferedReader().use { it.readText() }
    }

    /**
     * List the immediate child names (files or directories) under `content/<path>`.
     *
     * @param path directory path relative to `content/` (e.g. `"skills"`, `"agents"`)
     * @return child names; empty if the directory does not exist
     */
    fun listNames(path: String): List<String> {
        val resourcePath = "content/$path"
        val url = classLoader.getResource(resourcePath) ?: return emptyList()

        return when (url.protocol) {
            "file" -> listFromFileSystem(url)
            "jar" -> listFromJar(url, resourcePath)
            else -> emptyList()
        }
    }

    private fun listFromFileSystem(url: java.net.URL): List<String> =
        File(url.toURI())
            .listFiles()
            ?.map { it.name }
            .orEmpty()

    private fun listFromJar(
        url: java.net.URL,
        prefix: String,
    ): List<String> {
        val jarConn = url.openConnection() as JarURLConnection
        val jarFile: JarFile = jarConn.jarFile
        val normalizedPrefix = if (prefix.endsWith("/")) prefix else "$prefix/"
        return jarFile
            .entries()
            .asSequence()
            .filter { it.name.startsWith(normalizedPrefix) && it.name != normalizedPrefix }
            .map { it.name.removePrefix(normalizedPrefix) }
            .map { it.split("/").first() }
            .distinct()
            .toList()
    }
}
