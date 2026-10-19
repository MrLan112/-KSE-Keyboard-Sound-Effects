package com.mrlan123.kse.gui;

import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
/**
 * Step 1: "Press any key or button" - the next key or mouse button is recorded.
 * Step 2: shows what was pressed with the buttons "Add" and "Try again".
 */
public class KeyCaptureScreen extends Screen {
    private final KeyEditorScreen parent;
    private KSEConfig.BoundKey captured = null;
    private Text error = null;

    public KeyCaptureScreen(KeyEditorScreen parent) {
        super(Text.translatable("kse.capture.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (captured == null) {
            return;
        }
        int centerX = this.width / 2;
        int y = this.height / 2 + 20;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.capture.add"), b -> confirmAdd())
                .dimensions(centerX - 105, y, 100, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.capture.repeat"), b -> {
                    captured = null;
                    error = null;
                    reload();
                })
                .dimensions(centerX + 5, y, 100, 20)
                .build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.capture.cancel"), b -> close())
                .dimensions(centerX - 50, y + 24, 100, 20)
                .build());
    }

    private void reload() {
        this.clearChildren();
        this.init();
    }

    private void capture(boolean mouse, int code) {
        captured = new KSEConfig.BoundKey(mouse, code);
        error = null;
        reload();
    }

    private void confirmAdd() {
        if (!KSEConfig.get().addKey(captured.mouse, captured.code)) {
            error = Text.translatable("kse.capture.already");
            return;
        }
        this.client.setScreen(parent);
        parent.reload();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (captured == null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                close();
            } else {
                capture(false, keyCode);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (captured == null) {
            capture(true, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        if (captured == null) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, this.height / 2 - 20, WHITE);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("kse.capture.hint"),
                    centerX, this.height / 2, 0xFFAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("kse.capture.pressed", KeyNames.name(captured.mouse, captured.code)),
                    centerX, this.height / 2 - 20, 0xFFFFFF55);
            if (error != null) {
                context.drawCenteredTextWithShadow(this.textRenderer, error, centerX, this.height / 2 + 70, 0xFFFF5555);
            }
        }
    }

    private static final int WHITE = 0xFFFFFFFF;

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
