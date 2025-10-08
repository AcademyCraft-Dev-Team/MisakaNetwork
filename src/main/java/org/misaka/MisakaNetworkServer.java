package org.misaka;

import com.mojang.logging.LogUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.misaka.api.common.network.NetworkManager;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.S2CPacket;
import org.misaka.api.server.network.future.FutureManagerServer;
import org.slf4j.Logger;

import static org.misaka.MisakaNetwork.shutdownExecutorService;

@EventBusSubscriber(modid = MisakaNetwork.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class MisakaNetworkServer {
    public static final NetworkManager NETWORK_MANAGER = new NetworkManager();
    public static final FutureManagerServer FUTURE_MANAGER = new FutureManagerServer();
    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        NETWORK_MANAGER.registerPacketListener(FUTURE_MANAGER);
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(ServerPlayer player, P packet) {
        player.connection.send(new S2CPacket(packet));
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(Connection connection, P packet) {
        connection.send(new S2CPacket(packet));
    }

    public static <P extends Packet<ClientGamePacketListener, P>> void sendPacket(ServerGamePacketListenerImpl listener, P packet) {
        listener.send(new S2CPacket(packet));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("Dedicated server is stopping. Shutting down MisakaNetwork ExecutorService.");
        shutdownExecutorService();
    }

    private MisakaNetworkServer() {
    }
}