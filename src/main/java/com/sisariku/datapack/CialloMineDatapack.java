package com.sisariku.datapack;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CialloMineDatapack implements ModInitializer {
    public static final String MOD_ID = "ciallominedatapack";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CialloMineDatapack] 数据包编辑器已初始化。");

        // 服务端命令桩 — 为客户端 /ciallo datapack 提供 Tab 补全
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("ciallo")
                .then(CommandManager.literal("datapack")
                    .then(CommandManager.literal("edit")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal("请在客户端使用此命令。"), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("new")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> {
                                ctx.getSource().sendFeedback(() -> Text.literal("请在客户端使用此命令。"), false);
                                return 1;
                            })
                        )
                    )
                )
            )
        );
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
