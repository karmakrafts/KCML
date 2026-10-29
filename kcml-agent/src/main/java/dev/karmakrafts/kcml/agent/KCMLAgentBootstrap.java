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

package dev.karmakrafts.kcml.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;

public final class KCMLAgentBootstrap {
    private KCMLAgentBootstrap() {}

    public static void agentmain(final String options, final Instrumentation instrumentation) throws Throwable {
        final int separator = options == null ? -1 : options.indexOf('\n');
        if (separator <= 0) {
            throw new IllegalArgumentException("KCML agent requires its JAR URI followed by startup options");
        }
        final URL jar = URI.create(options.substring(0, separator)).toURL();
        // The JVM retains Agent-Class in the system loader, even when a snapshot JAR changes.
        // Keep this entrypoint JDK-only and resolve the implementation from the requested JAR.
        final URLClassLoader loader = new URLClassLoader(new URL[]{jar}, ClassLoader.getPlatformClassLoader());
        try {
            loader.loadClass("dev.karmakrafts.kcml.agent.KCMLAgent")
                .getMethod("agentmain", String.class, Instrumentation.class)
                .invoke(null, options.substring(separator + 1), instrumentation);
        } catch (final InvocationTargetException error) {
            loader.close();
            throw error.getCause();
        } catch (final Throwable error) {
            loader.close();
            throw error;
        }
        // The transformer needs this loader for lazy class/resource loading after agentmain returns.
    }
}