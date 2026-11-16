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

package dev.karmakrafts.kcml.api.plugin

import dev.karmakrafts.kcml.api.ipm.IPM
import dev.karmakrafts.kcml.api.target.CompileTargetRegistry

/**
 * Entry point for a KCML compiler plugin discovered by scanning plugin classpaths for [Plugin].
 * Implementations must have an accessible no-argument constructor.
 *
 * KCML orders discovered plugins by their declared dependencies, calls [registerTargets] for all plugins,
 * calls [preInit] to register their CLI options, and processes option values before calling [init].
 * Initialization completes before KCML installs the Kotlin FIR and IR extension adapters. Implementations
 * register the extensions that should participate in this compilation through the supplied initialization context.
 */
public interface CompilerPlugin {
    /**
     * Registers compilation target factories before any plugin's [preInit] context is created.
     *
     * Called once per compilation in dependency order. The registry is scoped to the current
     * compilation and already contains the built-in target factories. The default implementation
     * registers no targets.
     *
     * The selected factory is invoked once, and its target is shared by the initialization contexts
     * and backend adapters. Custom targets must implement the interface for the active Kotlin backend.
     *
     * @param registry registry used to realize targets for [preInit] and [init].
     */
    public fun registerTargets(registry: CompileTargetRegistry): Unit = Unit

    /**
     * Registers compiler CLI options before [init] is called.
     *
     * Options are prefixed with this plugin's [Plugin.id] and an uppercased first character,
     * matching the KCML Gradle support plugin: `-P plugin:kcml:<pluginId>OptionName=<value>`.
     * Parsers store their results in the compiler configuration supplied to [init].
     * Values placed in [PreInitializationContext.storage] remain available through
     * [InitializationContext.storage] during [init] for this invocation.
     * This method is called once per compilation,
     * in dependency order. The default implementation registers no options.
     *
     * @param context registry for this plugin's option definitions and parsers, with invocation-scoped storage.
     */
    public fun preInit(context: PreInitializationContext): Unit = Unit

    /**
     * Configures this plugin for the current Kotlin compiler invocation.
     *
     * @param context services scoped to the active plugin load, including the registry to which
     *   this plugin contributes extensions.
     */
    public fun init(context: InitializationContext)

    /**
     * Processes inter-plugin messages after all plugins have been initialized, in dependency order.
     *
     * The default implementation does nothing. Implementations can register receivers or send
     * messages through the supplied service.
     *
     * @param ipm this plugin's message service.
     */
    public fun processIpms(ipm: IPM): Unit = Unit
}