package org.misaka.api.common.network;

import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import org.misaka.api.common.network.asm.IPacketListener;
import org.misaka.api.common.network.packet.Packet;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 理论上你可以自己 new 一个实例喵, 但是没什么必要喵, 推荐直接用 MisakaNetwork 的喵
 */
public final class NetworkManager {
    private final ConcurrentHashMap<Class<? extends Packet<?, ?>>, List<IPacketListener>> typedListeners;
    private final Map<Object, List<IPacketListener>> listenersByTarget;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final ReadWriteLock lock;

    public NetworkManager() {
        typedListeners = new ConcurrentHashMap<>();
        listenersByTarget = new MapMaker().weakKeys().makeMap();
        lock = new ReentrantReadWriteLock();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            typedListeners.clear();
            listenersByTarget.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void registerPacketListener(Class<?> targetClass) {
        registerPacketListenerInternal(targetClass, null);
    }

    public void registerPacketListener(Object targetInstance) {
        registerPacketListenerInternal(targetInstance.getClass(), targetInstance);
    }

    public void registerPacketListener(IPacketListener iPacketListener) {
        lock.writeLock().lock();
        try {
            listenersByTarget.computeIfAbsent(iPacketListener.getPacketClass(), o -> new ArrayList<>()).add(iPacketListener);
            typedListeners.computeIfAbsent(iPacketListener.getPacketClass(), k -> new ArrayList<>()).add(iPacketListener);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void registerPacketListenerInternal(Class<?> clazz, @Nullable Object instance) {
        var generatedHandlers = NetworkSystem.findPacketListeners(clazz, instance);

        if (!generatedHandlers.isEmpty()) {
            lock.writeLock().lock();
            try {
                var key = (instance == null) ? clazz : instance;
                listenersByTarget.put(key, List.copyOf(generatedHandlers));

                for (var handler : generatedHandlers) {
                    typedListeners.computeIfAbsent(handler.getPacketClass(), k -> new ArrayList<>()).add(handler);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    public void unregisterPacketListener(Class<?> targetClass) {
        unregisterPacketListenerInternal(targetClass);
    }

    public void unregisterPacketListener(Object targetInstance) {
        unregisterPacketListenerInternal(targetInstance);
    }

    private void unregisterPacketListenerInternal(Object keyToRemove) {
        lock.writeLock().lock();
        try {
            var handlersToRemove = listenersByTarget.remove(keyToRemove);
            if (handlersToRemove != null) {
                for (var handler : handlersToRemove) {
                    var typedList = typedListeners.get(handler.getPacketClass());
                    if (typedList != null) {
                        typedList.remove(handler);
                        if (typedList.isEmpty()) {
                            typedListeners.remove(handler.getPacketClass());
                        }
                    }
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void dispatchPacket(Packet<?, ?> packet) {
        List<IPacketListener> handlers = null;
        lock.readLock().lock();
        try {
            var typedList = typedListeners.get(packet.getClass());
            if (typedList != null && !typedList.isEmpty()) {
                handlers = Lists.newArrayList(typedList);
            }
        } finally {
            lock.readLock().unlock();
        }

        if (handlers != null) {
            for (var handler : handlers) {
                try {
                    handler.handlePacket(packet);
                } catch (Throwable e) {
                    LOGGER.error("Exception dispatching packet {} to handler {}: {}", packet.getClass().getSimpleName(), handler.getClass().getName(), e.getMessage(), e);
                }
            }
        }
    }
}