package com.mrlan123.kse.gui;

import com.mrlan123.kse.FileDialogs;
import com.mrlan123.kse.config.KSEConfig;
import com.mrlan123.kse.config.SoundEntry;
import com.mrlan123.kse.sound.SoundPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KSEConfigScreen extends ScrollListScreen {
    private static final int LIST_TOP = 112;
    private static final int BOTTOM_RESERVE = 36;

    private final Screen parent;
    private final List<RowLabel> rowLabels = new ArrayList<>();
    private Button editorButton;

    private record RowLabel(Component text, int x, int y) {
    }

    public KSEConfigScreen(Screen parent) {
        super(Component.translatable("kse.config.title"));
        this.parent = parent;
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key)
                .append(": ")
                .append(Component.translatable(value ? "options.on" : "options.off"));
    }

    @Override
    protected void init() {
        rowLabels.clear();
        KSEConfig cfg = KSEConfig.get();
        int centerX = this.width / 2;
        int left = centerX - 160;

        this.addRenderableWidget(Button.builder(
                        toggleLabel("kse.config.play_on_keyboard", cfg.playOnKeyboard), b -> {
                            KSEConfig.get().playOnKeyboard = !KSEConfig.get().playOnKeyboard;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.play_on_keyboard", KSEConfig.get().playOnKeyboard));
                        })
                .bounds(left, 24, 158, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        toggleLabel("kse.config.play_on_mouse", cfg.playOnMouse), b -> {
                            KSEConfig.get().playOnMouse = !KSEConfig.get().playOnMouse;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.play_on_mouse", KSEConfig.get().playOnMouse));
                        })
                .bounds(left + 162, 24, 158, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        toggleLabel("kse.config.specific_only", cfg.playOnlyOnSpecificKeys), b -> {
                            KSEConfig.get().playOnlyOnSpecificKeys = !KSEConfig.get().playOnlyOnSpecificKeys;
                            KSEConfig.save();
                            b.setMessage(toggleLabel("kse.config.specific_only", KSEConfig.get().playOnlyOnSpecificKeys));
                            editorButton.active = KSEConfig.get().playOnlyOnSpecificKeys;
                        })
                .bounds(left, 48, 320, 20)
                .build());

        editorButton = Button.builder(Component.translatable("kse.config.key_editor"),
                        b -> this.minecraft.setScreen(new KeyEditorScreen(this)))
                .bounds(left, 72, 158, 20)
                .build();
        editorButton.active = cfg.playOnlyOnSpecificKeys;
        this.addRenderableWidget(editorButton);

        this.addRenderableWidget(new VolumeSlider(left + 162, 72, 158, 20, cfg.volume));

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
                    ? Component.translatable(entry.displayNameKey).getString()
                    : entry.customName;
            rowLabels.add(new RowLabel(fit(name, 142), left + 4, y + 6));

            Component buttonLabel = isSelected
                    ? Component.translatable("kse.config.selected")
                    : Component.translatable("kse.config.select");

            Button selectButton = Button.builder(buttonLabel, b -> {
                        KSEConfig.get().selectedSoundId = entry.id;
                        KSEConfig.save();
                        SoundPlayer.play(entry, KSEConfig.get().volume);
                        this.reload();
                    })
                    .bounds(left + 150, y, 76, 20)
                    .build();
            selectButton.active = !isSelected;
            this.addRenderableWidget(selectButton);

            // Preview: listen to the sound without selecting it.
            this.addRenderableWidget(Button.builder(Component.literal("▶"),
                            b -> SoundPlayer.play(entry, KSEConfig.get().volume))
                    .bounds(left + 230, y, 22, 20)
                    .build());

            if (!entry.builtin) {
                this.addRenderableWidget(Button.builder(Component.translatable("kse.config.delete"), b -> {
                            KSEConfig.get().removeCustomSound(entry.id);
                            this.reload();
                        })
                        .bounds(left + 256, y, 22, 20)
                        .build());
            }
        }

        this.addRenderableWidget(Button.builder(Component.translatable("kse.config.add_sound"), b -> openFileChooser())
                .bounds(left, this.height - 28, 158, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left + 162, this.height - 28, 158, 20)
                .build());
    }

    private void openFileChooser() {
        Minecraft mc = Minecraft.getInstance();
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Background (blur) is extracted exactly once by the super call.
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, WHITE);
        String selected = KSEConfig.get().selectedSoundId;
        if (selected == null || selected.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("kse.config.no_sound_hint"),
                    this.width / 2, 97, 0xFFFFFF55);
        }
        for (RowLabel row : rowLabels) {
            graphics.text(this.font, row.text(), row.x(), row.y(), WHITE, true);
        }
        drawScrollbar(graphics);
    }

    @Override
    public void onClose() {
        KSEConfig.save();
        this.minecraft.setScreen(parent);
    }

    /** Volume slider; the value is saved at most every quarter second while dragging and on close. */
    private static class VolumeSlider extends AbstractSliderButton {
        private static long lastSave = 0;

        VolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Component.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("kse.config.volume").append(": " + Math.round(this.value * 100) + "%"));
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
