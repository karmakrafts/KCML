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

@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package dev.karmakrafts.kcml.gradle

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.internal.DefaultDomainObjectCollection
import org.gradle.api.internal.collections.DefaultCollectionEventRegister
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.ImmutableActionSet
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilationFactory
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTargetPreset
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTargetConfigurator
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

internal class CustomJvmTargetPreset(
    project: Project, private val originalTarget: KotlinTarget
) : KotlinOnlyTargetPreset<KotlinJvmTarget, KotlinJvmCompilation>(project) {
    override val name: String = "kcmlJvm"
    override val platformType: KotlinPlatformType = KotlinPlatformType.jvm

    override fun instantiateTarget(name: String): KotlinJvmTarget =
        project.objects.newInstance(KotlinJvmTarget::class.java, project)

    override fun createKotlinTargetConfigurator(): KotlinJvmTargetConfigurator = KotlinJvmTargetConfigurator()

    override fun createCompilationFactory(forTarget: KotlinJvmTarget): KotlinJvmCompilationFactory =
        object : KotlinJvmCompilationFactory(forTarget) {
            override fun create(name: String): KotlinJvmCompilation {
                val sourceSets = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets
                val registerField = DefaultDomainObjectCollection::class.java.getDeclaredField("eventRegister").apply {
                    isAccessible = true
                }
                val register = registerField.get(sourceSets)
                val actionsField = DefaultCollectionEventRegister::class.java.getDeclaredField("addActions").apply {
                    isAccessible = true
                }

                @Suppress("UNCHECKED_CAST") val previous = actionsField.get(register) as ImmutableActionSet<SourceSet>
                val unpack = ImmutableActionSet::class.java.declaredMethods.single { it.name == "unpackInto" }.apply {
                    isAccessible = true
                }
                val builderClass = unpack.parameterTypes.single()
                val builder = builderClass.enclosingClass.getMethod("builder").invoke(null)
                unpack.invoke(previous, builder)
                @Suppress("UNCHECKED_CAST") val actions =
                    builderClass.getMethod("build").invoke(builder) as Iterable<Action<SourceSet>>
                // Do not also attach this Java source set to Kotlin JVM's fixed target.
                val filtered = actions.filterNot { it.isJvmSourceSetListener() }
                check(filtered.size < actions.count()) { "Could not isolate the Kotlin JVM source-set listener" }
                actionsField.set(register, ImmutableActionSet.of(*filtered.toTypedArray()))
                try {
                    return super.create(name)
                }
                finally {
                    @Suppress("UNCHECKED_CAST") val current =
                        actionsField.get(register) as ImmutableActionSet<SourceSet>
                    actionsField.set(register, previous.mergeFrom(current))
                }
            }
        }

    private fun Any.isJvmSourceSetListener(
        visited: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())
    ): Boolean {
        if (!visited.add(this)) return false
        if (javaClass.name.startsWith("org.jetbrains.kotlin.gradle.plugin.AbstractKotlinPlugin\$Companion")) {
            return javaClass.declaredFields.any { field ->
                field.isAccessible = true
                field.get(this) === originalTarget
            }
        }
        return javaClass.declaredFields.any { field ->
            if (!Action::class.java.isAssignableFrom(field.type) && !Function::class.java.isAssignableFrom(field.type)) return@any false
            field.isAccessible = true
            field.get(this)?.isJvmSourceSetListener(visited) == true
        }
    }

    fun create(name: String): KotlinJvmTarget = createTargetInternal(name).apply {
        compilations.configureEach { compilation ->
            compilation.compileTaskProvider.configure { task ->
                (task as KotlinCompile).destinationDirectory.set(project.layout.buildDirectory.dir("classes/kotlin/$name/${compilation.name}"))
            }
            if (project.findProperty("kotlin.stdlib.default.dependency")?.toString()?.toBoolean() != false) {
                compilation.defaultSourceSet.dependencies { implementation(kotlin("stdlib")) }
            }
        }
    }
}