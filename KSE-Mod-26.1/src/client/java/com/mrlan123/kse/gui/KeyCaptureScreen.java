package com.mrlan123.kse.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Step 1: "Press any key or button" - the next key or mouse button is recorded.
 * Step 2: shows what was pressed with the buttons "Add" and "Try again".
 */
public class KeyCaptureScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;

    private final KeyEditorScreen parent;
    private KSEConfig.BoundKey captured = null;
    private Component error = null;

    public KeyCaptureScreen(KeyEditorScreen parent) {
        super(Component.translatable("kse.capture.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (captured == null) {
            return;
        }
        int centerX = this.width / 2;
        int y = this.height / 2 + 20;

        this.addRenderableWidget(Button.builder(Component.translatable("kse.capture.add"), b -> confirmAdd())
                .bounds(centerX - 105, y, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("kse.capture.repeat"), b -> {
                    captured = null;
                    error = null;
                    reload();
                })
                .bounds(centerX + 5, y, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("kse.capture.cancel"), b -> onClose())
                .bounds(centerX - 50, y + 24, 100, 20)
                .build());
    }

    private void reload() {
        this.clearWidgets();
        this.init();
    }

    private void capture(boolean mouse, int code) {
        captured = new KSEConfig.BoundKey(mouse, code);
        error = null;
        reload();
    }

    private void confirmAdd() {
        if (!KSEConfig.get().addKey(captured.mouse, captured.code)) {
            error = Component.translatable("kse.capture.already");
            return;
        }
        this.minecraft.setScreen(parent);
        parent.reload();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (captured == null) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                onClose();
            } else {
                capture(false, event.key());
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (captured == null) {
            capture(true, event.button());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        if (captured == null) {
            graphics.centeredText(this.font, this.title, centerX, this.height / 2 - 20, WHITE);
            graphics.centeredText(this.font, Component.translatable("kse.capture.hint"),
                    centerX, this.height / 2, 0xFFAAAAAA);
        } else {
            graphics.centeredText(this.font,
                    Component.translatable("kse.capture.pressed", KeyNames.name(captured.mouse, captured.code)),
                    centerX, this.height / 2 - 20, 0xFFFFFF55);
            if (error != null) {
                graphics.centeredText(this.font, error, centerX, this.height / 2 + 70, 0xFFFF5555);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
