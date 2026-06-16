package org.misaka.api.common.network.listener;

import org.misaka.api.common.network.packet.Packet;

@FunctionalInterface
public interface PacketHandler {
    void handlePacket(Packet<?, ?> packet);
}