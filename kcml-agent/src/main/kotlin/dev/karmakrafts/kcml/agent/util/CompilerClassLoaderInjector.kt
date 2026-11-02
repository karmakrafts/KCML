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

package dev.karmakrafts.kcml.agent.util

import java.lang.instrument.Instrumentation
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import java.util.jar.JarFile

internal class CompilerClassLoaderInjector(
    private val path: Path, private val instrumentation: Instrumentation
) : Consumer<ClassLoader> {
    private val initializedLoaders: MutableSet<ClassLoader> = Collections.newSetFromMap(WeakHashMap())

    @Synchronized
    override fun accept(loader: ClassLoader) {
        if (loader in initializedLoaders) return
        when {
            loader === ClassLoader.getSystemClassLoader() -> {
                JarFile(path.toFile()).use(instrumentation::appendToSystemClassLoaderSearch)
            }

            loader is URLClassLoader -> {
                val method = URLClassLoader::class.java.getDeclaredMethod("addURL", URL::class.java)
                val agentModule = javaClass.module
                instrumentation.redefineModule(
                    URLClassLoader::class.java.module,
                    emptySet(),
                    emptyMap(),
                    mapOf("java.net" to setOf(agentModule)),
                    emptySet(),
                    emptyMap()
                )
                method.isAccessible = true
                method.invoke(loader, path.toUri().toURL())
            }

            else -> error("KCML cannot inject its plugin loader into compiler class loader ${loader.javaClass.name}")
        }
        initializedLoaders += loader
    }
}