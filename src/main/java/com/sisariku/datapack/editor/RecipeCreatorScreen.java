package com.sisariku.datapack.editor;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

public class RecipeCreatorScreen extends ModularUIScreen {

    private static File sDatapackDir;
    private static String sMode = "crafting";
    private static Item[][] sGrid = new Item[3][3];
    private static Item sInput, sOutput, sCraftResult;
    private static int sOutputCount = 1;
    private static boolean sIsShaped = true;
    private static String sSelectedDp = "";
    private static net.minecraft.client.gui.screen.Screen sPrevScreen;

    private static UIElement sRoot;
    private static Consumer<Item> sPickCallback;
    private static String sActiveSlot = "";
    private static int sItemScroll = 0;

    static final String[] MODES = {"crafting","smelting","blasting","smoking","campfire"};
    static final String[] LABELS = {"🔨 工作台","🔥 熔炉","💥 高炉","🚬 烟熏炉","🏕 营火"};

    public RecipeCreatorScreen(File datapackDir) {
        super(buildUIImpl(datapackDir), Text.literal("CialloMine 配方创建器"));
        sPrevScreen = MinecraftClient.getInstance().currentScreen;
    }

    public RecipeCreatorScreen(File datapackDir, String mode, Item[][] grid, Item input, Item output, Item craftResult, int count) {
        super(buildUIImpl(datapackDir), Text.literal("CialloMine 配方创建器"));
        sMode = mode; sGrid = grid; sInput = input; sOutput = output; sCraftResult = craftResult; sOutputCount = count;
    }

