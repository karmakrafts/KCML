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

package dev.karmakrafts.kcml.agent.mixin.component

import kotlin.reflect.KClass

internal abstract class AbstractMixinComponent : MixinComponent {
    override val dependencies: List<KClass<out MixinComponent>> by lazy {
        val clazz = this::class.java
        if (!clazz.isAnnotationPresent(DependsOn::class.java)) return@lazy emptyList()
        clazz.getAnnotation(DependsOn::class.java).components.toList()
    }
}