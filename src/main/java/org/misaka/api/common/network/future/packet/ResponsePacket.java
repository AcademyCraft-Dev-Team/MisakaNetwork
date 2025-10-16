package org.misaka.api.common.network.future.packet;

import net.minecraft.network.PacketListener;
import org.misaka.api.common.network.packet.Packet;

/**
 * 可以被 SubscribePacket 喵, 虽然何意味喵
 * <br>
 * RequestPacket 和 ResponsePacket 是不会通过 C2SPacket 或 S2CPacket 直接发送的喵,
 */
public abstract class ResponsePacket<
        T extends PacketListener,
        P extends Packet<T, P>
        > extends Packet<T, P> {
}