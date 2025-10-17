package org.misaka.api.common.network.listener;

import org.misaka.api.common.network.packet.Packet;

public interface IPacketListener {
    @SuppressWarnings("NullableProblems")
    void handlePacket(Packet<?, ?> packet);

    <P extends Packet<?, ?>> Class<P> getPacketClass();
}