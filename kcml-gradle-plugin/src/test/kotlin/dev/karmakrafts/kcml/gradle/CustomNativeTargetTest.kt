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

package dev.karmakrafts.kcml.gradle

import dev.karmakrafts.kcml.target.proto.NativeTargetDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.properties.PropertyValue
import org.gradle.internal.properties.PropertyVisitor
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.internal.DeferredUtil
import org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments
import org.jetbrains.kotlin.cli.common.arguments.K2NativeCompilerArguments
import org.jetbrains.kotlin.gradle.InternalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.konan.target.Architecture
import org.jetbrains.kotlin.konan.target.BinaryFormat
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.HostManager
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CustomNativeTargetTest {
    private fun withProject(
        crossCompilationEnabled: Boolean = true, block: (Project, KotlinMultiplatformExtension) -> Unit
    ) {
        val directory = Files.createTempDirectory(Path.of("build"), "custom-native-").toAbsolutePath()
        try {
            val project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build()
            project.extensions.extraProperties.set(
                "kotlin.native.enableKlibsCrossCompilation", crossCompilationEnabled.toString()
            )
            project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            project.pluginManager.apply(KCMLGradlePlugin::class.java)
            project.configurations.getByName("kcmlRuntime").dependencies.clear()
            block(project, project.extensions.getByType(KotlinMultiplatformExtension::class.java))
        }
        finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun uniqueName(prefix: String) = "${prefix}_${UUID.randomUUID().toString().replace("-", "")}"

    private fun String.readNativeTarget(): String = Files.readString(Path.of(this))

    private fun parseNativePluginOption(option: String): K2NativeCompilerArguments = K2NativeCompilerArguments().apply {
        Class.forName("org.jetbrains.kotlin.cli.common.arguments.ParseCommandLineArgumentsKt").getMethod(
                "parseCommandLineArguments",
                List::class.java,
                CommonToolArguments::class.java,
                Boolean::class.javaPrimitiveType
            ).invoke(null, listOf("-P", option), this, false)
    }

    private fun Task.registeredInput(name: String): Any? {
        var found = false
        var result: Any? = null
        (inputs as TaskInputsInternal).visitRegisteredProperties(object : PropertyVisitor {
            override fun visitInputProperty(propertyName: String, value: PropertyValue, optional: Boolean) {
                if (propertyName == name) {
                    found = true
                    result = DeferredUtil.unpackOrNull(value)
                }
            }
        })
        assertTrue(found, "Missing task input '$name'")
        return result
    }

    @Test
    @Suppress("DEPRECATION")
    fun `runtime family and architecture create independent real Native targets`() = withProject { project, kotlin ->
        val predefined = KonanTarget.predefinedTargets.toMap()
        val host = HostManager.host
        val family = nativeFamily(uniqueName("FAMILY"))
        val architecture = nativeArchitecture(uniqueName("ARCH"), 64)
        assertSame(family, Family.valueOf(family.name))
        assertSame(architecture, Architecture.valueOf(architecture.name))
        assertTrue(family in Family.entries)
        assertTrue(architecture in Architecture.entries)
        assertEquals(64, architecture.bitness)
        assertEquals(
            listOf("kexe", "lib", "so", "lib", "a"), listOf(
                family.exeSuffix, family.dynamicPrefix, family.dynamicSuffix, family.staticPrefix, family.staticSuffix
            )
        )
        assertSame(family, nativeFamily(family.name))
        assertSame(architecture, nativeArchitecture(architecture.name, 64))
        val first = kotlin.customNativeTarget("custom_one", family, architecture)
        val second = kotlin.customNativeTarget("custom_two", family, architecture)
        listOf(first, second).forEach { target ->
            assertSame(target, kotlin.targets.getByName(target.name))
            assertEquals(target.name, target.konanTarget.name)
            assertSame(family, target.konanTarget.family)
            assertSame(architecture, target.konanTarget.architecture)
            assertNotEquals(host, target.konanTarget)
            assertEquals(setOf("main", "test"), target.compilations.names)
            target.compilations.forEach { compilation ->
                assertEquals(
                    "${target.name}${compilation.name.replaceFirstChar(Char::uppercaseChar)}",
                    compilation.defaultSourceSet.name
                )
                assertSame(
                    compilation.compileTaskProvider.get(), project.tasks.getByName(compilation.compileKotlinTaskName)
                )
            }
            target.binaries.executable()
        }
        assertNotSame(first.konanTarget, second.konanTarget)
        assertNotSame(
            first.compilations.getByName("main").defaultSourceSet,
            second.compilations.getByName("main").defaultSourceSet
        )
        assertNotSame(
            first.compilations.getByName("main").compileTaskProvider.get(),
            second.compilations.getByName("main").compileTaskProvider.get()
        )
        (project as ProjectInternal).evaluate()
        assertNotSame(first.binaries.first().linkTaskProvider.get(), second.binaries.first().linkTaskProvider.get())
        listOf(first, second).forEach { target ->
            target.compilations.forEach { assertTrue(it.compileTaskProvider.get().enabled) }
            target.binaries.forEach { assertTrue(it.linkTaskProvider.get().enabled) }
        }
        assertSame(host, HostManager.host)
        assertEquals(predefined, KonanTarget.predefinedTargets)
        assertFailsWith<IllegalArgumentException> { kotlin.customNativeTarget("custom_one", family, architecture) }
        assertFailsWith<IllegalArgumentException> { kotlin.customNativeTarget(" ", family, architecture) }
    }

    @Test
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    fun `custom Native compilations remain executable with sources and libraries after evaluation`() =
        withProject(false) { project, kotlin ->
            val predefined = KonanTarget.predefinedTargets.toMap()
            val host = HostManager.host
            val family = nativeFamily(uniqueName("COMPILE_FAMILY"))
            val architecture = nativeArchitecture(uniqueName("COMPILE_ARCH"), 64)
            val target = kotlin.customNativeTarget("linux_x64", family, architecture)
            val source = project.file("src/commonMain/kotlin/Probe.kt").apply {
                parentFile.mkdirs()
                writeText("fun probe() = 42")
            }
            val testSource = project.file("src/commonTest/kotlin/ProbeTest.kt").apply {
                parentFile.mkdirs()
                writeText("fun probeTest() = 42")
            }
            val library = project.file("dependency.klib").apply { writeText("dependency") }
            target.binaries.staticLib()
            (project as ProjectInternal).evaluate()
            target.compilations.all { it.compileDependencyFiles = project.files(library) }

            target.compilations.forEach { compilation ->
                val task = compilation.compileTaskProvider.get()
                assertTrue(task.enabled)
                assertTrue(
                    (task as TaskInternal).onlyIf.isSatisfiedBy(task), "${task.name} must not retain KGP's host guard"
                )
                assertEquals("KotlinNativeFromToolchainProvider", task.kotlinNativeProvider.get().javaClass.simpleName)
                assertSame(target.konanTarget, task.konanTarget)
                assertEquals(setOf(library), task.libraries.files)
                assertTrue((if (compilation.name == "main") source else testSource) in task.sources.files)
                task.onlyIf { false }
                assertFalse(task.onlyIf.isSatisfiedBy(task), "User execution predicates must remain effective")
            }
            val compilationOutputs = target.binaries.map { binary ->
                val link = binary.linkTaskProvider.get()
                val output = binary.compilation.compileTaskProvider.get().outputFile.get()
                assertTrue(output in binary.compilation.output.classesDirs.files)
                assertFalse(output.exists())
                assertFalse(output in link.sources.files, "Missing compile outputs must not become link sources")
                assertTrue(link.sources.isEmpty)
                output
            }.toSet()
            compilationOutputs.forEach { output ->
                output.parentFile.mkdirs()
                assertTrue(output.createNewFile())
            }
            target.binaries.forEach { binary ->
                val link = binary.linkTaskProvider.get()
                assertTrue(link.enabled)
                assertTrue(binary.compilation.compileTaskProvider.get().outputFile.get() in link.sources.files)
            }
            assertSame(host, HostManager.host)
            assertEquals(predefined, KonanTarget.predefinedTargets)
        }

    @Test
    fun `Native enum definitions reuse matching entries and reject conflicting constructors`() {
        val family = nativeFamily(uniqueName("REUSED_FAMILY"), ".run", "dyn", ".dyn", "static", ".archive")
        repeat(2) {
            assertSame(family, nativeFamily(family.name, ".run", "dyn", ".dyn", "static", ".archive"))
            assertSame(Family.LINUX, nativeFamily("LINUX"))
            assertSame(Architecture.ARM64, nativeArchitecture("ARM64", Architecture.ARM64.bitness))
        }
        assertEquals(
            listOf(".run", "dyn", ".dyn", "static", ".archive"), listOf(
                family.exeSuffix, family.dynamicPrefix, family.dynamicSuffix, family.staticPrefix, family.staticSuffix
            )
        )
        assertFailsWith<IllegalArgumentException> {
            nativeFamily(
                family.name, "other", "dyn", ".dyn", "static", ".archive"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            nativeFamily(
                family.name, ".run", "other", ".dyn", "static", ".archive"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            nativeFamily(
                family.name, ".run", "dyn", "other", "static", ".archive"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            nativeFamily(
                family.name, ".run", "dyn", ".dyn", "other", ".archive"
            )
        }
        assertFailsWith<IllegalArgumentException> {
            nativeFamily(
                family.name, ".run", "dyn", ".dyn", "static", "other"
            )
        }
        assertSame(family, Family.valueOf(family.name))
        assertFailsWith<IllegalArgumentException> { nativeFamily("LINUX", exeSuffix = "other") }
        val architecture = nativeArchitecture(uniqueName("REUSED_ARCH"), 32)
        repeat(2) { assertSame(architecture, nativeArchitecture(architecture.name, 32)) }
        assertFailsWith<IllegalArgumentException> { nativeArchitecture(architecture.name, 64) }
        assertSame(architecture, Architecture.valueOf(architecture.name))
        assertFailsWith<IllegalArgumentException> { nativeArchitecture("ARM64", 32) }
    }

    @Test
    fun `supplied Konan instance and its default name are preserved`() = withProject { _, kotlin ->
        val supplied = customKonanTarget("real_name", Family.LINUX, Architecture.ARM64)
        val target = kotlin.customNativeTarget(supplied)
        assertEquals("real_name", target.name)
        assertSame(supplied, target.konanTarget)
        val alias = kotlin.customNativeTarget("different_name", supplied)
        assertSame(supplied, alias.konanTarget)
        assertEquals(
            "real_name", Json.parseToJsonElement(alias.nativeTargetJson()!!).jsonObject["name"]?.jsonPrimitive?.content
        )
        val builtin = kotlin.customNativeTarget("explicit_builtin", KonanTarget.MINGW_X64)
        assertSame(KonanTarget.MINGW_X64, builtin.konanTarget)
    }

    @Test
    @OptIn(InternalKotlinGradlePluginApi::class)
    fun `late architecture options reach compilation link and native cache inputs`() = withProject { project, kotlin ->
        val family = nativeFamily(uniqueName("OPTIONS"), ".run", "dyn", ".dyn", "static", ".archive")
        val architecture = nativeArchitecture(uniqueName("CPU"), 32)
        val target = kotlin.customNativeTarget("options_native", family, architecture)
        val other = kotlin.customNativeTarget("other_native", family, architecture)
        target.binaries.executable()
        other.binaries.executable()
        val compilation = target.compilations.getByName("main")
        val provider = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java).applyToCompilation(compilation)
        assertEquals(
            "{\"properties\":{}}", Json.parseToJsonElement(
                provider.get().single { it.key == "nativeTarget" }.value.readNativeTarget()
            ).jsonObject["llvm"].toString()
        )
        (project as ProjectInternal).evaluate()
        val binary = target.binaries.first { it.compilation == compilation }
        val link = binary.linkTaskProvider.get()
        val cache = link.taskDependencies.getDependencies(link).filterIsInstance<KCMLPrepareNativeCacheTask>().single()
        val before = link.registeredInput("kcmlPluginOptions")
        val nativeBefore = link.registeredInput("kcmlNativeTarget")
        val compileTask = compilation.compileTaskProvider.get()
        val compileBefore = compileTask.registeredInput("kcmlNativeTarget")
        val cacheBefore = cache.inputs.properties["kcmlNativeTarget${target.name}"]
        target.architecture {
            targetTriple = "riscv32-unknown-none"
            cpu = "generic-rv32"
            features = "+m,+a"
            dataLayout = "e-p:32:32"
            binaryFormat = BinaryFormat.ELF
            supports64BitAtomics = false
            supportsUnalignedAccess = true
            properties["llvmHome"] = "/custom/llvm"
            properties["targetSysRoot.options_native"] = "/custom/sysroot"
        }
        val expected =
            """{"explicitTarget":true,"name":"options_native","family":{"name":"${family.name}","exeSuffix":".run","dynamicPrefix":"dyn","dynamicSuffix":".dyn","staticPrefix":"static","staticSuffix":".archive"},"architecture":{"name":"${architecture.name}","bitness":32},"llvm":{"targetTriple":"riscv32-unknown-none","cpu":"generic-rv32","features":"+m,+a","dataLayout":"e-p:32:32","binaryFormat":"ELF","supports64BitAtomics":false,"supportsUnalignedAccess":true,"properties":{"llvmHome":"/custom/llvm","targetSysRoot.options_native":"/custom/sysroot"}}}"""
        val options = provider.get()
        val descriptorPath = options.single { it.key == "nativeTarget" }.value
        assertEquals(expected, descriptorPath.readNativeTarget())
        assertEquals(expected, target.nativeTargetJson())
        assertTrue(options.indexOfFirst { it.key == "nativeTarget" } < options.indexOfFirst { it.key == "agentCommPort" })
        val compileOptions =
            compileTask.kotlinPluginData!!.get().options.arguments + compileTask.compilerPluginCommandLine
        assertEquals(
            listOf("plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget=$descriptorPath"),
            compileOptions.filter { it.startsWith("plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget=") })
        val arguments = link.kotlinPluginData!!.get().options.arguments
        assertEquals(
            listOf("plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget=$descriptorPath"),
            arguments.filter { it.startsWith("plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget=") })
        assertEquals(expected, cache.inputs.properties["kcmlNativeTarget${target.name}"])
        assertEquals(before, link.registeredInput("kcmlPluginOptions"))
        assertEquals(expected, link.registeredInput("kcmlNativeTarget"))
        assertEquals(expected, compileTask.registeredInput("kcmlNativeTarget"))
        assertNotEquals(nativeBefore, link.registeredInput("kcmlNativeTarget"))
        assertNotEquals(compileBefore, compileTask.registeredInput("kcmlNativeTarget"))
        assertNotEquals(cacheBefore, cache.inputs.properties["kcmlNativeTarget${target.name}"])
        assertFalse(other.nativeTargetJson()!!.contains("llvmHome"))
        target.architecture {
            cpu = null
            features = null
            dataLayout = null
            targetTriple = null
            binaryFormat = null
            supports64BitAtomics = null
            supportsUnalignedAccess = null
            properties.clear()
        }
        assertEquals(
            "{\"properties\":{}}", Json.parseToJsonElement(
                provider.get().single { it.key == "nativeTarget" }.value.readNativeTarget()
            ).jsonObject["llvm"].toString()
        )
    }

    @Test
    fun `Native descriptor files are generated on demand in the configured build directory`() = withProject { project, kotlin ->
        project.layout.buildDirectory.set(project.layout.projectDirectory.dir("custom build 工具"))
        val target = kotlin.linuxX64()
        val other = kotlin.linuxX64("other")
        target.architecture { cpu = "first" }
        other.architecture { cpu = "other" }
        val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
        val provider = bootstrap.applyToCompilation(target.compilations.getByName("main"))
        assertFalse(project.layout.buildDirectory.get().asFile.exists())
        val path = Path.of(provider.get().single { it.key == "nativeTarget" }.value)
        assertTrue(path.isAbsolute)
        assertTrue(path.startsWith(project.layout.buildDirectory.get().asFile.toPath()))
        val option = "plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget=$path"
        assertEquals(listOf(option), parseNativePluginOption(option).pluginOptions.toList())
        assertEquals(target.nativeTargetJson(), Files.readString(path))
        val modified = Files.getLastModifiedTime(path)
        assertEquals(path.toString(), provider.get().single { it.key == "nativeTarget" }.value)
        assertEquals(modified, Files.getLastModifiedTime(path))
        target.architecture { cpu = "updated" }
        assertEquals(path.toString(), provider.get().single { it.key == "nativeTarget" }.value)
        assertEquals(target.nativeTargetJson(), Files.readString(path))
        val otherPath = Path.of(bootstrap.applyToCompilation(other.compilations.getByName("main"))
            .get().single { it.key == "nativeTarget" }.value)
        assertNotEquals(path, otherPath)
        assertEquals(other.nativeTargetJson(), Files.readString(otherPath))
        Files.delete(path)
        assertEquals(path.toString(), provider.get().single { it.key == "nativeTarget" }.value)
        assertEquals(target.nativeTargetJson(), Files.readString(path))
    }

    @Test
    fun `Native descriptor paths survive command line parsing without splitting JSON`() =
        withProject { project, kotlin ->
            val target = kotlin.linuxX64()
            target.architecture {
                features = "+m,+a"
                properties["llvmHome"] = "/custom/工具/llvm,bin:+/="
                properties["custom"] = "quoted \"value\"\nsecond line"
            }
            val json = target.nativeTargetJson()!!
            val descriptor = NativeTargetDescriptor.json.decodeFromString(NativeTargetDescriptor.serializer(), json)
            assertFalse(descriptor.explicitTarget)
            assertEquals(target.konanTarget.name, descriptor.name)
            assertEquals("+m,+a", descriptor.llvm.features)
            assertEquals("/custom/工具/llvm,bin:+/=", descriptor.llvm.properties["llvmHome"])
            assertEquals("quoted \"value\"\nsecond line", descriptor.llvm.properties["custom"])
            assertEquals(json, NativeTargetDescriptor.json.encodeToString(NativeTargetDescriptor.serializer(), descriptor))
            val prefix = "plugin:${BuildInfo.PLUGIN_ID}.bootstrap:nativeTarget="
            val raw = parseNativePluginOption("$prefix$json")
            assertNotEquals(listOf("$prefix$json"), raw.pluginOptions.toList())

            val option = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
                .applyToCompilation(target.compilations.getByName("main"))
                .get()
                .single { it.key == "nativeTarget" }
            assertTrue(Files.isRegularFile(Path.of(option.value)))
            val parsed = parseNativePluginOption("$prefix${option.value}")
            assertEquals(listOf("$prefix${option.value}"), parsed.pluginOptions.toList())
            assertEquals(json, parsed.pluginOptions.single().removePrefix(prefix).readNativeTarget())
            assertEquals(json, target.nativeTargetJson())
        }

    @Test
    fun `ordinary and legacy Native targets only send metadata after architecture DSL`() =
        withProject { project, kotlin ->
            val regular = kotlin.linuxX64()
            val legacy = kotlin.customNativeTarget("legacy_native")
            val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
            listOf(regular, legacy).forEach { target ->
                val provider = bootstrap.applyToCompilation(target.compilations.getByName("main"))
                assertTrue(provider.get().none { it.key == "nativeTarget" })
                assertFalse(project.layout.buildDirectory.dir("kcml_native_targets").get().asFile
                    .resolve("${target.name}.json").exists())
                target.architecture { cpu = "late-cpu" }
                assertEquals(
                    target.konanTarget.name, Json.parseToJsonElement(
                        provider.get().single { it.key == "nativeTarget" }.value.readNativeTarget()
                    ).jsonObject["name"]?.jsonPrimitive?.content
                )
            }
        }

    @Test
    fun `empty features and arbitrary property values are preserved in metadata`() = withProject { _, kotlin ->
        val target = kotlin.linuxX64()
        target.architecture {
            features = ""
            properties["runtimeDefinitions"] = ""
            properties["targetCpuFeatures.linux_x64"] = ""
            properties["dependencies.linux_x64"] = ""
            properties["customWhitespace"] = " \t"
        }
        val llvm = Json.parseToJsonElement(target.nativeTargetJson()!!).jsonObject.getValue("llvm").jsonObject
        assertEquals("", llvm.getValue("features").jsonPrimitive.content)
        val properties = llvm.getValue("properties").jsonObject
        listOf("runtimeDefinitions", "targetCpuFeatures.linux_x64", "dependencies.linux_x64").forEach {
            assertEquals("", properties.getValue(it).jsonPrimitive.content)
        }
        assertEquals(" \t", properties.getValue("customWhitespace").jsonPrimitive.content)
        target.architecture { features = null }
        val defaults = Json.parseToJsonElement(target.nativeTargetJson()!!).jsonObject.getValue("llvm").jsonObject
        assertFalse("features" in defaults)
        assertEquals(properties, defaults.getValue("properties").jsonObject)
    }

    @Test
    fun `invalid configuration is rejected before agent port bootstrap`() = withProject { project, kotlin ->
        val settings = NativeArchitectureConfiguration()
        assertNull(settings.targetTriple)
        assertNull(settings.cpu)
        assertNull(settings.features)
        assertNull(settings.dataLayout)
        assertNull(settings.binaryFormat)
        assertNull(settings.supports64BitAtomics)
        assertNull(settings.supportsUnalignedAccess)
        assertFailsWith<IllegalArgumentException> { settings.targetTriple = " " }
        assertFailsWith<IllegalArgumentException> { settings.cpu = "" }
        assertFailsWith<IllegalArgumentException> { settings.features = "\t" }
        assertFailsWith<IllegalArgumentException> { settings.dataLayout = "\n" }
        assertFailsWith<IllegalArgumentException> { nativeFamily(" ") }
        assertFailsWith<IllegalArgumentException> { nativeArchitecture("", 64) }
        assertFailsWith<IllegalArgumentException> { nativeArchitecture(uniqueName("BAD"), 0) }
        val target = kotlin.linuxX64()
        val bootstrap = project.plugins.getPlugin(KCMLBootstrapGradlePlugin::class.java)
        val provider = bootstrap.applyToCompilation(target.compilations.getByName("main"))
        target.architecture { properties[" "] = "invalid" }
        project.extensions.getByType(KCMLExtension::class.java).apply {
            agentPortRangeStart.set(1)
            agentPortRangeEnd.set(1)
        }
        assertTrue(
            assertFailsWith<IllegalArgumentException> { provider.get() }.message.orEmpty().contains("property keys")
        )
        target.architecture { properties.clear(); properties[""] = "" }
        assertTrue(
            assertFailsWith<IllegalArgumentException> { provider.get() }.message.orEmpty().contains("property keys")
        )
    }
}