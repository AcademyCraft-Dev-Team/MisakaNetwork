package org.misaka.api.common.network.future.invoker;

import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;

@FunctionalInterface
public interface FutureHandler {
    ResponsePacket<?, ?> invoke(RequestPacket<?, ?, ?, ?> requestPacket);
}