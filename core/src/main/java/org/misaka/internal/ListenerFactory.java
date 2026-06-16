package org.misaka.internal;

import org.misaka.api.common.network.listener.PacketHandler;
import org.misaka.api.common.network.future.invoker.FutureHandler;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ListenerFactory {
    private ListenerFactory() {}

    public static PacketHandler createPacketHandler(Method method, @Nullable Object instance) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException(
                    "Method annotated with @SubscribePacket must be public: " + method
            );
        }
        try {
            var lookup = MethodHandles.lookup();
            var target = lookup.unreflect(method);
            var isStatic = Modifier.isStatic(method.getModifiers());

            var samType = MethodType.methodType(void.class, Packet.class);
            MethodType factoryType;
            MethodType instantiatedType;

            if (isStatic) {
                factoryType = MethodType.methodType(PacketHandler.class);
                instantiatedType = target.type();
            } else {
                var receiverType = method.getDeclaringClass();
                factoryType = MethodType.methodType(PacketHandler.class, receiverType);
                instantiatedType = target.type().dropParameterTypes(0, 1);
            }

            var site = LambdaMetafactory.metafactory(
                    lookup,
                    "handlePacket",
                    factoryType,
                    samType,
                    target,
                    instantiatedType
            );

            var factory = site.getTarget();
            if (!isStatic) {
                factory = factory.asType(MethodType.methodType(PacketHandler.class, Object.class));
            }
            return isStatic
                    ? (PacketHandler) factory.invoke()
                    : (PacketHandler) factory.invoke(instance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create PacketHandler for " + method, e);
        }
    }

    public static FutureHandler createFutureHandler(Method method, @Nullable Object instance) {
        if (!Modifier.isPublic(method.getModifiers())) {
            throw new IllegalArgumentException(
                    "Method annotated with @HandleFuture must be public: " + method
            );
        }
        try {
            var lookup = MethodHandles.lookup();
            var target = lookup.unreflect(method);
            var isStatic = Modifier.isStatic(method.getModifiers());

            var samType = MethodType.methodType(ResponsePacket.class, RequestPacket.class);
            MethodType factoryType;
            MethodType instantiatedType;

            if (isStatic) {
                factoryType = MethodType.methodType(FutureHandler.class);
                instantiatedType = target.type();
            } else {
                var receiverType = method.getDeclaringClass();
                factoryType = MethodType.methodType(FutureHandler.class, receiverType);
                instantiatedType = target.type().dropParameterTypes(0, 1);
            }

            var site = LambdaMetafactory.metafactory(
                    lookup,
                    "invoke",
                    factoryType,
                    samType,
                    target,
                    instantiatedType
            );

            var factory = site.getTarget();
            if (!isStatic) {
                factory = factory.asType(MethodType.methodType(FutureHandler.class, Object.class));
            }
            return isStatic
                    ? (FutureHandler) factory.invoke()
                    : (FutureHandler) factory.invoke(instance);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to create FutureHandler for " + method, e);
        }
    }
}