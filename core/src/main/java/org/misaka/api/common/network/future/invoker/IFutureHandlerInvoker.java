package org.misaka.api.common.network.future.invoker;

import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;

public interface IFutureHandlerInvoker {
    ResponsePacket<?, ?> invoke(RequestPacket<?, ?, ?, ?> requestPacket);

    <P extends RequestPacket<?, ?, ?, ?>> Class<P> getRequestPacketClass();
}