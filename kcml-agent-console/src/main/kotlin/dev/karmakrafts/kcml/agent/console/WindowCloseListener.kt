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

import java.awt.event.WindowEvent
import java.awt.event.WindowListener

internal class WindowCloseListener(
    private val callback: () -> Unit
) : WindowListener {
    override fun windowOpened(e: WindowEvent?) = Unit
    override fun windowClosing(e: WindowEvent?) = callback()
    override fun windowClosed(e: WindowEvent?) = Unit
    override fun windowIconified(e: WindowEvent?) = Unit
    override fun windowDeiconified(e: WindowEvent?) = Unit
    override fun windowActivated(e: WindowEvent?) = Unit
    override fun windowDeactivated(e: WindowEvent?) = Unit
}