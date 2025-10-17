package org.misaka.api.common.network.asm;

import net.minecraft.network.PacketListener;
import org.misaka.api.common.network.packet.Packet;

public interface IPacketListener {
    void handlePacket(Packet<?, ?> packet);

    <L extends PacketListener, P extends Packet<L, P>> Class<P> getPacketClass();
}