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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * A Gradle plugin that contributes compiler options for KCML compilations.
 *
 * Implementations can be applied alongside the KCML Gradle plugin to customize
 * the compiler options supplied to individual Kotlin compilations.
 */
interface KCMLGradleSupportPlugin : Plugin<Project> {
    /**
     * Applies this support plugin to [target].
     *
     * The default implementation performs no project-level configuration.
     *
     * @param target the project to which this plugin is applied.
     */
    override fun apply(target: Project) = Unit

    /**
     * Produces the additional KCML compiler options for [kotlinCompilation].
     *
     * @param kotlinCompilation the Kotlin compilation being configured.
     * @return a provider of compiler options to pass to the KCML compiler plugin.
     */
    fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>>
}