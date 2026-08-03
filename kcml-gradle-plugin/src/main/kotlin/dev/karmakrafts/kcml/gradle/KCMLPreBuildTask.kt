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

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.TaskAction

// A little hack for Kotlin/Native to keep the KCMLBuildService alive until after linking
internal abstract class KCMLPreBuildTask : DefaultTask() {
    @get:ServiceReference
    internal abstract val buildService: Property<KCMLBuildService>

    @TaskAction
    fun performAction() {
        buildService.get() // Simply get the reference to keep service alive
    }
}