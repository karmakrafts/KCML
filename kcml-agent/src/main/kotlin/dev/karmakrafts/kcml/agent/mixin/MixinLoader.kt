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
import dev.karmakrafts.kcml.agent.asm.getAnnotation
import dev.karmakrafts.kcml.agent.asm.getValue
import dev.karmakrafts.kcml.agent.asm.hasAnnotation
import dev.karmakrafts.kcml.agent.asm.implements
import dev.karmakrafts.kcml.agent.log.Logger
import dev.karmakrafts.kcml.agent.log.error
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

internal class MixinLoader(
    private val logger: Logger
) {
    fun load(paths: List<Path>): List<Mixin> {
        val mixins = ArrayList<Mixin>()
        for (path in paths) {
            mixins += when {
                path.isRegularFile() && path.extension == "jar" -> loadFromJar(path)
                else -> error("MixinLoader does not currently support loading mixins from $path")
            }
        }
        return mixins
    }

    private fun isValidMixin(classNode: ClassNode): Boolean { // @formatter:off
        return (classNode.hasAnnotation(Types.Mixin.directMixin)
            || classNode.hasAnnotation(Types.Mixin.indirectMixin))
            && classNode.implements(Types.Mixin.mixin)
    } // @formatter:on

    private fun createMixin(classNode: ClassNode): Result<Mixin> {
        val (targetClassType, priority) = when {
            classNode.hasAnnotation(Types.Mixin.directMixin) -> {
                val annotation = classNode.getAnnotation(Types.Mixin.directMixin)
                annotation.getValue<Type>("target")?.let { type ->
                    type to (annotation.getValue<Int>("priority") ?: 0)
                } ?: return Result.failure(Throwable("Mixin ${classNode.dottedName} is missing a target"))
            }

            classNode.hasAnnotation(Types.Mixin.indirectMixin) -> {
                val annotation = classNode.getAnnotation(Types.Mixin.indirectMixin)
                Type.getObjectType(annotation.getValue<String>("target")?.replace('.', '/'))?.let { type ->
                    type to (annotation.getValue<Int>("priority") ?: 0)
                } ?: return Result.failure(Throwable("Mixin ${classNode.dottedName} is missing a target"))
            }

            else -> return Result.failure(Throwable("Unsupported mixin definition for class ${classNode.dottedName}"))
        }
        logger.info { "Loaded mixin ${classNode.dottedName}" }
        return Result.success(Mixin(classNode, targetClassType, priority))
    }

    private fun loadFromJar(jarPath: Path): List<Mixin> {
        logger.info { "Loading mixins from JAR at $jarPath" }
        return JarFile(jarPath.toFile()).use { jarFile ->
            // @formatter:off
            jarFile.versionedStream()
                .parallel()
                .filter { entry -> entry.realName.endsWith(".class") }
                .map { entry ->
                    jarFile.getInputStream(entry).use { classStream ->
                        val classBytes = classStream.readBytes()
                        val reader = ClassReader(classBytes)
                        val classNode = ClassNode()
                        reader.accept(classNode, 0)
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
            // @formatter:on
        }
    }
}