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

package dev.karmakrafts.kcml.agent.mixin

import org.objectweb.asm.tree.AnnotationNode
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptureTest {
    @Test
    fun `reads annotation values and supplies default index`() {
        val defaultCapture = Capture.fromAnnotation(AnnotationNode("Lexample/Capture;").apply {
            values = mutableListOf()
        })
        val configuredCapture = Capture.fromAnnotation(AnnotationNode("Lexample/Capture;").apply {
            values = mutableListOf("name", "value", "index", 3)
        })

        assertEquals(Capture(name = null, index = Capture.ANY_INDEX), defaultCapture)
        assertEquals(Capture(name = "value", index = 3), configuredCapture)
    }
}