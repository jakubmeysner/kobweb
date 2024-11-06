@file:Suppress("unused") // Used by serializer
@file:UseSerializers(DurationSerializer::class)

package com.varabyte.kobweb.project.conf

import com.charleskorn.kaml.Yaml
import com.varabyte.kobweb.common.data.DataSize
import com.varabyte.kobweb.common.data.DataSize.Companion.mebibytes
import com.varabyte.kobweb.common.time.DurationSerializer
import com.varabyte.kobweb.common.yaml.nonStrictDefault
import com.varabyte.kobweb.project.KobwebFolder
import com.varabyte.kobweb.project.conf.Server.Logging.Level
import com.varabyte.kobweb.project.io.KobwebReadableTextFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * @property title The title of the site. See also: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/title
 * @property basePath If specified, it means all content for this site live under a subfolder. So if this value is
 *   "/a/b/c", then the root index.html file will be visited by the user going to `mysite.com/a/b/c/`. This should
 *   rarely need to be used, but it may be required by some server configurations which nest your site to a
 *   subdirectory.
 */
@Serializable
class Site(
    val title: String,
    @Deprecated("`routePrefix` changed to `basePath` as that name is more consistent with what other web frameworks use.")
    val routePrefix: String = "",
    val basePath: String = "",
) {
    /** Temporary fallback until we can remove routePrefix */
    @Suppress("DEPRECATION")
    val basePathOrRoutePrefix get() = basePath.takeIf { it.isNotEmpty() } ?: routePrefix
}

