package com.sisariku.datapack;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class DatapackNetwork {
    public static final Identifier PERMISSION_QUERY_ID = Identifier.of(CialloMineDatapack.MOD_ID, "permission_query");
    public static final Identifier PERMISSION_REPLY_ID = Identifier.of(CialloMineDatapack.MOD_ID, "permission_reply");

    public record PermissionQuery() implements CustomPayload {
        public static final Id<PermissionQuery> ID = new Id<>(PERMISSION_QUERY_ID);
        @Override public Id<PermissionQuery> getId() { return ID; }
    }

    public record PermissionReply(boolean granted) implements CustomPayload {
        public static final Id<PermissionReply> ID = new Id<>(PERMISSION_REPLY_ID);
        @Override public Id<PermissionReply> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PermissionQuery.ID, PacketCodec.unit(new PermissionQuery()));
        PayloadTypeRegistry.playS2C().register(PermissionReply.ID, PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.granted),
            buf -> new PermissionReply(buf.readBoolean())
        ));

        PayloadTypeRegistry.playC2S().register(LockRequest.ID, PacketCodec.of(
            (value, buf) -> buf.writeString(value.path),
            buf -> new LockRequest(buf.readString())
        ));
        PayloadTypeRegistry.playS2C().register(LockReply.ID, PacketCodec.of(
            (value, buf) -> { buf.writeString(value.path); buf.writeBoolean(value.granted); },
            buf -> new LockReply(buf.readString(), buf.readBoolean())
        ));
        PayloadTypeRegistry.playC2S().register(UnlockRequest.ID, PacketCodec.of(
            (value, buf) -> buf.writeString(value.path),
            buf -> new UnlockRequest(buf.readString())
        ));

        ServerPlayNetworking.registerGlobalReceiver(PermissionQuery.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            boolean granted = DatapackPermissionManager.isAuthorized(player);
            ServerPlayNetworking.send(player, new PermissionReply(granted));
        });

        // 文件锁
        ServerPlayNetworking.registerGlobalReceiver(LockRequest.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            boolean granted = DatapackLockManager.tryLock(payload.path, player.getUuid());
            ServerPlayNetworking.send(player, new LockReply(payload.path, granted));
        });
        ServerPlayNetworking.registerGlobalReceiver(UnlockRequest.ID, (payload, context) -> {
            DatapackLockManager.release(payload.path, context.player().getUuid());
        });
    }

    public record LockRequest(String path) implements CustomPayload {
        public static final Id<LockRequest> ID = new Id<>(Identifier.of(CialloMineDatapack.MOD_ID, "lock_request"));
        @Override public Id<LockRequest> getId() { return ID; }
    }
    public record LockReply(String path, boolean granted) implements CustomPayload {
        public static final Id<LockReply> ID = new Id<>(Identifier.of(CialloMineDatapack.MOD_ID, "lock_reply"));
        @Override public Id<LockReply> getId() { return ID; }
    }
    public record UnlockRequest(String path) implements CustomPayload {
        public static final Id<UnlockRequest> ID = new Id<>(Identifier.of(CialloMineDatapack.MOD_ID, "unlock_request"));
        @Override public Id<UnlockRequest> getId() { return ID; }
    }
}
