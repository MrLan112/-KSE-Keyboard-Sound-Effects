package com.mrlan123.kse.mixin;

import com.mrlan123.kse.sound.SoundPlayer;
import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void kse$onKey(long window, int action, KeyInput input, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            SoundPlayer.onKeyboard(input.key());
        }
    }
}
