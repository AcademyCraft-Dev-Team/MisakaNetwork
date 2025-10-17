package org.misaka.internal;

import org.misaka.api.common.network.listener.IPacketListener;
import org.misaka.api.common.network.future.invoker.IFutureHandlerInvoker;

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

        var providers = ServiceLoader.load(MisakaHandlersProvider.class);

        for (var provider : providers) {
            provider.getStaticListeners().forEach((key, value) -> staticListeners.merge(key, value, MisakaRegistryAggregator::mergeLists));
            provider.getInstanceListenerFactories().forEach((key, value) -> instanceListenerFactories.merge(key, value, MisakaRegistryAggregator::mergeLists));
            provider.getStaticInvokers().forEach((key, value) -> staticInvokers.merge(key, value, MisakaRegistryAggregator::mergeLists));
            provider.getInstanceInvokerFactories().forEach((key, value) -> instanceInvokerFactories.merge(key, value, MisakaRegistryAggregator::mergeLists));
        }

        STATIC_LISTENERS = Collections.unmodifiableMap(staticListeners);
        INSTANCE_LISTENER_FACTORIES = Collections.unmodifiableMap(instanceListenerFactories);
        STATIC_INVOKERS = Collections.unmodifiableMap(staticInvokers);
        INSTANCE_INVOKER_FACTORIES = Collections.unmodifiableMap(instanceInvokerFactories);
    }

    private static <T> List<T> mergeLists(List<T> l1, List<T> l2) {
        var merged = new ArrayList<T>(l1.size() + l2.size());
        merged.addAll(l1);
        merged.addAll(l2);
        return merged;
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