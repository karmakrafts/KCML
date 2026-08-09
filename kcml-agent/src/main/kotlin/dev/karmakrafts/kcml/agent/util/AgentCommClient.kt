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

package dev.karmakrafts.kcml.agent.util

import java.io.IOException
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetAddress
import java.net.Socket

internal class AgentCommClient(port: Int) {
    private val socket: Socket = Socket(InetAddress.getLoopbackAddress(), port)
    private val writer: PrintWriter = PrintWriter(socket.getOutputStream(), true)
    private val writerLock: Any = Any()

    private val thread: Thread = Thread {
        try {
            socket.getInputStream().transferTo(OutputStream.nullOutputStream())
        } catch (_: IOException) {
            // The server closed the connection
        }
        finally {
            synchronized(writerLock) {
                writer.close()
            }
            runCatching(socket::close)
        }
    }

    init {
        thread.start()
    }

    fun log(message: String?) {
        synchronized(writerLock) {
            writer.println(message)
        }
    }
}