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

package dev.karmakrafts.kcml.iridium

import dev.karmakrafts.kcml.api.log.Logger

internal class TestLogger( // @formatter:off
    override val name: String
) : Logger { // @formatter:on
    override fun debug(message: String) {
        println("[$name][DEBUG] $message")
    }

    override fun info(message: String) {
        println("[$name][INFO-] $message")
    }

    override fun warn(message: String) {
        println("[$name][WARN-] $message")
    }

    override fun error(message: String) {
        System.err.println("[$name][ERROR] $message")
    }
}