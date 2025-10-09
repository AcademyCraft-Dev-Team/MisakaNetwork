package org.misaka.internal.common.network;

import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.misaka.MisakaNetwork;
import org.misaka.api.common.registries.MisakaNetworkRegistries;
import org.misaka.api.common.network.future.packet.FutureRequestPacket;
import org.misaka.api.common.network.future.packet.FutureResponsePacket;
import org.misaka.api.common.network.packet.PacketType;

public final class PacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, MisakaNetwork.MOD_ID);

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<PacketType<?, ?>, PacketType<?, ?>>
            FUTURE_REQUEST = PACKET_TYPES.register("future_request",
            () -> new PacketType<>(FutureRequestPacket.class, (StreamCodec) FutureRequestPacket.CODEC));

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static final DeferredHolder<PacketType<?, ?>, PacketType<?, ?>>
            FUTURE_RESPONSE = PACKET_TYPES.register("future_response",
            () -> new PacketType<>(FutureResponsePacket.class, (StreamCodec) FutureResponsePacket.CODEC));

    private PacketTypes() {
    }
}