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

package dev.karmakrafts.kcml.extension

import dev.karmakrafts.kcml.api.extension.AbstractExtension
import dev.karmakrafts.kcml.api.extension.Extension
import dev.karmakrafts.kcml.api.extension.ExtensionDependsOn
import dev.karmakrafts.kcml.api.extension.ExtensionId
import dev.karmakrafts.kcml.api.util.Order
import dev.karmakrafts.kcml.log.MessageCollectorLoggerFactory
import dev.karmakrafts.kcml.plugin.PluginLoaderImpl
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultExtensionRegistryTest {
    @ExtensionId("foundation")
    private class FoundationExtension : AbstractExtension()

    @ExtensionId("dependent")
    @ExtensionDependsOn("foundation", order = Order.AFTER)
    private class AfterExtension : AbstractExtension()

    @ExtensionId("dependent")
    @ExtensionDependsOn("foundation", order = Order.BEFORE)
    private class BeforeExtension : AbstractExtension()

    private fun sorted(dependent: Extension): List<String> {
        val loader = PluginLoaderImpl
        val logger = MessageCollectorLoggerFactory(loader, MessageCollector.NONE)("registry-test")
        val registry = DefaultExtensionRegistry(loader, "registry-test", logger)
        registry.register(dependent)
        registry.register(FoundationExtension())
        registry.freeze()
        return registry.allSorted().map(Extension::id)
    }

    @Test
    fun `different extension implementations can depend on running after one another`() {
        assertEquals(listOf("foundation", "dependent"), sorted(AfterExtension()))
    }

    @Test
    fun `different extension implementations can depend on running before one another`() {
        assertEquals(listOf("dependent", "foundation"), sorted(BeforeExtension()))
    }
}