package com.Niko.EclipseAwakenedTweak.compatibility.netherexp;

import com.benji.netherman.NetherExp;
import com.benji.netherman.config.AzazelConfig;
import com.benji.netherman.entity.AzazelHumanEntity;
import com.benji.netherman.entity.GildedGolemEntity;
import com.benji.netherman.entity.GuardianEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;

import java.lang.reflect.Field;

/** Adds non-player targets and phase-two Nether attacks to NetherExp's human Azazel. */
public final class AzazelReworkHandler {
    private static final String PREFIX = "eclipseawakenedtweak.azazel_human.";
    private static final Field LONG_RANGE_NEEDLE_PATTERN_FIELD = findLongRangeNeedlePatternField();

    private AzazelReworkHandler() {}

    @SubscribeEvent
    public static void onAzazelAttacked(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof AzazelHumanEntity azazel && AzazelGoetyBridge.hasOwnedObsidianMonolith(azazel)) {
            event.setCanceled(true);
            return;
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker == null && event.getSource().getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        if (attacker instanceof LivingEntity livingAttacker
                && areBothInAzazelSquad(livingAttacker, victim)) {
            event.setCanceled(true);
            return;
        }
        if (attacker != null) {
            AzazelHumanEntity owner = victim instanceof AzazelHumanEntity azazel ? azazel : findSummoner(victim);
            if (owner != null && AzazelGoetyBridge.isOwnedByAzazel(owner, attacker)) {
                event.setCanceled(true);
                return;
            }
        }

        // AzazelSpikeEntity uses unattributed magic damage, so identify its source by its tiny hitbox.
        if (attacker == null && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            for (com.benji.netherman.entity.AzazelSpikeEntity spike : victim.level().getEntitiesOfClass(
                    com.benji.netherman.entity.AzazelSpikeEntity.class,
                    victim.getBoundingBox().inflate(0.25D))) {
                if (spike.getOwner() instanceof AzazelHumanEntity owner && isAzazelSummon(owner, victim)) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }

    /** Handles NetherExp's player-only spike projectile hit for Azazel's current mob target. */
    public static boolean handleNonPlayerSpikeImpact(Object projectile, Object hitEntity) {
        if (!(projectile instanceof com.benji.netherman.entity.AzazelSpikesProjectileEntity spike)
                || !(hitEntity instanceof LivingEntity target)
                || !(spike.getOwner() instanceof AzazelHumanEntity azazel)
                || target instanceof Player || target instanceof AzazelHumanEntity
                || target != azazel.getTarget() || isAzazelSummon(azazel, target)
                || target.isInvulnerable() || target.level().isClientSide()) return false;

        target.hurt(spike.damageSources().mobProjectile(spike, azazel),
                AzazelConfig.HUMAN_PROJECTILE_SPIKE_DAMAGE.get().floatValue());
        int witherDuration = AzazelConfig.HUMAN_PROJECTILE_SPIKE_WITHER_DURATION.get();
        if (witherDuration > 0) target.addEffect(new MobEffectInstance(MobEffects.WITHER, witherDuration, 1));
        spike.discard();
        return true;
    }

    @SubscribeEvent
    public static void onAzazelSpikeImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit
                && handleNonPlayerSpikeImpact(event.getProjectile(), hit.getEntity())) {
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onBreathDebuffApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) return;
        var effect = event.getEffectInstance().getEffect();
        if (effect != MobEffects.WITHER && effect != MobEffects.DARKNESS
                && effect != NetherExp.MANIPULATION_EFFECT.get()) return;

        for (AzazelHumanEntity azazel : player.level().getEntitiesOfClass(AzazelHumanEntity.class,
                player.getBoundingBox().inflate(20.0D), boss -> boss.isAlive()
                        && boss.getEntityData().get(AzazelHumanEntity.BOSS_STATE) == 32
                        && boss.getTarget() != player)) {
            event.setResult(Event.Result.DENY);
            return;
        }
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob && !(mob instanceof AzazelHumanEntity)) {
            updateAzazelSummonTarget(mob);
        }
        if (event.getEntity() instanceof AzazelHumanEntity azazel) {
            tickAzazel(azazel);
        }
    }

    /** Applies target selection, movement, and non-player attack hitboxes during the living tick. */
    public static void tickAzazel(Object entity) {
        if (!(entity instanceof AzazelHumanEntity azazel)) return;
        if (!(azazel.level() instanceof ServerLevel level) || !azazel.isAlive()) return;

        var data = azazel.getPersistentData();
        if (azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE) < 5) return;

        Entity monolith = AzazelGoetyBridge.getOwnedObsidianMonolith(azazel);
        int state = azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE);
        if (monolith != null && (state == 12 || state == 13)) {
            // NetherExp states 12 and 13 are the run/jump attack rush sequence.
            // Cancel it while the monolith arena is active so Azazel stays tethered to the monolith.
            azazel.getEntityData().set(AzazelHumanEntity.BOSS_STATE, 5);
            Vec3 velocity = azazel.getDeltaMovement();
            azazel.setDeltaMovement(0.0D, velocity.y, 0.0D);
            state = 5;
        }
        int previousAttackState = data.getInt(PREFIX + "attackState");
        if (previousAttackState != state) {
            data.putInt(PREFIX + "attackState", state);
            data.putInt(PREFIX + "attackAge", 0);
            data.remove(PREFIX + "rushHitEntities");
            if (state != 5) data.remove(PREFIX + "queuedRangeGoal");
        } else {
            data.putInt(PREFIX + "attackAge", data.getInt(PREFIX + "attackAge") + 1);
        }

        long now = level.getGameTime();
        LivingEntity target = acquireTarget(azazel, monolith);
        if (target != null) {
            // Keep the boss oriented toward its active target even during attack animations.
            azazel.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (monolith instanceof LivingEntity livingMonolith && azazel.distanceToSqr(monolith) > 14.0D * 14.0D) {
            azazel.setTarget(null);
            azazel.getNavigation().moveTo(livingMonolith, 1.15D);
            target = null;
        } else if (target != null && canPursueDuringState(state)
                && (monolith == null || azazel.distanceToSqr(monolith) <= 14.0D * 14.0D)
                && azazel.distanceToSqr(target) > 4.0D * 4.0D
                && now >= data.getLong(PREFIX + "nextPursuitPath")) {
            // Keep closing during ranged attack animations, with faster pursuit at long range.
            azazel.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = azazel.distanceTo(target);
            boolean phaseTwo = azazel.getEntityData().get(AzazelHumanEntity.IS_PHASE_2);
            double speed = distance > 20.0D ? 1.65D : distance > 10.0D ? 1.45D : 1.25D;
            if (phaseTwo) speed += 0.1D;
            azazel.getNavigation().moveTo(target, speed);
            data.putLong(PREFIX + "nextPursuitPath", now + 5L);
        }
        suppressFarNeedleAttacks(azazel, target);
        queueRangeBasedAttack(azazel, target, state, now, data);
        int attackAge = data.getInt(PREFIX + "attackAge");
        applyNonPlayerAttackHitboxes(azazel, target, state, attackAge);
        applyRushHit(azazel, state, data);

        if (azazel.getHealth() <= azazel.getMaxHealth() * 0.5F
                && !data.getBoolean(PREFIX + "halfPhase")) {
            data.putBoolean(PREFIX + "halfPhase", true);
            data.putLong(PREFIX + "halfPhaseAt", now + 40L);
            AzazelGoetyBridge.prepareSpell(azazel, true);
        }

        if (data.getLong(PREFIX + "halfPhaseAt") > 0L && now >= data.getLong(PREFIX + "halfPhaseAt")) {
            data.putLong(PREFIX + "halfPhaseAt", 0L);
            AzazelGoetyBridge.halfPhase(azazel, target);
        }

        if (target == null || !data.getBoolean(PREFIX + "halfPhase")) return;
        if (now >= data.getLong(PREFIX + "nextSummon")) {
            data.putLong(PREFIX + "nextSummon", now + 600L);
            data.putLong(PREFIX + "summonAt", now + 60L);
            AzazelGoetyBridge.prepareSpell(azazel, true);
        }
        if (data.getLong(PREFIX + "summonAt") > 0L && now >= data.getLong(PREFIX + "summonAt")) {
            data.putLong(PREFIX + "summonAt", 0L);
            summonAttack(azazel, level, target);
        }
        if (now >= data.getLong(PREFIX + "nextNetherSpell")) {
            data.putLong(PREFIX + "nextNetherSpell", now + 240L);
            data.putLong(PREFIX + "netherSpellAt", now + 30L);
            AzazelGoetyBridge.prepareSpell(azazel, false);
        }
        if (data.getLong(PREFIX + "netherSpellAt") > 0L && now >= data.getLong(PREFIX + "netherSpellAt")) {
            data.putLong(PREFIX + "netherSpellAt", 0L);
            AzazelGoetyBridge.castNetherSpell(azazel, target);
        }

        if (net.minecraftforge.fml.ModList.get().isLoaded("goety")) {
            int damnedRemaining = data.getInt(PREFIX + "damnedRemaining");
            boolean farTarget = azazel.distanceTo(target) > 10.0D;
            if (damnedRemaining <= 0 && farTarget
                    && now >= data.getLong(PREFIX + "nextDamnedWave")) {
                data.putInt(PREFIX + "damnedRemaining", 4);
                data.putLong(PREFIX + "damnedSpawnAt", now + 60L);
                data.putLong(PREFIX + "nextDamnedWave", now + 1200L);
                AzazelGoetyBridge.prepareSpell(azazel, true);
            }
            if (damnedRemaining > 0 && now >= data.getLong(PREFIX + "damnedSpawnAt")) {
                AzazelGoetyBridge.summonDamned(azazel, target);
                data.putInt(PREFIX + "damnedRemaining", damnedRemaining - 1);
                data.putLong(PREFIX + "damnedSpawnAt", now + 10L);
            }
        }
    }

    private static LivingEntity acquireTarget(AzazelHumanEntity azazel, Entity monolith) {
        LivingEntity current = azazel.getTarget();
        boolean currentValid = isValidTarget(azazel, current) && isWithinMonolithArena(current, monolith);
        if (currentValid && azazel.tickCount % 20 != 0) return current;

        net.minecraft.world.phys.AABB search = monolith == null
                ? azazel.getBoundingBox().inflate(128.0D)
                : monolith.getBoundingBox().inflate(14.0D);
        double range = monolith == null ? 128.0D : 14.0D;
        LivingEntity target = azazel.level().getNearestEntity(
                azazel.level().getEntitiesOfClass(LivingEntity.class, search,
                        candidate -> isValidTarget(azazel, candidate) && isWithinMonolithArena(candidate, monolith)),
                TargetingConditions.forCombat().range(range), azazel,
                monolith == null ? azazel.getX() : monolith.getX(),
                monolith == null ? azazel.getY() : monolith.getY(),
                monolith == null ? azazel.getZ() : monolith.getZ());
        if (currentValid && (target == null
                || azazel.distanceToSqr(target) >= azazel.distanceToSqr(current) * 0.64D)) {
            return current;
        }
        azazel.setTarget(target);
        return target;
    }

    private static boolean isWithinMonolithArena(LivingEntity target, Entity monolith) {
        return monolith == null || target.distanceToSqr(monolith) <= 14.0D * 14.0D;
    }

    private static boolean canPursueDuringState(int state) {
        // Ranged attacks remain stationary; the idle state prioritizes closing the distance.
        return state == 5;
    }

    private static void queueRangeBasedAttack(AzazelHumanEntity azazel, LivingEntity target, int state,
                                               long now, net.minecraft.nbt.CompoundTag data) {
        if (state != 5 || target == null || !isValidTarget(azazel, target)) return;
        if (azazel.forcedAttackGoal == 4 || azazel.forcedAttackGoal == 5) return;

        double distance = azazel.distanceTo(target);
        if (distance > 10.0D) {
            // Beyond attack range, discard queued offense and let the movement goal close in.
            if (azazel.forcedAttackGoal >= 1 && azazel.forcedAttackGoal <= 3) {
                azazel.forcedAttackGoal = 0;
            }
            data.remove(PREFIX + "queuedRangeGoal");
            return;
        }

        int rangeGoal = distance < 4.0D
                ? (azazel.getRandom().nextFloat() < 0.3F ? 3 : 1)
                : 2;
        boolean attackReady = now >= data.getLong(PREFIX + "nextSmartAttack");
        int queuedGoal = azazel.forcedAttackGoal;
        long attackCooldown = azazel.getEntityData().get(AzazelHumanEntity.IS_PHASE_2) ? 22L : 35L;

        // Pick a new attack only when idle and off cooldown. Once NetherExp has queued an
        // attack, keep the selected goal stable until it starts instead of rewriting it every tick.
        if (queuedGoal == 0) {
            if (!attackReady) return;
            azazel.forcedAttackGoal = rangeGoal;
            data.putInt(PREFIX + "queuedRangeGoal", rangeGoal);
            data.putLong(PREFIX + "nextSmartAttack", now + attackCooldown);
        } else if (queuedGoal >= 1 && queuedGoal <= 3) {
            // NetherExp may queue a random range goal itself; lock it to the range at queue time.
            int lockedGoal = data.getInt(PREFIX + "queuedRangeGoal");
            if (lockedGoal == 0) {
                lockedGoal = rangeGoal;
                data.putInt(PREFIX + "queuedRangeGoal", lockedGoal);
            }
            azazel.forcedAttackGoal = lockedGoal;
        }
    }

    private static void suppressFarNeedleAttacks(AzazelHumanEntity azazel, LivingEntity target) {
        if (target == null || azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE) != 51
                || LONG_RANGE_NEEDLE_PATTERN_FIELD == null) return;
        int desiredPattern = azazel.distanceTo(target) <= 4.0D ? 1 : 3;
        for (var wrappedGoal : azazel.goalSelector.getAvailableGoals()) {
            if (!wrappedGoal.getGoal().getClass().getName().equals(
                    "com.benji.netherman.entity.AzazelHumanLongRangeGoal")) continue;
            try {
                // Patterns 1/2 rotate the needles around Azazel; pattern 3 is target-centered.
                LONG_RANGE_NEEDLE_PATTERN_FIELD.setInt(wrappedGoal.getGoal(), desiredPattern);
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }

    private static Field findLongRangeNeedlePatternField() {
        try {
            Class<?> goalClass = Class.forName("com.benji.netherman.entity.AzazelHumanLongRangeGoal");
            Field field = goalClass.getDeclaredField("spikePattern");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isValidTarget(AzazelHumanEntity azazel, LivingEntity candidate) {
        return candidate != null && candidate != azazel
                && candidate.isAlive() && candidate.isAttackable()
                && !azazel.isAlliedTo(candidate) && !isAzazelSummon(azazel, candidate);
    }

    /** Fills the Player-only melee hitboxes in NetherExp's current human-boss goals for other mobs. */
    private static void applyNonPlayerAttackHitboxes(AzazelHumanEntity azazel, LivingEntity currentTarget,
                                                     int state, int age) {
        if (!(azazel.level() instanceof ServerLevel)) return;

        if (state == 20 && age == 14) {
            float damage = AzazelConfig.HUMAN_STOMP_DAMAGE.get().floatValue();
            double knockback = AzazelConfig.HUMAN_STOMP_KNOCKBACK.get();
            for (LivingEntity target : nearbyNonPlayerTargets(azazel, 15.0D)) {
                Vec3 push = target.position().subtract(azazel.position()).normalize();
                target.setDeltaMovement(push.x * knockback, knockback * 1.5D, push.z * knockback);
                target.hurt(azazel.damageSources().mobAttack(azazel), damage);
                target.hurtMarked = true;
            }
        } else if (state == 21 && (age == 4 || age == 14)) {
            boolean firstSwing = age == 4;
            hitNonPlayersInFront(azazel,
                    firstSwing ? AzazelConfig.HUMAN_SCYTHE_1_DAMAGE.get().floatValue()
                            : AzazelConfig.HUMAN_SCYTHE_2_DAMAGE.get().floatValue(),
                    firstSwing ? AzazelConfig.HUMAN_SCYTHE_1_KNOCKBACK.get()
                            : AzazelConfig.HUMAN_SCYTHE_2_KNOCKBACK.get(), false, -0.2D);
        } else if (state == 30 && age == 7) {
            boolean melee = currentTarget != null && azazel.distanceToSqr(currentTarget)
                    <= Math.pow(AzazelConfig.HUMAN_MELEE_ATTACK_RADIUS.get(), 2.0D);
            hitNonPlayersInFront(azazel,
                    melee ? AzazelConfig.HUMAN_SPEAR_MELEE_DAMAGE.get().floatValue()
                            : AzazelConfig.HUMAN_SPEAR_MID_PUSH_DAMAGE.get().floatValue(),
                    melee ? AzazelConfig.HUMAN_SPEAR_MELEE_KNOCKBACK.get()
                            : AzazelConfig.HUMAN_SPEAR_MID_PUSH_KNOCKBACK.get(), false, 0.0D);
        } else if (state == 31 && age == 9) {
            hitNonPlayersInFront(azazel, AzazelConfig.HUMAN_SPEAR_MID_PULL_DAMAGE.get().floatValue(),
                    AzazelConfig.HUMAN_SPEAR_MID_PULL_KNOCKBACK.get(), true, 0.0D);
        } else if (state == 32 && age == 39) {
            if (currentTarget != null && currentTarget.isAlive()
                    && azazel.distanceToSqr(currentTarget) <= 20.0D * 20.0D) {
                currentTarget.addEffect(new MobEffectInstance(MobEffects.WITHER, 600, 1));
                currentTarget.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 600, 0));
                currentTarget.addEffect(new MobEffectInstance(NetherExp.MANIPULATION_EFFECT.get(), 400, 0));
            }
        }
    }

    private static java.util.List<LivingEntity> nearbyNonPlayerTargets(AzazelHumanEntity azazel, double range) {
        return azazel.level().getEntitiesOfClass(LivingEntity.class, azazel.getBoundingBox().inflate(range),
                target -> !(target instanceof Player) && isValidTarget(azazel, target));
    }

    private static void hitNonPlayersInFront(AzazelHumanEntity azazel, float damage, double force,
                                              boolean pull, double minimumDot) {
        Vec3 look = azazel.getLookAngle();
        Vec3 facing = new Vec3(look.x, 0.0D, look.z).normalize();
        for (LivingEntity target : nearbyNonPlayerTargets(azazel, 12.0D)) {
            Vec3 offset = target.position().subtract(azazel.position());
            Vec3 flatOffset = new Vec3(offset.x, 0.0D, offset.z).normalize();
            if (flatOffset.lengthSqr() > 0.01D && facing.dot(flatOffset) <= minimumDot) continue;

            target.hurt(azazel.damageSources().mobAttack(azazel), damage);
            if (pull) {
                target.setDeltaMovement(target.getDeltaMovement().add(-flatOffset.x * force,
                        0.3D, -flatOffset.z * force));
            } else {
                target.setDeltaMovement(target.getDeltaMovement().add(flatOffset.x * force,
                        0.5D, flatOffset.z * force));
            }
            target.hurtMarked = true;
        }
    }

    private static void applyRushHit(AzazelHumanEntity azazel, int state, net.minecraft.nbt.CompoundTag data) {
        if (state != 13) return;
        String hitKey = PREFIX + "rushHitEntities";
        ListTag hitEntities = data.getList(hitKey, Tag.TAG_STRING);
        for (LivingEntity target : azazel.level().getEntitiesOfClass(LivingEntity.class,
                azazel.getBoundingBox().inflate(1.0D), candidate -> !(candidate instanceof Player)
                        && isValidTarget(azazel, candidate)
                        && azazel.getBoundingBox().inflate(1.0D).intersects(candidate.getBoundingBox()))) {
            String targetId = target.getStringUUID();
            boolean alreadyHit = false;
            for (int i = 0; i < hitEntities.size(); i++) {
                if (hitEntities.getString(i).equals(targetId)) {
                    alreadyHit = true;
                    break;
                }
            }
            if (alreadyHit) continue;

            hitEntities.add(StringTag.valueOf(targetId));
            target.hurt(azazel.damageSources().mobAttack(azazel), AzazelConfig.HUMAN_STOMP_DAMAGE.get().floatValue());
            Vec3 push = target.position().subtract(azazel.position()).normalize();
            double knockback = AzazelConfig.HUMAN_STOMP_KNOCKBACK.get();
            target.setDeltaMovement(push.x * knockback, Math.max(0.25D, knockback), push.z * knockback);
            target.hurtMarked = true;
        }
        data.put(hitKey, hitEntities);
    }

    private static void updateAzazelSummonTarget(Mob summon) {
        AzazelHumanEntity owner = findSummoner(summon);
        if (owner == null) return;
        if (summon instanceof GildedGolemEntity || summon instanceof GuardianEntity) {
            teleportBackToOwnerIfNeeded(summon, owner);
        }
        LivingEntity current = summon.getTarget();
        if (!isValidTarget(owner, current)) {
            LivingEntity target = owner.getTarget();
            summon.setTarget(isValidTarget(owner, target) ? target : null);
        }
    }

    private static void teleportBackToOwnerIfNeeded(Mob summon, AzazelHumanEntity owner) {
        if (summon.distanceToSqr(owner) <= 12.0D * 12.0D) return;
        String cooldownKey = PREFIX + "summonTeleportCooldown";
        int cooldown = summon.getPersistentData().getInt(cooldownKey);
        if (cooldown > 0) {
            summon.getPersistentData().putInt(cooldownKey, cooldown - 1);
            return;
        }

        summon.getPersistentData().putInt(cooldownKey, 20);
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = summon.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = 2.5D + summon.getRandom().nextDouble() * 2.0D;
            double x = owner.getX() + Math.cos(angle) * distance;
            double z = owner.getZ() + Math.sin(angle) * distance;
            if (summon.randomTeleport(x, owner.getY(), z, true)) {
                summon.setDeltaMovement(Vec3.ZERO);
                summon.setTarget(isValidTarget(owner, owner.getTarget()) ? owner.getTarget() : null);
                break;
            }
        }
    }

    private static AzazelHumanEntity findSummoner(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return null;
        if (!(entity instanceof GildedGolemEntity) && !(entity instanceof GuardianEntity)
                && !AzazelGoetyBridge.isGoetyOwnedEntity(entity)) return null;
        return level.getEntitiesOfClass(AzazelHumanEntity.class, entity.getBoundingBox().inflate(128.0D),
                azazel -> isAzazelSummon(azazel, entity)).stream().findFirst().orElse(null);
    }

    private static boolean isAzazelSummon(AzazelHumanEntity azazel, Entity entity) {
        if (entity == azazel) return true;
        if (entity instanceof GildedGolemEntity golem && azazel.getUUID().equals(golem.getCreatorUUID())) return true;
        if (entity instanceof GuardianEntity guardian && azazel.getUUID().equals(guardian.getOwnerUUID())) return true;
        return AzazelGoetyBridge.isOwnedByAzazel(azazel, entity);
    }

    private static boolean areBothInAzazelSquad(LivingEntity first, LivingEntity second) {
        if (first instanceof AzazelHumanEntity azazel) return isAzazelSummon(azazel, second);
        if (second instanceof AzazelHumanEntity azazel) return isAzazelSummon(azazel, first);
        AzazelHumanEntity owner = findSummoner(first);
        return owner != null && isAzazelSummon(owner, second);
    }

    private static void summonAttack(AzazelHumanEntity azazel, ServerLevel level, LivingEntity target) {
        boolean goetyLoaded = net.minecraftforge.fml.ModList.get().isLoaded("goety");
        int choice = azazel.getRandom().nextInt(goetyLoaded ? 3 : 2);
        if (goetyLoaded && choice == 0) {
            AzazelGoetyBridge.summonNetherServant(azazel, target, 0);
            return;
        }
        if (goetyLoaded) choice--;

        if (choice == 0) {
            GildedGolemEntity golem = NetherExp.GILDED_GOLEM.get().create(level);
            if (golem == null) return;
            golem.moveTo(azazel.getX() + 4.0D, azazel.getY(), azazel.getZ(), azazel.getYRot(), 0.0F);
            golem.setCreatorUUID(azazel.getUUID());
            golem.setTarget(target);
            level.addFreshEntity(golem);
        } else {
            GuardianEntity guardian = NetherExp.GUARDIAN.get().create(level);
            if (guardian == null) return;
            guardian.moveTo(azazel.getX() - 4.0D, azazel.getY(), azazel.getZ(), azazel.getYRot(), 0.0F);
            guardian.setOwnerUUID(azazel.getUUID());
            guardian.setTarget(target);
            guardian.startSpawning();
            level.addFreshEntity(guardian);
        }
    }
}
