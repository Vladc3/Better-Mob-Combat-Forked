package me.Thelnfamous1.bettermobcombat.platform;

import me.Thelnfamous1.bettermobcombat.network.*;
import me.Thelnfamous1.bettermobcombat.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class FabricPlatformHelper implements IPlatformHelper {

    private static MinecraftServer currentServer;

    static {
        // Track the server lifecycle globally to simulate NeoForge's ServerLifecycleHooks
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> currentServer = null);
    }

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public boolean isCastingSpell(LivingEntity mob) {
        return false;
    }

    @Override
    public <MSG extends S2CModPacket<?>> void registerClientPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        // Step 1: Register packet type globally (Safe on both client and server threads)
        PayloadTypeRegistry.playS2C().register(type, streamCodec);

        // Step 2: Register packet listener ONLY if we are physically running on a client environment
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientHelper.registerClientReceiver(type);
        }
    }

    @Override
    public <MSG extends C2SModPacket<?>> void registerServerPlayPacket(CustomPacketPayload.Type<MSG> type, StreamCodec<RegistryFriendlyByteBuf, MSG> streamCodec) {
        // Register packet type and server packet listener
        PayloadTypeRegistry.playC2S().register(type, streamCodec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            context.server().execute(() -> payload.handleServer(context.player()));
        });
    }

    @Override
    public void sendToClient(S2CModPacket<?> msg, ServerPlayer player) {
        ServerPlayNetworking.send(player, msg);
    }

    @Override
    public void sendToServer(C2SModPacket<?> msg) {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientHelper.sendToServer(msg);
        }
    }

    @Override
    public void sendToTracking(S2CModPacket<?> msg, Entity entity) {
        // Fabric API's PlayerLookup accurately gathers all players tracking the given entity
        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            ServerPlayNetworking.send(player, msg);
        }
    }

    @Override
    public MinecraftServer getServer() {
        return currentServer;
    }

    /**
     * Isolated helper class to securely enclose client-only Fabric API components.
     * Prevents dedicated server environments from prematurely verifying and throwing NoClassDefFoundErrors.
     */
    private static class ClientHelper {
        private static <MSG extends S2CModPacket<?>> void registerClientReceiver(CustomPacketPayload.Type<MSG> type) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
                context.client().execute(() -> payload.handleClient());
            });
        }

        private static void sendToServer(CustomPacketPayload msg) {
            ClientPlayNetworking.send(msg);
        }
    }

    @Override
    public int getAttackStrengthTicker(LivingEntity entity) {
        return ((net.bettercombat.mixin.player.LivingEntityAccessor) entity).getLastAttackedTicks();
    }
}