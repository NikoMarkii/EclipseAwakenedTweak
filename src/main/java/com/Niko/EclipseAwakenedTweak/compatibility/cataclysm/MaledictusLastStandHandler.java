package com.Niko.EclipseAwakenedTweak.compatibility.cataclysm;

import com.github.L_Ender.cataclysm.entity.InternalAnimationMonster.IABossMonsters.Maledictus.Maledictus_Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/** Server-side last stand behavior for Cataclysm's Maledictus. */
public final class MaledictusLastStandHandler {
    private static final String ACTIVE_KEY = "eclipseawakenedtweak_maledictus_last_stand";
    private static final String START_TICK_KEY = "eclipseawakenedtweak_maledictus_last_stand_start";
    private static final String EXPIRED_KEY = "eclipseawakenedtweak_maledictus_last_stand_expired";
    private static final String OPENING_SMASH_DONE_KEY = "eclipseawakenedtweak_maledictus_opening_smash_done";
    private static final String TIMER_STARTED_KEY = "eclipseawakenedtweak_maledictus_last_stand_timer_started";
    private static final String HUD_SECOND_KEY = "eclipseawakenedtweak_maledictus_last_stand_hud_second";
    private static final String HUD_CLEARED_KEY = "eclipseawakenedtweak_maledictus_last_stand_hud_cleared";
    private static final String GOALS_CONFIGURED_KEY = "eclipseawakenedtweak_maledictus_last_stand_goals_configured";
    private static final String LAST_PLAYER_ATTACKER_KEY = "eclipseawakenedtweak_maledictus_last_player_attacker";
    private static final String TARGET_UUID_KEY = "eclipseawakenedtweak_maledictus_last_stand_target";
    private static final long DURATION_TICKS = 20L * 20L;
    private static final String NATIVE_CHARGE_GOAL = Maledictus_Entity.class.getName() + "$MaledictusChargeGoal";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, List<GoalEntry>> REMOVED_GOALS = new HashMap<>();
    private static final Map<UUID, Float> ORIGINAL_CHARGE_CHANCES = new HashMap<>();

    private record GoalEntry(int priority, Goal goal) {}

