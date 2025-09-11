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

package dev.karmakrafts.kcml.monitor.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@Sharable
public class MessageEncoder extends MessageToByteEncoder<Packet> {
    @Override
    protected void encode(final ChannelHandlerContext ctx, final Packet msg, final ByteBuf out) {
        if (!out.isWritable()) {
            return;
        }
        final var start = out.writerIndex();
        out.writeLong(0);
        final var payloadStart = out.writerIndex();
        PacketCodecs.serialize(msg, out);
        final var payloadSize = out.writerIndex() - payloadStart;
        out.setLong(start, payloadSize);
    }
}
