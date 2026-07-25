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

package dev.karmakrafts.kcml.agent.console

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.atomics.AtomicBoolean

internal class ConsoleServer( // @formatter:off
    port: Int,
    private val callback: (String) -> Unit
) : AutoCloseable { // @formatter:on
    private val socket: ServerSocket = ServerSocket(port, 10, InetAddress.getLoopbackAddress())
    private val coroutineScope: CoroutineScope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("KCML Agent Console Server"))
    private val isRunning: AtomicBoolean = AtomicBoolean(true)

    init {
        println("Starting console server at localhost:$port")
        startNextConnectionJob() // Start initial connection job
    }

    private fun startNextConnectionJob() {
        val job = coroutineScope.launch {
            try {
                while (isRunning.load()) { // While we are running, try to accept a connection
                    socket.accept().use { clientSocket ->
                        println("Accepted connection")
                        startNextConnectionJob() // When this task accepts a new connection, spin up a new one
                        clientSocket.getInputStream().bufferedReader().use { reader ->
                            while (isRunning.load()) { // While we are running, try to pipe content into the console
                                val line = reader.readLine()
                                while (line == null) yield()
                                callback(line)
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                cancel("Connection was terminated", error)
            }
        }
        job.invokeOnCompletion {
            println("Connection job completed")
        }
        println("Started new connection job")
    }

    override fun close() {
        println("Shutting down console server")
        isRunning.store(false) // Let all idle connection tasks know we are done
        socket.close()
        runBlocking {
            coroutineScope.coroutineContext.job.cancelAndJoin()
        }
    }
}