@Serializable
class Server(
    val files: Files,
    val port: Int = 8080,
    val logging: Logging = Logging(),
    val cors: Cors = Cors(),
    val redirects: List<Redirect> = emptyList(),
    val streaming: Streaming = Streaming(),
    val nativeLibraries: List<NativeLibrary> = emptyList(),
) {
    /**
     * A collection of files and paths needed by the Kobweb server to serve its files.
     */
    @Serializable
    class Files(
        val dev: Dev,
        val prod: Prod,
    ) {
        /**
         * The dev server only serves a single html file that represents the whole project.
         *
         * @param contentRoot The path to serve content from, which includes the Kobweb index.html file.
         * @param script The path to the final JavaScript file generated from the user's Kotlin code.
         * @param api A path to the API jar that may have been generated by the Kobweb project. If present, it can be
         *    used to extend the behavior of the Kobweb server.
         */
        @Serializable
        class Dev(
            val contentRoot: String,
            val script: String,
            val api: String? = null,
        )

        /**
         * @param script The path to the final JavaScript file generated from the user's Kotlin code. Unlike
         *   the [Dev.script] path, this version should be minimized.
         * @param siteRoot The path to the root of where the static site lives
         */
        @Serializable
        class Prod(
            val script: String,
            val siteRoot: String = ".kobweb/site",
        )
    }

    /**
     * Configuration for logging.
     *
     * By default, logs append to a single file and rollover at the end of each day.
     *
     * @param level The minimum level of log messages to show. If set to [Level.OFF], no logs will be shown. See the
     *   [Level] enum for more details. [Level.DEBUG] is a decent balance of verbosity and usefulness. [Level.TRACE]
     *   is incredibly verbose, and is mostly intended for locally debugging. It probably shouldn't be used in
     *   production, but it can be helpful when trying to debug a particularly tricky issue (such as a CORS
     *   misconfiguration or a case where the ktor server is sending you back 403s).
     * @param logRoot The root directory where logs will be stored. If you change this to a directory that will contain
     *   other files besides just logs, consider setting [clearLogsOnStart] to false.
     * @param clearLogsOnStart If true, all existing files under the log root will be deleted when a server is
     *   started in dev mode. Be careful if you changed [logRoot] to a path with non-log files in it!
     * @param logFileBaseName The base name of the log file. A log suffix will automatically be added. Later, if the
     *   logs roll over, they'll be archived, and the base name will be used again in that context.
     * @param maxFileCount The maximum number of log files to keep before old entries get deleted. Pass in null (or 0)
     *   to indicate unbounded file count.
     * @param totalSizeCap The maximum size of all log files before old entries get deleted. Pass in null (or "0b") to
     *   indicate unbounded size.
     * @param compressHistory If true, log files will be compressed when they roll over.
     */
    @Serializable
    class Logging(
        val level: Level = Level.DEBUG,
        val logRoot: String = ".kobweb/server/logs",
        val clearLogsOnStart: Boolean = true,
        val logFileBaseName: String = "kobweb-server",
        val maxFileCount: Int? = null,
        // Note: We're being very conservative here with the default size cap, just in case the user is running Kobweb
        // in a constrained server environment. Users should definitely consider overriding this value. That said, we
        // may revisit this value later if people complain it's too small!
        val totalSizeCap: DataSize? = 10.mebibytes,
        val compressHistory: Boolean = true,
    ) {
        /**
         * The various logging levels supported by Logback, the engine used by the server to do logging.
         *
         * Review [the official documentation](https://logback.qos.ch/apidocs/ch/qos/logback/classic/Level.html) for
         * more details.
         */
        enum class Level {
            ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF
        }
    }

    /**
     * Configuration for CORS.
     *
     * See also: https://ktor.io/docs/cors.html
     * See also: https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS
     */
    @Serializable
    class Cors(
        val hosts: List<Host> = listOf(),
    )

    /**
     * URL redirect mappings.
     *
     * Specifying a mapping like "/legacy-name" to "/new-name" will cause the Kobweb server to issue a 301 redirect if
     * the [from] path is visited.
     *
     * The `from` path supports regexes, and capture groups can be substituted into the `to` path using `$1`, `$2`, etc.
     * For example, a mapping like "/old/([^/]*)" to "/new/$1" will redirect "/old/abc" to "/new/abc".
     *
     * IMPORTANT: The user must remember to add a leading slash. Unfortunately, we cannot simply detect a missing slash
     * ourselves because `(.+)/old` is a valid `from` value. If the user does not include a leading slash, then their
     * `from` pattern will never match.
     */
    @Serializable
    class Redirect(
        val from: String,
        val to: String,
    )

    /**
     * Configuration for Streaming APIs.
     *
     * Streaming APIs work through the WebSockets API, so configuring this section looks like configuring WebSockets.
     * Therefore, you can refer to WebSockets documentation for more details.
     *
     * See also: https://ktor.io/docs/websocket.html
     * See also: https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API
     *
     * @param pingPeriod The duration between pings or `0` to disable pings.
     * @param timeout The write/ping timeout after that a connection will be closed.
     */
    @Serializable
    class Streaming(
        val pingPeriod: Duration = Duration.ZERO,
        val timeout: Duration = 15.seconds,
    )

    /**
     * A name-to-path mapping provided as a way for users to explicitly tell Kobweb which native library file to load.
     *
     * It is not expected that a users will ever need to use this, but essentially, if you use a native library in your
     * Kobweb API route logic, the JVM will kick off a request to load it, starting with a vague, generic name. We do
     * our best to use heuristics to load the right file, but we provide a way for users to explicitly tell us which
     * file to load if they need to.
     *
     * For example, a request to load a library called "test" might result in a request to load a file called
     * "libtest.so", "libtest.dylib", or "test.dll", depending on the platform. Also, if you've managed to package two
     * different libraries which each contain a "test.dll" somewhere in their dependencies, the user can use this
     * mapping to tell Kobweb exactly which one you want to load when a request for "test" comes in.
     *
     * For an example of how to use this:
     *
     * ```
     * server:
     *   nativeLibraries:
     *   - name: test
     *     path: jni/linux/libtest.so
     * ```
     */
    @Serializable
    class NativeLibrary(
        val name: String,
        val path: String,
    )

    /**
     * A collection of host values that can be used to configure CORS.
     *
     * See also: https://ktor.io/docs/cors.html#hosts
     *
     * @param name The hostname (and optional port), e.g. "somesite.com:1234". Do *not* put the scheme in the hostname,
     *   like "http://badexample.com". That should be mentioned separately. "*" is a special value which would mean
     *   allow any host to send a request to this site, but this should be avoided in production.
     * @param schemes Support HTTP schemes for this cross-site request, e.g. "http"
     * @param subdomains Subdomains for the site, e.g. "media" if you wanted to allow "media.varabyte.com"
     */
    @Serializable
    class Host(
        val name: String,
        val schemes: List<String> = listOf("http", "https"),
        val subdomains: List<String> = emptyList(),
    ) {
        init {
            require(name == "*" || schemes.isNotEmpty()) {
                "Invalid host configuration values. Either the hostname should be '*' or at least one scheme should be specified."
            }
        }
    }
}

/**
 * Data values exposed to users that are used to define values globally useful to a Kobweb project.
 */
@Serializable
class KobwebConf(
    val site: Site,
    val server: Server,
)

class KobwebConfFile(kobwebFolder: KobwebFolder) : KobwebReadableTextFile<KobwebConf>(
    kobwebFolder,
    "conf.yaml",
    deserialize = { text -> Yaml.nonStrictDefault.decodeFromString(KobwebConf.serializer(), text) }
)
