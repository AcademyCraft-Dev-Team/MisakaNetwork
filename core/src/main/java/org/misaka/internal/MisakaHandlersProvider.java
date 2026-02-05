package org.misaka.internal;

import org.misaka.api.common.network.future.invoker.IFutureHandlerInvoker;
import org.misaka.api.common.network.listener.IPacketListener;

import java.util.function.Function;

public interface MisakaHandlersProvider {
    void register(Registry registry);

    interface Registry {
        void addStaticListener(Class<?> sourceClass, IPacketListener listener);

        void addInstanceListenerFactory(Class<?> sourceClass, Function<Object, IPacketListener> factory);

        void addStaticInvoker(Class<?> sourceClass, IFutureHandlerInvoker invoker);

        void addInstanceInvokerFactory(Class<?> sourceClass, Function<Object, IFutureHandlerInvoker> factory);
    }
}