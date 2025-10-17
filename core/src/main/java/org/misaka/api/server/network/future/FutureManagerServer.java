package org.misaka.api.server.network.future;

import io.netty.buffer.Unpooled;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.ClientboundPacketListener;
import net.minecraft.network.ServerboundPacketListener;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.future.AbstractFutureManager;
import org.misaka.api.common.network.future.packet.FutureRequestPacket;
import org.misaka.api.common.network.future.packet.FutureResponsePacket;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.S2CPacket;

import java.util.function.Consumer;

/**
 * 可以 new 一个实例, 但推荐使用 MisakaNetwork 提供的以免造成线程资源浪费
 */
public class FutureManagerServer extends AbstractFutureManager {
    public FutureManagerServer() {
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToClient(ServerPlayer player, REQ_P requestPacket, Consumer<RES_P> callback, long timeoutMillis) {
        var futureId = createPendingFuture(requestPacket.getResponsePacketType(), callback, timeoutMillis);
        if (futureId == -1) return;
        var requestTypeId = requestPacket.getPacketType().getPacketId();
        var buffer = Unpooled.buffer();
        requestPacket.getPacketType().codec().encode(buffer, requestPacket);

        var bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);

        var packet = new FutureRequestPacket<ClientPacketListener>(futureId, requestTypeId, bytes);
        player.connection.send(new S2CPacket(packet));
    }

    public <
            RES_L extends ClientboundPacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_L extends ServerboundPacketListener,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            >
    void sendRequestToClient(ServerPlayer player, REQ_P requestPacket, Consumer<RES_P> callback) {
        sendRequestToClient(player, requestPacket, callback, DEFAULT_TIMEOUT_MS);
    }

    @SubscribePacket
    public <
            RES_P extends ResponsePacket<ClientPacketListener, RES_P>,
            REQ_P extends RequestPacket<ServerGamePacketListenerImpl, REQ_P, ClientPacketListener, RES_P>
            > void handleFutureRequestFromClient(FutureRequestPacket<ServerGamePacketListenerImpl> futureRequestPacket) {
        var packetListener = futureRequestPacket.getPacketListener();
        var player = packetListener.getPlayer();

        super.<ServerGamePacketListenerImpl, ClientPacketListener, RES_P, REQ_P>handleRequest(
                futureRequestPacket, futureRequestPacket.getPacketListener(), response -> {
                    var responseTypeId = response.getPacketType().getPacketId();
                    var responseBuffer = Unpooled.buffer();
                    response.getPacketType().codec().encode(responseBuffer, response);

                    var bytes = new byte[responseBuffer.readableBytes()];
                    responseBuffer.readBytes(bytes);

                    var responsePkt = new FutureResponsePacket<ClientPacketListener>(
                            futureRequestPacket.getFutureId(), responseTypeId, bytes
                    );
                    player.connection.send(new S2CPacket(responsePkt));
                }
        );
    }

    @SubscribePacket
    public void handleFutureResponseFromClient(FutureResponsePacket<ServerGamePacketListenerImpl> responsePacket) {
        handleResponse(responsePacket, resPacket ->
                responsePacket.getPacketListener().server.execute(
                        () -> executeCallback(
                                responsePacket.getFutureId(), resPacket
                        )
                )
        );
    }
}