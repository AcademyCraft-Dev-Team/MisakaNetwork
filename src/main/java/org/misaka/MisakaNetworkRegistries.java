package org.misaka;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.misaka.api.common.network.packet.PacketType;

public final class MisakaNetworkRegistries {
    public static final Registry<PacketType<?, ?>> PACKET_TYPES = new RegistryBuilder<>(Keys.PACKET_TYPES).sync(true).create();

    public static final class Keys {
        public static final ResourceKey<Registry<PacketType<?, ?>>> PACKET_TYPES =
                ResourceKey.createRegistryKey(MisakaNetwork.location("packet_type"));

        private Keys() {
        }
    }

    private MisakaNetworkRegistries() {
    }
}
