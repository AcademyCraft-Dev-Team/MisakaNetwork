package org.misaka.internal;

import org.misaka.api.common.network.future.invoker.IFutureHandlerInvoker;
import org.misaka.api.common.network.listener.IPacketListener;

import java.util.*;
import java.util.function.Function;

public final class MisakaRegistryAggregator {
    private static final Map<Class<?>, List<IPacketListener>> STATIC_LISTENERS;
    private static final Map<Class<?>, List<Function<Object, IPacketListener>>> INSTANCE_LISTENER_FACTORIES;
    private static final Map<Class<?>, List<IFutureHandlerInvoker>> STATIC_INVOKERS;
    private static final Map<Class<?>, List<Function<Object, IFutureHandlerInvoker>>> INSTANCE_INVOKER_FACTORIES;

    static {
        var staticListeners = new HashMap<Class<?>, List<IPacketListener>>();
        var instanceListenerFactories = new HashMap<Class<?>, List<Function<Object, IPacketListener>>>();
        var staticInvokers = new HashMap<Class<?>, List<IFutureHandlerInvoker>>();
        var instanceInvokerFactories = new HashMap<Class<?>, List<Function<Object, IFutureHandlerInvoker>>>();

        var registry = new MisakaHandlersProvider.Registry() {
            @Override
            public void addStaticListener(Class<?> sourceClass, IPacketListener listener) {
                staticListeners.computeIfAbsent(sourceClass, _ -> new ArrayList<>()).add(listener);
            }

            @Override
            public void addInstanceListenerFactory(Class<?> sourceClass, Function<Object, IPacketListener> factory) {
                instanceListenerFactories.computeIfAbsent(sourceClass, _ -> new ArrayList<>()).add(factory);
            }

            @Override
            public void addStaticInvoker(Class<?> sourceClass, IFutureHandlerInvoker invoker) {
                staticInvokers.computeIfAbsent(sourceClass, _ -> new ArrayList<>()).add(invoker);
            }

            @Override
            public void addInstanceInvokerFactory(Class<?> sourceClass, Function<Object, IFutureHandlerInvoker> factory) {
                instanceInvokerFactories.computeIfAbsent(sourceClass, _ -> new ArrayList<>()).add(factory);
            }
        };

        ServiceLoader.load(MisakaHandlersProvider.class)
                .forEach(provider -> provider.register(registry));

        STATIC_LISTENERS = Collections.unmodifiableMap(staticListeners);
        INSTANCE_LISTENER_FACTORIES = Collections.unmodifiableMap(instanceListenerFactories);
        STATIC_INVOKERS = Collections.unmodifiableMap(staticInvokers);
        INSTANCE_INVOKER_FACTORIES = Collections.unmodifiableMap(instanceInvokerFactories);
    }

    public static List<IPacketListener> getStaticListenersFor(Class<?> targetClass) {
        return STATIC_LISTENERS.getOrDefault(targetClass, Collections.emptyList());
    }

    public static List<Function<Object, IPacketListener>> getInstanceListenerFactoriesFor(Class<?> targetClass) {
        return INSTANCE_LISTENER_FACTORIES.getOrDefault(targetClass, Collections.emptyList());
    }

    public static List<IFutureHandlerInvoker> getStaticInvokersFor(Class<?> targetClass) {
        return STATIC_INVOKERS.getOrDefault(targetClass, Collections.emptyList());
    }

    public static List<Function<Object, IFutureHandlerInvoker>> getInstanceInvokerFactoriesFor(Class<?> targetClass) {
        return INSTANCE_INVOKER_FACTORIES.getOrDefault(targetClass, Collections.emptyList());
    }
}