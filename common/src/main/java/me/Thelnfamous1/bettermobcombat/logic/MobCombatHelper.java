package me.Thelnfamous1.bettermobcombat.logic;

import com.google.common.collect.Multimap;
import me.Thelnfamous1.bettermobcombat.BetterMobCombat;
import me.Thelnfamous1.bettermobcombat.Constants;
import me.Thelnfamous1.bettermobcombat.api.MobAttackRangeExtensions;
import me.Thelnfamous1.bettermobcombat.api.MobAttackWindup;
import me.Thelnfamous1.bettermobcombat.mixin.MobAccessor;
import me.Thelnfamous1.bettermobcombat.platform.Services;
import net.bettercombat.BetterCombatMod;
import net.bettercombat.api.AttackHand;
import net.bettercombat.api.WeaponAttributes;
import net.bettercombat.client.collision.OrientedBoundingBox;
import net.bettercombat.client.collision.WeaponHitBoxes;
import net.bettercombat.logic.*;
import net.bettercombat.logic.knockback.ConfigurableKnockback;
import net.bettercombat.mixin.player.LivingEntityAccessor;
import net.bettercombat.utils.AttributeModifierHelper;
import net.bettercombat.utils.MathHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static net.bettercombat.network.ServerNetwork.TEMPORARY_ATTACK;

public class MobCombatHelper {

    public static ItemStack getDirectMainhand(Mob mob){
        return ((MobAccessor)mob).bettermobcombat$getHandItems().get(EquipmentSlot.MAINHAND.getIndex());
    }

    public static ItemStack getDirectOffhand(Mob mob){
        return ((MobAccessor)mob).bettermobcombat$getHandItems().get(EquipmentSlot.OFFHAND.getIndex());
    }

    public static void onHoldingBetterCombatWeapon(Mob mob, BiConsumer<Mob, WeaponAttributes> callback) {
        if(BetterMobCombat.getServerConfigHelper().isBlacklistedForBetterCombat(mob)){
            return;
        }
        WeaponAttributes attributes = WeaponRegistry.getAttributes(mob.getMainHandItem());
        if (attributes != null && attributes.attacks() != null) {
            callback.accept(mob, attributes);
        }
    }

    public static boolean canUseBetterCombatWeapon(Mob mob, BiPredicate<Mob, WeaponAttributes> predicate) {
        if(BetterMobCombat.getServerConfigHelper().isBlacklistedForBetterCombat(mob)){
            return false;
        }
        WeaponAttributes attributes = WeaponRegistry.getAttributes(mob.getMainHandItem());
        if (attributes != null && attributes.attacks() != null) {
            return predicate.test(mob, attributes);
        }
        return false;
    }

    public static <T> T applyWithBetterCombatWeapon(Mob mob, BiFunction<Mob, WeaponAttributes, T> function, Supplier<T> defaultValue) {
        if(BetterMobCombat.getServerConfigHelper().isBlacklistedForBetterCombat(mob)){
            return defaultValue.get();
        }
        WeaponAttributes attributes = WeaponRegistry.getAttributes(mob.getMainHandItem());
        if (attributes != null && attributes.attacks() != null) {
            return function.apply(mob, attributes);
        }
        return defaultValue.get();
    }

