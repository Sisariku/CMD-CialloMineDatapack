package com.sisariku.datapack;

import net.minecraft.server.network.ServerPlayerEntity;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatapackLockManager {
    private static final Map<String, UUID> fileLocks = new ConcurrentHashMap<>();
    private static final String LOCK_PREFIX = "datapacks/";

    public static boolean tryLock(String relativePath, UUID playerId) {
        String key = LOCK_PREFIX + relativePath;
        UUID current = fileLocks.get(key);
        if (current != null && !current.equals(playerId)) return false; // 已被他人锁定
        fileLocks.put(key, playerId);
        return true;
    }

    public static void release(String relativePath, UUID playerId) {
        String key = LOCK_PREFIX + relativePath;
        fileLocks.remove(key, playerId);
    }

    public static void releaseAll(UUID playerId) {
        fileLocks.entrySet().removeIf(e -> e.getValue().equals(playerId));
    }

    public static UUID getOwner(String relativePath) {
        return fileLocks.get(LOCK_PREFIX + relativePath);
    }
}
