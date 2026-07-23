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

package dev.karmakrafts.kcml.api.extension

import dev.karmakrafts.kcml.api.util.Order

abstract class AbstractExtension : Extension {
    override val id: String by lazy {
        this@AbstractExtension::class.getExtensionId()
            ?: error("Extension without @ExtensionId annotation requires id to be specified explicitly")
    }

    override val dependencies: List<ExtensionDependency> by lazy {
        val type = this@AbstractExtension::class.java
        type.getAnnotationsByType(ExtensionDependsOn::class.java).map { annotation ->
            object : ExtensionDependency {
                override val id: String = annotation.id
                override val required: Boolean = annotation.isRequired
                override val order: Order = annotation.order
            }
        }
    }
}