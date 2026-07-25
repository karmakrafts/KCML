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

import sun.misc.Unsafe
import java.awt.Toolkit
import javax.swing.SwingUtilities

object Main {
    private const val APP_NAME: String = "KCML Agent Console"

    private fun parseArgs(args: Array<String>): Map<String, String> {
        val options = HashMap<String, String>()
        for (arg in args) {
            if (!arg.startsWith("--") || '=' !in arg) continue // We only accept args in --key=value form
            val splitArg = arg.drop(2).split("=")
            if (splitArg.size != 2) continue // We only accept args in --key=value form
            options[splitArg[0]] = splitArg[1]
        }
        return options
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val options = parseArgs(args)
        updateAppClassName()
        val window = AgentConsole()
        // TODO: make configurable in GUI
        val server = ConsoleServer( // @formatter:off
            host = options["host"] ?: "127.0.0.1",
            port = options["port"]?.toIntOrNull() ?: 9876,
            callback = window::log
        ) // @formatter:on
        window.addWindowListener(WindowCloseListener {
            server.close()
        })
        SwingUtilities.invokeLater {
            window.isVisible = true
        }
    }

    @Suppress("DEPRECATION")
    private fun updateAppClassName() {
        try {
            val toolkit = Toolkit.getDefaultToolkit()
            if (toolkit::class.java.simpleName != "XToolkit") return
            val classNameField = toolkit.javaClass.getDeclaredField("awtAppClassName")
            try {
                classNameField.isAccessible = true
                classNameField.set(null, APP_NAME)
            } catch (_: Throwable) {
                val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
                theUnsafe.isAccessible = true
                val unsafe = theUnsafe.get(null) as? Unsafe ?: return
                val fieldBase = unsafe.staticFieldBase(classNameField)
                val fieldOffset = unsafe.staticFieldOffset(classNameField)
                unsafe.putObjectVolatile(fieldBase, fieldOffset, APP_NAME)
            }
        } catch (error: Throwable) {
            // Swallow silently
        }
    }
}