package com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman;

import com.Niko.EclipseAwakenedTweak.Event.BossIntroManager;
import com.benji.netherman.NetherExp;
import com.benji.netherman.config.AzazelConfig;
import com.benji.netherman.entity.AzazelHumanEntity;
import com.benji.netherman.entity.GildedGolemEntity;
import com.benji.netherman.entity.GuardianEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** Adds non-player targets and phase-two Nether attacks to NetherExp's human Azazel. */
public final class AzazelReworkHandler {
    private static final String PREFIX = "eclipseawakenedtweak.azazel_human.";
    private static final Field GOLEM_HEALING_PHASE_FIELD = findGolemHealingPhaseField();
    private static final Map<GildedGolemEntity, GolemHealthSnapshot> PENDING_GOLEM_HEALTH_RESTORE =
            new IdentityHashMap<>();
    private static final Set<ServerPlayer> PENDING_PRAEMIUM_RESTORE = new HashSet<>();
    private AzazelReworkHandler() {}

    private record GolemHealthSnapshot(float originalHealth, float temporaryHealth) {}

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

    /** Caps NetherExp's 100 HP heal during the defense animation to a visible, recoverable amount. */
    @SubscribeEvent
    public static void onAzazelHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof AzazelHumanEntity azazel
                && azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE) == 22) {
            event.setAmount(Math.min(event.getAmount(), azazel.getMaxHealth() * 0.10F));
        }
    }

    /** Starts the owner's one-minute golem cooldown when an owned gilded golem dies. */
    @SubscribeEvent
    public static void onOwnedGolemDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof GildedGolemEntity golem)
                || golem.level().isClientSide()
                || !(golem.level() instanceof ServerLevel level)) return;
        AzazelHumanEntity owner = findSummoner(golem);
        if (owner != null) {
            owner.getPersistentData().putLong(PREFIX + "golemSummonCooldownUntil",
                    level.getGameTime() + 1200L);
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
    public static void onAzazelManipulationAdded(MobEffectEvent.Added event) {
        if (event.getEffectInstance().getEffect() != NetherExp.MANIPULATION_EFFECT.get()) return;
        if (event.getEffectSource() instanceof AzazelHumanEntity) {
            event.getEntity().removeEffect(NetherExp.MANIPULATION_EFFECT.get());
        }
    }

    @SubscribeEvent
    public static void onPraemiumRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() != NetherExp.PRAEMIUM.get()
                || !(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide()) return;

        // Reapply after the removal operation finishes. Doing it inside Remove can be
        // overwritten by milk/totem's remove-all-effects loop.
        PENDING_PRAEMIUM_RESTORE.add(player);
    }

    @SubscribeEvent
    public static void onLivingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof GildedGolemEntity golem) {
            suppressAzazelGolemHealingSuck(golem);
        }
        if (event.getEntity() instanceof Mob mob && !(mob instanceof AzazelHumanEntity)) {
            updateAzazelSummonTarget(mob);
        }
        if (event.getEntity() instanceof AzazelHumanEntity azazel) {
            tickAzazel(azazel);
        }
    }

    private static void suppressAzazelGolemHealingSuck(GildedGolemEntity golem) {
        if (golem.level().isClientSide || !golem.isAlive() || GOLEM_HEALING_PHASE_FIELD == null) return;
        AzazelHumanEntity owner = findSummoner(golem);
        if (owner == null || golem.getHealth() / golem.getMaxHealth() > 0.25F) return;

        float originalHealth = golem.getHealth();
        float temporaryHealth = golem.getMaxHealth() * 0.251F;
        try {
            GOLEM_HEALING_PHASE_FIELD.setBoolean(golem, false);
            golem.setHealth(temporaryHealth);
            PENDING_GOLEM_HEALTH_RESTORE.put(golem,
                    new GolemHealthSnapshot(originalHealth, temporaryHealth));
        } catch (IllegalAccessException ignored) {
            // Keep the original NetherExp behavior if the implementation field is unavailable.
        }
    }

    @SubscribeEvent
    public static void restoreAzazelGolemHealth(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        if (!PENDING_GOLEM_HEALTH_RESTORE.isEmpty()) {
            for (Map.Entry<GildedGolemEntity, GolemHealthSnapshot> entry
                    : PENDING_GOLEM_HEALTH_RESTORE.entrySet()) {
                GildedGolemEntity golem = entry.getKey();
                if (!golem.isAlive() || golem.isRemoved()) continue;
                GolemHealthSnapshot snapshot = entry.getValue();
                float tickHealthChange = golem.getHealth() - snapshot.temporaryHealth();
                golem.setHealth(Math.max(0.0F, snapshot.originalHealth() + tickHealthChange));
            }
            PENDING_GOLEM_HEALTH_RESTORE.clear();
        }

        if (PENDING_PRAEMIUM_RESTORE.isEmpty()) return;
        for (ServerPlayer player : PENDING_PRAEMIUM_RESTORE) {
            if (!player.isAlive() || player.isRemoved()
                    || !(player.level() instanceof ServerLevel level)
                    || !hasLivingAzazel(level)) continue;
            if (!player.hasEffect(NetherExp.PRAEMIUM.get())) {
                player.addEffect(new MobEffectInstance(NetherExp.PRAEMIUM.get(), -1, 0,
                        false, false, true));
            }
        }
        PENDING_PRAEMIUM_RESTORE.clear();
    }

    private static boolean hasLivingAzazel(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AzazelHumanEntity azazel && azazel.isAlive()) return true;
        }
        return false;
    }

    private static Field findGolemHealingPhaseField() {
        try {
            Field field = GildedGolemEntity.class.getDeclaredField("isHealingPhase");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    /** Applies target selection, movement, and non-player attack hitboxes during the living tick. */
    public static void tickAzazel(Object entity) {
        if (!(entity instanceof AzazelHumanEntity azazel)) return;
        if (!(azazel.level() instanceof ServerLevel level) || !azazel.isAlive()) return;

        var data = azazel.getPersistentData();
        if (azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE) < 5) return;

        int state = azazel.getEntityData().get(AzazelHumanEntity.BOSS_STATE);
        String wasOnGroundKey = PREFIX + "wasOnGround";
        boolean wasOnGround = data.contains(wasOnGroundKey, net.minecraft.nbt.Tag.TAG_BYTE)
                ? data.getBoolean(wasOnGroundKey) : azazel.onGround();
        int previousAttackState = data.getInt(PREFIX + "attackState");
        if (state == 5 && previousAttackState != 5) {
            var entityTypeId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(azazel.getType());
            BossIntroManager.register(entityTypeId,
                    new BossIntroManager.Profile(BossIntroManager.Trigger.CUSTOM, 0xFF6B2C, 0xFFD080, false));
            BossIntroManager.trigger(azazel);
        }
        if (previousAttackState != state) {
            data.putInt(PREFIX + "attackState", state);
            data.putInt(PREFIX + "attackAge", 0);
            data.remove(PREFIX + "rushHitEntities");
            data.remove(PREFIX + "jumpImpactPillarsDone");
            if (state != 5) data.remove(PREFIX + "queuedRangeGoal");
            if (state == 32) {
                azazel.getEntityData().set(AzazelHumanEntity.BOSS_STATE, 5);
                state = 5;
            }
        } else {
            data.putInt(PREFIX + "attackAge", data.getInt(PREFIX + "attackAge") + 1);
        }

        long now = level.getGameTime();
        // Leave target selection, facing, movement, and attack choice to NetherExp's AI.
        LivingEntity target = azazel.getTarget();
        if (state == 12 || state == 13) {
            Vec3 travel = azazel.getDeltaMovement();
            if (travel.horizontalDistanceSqr() < 1.0E-6D && target != null) {
                travel = target.position().subtract(azazel.position());
            }
            if (travel.horizontalDistanceSqr() >= 1.0E-6D) {
                float travelYaw = (float) (Math.atan2(travel.z, travel.x) * (180.0D / Math.PI)) - 90.0F;
                azazel.setYRot(travelYaw);
                azazel.setYBodyRot(travelYaw);
                azazel.setYHeadRot(travelYaw);
            }
        }
        int attackAge = data.getInt(PREFIX + "attackAge");
        applyNonPlayerAttackHitboxes(azazel, target, state, attackAge);
        applyRushHit(azazel, state, data);
        if (state == 13 && !wasOnGround && azazel.onGround()
                && !data.getBoolean(PREFIX + "jumpImpactPillarsDone")) {
            AzazelSoulPillarBridge.spawnImpactRings(azazel);
            data.putBoolean(PREFIX + "jumpImpactPillarsDone", true);
        }
        data.putBoolean(wasOnGroundKey, azazel.onGround());

        // Fire each health milestone as soon as it is crossed. Do not defer these until an
        // idle animation: a later threshold must never consume an earlier threshold's event.
        data.remove(PREFIX + "seventyPercentMonolithAt");
        data.remove(PREFIX + "halfPhaseAt");
        data.remove(PREFIX + "thirtyPercentPhaseAt");
        if (azazel.getHealth() <= azazel.getMaxHealth() * 0.30F
                && !data.getBoolean(PREFIX + "thirtyPercentPhase")) {
            data.putBoolean(PREFIX + "seventyPercentMonolith", true);
            data.putBoolean(PREFIX + "halfPhase", true);
            data.putBoolean(PREFIX + "halfPhaseSpawned", true);
            data.putBoolean(PREFIX + "thirtyPercentPhase", true);
            AzazelGoetyBridge.prepareSpell(azazel, true);
            AzazelGoetyBridge.thirtyPercentPhase(azazel, target);
        } else if (azazel.getHealth() <= azazel.getMaxHealth() * 0.50F
                && !data.getBoolean(PREFIX + "halfPhase")) {
            data.putBoolean(PREFIX + "seventyPercentMonolith", true);
            data.putBoolean(PREFIX + "halfPhase", true);
            data.putBoolean(PREFIX + "halfPhaseSpawned", true);
            AzazelGoetyBridge.prepareSpell(azazel, true);
            AzazelGoetyBridge.halfPhase(azazel, target);
        } else if (azazel.getHealth() <= azazel.getMaxHealth() * 0.70F
                && !data.getBoolean(PREFIX + "seventyPercentMonolith")) {
            data.putBoolean(PREFIX + "seventyPercentMonolith", true);
            AzazelGoetyBridge.prepareSpell(azazel, true);
            AzazelGoetyBridge.seventyPercentPhase(azazel, target);
        }

        if (target != null && net.minecraftforge.fml.ModList.get().isLoaded("goety")) {
            boolean rushing = state == 12 || state == 13;
            int blastRemaining = data.getInt(PREFIX + "hellBlastRemaining");
            if (blastRemaining <= 0 && !rushing && now >= data.getLong(PREFIX + "nextGoetyHellBlast")) {
                boolean phaseThree = azazel.getHealth() <= azazel.getMaxHealth() * 0.30F;
                boolean phaseTwo = azazel.getHealth() <= azazel.getMaxHealth() * 0.50F;
                data.putInt(PREFIX + "hellBlastRemaining", phaseThree ? 5 : phaseTwo ? 3 : 1);
                data.putInt(PREFIX + "hellBlastShotsPerDirection", phaseThree ? 10 : 1);
                data.putInt(PREFIX + "hellBlastFired", 0);
                data.putLong(PREFIX + "nextGoetyHellBlast", now + 200L);
                data.putLong(PREFIX + "hellBlastNextShotAt", now + 25L);
                AzazelGoetyBridge.prepareSpell(azazel, false);
                blastRemaining = data.getInt(PREFIX + "hellBlastRemaining");
            }
            if (blastRemaining > 0 && !rushing && now >= data.getLong(PREFIX + "hellBlastNextShotAt")) {
                int shotsPerDirection = Math.max(1, data.getInt(PREFIX + "hellBlastShotsPerDirection"));
                int fired = data.getInt(PREFIX + "hellBlastFired");
                if (shotsPerDirection == 1) {
                    AzazelGoetyBridge.castGoetyHellBlast(azazel, target, 1);
                } else {
                    // One volley launches all three fan directions together; repeat ten volleys.
                    for (int direction = 0; direction < 3; direction++) {
                        AzazelGoetyBridge.castGoetyHellBlast(azazel, target, direction);
                    }
                }
                data.putInt(PREFIX + "hellBlastFired", fired + 1);
                data.putInt(PREFIX + "hellBlastRemaining", blastRemaining - 1);
                data.putLong(PREFIX + "hellBlastNextShotAt", now + (shotsPerDirection == 1 ? 5L : 2L));
            }

            if (data.getBoolean(PREFIX + "halfPhase")) {
                if (now >= data.getLong(PREFIX + "nextSummon")) {
                    data.putLong(PREFIX + "nextSummon", now + 600L);
                    data.putLong(PREFIX + "summonAt", now + 60L);
                    AzazelGoetyBridge.prepareSpell(azazel, true);
                }
                if (data.getLong(PREFIX + "summonAt") > 0L && now >= data.getLong(PREFIX + "summonAt")) {
                    data.putLong(PREFIX + "summonAt", 0L);
                    summonAttack(azazel, target);
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
                int damnedRemaining = data.getInt(PREFIX + "damnedRemaining");
                if (damnedRemaining <= 0 && azazel.distanceTo(target) > 10.0D
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
            AzazelSoulPillarBridge.spawnImpactRings(azazel);
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
        if (AzazelSummonTag.belongsTo(azazel, entity)) return true;
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

    static void summonAttack(AzazelHumanEntity azazel, LivingEntity target) {
        if (!(azazel.level() instanceof ServerLevel level)) return;
        boolean goetyLoaded = net.minecraftforge.fml.ModList.get().isLoaded("goety");
        int choice = azazel.getRandom().nextInt(goetyLoaded ? 3 : 1);
        if (goetyLoaded && choice == 2) {
            if (AzazelGoetyBridge.countOwnedMalghasts(azazel) >= 2) return;
            AzazelGoetyBridge.summonMalghast(azazel, target);
            return;
        }

        if (hasLivingOwnedGolem(azazel, level)
                || level.getGameTime() < azazel.getPersistentData().getLong(PREFIX + "golemSummonCooldownUntil")) return;
        GildedGolemEntity golem = NetherExp.GILDED_GOLEM.get().create(level);
        if (golem == null) return;
        golem.moveTo(azazel.getX() + 4.0D, azazel.getY(), azazel.getZ(), azazel.getYRot(), 0.0F);
        golem.setCreatorUUID(azazel.getUUID());
        AzazelSummonTag.mark(azazel, golem);
        golem.setTarget(target);
        level.addFreshEntity(golem);
    }

    private static boolean hasLivingOwnedGolem(AzazelHumanEntity azazel, ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof GildedGolemEntity golem && golem.isAlive()
                    && azazel.getUUID().equals(golem.getCreatorUUID())) return true;
        }
        return false;
    }

}
