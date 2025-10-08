package org.misaka.api.common.network.packet;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import org.misaka.MisakaNetwork;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.event.C2SPacketEvent;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

public final class C2SPacket implements net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final PacketType<C2SPacket> TYPE = new PacketType<>(PacketFlow.SERVERBOUND, MisakaNetwork.location("c2s_packet"));
    public static final StreamCodec<FriendlyByteBuf, C2SPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            C2SPacket::write, C2SPacket::new
    );

    private final int id;
    private final FriendlyByteBuf friendlyByteBuf;

    public <L extends ServerGamePacketListenerImpl, T extends Packet<L, T>> C2SPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        this.friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());

        var startTime = NetworkSystem.isDebugInfo() ? System.nanoTime() : 0;
        packet.getPacketType().codec().encode(friendlyByteBuf, packet);

        if (NetworkSystem.isDebugInfo()) {
            var endTime = System.nanoTime();
            var packetSize = friendlyByteBuf.readableBytes();
            LOGGER.debug(
                    "[SEND][C2S] Packet: {}(ID: {}), Size: {} bytes, Encode Time: {} ns",
                    packet.getClass().getSimpleName(),
                    id,
                    packetSize,
                    endTime - startTime
            );
        }
    }

    @ApiStatus.Internal
    private C2SPacket(FriendlyByteBuf newFriendlyByteBuf) {
        id = newFriendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(newFriendlyByteBuf.readBytes(newFriendlyByteBuf.readableBytes()));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public PacketType<? extends net.minecraft.network.protocol.Packet<ServerGamePacketListenerImpl>> type() {
        return TYPE;
    }

    @Override
    public void handle(ServerGamePacketListenerImpl handler) {
        handler.server.execute(() -> {
            var event = new C2SPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);

            var packetType = NetworkSystem.<org.misaka.api.common.network.packet.PacketType
                    <ServerGamePacketListenerImpl, ?>>getPacketTypeById(id);
            var packetClass = packetType.packetClass();

            if (NetworkSystem.isDebugInfo()) {
                var packetSize = friendlyByteBuf.readableBytes();
                LOGGER.debug(
                        "[RECEIVE][C2S] Packet: {}(ID: {}), Size: {} bytes",
                        packetClass.getSimpleName(),
                        id,
                        packetSize
                );
            }

            if (!NetworkSystem.shouldReceive(packetClass, ThreadType.SERVER)) return;

            try {
                var instance = packetType.codec().decode(friendlyByteBuf);
                instance.setPacketListener(handler);
                MisakaNetworkServer.NETWORK_MANAGER.dispatchPacket(instance);
            } catch (Throwable e) {
                LOGGER.error(
                        "Exception processing C2S packet. Class: {}, ID: {}. Player: {}. Error: {}",
                        packetClass.getSimpleName(),
                        id,
                        handler.player.getGameProfile().name(),
                        e.getMessage(),
                        e
                );
            }
        });
    }
}