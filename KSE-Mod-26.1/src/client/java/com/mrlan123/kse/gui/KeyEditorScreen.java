package com.mrlan123.kse.gui;

import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Lists the keys / mouse buttons that trigger a sound in "specific keys only" mode. */
public class KeyEditorScreen extends ScrollListScreen {
    private static final int LIST_TOP = 56;
    private static final int BOTTOM_RESERVE = 36;

    private final Screen parent;
    private final List<RowLabel> rowLabels = new ArrayList<>();

    private record RowLabel(Component text, int x, int y) {
    }

    public KeyEditorScreen(Screen parent) {
        super(Component.translatable("kse.keys.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowLabels.clear();
        int left = this.width / 2 - 160;

        this.addRenderableWidget(Button.builder(Component.translatable("kse.keys.add"),
                        b -> this.minecraft.setScreen(new KeyCaptureScreen(this)))
                .bounds(left, 28, 320, 20)
                .build());

        List<KSEConfig.BoundKey> keys = new ArrayList<>(KSEConfig.get().specificKeys);
        int rowsFit = Math.max(2, (this.height - LIST_TOP - BOTTOM_RESERVE) / ROW_HEIGHT);
        setupList(left, LIST_TOP, 308, rowsFit, keys.size());

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollOffset + i;
            if (index >= keys.size()) {
                break;
            }
            KSEConfig.BoundKey key = keys.get(index);
            int y = LIST_TOP + i * ROW_HEIGHT;
            rowLabels.add(new RowLabel(fit(KeyNames.name(key.mouse, key.code).getString(), 200), left + 4, y + 6));
            this.addRenderableWidget(Button.builder(Component.translatable("kse.keys.remove"), b -> {
                        KSEConfig.get().removeKey(key);
                        this.reload();
                    })
                    .bounds(left + 226, y, 80, 20)
                    .build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title, this.width / 2, 10, WHITE);
        if (totalRows == 0) {
            graphics.centeredText(this.font, Component.translatable("kse.keys.empty"),
                    this.width / 2, LIST_TOP + 8, 0xFFAAAAAA);
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
}
