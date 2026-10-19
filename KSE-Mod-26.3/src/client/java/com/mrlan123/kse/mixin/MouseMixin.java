package com.mrlan123.kse.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrlan123.kse.sound.SoundPlayer;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {

    @Inject(method = "onButton", at = @At("HEAD"))
    private void kse$onButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            SoundPlayer.onMouse(info.button());
        }
    }
}
