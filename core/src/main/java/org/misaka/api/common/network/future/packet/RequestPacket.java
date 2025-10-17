package org.misaka.api.common.network.future.packet;

import net.minecraft.network.PacketListener;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;

/**
 * 可以被 SubscribePacket 喵, 虽然何意味喵
 * <br>
 * RequestPacket 和 ResponsePacket 是不会通过 C2SPacket 或 S2CPacket 直接序列化发送的喵, 是通过 FutureRequestPacket 或 FutureResponsePacket 间接序列化的喵
 */
public abstract class RequestPacket<
        REQ_L extends PacketListener,
        REQ_P extends Packet<REQ_L, REQ_P>,
        RES_L extends PacketListener,
        RES_P extends ResponsePacket<RES_L, RES_P>
        > extends Packet<REQ_L, REQ_P> {
    public abstract PacketType<RES_L, RES_P> getResponsePacketType();
}