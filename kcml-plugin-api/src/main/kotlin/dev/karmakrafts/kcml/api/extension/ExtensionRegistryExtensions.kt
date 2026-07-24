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

/**
 * Finds a registered extension by its reified extension type.
 *
 * @param E extension subtype annotated with [ExtensionId].
 * @return the matching extension cast to [E], or `null` when its identifier is absent or unregistered.
 */
inline fun <reified E : Extension> ExtensionRegistry.find(): E? = find(E::class.getExtensionId() ?: return null) as? E

/**
 * Gets a registered extension by its reified extension type.
 *
 * @param E extension subtype annotated with [ExtensionId].
 * @return the matching registered extension.
 * @throws IllegalStateException if [E] has no [ExtensionId] or no matching extension is registered.
 */
inline fun <reified E : Extension> ExtensionRegistry.get(): E {
    val id =
        E::class.getExtensionId() ?: error("Cannot get extension of type ${E::class} without @ExtensionId annotation")
    return find(id) as? E ?: error("No extension with ID '$id'")
}