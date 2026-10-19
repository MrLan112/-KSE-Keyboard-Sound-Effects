package com.mrlan123.kse.gui;

import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AddSoundNameScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;

    private final KSEConfigScreen parent;
    private final Path selectedFile;
    private EditBox nameField;
    private EditBox pathField;
    private Component errorMessage = null;

    /** @param selectedFile the chosen file, or null to let the player type the path */
    public AddSoundNameScreen(KSEConfigScreen parent, Path selectedFile) {
        super(Component.translatable("kse.config.add_sound.title"));
        this.parent = parent;
        this.selectedFile = selectedFile;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int fieldY = this.height / 2 - 30;
        pathField = null;
        if (selectedFile == null) {
            fieldY += 15;
            pathField = new EditBox(this.font, centerX - 150, fieldY - 40, 300, 20,
                    Component.translatable("kse.config.add_sound.path"));
            pathField.setMaxLength(1024);
            this.addRenderableWidget(pathField);
        }

        nameField = new EditBox(this.font, centerX - 100, fieldY, 200, 20,
                Component.translatable("kse.config.add_sound.name"));
        nameField.setMaxLength(32);
        if (selectedFile != null) {
            nameField.setValue(stem(selectedFile));
        }
        this.addRenderableWidget(nameField);
        this.setInitialFocus(pathField != null ? pathField : nameField);

        this.addRenderableWidget(Button.builder(Component.translatable("kse.config.confirm"), b -> confirm())
                .bounds(centerX - 100, fieldY + 30, 95, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(centerX + 5, fieldY + 30, 95, 20)
                .build());
    }

    private static String stem(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private void confirm() {
        Path file = selectedFile;
        if (file == null) {
            String raw = pathField.getValue().trim();
            if (raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            try {
                file = Path.of(raw);
            } catch (RuntimeException e) {
                file = null;
            }
            if (file == null || !Files.isRegularFile(file)) {
                errorMessage = Component.translatable("kse.config.add_sound.error_file");
                return;
            }
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty() && selectedFile == null) {
            name = stem(file);
        }
        if (name.isEmpty()) {
            errorMessage = Component.translatable("kse.config.add_sound.error_empty");
            return;
        }
        try {
            String id = KSEConfig.get().addCustomSound(name, file);
            KSEConfig.get().selectedSoundId = id;
            KSEConfig.save();
            this.minecraft.gui.setScreen(parent);
            parent.reload();
        } catch (IOException e) {
            errorMessage = Component.translatable("kse.config.add_sound.error_copy");
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 50, WHITE);
        if (errorMessage != null) {
            graphics.centeredText(this.font, errorMessage, this.width / 2, this.height / 2 + 70, RED);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
