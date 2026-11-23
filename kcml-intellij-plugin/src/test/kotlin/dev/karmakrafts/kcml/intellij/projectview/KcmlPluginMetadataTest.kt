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

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class KcmlPluginMetadataTest : LightPlatformTestCase() {
    private lateinit var directory: Path

    override fun setUp() {
        super.setUp()
        directory = Files.createTempDirectory(Files.createDirectories(Path.of("build").toAbsolutePath()), "kcml-metadata-")
    }

    override fun tearDown() {
        try {
            directory.toFile().deleteRecursively()
        }
        finally {
            super.tearDown()
        }
    }

    fun testDirectoryAndJarMetadataAndThemeResources() {
        val entries = linkedMapOf(
            "META-INF/zeta.json" to """{"id":"zeta","name":"Zeta","version":"2.0"}""",
            "META-INF/alpha.json" to """{
                "id":"alpha","name":"Alpha Plugin","version":"1.0",
                "icon":{"light":"icons/light #1.svg","dark":"icons/dark.svg"}
            }""",
            "META-INF/icons/light #1.svg" to "light icon",
            "META-INF/icons/dark.svg" to "dark icon"
        )
        for (jar in listOf(false, true)) {
            val plugins = KcmlPluginMetadata.fromClasspath(listOf(artifact(entries, jar)))
            assertEquals(listOf("alpha", "zeta"), plugins.map { it.id })
            val alpha = plugins.first()
            assertEquals("Alpha Plugin", alpha.name)
            assertEquals("1.0", alpha.version)
            assertEquals(entries.getValue("META-INF/alpha.json"), alpha.configUrl.readText())
            assertEquals("light icon", alpha.lightIcon!!.readText())
            assertEquals("dark icon", alpha.darkIcon!!.readText())
            assertEquals(if (jar) "jar" else "file", alpha.configUrl.protocol)
            assertNull(plugins.last().lightIcon)
            assertNull(plugins.last().darkIcon)
        }
    }

    fun testMissingBlankAndInvalidNameAndVersionUseFallbacks() {
        val entries = linkedMapOf(
            "META-INF/missing.json" to """{"id":"missing"}""",
            "META-INF/blank.json" to """{"id":"blank","name":"  ","version":"\n"}""",
            "META-INF/numeric.json" to """{"id":"numeric","name":42,"version":1}""",
            "META-INF/structured.json" to """{"id":"structured","name":{},"version":[]}""",
            "META-INF/null.json" to """{"id":"null","name":null,"version":false}"""
        )
        for (jar in listOf(false, true)) {
            val plugins = KcmlPluginMetadata.fromClasspath(listOf(artifact(entries, jar)))
            assertEquals(5, plugins.size)
            for (plugin in plugins) {
                assertEquals(plugin.id, plugin.name)
                assertEquals("<not available>", plugin.version)
                assertNull(plugin.lightIcon)
                assertNull(plugin.darkIcon)
            }
        }
    }

    fun testSerializationRoundTripExcludesResolvedResourcesAndIgnoresUnknownFields() {
        val config = """{
            "id":"example","name":"Example","version":"1.2.3",
            "description":"Unknown metadata","dependencies":[],
            "icon":{"light":"icon.svg","future":"ignored"}
        }"""
        val plugin = KcmlPluginMetadata.fromClasspath(listOf(artifact(mapOf(
            "META-INF/example.json" to config,
            "META-INF/icon.svg" to "icon"
        ), false))).single()
        val encoded = Json.encodeToString(plugin)
        assertEquals(
            Json.parseToJsonElement("""{"id":"example","name":"Example","version":"1.2.3","icon":{"light":"icon.svg"}}"""),
            Json.parseToJsonElement(encoded)
        )
        val decoded = Json.decodeFromString<KcmlPluginMetadata>(encoded)
        assertEquals(plugin, decoded)
        assertEquals("Example", decoded.name)
        assertEquals("1.2.3", decoded.version)
        assertNull(decoded.lightIcon)
        assertNull(decoded.darkIcon)
    }

    fun testMalformedUnrelatedAndMismatchedMetadataIsIgnored() {
        val entries = linkedMapOf(
            "META-INF/broken.json" to "{",
            "META-INF/unquoted.json" to """{id:"unquoted"}""",
            "META-INF/comment.json" to """{/* comment */"id":"comment"}""",
            "META-INF/trailing.json" to """{"id":"trailing"} {}""",
            "META-INF/array.json" to "[]",
            "META-INF/unrelated.json" to """{"description":"not a plugin"}""",
            "META-INF/number.json" to """{"id":42}""",
            "META-INF/object.json" to """{"id":{}}""",
            "META-INF/blank.json" to """{"id":" "}""",
            "META-INF/mismatch.json" to """{"id":"different"}""",
            "META-INF/nested/hidden.json" to """{"id":"hidden"}""",
            "META-INF/backslash.json" to """{"id":"bad\\id"}""",
            "elsewhere.json" to """{"id":"elsewhere"}""",
            "META-INF/valid.txt" to """{"id":"valid"}""",
            "META-INF/valid.json" to """{"id":"valid"}"""
        )
        for (jar in listOf(false, true)) {
            assertEquals(listOf("valid"), KcmlPluginMetadata.fromClasspath(listOf(artifact(entries, jar))).map { it.id })
        }
    }

    fun testFallbacksAndThemeIconsAreIndependent() {
        val entries = mapOf(
            "META-INF/named.json" to """{"id":"named","name":"Named Plugin","icon":{"light":"missing.svg","dark":"icon.svg"}}""",
            "META-INF/versioned.json" to """{"id":"versioned","version":"2.0","icon":{"light":"icon.svg","dark":"../icon.svg"}}""",
            "META-INF/icon.svg" to "icon"
        )
        for (jar in listOf(false, true)) {
            val plugins = KcmlPluginMetadata.fromClasspath(listOf(artifact(entries, jar))).associateBy { it.id }
            val named = plugins.getValue("named")
            assertEquals("Named Plugin", named.name)
            assertEquals("<not available>", named.version)
            assertNull(named.lightIcon)
            assertEquals("icon", named.darkIcon!!.readText())
            val versioned = plugins.getValue("versioned")
            assertEquals("versioned", versioned.name)
            assertEquals("2.0", versioned.version)
            assertEquals("icon", versioned.lightIcon!!.readText())
            assertNull(versioned.darkIcon)
        }
    }

    fun testFirstClasspathOccurrenceWinsAcrossDirectoriesAndJars() {
        val first = artifact(mapOf("META-INF/example.json" to """{"id":"example","name":"First"}"""), false)
        val second = artifact(mapOf(
            "META-INF/example.json" to """{"id":"example","name":"Second"}""",
            "META-INF/other.json" to """{"id":"other"}"""
        ), true)
        val plugins = KcmlPluginMetadata.fromClasspath(listOf(first, second, first))
        assertEquals(listOf("example", "other"), plugins.map { it.id })
        assertEquals("First", plugins.first().name)
        assertEquals("Second", KcmlPluginMetadata.fromClasspath(listOf(second, first)).first().name)
    }

    fun testMissingAndCorruptPathsDoNotHideLaterPlugins() {
        val corrupt = directory.resolve("corrupt.jar")
        Files.writeString(corrupt, "not a jar")
        val empty = Files.createDirectory(directory.resolve("unbuilt"))
        val valid = artifact(mapOf("META-INF/example.json" to """{"id":"example"}"""), true)
        val plugins = KcmlPluginMetadata.fromClasspath(listOf(directory.resolve("missing.jar"), corrupt, empty, valid))
        assertEquals(listOf("example"), plugins.map { it.id })
        assertEmpty(KcmlPluginMetadata.fromClasspath(emptyList()))
    }

    fun testInvalidAndMissingIconsDoNotDropMetadata() {
        val icons = listOf(
            "../outside.svg", "/absolute.svg", "https://example.com/icon.svg", "file:outside.svg",
            "icons/../../outside.svg", "icons\\outside.svg", "missing.svg", "icons", ""
        )
        val entries = linkedMapOf("META-INF/icons/placeholder" to "directory marker", "outside.svg" to "outside")
        icons.forEachIndexed { index, icon ->
            val escaped = icon.replace("\\", "\\\\")
            entries["META-INF/plugin$index.json"] = """{"id":"plugin$index","icon":{"light":"$escaped","dark":42}}"""
        }
        entries["META-INF/invalid.json"] = """{"id":"invalid","icon":[]}"""
        for (jar in listOf(false, true)) {
            val plugins = KcmlPluginMetadata.fromClasspath(listOf(artifact(entries, jar)))
            assertEquals(icons.size + 1, plugins.size)
            plugins.forEach {
                assertNull(it.lightIcon)
                assertNull(it.darkIcon)
            }
        }
    }

    fun testDirectorySymlinksCannotEscapeArtifact() {
        val root = artifact(mapOf("META-INF/example.json" to """{"id":"example","icon":{"light":"linked.svg"}}"""), false)
        val outside = Files.writeString(directory.resolve("outside.svg"), "outside icon")
        Files.createSymbolicLink(root.resolve("META-INF/linked.svg"), outside)
        val outsideMetadata = Files.writeString(directory.resolve("outside.json"), """{"id":"outside"}""")
        Files.createSymbolicLink(root.resolve("META-INF/outside.json"), outsideMetadata)
        val plugins = KcmlPluginMetadata.fromClasspath(listOf(root))
        assertEquals(listOf("example"), plugins.map { it.id })
        assertNull(plugins.single().lightIcon)
    }

    fun testCancellationPropagates() {
        val indicator = ProgressIndicatorBase()
        indicator.cancel()
        try {
            ProgressManager.getInstance().runProcess(Runnable {
                KcmlPluginMetadata.fromClasspath(listOf(directory))
            }, indicator)
            fail("Expected cancellation to propagate")
        }
        catch (_: ProcessCanceledException) {
        }
    }

    private fun artifact(entries: Map<String, String>, jar: Boolean): Path {
        val root = Files.createTempDirectory(directory, "artifact ")
        if (jar) {
            val path = root.resolve("plugins #1.jar")
            JarOutputStream(Files.newOutputStream(path)).use { output ->
                for ((name, content) in entries) {
                    output.putNextEntry(JarEntry(name))
                    output.write(content.toByteArray(Charsets.UTF_8))
                    output.closeEntry()
                }
            }
            return path
        }
        for ((name, content) in entries) {
            val path = root.resolve(name)
            Files.createDirectories(path.parent)
            Files.writeString(path, content)
        }
        return root
    }
}