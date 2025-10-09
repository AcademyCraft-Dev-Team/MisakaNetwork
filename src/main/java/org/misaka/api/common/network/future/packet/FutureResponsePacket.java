package org.misaka.api.common.network.future.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.misaka.internal.common.network.PacketTypes;
import org.misaka.api.common.network.packet.PacketType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class FutureResponsePacket<T extends PacketListener> extends FuturePacket<T, FutureResponsePacket<T>> {
    public static final StreamCodec<ByteBuf, FutureResponsePacket<?>> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            FuturePacket::getFutureId,
            ByteBufCodecs.INT,
            FuturePacket::getTargetPacketTypeId,
            ByteBufCodecs.BYTE_ARRAY,
            FuturePacket::getBytes,
            FutureResponsePacket::new
    );

    public FutureResponsePacket(int futureId, int responsePacketTypeId, byte[] bytes) {
        super(futureId, responsePacketTypeId, bytes);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketType<T, FutureResponsePacket<T>> getPacketType() {
        return (PacketType<T, FutureResponsePacket<T>>) PacketTypes.FUTURE_RESPONSE.get();
    }
}