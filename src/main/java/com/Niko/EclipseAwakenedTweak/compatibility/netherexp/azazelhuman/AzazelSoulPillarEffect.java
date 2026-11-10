package com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman;

import com.benji.netherman.entity.AzazelHumanEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.miauczel.legendary_monsters.entity.AnimatedMonster.Projectile.SoulPillarExplosionEntity;

/** Spawns Legendary Monsters' Soul Pillar Explosions as three expanding ground rings. */
public final class AzazelSoulPillarEffect {
    private static final double[] RADII = {2.5D, 5.0D, 7.5D};
    private static final int[] PILLARS_PER_RING = {8, 12, 16};
    private static final int RING_DELAY_TICKS = 5;

    private AzazelSoulPillarEffect() {}

    public static void spawnImpactRings(AzazelHumanEntity azazel) {
        if (!(azazel.level() instanceof ServerLevel level)) return;

        for (int ring = 0; ring < RADII.length; ring++) {
            int count = PILLARS_PER_RING[ring];
            double radius = RADII[ring];
            double angleOffset = ring * Math.PI / count;
            for (int pillarIndex = 0; pillarIndex < count; pillarIndex++) {
                double angle = Math.PI * 2.0D * pillarIndex / count + angleOffset;
                double x = azazel.getX() + Math.cos(angle) * radius;
                double z = azazel.getZ() + Math.sin(angle) * radius;
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        (int) Math.floor(x), (int) Math.floor(z));

                SoulPillarExplosionEntity pillar = new SoulPillarExplosionEntity(
                        level, x, groundY, z, 0.0F, ring * RING_DELAY_TICKS,
                        azazel, 20, 6.0F, false);
                level.addFreshEntity(pillar);
            }
        }
    }
}
