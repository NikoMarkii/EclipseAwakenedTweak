package com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman;

import com.benji.netherman.entity.AzazelHumanEntity;
import net.minecraft.world.entity.Entity;

/** Stores a stable per-Azazel NBT marker on the boss and each of its summons. */
public final class AzazelSummonTag {
    private AzazelSummonTag() {}

    public static String key(AzazelHumanEntity azazel) {
        return "azazelhuman" + Long.toUnsignedString(azazel.getUUID().getLeastSignificantBits());
    }

    public static void mark(AzazelHumanEntity azazel, Entity entity) {
        String key = key(azazel);
        azazel.getPersistentData().putBoolean(key, true);
        entity.getPersistentData().putBoolean(key, true);
    }

    public static boolean belongsTo(AzazelHumanEntity azazel, Entity entity) {
        return entity.getPersistentData().getBoolean(key(azazel));
    }
}
