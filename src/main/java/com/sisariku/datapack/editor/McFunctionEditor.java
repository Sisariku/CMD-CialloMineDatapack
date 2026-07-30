package com.sisariku.datapack.editor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** 原版 TextRenderer 多行编辑器 + mcfunction 语法高亮 */
public class McFunctionEditor {

    private String[] lines = {""};
    private int cursorLine, cursorCol;
    private boolean focused;
    private Consumer<String[]> changeListener;

    // 渲染区域（由 Screen 设置）
    public float x, y, w, h;

    // 高亮
    private static final Pattern[] PATTERNS = {
        Pattern.compile("#.*"),           // 注释 灰
        Pattern.compile("@[parse]"),      // 选择器 紫
        Pattern.compile("\"[^\"]*\""),    // 字符串 绿
        Pattern.compile("\\b\\d+(\\.\\d+)?\\b"), // 数字 青
        Pattern.compile("\\{[^}]*\\}"),    // NBT 黄
        Pattern.compile("[\\[\\]]"),       // 括号 浅灰
    };
    private static final int[] COLORS = {0xFF808080, 0xFFCF8EFF, 0xFF6AAB73, 0xFF2AACB8, 0xFFFFFF55, 0xFFAAAAAA};

    public String[] getLines() { return lines; }
    public void setLines(String[] l) { this.lines = l != null ? l : new String[]{""}; cursorLine = 0; cursorCol = 0; }
    public String getValue() { return String.join("\n", lines); }
    public void setValue(String[] l) { setLines(l); }
    public int getCursorLine() { return cursorLine; }
    public int getCursorCol() { return cursorCol; }
    public void setCursor(int l, int c) { cursorLine = clamp(0, l, lines.length - 1); cursorCol = clamp(0, c, lines[cursorLine].length()); }
    public void setChangeListener(Consumer<String[]> l) { this.changeListener = l; }
    public boolean isFocused() { return focused; }
    public void setFocused(boolean f) { this.focused = f; }

    public void render(DrawContext ctx) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int lineH = tr.fontHeight + 2;
        int maxVis = Math.max(1, (int)(h / lineH));

        // 背景
        ctx.fill((int)x, (int)y, (int)(x + w), (int)(y + h), 0xAA1E1E1E);

        for (int i = 0; i < Math.min(lines.length, maxVis); i++) {
            String line = lines[i];
            float lx = x + 4, ly = y + i * lineH + 1;
            // 语法高亮
            int[] segColors = highlight(line);
            int pos = 0;
            for (int si = 0; si < segColors.length; si++) {
                String seg = line.substring(pos, si + 1);
                ctx.drawText(tr, seg, (int)lx, (int)ly, segColors[si], false);
                lx += tr.getWidth(seg);
                pos = si + 1;
            }
        }
        // 光标
        if (focused) {
            String cline = cursorLine < lines.length ? lines[cursorLine] : "";
            int cc = Math.min(cursorCol, cline.length());
            float cx = x + 4 + tr.getWidth(cline.substring(0, cc));
            float cy = y + cursorLine * lineH + 1;
            ctx.fill((int)cx, (int)cy, (int)cx + 1, (int)(cy + tr.fontHeight), 0xFFFFFFFF);
        }
    }

    private int[] highlight(String line) {
        int[] c = new int[line.length()];
        Arrays.fill(c, 0xFFCCCCCC);
        for (int pi = 0; pi < PATTERNS.length; pi++) {
            Matcher m = PATTERNS[pi].matcher(line);
            while (m.find()) {
                int col = COLORS[Math.min(pi, COLORS.length - 1)];
                for (int i = m.start(); i < m.end(); i++) c[i] = col;
            }
        }
        return c;
    }

    // === 输入处理 ===
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focused) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (cursorCol > 0) {
                String l = lines[cursorLine];
                lines[cursorLine] = l.substring(0, cursorCol - 1) + l.substring(cursorCol);
                cursorCol--;
            } else if (cursorLine > 0) {
                int removedLen = lines[cursorLine].length(); // 保存移除前行长度
                lines[cursorLine - 1] += lines[cursorLine];
                lines = removeLine(lines, cursorLine);
                cursorLine--;
                cursorCol = lines[cursorLine].length() - removedLen;
            }
            notifyChange(); return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            if (cursorCol < lines[cursorLine].length()) {
                lines[cursorLine] = lines[cursorLine].substring(0, cursorCol) + lines[cursorLine].substring(cursorCol + 1);
            } else if (cursorLine < lines.length - 1) {
                lines[cursorLine] += lines[cursorLine + 1];
                lines = removeLine(lines, cursorLine + 1);
            }
            notifyChange(); return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            String rest = lines[cursorLine].substring(cursorCol);
            lines[cursorLine] = lines[cursorLine].substring(0, cursorCol);
            lines = insertLine(lines, cursorLine + 1, rest);
            cursorLine++; cursorCol = 0;
            notifyChange(); return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) { if (cursorCol > 0) cursorCol--; return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) { if (cursorCol < lines[cursorLine].length()) cursorCol++; return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP && cursorLine > 0) { cursorLine--; cursorCol = Math.min(cursorCol, lines[cursorLine].length()); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN && cursorLine < lines.length - 1) { cursorLine++; cursorCol = Math.min(cursorCol, lines[cursorLine].length()); return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) { cursorCol = 0; return true; }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) { cursorCol = lines[cursorLine].length(); return true; }
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        if (!focused || chr == 0) return false;
        String c = String.valueOf(chr);
        lines[cursorLine] = lines[cursorLine].substring(0, cursorCol) + c + lines[cursorLine].substring(cursorCol);
        cursorCol += c.length();
        notifyChange(); return true;
    }

    public void mouseClicked(double mx, double my, int button) {
        if (mx < x || mx > x + w || my < y || my > y + h) { focused = false; return; }
        focused = true;
        var tr = MinecraftClient.getInstance().textRenderer;
        int lineH = tr.fontHeight + 2;
        cursorLine = Math.min((int)((my - y) / lineH), lines.length - 1);
        if (cursorLine < 0) cursorLine = 0;
        String cl = lines[cursorLine];
        float cx = (float)(mx - x - 4);
        cursorCol = cl.length();
        for (int i = 0; i < cl.length(); i++) {
            if (tr.getWidth(cl.substring(0, i + 1)) > cx) { cursorCol = i; break; }
        }
    }

    private void notifyChange() { if (changeListener != null) changeListener.accept(lines); }

    private static String[] removeLine(String[] arr, int idx) {
        String[] n = new String[arr.length - 1];
        System.arraycopy(arr, 0, n, 0, idx);
        if (idx < arr.length - 1) System.arraycopy(arr, idx + 1, n, idx, arr.length - idx - 1);
        return n;
    }

    private static String[] insertLine(String[] arr, int idx, String line) {
        String[] n = new String[arr.length + 1];
        System.arraycopy(arr, 0, n, 0, idx);
        n[idx] = line;
        System.arraycopy(arr, idx, n, idx + 1, arr.length - idx);
        return n;
    }

    private static int clamp(int min, int v, int max) { return Math.max(min, Math.min(v, max)); }
}
