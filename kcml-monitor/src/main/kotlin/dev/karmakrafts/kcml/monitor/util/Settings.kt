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

package dev.karmakrafts.kcml.monitor.util

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class Settings(
    val version: Int = VERSION,
    val port: Int = 65000,
    @SerialName("look_and_feel") val lookAndFeel: String = "FlatLaf Dark",
    @SerialName("window_x") val windowX: Int = -1,
    @SerialName("window_y") val windowY: Int = -1,
    @SerialName("window_w") val windowWidth: Int = 1400,
    @SerialName("window_h") val windowHeight: Int = 1000,
    @SerialName("v_divider_location") val vDividerLocation: Int = 260,
    @SerialName("nh0_divider_location") val nh0DividerLocation: Int = 600,
    @SerialName("nh1_divider_location") val nh1DividerLocation: Int = 220,
    @SerialName("sh0_divider_location") val sh0DividerLocation: Int = 400,
    @SerialName("mock_agent") val mockAgent: MockAgent = MockAgent()
) {
    companion object {
        const val VERSION: Int = 1
    }

    @Serializable
    data class MockAgent(
        @SerialName("window_x") override val windowX: Int = -1,
        @SerialName("window_y") override val windowY: Int = -1,
        @SerialName("window_w") override val windowWidth: Int = 1400,
        @SerialName("window_h") override val windowHeight: Int = 1000
    ) : PersistentWindowState
}