    private MaledictusLastStandHandler() {}

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Maledictus_Entity boss && !boss.level().isClientSide
                && boss.getPersistentData().getBoolean(ACTIVE_KEY)) {
            // GoalSelector contents are rebuilt when an entity is loaded.
            boss.getPersistentData().putBoolean(GOALS_CONFIGURED_KEY, false);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Maledictus_Entity boss) || boss.level().isClientSide) {
            return;
        }

        CompoundTag data = boss.getPersistentData();
        // Commands and other bypass damage (notably /kill) must retain their
        // normal lethal behavior instead of being converted into the phase.
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player) && event.getSource().getDirectEntity() instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        if (attacker instanceof Player player) {
            data.putUUID(LAST_PLAYER_ATTACKER_KEY, player.getUUID());
        }

        // The handler's own finishing hit must pass through normally.
        if (data.getBoolean(EXPIRED_KEY)) {
            return;
        }
        if (data.getBoolean(ACTIVE_KEY)) {
            event.setCanceled(true);
            boss.setHealth(1.0F);
            return;
        }

        // Begin the last stand only when this hit would otherwise be lethal.
        if (boss.getHealth() - event.getAmount() <= 1.0F) {
            event.setCanceled(true);
            boss.setHealth(1.0F);
            data.putBoolean(ACTIVE_KEY, true);
            data.putBoolean(TIMER_STARTED_KEY, false);
            data.putBoolean(EXPIRED_KEY, false);
            data.putBoolean(OPENING_SMASH_DONE_KEY, false);
            data.putInt(HUD_SECOND_KEY, -1);
            data.putBoolean(HUD_CLEARED_KEY, false);
            LivingEntity target = boss.getTarget();
            if (target != null) {
                data.putUUID(TARGET_UUID_KEY, target.getUUID());
            } else {
                data.remove(TARGET_UUID_KEY);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || level.isClientSide) {
            return;
        }
        UUID deadTarget = event.getEntity().getUUID();
        for (Entity entity : level.getEntities().getAll()) {
            if (entity instanceof Maledictus_Entity boss) {
                CompoundTag data = boss.getPersistentData();
                if (data.getBoolean(ACTIVE_KEY) && data.hasUUID(TARGET_UUID_KEY)
                        && deadTarget.equals(data.getUUID(TARGET_UUID_KEY))) {
                    resetLastStand(boss, data);
                }
            }
        }
    }

    private static void resetLastStand(Maledictus_Entity boss, CompoundTag data) {
        data.putBoolean(ACTIVE_KEY, false);
        data.putBoolean(TIMER_STARTED_KEY, false);
        data.putBoolean(EXPIRED_KEY, false);
        data.putBoolean(OPENING_SMASH_DONE_KEY, false);
        data.putBoolean(HUD_CLEARED_KEY, true);
        data.remove(TARGET_UUID_KEY);
        List<GoalEntry> removed = REMOVED_GOALS.remove(boss.getUUID());
        if (removed != null) {
            for (GoalEntry entry : removed) {
                boss.goalSelector.addGoal(entry.priority(), entry.goal());
            }
        }
        data.putBoolean(GOALS_CONFIGURED_KEY, false);
        Float originalChance = ORIGINAL_CHARGE_CHANCES.remove(boss.getUUID());
        if (originalChance != null) {
            setNativeChargeChance(boss, originalChance);
        }
        broadcastActionBar(boss, Component.empty());
        boss.setTarget(null);
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Maledictus_Entity boss) || boss.level().isClientSide) {
            return;
        }

        CompoundTag data = boss.getPersistentData();
        if (!data.getBoolean(ACTIVE_KEY)) {
            return;
        }

        if (data.hasUUID(TARGET_UUID_KEY)) {
            Entity target = boss.level() instanceof ServerLevel serverLevel
                    ? serverLevel.getEntity(data.getUUID(TARGET_UUID_KEY)) : null;
            if (target != null && !target.isAlive()) {
                resetLastStand(boss, data);
                return;
            }
        }

        boss.setHealth(1.0F);
        // Leave the attack that was active at the lethal hit untouched. Once
        // it naturally returns to state 0, replace the other attack goals and
        // start the state-7 finisher before another normal attack is selected.
        if (!data.getBoolean(OPENING_SMASH_DONE_KEY) && boss.getAttackState() != 0) {
            ensureOpeningGoal(boss);
            return;
        }

        configureLastStandGoals(boss, data);
        if (data.getBoolean(OPENING_SMASH_DONE_KEY) && !data.getBoolean(EXPIRED_KEY)
                && boss.getAttackState() == 0) {
            setNativeChargeCooldown(boss, 0);
        }
        if (data.getBoolean(TIMER_STARTED_KEY)
                && boss.level().getGameTime() - data.getLong(START_TICK_KEY) >= DURATION_TICKS) {
            data.putBoolean(EXPIRED_KEY, true);
            setNativeChargeChance(boss, -1.0F);
            boss.setTarget(null);
        }

        if (data.getBoolean(TIMER_STARTED_KEY) && !data.getBoolean(EXPIRED_KEY)) {
            long elapsed = boss.level().getGameTime() - data.getLong(START_TICK_KEY);
            int secondsLeft = (int) Math.max(0, (DURATION_TICKS - elapsed + 19) / 20);
            if (secondsLeft != data.getInt(HUD_SECOND_KEY)) {
                data.putInt(HUD_SECOND_KEY, secondsLeft);
                broadcastActionBar(boss, Component.literal(String.format(Locale.ROOT, "00:%02d", secondsLeft))
                        .withStyle(style -> style.withColor(ChatFormatting.AQUA).withBold(true)));
            }
        }

        // Let the current attack finish before applying the terminal hit.
        if (data.getBoolean(EXPIRED_KEY) && boss.getAttackState() == 0) {
            data.putBoolean(ACTIVE_KEY, false);
            if (!data.getBoolean(HUD_CLEARED_KEY)) {
                data.putBoolean(HUD_CLEARED_KEY, true);
                broadcastActionBar(boss, Component.empty());
            }
            boss.hurt(creditedDeathSource(boss, data), Float.MAX_VALUE);
        }
    }

    private static net.minecraft.world.damagesource.DamageSource creditedDeathSource(
            Maledictus_Entity boss, CompoundTag data) {
        if (boss.level() instanceof ServerLevel serverLevel && data.hasUUID(LAST_PLAYER_ATTACKER_KEY)) {
            UUID playerId = data.getUUID(LAST_PLAYER_ATTACKER_KEY);
            Player player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                return boss.damageSources().playerAttack(player);
            }
        }
        return boss.damageSources().generic();
    }

    private static void broadcastActionBar(Maledictus_Entity boss, Component message) {
        double radiusSqr = 128.0D * 128.0D;
        for (Player player : boss.level().players()) {
            if (player.distanceToSqr(boss) <= radiusSqr) {
                player.displayClientMessage(message, true);
            }
        }
    }

    private static void configureLastStandGoals(Maledictus_Entity boss, CompoundTag data) {
        boolean alreadyInstalled = boss.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal().getClass().getName().equals(NATIVE_CHARGE_GOAL));
        if (alreadyInstalled && data.getBoolean(GOALS_CONFIGURED_KEY)) {
            return;
        }

        List<GoalEntry> removedGoals = REMOVED_GOALS.computeIfAbsent(boss.getUUID(), ignored -> new ArrayList<>());
        for (var wrapped : new ArrayList<>(boss.goalSelector.getAvailableGoals())) {
            String goalName = wrapped.getGoal().getClass().getName();
            boolean keepChargeChain = goalName.contains("MaledictusChargeState");
            boolean keepNativeCharge = goalName.equals(NATIVE_CHARGE_GOAL);
            boolean keepMovement = goalName.endsWith("InternalMoveGoal");
            boolean keepOpening = wrapped.getGoal() instanceof LastStandAttackGoal;
            if (!keepChargeChain && !keepNativeCharge && !keepMovement && !keepOpening) {
                removedGoals.add(new GoalEntry(wrapped.getPriority(), wrapped.getGoal()));
                boss.goalSelector.removeGoal(wrapped.getGoal());
            }
        }

        boolean hasOpening = boss.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof LastStandAttackGoal);
        if (!hasOpening) {
            boss.goalSelector.addGoal(0, new LastStandAttackGoal(boss));
        }

        ORIGINAL_CHARGE_CHANCES.computeIfAbsent(boss.getUUID(), ignored -> getNativeChargeChance(boss));
        boolean tunedNativeGoal = setNativeChargeChance(boss, 100.0F);
        if (!tunedNativeGoal) {
            LOGGER.error("Cataclysm's native Maledictus charge goal was not found");
        }
        data.putBoolean(GOALS_CONFIGURED_KEY, tunedNativeGoal);
    }

    private static void ensureOpeningGoal(Maledictus_Entity boss) {
        boolean hasOpening = boss.goalSelector.getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof LastStandAttackGoal);
        if (!hasOpening) {
            boss.goalSelector.addGoal(0, new LastStandAttackGoal(boss));
        }
    }

    private static boolean setNativeChargeChance(Maledictus_Entity boss, float chance) {
        try {
            for (var wrapped : boss.goalSelector.getAvailableGoals()) {
                Goal goal = wrapped.getGoal();
                if (goal.getClass().getName().equals(NATIVE_CHARGE_GOAL)) {
                    Field randomChance = goal.getClass().getDeclaredField("random");
                    randomChance.setAccessible(true);
                    randomChance.setFloat(goal, chance);
                    Field minRange = goal.getClass().getDeclaredField("attackminrange");
                    minRange.setAccessible(true);
                    minRange.setFloat(goal, 0.0F);
                    Field maxRange = goal.getClass().getDeclaredField("attackrange");
                    maxRange.setAccessible(true);
                    maxRange.setFloat(goal, 64.0F);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | SecurityException exception) {
            LOGGER.error("Unable to update Cataclysm's native Maledictus charge chance", exception);
        }
        return false;
    }

    private static Float getNativeChargeChance(Maledictus_Entity boss) {
        try {
            for (var wrapped : boss.goalSelector.getAvailableGoals()) {
                Goal goal = wrapped.getGoal();
                if (goal.getClass().getName().equals(NATIVE_CHARGE_GOAL)) {
                    Field randomChance = goal.getClass().getDeclaredField("random");
                    randomChance.setAccessible(true);
                    return randomChance.getFloat(goal);
                }
            }
        } catch (ReflectiveOperationException | SecurityException exception) {
            LOGGER.error("Unable to read Cataclysm's Maledictus charge chance", exception);
        }
        return null;
    }

    private static void setNativeChargeCooldown(Maledictus_Entity boss, int ticks) {
        try {
            Field cooldown = Maledictus_Entity.class.getDeclaredField("charge_cooldown");
            cooldown.setAccessible(true);
            cooldown.setInt(boss, ticks);
        } catch (ReflectiveOperationException | SecurityException exception) {
            LOGGER.error("Unable to update Cataclysm's Maledictus charge cooldown", exception);
        }
    }

    /** Executes Cataclysm's state animations with the corresponding movement. */
    private static final class LastStandAttackGoal extends Goal {
        private final Maledictus_Entity boss;

        private LastStandAttackGoal(Maledictus_Entity boss) {
            this.boss = boss;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return boss.getPersistentData().getBoolean(ACTIVE_KEY)
                    && !boss.getPersistentData().getBoolean(EXPIRED_KEY)
                    && !boss.getPersistentData().getBoolean(OPENING_SMASH_DONE_KEY)
                    && boss.getAttackState() == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return boss.getPersistentData().getBoolean(ACTIVE_KEY)
                    && !boss.getPersistentData().getBoolean(EXPIRED_KEY)
                    && !boss.getPersistentData().getBoolean(OPENING_SMASH_DONE_KEY);
        }

        @Override
        public void start() {
            beginNextAttack();
        }

        @Override
        public void tick() {
            CompoundTag data = boss.getPersistentData();
            int state = boss.getAttackState();
            int tick = boss.attackTicks;
            LivingEntity target = boss.getTarget();

            if (state == 0) {
                if (data.getBoolean(EXPIRED_KEY)) {
                    return;
                }
                beginNextAttack();
                return;
            }

            if (target != null && target.isAlive() && tick < 56) {
                boss.getLookControl().setLookAt(target, 30.0F, 0.0F);
                boss.lookAt(target, 30.0F, 0.0F);
            }

            if (state == 7) {
                // State 7 is Cataclysm's grounded Mass Effect slam. It stops
                // horizontal movement and lands its two-mace impact at tick 34.
                boss.setDeltaMovement(0.0, boss.getDeltaMovement().y, 0.0);
                if (tick >= 66) {
                    boss.setWeapon(0);
                    data.putBoolean(OPENING_SMASH_DONE_KEY, true);
                    data.putLong(START_TICK_KEY, boss.level().getGameTime());
                    data.putBoolean(TIMER_STARTED_KEY, true);
                    configureLastStandGoals(boss, data);
                    boss.setAttackState(0);
                }
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        private void beginNextAttack() {
            CompoundTag data = boss.getPersistentData();
            LivingEntity target = boss.getTarget();
            if (data.getBoolean(EXPIRED_KEY) || target == null || !target.isAlive()) {
                return;
            }

            if (!data.getBoolean(OPENING_SMASH_DONE_KEY)) {
                boss.setWeapon(0);
                boss.setAttackState(7);
            }
        }
    }
}
