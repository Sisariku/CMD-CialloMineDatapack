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
                if (client.currentScreen == null) {
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
        // 单人/局域网主机：集成服务器在运行
        if (client.getServer() != null && !client.getServer().isRemote()) return true;
        // 局域网主机重连后 getServer 可能为 null，通过 isIntegratedServerRunning 判断
        return client.isIntegratedServerRunning();
    }

    private static File findWorldDatapacks() {
        var client = MinecraftClient.getInstance();
        // 主机/单人：用集成服务器的世界名
        if (client.getServer() instanceof IntegratedServer s) {
            String name = s.getSaveProperties().getLevelName();
            return new File(client.runDirectory, "saves/" + name + "/datapacks");
        }
        // 局域网/远程客户端：用连接到的服务器名作为本地数据包目录
        var entry = client.getCurrentServerEntry();
        if (entry != null) {
            String name = entry.name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            return new File(client.runDirectory, "datapacks/" + name);
        }
        return null;
    }

    private static void openEditor(File dpRoot) {
        var screen = new DatapackEditorScreen();
        screen.setDatapackRoot(dpRoot);
        MinecraftClient.getInstance().send(() -> MinecraftClient.getInstance().setScreen(screen));
    }
}
