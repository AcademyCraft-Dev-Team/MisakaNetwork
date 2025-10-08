package org.misaka.api.common.network.asm;

public abstract class InstancePacketListener implements IPacketListener {
    protected final Object instance;

    protected InstancePacketListener(Object newInstance) {
        instance = newInstance;
    }
}