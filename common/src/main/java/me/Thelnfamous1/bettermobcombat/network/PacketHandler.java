package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.platform.Services;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PacketHandler {

    public static void registerPackets() {
        Services.PLATFORM.registerClientPlayPacket(S2CAttackAnimation.ID,S2CAttackAnimation.STREAM_CODEC);
        Services.PLATFORM.registerClientPlayPacket(S2CConfigSync.ID, S2CConfigSync.STREAM_CODEC);
        Services.PLATFORM.registerClientPlayPacket(S2CAttackSound.ID, S2CAttackSound.STREAM_CODEC);
        Services.PLATFORM.registerClientPlayPacket(S2CComboCountSync.ID, S2CComboCountSync.STREAM_CODEC);
    }

    public static <B extends FriendlyByteBuf, V extends Enum<V>> StreamCodec<B, V> enumStreamCodec(final Class<V> enumClass) {
        return new StreamCodec<>() {
            @Override
            public V decode(B buf) {
                return buf.readEnum(enumClass);
            }

            @Override
            public void encode(B buf, V value) {
                buf.writeEnum(value);
            }
        };
    }

    public static void sendToServer(C2SModPacket<?> packet) {
        Services.PLATFORM.sendToServer(packet);
    }

    public static void sendToClient(S2CModPacket<?> packet, ServerPlayer player) {
            Services.PLATFORM.sendToClient(packet, player);
    }

    public static void sendToTracking(S2CModPacket<?> packet, Entity entity) {
        Services.PLATFORM.sendToTracking(packet, entity);
    }

    public static void sendToAll(S2CModPacket<?> msg, MinecraftServer server) {
        if (server != null) {
            server.getPlayerList().getPlayers().forEach(player -> sendToClient(msg, player));
        }
    }

    public static void sendToNearby(S2CModPacket<?> msg, ServerLevel world, Vec3 pos, double radius) {
        List<ServerPlayer> players = world.players();
        double radiusSq = radius * radius;
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(pos) < radiusSq) {
                sendToClient(msg,player);
            }
        }
    }
}
