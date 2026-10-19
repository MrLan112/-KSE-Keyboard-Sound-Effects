package com.mrlan123.kse.gui;

import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AddSoundNameScreen extends Screen {
    private static final int WHITE = 0xFFFFFFFF;
    private static final int RED = 0xFFFF5555;

    private final KSEConfigScreen parent;
    private final Path selectedFile;
    private TextFieldWidget nameField;
    private TextFieldWidget pathField;
    private Text errorMessage = null;

    /** @param selectedFile the chosen file, or null to let the player type the path */
    public AddSoundNameScreen(KSEConfigScreen parent, Path selectedFile) {
        super(Text.translatable("kse.config.add_sound.title"));
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
            pathField = new TextFieldWidget(this.textRenderer, centerX - 150, fieldY - 40, 300, 20,
                    Text.translatable("kse.config.add_sound.path"));
            pathField.setMaxLength(1024);
            this.addDrawableChild(pathField);
        }

        nameField = new TextFieldWidget(this.textRenderer, centerX - 100, fieldY, 200, 20,
                Text.translatable("kse.config.add_sound.name"));
        nameField.setMaxLength(32);
        if (selectedFile != null) {
            nameField.setText(stem(selectedFile));
        }
        this.addDrawableChild(nameField);
        this.setInitialFocus(pathField != null ? pathField : nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.config.confirm"), b -> confirm())
                .dimensions(centerX - 100, fieldY + 30, 95, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), b -> close())
                .dimensions(centerX + 5, fieldY + 30, 95, 20)
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
            String raw = pathField.getText().trim();
            if (raw.length() > 1 && raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1);
            }
            try {
                file = Path.of(raw);
            } catch (RuntimeException e) {
                file = null;
            }
            if (file == null || !Files.isRegularFile(file)) {
                errorMessage = Text.translatable("kse.config.add_sound.error_file");
                return;
            }
        }
        String name = nameField.getText().trim();
        if (name.isEmpty() && selectedFile == null) {
            name = stem(file);
        }
        if (name.isEmpty()) {
            errorMessage = Text.translatable("kse.config.add_sound.error_empty");
            return;
        }
        try {
            String id = KSEConfig.get().addCustomSound(name, file);
            KSEConfig.get().selectedSoundId = id;
            KSEConfig.save();
            this.client.setScreen(parent);
            parent.reload();
        } catch (IOException e) {
            errorMessage = Text.translatable("kse.config.add_sound.error_copy");
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50, WHITE);
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, errorMessage, this.width / 2, this.height / 2 + 70, RED);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
