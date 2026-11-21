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

package dev.karmakrafts.kcml

import java.util.jar.JarFile
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class CompilerRuntimePackagingTest {
    @Test
    fun `embedded loader includes custom Native target runtime dependencies`() {
        JarFile(System.getProperty("kcml.compiler.plugin.jar")).use { compiler ->
            compiler.getInputStream(compiler.getJarEntry("kcml-loader.jar")).use { stream ->
                ZipInputStream(stream).use { loader ->
                    val classes = generateSequence(loader::getNextEntry).map { it.name }.toSet()
                    for (name in listOf(
                        "dev/karmakrafts/kcml/hacks/EnumExtender.class",
                        "dev/karmakrafts/kcml/hacks/SyntheticClass.class",
                        "dev/karmakrafts/kcml/hacks/SyntheticDelegateClass.class",
                        "dev/karmakrafts/kcml/hacks/UnsafeUtils.class",
                        "dev/karmakrafts/kcml/target/KonanTargets.class"
                    )) assertTrue(name in classes, "Missing compiler runtime dependency $name")
                    assertTrue(classes.any { it.endsWith("/org/objectweb/asm/ClassWriter.class") })
                }
            }
        }
    }
}