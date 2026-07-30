package com.sisariku.datapack;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DatapackPermissionManager {
    private static final Gson GSON = new Gson();
    // player UUID -> Set of datapack names they can edit
    private static final Map<UUID, Set<String>> permissions = new HashMap<>();
    private static Path savePath;

    public static void init(MinecraftServer server) {
        savePath = java.nio.file.Path.of(server.getRunDirectory().resolve("config").resolve("ciallomine_permissions.json").toString());
        load();
    }

    /** 检查玩家是否有权限编辑指定数据包 */
    public static boolean canEdit(UUID playerId, String datapackName) {
        // Server OP always has access
        return permissions.getOrDefault(playerId, Set.of()).contains(datapackName);
    }

    public static boolean hasAnyPermission(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || permissions.containsKey(player.getUuid());
    }

    /** 授权 */
    public static void grant(UUID playerId, String datapackName) {
        permissions.computeIfAbsent(playerId, k -> new HashSet<>()).add(datapackName);
        save();
    }

    /** 撤销单个数据包权限 */
    public static void revoke(UUID playerId, String datapackName) {
        Set<String> set = permissions.get(playerId);
        if (set != null) { set.remove(datapackName); if (set.isEmpty()) permissions.remove(playerId); }
        save();
    }

    /** 撤销玩家全部权限 */
    public static void revokeAll(UUID playerId) {
        permissions.remove(playerId);
        save();
    }

    private static void load() {
        permissions.clear();
        if (savePath != null && Files.exists(savePath)) {
            try {
                String json = Files.readString(savePath);
                Map<String, List<String>> raw = GSON.fromJson(json, new TypeToken<Map<String, List<String>>>(){}.getType());
                if (raw != null) raw.forEach((uuid, names) -> permissions.put(UUID.fromString(uuid), new HashSet<>(names)));
            } catch (Exception e) {
                CialloMineDatapack.LOGGER.warn("Failed to load permissions: {}", e.getMessage());
            }
        }
    }

    private static void save() {
        if (savePath == null) return;
        try {
            Map<String, List<String>> raw = new LinkedHashMap<>();
            permissions.forEach((uuid, names) -> raw.put(uuid.toString(), new ArrayList<>(names)));
            Files.writeString(savePath, GSON.toJson(raw));
        } catch (IOException e) {
            CialloMineDatapack.LOGGER.error("Failed to save permissions: {}", e.getMessage());
        }
    }
}
