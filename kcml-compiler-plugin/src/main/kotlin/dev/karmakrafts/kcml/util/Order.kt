/*
 * Copyright 2025 Karma Krafts & associates
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

package dev.karmakrafts.kcml.util

import io.github.alexandrepiveteau.graphs.Vertex
import io.github.alexandrepiveteau.graphs.edgeTo
import kotlinx.serialization.Serializable

@Serializable
enum class Order(
    @Transient val edgeFunctor: (a: Vertex, b: Vertex) -> Unit
) {
    // @formatter:off
    NONE    ({ _, _ -> }),
    BEFORE  ({ a, b -> a edgeTo b }),
    AFTER   ({ a, b -> b edgeTo a })
    // @formatter:on
}