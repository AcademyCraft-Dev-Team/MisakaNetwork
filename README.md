### How to use?

For reference, see [AcademyCraft-Reborn](https://github.com/AcademyCraft-Dev-Team/AcademyCraft-Reborn).

``` groovy
repositories {
    maven {
        url = "https://raw.githubusercontent.com/AcademyCraft-Dev-Team/maven-repo/main/"
    }
}

dependencies {
    // It is recommended to use jarJar
    jarJar annotationProcessor(implementation("org.academy:misaka-network:21.10.1"))
    annotationProcessor("com.google.auto.service:auto-service:1.1.1")
}

idea {
    module {
        def buildDirFile = layout.buildDirectory.get().asFile
        def generatedSourceDir = file("${buildDirFile}/generated/sources/annotationProcessor/java/main")
        generatedSourceDirs += generatedSourceDir
    }
}
```

### Example

```java
@Mod(Example.MOD_ID)
public class Example {
    public static final String MOD_ID = "example";

    public Example(IEventBus modEventBus) {
        ExamplePacketTypes.PACKET_TYPES.register(modEventBus);
    }
}

public class ExamplePacketTypes {
    public static final DeferredRegister<PacketType<?, ?>> PACKET_TYPES =
            DeferredRegister.create(MisakaNetworkRegistries.Keys.PACKET_TYPES, Example.MOD_ID);

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
public class APacket extends Packet<ClientPacketListener, APacket> {
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
public class BPacket extends Packet<ClientPacketListener, BPacket> {
    public static final BPacket INSTANCE = new BPacket();
    public static final StreamCodec<ByteBuf, BPacket> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public PacketType<ClientPacketListener, BPacket> getPacketType() {
        return ExamplePacketTypes.B.get();
    }
}

@PacketTarget(ThreadType.SERVER)
public class CPacket extends Packet<ServerGamePacketListenerImpl, CPacket> {
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
public class AFuturePacket extends RequestPacket<ServerGamePacketListenerImpl, AFuturePacket, ClientPacketListener, AFuturePacket.Response> {
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
    public class Response extends ResponsePacket<ClientPacketListener, Response> {
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
public class ExampleClient {
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
public class ExampleServer {
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
```

### How to build?

``` shell
./gradlew build
```

### IDEA Settings

If you are using IntelliJ IDEA, I recommend adding or replacing the following section in your `.idea/misc.xml` file:
This will mark the methods as entry points to prevent "unused" warnings

```xml

<component name="EntryPointsManager">
    <list size="2">
        <item index="0" class="java.lang.String"
              itemvalue="org.misaka.api.common.network.annotation.SubscribePacket"/>
        <item index="1" class="java.lang.String"
              itemvalue="org.misaka.api.common.network.future.annotation.HandleFuture"/>
    </list>
</component>
```