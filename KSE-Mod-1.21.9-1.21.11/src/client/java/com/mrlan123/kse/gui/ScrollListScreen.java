package com.mrlan123.kse.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.client.gui.Click;
/**
 * Base class for screens that show a list of fixed-height rows which can be
 * scrolled with the mouse wheel or by dragging a scrollbar (like a web page).
 * Subclasses call {@link #setupList} from init(), create the visible rows and
 * call {@link #drawScrollbar} at the end of render().
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

    protected ScrollListScreen(Text title) {
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
        this.clearChildren();
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

    protected void drawScrollbar(DrawContext context) {
        if (totalRows <= visibleRows) {
            return;
        }
        int x = barX();
        context.fill(x, listTop, x + BAR_WIDTH, listTop + trackHeight(), 0x80000000);
        context.fill(x, thumbY(), x + BAR_WIDTH, thumbY() + thumbHeight(), draggingBar ? 0xFFFFFFFF : 0xFFA0A0A0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (totalRows > visibleRows && verticalAmount != 0 && overList(mouseX, mouseY)) {
            setScroll(scrollOffset + (verticalAmount > 0 ? -1 : 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0 && overBar(click.x(), click.y())) {
            draggingBar = true;
            dragTo(click.y());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingBar) {
            dragTo(click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingBar && click.button() == 0) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(click);
    }
    /** Shortens a label so it fits into the given pixel width. */
    protected Text fit(String s, int maxWidth) {
        if (this.textRenderer.getWidth(s) <= maxWidth) {
            return Text.literal(s);
        }
        int end = s.length();
        while (end > 0 && this.textRenderer.getWidth(s.substring(0, end) + "...") > maxWidth) {
            end--;
        }
        return Text.literal(s.substring(0, end) + "...");
    }
}
