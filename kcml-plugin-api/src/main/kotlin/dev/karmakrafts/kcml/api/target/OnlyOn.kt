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

package dev.karmakrafts.kcml.api.target

import kotlin.reflect.KClass

/**
 * Marker annotation to indicate in which platform target context the annotated element may be used.
 * This is to present target-specific code from being invoked on the wrong platform.
 *
 * The platform target compatibility is modeled using a lexicographical scope, where the root
 * (or top-level) scope supports all targets.
 * However, referencing code annotated with this annotation outside a designated [whenCompilingFor] scope
 * is discouraged and will cause an inspection warning to appear.
 *
 * Each allowed target includes the target itself and all its subtypes. This applies to every supported
 * annotation location, and target constraints also apply to nested declarations and expressions.
 *
 * This warning may be suppressed by using the `KCML_TARGET_SPECIFIC_CODE` inspection identifier.
 *
 * Referencing code allowing the [JvmCompileTarget] while inside a [JsCompileTarget] for example,
 * would be treated like a hard error instead of a warning. However, as a means of an escape hatch,
 * this error can also be suppressed using the `KCML_MISMATCHED_TARGET` inspection identifier.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.TYPE // For closures only, other type usages are prohibited
)
public annotation class OnlyOn(
    public vararg val targets: KClass<out CompileTarget>
)
