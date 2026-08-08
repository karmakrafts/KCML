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

package dev.karmakrafts.kcml.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

internal data class EmbeddedJar( // @formatter:off
    val path: String,
    val logger: (String) -> Unit = {}
) { // @formatter:on
    private fun unpack(target: Path) {
        logger("Unpacking JAR $path to $target")
        this::class.java.getResourceAsStream(path)?.use { stream ->
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
        } ?: error("Could not unpack $path")
        logger("Unpacked ${Files.size(target)} bytes to $target")
    }

    fun unpackIfNeeded(target: Path) {
        if (target.exists()) {
            logger("File $target already exists, validating integrity")
            val expectedChecksum = FileChecksum.forResource(path)
            logger("Expected checksum is $expectedChecksum")
            val existingChecksum = FileChecksum.forFile(target)
            logger("Existing checksum is $existingChecksum")
            if (existingChecksum == expectedChecksum) return
            logger("Mismatched checksum detected, overwriting old file")
        }
        unpack(target)
    }
}