package org.misaka.api.common.network.future.invoker;

public abstract class InstanceFutureHandlerInvoker implements IFutureHandlerInvoker {
    protected final Object instance;

    protected InstanceFutureHandlerInvoker(Object instance) {
        this.instance = instance;
    }
}