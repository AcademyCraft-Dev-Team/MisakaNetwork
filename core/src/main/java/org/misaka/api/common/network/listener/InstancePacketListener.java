package org.misaka.api.common.network.listener;

public abstract class InstancePacketListener implements IPacketListener {
    protected final Object instance;

    protected InstancePacketListener(Object instance) {
        this.instance = instance;
    }
}