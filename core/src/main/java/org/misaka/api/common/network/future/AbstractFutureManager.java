package org.misaka.api.common.network.future;

import com.mojang.logging.LogUtils;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketListener;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetwork;
import org.misaka.api.common.network.NetworkSystem;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.misaka.api.common.network.future.invoker.FutureHandler;
import org.misaka.api.common.network.future.packet.FuturePacket;
import org.misaka.api.common.network.future.packet.FutureRequestPacket;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.internal.ListenerFactory;
import org.slf4j.Logger;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class AbstractFutureManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected final Map<Integer, PendingFutureInfo> pendingFutures = new ConcurrentHashMap<>();
    protected final Map<Integer, FutureHandler> requestHandlers = new ConcurrentHashMap<>();
    private final AtomicInteger nextFutureId = new AtomicInteger(0);
    protected static final long DEFAULT_TIMEOUT_MS = 60000;

    protected record PendingFutureInfo(Consumer<? extends @Nullable Object> callback,
                                       int expectedResponsePacketId,
                                       long expireTime
    ) {
    }

    protected AbstractFutureManager() {
        MisakaNetwork.executorService.scheduleAtFixedRate(this::cleanupTimedOutFutures, 1, 1, TimeUnit.SECONDS);
    }

    public void clear() {
        pendingFutures.clear();
        requestHandlers.clear();
    }

    protected int generateFutureId() {
        return nextFutureId.getAndIncrement();
    }

    protected <T_RESP extends ResponsePacket<?, T_RESP>> int createPendingFuture(
            PacketType<?, T_RESP> responsePacketType, Consumer<@Nullable T_RESP> callback, long timeoutMillis
    ) {
        var futureId = generateFutureId();
        var expectedResponsePacketId = responsePacketType.getPacketId();
        if (expectedResponsePacketId == -1) {
            LOGGER.error("Response packet type {} is not registered.", responsePacketType.packetClass().getName());
            return -1;
        }
        var expireTime = System.currentTimeMillis() + timeoutMillis;
        pendingFutures.put(futureId, new PendingFutureInfo(callback, expectedResponsePacketId, expireTime));
        return futureId;
    }

    @SuppressWarnings("rawtypes")
    public void register(Class<? extends RequestPacket> requestClass, FutureHandler handler) {
        var id = NetworkSystem.getPacketType(requestClass).getPacketId();
        requestHandlers.put(id, handler);
    }

    public void register(Object handlerInstance) {
        var handlerClass = handlerInstance.getClass();
        for (var method : handlerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(HandleFuture.class) && !Modifier.isStatic(method.getModifiers())) {
                var requestType = method.getParameterTypes()[0];
                var handler = ListenerFactory.createFutureHandler(method, handlerInstance);
                register(requestType.asSubclass(RequestPacket.class), handler);
            }
        }
    }

    public void register(Class<?> handlerClass) {
        for (var method : handlerClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(HandleFuture.class) && Modifier.isStatic(method.getModifiers())) {
                var requestType = method.getParameterTypes()[0];
                var handler = ListenerFactory.createFutureHandler(method, null);
                register(requestType.asSubclass(RequestPacket.class), handler);
            }
        }
    }

    /**
     * 处理 FutureRequestPacket 喵
     * <br>
     * Request 发送过来后, 由带有 HandleFuture 注解的方法处理并返回 ResponsePacket 喵, 随后后将 Response 发送回去喵
     *
     * @param futureRequestPacket 各端 FutureManager 传来的实例喵
     * @param packetListener      当前端的 PacketListener 喵
     * @param responseSender      用于当前端发送 ResponsePacket 喵
     * @param <REQ_L>             当前端的 PacketListener 的泛型喵
     * @param <RES_L>             目标端的 PacketListener 喵, 只是泛型占位而已喵, 没有使用喵
     * @param <RES_P>             RequestPacket 期望的 ResponsePacket 的泛型喵, responsePacket 的类型喵
     * @param <REQ_P>             RequestPacket 的泛型喵, instance 的类型喵
     */
    @SuppressWarnings("unchecked")
    protected <
            REQ_L extends PacketListener,
            RES_L extends PacketListener,
            RES_P extends ResponsePacket<RES_L, RES_P>,
            REQ_P extends RequestPacket<REQ_L, REQ_P, RES_L, RES_P>
            > void handleRequest(FutureRequestPacket<REQ_L> futureRequestPacket, REQ_L packetListener, Consumer<RES_P> responseSender) {
        var targetPacketTypeId = futureRequestPacket.getTargetPacketTypeId();

        var handler = requestHandlers.get(targetPacketTypeId);
        if (handler == null) {
            LOGGER.error("No handler for request packet ID {}", targetPacketTypeId);
            return;
        }

        var packetType = NetworkSystem.<PacketType<REQ_L, REQ_P>>getPacketTypeById(targetPacketTypeId);
        var codec = packetType.codec();
        var bytes = futureRequestPacket.getBytes();

        if (NetworkSystem.isDebugInfo()) LOGGER.debug(Arrays.toString(bytes));

        var instance = codec.decode(Unpooled.wrappedBuffer(bytes));
        instance.setPacketListener(packetListener);

        var responsePacket = handler.invoke(instance);
        responseSender.accept((RES_P) responsePacket);
    }

    protected <
            L extends PacketListener,
            F_P extends FuturePacket<L, F_P>,
            RES_P extends ResponsePacket<L, RES_P>
            >
    void handleResponse(F_P responsePacket, Consumer<RES_P> callbackExecutor) {
        var targetPacketTypeId = responsePacket.getTargetPacketTypeId();
        var info = pendingFutures.get(responsePacket.getFutureId());
        if (info == null) {
            LOGGER.warn("Received response for unknown/timed-out futureId: {}", responsePacket.getFutureId());
            return;
        }

        if (info.expectedResponsePacketId != -1 && info.expectedResponsePacketId != targetPacketTypeId) {
            LOGGER.error("Mismatched response packet. Expected ID {}, Got ID {}", info.expectedResponsePacketId, targetPacketTypeId);
            return;
        }

        var codec = NetworkSystem.<PacketType<L, RES_P>>getPacketTypeById(targetPacketTypeId).codec();

        try {
            var buffer = Unpooled.buffer();
            var bytes = responsePacket.getBytes();
            if (NetworkSystem.isDebugInfo()) LOGGER.debug(Arrays.toString(bytes));
            var resP = codec.decode(buffer.writeBytes(bytes));
            resP.setPacketListener(responsePacket.getPacketListener());
            callbackExecutor.accept(resP);
        } catch (Exception e) {
            LOGGER.error("Error processing response for futureId {}: {}", responsePacket.getFutureId(), e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    protected void executeCallback(int futureId, ResponsePacket<?, ?> responsePacket) {
        var info = pendingFutures.remove(futureId);
        if (info != null) {
            try {
                ((Consumer<ResponsePacket<?, ?>>) info.callback()).accept(responsePacket);
            } catch (Exception e) {
                LOGGER.error("Error executing callback for futureId {}: {}", futureId, e.getMessage(), e);
            }
        } else {
            LOGGER.warn("Response for futureId {} arrived, but future was already handled/timed out.", futureId);
        }
    }

    private void cleanupTimedOutFutures() {
        var now = System.currentTimeMillis();
        pendingFutures.forEach((id, info) -> {
            if (now > info.expireTime()) {
                LOGGER.warn("Future {} timed out.", id);
                if (pendingFutures.remove(id, info)) {
                    try {
                        info.callback().accept(null);
                    } catch (Exception e) {
                        LOGGER.error("Error executing timeout callback for futureId {}", id, e);
                    }
                }
            }
        });
    }
}