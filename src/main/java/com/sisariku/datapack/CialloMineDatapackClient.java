package com.sisariku.datapack;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sisariku.datapack.editor.DatapackEditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;

public class CialloMineDatapackClient implements ClientModInitializer {

    private static final int PACK_FORMAT = 48;
    private static boolean canEdit = true; // 默认主机有权限

    @Override
    public void onInitializeClient() {
        CialloMineDatapack.LOGGER.info("[CialloMineDatapack] 客户端已初始化。");

        // 监听权限回复
        ClientPlayNetworking.registerGlobalReceiver(DatapackNetwork.PermissionReply.ID, (payload, ctx) -> {
            canEdit = payload.granted();
            if (!canEdit) ctx.client().player.sendMessage(Text.literal("§c你没有数据包编辑权限，文件将只读。"), true);
        });

        // 快捷键 O 打开编辑器
        KeyBinding openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ciallominedatapack.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, "category.ciallominedatapack"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) {
                    canEdit = isHost(); // 主机默认有权限
                    if (!canEdit) queryPermission(); // 非主机向服务器查询
                    File dpRoot = findWorldDatapacks();
                    if (dpRoot != null) openEditor(dpRoot);
                }
            }
        });

        // 命令
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("ciallo")
                .then(ClientCommandManager.literal("datapack")
                    .then(ClientCommandManager.literal("edit")
                        .executes(ctx -> {
                            File dpRoot = findWorldDatapacks();
                            if (dpRoot == null) { ctx.getSource().sendFeedback(Text.literal("§c未找到数据包目录")); return 0; }
                            openEditor(dpRoot); return 1;
                        })
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                File dpRoot = new File(StringArgumentType.getString(ctx, "path"));
                                if (!dpRoot.exists()) { ctx.getSource().sendFeedback(Text.literal("§c路径不存在")); return 0; }
                                openEditor(dpRoot); return 1;
                            })
                        )
                    )
                    .then(ClientCommandManager.literal("new")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                String name = StringArgumentType.getString(ctx, "name");
                                if (!isValidName(name)) { ctx.getSource().sendFeedback(Text.literal("§c名称只能包含小写字母、数字、下划线和短横线")); return 0; }
                                if (!canEdit) { ctx.getSource().sendFeedback(Text.literal("§c无编辑权限，无法创建数据包")); return 0; }
                                createDatapack(name);
                                return 1;
                            })
                        )
                    )
                )
            )
        );
    }

    private static void queryPermission() {
        if (!isHost()) ClientPlayNetworking.send(new DatapackNetwork.PermissionQuery());
    }

    public static boolean isHost() {
        var client = MinecraftClient.getInstance();
        return client.isIntegratedServerRunning() && client.getServer() != null && !client.getServer().isRemote();
    }

    public static boolean canEdit() { return canEdit; }

    private static File findWorldDatapacks() {
        var client = MinecraftClient.getInstance();
        if (client.getServer() instanceof IntegratedServer server) {
            String levelName = server.getSaveProperties().getLevelName();
            return new File(client.runDirectory, "saves/" + levelName + "/datapacks");
        }
        if (client.getServer() == null && client.getCurrentServerEntry() != null) {
            return new File(client.runDirectory, "config/ciallomine_datapacks");
        }
        return null;
    }

    public static void createDatapack(String name) {
        if (name.isEmpty() || !isValidName(name)) return;
        var client = MinecraftClient.getInstance();
        File root = findWorldDatapacks();
        if (root == null) return;
        File dir = new File(root, name);
        if (dir.exists()) { if (client.player != null) client.player.sendMessage(Text.literal("§c数据包已存在")); return; }
        File dataDir = new File(dir, "data/" + name);
        File funcDir = new File(dataDir, "function");
        File recipeDir = new File(dataDir, "recipe");
        dataDir.mkdirs(); funcDir.mkdirs(); recipeDir.mkdirs();
        try {
            Files.writeString(new File(funcDir, "tick.mcfunction").toPath(), "# " + name + " tick 函数\n");
            Files.writeString(new File(funcDir, "load.mcfunction").toPath(), "# " + name + " load 函数\n");
            String mcmeta = "{\"pack\":{\"pack_format\":" + PACK_FORMAT + ",\"description\":\"" + name + "\"}}";
            Files.writeString(new File(dir, "pack.mcmeta").toPath(), mcmeta);
            if (client.player != null) client.player.sendMessage(Text.literal("§a已创建数据包: " + name));
        } catch (IOException e) {
            if (client.player != null) client.player.sendMessage(Text.literal("§c创建失败"));
        }
    }

    private static boolean isValidName(String name) {
        return name != null && name.matches("^[a-z0-9_][a-z0-9_-]*$");
    }

    private static void openEditor(File dpRoot) {
        var screen = new DatapackEditorScreen();
        screen.setDatapackRoot(dpRoot);
        screen.setReadOnly(!canEdit);
        MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(screen));
    }
}
