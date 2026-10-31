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

package dev.karmakrafts.kcml.agent

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.log.Logger
import dev.karmakrafts.kcml.agent.log.NoopLogger
import dev.karmakrafts.kcml.agent.log.RemoteLogger
import dev.karmakrafts.kcml.agent.mixin.MixinClassTransformer
import dev.karmakrafts.kcml.agent.mixin.MixinLoader
import dev.karmakrafts.kcml.agent.mixin.source.BuiltinMixinSource
import dev.karmakrafts.kcml.agent.util.AgentCommClient
import dev.karmakrafts.kcml.agent.util.KCMLAgentArguments
import dev.karmakrafts.kcml.agent.util.commPort
import dev.karmakrafts.kcml.agent.util.logging
import dev.karmakrafts.kcml.api.mixin.MixinBridge
import dev.karmakrafts.kcml.api.mixin.MixinBridgeABI
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.lang.instrument.Instrumentation
import java.util.jar.JarFile
import kotlin.io.path.Path

@Suppress("UNUSED")
object KCMLAgent {
    internal fun injectMixinBridge( // @formatter:off
        path: String,
        logger: Logger,
        instrumentation: Instrumentation
    ) { // @formatter:on
        logger.info { "Injecting KCML mixin bridge JAR $path into bootstrap classpath" }
        val file = JarFile(File(path))
        instrumentation.appendToBootstrapClassLoaderSearch(file)
    }

    private fun readMixinBridgeABI(path: String): Int {
        var abi: Int? = null
        JarFile(File(path)).use { jar ->
            val entry = checkNotNull(jar.getJarEntry("${Types.Mixin.bridge.internalName}.class")) {
                "MixinBridge class is missing from $path"
            }
            jar.getInputStream(entry).use { input ->
                ClassReader(input).accept(object : ClassVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        if (!visible || descriptor != Types.Mixin.bridgeABI.descriptor) return null
                        return object : AnnotationVisitor(Opcodes.ASM9) {
                            override fun visit(name: String, value: Any) {
                                if (name == "value") abi = value as? Int
                            }
                        }
                    }
                }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            }
        }
        return checkNotNull(abi) { "MixinBridge in $path has no @MixinBridgeABI value" }
    }

    private fun verifyMixinBridge(path: String) {
        val failure = try {
            val expectedABI = readMixinBridgeABI(path)
            val loader = KCMLAgent::class.java.classLoader
            val bridge = Class.forName(Types.Mixin.bridge.className, false, loader)
            val annotation = checkNotNull(bridge.getAnnotation(MixinBridgeABI::class.java)) {
                "Loaded MixinBridge has no @MixinBridgeABI annotation (expected ABI $expectedABI)"
            }
            check(annotation.value == expectedABI) {
                "Loaded MixinBridge ABI ${annotation.value} does not match expected ABI $expectedABI"
            }
            return
        } catch (error: ReflectiveOperationException) {
            error
        } catch (error: LinkageError) {
            error
        } catch (error: IllegalStateException) {
            error
        }
        throw IllegalStateException(
            "KCML could not load the required mixin bridge ABI from $path: ${failure.message}. This JVM may still be using a bridge from an earlier build. Bootstrap classes cannot be replaced in a running JVM. Run './gradlew --stop' and rerun the build (or restart the compiler JVM if not using in-process compilation).",
            failure
        )
    }

    @JvmStatic
    fun agentmain(joinedArgs: String?, instrumentation: Instrumentation) {
        require(joinedArgs != null) { "KCML compiler agent requires initial startup options" }
        val args = KCMLAgentArguments.parse(joinedArgs)
        val commClient = AgentCommClient(args.commPort)
        val handshake = try {
            commClient.handshake()
        } catch (error: Throwable) {
            commClient.close()
            throw error
        }
        val logger = if (args.logging) RemoteLogger(commClient) else NoopLogger
        logger.info { "Agent invoked with options: $joinedArgs" }
        logger.info { "Initializing KCML compiler agent.." }
        try {
            injectMixinBridge(handshake.bridgePath, logger, instrumentation)
            verifyMixinBridge(handshake.bridgePath)
        } catch (error: Throwable) {
            commClient.close()
            throw error
        }
        // ================== Mixin Bridge boundary ==================
        logger.info { "Initializing default constant table entries" }
        MixinBridge.INSTANCE.apply {
            putConstant(BuiltinMixinSource, "loader_path", handshake.loaderPath)
            putConstant(BuiltinMixinSource, "bridge_path", handshake.bridgePath)
            putConstant(BuiltinMixinSource, "plugin_paths", handshake.pluginPaths)
        }
        val loader = MixinLoader(logger)
        logger.info { "Loading builtin loader and plugin mixins" }
        loader.load((listOf(handshake.loaderPath) + handshake.pluginPaths).map(::Path), Path(handshake.loaderPath))
        val dumpPath = if (handshake.dumpClasses) handshake.dumpPath?.let(::Path) else null
        val canRetransform = instrumentation.isRetransformClassesSupported
        instrumentation.addTransformer(MixinClassTransformer(logger, loader, dumpPath), canRetransform)
        if (canRetransform) {
            val targets = loader.mixins.map { mixin -> mixin.target.className }.toSet()
            val loadedTargets = instrumentation.allLoadedClasses.filter { type ->
                type.name in targets && instrumentation.isModifiableClass(type)
            }
            for (type in loadedTargets) {
                logger.info { "Patching mixin target ${type.name} loaded before agent attachment" }
                instrumentation.retransformClasses(type)
            }
        }
    }
}