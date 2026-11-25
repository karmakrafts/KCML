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

package dev.karmakrafts.kcml.api.plugin

/**
 * An object representing an icon for a given plugin.
 * The object contains both, a light and dark variant, which are used
 * based on the theme of the application.
 * If the same icon is supposed to be used for both theme modes,
 * only the light variant may be specified, which defaults to both variants.
 */
public interface PluginIcon {
    /** The resource path to the light variant of the icon. */
    public val light: String?

    /** The resource path to the dark variant of the icon. */
    public val dark: String?
}