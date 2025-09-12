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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

@OptIn(ExperimentalAtomicApi::class)
internal class SettingsHolder( // @formatter:off
    val path: Path,
    settings: Settings = Settings()
) { // @formatter:on
    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val codec: Json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
            prettyPrintIndent = "\t"
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun load(path: Path): SettingsHolder = SettingsHolder(path).apply {
            load(path)
        }
    }

    private val _settings: AtomicReference<Settings> = AtomicReference(settings)
    inline var settings: Settings
        get() = _settings.load()
        private set(value) {
            _settings.store(value)
        }

    inline fun update(block: (Settings) -> Settings) {
        settings = block(settings)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun load(path: Path) {
        if (!path.exists()) return
        settings = Files.newInputStream(path, StandardOpenOption.READ).use { stream ->
            codec.decodeFromStream<Settings>(stream)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun save(path: Path = this.path) {
        path.deleteIfExists()
        Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { stream ->
            codec.encodeToStream(settings, stream)
        }
    }
}