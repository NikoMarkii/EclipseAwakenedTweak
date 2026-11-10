package com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman;

import com.benji.netherman.entity.AzazelHumanEntity;
import net.minecraftforge.fml.ModList;

/** Keeps Legendary Monsters optional while requesting Azazel's ground impact effect. */
public final class AzazelSoulPillarBridge {
    private static final String EFFECT_CLASS =
            "com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman.AzazelSoulPillarEffect";

    private AzazelSoulPillarBridge() {}

    public static void spawnImpactRings(AzazelHumanEntity azazel) {
        if (!ModList.get().isLoaded("legendary_monsters")) return;
        try {
            Class.forName(EFFECT_CLASS)
                    .getMethod("spawnImpactRings", AzazelHumanEntity.class)
                    .invoke(null, azazel);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The effect stays optional if Legendary Monsters is unavailable or changes its API.
        }
    }
}
