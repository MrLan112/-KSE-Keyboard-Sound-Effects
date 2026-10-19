package com.mrlan123.kse.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base class for screens that show a list of fixed-height rows which can be
 * scrolled with the mouse wheel or by dragging a scrollbar (like a web page).
 * Subclasses call {@link #setupList} from init(), create the visible rows and
 * call {@link #drawScrollbar} at the end of extractRenderState().
 */
public abstract class ScrollListScreen extends Screen {
    protected static final int ROW_HEIGHT = 22;
    protected static final int BAR_WIDTH = 6;
    protected static final int WHITE = 0xFFFFFFFF;

    protected int scrollOffset = 0;
    protected int listLeft;
    protected int listTop;
    protected int listWidth;
    protected int visibleRows = 1;
    protected int totalRows = 0;
    private boolean draggingBar = false;

    protected ScrollListScreen(Component title) {
        super(title);
    }

    protected void setupList(int left, int top, int width, int rowsFit, int total) {
        this.listLeft = left;
        this.listTop = top;
        this.listWidth = width;
        this.visibleRows = Math.max(1, rowsFit);
        this.totalRows = total;
        this.scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset()));
    }

    protected int maxOffset() {
        return Math.max(0, totalRows - visibleRows);
    }

    /** Rebuilds all widgets, e.g. after the list or the scroll position changed. */
    public void reload() {
        this.clearWidgets();
        this.init();
    }

    protected void setScroll(int value) {
        int clamped = Math.max(0, Math.min(value, maxOffset()));
        if (clamped != scrollOffset) {
            scrollOffset = clamped;
            reload();
        }
    }

    private int barX() {
        return listLeft + listWidth + 4;
    }

    private int trackHeight() {
        return visibleRows * ROW_HEIGHT - 2;
    }

    private int thumbHeight() {
        return Math.max(16, trackHeight() * visibleRows / Math.max(1, totalRows));
    }

    private int thumbY() {
        int range = trackHeight() - thumbHeight();
        return listTop + (maxOffset() == 0 ? 0 : range * scrollOffset / maxOffset());
    }

    private boolean overList(double x, double y) {
        return x >= listLeft && x <= barX() + BAR_WIDTH + 2
                && y >= listTop && y <= listTop + visibleRows * ROW_HEIGHT;
    }

    private boolean overBar(double x, double y) {
        return totalRows > visibleRows
                && x >= barX() - 2 && x <= barX() + BAR_WIDTH + 2
                && y >= listTop && y <= listTop + trackHeight();
    }

    private void dragTo(double mouseY) {
        int range = Math.max(1, trackHeight() - thumbHeight());
        double rel = (mouseY - listTop - thumbHeight() / 2.0) / range;
        setScroll((int) Math.round(rel * maxOffset()));
    }

    protected void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (totalRows <= visibleRows) {
            return;
        }
        int x = barX();
        graphics.fill(x, listTop, x + BAR_WIDTH, listTop + trackHeight(), 0x80000000);
        graphics.fill(x, thumbY(), x + BAR_WIDTH, thumbY() + thumbHeight(), draggingBar ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (totalRows > visibleRows && scrollY != 0 && overList(mouseX, mouseY)) {
            setScroll(scrollOffset + (scrollY > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && overBar(event.x(), event.y())) {
            draggingBar = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingBar && event.button() == 0) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Shortens a label so it fits into the given pixel width. */
    protected Component fit(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) {
            return Component.literal(s);
        }
        int end = s.length();
        while (end > 0 && this.font.width(s.substring(0, end) + "...") > maxWidth) {
            end--;
        }
        return Component.literal(s.substring(0, end) + "...");
    }
}
