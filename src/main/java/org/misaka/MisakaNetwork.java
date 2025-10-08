package org.misaka;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.future.PacketTypes;
import org.slf4j.Logger;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.misaka.MisakaNetworkRegistries.PACKET_TYPES;

@Mod(MisakaNetwork.MOD_ID)
public final class MisakaNetwork {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static final String MOD_ID = "misaka_network";

    public MisakaNetwork(IEventBus modEventBus) {
        PacketTypes.PACKET_TYPES.register(modEventBus);
        modEventBus.addListener(MisakaNetwork::onNewRegistry);
        modEventBus.addListener(MisakaNetwork::onCommonSetup);
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(PACKET_TYPES);
    }

    private static void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkSystem::progressRegistry);
    }

    public static void shutdownExecutorService() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("ExecutorService did not terminate in 5 seconds. Forcing shutdown.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOGGER.error("Interrupted while waiting for ExecutorService to terminate. Forcing shutdown.");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}