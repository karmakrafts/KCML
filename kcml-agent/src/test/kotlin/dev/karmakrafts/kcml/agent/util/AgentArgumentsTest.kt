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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentArgumentsTest {
    @Test
    fun `parses supported argument types`() {
        val byte = AgentArguments.Argument.create<Byte>("byte")
        val short = AgentArguments.Argument.create<Short>("short")
        val integer = AgentArguments.Argument.create<Int>("integer")
        val long = AgentArguments.Argument.create<Long>("long")
        val float = AgentArguments.Argument.create<Float>("float")
        val double = AgentArguments.Argument.create<Double>("double")
        val boolean = AgentArguments.Argument.create<Boolean>("boolean")
        val string = AgentArguments.Argument.create<String>("string")

        val arguments = AgentArguments.parse(
            "byte=1:short=2:integer=3:long=4:float=5.5:double=6.5:boolean=TrUe:string=last",
            byte,
            short,
            integer,
            long,
            float,
            double,
            boolean,
            string
        )

        assertEquals(1, arguments[byte])
        assertEquals(2, arguments[short])
        assertEquals(3, arguments[integer])
        assertEquals(4, arguments[long])
        assertEquals(5.5f, arguments[float])
        assertEquals(6.5, arguments[double])
        assertEquals(true, arguments[boolean])
        assertEquals("last", arguments[string])
    }

    @Test
    fun `parses quoted and escaped string values`() {
        val path = AgentArguments.Argument.create<String>("path")
        val message = AgentArguments.Argument.create<String>("message")

        val arguments = AgentArguments.parse(
            "path=\"before:after\":message=\"say \\\"hello\\\"\"", path, message
        )

        assertEquals("before:after", arguments[path])
        assertEquals("say \"hello\"", arguments[message])
    }

    @Test
    fun `ignores unknown arguments and returns null for invalid values`() {
        val enabled = AgentArguments.Argument.create<Boolean>("enabled")
        val message = AgentArguments.Argument.create<String>("message")

        val arguments = AgentArguments.parse(
            "unknown=value:enabled=not-a-boolean:message=\"accepted\"", enabled, message
        )

        assertNull(arguments.getOrNull(enabled))
        assertEquals("accepted", arguments[message])
    }

    @Test
    fun `parses KCML agent bridge path`() {
        val arguments = KCMLAgentArguments.parse(
            "comm_port=1:logging=false:loader_path=loader.jar:bridge_path=bridge.jar"
        )

        assertEquals("bridge.jar", arguments.bridgePath)
    }
}