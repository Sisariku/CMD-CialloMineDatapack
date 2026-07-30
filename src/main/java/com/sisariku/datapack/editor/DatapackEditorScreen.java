package com.sisariku.datapack.editor;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class DatapackEditorScreen extends ModularUIScreen {

    private static TreeList<TreeNode<String, File>> cFileTree;
    private static Button cSaveBtn, cRecipeBtn;
    private static Label cStatusLabel;

    private TreeList<TreeNode<String, File>> fileTree;
    private Button saveBtn, recipeBtn;
    private Label statusLabel;
    private McFunctionEditor editor;

    private File currentFile, datapackRoot;
    private boolean dirty;
    private static File clipboardFile;
    private TreeNode<String, File> contextNode;
    private File selectedFile;
    private boolean ctxFromNode; // 防止冒泡

    private final TreeNode<String, File> rootNode = new TreeNode<>("datapacks");

    private List<String> completions = List.of();
    private int completionIdx;
    private boolean completing;

    public DatapackEditorScreen() { this(null); }
    public DatapackEditorScreen(File root) {
        super(buildUIImpl(), Text.literal("CialloMine 数据包编辑器"));
        this.fileTree = cFileTree; this.saveBtn = cSaveBtn; this.recipeBtn = cRecipeBtn; this.statusLabel = cStatusLabel;
        this.datapackRoot = root;
        cFileTree = null; cSaveBtn = null; cRecipeBtn = null; cStatusLabel = null;
    }

    private static ModularUI buildUIImpl() {
        var scroller = new ScrollerView();
        scroller.layout(l -> l.flex(1).minHeight(0));

        var fileTree = new TreeList<TreeNode<String, File>>(new TreeNode<>("_temp"));
        fileTree.setNodeUISupplier(TreeList.textTemplate(n -> Text.literal(lastSeg(n.getKey()))));
        fileTree.layout(l -> l.widthPercent(100));
        scroller.addScrollViewChild(fileTree);

        var saveBtn = new Button(); saveBtn.setText("💾 保存");
        var recipeBtn = new Button(); recipeBtn.setText("📦 新增配方");
        var statusLabel = new Label();

        cFileTree = fileTree; cSaveBtn = saveBtn; cRecipeBtn = recipeBtn; cStatusLabel = statusLabel;

        var root = new UIElement();
        root.layout(l -> l.widthPercent(100).heightPercent(100).flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
        var left = new UIElement();
        left.layout(l -> { l.widthPercent(22); l.heightPercent(100).minHeight(0); });
        left.addChildren(new Label().setText(Text.literal("文件浏览")).layout(l -> l.paddingBottom(2)),
            scroller, statusLabel.layout(l -> l.paddingTop(2).paddingBottom(2)), recipeBtn, saveBtn);
        var right = new UIElement();
        right.layout(l -> { l.widthPercent(78); l.heightPercent(100).minHeight(0); });
        root.addChildren(left, right);
        return ModularUI.of(UI.of(root, List.of(), s -> s));
    }

    private static String lastSeg(String k) { int i = Math.max(k.lastIndexOf('/'), k.lastIndexOf('\\')); return i >= 0 ? k.substring(i+1) : k; }

    private static void scanDir(File dir, TreeNode<String, File> p) {
        File[] kids = dir.listFiles(); if (kids == null) return;
        Arrays.sort(kids, Comparator.comparing(File::getName));
        for (File f : kids) { if (f.getName().startsWith(".")) continue; p.addContent(f.getAbsolutePath(), f); if (f.isDirectory()) scanDir(f, (TreeNode<String, File>)p.getChildren().get(p.getChildren().size()-1)); }
    }

    @Override
    public void init() {
        super.init();
        this.editor = new McFunctionEditor();
        fileTree.setRoot(rootNode);
        fileTree.setDoubleClickToExpand(true);
        fileTree.setOnDoubleClickNode(n -> { File f = n.getContent(); if (f != null && f.isFile()) openFile(f); });
        fileTree.setOnNodeUICreated((n, el) -> el.addEventListener(UIEvents.CLICK, ev -> {
            if (ev.button == 1) { contextNode = n; ctxFromNode = true; showContextMenu(); return; }
            if (ev.button == 0) { selectedFile = n.getContent(); }
        }));
        // 右键空白区域 = 右键根节点（只在节点没被点击时）
        fileTree.addEventListener(UIEvents.CLICK, ev -> { if (ev.button == 1 && !ctxFromNode) { contextNode = rootNode; showContextMenu(); } ctxFromNode = false; });
        if (datapackRoot != null) { scanDatapack(datapackRoot); fileTree.expandNode(rootNode); }
        saveBtn.setOnClick(e -> saveFile());
        if (recipeBtn != null) recipeBtn.setOnClick(e -> openRecipeCreator(datapackRoot));
        editor.setChangeListener(lines -> { dirty = true; autoComplete(); });
    }

    private void scanDatapack(File root) {
        if (root == null || !root.isDirectory()) return; this.datapackRoot = root;
        rootNode.getChildren().clear(); scanDir(root, rootNode);
        fileTree.setRoot(rootNode); fileTree.reloadList();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        float lw = width * 0.22f;
        editor.x = lw + 4; editor.y = 4; editor.w = width - lw - 8; editor.h = height - 4;
        editor.render(ctx);
        renderCompletions(ctx);
        // 右键菜单浮层
        if (ctxOpen && ctxItems != null) {
            int pad = 4, lineH = client.textRenderer.fontHeight + 2;
            int bw = 110, bh = ctxItems.length * lineH + pad * 2;
            ctx.fill(ctxX, ctxY, ctxX + bw, ctxY + bh, 0xDD1E1E1E);
            ctx.drawBorder(ctxX, ctxY, bw, bh, 0xFFFFFFFF);
            for (int i = 0; i < ctxItems.length; i++) {
                int color = (my >= ctxY + pad + i * lineH && my <= ctxY + pad + (i+1) * lineH && mx >= ctxX && mx <= ctxX + bw) ? 0xFFFFFF00 : 0xFFCCCCCC;
                ctx.drawText(client.textRenderer, ctxItems[i], ctxX + pad, ctxY + pad + i * lineH, color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 右键菜单点击
        if (ctxOpen && ctxItems != null) {
            int pad = 4, lineH = client.textRenderer.fontHeight + 2;
            int bw = 110;
            if (mx >= ctxX && mx <= ctxX + bw && my >= ctxY + pad && my <= ctxY + pad + ctxItems.length * lineH) {
                int idx = (int)((my - ctxY - pad) / lineH);
                handleCtxClick(idx);
                return true;
            }
        }
        closeCtx(); // 点击其他地方关闭
        if (mx > width * 0.22) { editor.mouseClicked(mx, my, btn); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean charTyped(char c, int m) { if (editor.charTyped(c, m)) return true; return super.charTyped(c, m); }
    @Override public boolean keyPressed(int k, int s, int m) {
        if (editor.isFocused()) {
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { editor.setFocused(false); if (dirty && currentFile != null) { showExitDialog(); return true; } client.setScreen(null); return true; }
            if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_S && (m & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) { saveFile(); return true; }
            if (!completions.isEmpty()) {
                if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) { completionIdx = (completionIdx+1)%completions.size(); return true; }
                if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) { acceptCompletion(); return true; }
                if (k == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) { acceptCompletion(); editor.charTyped(' ', 0); return true; }
            }
            if (editor.keyPressed(k, s, m)) return true;
        }
        return super.keyPressed(k, s, m);
    }

    // === 文件操作 ===
    private void openFile(File f) {
        if (f == null || !f.isFile()) return;
        try { currentFile = f; dirty = false; editor.setLines(Files.readString(f.toPath()).split("\n", -1)); updateStatus(); }
        catch (IOException e) { showOverlay("§c无法读取"); }
    }
    private void saveFile() {
        if (currentFile == null) return;
        try { Files.writeString(currentFile.toPath(), String.join("\n", editor.getLines())); dirty = false; updateStatus(); showOverlay("§a已保存"); }
        catch (IOException e) { showOverlay("§c保存失败"); }
    }
    private void updateStatus() {
        if (currentFile == null) statusLabel.setText(Text.literal(""));
        else if (dirty) statusLabel.setText(Text.literal("● "+currentFile.getName()+" (未保存)"));
        else statusLabel.setText(Text.literal(currentFile.getName()));
    }

    // === 右键 ===
    private String[] ctxItems;
    private int ctxX, ctxY;
    private boolean ctxOpen;

    private void showContextMenu() {
        File f = contextNode.getContent();
        File pd = f != null ? (f.isDirectory() ? f : f.getParentFile()) : datapackRoot;
        if (pd == null) return;
        List<String> items = new ArrayList<>();
        items.add("📄 新建文件");
        if (f == null || f.isDirectory()) items.add("📁 新建文件夹");
        if (f != null && f.exists()) {
            items.add("✏ 重命名");
            items.add("🗑 删除");
            items.add("📋 复制");
            if (f.isDirectory()) items.add("📦 新增配方");
        }
        if (clipboardFile != null && clipboardFile.exists()) items.add("📌 粘贴");
        items.add("📂 打开文件夹");
        ctxItems = items.toArray(new String[0]);
        ctxX = (int)(width * 0.22) + 4; ctxY = 30;
        ctxOpen = true;
    }

    private void closeCtx() { ctxOpen = false; ctxItems = null; }

    private void handleCtxClick(int idx) {
        if (ctxItems == null || idx < 0 || idx >= ctxItems.length) return;
        File f = contextNode.getContent();
        File pd = f != null ? (f.isDirectory() ? f : f.getParentFile()) : datapackRoot;
        String item = ctxItems[idx];
        closeCtx();
        if (item.startsWith("📄")) { promptCreate(pd, false); refresh(); }
        else if (item.startsWith("📁")) { promptCreate(pd, true); refresh(); }
        else if (item.startsWith("✏")) { promptRename(f); refresh(); }
        else if (item.startsWith("🗑")) { deleteRecursive(f); refresh(); if (f != null && f.equals(currentFile)) { currentFile = null; editor.setLines(new String[]{""}); updateStatus(); } }
        else if (item.startsWith("📋")) { clipboardFile = f; showOverlay("§e已复制: "+f.getName()); }
        else if (item.startsWith("📦")) { openRecipeCreator(f); }
        else if (item.startsWith("📌")) { File d = new File(pd, clipboardFile.getName()); if (!d.exists()) { pasteFile(clipboardFile, pd); clipboardFile = null; refresh(); } else showOverlay("§c已存在"); }
        else if (item.startsWith("📂")) { openInExplorer(pd); }
    }
    private void refresh() { scanDatapack(datapackRoot); fileTree.expandNode(rootNode); }

    private void promptCreate(File p, boolean dir) {
        if (p == null) return; var dlg = new Dialog(); dlg.setAutoClose(false); dlg.darkenBackground();
        dlg.titleBar.addChildren(new Label().setText(Text.literal(dir?"新建文件夹":"新建文件")));
        var in = new TextField(); in.layout(l -> l.widthPercent(80));
        dlg.contentContainer.addChildren(new Label().setText(Text.literal("名称:")), in);
        var ok = new Button().setText("确定"); ok.setOnClick(e -> { String n = in.getValue().trim(); if (!n.isEmpty()) try { File nf = new File(p, n); if (dir) nf.mkdirs(); else nf.createNewFile(); } catch (IOException ex) {} dlg.close(); });
        var no = new Button().setText("取消"); no.setOnClick(e -> dlg.close());
        dlg.buttonContainer.layout(l -> l.flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
        dlg.buttonContainer.addChildren(ok, no); dlg.show(this.modularUI);
    }

    private void promptRename(File f) {
        if (f == null) return; var dlg = new Dialog(); dlg.setAutoClose(false); dlg.darkenBackground();
        dlg.titleBar.addChildren(new Label().setText(Text.literal("重命名")));
        var in = new TextField(); in.setText(f.getName()); in.layout(l -> l.widthPercent(80));
        dlg.contentContainer.addChildren(new Label().setText(Text.literal("新名称:")), in);
        var ok = new Button().setText("确定"); ok.setOnClick(e -> { String n = in.getValue().trim(); if (!n.isEmpty() && !n.equals(f.getName())) { f.renameTo(new File(f.getParentFile(), n)); if (currentFile != null && currentFile.equals(f)) currentFile = new File(f.getParentFile(), n); } dlg.close(); });
        var no = new Button().setText("取消"); no.setOnClick(e -> dlg.close());
        dlg.buttonContainer.layout(l -> l.flexDirection(org.appliedenergistics.yoga.YogaFlexDirection.ROW));
        dlg.buttonContainer.addChildren(ok, no); dlg.show(this.modularUI);
    }
    private boolean deleteRecursive(File f) { if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File c : k) deleteRecursive(c); } return f.delete(); }
    private void pasteFile(File s, File d) { try { File t = new File(d, s.getName()); if (s.isDirectory()) { if (!t.exists()) t.mkdirs(); File[] k = s.listFiles(); if (k != null) for (File c : k) pasteFile(c, t); } else Files.copy(s.toPath(), t.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (IOException e) {} }
    private void openInExplorer(File f) { try { Runtime.getRuntime().exec(new String[]{"explorer", "/select,", f.getAbsolutePath()}); } catch (IOException ignored) {} }
    private void openRecipeCreator(File f) { if (f != null) client.setScreen(new RecipeCreatorScreen(f)); }

    // === 补全 ===
    private void renderCompletions(DrawContext ctx) {
        if (completions.isEmpty() || !editor.isFocused()) return;
        var tr = client.textRenderer; int lh = tr.fontHeight+2;
        float cx = editor.x+4+tr.getWidth(editor.getLines()[editor.getCursorLine()].substring(0,Math.min(editor.getCursorCol(),editor.getLines()[editor.getCursorLine()].length())));
        float cy = editor.y+editor.getCursorLine()*lh+lh+4;
        int show = Math.min(5, completions.size()), start = Math.max(0, completionIdx-show/2);
        if (start+show>completions.size()) start = Math.max(0, completions.size()-show);
        int mw=0; for (int i=start;i<Math.min(completions.size(),start+show);i++) mw=Math.max(mw,tr.getWidth(completions.get(i)));
        int pad=6, bw=mw+pad*2, bh=show*(tr.fontHeight+1)+pad*2;
        if (cx+bw>width) cx=width-bw; if (cy+bh>height) cy=editor.y+editor.getCursorLine()*lh-bh;
        ctx.fill((int)cx,(int)cy,(int)(cx+bw),(int)(cy+bh),0xCC1E1E1E);
        ctx.drawBorder((int)cx,(int)cy,bw,bh,0xFFFFFFFF);
        for (int i=start;i<Math.min(completions.size(),start+show);i++)
            ctx.drawText(tr,(i==completionIdx?"§e§l":"§7")+completions.get(i),(int)(cx+pad),(int)(cy+pad+(i-start)*(tr.fontHeight+1)),-1,false);
    }
    private void autoComplete() {
        if (completing) return; String[] ls = editor.getLines();
        int li=editor.getCursorLine(),col=editor.getCursorCol();
        if (li>=ls.length) return;
        String p=col<=ls[li].length()?ls[li].substring(0,col):ls[li];
        if (p.isEmpty()) { completions=List.of(); return; }
        completing=true;
        McFunctionCompletionProvider.getCompletionsAsync(p,s->client.execute(()->{completions=s;completionIdx=0;completing=false;}));
    }
    private void acceptCompletion() {
        if (completions.isEmpty()) return; String[] ls=editor.getLines();
        int li=editor.getCursorLine(),col=editor.getCursorCol();
        String rest=col<ls[li].length()?ls[li].substring(col):"", cp=completions.get(completionIdx);
        int ins=col; for (int i=col-1;i>=0;i--) { if (cp.startsWith(ls[li].substring(i,col))) { ins=i; break; } }
        ls[li]=ls[li].substring(0,ins)+cp+rest; editor.setLines(ls); editor.setCursor(li,ins+cp.length()); completions=List.of();
    }
    private void showExitDialog() {
        var dlg = new Dialog(); dlg.darkenBackground();
        dlg.titleBar.addChildren(new Label().setText(Text.literal("未保存")));
        dlg.contentContainer.addChildren(new Label().setText(Text.literal(currentFile.getName()+" 已修改，保存？")));
        var s=new Button().setText("保存"); s.layout(l->l.widthPercent(100));
        var d=new Button().setText("丢弃"); d.layout(l->l.widthPercent(100));
        s.setOnClick(e->{saveFile();client.setScreen(null);dlg.close();});
        d.setOnClick(e->{client.setScreen(null);dlg.close();});
        dlg.contentContainer.addChildren(s,d); dlg.show(this.modularUI);
    }
    private void showOverlay(String msg) { if (client.player!=null) client.player.sendMessage(Text.literal("§8[CialloMine] "+msg), true); }
    public void setDatapackRoot(File r) { this.datapackRoot=r; }
}
