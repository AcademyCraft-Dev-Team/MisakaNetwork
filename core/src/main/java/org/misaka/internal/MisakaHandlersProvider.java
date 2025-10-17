package org.misaka.internal;

import org.jetbrains.annotations.ApiStatus;
import org.misaka.api.common.network.listener.IPacketListener;
import org.misaka.api.common.network.future.invoker.IFutureHandlerInvoker;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@ApiStatus.Internal
public interface MisakaHandlersProvider {
    Map<Class<?>, List<IPacketListener>> getStaticListeners();

    Map<Class<?>, List<Function<Object, IPacketListener>>> getInstanceListenerFactories();

    Map<Class<?>, List<IFutureHandlerInvoker>> getStaticInvokers();

    Map<Class<?>, List<Function<Object, IFutureHandlerInvoker>>> getInstanceInvokerFactories();
}