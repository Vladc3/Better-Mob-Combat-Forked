package me.Thelnfamous1.bettermobcombat.network;

import me.Thelnfamous1.bettermobcombat.Constants;
import net.bettercombat.logic.AnimatedHand;
import net.bettercombat.network.Packets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record S2CAttackAnimation(int mobId, AnimatedHand animatedHand, String animationName, float length, float upswing)implements S2CModPacket<RegistryFriendlyByteBuf> {
    public static Type<S2CAttackAnimation> ID = new Type<>(Constants.id("attack_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf,S2CAttackAnimation> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,S2CAttackAnimation::mobId,
                    PacketHandler.enumStreamCodec(AnimatedHand.class),S2CAttackAnimation::animatedHand,
                    ByteBufCodecs.STRING_UTF8,S2CAttackAnimation::animationName,
                    ByteBufCodecs.FLOAT,S2CAttackAnimation::length,
                    ByteBufCodecs.FLOAT,S2CAttackAnimation::upswing,
                    S2CAttackAnimation::new
                    );

    public static S2CAttackAnimation stop(int mobId, int length) {
        return new S2CAttackAnimation(mobId, AnimatedHand.MAIN_HAND, Packets.AttackAnimation.StopSymbol, (float)length, 0.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    @Override
    public void handleClient() {
        BMCClientNetworkHandler.handleAttackAnimation(this.mobId(), this.animationName(), this.length(), this.animatedHand(), this.upswing());
    }
}