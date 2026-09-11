package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CComboCountSync(int mobId, int comboCount) implements S2CModPacket<RegistryFriendlyByteBuf> {
    public static Type<S2CComboCountSync> ID = new Type<>(Constants.id("combo_count_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf,S2CComboCountSync> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,S2CComboCountSync::mobId,
            ByteBufCodecs.INT,S2CComboCountSync::comboCount,
            S2CComboCountSync::new
            );

    public static S2CComboCountSync read(FriendlyByteBuf buffer) {
        int mobId = buffer.readInt();
        int comboCount = buffer.readInt();
        return new S2CComboCountSync(mobId, comboCount);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.mobId);
        buffer.writeInt(this.comboCount);
    }

    public int mobId() {
            return this.mobId;
    }

    public int comboCount() {
        return this.comboCount;
    }

    @Override
    public void handleClient() {
        BMCClientNetworkHandler.handleComboSync(this.mobId(), this.comboCount());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}