package org.misaka.api.client.network.future;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.misaka.MisakaNetworkClient;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.future.AbstractFutureManager;
import org.misaka.api.common.network.future.packet.FutureRequestPacket;
import org.misaka.api.common.network.future.packet.FutureResponsePacket;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.function.Consumer;

/**
 * 可以 new 一个实例, 但推荐使用 MisakaNetwork 提供的以免造成线程资源浪费
 */
public final class FutureManagerClient extends AbstractFutureManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    public FutureManagerClient() {
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToServer(REQ_P requestPacket, Consumer<RES_P> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPacket.getResponsePacketType(), callback, timeoutMillis);
        if (futureId == -1) return;
        var requestTypeId = requestPacket.getPacketType().getPacketId();
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        requestPacket.getPacketType().codec().encode(buffer, requestPacket);

        var bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);

        var packet = new FutureRequestPacket<ServerGamePacketListenerImpl>(futureId, requestTypeId, bytes);
        MisakaNetworkClient.sendPacket(packet);
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToServer(REQ_P requestPacket, Consumer<RES_P> callback) {
        sendRequestToServer(requestPacket, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public <
            RES_P extends ResponsePacket<ServerGamePacketListenerImpl, RES_P>,
            REQ_P extends RequestPacket<ClientGamePacketListener, REQ_P, ServerGamePacketListenerImpl, RES_P>
            > void handleFutureRequestFromServer(FutureRequestPacket<ClientGamePacketListener> futureRequestPacket) {
        super.<ClientGamePacketListener, ServerGamePacketListenerImpl, RES_P, REQ_P>handleRequest(
                futureRequestPacket, futureRequestPacket.getPacketListener(), response -> {
                    var responseTypeId = response.getPacketType().getPacketId();
                    var responseBuffer = Unpooled.buffer();
                    response.getPacketType().codec().encode(responseBuffer, response);

                    var bytes = new byte[responseBuffer.readableBytes()];
                    responseBuffer.readBytes(bytes);

                    if (NetworkSystem.isDebugInfo()) {
                        LOGGER.debug("Response bytes: {}", Arrays.toString(bytes));
                    }

                    var responsePkt = new FutureResponsePacket<ServerGamePacketListenerImpl>(
                            futureRequestPacket.getFutureId(), responseTypeId, bytes
                    );
                    MisakaNetworkClient.sendPacket(responsePkt);
                }
        );
    }

    @SubscribePacket
    public void handleFutureResponseFromServer(FutureResponsePacket<ClientGamePacketListener> responsePacket) {
        handleResponse(responsePacket, resPacket ->
                Minecraft.getInstance().execute(
                        () -> executeCallback(
                                responsePacket.getFutureId(), resPacket
                        )
                )
        );
    }
}