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

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

internal class FileChecksum(val bytes: ByteArray) {
    companion object {
        private val digest: ThreadLocal<MessageDigest> = ThreadLocal.withInitial {
            MessageDigest.getInstance("SHA-256")
        }

        fun fromBytes(bytes: ByteArray): FileChecksum {
            val digest = this.digest.get()
            digest.reset()
            digest.update(bytes)
            return FileChecksum(digest.digest())
        }

        fun forFile(path: Path): FileChecksum = fromBytes(path.readBytes())

        fun forResource(path: String): FileChecksum {
            return requireNotNull(this::class.java.getResourceAsStream(path)) {
                "Could not create FileChecksum for resource $path"
            }.use { stream ->
                fromBytes(stream.readAllBytes())
            }
        }
    }

    override fun equals(other: Any?): Boolean = when (other) {
        is FileChecksum -> bytes.contentEquals(other.bytes)
        else -> false
    }

    override fun hashCode(): Int = bytes.contentHashCode()

    override fun toString(): String = bytes.joinToString("") { it.toHexString().uppercase() }
}