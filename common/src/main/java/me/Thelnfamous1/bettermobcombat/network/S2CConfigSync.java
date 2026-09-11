package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CConfigSync(String json) implements S2CModPacket<RegistryFriendlyByteBuf> {
    public static Type<S2CConfigSync> ID = new Type<>(Constants.id("config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf,S2CConfigSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,S2CConfigSync::json,S2CConfigSync::new);

    @Override
    public void handleClient() {
        BMCClientNetworkHandler.handleConfigSync(this.json);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
