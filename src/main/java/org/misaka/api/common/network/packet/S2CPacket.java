package org.misaka.api.common.network.packet;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.neoforged.neoforge.common.NeoForge;
import org.misaka.MisakaNetwork;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.event.S2CPacketEvent;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

public final class S2CPacket implements net.minecraft.network.protocol.Packet<ClientGamePacketListener> {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final PacketType<S2CPacket> TYPE = new PacketType<>(PacketFlow.CLIENTBOUND, MisakaNetwork.location("s2c_packet"));
    public static final StreamCodec<FriendlyByteBuf, S2CPacket> STREAM_CODEC = net.minecraft.network.protocol.Packet.codec(
            S2CPacket::write, S2CPacket::new
    );

    private final int id;
    private final FriendlyByteBuf friendlyByteBuf;

    public <T extends Packet<ClientGamePacketListener, T>> S2CPacket(T packet) {
        id = packet.getPacketType().getPacketId();
        friendlyByteBuf = new FriendlyByteBuf(Unpooled.buffer());

        var startTime = NetworkSystem.isDebugInfo() ? System.nanoTime() : 0;
        packet.getPacketType().codec().encode(friendlyByteBuf, packet);

        if (NetworkSystem.isDebugInfo()) {
            var endTime = System.nanoTime();
            var packetSize = friendlyByteBuf.readableBytes();
            LOGGER.debug(
                    "[SEND][S2C] Packet: {}(ID: {}), Size: {} bytes, Encode Time: {} ns",
                    packet.getClass().getSimpleName(),
                    id,
                    packetSize,
                    endTime - startTime
            );
        }
    }

    @ApiStatus.Internal
    public S2CPacket(FriendlyByteBuf friendlyByteBuf) {
        id = friendlyByteBuf.readVarInt();
        this.friendlyByteBuf = new FriendlyByteBuf(friendlyByteBuf.readBytes(friendlyByteBuf.readableBytes()));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(id);
        buffer.writeBytes(friendlyByteBuf.copy());
    }

    @Override
    public PacketType<? extends net.minecraft.network.protocol.Packet<ClientGamePacketListener>> type() {
        return TYPE;
    }

    @Override
    public void handle(ClientGamePacketListener handler) {
        Minecraft.getInstance().execute(() -> {
            var event = new S2CPacketEvent(this);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return;

            var packetType = NetworkSystem.<org.misaka.api.common.network.packet.PacketType
                    <ClientGamePacketListener, ?>>getPacketTypeById(id);
            var packetClass = packetType.packetClass();

            if (NetworkSystem.isDebugInfo()) {
                var packetSize = friendlyByteBuf.readableBytes();
                LOGGER.info(
                        "[RECEIVE][S2C] Packet: {}(ID: {}), Size: {} bytes",
                        packetClass.getSimpleName(),
                        id,
                        packetSize
                );
            }

            if (!NetworkSystem.shouldReceive(packetClass, ThreadType.CLIENT)) return;

            try {
                var codec = packetType.codec();
                var instance = codec.decode(friendlyByteBuf);
                instance.setPacketListener(handler);
                MisakaNetworkClient.NETWORK_MANAGER.dispatchPacket(instance);
            } catch (Throwable e) {
                LOGGER.error(
                        "Exception processing S2C packet. Class: {}, ID: {}. Listener: {}. Error: {}",
                        packetClass.getSimpleName(),
                        id,
                        handler.getClass().getSimpleName(),
                        e.getMessage(),
                        e
                );
            }
        });
    }
}