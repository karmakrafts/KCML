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

import java.lang.AutoCloseable
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.channels.FileLock as NioFileLock

internal class FileLock(path: Path) : AutoCloseable {
    private val channel: FileChannel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    private val lock: NioFileLock = channel.lock()

    override fun close() {
        lock.close()
        channel.close()
    }
}