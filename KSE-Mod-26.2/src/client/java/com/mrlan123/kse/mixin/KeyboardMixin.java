package com.mrlan123.kse.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrlan123.kse.sound.SoundPlayer;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void kse$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == InputConstants.PRESS) {
            SoundPlayer.onKeyboard(event.key());
        }
    }
}
