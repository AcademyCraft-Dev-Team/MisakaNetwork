package org.misaka.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import org.misaka.MisakaNetworkClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * For Network System
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    @Shadow
    public abstract Connection getConnection();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        MisakaNetworkClient.connection = getConnection();
    }
}