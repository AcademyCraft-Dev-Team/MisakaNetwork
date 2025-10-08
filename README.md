### How to build?

```
./gradlew build
```

### IDEA Settings

If you are using IntelliJ IDEA, I recommend adding or replacing the following section in your `.idea/misc.xml` file:

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