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

package dev.karmakrafts.kcml.api.mixin

import kotlin.reflect.KClass

/**
 * Declares a mixin whose target class is accessible as a concrete type at compile time.
 *
 * Use [IndirectMixin] when the target cannot be referenced directly.
 *
 * @property target class to which the mixin is applied.
 * @property priority application priority; mixins with lower values are applied first.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class DirectMixin( // @formatter:off
    public val target: KClass<*>,
    public val priority: Int = 0
) // @formatter:on