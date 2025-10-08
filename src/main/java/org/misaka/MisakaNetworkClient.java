package org.misaka;

import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import org.jetbrains.annotations.Nullable;
import org.misaka.api.client.network.future.FutureManagerClient;
import org.misaka.api.common.network.NetworkManager;
import org.misaka.api.common.network.packet.C2SPacket;
import org.misaka.api.common.network.packet.Packet;
import org.slf4j.Logger;

import static org.misaka.MisakaNetwork.shutdownExecutorService;

@EventBusSubscriber(modid = MisakaNetwork.MOD_ID, value = Dist.CLIENT)
public final class MisakaNetworkClient {
    @Nullable
    public static Connection connection;
    public static final NetworkManager NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerClient FUTURE_MANAGER = new FutureManagerClient();
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        NETWORK_MANAGER.registerPacketListener(FUTURE_MANAGER);
    }

    public static void sendPacket(net.minecraft.network.protocol.Packet<?> packet) {
        if (connection != null) {
            connection.send(packet);
        }
    }

    @SubscribeEvent
    public static void onClientStopping(ClientStoppingEvent event) {
        LOGGER.info("Client is stopping. Shutting down MisakaNetwork ExecutorService.");
        shutdownExecutorService();
    }

    public static <P extends Packet<ServerGamePacketListenerImpl, P>> void sendPacket(P packet) {
        sendPacket(new C2SPacket(packet));
    }

    private MisakaNetworkClient() {
    }
}