    private static ModularUI buildUIImpl(File datapackDir) {
        sDatapackDir = datapackDir;
        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100)
                .flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.COLUMN));

        // === 顶部栏 ===
        var topBar = new UIElement();
        topBar.layout(l -> l.widthPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW).paddingAll(4));
        topBar.style(s -> s.backgroundTexture(Sprites.BORDER_DARK));
        for (int i = 0; i < MODES.length; i++) {
            final String m = MODES[i];
            var btn = new Button().setText(sMode.equals(m) ? "§e§l" + LABELS[i] + "§r" : LABELS[i]);
            btn.setOnClick(e -> switchMode(m));
            topBar.addChildren(btn);
        }
        var closeBtn = new Button().setText("✕ 关闭");
        closeBtn.setOnClick(e -> closeScreen());
        topBar.addChildren(closeBtn);
        root.addChildren(topBar);

        // === 中间：左配方 + 右选择器 ===
        var middle = new UIElement();
        middle.layout(l -> l.widthPercent(100).flex(1).minHeight(0)
                .flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));

        // 左：配方区
        var leftPanel = new UIElement();
        leftPanel.layout(l -> l.widthPercent(55).heightPercent(100).minHeight(0)
                .justifyContent(org.appliedenergistics.yoga.YogaJustify.CENTER)
                .alignItems(org.appliedenergistics.yoga.YogaAlign.CENTER));
        if (sMode.equals("crafting")) buildCrafting(leftPanel);
        else buildFurnace(leftPanel);
        middle.addChildren(leftPanel);

        // 右：物品选择器
        var rightPanel = new UIElement();
        rightPanel.layout(l -> l.widthPercent(45).heightPercent(100).minHeight(0).paddingAll(4));
        buildItemPicker(rightPanel);
        middle.addChildren(rightPanel);

        root.addChildren(middle);

        // === 底部 ===
        var bottom = new UIElement();
        bottom.layout(l -> l.widthPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW).paddingAll(4));
        bottom.style(s -> s.backgroundTexture(Sprites.BORDER_DARK));
        File[] dpDirs = sDatapackDir.listFiles(File::isDirectory);
        List<String> dpNames = new ArrayList<>();
        if (dpDirs != null) for (File d : dpDirs) dpNames.add(d.getName());
        if (sSelectedDp.isEmpty() && !dpNames.isEmpty()) sSelectedDp = dpNames.get(0);
        final int[] dpIdx = {dpNames.indexOf(sSelectedDp)};
        if (dpIdx[0] < 0) { dpIdx[0] = 0; if (!dpNames.isEmpty()) sSelectedDp = dpNames.get(0); }
        var dpText = new TextField(); dpText.setText(sSelectedDp); dpText.layout(l -> l.width(100));
        var prevBtn = new Button().setText("◀"); var nextBtn = new Button().setText("▶");
        Runnable updateDp = () -> { if (!dpNames.isEmpty()) { sSelectedDp = dpNames.get(dpIdx[0]); dpText.setText(sSelectedDp); } };
        prevBtn.setOnClick(e -> { if (!dpNames.isEmpty()) { dpIdx[0] = (dpIdx[0] - 1 + dpNames.size()) % dpNames.size(); updateDp.run(); } });
        nextBtn.setOnClick(e -> { if (!dpNames.isEmpty()) { dpIdx[0] = (dpIdx[0] + 1) % dpNames.size(); updateDp.run(); } });
        var nsIn = new TextField(); nsIn.setText("mydatapack"); nsIn.layout(l -> l.width(100));
        var nameIn = new TextField(); nameIn.setText("my_recipe"); nameIn.layout(l -> l.width(100));
        var countIn = new TextField(); countIn.setText(String.valueOf(sOutputCount)); countIn.layout(l -> l.width(40));
        var genBtn = new Button().setText("✨ 生成");
        genBtn.setOnClick(e -> {
            try { sOutputCount = Integer.parseInt(countIn.getValue().trim()); } catch (NumberFormatException ex) {}
            String dp = dpText.getValue().trim(), ns = nsIn.getValue().trim(), name = nameIn.getValue().trim();
            if (dp.isEmpty() || ns.isEmpty() || name.isEmpty()) return;
            try { generate(dp, ns, name); overlay("§a" + name + " 已生成"); }
            catch (IOException ex) { overlay("§c失败: " + ex.getMessage()); }
        });
        bottom.addChildren(new Label().setText(Text.literal("§7数据包:")), prevBtn, dpText, nextBtn,
            new Label().setText(Text.literal(" NS:")), nsIn,
            new Label().setText(Text.literal(" 名称:")), nameIn,
            new Label().setText(Text.literal(" 数量:")), countIn, genBtn);
        root.addChildren(bottom);

        sRoot = root;
        return ModularUI.of(UI.of(root, List.of(), s -> s));
    }

    // === 右侧物品选择器面板 ===
    private static void buildItemPicker(UIElement parent) {
        var col = new UIElement();
        col.layout(l -> l.widthPercent(100).heightPercent(100).minHeight(0)
                .flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.COLUMN));
        col.style(s -> s.backgroundTexture(Sprites.RECT_DARK));

        col.addChildren(new Label().setText(Text.literal("物品选择")).layout(l -> l.paddingBottom(2)));

        // 搜索栏 + 按钮同行
        var searchRow = new UIElement();
        searchRow.layout(l -> l.widthPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
        var search = new TextField(); search.layout(l -> l.flex(1));
        var doBtn = new Button().setText("搜索"); doBtn.layout(l -> l.width(40));
        searchRow.addChildren(search, doBtn);
        col.addChildren(searchRow);

        var list = new UIElement();
        list.layout(l -> l.widthPercent(100).flex(1).minHeight(0));
        col.addChildren(list);

        List<Item> all = new ArrayList<>(); Registries.ITEM.forEach(all::add);
        all.sort(Comparator.comparing(i -> Registries.ITEM.getId(i).toString()));

        // 使用数组持有 Runnable 以解决前向引用
        Runnable[] refreshHolder = new Runnable[1];

        // 滚轮滚动
        list.addEventListener(UIEvents.MOUSE_WHEEL, ev -> {
            sItemScroll += (int) ev.deltaY * 2;
            if (sItemScroll < 0) sItemScroll = 0;
            if (refreshHolder[0] != null) refreshHolder[0].run();
        });

        Runnable refresh = () -> {
            new ArrayList<>(list.getChildren()).forEach(c -> list.removeChild(c));
            String q = search.getValue().toLowerCase();
            int idx = 0, rendered = 0, maxVis = 25;
            for (Item it : all) {
                String id = Registries.ITEM.getId(it).toString();
                if (id.contains("ldlib") || id.contains(":test")) continue;
                String name = it.getName().getString();
                if (!name.toLowerCase().contains(q) && !id.toLowerCase().contains(q)) continue;
                if (idx++ < sItemScroll) continue;
                if (rendered++ >= maxVis) break;
                var b = new Button().setText("§7" + name + "§r");
                b.layout(l -> l.widthPercent(100).paddingTop(1).paddingBottom(1));
                b.setOnClick(e -> {
                    if (sPickCallback != null) { sPickCallback.accept(it); sPickCallback = null; }
                    refreshAfterPick();
                });
                list.addChildren(b);
            }
        };
        refreshHolder[0] = refresh;
        doBtn.setOnClick(e -> { sItemScroll = 0; refresh.run(); });
        refresh.run();

        parent.addChildren(col);
    }

    private static void refreshAfterPick() {
        sActiveSlot = "";
        sItemScroll = 0;
        var s = new RecipeCreatorScreen(sDatapackDir, sMode, sGrid, sInput, sOutput, sCraftResult, sOutputCount);
        MinecraftClient.getInstance().setScreen(s);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (sRoot != null) renderItemsInTree(ctx, sRoot, 0, 0);
    }

    private void renderItemsInTree(DrawContext ctx, UIElement el, float px, float py) {
        float x = px + el.getLayoutX();
        float y = py + el.getLayoutY();
        String id = el.getId();
        if (id != null && !id.isEmpty()) {
            var parsed = Identifier.tryParse(id);
            if (parsed != null && Registries.ITEM.containsId(parsed)) {
                var stack = new net.minecraft.item.ItemStack(Registries.ITEM.get(parsed));
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 0, y + 0, 0);
                ctx.getMatrices().scale(2, 2, 1);
                ctx.drawItem(stack, 0, 0);
                ctx.getMatrices().pop();
            }
        }
        for (var child : el.getChildren()) {
            renderItemsInTree(ctx, child, x, y);
        }
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(sPrevScreen); }

    // === 模式切换 ===
    private static void switchMode(String m) {
        sMode = m;
        var s = new RecipeCreatorScreen(sDatapackDir, sMode, sGrid, sInput, sOutput, sCraftResult, sOutputCount);
        MinecraftClient.getInstance().setScreen(s);
    }

    // === 配方布局 ===
    private static void buildCrafting(UIElement parent) {
        var col = new UIElement();
        col.layout(l -> l.flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.COLUMN));

        var toggle = new UIElement();
        toggle.layout(l -> l.widthPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW).paddingBottom(4));
        var shapedBtn = new Button().setText(sIsShaped ? "§e§l有序§r" : "有序");
        var unshapedBtn = new Button().setText(sIsShaped ? "无序" : "§e§l无序§r");
        shapedBtn.setOnClick(e -> { if (!sIsShaped) { sIsShaped = true; refreshAfterPick(); } });
        unshapedBtn.setOnClick(e -> { if (sIsShaped) { sIsShaped = false; refreshAfterPick(); } });
        toggle.addChildren(shapedBtn, unshapedBtn);
        col.addChildren(toggle);

        var row = new UIElement();
        row.layout(l -> l.flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
        var container = new UIElement();
        container.layout(l -> l.width(120).paddingAll(2));
        container.style(s -> s.backgroundTexture(Sprites.RECT_DARK));
        for (int r = 0; r < 3; r++) {
            var gridRow = new UIElement();
            gridRow.layout(l -> l.widthPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
            for (int c = 0; c < 3; c++) {
                final int rr = r, cc = c;
                var slot = makeSlot(sGrid[rr][cc], sActiveSlot.equals("g"+rr+cc));
                slot.addEventListener(UIEvents.CLICK, ev -> {
                    if (ev.button == 1) { sGrid[rr][cc] = null; refreshAfterPick(); return; }
                    sActiveSlot = "g"+rr+cc; sPickCallback = item -> {
                    sGrid[rr][cc] = item; refreshAfterPick();
                };});
                gridRow.addChildren(slot);
            }
            container.addChildren(gridRow);
        }
        row.addChildren(container);

        var arrowCol = new UIElement();
        arrowCol.layout(l -> l.paddingLeft(12).paddingRight(12).justifyContent(org.appliedenergistics.yoga.YogaJustify.CENTER));
        arrowCol.addChildren(new Label().setText(Text.literal("→")));
        row.addChildren(arrowCol);

        var outCol = new UIElement();
        outCol.layout(l -> l.paddingTop(24));
        outCol.addChildren(new Label().setText(Text.literal("产物")));
        var outSlot = makeSlot(sCraftResult, sActiveSlot.equals("result"));
        outSlot.addEventListener(UIEvents.CLICK, ev -> {
        if (ev.button == 1) { sCraftResult = null; refreshAfterPick(); return; }
        sActiveSlot = "result"; sPickCallback = item -> {
            sCraftResult = item; refreshAfterPick();
        };});
        outCol.addChildren(outSlot);
        row.addChildren(outCol);
        col.addChildren(row);
        parent.addChildren(col);
    }

    private static void buildFurnace(UIElement parent) {
        var row = new UIElement();
        row.layout(l -> l.flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW).paddingAll(8));
        var inCol = new UIElement(); inCol.addChildren(new Label().setText(Text.literal("原料")));
        var inSlot = makeSlot(sInput, sActiveSlot.equals("input"));
        inSlot.addEventListener(UIEvents.CLICK, ev -> {
        if (ev.button == 1) { sInput = null; refreshAfterPick(); return; }
        sActiveSlot = "input"; sPickCallback = item -> { sInput = item; refreshAfterPick(); };});
        inCol.addChildren(inSlot); row.addChildren(inCol);

        var arrCol = new UIElement();
        arrCol.layout(l -> l.paddingLeft(20).paddingRight(20).paddingTop(18).justifyContent(org.appliedenergistics.yoga.YogaJustify.CENTER));
        arrCol.addChildren(new Label().setText(Text.literal("→")));
        row.addChildren(arrCol);

        var outCol = new UIElement(); outCol.addChildren(new Label().setText(Text.literal("产物")));
        var outSlot = makeSlot(sOutput, sActiveSlot.equals("output"));
        outSlot.addEventListener(UIEvents.CLICK, ev -> {
        if (ev.button == 1) { sOutput = null; refreshAfterPick(); return; }
        sActiveSlot = "output"; sPickCallback = item -> { sOutput = item; refreshAfterPick(); };});
        outCol.addChildren(outSlot); row.addChildren(outCol);
        parent.addChildren(row);
    }

    // === 格子 ===
    private static UIElement makeSlot(Item item, boolean active) {
        var slot = new UIElement();
        slot.layout(l -> l.width(36).height(36).marginAll(2));
        slot.style(s -> s.backgroundTexture(active ? Sprites.RECT_LIGHT : Sprites.RECT));
        slot.setId(item != null ? Registries.ITEM.getId(item).toString() : "");
        return slot;
    }

    // === 生成 ===
    private static void generate(String dpName, String ns, String name) throws IOException {
        File dpDir = new File(sDatapackDir, dpName);
        File dir = new File(dpDir, "data/" + ns + "/recipe"); dir.mkdirs();
        String json = sMode.equals("crafting") ? genCrafting() : genFurnace();
        Files.writeString(new File(dir, name + ".json").toPath(), json);
    }

    private static String genCrafting() {
        List<String> patternLines = new ArrayList<>();
        int minR = 3, maxR = -1, minC = 3, maxC = -1;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            if (sGrid[r][c] != null) { minR = Math.min(minR, r); maxR = Math.max(maxR, r); minC = Math.min(minC, c); maxC = Math.max(maxC, c); }
        if (maxR < 0) return "{}";
        Map<Item, Character> map = new LinkedHashMap<>();
        char[] next = {'A'};
        for (int r = minR; r <= maxR; r++) {
            StringBuilder line = new StringBuilder();
            for (int c = minC; c <= maxC; c++) {
                Item it = sGrid[r][c];
                if (it == null) { line.append(' '); continue; }
                line.append(map.computeIfAbsent(it, k -> next[0]++));
            }
            patternLines.add(line.toString().trim());
        }
        String type = sIsShaped ? "minecraft:crafting_shaped" : "minecraft:crafting_shapeless";
        StringBuilder sb = new StringBuilder("{\n  \"type\": \"" + type + "\",\n");
        if (sIsShaped) {
            sb.append("  \"pattern\": [\n");
            for (int i = 0; i < patternLines.size(); i++) {
                sb.append("    \"").append(patternLines.get(i)).append("\"");
                if (i < patternLines.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n  \"key\": {\n");
            List<Map.Entry<Item, Character>> entries = new ArrayList<>(map.entrySet());
            for (int i = 0; i < entries.size(); i++) {
                var e = entries.get(i);
                sb.append("    \"").append(e.getValue()).append("\": { \"item\": \"").append(Registries.ITEM.getId(e.getKey())).append("\" }");
                if (i < entries.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  },\n");
        } else {
            List<Item> ingrItems = new ArrayList<>();
            for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
                if (sGrid[r][c] != null) ingrItems.add(sGrid[r][c]);
            sb.append("  \"ingredients\": [\n");
            for (int i = 0; i < ingrItems.size(); i++) {
                sb.append("    { \"item\": \"").append(Registries.ITEM.getId(ingrItems.get(i))).append("\" }");
                if (i < ingrItems.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ],\n");
        }
        Item result = sCraftResult != null ? sCraftResult : (!map.isEmpty() ? map.keySet().iterator().next() : null);
        String resultId = result != null ? Registries.ITEM.getId(result).toString() : "minecraft:stone";
        sb.append("  \"result\": { \"id\": \"").append(resultId)
                .append("\", \"count\": ").append(sOutputCount).append(" }\n}\n");
        return sb.toString();
    }

    private static String genFurnace() {
        String in = sInput != null ? Registries.ITEM.getId(sInput).toString() : "minecraft:stone";
        String out = sOutput != null ? Registries.ITEM.getId(sOutput).toString() : "minecraft:stone";
        String type = sMode.equals("blasting") ? "minecraft:blasting" : sMode.equals("smoking") ? "minecraft:smoking" : sMode.equals("campfire") ? "minecraft:campfire_cooking" : "minecraft:smelting";
        int time = sMode.equals("blasting") ? 100 : sMode.equals("campfire") ? 600 : 200;
        return "{\n  \"type\": \"" + type + "\",\n  \"ingredient\": { \"item\": \"" + in + "\" },\n  \"result\": { \"id\": \"" + out + "\", \"count\": " + sOutputCount + " },\n  \"experience\": 0.1,\n  \"cookingtime\": " + time + "\n}\n";
    }

    private static void closeScreen() { MinecraftClient.getInstance().setScreen(sPrevScreen); }
    private static void overlay(String msg) { var c = MinecraftClient.getInstance(); if (c.player != null) c.player.sendMessage(Text.literal("§8[CialloMine] " + msg), true); }
}
