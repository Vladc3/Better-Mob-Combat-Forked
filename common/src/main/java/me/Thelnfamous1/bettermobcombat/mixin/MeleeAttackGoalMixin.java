package me.Thelnfamous1.bettermobcombat.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.Thelnfamous1.bettermobcombat.api.MobAttackWindup;
import me.Thelnfamous1.bettermobcombat.logic.MobCombatHelper;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.EntityPlayer_BetterCombat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin extends Goal {

    @Shadow @Final protected PathfinderMob mob;
    @Shadow private int ticksUntilNextAttack;

    // Proximity Slowdown
    @Unique
    private static final ResourceLocation bettermobcombat$PROXIMITY_SLOWDOWN_ID =
            ResourceLocation.fromNamespaceAndPath("bettermobcombat", "close_range_speed_cap");
    @Unique
    private static final double bettermobcombat$CLOSE_SPEED = 0.23D;
    @Inject(method = "tick", at = @At("HEAD"))
    private void pre_tick(CallbackInfo ci) {
        LivingEntity target = this.mob.getTarget();
        AttributeInstance speedAttr = this.mob.getAttribute(Attributes.MOVEMENT_SPEED);

        if (speedAttr != null) {
            boolean isWindup = ((MobAttackWindup) this.mob).bettermobcombat$hasDelayedUpswing();
            boolean isWithin3Blocks = target != null && this.mob.distanceToSqr(target) <= 16.0D;

            if (isWindup && isWithin3Blocks) {
                speedAttr.removeModifier(bettermobcombat$PROXIMITY_SLOWDOWN_ID);
                double currentSpeed = speedAttr.getValue();
                if (currentSpeed > bettermobcombat$CLOSE_SPEED) {
                    double reductionNeeded = bettermobcombat$CLOSE_SPEED - currentSpeed;
                    speedAttr.addTransientModifier(new AttributeModifier(
                            bettermobcombat$PROXIMITY_SLOWDOWN_ID,
                            reductionNeeded,
                            AttributeModifier.Operation.ADD_VALUE
                    ));
                }
            } else {
                speedAttr.removeModifier(bettermobcombat$PROXIMITY_SLOWDOWN_ID);
            }
        }
    }
    @Inject(method = "stop", at = @At("HEAD"))
    private void pre_stop(CallbackInfo ci) {
        AttributeInstance speedAttr = this.mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(bettermobcombat$PROXIMITY_SLOWDOWN_ID);
        }
    }


    @Inject(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/PathfinderMob;isWithinMeleeAttackRange(Lnet/minecraft/world/entity/LivingEntity;)Z"), cancellable = true)
    private void pre_getAttackReachSqr_canUse(CallbackInfoReturnable<Boolean> cir){
        LivingEntity target = this.mob.getTarget();
        if (target == null) return;

        //  If the target is physically overlapping/touching the mob, it is ALWAYS in range to start the goal
        double inflation = this.mob.isPassenger() ? 0.5 : 0.25;
        if (this.mob.getBoundingBox().inflate(inflation).intersects(target.getBoundingBox())) {
            cir.setReturnValue(true);
            return;
        }

        MobCombatHelper.onHoldingBetterCombatWeapon(this.mob, (m, wa) -> {
            AttackHand currentAttack = ((EntityPlayer_BetterCombat)m).getCurrentAttack();
            if(currentAttack != null){
                cir.setReturnValue(MobCombatHelper.isWithinAttackRange(m, target, currentAttack.attack()));
            }
        });
    }

    @WrapOperation(method = "canContinueToUse", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/ai/goal/MeleeAttackGoal;followingTargetEvenIfNotSeen:Z", opcode = 180 /*GETFIELD*/))
    private boolean wrap_followingTargetEvenIfNotSeen(MeleeAttackGoal instance, Operation<Boolean> original){
        boolean followingTargetEvenIfNotSeen = original.call(instance);
        if(followingTargetEvenIfNotSeen){
            return MobCombatHelper.canUseBetterCombatWeapon(this.mob, (m, wa) -> {
                AttackHand currentAttack = ((EntityPlayer_BetterCombat)m).getCurrentAttack();
                return currentAttack == null;
            });
        }
        return false;
    }

    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/MeleeAttackGoal;checkAndPerformAttack(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private boolean pre_checkAndPerformAttack(MeleeAttackGoal goal, LivingEntity target){
        return bettermobcombat$allowCheckAndPerformAttackCall(target);
    }

    @Unique
    protected boolean bettermobcombat$allowCheckAndPerformAttackCall(LivingEntity target) {
        return !this.bettermobcombat$useBetterCombatAttackCheck(target);
    }

    @Unique
    protected boolean bettermobcombat$useBetterCombatAttackCheck(LivingEntity target) {
        return MobCombatHelper.canUseBetterCombatWeapon(this.mob, (m, wa) -> {
            AttackHand currentAttack = ((EntityPlayer_BetterCombat) m).getCurrentAttack();
            if (currentAttack != null) {

                // Combine the weapon math check with the point-blank physical collision check
                double inflation = m.isPassenger() ? 0.5 : 0.25;
                boolean isWithinRange = MobCombatHelper.isWithinAttackRange(m, target, currentAttack.attack())
                        || m.getBoundingBox().inflate(inflation).intersects(target.getBoundingBox());

                if (MobCombatHelper.isAttackReady(m) && isWithinRange) {
                    MobCombatHelper.setDelayedUpswing(m, () -> {
                        ((MobAttackWindup) m).bettermobcombat$startUpswing(wa);
                        this.bettermobcombat$setTicksUntilNextAttack(((MobAttackWindup) m).bettermobcombat$getAttackCooldown());
                        this.bettermobcombat$postBetterCombatAttack();
                    });
                }
                return true;
            }
            return false;
        });
    }

    @Unique
    protected void bettermobcombat$postBetterCombatAttack() {
    }

    @Inject(method = "checkAndPerformAttack", at = @At("HEAD"), cancellable = true)
    private void pre_checkAndPerformAttack(LivingEntity target, CallbackInfo ci) {
        if(this.bettermobcombat$useBetterCombatAttackCheck(target)){
            ci.cancel();
        }
    }

    @Unique
    protected void bettermobcombat$setTicksUntilNextAttack(int ticksUntilNextAttack){
        this.ticksUntilNextAttack = this.adjustedTickDelay(ticksUntilNextAttack);
    }
}