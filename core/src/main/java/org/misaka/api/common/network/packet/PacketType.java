package org.misaka.api.common.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.StreamCodec;
import org.misaka.api.common.registries.MisakaNetworkRegistries;

public record PacketType<L extends PacketListener, P extends Packet<L, P>>
        (Class<P> packetClass, StreamCodec<ByteBuf, P> codec) {
    public int getPacketId() {
        return MisakaNetworkRegistries.PACKET_TYPES.getId(this);
    }
}