package com.Niko.EclipseAwakenedTweak.compatibility.netherexp;

import com.Polarice3.Goety.common.entities.ModEntityType;
import com.Polarice3.Goety.common.entities.neutral.AbstractObsidianMonolith;
import com.Polarice3.Goety.common.entities.hostile.servants.Damned;
import com.Polarice3.Goety.common.entities.hostile.servants.Inferno;
import com.Polarice3.Goety.common.entities.neutral.Owned;
import com.Polarice3.Goety.common.entities.projectiles.FireTornado;
import com.Polarice3.Goety.common.entities.projectiles.HellCloud;
import com.Polarice3.Goety.common.entities.projectiles.NetherMeteor;
import com.Polarice3.Goety.common.entities.projectiles.SpellEntity;
import com.Polarice3.Goety.common.entities.util.SummonCircle;
import com.Polarice3.Goety.init.ModSounds;
import com.benji.netherman.entity.AzazelHumanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Goety's Nether spells and hostile summons used by the Azazel phase upgrade. */
public final class AzazelGoetyMagic {
    private AzazelGoetyMagic() {}

    /** Mirrors Apostle's monolith immunity: the owner stays invulnerable while its monolith is alive. */
    public static boolean hasOwnedObsidianMonolith(AzazelHumanEntity azazel) {
        return getOwnedObsidianMonolith(azazel) != null;
    }

    public static AbstractObsidianMonolith getOwnedObsidianMonolith(AzazelHumanEntity azazel) {
        return azazel.level().getEntitiesOfClass(AbstractObsidianMonolith.class,
                        azazel.getBoundingBox().inflate(64.0D), monolith -> monolith.getTrueOwner() == azazel)
                .stream().min(java.util.Comparator.comparingDouble(monolith -> azazel.distanceToSqr(monolith)))
                .orElse(null);
    }

    public static boolean isOwnedByAzazel(AzazelHumanEntity azazel, Entity entity) {
        return entity instanceof Owned owned && owned.getTrueOwner() == azazel
                || entity instanceof SpellEntity spell && spell.getOwner() == azazel;
    }

    public static boolean isGoetyOwnedEntity(Entity entity) {
        return entity instanceof Owned || entity instanceof SpellEntity;
    }

    public static void prepareSpell(AzazelHumanEntity azazel, boolean summon) {
        azazel.level().playSound(null, azazel.blockPosition(),
                (summon ? ModSounds.APOSTLE_PREPARE_SUMMON : ModSounds.APOSTLE_PREPARE_SPELL).get(),
                SoundSource.HOSTILE, 1.5F, 0.85F + azazel.getRandom().nextFloat() * 0.3F);
    }

    public static void triggerHalfHealth(AzazelHumanEntity azazel, LivingEntity target) {
        if (!(azazel.level() instanceof ServerLevel level)) return;
        summonMonolith(level, azazel, target);
        for (int i = 0; i < 7; i++) {
            double angle = Math.PI * 2.0D * i / 7.0D + azazel.getRandom().nextDouble() * 0.2D;
            double radius = 4.0D + azazel.getRandom().nextDouble() * 9.0D;
            spawnMeteor(level, azazel, azazel.getX() + Math.cos(angle) * radius,
                    azazel.getY() + 16.0D + azazel.getRandom().nextDouble() * 5.0D,
                    azazel.getZ() + Math.sin(angle) * radius);
        }
    }

