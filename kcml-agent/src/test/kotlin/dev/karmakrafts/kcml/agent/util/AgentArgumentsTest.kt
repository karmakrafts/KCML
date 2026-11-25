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
import kotlin.test.assertFailsWith
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

        assertEquals(1.toByte(), arguments[byte])
        assertEquals(2.toShort(), arguments[short])
        assertEquals(3, arguments[integer])
        assertEquals(4L, arguments[long])
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
    fun `ignores unknown arguments and returns null for invalid booleans`() {
        val enabled = AgentArguments.Argument.create<Boolean>("enabled")
        val message = AgentArguments.Argument.create<String>("message")

        val arguments = AgentArguments.parse(
            "unknown=value:enabled=not-a-boolean:message=\"accepted\"", enabled, message
        )

        assertNull(arguments.getOrNull(enabled))
        assertEquals("accepted", arguments[message])
    }

    @Test
    fun `ignores unknown quoted arguments without losing subsequent arguments`() {
        val port = AgentArguments.Argument.create<Int>("port")
        val message = AgentArguments.Argument.create<String>("message")

        val arguments = AgentArguments.parse(
            "unknown=\"before:after\":port=11000:message=\"accepted\"", port, message
        )

        assertEquals(11000, arguments[port])
        assertEquals("accepted", arguments[message])
    }

    @Test
    fun `returns null for absent arguments and rejects required lookup`() {
        val port = AgentArguments.Argument.create<Int>("port")
        val arguments = AgentArguments.parse("", port)

        assertNull(arguments.getOrNull(port))
        assertFailsWith<IllegalArgumentException> { arguments[port] }
    }

    @Test
    fun `preserves empty string values`() {
        val quoted = AgentArguments.Argument.create<String>("quoted")
        val unquoted = AgentArguments.Argument.create<String>("unquoted")
        val trailing = AgentArguments.Argument.create<String>("trailing")

        val arguments = AgentArguments.parse("quoted=\"\":unquoted=:trailing=", quoted, unquoted, trailing)

        assertEquals("", arguments[quoted])
        assertEquals("", arguments[unquoted])
        assertEquals("", arguments[trailing])
    }

    @Test
    fun `preserves escaped backslashes and equals signs in quoted strings`() {
        val value = AgentArguments.Argument.create<String>("value")

        val arguments = AgentArguments.parse("value=\"before\\\\after=last\"", value)

        assertEquals("before\\after=last", arguments[value])
    }

    @Test
    fun `last occurrence of an argument wins`() {
        val port = AgentArguments.Argument.create<Int>("port")

        val arguments = AgentArguments.parse("port=1:port=11000", port)

        assertEquals(11000, arguments[port])
    }

    @Test
    fun `rejects unsupported argument types`() {
        assertFailsWith<IllegalArgumentException> { AgentArguments.Argument.create<Char>("character") }
    }

    @Test
    fun `ignores legacy KCML options before communication options`() {
        val arguments = KCMLAgentArguments.parse(
            "loader_path=\"loader.jar\":bridge_path=\"bridge.jar\":dump_classes=true:" + "dump_path=\"build/kcml:dump\":comm_port=11000:logging=true:module_name=example"
        )

        assertEquals(11000, arguments.commPort)
        assertEquals(true, arguments.logging)
        assertNull(arguments.getOrNull(AgentArguments.Argument.create<String>("loader_path")))
        assertNull(arguments.getOrNull(AgentArguments.Argument.create<String>("bridge_path")))
        assertNull(arguments.getOrNull(AgentArguments.Argument.create<String>("dump_path")))
    }

    @Test
    fun `parses minimal KCML agent startup options`() {
        val arguments = KCMLAgentArguments.parse(
            "comm_port=1:logging=false"
        )

        assertEquals(1, arguments.commPort)
        assertEquals(false, arguments.logging)
    }
}