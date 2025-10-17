package org.misaka.api.common.network;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.logging.LogUtils;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.misaka.api.common.registries.MisakaNetworkRegistries.PACKET_TYPES;

public final class NetworkSystem {
    private static final AtomicBoolean DEBUG_INFO = new AtomicBoolean(false);
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BiMap<Class<? extends Packet<?, ?>>, PacketType<?, ?>> CLASS_TO_TYPE = HashBiMap.create();
    private static final Map<Class<? extends Packet<?, ?>>, ThreadType> THREAD_TYPE_MAP = new HashMap<>();

    private NetworkSystem() {
    }

    public static boolean isDebugInfo() {
        return DEBUG_INFO.get();
    }

    /**
     * 感觉有点多此一举了喵, 无所谓了喵
     */
    public static void setDebugInfo(boolean debugInfo) {
        boolean prev;
        do {
            prev = DEBUG_INFO.get();
            if (prev == debugInfo) return;
        } while (!DEBUG_INFO.compareAndSet(prev, debugInfo));
    }

    public static void progressRegistry() {
        for (var type : PACKET_TYPES) {
            CLASS_TO_TYPE.put(type.packetClass(), type);
            var clazz = type.packetClass();
            if (clazz.isAnnotationPresent(PacketTarget.class)) {
                var targetType = clazz.getAnnotation(PacketTarget.class).value();
                THREAD_TYPE_MAP.put(clazz, targetType);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends PacketType<?, ?>> T getPacketTypeById(int id) {
        return (T) PACKET_TYPES.byIdOrThrow(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends PacketType<?, ?>> T getPacketType(Class<?> packetClass) {
        var packetType = CLASS_TO_TYPE.get(packetClass);
        if (packetType == null) {
            if (CLASS_TO_TYPE.isEmpty()) {
                throw new IllegalStateException(
                        "Too early!!! It should be called after FMLCommonSetupEvent."
                );
            } else {
                throw new IllegalStateException(
                        "Unregistered packet class used: " + packetClass.getName()
                );
            }
        }
        return (T) packetType;
    }

    /**
     * 老实说这个报错很烦喵
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static <P extends Packet<?, ?>> boolean shouldReceive(Class<P> clazz, ThreadType currentThreadType) {
        var targetType = THREAD_TYPE_MAP.get(clazz);
        return targetType == null || targetType == currentThreadType;
    }
}