    public static void castNetherSpell(AzazelHumanEntity azazel, LivingEntity target) {
        if (!(azazel.level() instanceof ServerLevel level) || target == null || !target.isAlive()) return;
        int spell = azazel.getRandom().nextInt(3);
        if (spell == 0) {
            HellCloud cloud = new HellCloud(level, azazel, target);
            cloud.setRadius(2.0F);
            cloud.setLifeSpan(1200);
            level.addFreshEntity(cloud);
        } else if (spell == 1) {
            Vec3 center = target.position();
            for (int i = 0; i < 5; i++) {
                double angle = Math.PI * 2.0D * i / 5.0D + azazel.getRandom().nextDouble() * 0.5D;
                double radius = 2.0D + azazel.getRandom().nextDouble() * 4.0D;
                spawnMeteor(level, azazel, center.x + Math.cos(angle) * radius, center.y + 16.0D,
                        center.z + Math.sin(angle) * radius);
            }
        } else {
            FireTornado tornado = new FireTornado(level, azazel, target.getX(), target.getY(), target.getZ());
            tornado.setOwner(azazel);
            tornado.setTarget(target);
            level.addFreshEntity(tornado);
        }
    }

    public static void summonNetherServant(AzazelHumanEntity azazel, LivingEntity target, int choice) {
        if (!(azazel.level() instanceof ServerLevel level)) return;
        Mob servant = choice == 0 ? ModEntityType.INFERNO.get().create(level) : null;
        if (servant == null) return;
        double x = azazel.getX() + (azazel.getRandom().nextDouble() - 0.5D) * 8.0D;
        double z = azazel.getZ() + (azazel.getRandom().nextDouble() - 0.5D) * 8.0D;
        servant.moveTo(x, azazel.getY(), z, azazel.getYRot(), 0.0F);
        if (servant instanceof Owned owned) {
            owned.setOwnerId(azazel.getUUID());
            owned.setHostile(true);
        }
        if (servant instanceof Inferno inferno) {
            inferno.setLifespan(60 * (90 + level.random.nextInt(180)));
            inferno.setUpgraded(true);
        }
        if (target != null) servant.setTarget(target);
        SummonCircle circle = new SummonCircle(level, servant.blockPosition(), servant, true, true, azazel);
        level.addFreshEntity(circle);
    }

    public static void summonDamned(AzazelHumanEntity azazel, LivingEntity target) {
        if (!(azazel.level() instanceof ServerLevel level)) return;
        Damned damned = ModEntityType.DAMNED.get().create(level);
        if (damned == null) return;
        double angle = azazel.getRandom().nextDouble() * Math.PI * 2.0D;
        int radius = 2 + azazel.getRandom().nextInt(3);
        net.minecraft.core.BlockPos spawnPos = azazel.blockPosition().offset(
                (int) Math.round(Math.cos(angle) * radius), -2,
                (int) Math.round(Math.sin(angle) * radius));
        damned.moveTo(spawnPos, azazel.getYRot(), azazel.getXRot());
        damned.setOwnerId(azazel.getUUID());
        damned.setHostile(true);
        if (target != null) damned.setTarget(target);
        damned.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null, null);
        damned.setTarget(target);
        damned.setLifespan(100);
        level.addFreshEntity(damned);
    }

    private static void summonMonolith(ServerLevel level, AzazelHumanEntity azazel, LivingEntity target) {
        double angleOffset = azazel.getRandom().nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < 3; i++) {
            Mob monolith = ModEntityType.OBSIDIAN_MONOLITH.get().create(level);
            if (monolith == null) continue;
            double angle = angleOffset + Math.PI * 2.0D * i / 3.0D;
            double radius = 8.0D;
            monolith.moveTo(azazel.getX() + Math.cos(angle) * radius, azazel.getY(),
                    azazel.getZ() + Math.sin(angle) * radius, azazel.getYRot(), 0.0F);
            if (monolith instanceof Owned owned) {
                // Sets both the owner UUID and the client entity ID used by Goety's chain renderer.
                owned.setTrueOwner(azazel);
                owned.setHostile(true);
            }
            if (target != null) monolith.setTarget(target);
            level.addFreshEntity(monolith);
        }
    }

    private static void spawnMeteor(ServerLevel level, AzazelHumanEntity azazel, double x, double y, double z) {
        NetherMeteor meteor = new NetherMeteor(level, x, y, z, 0.0D, -0.12D, 0.0D);
        meteor.setOwner(azazel);
        meteor.defaultExplosionAndDamage();
        level.addFreshEntity(meteor);
    }
}
