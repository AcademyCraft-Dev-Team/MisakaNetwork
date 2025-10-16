package org.misaka.test;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.misaka.MisakaNetwork;
import org.misaka.MisakaNetworkClient;
import org.misaka.MisakaNetworkServer;
import org.misaka.api.common.network.ThreadType;
import org.misaka.api.common.network.annotation.PacketTarget;
import org.misaka.api.common.network.annotation.SubscribePacket;
import org.misaka.api.common.network.future.annotation.HandleFuture;
import org.misaka.api.common.network.future.packet.RequestPacket;
import org.misaka.api.common.network.future.packet.ResponsePacket;
import org.misaka.api.common.network.packet.Packet;
import org.misaka.api.common.network.packet.PacketType;
import org.misaka.api.common.registries.MisakaNetworkRegistries;

@Mod(MisakaNetwork.MOD_ID)
public final class MisakaNetworkTest {
    public MisakaNetworkTest(IEventBus modEventBus) {
        ExamplePacketTypes.PACKET_TYPES.register(modEventBus);
    }

    public static class ExamplePacketTypes {
        public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
                DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, MisakaNetwork.MOD_ID);

        public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, APacket>>
                A = PACKET_TYPES.register("a",
                () -> new PacketType<>(APacket.class, APacket.CODEC));
        public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, BPacket>>
                B = PACKET_TYPES.register("b",
                () -> new PacketType<>(BPacket.class, BPacket.CODEC));
        public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, CPacket>>
                C = PACKET_TYPES.register("c",
                () -> new PacketType<>(CPacket.class, CPacket.CODEC));

        public static final DeferredHolder<PacketType<?, ?>, PacketType<ServerGamePacketListenerImpl, AFuturePacket>>
                AF = PACKET_TYPES.register("af",
                () -> new PacketType<>(AFuturePacket.class, AFuturePacket.CODEC));
        public static final DeferredHolder<PacketType<?, ?>, PacketType<ClientPacketListener, AFuturePacket.Response>>
                AFR = PACKET_TYPES.register("afr",
                () -> new PacketType<>(AFuturePacket.Response.class, AFuturePacket.Response.CODEC));
    }

    @PacketTarget(ThreadType.CLIENT)
    public static class APacket extends Packet<ClientPacketListener, APacket> {
        public static final StreamCodec<ByteBuf, APacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                APacket::getF,
                APacket::new
        );

        private final float f;

        public APacket(float f) {
            this.f = f;
        }

        public float getF() {
            return f;
        }

        @Override
        public PacketType<ClientPacketListener, APacket> getPacketType() {
            return ExamplePacketTypes.A.get();
        }
    }

    @PacketTarget(ThreadType.CLIENT)
    public static class BPacket extends Packet<ClientPacketListener, BPacket> {
        public static final BPacket INSTANCE = new BPacket();
        public static final StreamCodec<ByteBuf, BPacket> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public PacketType<ClientPacketListener, BPacket> getPacketType() {
            return ExamplePacketTypes.B.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static class CPacket extends Packet<ServerGamePacketListenerImpl, CPacket> {
        public static final StreamCodec<ByteBuf, CPacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                CPacket::getF,
                CPacket::new
        );

        private final float f;

        public CPacket(float f) {
            this.f = f;
        }

        public float getF() {
            return f;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, CPacket> getPacketType() {
            return ExamplePacketTypes.C.get();
        }
    }

    @PacketTarget(ThreadType.SERVER)
    public static class AFuturePacket extends RequestPacket<ServerGamePacketListenerImpl, AFuturePacket, ClientPacketListener, AFuturePacket.Response> {
        public static final StreamCodec<ByteBuf, AFuturePacket> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT,
                AFuturePacket::getF,
                AFuturePacket::new
        );

        private final float f;

        public AFuturePacket(float f) {
            this.f = f;
        }

        public float getF() {
            return f;
        }

        @Override
        public PacketType<ServerGamePacketListenerImpl, AFuturePacket> getPacketType() {
            return ExamplePacketTypes.AF.get();
        }

        @Override
        public PacketType<ClientPacketListener, Response> getResponsePacketType() {
            return ExamplePacketTypes.AFR.get();
        }

        @PacketTarget(ThreadType.SERVER)
        public static class Response extends ResponsePacket<ClientPacketListener, Response> {
            public static final StreamCodec<ByteBuf, Response> CODEC = StreamCodec.composite(
                    ByteBufCodecs.FLOAT,
                    Response::getF,
                    Response::new
            );

            private final float f;

            public Response(float f) {
                this.f = f;
            }

            public float getF() {
                return f;
            }

            @Override
            public PacketType<ClientPacketListener, Response> getPacketType() {
                return ExamplePacketTypes.AFR.get();
            }
        }
    }

    @EventBusSubscriber
    public static class ExampleClient {
        @SubscribeEvent
        public static void init(ClientStartedEvent event) {
            // onA
            MisakaNetworkClient.NETWORK_MANAGER.registerPacketListener(ExampleClient.class);
            // onB
            MisakaNetworkClient.NETWORK_MANAGER.registerPacketListener(new ExampleClient());
        }

        @SubscribeEvent
        public static void res(PlayerEvent.PlayerRespawnEvent event) {
            MisakaNetworkClient.sendPacket(new CPacket(1f));
            var request = new AFuturePacket(114514f);
            MisakaNetworkClient.FUTURE_MANAGER.sendRequestToServer(
                    request,
                    response -> System.out.print(response.getF())
            );
        }

        @SubscribePacket
        public static void onA(APacket packet) {
            System.out.print(packet.getF());
        }

        @SubscribePacket
        public void onB(BPacket packet) {
            System.out.print(packet == BPacket.INSTANCE);
        }
    }

    @EventBusSubscriber
    public static class ExampleServer {
        @SubscribeEvent
        public static void init(ServerStartedEvent event) {
            MisakaNetworkServer.NETWORK_MANAGER.registerPacketListener(ExampleServer.class);
            MisakaNetworkServer.FUTURE_MANAGER.registerFutureHandler(ExampleServer.class);
        }

        @SubscribePacket
        public static void onC(CPacket packet) {
            System.out.print(packet.getF());
            MisakaNetworkServer.sendPacket(packet.getPacketListener(), new APacket(1f));
            MisakaNetworkServer.sendPacket(packet.getPacketListener(), BPacket.INSTANCE);
        }

        @HandleFuture
        public static AFuturePacket.Response handleAFuture(AFuturePacket packet) {
            return new AFuturePacket.Response(packet.getF());
        }
    }
}