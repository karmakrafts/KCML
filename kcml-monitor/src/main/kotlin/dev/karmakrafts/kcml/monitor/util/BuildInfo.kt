/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.monitor.util

import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@ConsistentCopyVisibility
data class BuildInfo private constructor( // @formatter:off
    val version: String = "0.0.0"
) { // @formatter:off
    companion object {
        private val codec: Json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            prettyPrintIndent = "\t"
        }

        val current: BuildInfo by lazy {
            try {
                this::class.java.getResourceAsStream("/build_info.json")!!.use(codec::decodeFromStream)
            } catch (_: Throwable) {
                BuildInfo()
            }
        }

        val licenses: Libs by lazy {
            try {
                this::class.java.getResourceAsStream("/licenses.json")!!.bufferedReader().use { reader ->
                    Libs.Builder().withJson(reader.readText()).build()
                }
            } catch(_: Throwable) {
                Libs(emptyList<Library>().toImmutableList(), emptySet<License>().toImmutableSet())
            }
        }
    }
}