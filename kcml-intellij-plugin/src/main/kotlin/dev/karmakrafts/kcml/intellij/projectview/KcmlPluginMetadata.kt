/*
 * Copyright 2026 Karma Krafts
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.karmakrafts.kcml.intellij.projectview

import com.intellij.openapi.progress.ProgressManager
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import java.io.Reader
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

@Serializable
internal data class KcmlPluginMetadata( // @formatter:off
    val id: String,

    @SerialName("name")
    @Serializable(with = OptionalStringSerializer::class)
    private val configuredName: String? = null,

    @SerialName("version")
    @Serializable(with = OptionalStringSerializer::class)
    private val configuredVersion: String? = null,

    @Serializable(with = OptionalAuthorsSerializer::class)
    val authors: List<Author> = emptyList(),

    @Serializable(with = OptionalIconSerializer::class)
    private val icon: Icon? = null
) { // @formatter:on
    val name: String get() = configuredName?.takeIf { it.isNotBlank() } ?: id
    val version: String get() = configuredVersion?.takeIf { it.isNotBlank() } ?: "<not available>"
    val configUrl: URL get() = checkNotNull(source)

    @Transient
    private var source: URL? = null

    @Transient
    var lightIcon: URL? = null
        private set

    @Transient
    var darkIcon: URL? = null
        private set

    @Serializable
    internal data class Author( // @formatter:off
        val name: String,

        @Serializable(with = OptionalStringSerializer::class)
        val url: String? = null
    ) // @formatter:on

    @Serializable
    internal data class Icon( // @formatter:off
        @Serializable(with = OptionalStringSerializer::class)
        val light: String? = null,

        @Serializable(with = OptionalStringSerializer::class)
        val dark: String? = null
    ) // @formatter:on

    private object OptionalStringSerializer : JsonTransformingSerializer<String?>(String.serializer().nullable) {
        override fun transformDeserialize(element: JsonElement): JsonElement =
            element.takeIf { it is JsonPrimitive && it.isString } ?: JsonNull
    }

    private object OptionalIconSerializer : JsonTransformingSerializer<Icon?>(Icon.serializer().nullable) {
        override fun transformDeserialize(element: JsonElement): JsonElement =
            element.takeIf { it is JsonObject } ?: JsonNull
    }

    private object OptionalAuthorsSerializer : JsonTransformingSerializer<List<Author>>(ListSerializer(Author.serializer())) {
        override fun transformDeserialize(element: JsonElement): JsonElement = JsonArray(
            (element as? JsonArray).orEmpty().filter {
                val name = (it as? JsonObject)?.get("name") as? JsonPrimitive
                name != null && name.isString && name.content.isNotBlank()
            }
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromClasspath(paths: List<Path>): List<KcmlPluginMetadata> {
            ProgressManager.checkCanceled()
            val plugins = linkedMapOf<String, KcmlPluginMetadata>()
            for (path in paths) {
                ProgressManager.checkCanceled()
                try {
                    val root = path.toRealPath()
                    val entries = if (Files.isDirectory(root)) fromDirectory(root)
                    else if (Files.isRegularFile(root)) fromJar(root)
                    else emptyList()
                    for (entry in entries) plugins.putIfAbsent(entry.id, entry)
                } catch (_: Throwable) {
                    continue
                }
            }
            return plugins.values.toList()
        }

        private fun fromDirectory(root: Path): List<KcmlPluginMetadata> {
            val directory = root.resolve("META-INF").toRealPath()
            if (!directory.startsWith(root) || !Files.isDirectory(directory)) return emptyList()
            val candidates = Files.list(directory).use { entries ->
                entries.peek { ProgressManager.checkCanceled() }
                    .filter { it.fileName.toString().endsWith(".json") }
                    .sorted()
                    .toList()
            }
            return candidates.mapNotNull { candidate ->
                ProgressManager.checkCanceled()
                try {
                    val file = candidate.toRealPath()
                    if (!file.startsWith(root) || !Files.isRegularFile(file)) return@mapNotNull null
                    Files.newBufferedReader(file).use { reader ->
                        read(candidate.fileName.toString(), reader, candidate.toUri().toURL()) { resource ->
                            val icon = root.resolve(resource).toRealPath()
                            if (icon.startsWith(root) && Files.isRegularFile(icon)) icon.toUri().toURL()
                            else null
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }

        private fun fromJar(path: Path): List<KcmlPluginMetadata> = JarFile(path.toFile()).use { jar ->
            val candidates = jar.entries()
                .asSequence()
                .onEach { ProgressManager.checkCanceled() }
                .filter { !it.isDirectory && it.name.startsWith("META-INF/") }
                .filter {
                    val name = it.name.removePrefix("META-INF/")
                    '/' !in name && name.endsWith(".json")
                }
                .sortedBy { it.name }
                .toList()
            candidates.mapNotNull { candidate ->
                ProgressManager.checkCanceled()
                try {
                    jar.getInputStream(candidate).bufferedReader(Charsets.UTF_8).use { reader ->
                        read(
                            candidate.name.removePrefix("META-INF/"), reader, jarUrl(path, candidate.name)
                        ) { resource ->
                            val entry = jar.getJarEntry(resource)
                            if (entry == null || entry.isDirectory) null else jarUrl(path, resource)
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            }
        }

        private fun read(
            filename: String, reader: Reader, configUrl: URL, icon: (String) -> URL?
        ): KcmlPluginMetadata? {
            val metadata = json.decodeFromString<KcmlPluginMetadata>(reader.readText())
            val id = metadata.id
            if (id.isBlank() || '/' in id || '\\' in id || filename != "$id.json") return null
            fun resolveIcon(relative: String?): URL? {
                ProgressManager.checkCanceled()
                if (relative.isNullOrBlank()) return null
                if (relative.any { it == '\\' || it == ':' || it == '\u0000' }) return null
                if (relative.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
                return try {
                    icon("META-INF/$relative")
                } catch (_: Throwable) {
                    null
                }
            }
            metadata.source = configUrl
            metadata.lightIcon = resolveIcon(metadata.icon?.light)
            metadata.darkIcon = resolveIcon(metadata.icon?.dark)
            return metadata
        }

        private fun jarUrl(path: Path, entry: String): URL {
            val encoded = URI(null, null, "/$entry", null).rawPath.removePrefix("/")
            return URI.create("jar:${path.toUri().toASCIIString()}!/$encoded").toURL()
        }
    }
}