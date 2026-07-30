package com.sisariku.datapack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DatapackPermissionManager {
    private static final Gson GSON = new Gson();
    private static final Set<UUID> authorized = new HashSet<>();
    private static java.nio.file.Path savePath;

    public static void init(MinecraftServer server) {
        savePath = java.nio.file.Path.of(server.getRunDirectory().resolve("config").resolve("ciallomine_permissions.json").toString());
        load();
    }

    public static boolean isAuthorized(UUID playerId) {
        return authorized.contains(playerId);
    }

    public static boolean isAuthorized(ServerPlayerEntity player) {
        return authorized.contains(player.getUuid()) || player.hasPermissionLevel(2); // OP always has access
    }

    public static void grant(ServerPlayerEntity player) {
        authorized.add(player.getUuid());
        save();
    }

    public static void revoke(UUID playerId) {
        authorized.remove(playerId);
        save();
    }

    private static void load() {
        authorized.clear();
        if (savePath != null && Files.exists(savePath)) {
            try {
                String json = Files.readString(savePath);
                List<String> uuids = GSON.fromJson(json, new TypeToken<List<String>>(){}.getType());
                if (uuids != null) uuids.forEach(u -> authorized.add(UUID.fromString(u)));
            } catch (Exception e) {
                CialloMineDatapack.LOGGER.warn("Failed to load permissions: {}", e.getMessage());
            }
        }
    }

    private static void save() {
        if (savePath == null) return;
        try {
            List<String> uuids = new ArrayList<>();
            authorized.forEach(u -> uuids.add(u.toString()));
            Files.writeString(savePath, GSON.toJson(uuids));
        } catch (IOException e) {
            CialloMineDatapack.LOGGER.error("Failed to save permissions: {}", e.getMessage());
        }
    }
}
