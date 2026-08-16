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

package dev.karmakrafts.kcml.agent.mixin

import dev.karmakrafts.kcml.agent.asm.Types
import dev.karmakrafts.kcml.agent.asm.dottedName
import dev.karmakrafts.kcml.agent.asm.getInvisibleAnnotation
import dev.karmakrafts.kcml.agent.asm.getValue
import dev.karmakrafts.kcml.agent.asm.hasInvisibleAnnotation
import dev.karmakrafts.kcml.agent.asm.restoreParameters
import dev.karmakrafts.kcml.agent.log.Logger
import dev.karmakrafts.kcml.agent.log.error
import dev.karmakrafts.kcml.agent.mixin.component.MixinComponents
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.time.Clock

internal class MixinLoader(
    private val logger: Logger
) {
    val components: MixinComponents = MixinComponents(logger)

    val mixins: List<Mixin>
        field: ArrayList<Mixin> = ArrayList()

    @Synchronized
    fun load(paths: List<Path>) {
        mixins.clear() // Allow re-loading all mixins
        val startTime = Clock.System.now()
        for (path in paths) {
            mixins += when {
                path.isRegularFile() && path.extension == "jar" -> loadFromJar(path)
                else -> {
                    logger.warn { "Unsupported path $path for MixinLoader, skipping" }
                    continue
                }
            }
        }
        val time = Clock.System.now() - startTime
        logger.info { "Loaded ${mixins.size} mixins in ${time.inWholeMilliseconds}ms" }
    }

    fun isMixin(type: Type): Boolean = mixins.any { mixin ->
        mixin.mixinClass.name == type.internalName
    }

    private fun isValidMixin(classNode: ClassNode): Boolean { // @formatter:off
        return classNode.hasInvisibleAnnotation(Types.Mixin.directMixin)
            || classNode.hasInvisibleAnnotation(Types.Mixin.indirectMixin)
    } // @formatter:on

    private fun createMixin(classNode: ClassNode): Result<Mixin> {
        val (targetClassType, priority) = when {
            classNode.hasInvisibleAnnotation(Types.Mixin.directMixin) -> {
                val annotation = classNode.getInvisibleAnnotation(Types.Mixin.directMixin)
                annotation.getValue<Type>("target")?.let { type ->
                    type to (annotation.getValue<Int>("priority") ?: 0)
                } ?: return Result.failure(Throwable("Mixin ${classNode.dottedName} is missing a target"))
            }

            classNode.hasInvisibleAnnotation(Types.Mixin.indirectMixin) -> {
                val annotation = classNode.getInvisibleAnnotation(Types.Mixin.indirectMixin)
                Type.getObjectType(annotation.getValue<String>("target")?.replace('.', '/'))?.let { type ->
                    type to (annotation.getValue<Int>("priority") ?: 0)
                } ?: return Result.failure(Throwable("Mixin ${classNode.dottedName} is missing a target"))
            }

            else -> return Result.failure(Throwable("Unsupported mixin definition for class ${classNode.dottedName}"))
        }
        logger.info { "Loaded mixin ${classNode.dottedName}" }
        return Result.success(Mixin(classNode, targetClassType, priority, logger, this))
    }

    private fun preprocessClass(classNode: ClassNode) {
        for (method in classNode.methods) {
            method.restoreParameters()
        }
    }

    private fun loadFromJar(jarPath: Path): List<Mixin> {
        logger.info { "Loading mixins from JAR at $jarPath" }
        return JarFile(jarPath.toFile()).use { jarFile ->
            // @formatter:off
            jarFile.stream()
                .parallel()
                .filter { entry -> !entry.isDirectory && entry.realName.endsWith(".class") }
                .map { entry ->
                    jarFile.getInputStream(entry).use { classStream ->
                        val classBytes = classStream.readBytes()
                        val reader = ClassReader(classBytes)
                        val classNode = ClassNode()
                        reader.accept(classNode, 0)
                        preprocessClass(classNode)
                        classNode
                    }
                }
                .filter(::isValidMixin)
                .map { classNode ->
                    createMixin(classNode).fold(
                        onSuccess = { mixin -> mixin },
                        onFailure = { error ->
                            logger.error(error) { "Could not create complete Mixin from class ${classNode.dottedName}" }
                            null
                        }
                    )
                }
                .toList()
                .filterNotNull()
                .sorted() // Sort mixins by priority
            // @formatter:on
        }
    }
}