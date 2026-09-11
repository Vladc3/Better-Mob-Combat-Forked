package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.Constants;
import net.bettercombat.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CAttackSound(int mobId, double x, double y, double z, String soundId, float volume, float pitch, long seed) implements S2CModPacket<RegistryFriendlyByteBuf> {
    public static Type<S2CAttackSound> ID = new Type<>(Constants.id("attack_sound"));

    public static final StreamCodec<RegistryFriendlyByteBuf,S2CAttackSound> STREAM_CODEC =
            StreamCodec.ofMember(S2CAttackSound::write, S2CAttackSound::read);

    public static S2CAttackSound read(FriendlyByteBuf buffer) {
        int mobId = buffer.readInt();
        double x = buffer.readDouble();
        double y = buffer.readDouble();
        double z = buffer.readDouble();
        String soundId = buffer.readUtf();
        float volume = buffer.readFloat();
        float pitch = buffer.readFloat();
        long seed = buffer.readLong();
        return new S2CAttackSound(mobId, x, y, z, soundId, volume, pitch, seed);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeInt(this.mobId);
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeUtf(this.soundId);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    @Override
    public void handleClient(){
        BMCClientNetworkHandler.handlePlaySound(this.mobId(), this.x(), this.y(), this.z(), this.soundId(), this.volume(), this.pitch(), this.seed());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}