    public static void processAttack(Level world, Mob mob, int comboCount, List<Entity> targets, @Nullable BiConsumer<Mob, Entity> damageApplicator){
        if (world != null && !world.isClientSide) {
            // Hit point-blank entities
            List<Entity> finalTargets = new java.util.ArrayList<>(targets);
            double inflationValue = mob.isPassenger() ? 0.5 : 0.25;
            net.minecraft.world.phys.AABB pointBlankZone = mob.getBoundingBox().inflate(inflationValue);
            List<Entity> pointBlankTargets = world.getEntities(mob, pointBlankZone, entity -> {
                if (!(entity instanceof LivingEntity) || entity == mob || finalTargets.contains(entity)) {
                    return false;
                }
                net.bettercombat.logic.TargetHelper.Relation relation = MobTargetHelper.getRelation(mob, entity);
                return net.bettercombat.logic.TargetHelper.isHitAllowed(true, relation);
            });

            finalTargets.addAll(pointBlankTargets);

            AttackHand hand = MobAttackHelper.getCurrentAttack(mob, comboCount);
            if (hand == null) {
                Constants.LOG.error("Server handling attack for {} - No current attack hand!", mob);
                Constants.LOG.error("Combo count: " + comboCount + " is dual wielding: " + MobAttackHelper.isDualWielding(mob));
                Constants.LOG.error("Main-hand stack: " + mob.getMainHandItem());
                Constants.LOG.error("Off-hand stack: " + mob.getOffhandItem());
            } else {
                WeaponAttributes.Attack attack = hand.attack();
                WeaponAttributes attributes = hand.attributes();
                world.getServer().executeIfPossible(() -> {
                    double damageBaseMultiplier = 0.0;
                    ((PlayerAttackProperties)mob).setComboCount(comboCount);
                    boolean isDualWielding = MobAttackHelper.isDualWielding(mob);
                    float knockbackMultiplier;
                    if (attributes != null && attack != null) {
                        double comboMultiplier = attack.damageMultiplier() - 1.0;
                        damageBaseMultiplier += comboMultiplier;
                        knockbackMultiplier = MobAttackHelper.getDualWieldingAttackDamageMultiplier(mob, hand) - 1.0F;
                        damageBaseMultiplier += knockbackMultiplier;
                        MobSoundHelper.playSound((ServerLevel)world, mob, attack.swingSound());

                        if (hand.isOffHand()) {
                            MobAttackHelper.setAttributesForOffHandAttack(mob, true);
                        }

                        // Adjusted to use 'finalTargets' for sweeping calculations
                        if (BetterCombatMod.config.allow_reworked_sweeping && finalTargets.size() > 1) {
                            double multiplier = - (double)(BetterCombatMod.config.reworked_sweeping_maximum_damage_penalty / (float)BetterCombatMod.config.reworked_sweeping_extra_target_count * (float)Math.min(BetterCombatMod.config.reworked_sweeping_extra_target_count, finalTargets.size() - 1));
                            double sweepRatio = MobAttackHelper.getSweepRatio(mob);
                            damageBaseMultiplier += multiplier + (double)BetterCombatMod.config.reworked_sweeping_maximum_damage_penalty * sweepRatio;
                            boolean playEffects = !BetterCombatMod.config.reworked_sweeping_sound_and_particles_only_for_swords || hand.itemStack().getItem() instanceof SwordItem;
                            if (BetterCombatMod.config.reworked_sweeping_plays_sound && playEffects) {
                                world.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, mob.getSoundSource(), 1.0F, 1.0F);
                            }

                            if (BetterCombatMod.config.reworked_sweeping_emits_particles && playEffects) {
                                sweepAttack(mob);
                            }
                        }
                    }

                    Multimap<Holder<Attribute>, AttributeModifier> damageModifier = null;
                    if (damageBaseMultiplier != 0.0) {
                        AttributeModifierHelper.fromModifier(Attributes.ATTACK_DAMAGE, null);
                        damageModifier = AttributeModifierHelper.fromModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(TEMPORARY_ATTACK, damageBaseMultiplier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
                        mob.getAttributes().addTransientAttributeModifiers(damageModifier);
                    }

                    float attackCooldown = MobAttackHelper.getAttackCooldownTicksCapped(mob);
                    knockbackMultiplier = 1;

                    if (BetterCombatMod.config.knockback_reduced_for_fast_attacks) {
                        knockbackMultiplier = MathHelper.clamp(attackCooldown / BetterCombatMod.config.knockback_reduction_threshold, 0.1F, 1.0F);
                        switch (BetterCombatMod.config.knockback_reduction_curve) {
                            case SQUARE -> knockbackMultiplier *= knockbackMultiplier;
                            case HALF_SQUARE -> knockbackMultiplier = (knockbackMultiplier * knockbackMultiplier + knockbackMultiplier) * 0.5F;
                        }
                    }

                    int lastAttackedTicks = Services.PLATFORM.getAttackStrengthTicker(mob);

                    // Adjusted loop to process our expanded 'finalTargets' list
                    for(int sweepingLevel = 0; sweepingLevel < finalTargets.size(); ++sweepingLevel) {
                        Entity target = finalTargets.get(sweepingLevel);

                        if (target != null && (!target.equals(mob.getVehicle()) || TargetHelper.isAttackableMount(target)) && (!(target instanceof ArmorStand) || !((ArmorStand)target).isMarker())) {
                            LivingEntity livingTarget = target instanceof LivingEntity ? (LivingEntity) target : null;
                            if (livingTarget != null) {
                                if (BetterCombatMod.config.allow_fast_attacks) {
                                    livingTarget.invulnerableTime = 0;
                                }

                                if (knockbackMultiplier != 1.0F) {
                                    ((ConfigurableKnockback)livingTarget).setKnockbackMultiplier_BetterCombat(knockbackMultiplier);
                                }
                            }

                            ((LivingEntityAccessor)mob).setLastAttackedTicks(lastAttackedTicks);
                            if (target instanceof ItemEntity || target instanceof ExperienceOrb || target instanceof AbstractArrow || target == mob) {
                                Constants.LOG.error("{} tried to attack an invalid entity - {}", mob.getName().getString(), target);
                                return;
                            }

                            if(damageApplicator != null){
                                damageApplicator.accept(mob, target);
                            } else{
                                mob.doHurtTarget(target);
                            }

                            if (livingTarget != null) {
                                if (knockbackMultiplier != 1.0F) {
                                    ((ConfigurableKnockback)livingTarget).setKnockbackMultiplier_BetterCombat(1.0F);
                                }
                            }
                        }
                    }

                    mob.setNoActionTime(0);

                    if (damageModifier != null) {
                        mob.getAttributes().removeAttributeModifiers(damageModifier);
                    }
                });
            }
        }
    }

