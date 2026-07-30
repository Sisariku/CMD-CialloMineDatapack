package com.sisariku.datapack.editor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.*;
import java.util.concurrent.*;

/**
 * 借用 Minecraft 内置 CommandDispatcher 提供命令补全。
 * 数据源与聊天栏/命令方块完全一致，自动包含所有 Mod 命令。
 */
public class McFunctionCompletionProvider {

    public interface Callback { void onResult(List<String> completions); }

    private static String lastQuery = "";
    private static List<String> lastResult = List.of();

    /** 同步获取（Tab 键循环用） */
    public static List<String> getCompletions(String input) {
        if (input.equals(lastQuery)) return lastResult;
        List<String> result = doQuery(input);
        lastQuery = input;
        lastResult = result;
        return result;
    }

    /** 异步获取（输入时自动补全用） */
    public static void getCompletionsAsync(String input, Callback callback) {
        CompletableFuture.supplyAsync(() -> doQuery(input))
                .thenAccept(result -> MinecraftClient.getInstance().execute(() -> callback.onResult(result)));
    }

    private static List<String> doQuery(String input) {
        List<String> result = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (!client.isIntegratedServerRunning() || client.getServer() == null) return result;
        try {
            CommandManager cmdMgr = client.getServer().getCommandManager();
            CommandDispatcher<ServerCommandSource> dispatcher = cmdMgr.getDispatcher();
            ServerCommandSource source = client.getServer().getCommandSource().withSilent();
            ParseResults<ServerCommandSource> parse = dispatcher.parse(input, source);
            Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).get(300, TimeUnit.MILLISECONDS);
            for (Suggestion s : suggestions.getList()) result.add(s.getText());
        } catch (Exception ignored) {}
        return result;
    }
}
