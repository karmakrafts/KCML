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

/**
 * Declares a dependency for a KCML extension class.
 *
 * [AbstractExtension] converts each runtime instance of this repeatable annotation into an
 * [ExtensionDependency] used by KCML to resolve extension availability and dispatch order.
 *
 * @param id identifier of the extension on which the annotated extension depends.
 * @param isRequired whether the annotated extension is invalid when the dependency is unavailable.
 * @param order relative order in which the dependency runs with respect to the annotated extension.
 */
@Repeatable
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ExtensionDependsOn( // @formatter:off
    val id: String,
    val isRequired: Boolean = true,
    val order: Order = Order.AFTER
) // @formatter:on