    public static void sweepAttack(Mob mob) {
        double xOffset = -Mth.sin(mob.getYRot() * Mth.DEG_TO_RAD);
        double zOffset = Mth.cos(mob.getYRot() * Mth.DEG_TO_RAD);
        if (mob.level() instanceof ServerLevel) {
            ((ServerLevel)mob.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX() + xOffset, mob.getY(0.5D), mob.getZ() + zOffset, 0, xOffset, 0.0D, zOffset, 0.0D);
        }

    }

    private static double getRangeWithItem(ItemStack stack, double interactionRangeValue) {
        if (EntityAttributeHelper.itemHasRangeAttribute(stack)) {
            return interactionRangeValue;
        } else {
            WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
            return PlayerAttackHelper.combineAttackRange(attributes, interactionRangeValue);
        }
    }

    public static double getRangeForItem(Mob mob, ItemStack stack) {
        double interactionRangeValue = mob.getAttributes().hasAttribute(Attributes.ENTITY_INTERACTION_RANGE) ?
                mob.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) : 3;
        return getRangeWithItem(stack, interactionRangeValue);
    }

    public static double getRangeWithWeapon(Mob mob, double interactionRangeValue) {
        return getRangeWithItem(mob.getMainHandItem(), interactionRangeValue);
    }

    public static double calculateAttributeValue(Holder<Attribute> attribute, Collection<AttributeModifier> modifiers) {
        double sumValue = attribute.value().getDefaultValue();

        for(AttributeModifier additive : modifiers.stream().filter(mod -> mod.operation() == AttributeModifier.Operation.ADD_VALUE).toList()) {
            sumValue += additive.amount();
        }

        double productValue = sumValue;

        for(AttributeModifier baseMultiplicative : modifiers.stream().filter(mod -> mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE).toList()) {
            productValue += sumValue * baseMultiplicative.amount();
        }

        for(AttributeModifier totalMultiplicative : modifiers.stream().filter(mod -> mod.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL).toList()) {
            productValue *= 1.0D + totalMultiplicative.amount();
        }

        return attribute.value().sanitizeValue(productValue);
    }

    public static boolean isWithinAttackRange(Mob mob, Entity target, WeaponAttributes.Attack attack) {
        double attackRange = getRangeForItem(mob,mob.getMainHandItem());
        return isWithinAttackRange(mob,target,attack,attackRange);
    }


    private static boolean isWithinAttackRange(LivingEntity mob, Entity target, WeaponAttributes.Attack attack, double attackRange) {
        Vec3 origin = MobTargetFinder.getInitialTracingPoint(mob);
        if (!MobAttackRangeExtensions.sources().isEmpty()) {
            attackRange = MobTargetFinder.applyAttackRangeModifiers(mob, attackRange);
        }
        // scale the attack range by the config multiplier, as this method is used for starting attacks, not damage application
        attackRange *= BetterMobCombat.getServerConfig().mob_begin_attack_range_multiplier;

        boolean isSpinAttack = attack.angle() > 180.0;
        Vec3 size = WeaponHitBoxes.createHitbox(attack.hitbox(), attackRange, isSpinAttack);
        OrientedBoundingBox obb = new OrientedBoundingBox(origin, size, mob.getXRot(), mob.getYRot());
        if (!isSpinAttack) {
            obb = obb.offsetAlongAxisZ(size.z / 2.0);
        }

        obb.updateVertex();
        boolean test = new MobTargetFinder.CollisionFilter(obb)
                .and(new MobTargetFinder.RadialFilter(origin, obb.axisZ, attackRange, attack.angle()))
                .test(target, mob);
        return test;
    }

    public static boolean isAttackReady(Mob mob) {
        return !((MobAttackWindup) mob).bettermobcombat$hasDelayedUpswing() && ((MobAttackWindup) mob).bettermobcombat$getAttackCooldown() <= 0;
    }

    public static void setDelayedUpswing(Mob mob, Runnable runnable){
        ((MobAttackWindup)mob).bettermobcombat$setDelayedUpswing(runnable);
    }
}
