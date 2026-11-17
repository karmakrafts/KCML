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

import org.gradle.api.Describable
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.api.specs.AndSpec
import org.gradle.api.specs.CompositeSpec
import org.jetbrains.kotlin.commonizer.CommonizerTarget
import org.jetbrains.kotlin.commonizer.LeafCommonizerTarget
import org.jetbrains.kotlin.commonizer.allLeaves
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.native.internal.commonizerTarget
import org.jetbrains.kotlin.gradle.targets.native.toolchain.chooseKotlinNativeProvider
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinNativeCompile
import org.jetbrains.kotlin.gradle.utils.Future
import org.jetbrains.kotlin.gradle.utils.exclude
import org.jetbrains.kotlin.gradle.utils.map
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.tooling.core.HasMutableExtras
import java.util.*

/** Preserves explicit identities where KGP's commonizer model otherwise only remembers a built-in name. */
internal class CustomNativeTargetAdapter(project: Project) {
    private val targets = HashMap<String, KonanTarget>()
    private val configured = Collections.newSetFromMap(IdentityHashMap<HasMutableExtras, Boolean>())
    private val konanTargetField = LeafCommonizerTarget::class.java.getDeclaredField("konanTargetOrNull").apply {
        isAccessible = true
    }
    private val librariesDelegate =
        AbstractKotlinNativeCompile::class.java.getDeclaredField($$"libraries$delegate").apply {
            isAccessible = true
        }

    init {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.sourceSets.all { sourceSet -> configure(sourceSet, sourceSet.commonizerTarget) }
        kotlin.targets.all { target ->
            target.compilations.all { compilation ->
                configure(compilation, compilation.commonizerTarget)
            }
        }
    }

    fun register(target: KonanTarget) {
        require(targets[target.name]?.let { it === target } != false) {
            "Different explicit Konan targets cannot share the name '${target.name}'"
        }
        targets[target.name] = target
    }

    fun configure(target: KotlinNativeTarget) {
        if (KonanTarget.predefinedTargets[target.konanTarget.name] === target.konanTarget) return
        target.compilations.all { compilation ->
            compilation.compileTaskProvider.configure { task ->
                val hostGuard = "Compilation of target '${target.disambiguationClassifier}' is supported on this host"

                @Suppress("UNCHECKED_CAST") //
                val predicates = (task as TaskInternal).onlyIf as CompositeSpec<Task>

                task.setOnlyIf(AndSpec(predicates.specs.filterNot { (it as? Describable)?.displayName == hostGuard }))
                task.enabled = true
                task.kotlinNativeProvider.set(task.chooseKotlinNativeProvider(true, target.konanTarget, target.project))
                // KGP's library delegate creates an empty collection on unsupported hosts on every read.
                val libraries = target.project.files(target.project.provider { compilation.compileDependencyFiles })
                librariesDelegate.set(task, target.project.provider { libraries })
            }
        }
        target.binaries.all { binary ->
            binary.linkTaskProvider.configure { task ->
                // Custom toolchains are not in HostManager's built-in support matrix.
                task.enabled = true
                task.kotlinNativeProvider.set(task.chooseKotlinNativeProvider(true, target.konanTarget, target.project))
                task.libraries.setFrom(target.project.provider {
                    binary.compilation.compileDependencyFiles.exclude(task.excludeDependencies)
                })
            }
        }
    }

    private fun configure(owner: HasMutableExtras, original: Future<CommonizerTarget?>) {
        if (!configured.add(owner)) return
        val adapted = original.map { target ->
            target?.allLeaves()?.forEach { leaf ->
                targets[leaf.name]?.let { konanTargetField.set(leaf, it) }
            }
            target
        }
        // KGP 2.5 stores these futures by delegate identity, not an addressable extras key.
        val storage = Class.forName("org.jetbrains.kotlin.gradle.utils.StoredPropertyKt")
            .getDeclaredMethod("getStoredPropertyStorage", HasMutableExtras::class.java)
            .apply { isAccessible = true }
            .invoke(null, owner)

        @Suppress("UNCHECKED_CAST") val values = storage.javaClass.getDeclaredField("values")
            .apply { isAccessible = true }
            .get(storage) as MutableMap<Any, Any?>
        values.entries.single { it.value === original }.setValue(adapted)
    }
}