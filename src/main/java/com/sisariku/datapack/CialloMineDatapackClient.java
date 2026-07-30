package com.sisariku.datapack;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.sisariku.datapack.editor.DatapackEditorScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;

public class CialloMineDatapackClient implements ClientModInitializer {

    private static final int PACK_FORMAT = 48; // 1.21.1

    @Override
    public void onInitializeClient() {
        CialloMineDatapack.LOGGER.info("[CialloMineDatapack] 客户端已初始化。");

        // 快捷键 Ctrl+E 打开数据包编辑器
        KeyBinding openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.ciallominedatapack.open",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "category.ciallominedatapack"
        ));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (isHost() && client.currentScreen == null) {
                    File dpRoot = findWorldDatapacks();
                    if (dpRoot != null) openEditor(dpRoot);
                }
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("ciallo")
                .then(ClientCommandManager.literal("datapack")
                    .then(ClientCommandManager.literal("edit")
                        .executes(ctx -> {
                            if (!isHost()) { ctx.getSource().sendFeedback(Text.literal("§c仅主机可执行此命令")); return 0; }
                            File dpRoot = findWorldDatapacks();
                            if (dpRoot == null) {
                                ctx.getSource().sendFeedback(Text.literal("§c未在单机世界中，请指定路径: /ciallo datapack edit <路径>"));
                                return 0;
                            }
                            openEditor(dpRoot);
                            return 1;
                        })
                        .then(ClientCommandManager.argument("path", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                if (!isHost()) { ctx.getSource().sendFeedback(Text.literal("§c仅主机可执行此命令")); return 0; }
                                File dpRoot = new File(StringArgumentType.getString(ctx, "path"));
                                if (!dpRoot.exists() || !dpRoot.isDirectory()) {
                                    ctx.getSource().sendFeedback(Text.literal("§c路径不存在或不是目录: " + dpRoot));
                                    return 0;
                                }
                                openEditor(dpRoot);
                                return 1;
                            })
                        )
                    )
                    .then(ClientCommandManager.literal("new")
                        .then(ClientCommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                if (!isHost()) { ctx.getSource().sendFeedback(Text.literal("§c仅主机可执行此命令")); return 0; }
                                String name = StringArgumentType.getString(ctx, "name");
                                if (!isValidName(name)) {
                                    ctx.getSource().sendFeedback(Text.literal("§c名称只能包含小写字母、数字、下划线和短横线"));
                                    return 0;
                                }
                                File baseDir = findWorldDatapacks();
                                if (baseDir == null) {
                                    ctx.getSource().sendFeedback(Text.literal("§c未在单机世界中"));
                                    return 0;
                                }
                                File dpDir = new File(baseDir, name);
                                if (dpDir.exists()) {
                                    ctx.getSource().sendFeedback(Text.literal("§c数据包 '" + name + "' 已存在"));
                                    return 0;
                                }
                                try {
                                    createDatapack(dpDir, name);
                                    ctx.getSource().sendFeedback(Text.literal("§a数据包 '" + name + "' 创建成功"));
                                    return 1;
                                } catch (IOException e) {
                                    ctx.getSource().sendFeedback(Text.literal("§c创建失败: " + e.getMessage()));
                                    return 0;
                                }
                            })
                        )
                    )
                )
            )
        );
    }

    private static boolean isValidName(String name) {
        return name.matches("[a-z0-9_-]+");
    }

    private static void createDatapack(File dir, String name) throws IOException {
        // 目录结构
        File dataDir = new File(dir, "data/" + name + "/function");
        dataDir.mkdirs();
        File tagsDir = new File(dir, "data/minecraft/tags/function");
        tagsDir.mkdirs();

        // pack.mcmeta
        String mcmeta = "{\n" +
                "  \"pack\": {\n" +
                "    \"pack_format\": " + PACK_FORMAT + ",\n" +
                "    \"description\": \"" + name + "\"\n" +
                "  }\n" +
                "}\n";
        Files.writeString(new File(dir, "pack.mcmeta").toPath(), mcmeta);

        // data/minecraft/tags/function/tick.json
        Files.writeString(new File(tagsDir, "tick.json").toPath(),
                "{\"values\":[\"" + name + ":tick\"]}\n");
        // data/minecraft/tags/function/load.json
        Files.writeString(new File(tagsDir, "load.json").toPath(),
                "{\"values\":[\"" + name + ":load\"]}\n");

        // data/<name>/function/tick.mcfunction
        Files.writeString(new File(dataDir, "tick.mcfunction").toPath(), "");
        // data/<name>/function/load.mcfunction
        Files.writeString(new File(dataDir, "load.mcfunction").toPath(), "");
    }

    private static boolean isHost() {
        var client = MinecraftClient.getInstance();
        return client.getServer() != null && !client.getServer().isRemote();
    }

    private static File findWorldDatapacks() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getServer() instanceof IntegratedServer server) {
            String levelName = server.getSaveProperties().getLevelName();
            return new File(client.runDirectory, "saves/" + levelName + "/datapacks");
        }
        return null;
    }

    private static void openEditor(File dpRoot) {
        var screen = new DatapackEditorScreen();
        screen.setDatapackRoot(dpRoot);
        MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(screen));
    }
}
