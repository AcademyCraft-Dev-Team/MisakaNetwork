package org.misaka.api.common.network.packet;

import net.minecraft.network.PacketListener;
import org.jetbrains.annotations.Nullable;

/**
 * 注意 C/S 所拥有的类差异喵
 * <br>
 * 应该没有人会让构造函数接受一个客户端独有类吧喵
 */
public abstract class Packet<T extends PacketListener, P extends Packet<T, P>> {
    @Nullable
    private T packetListener;

    public T getPacketListener() {
        if (packetListener == null) {
            throw new IllegalStateException("Cannot get PacketListener on the sending side; it is only available for a received packet.");
        }
        return packetListener;
    }

    public final void setPacketListener(T packetListener) {
        this.packetListener = packetListener;
    }

    public abstract PacketType<T, P> getPacketType();
}