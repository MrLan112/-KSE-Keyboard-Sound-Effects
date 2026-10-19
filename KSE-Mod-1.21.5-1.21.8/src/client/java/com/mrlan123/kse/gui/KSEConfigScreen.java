package com.mrlan123.kse.gui;

import com.mrlan123.kse.FileDialogs;
import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.config.SoundEntry;
import com.mrlan123.kse.sound.SoundPlayer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KSEConfigScreen extends ScrollListScreen {
    private static final int LIST_TOP = 112;
    private static final int BOTTOM_RESERVE = 36;

    private final Screen parent;
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private ButtonWidget editorButton;

    private record RowLabel(Text text, int x, int y) {
    }

    public KSEConfigScreen(Screen parent) {
        super(Text.translatable("kse.config.title"));
        this.parent = parent;
    }

    private static Text toggleLabel(String key, boolean value) {
        return Text.translatable(key)
                .append(": ")
                .append(Text.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    protected void init() {
        rowLabels.clear();
        KSEConfig cfg = KSEConfig.get();
        int centerX = this.width / 2;
        int left = centerX - 160;

        this.addDrawableChild(ButtonWidget.builder(
                        toggleLabel("kse.config.play_on_keyboard", cfg.playOnKeyboard), b -> {
                            KSEConfig.get().playOnKeyboard = !KSEConfig.get().playOnKeyboard;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.play_on_keyboard", KSEConfig.get().playOnKeyboard));
                        })
                .dimensions(left, 24, 158, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        toggleLabel("kse.config.play_on_mouse", cfg.playOnMouse), b -> {
                            KSEConfig.get().playOnMouse = !KSEConfig.get().playOnMouse;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.play_on_mouse", KSEConfig.get().playOnMouse));
                        })
                .dimensions(left + 162, 24, 158, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        toggleLabel("kse.config.specific_only", cfg.playOnlyOnSpecificKeys), b -> {
                            KSEConfig.get().playOnlyOnSpecificKeys = !KSEConfig.get().playOnlyOnSpecificKeys;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.specific_only", KSEConfig.get().playOnlyOnSpecificKeys));
                            editorButton.active = KSEConfig.get().playOnlyOnSpecificKeys;
                        })
                .dimensions(left, 48, 320, 20)
                .build());

        editorButton = ButtonWidget.builder(Text.translatable("kse.config.key_editor"),
                        b -> this.client.setScreen(new KeyEditorScreen(this)))
                .dimensions(left, 72, 158, 20)
                .build();
        editorButton.active = cfg.playOnlyOnSpecificKeys;
        this.addDrawableChild(editorButton);

        this.addDrawableChild(new VolumeSlider(left + 162, 72, 158, 20, cfg.volume));

        List<SoundEntry> all = SoundPlayer.getAllSounds();
        int rowsFit = Math.max(2, (this.height - LIST_TOP - BOTTOM_RESERVE) / ROW_HEIGHT);
        setupList(left, LIST_TOP, 308, rowsFit, all.size());

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= all.size()) {
                break;
            }
            SoundEntry entry = all.get(index);
            int y = LIST_TOP + i * ROW_HEIGHT;

            boolean isSelected = entry.id.equals(cfg.selectedSoundId);
            String name = entry.builtin
                    ? Text.translatable(entry.displayNameKey).getString()
                    : entry.customName;
            rowLabels.add(new RowLabel(fit(name, 142), left + 4, y + 6));

            Text buttonLabel = isSelected
                    ? Text.translatable("kse.config.selected")
                    : Text.translatable("kse.config.select");

            ButtonWidget selectButton = ButtonWidget.builder(buttonLabel, b -> {
                        KSEConfig.get().selectedSoundId = entry.id;
                        KSEConfig.save();
                        SoundPlayer.play(entry, KSEConfig.get().volume);
                        this.reload();
                    })
                    .dimensions(left + 150, y, 76, 20)
                    .build();
            selectButton.active = !isSelected;
            this.addDrawableChild(selectButton);

            // Preview: listen to the sound without selecting it.
            this.addDrawableChild(ButtonWidget.builder(Text.literal("▶"),
                            b -> SoundPlayer.play(entry, KSEConfig.get().volume))
                    .dimensions(left + 230, y, 22, 20)
                    .build());

            if (!entry.builtin) {
                this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.config.delete"), b -> {
                            KSEConfig.get().removeCustomSound(entry.id);
                            this.reload();
                        })
                        .dimensions(left + 256, y, 22, 20)
                        .build());
            }
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.config.add_sound"), b -> openFileChooser())
                .dimensions(left, this.height - 28, 158, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(left + 162, this.height - 28, 158, 20)
                .build());
    }

    private void openFileChooser() {
        MinecraftClient mc = MinecraftClient.getInstance();
        new Thread(() -> {
            FileDialogs.Result result = FileDialogs.pickAudioFile();
            switch (result.status()) {
                case OK -> {
                    final Path chosen = result.path();
                    mc.execute(() -> mc.setScreen(new AddSoundNameScreen(this, chosen)));
                }
                // No system dialog available: let the player type the file path in-game.
                case UNAVAILABLE -> mc.execute(() -> mc.setScreen(new AddSoundNameScreen(this, null)));
                default -> {
                }
            }
        }, "kse-file-chooser").start();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // The background (blur) is drawn by the game / by super exactly once.
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, WHITE);
        String selected = KSEConfig.get().selectedSoundId;
        if (selected == null || selected.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("kse.config.no_sound_hint"),
                    this.width / 2, 97, 0xFFFFFF55);
        }
        for (RowLabel row : rowLabels) {
            drawLabel(context, row.text(), row.x(), row.y());
        }
        drawScrollbar(context);
    }

    @Override
    public void close() {
        KSEConfig.save();
        this.client.setScreen(parent);
    }

    /** Volume slider; the value is saved at most every quarter second while dragging and on close. */
    private static class VolumeSlider extends SliderWidget {
        private static long lastSave = 0;

        VolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Text.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("kse.config.volume").append(": " + Math.round(this.value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            KSEConfig.get().volume = (float) this.value;
            long now = System.currentTimeMillis();
            if (now - lastSave > 250) {
                lastSave = now;
                KSEConfig.save();
            }
        }
    }
}
