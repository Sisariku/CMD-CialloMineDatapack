package com.sisariku.datapack;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
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

        DatapackNetwork.register();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> DatapackPermissionManager.init(server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("ciallo")
                .then(CommandManager.literal("datapack")
                    // 权限管理
                    .then(CommandManager.literal("op")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .requires(src -> src.hasPermissionLevel(2))
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                DatapackPermissionManager.grant(target);
                                ctx.getSource().sendFeedback(() -> Text.literal("§a已授予 " + target.getName().getString() + " 数据包编辑权限。"), true);
                                return 1;
                            })
                        )
                    )
                    .then(CommandManager.literal("deop")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                            .requires(src -> src.hasPermissionLevel(2))
                            .executes(ctx -> {
                                ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
                                DatapackPermissionManager.revoke(target.getUuid());
                                ctx.getSource().sendFeedback(() -> Text.literal("§c已撤销 " + target.getName().getString() + " 的数据包编辑权限。"), true);
                                return 1;
                            })
                        )
                    )
                    // 客户端使用的桩命令（Tab 补全）
                    .then(CommandManager.literal("edit")
                        .executes(ctx -> { ctx.getSource().sendFeedback(() -> Text.literal("请在客户端使用此命令。"), false); return 1; })
                    )
                    .then(CommandManager.literal("new")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> { ctx.getSource().sendFeedback(() -> Text.literal("请在客户端使用此命令。"), false); return 1; })
                        )
                    )
                )
            )
        );
    }
}
