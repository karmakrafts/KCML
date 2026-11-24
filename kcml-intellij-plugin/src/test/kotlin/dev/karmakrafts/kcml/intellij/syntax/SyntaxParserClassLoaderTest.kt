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

package dev.karmakrafts.kcml.intellij.syntax

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.karmakrafts.kcml.intellij.util.TypeNames
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyntaxParserClassLoaderTest : BasePlatformTestCase() {
    fun testParallelLoadsDefinePrivateParserClassesOnceAndShareIdeTypes() {
        val loader = SyntaxParserClassLoader(javaClass.classLoader)
        assertTrue("Parser instrumentation must use per-class loading locks", loader.isRegisteredAsParallelCapable)
        val names = listOf(TypeNames.KOTLIN_PARSER, TypeNames.KOTLIN_PARSING, TypeNames.KOTLIN_EXPRESSION_PARSING)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val classes = executor.invokeAll((0 until 24).map { index ->
                Callable { loader.loadClass(names[index % names.size]) }
            }, 10, TimeUnit.SECONDS).map { it.get() }
            for ((index, type) in classes.withIndex()) {
                assertSame(loader, type.classLoader)
                assertSame(classes[index % names.size], type)
                assertNotSame(javaClass.classLoader.loadClass(type.name), type)
            }
            assertSame(org.jetbrains.kotlin.psi.KtFile::class.java, loader.loadClass("org.jetbrains.kotlin.psi.KtFile"))
        }
        finally {
            executor.shutdownNow()
        }
    }
}