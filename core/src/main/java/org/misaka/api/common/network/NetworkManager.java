package org.misaka.api.common.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.listener.PacketHandler;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.internal.ListenerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class NetworkManager {
    private final Map<Class<? extends Packet<?, ?>>, List<PacketHandler>> typedHandlers;
    private final Map<Object, List<PacketHandler>> handlersByTarget;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ReadWriteLock lock;

    public NetworkManager() {
        typedHandlers = new ConcurrentHashMap<>();
        handlersByTarget = new MapMaker().weakKeys().makeMap();
        lock = new ReentrantReadWriteLock();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register(Class targetClass, Class packetClass, PacketHandler handler) {
        lock.writeLock().lock();
        try {
            handlersByTarget.computeIfAbsent(targetClass, _ -> new ArrayList<>()).add(handler);
            typedHandlers.computeIfAbsent(packetClass, _ -> new ArrayList<>()).add(handler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register(Object targetInstance, Class packetClass, PacketHandler handler) {
        lock.writeLock().lock();
        try {
            handlersByTarget.computeIfAbsent(targetInstance, _ -> new ArrayList<>()).add(handler);
            typedHandlers.computeIfAbsent(packetClass, _ -> new ArrayList<>()).add(handler);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void register(Class<?> listenerClass) {
        for (var method : listenerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribePacket.class) && Modifier.isStatic(method.getModifiers())) {
                var packetType = method.getParameterTypes()[0];
                var handler = ListenerFactory.createPacketHandler(method, null);
                register(listenerClass, packetType.asSubclass(Packet.class), handler);
            }
        }
    }

    public void register(Object listenerInstance) {
        var listenerClass = listenerInstance.getClass();
        for (var method : listenerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(SubscribePacket.class) && !Modifier.isStatic(method.getModifiers())) {
                var packetType = method.getParameterTypes()[0];
                var handler = ListenerFactory.createPacketHandler(method, listenerInstance);
                register(listenerInstance, packetType.asSubclass(Packet.class), handler);
            }
        }
    }

    public void unregister(Class<?> targetClass) {
        unregisterInternal(targetClass);
    }

    public void unregister(Object targetInstance) {
        unregisterInternal(targetInstance);
    }

    private void unregisterInternal(Object target) {
        lock.writeLock().lock();
        try {
            var removed = handlersByTarget.remove(target);
            if (removed != null) {
                typedHandlers.values().forEach(list -> list.removeAll(removed));
                typedHandlers.values().removeIf(List::isEmpty);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void dispatchPacket(Packet<?, ?> packet) {
        List<PacketHandler> handlers;
        lock.readLock().lock();
        try {
            var typedList = typedHandlers.get(packet.getClass());
            handlers = (typedList != null && !typedList.isEmpty()) ? Lists.newArrayList(typedList) : null;
        } finally {
            lock.readLock().unlock();
        }

        if (handlers != null) {
            for (var handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    LOGGER.error("Exception dispatching packet {} to handler {}: {}",
                            packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }
}