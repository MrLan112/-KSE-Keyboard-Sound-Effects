package com.mrlan123.kse.gui;

import com.mrlan123.kse.config.KSEConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Lists the keys / mouse buttons that trigger a sound in "specific keys only" mode. */
public class KeyEditorScreen extends ScrollListScreen {
    private static final int LIST_TOP = 56;
    private static final int BOTTOM_RESERVE = 36;

    private final Screen parent;
    private final List<RowLabel> rowLabels = new ArrayList<>();

    private record RowLabel(Text text, int x, int y) {
    }

    public KeyEditorScreen(Screen parent) {
        super(Text.translatable("kse.keys.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rowLabels.clear();
        int left = this.width / 2 - 160;

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.keys.add"),
                        b -> this.client.setScreen(new KeyCaptureScreen(this)))
                .dimensions(left, 28, 320, 20)
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
            this.addDrawableChild(ButtonWidget.builder(Text.translatable("kse.keys.remove"), b -> {
                        KSEConfig.get().removeKey(key);
                        this.reload();
                    })
                    .dimensions(left + 226, y, 80, 20)
                    .build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, WHITE);
        if (totalRows == 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("kse.keys.empty"),
                    this.width / 2, LIST_TOP + 8, 0xFFAAAAAA);
        }
        for (RowLabel row : rowLabels) {
            context.drawTextWithShadow(this.textRenderer, row.text(), row.x(), row.y(), WHITE);
        }
        drawScrollbar(context);
    }

    @Override
    public void close() {
        KSEConfig.save();
        this.client.setScreen(parent);
    }
}
