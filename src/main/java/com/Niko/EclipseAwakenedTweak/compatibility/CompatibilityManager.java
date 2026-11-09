package com.Niko.EclipseAwakenedTweak.compatibility;

import com.Niko.EclipseAwakenedTweak.compatibility.cataclysm.CataclysmCompat;
import net.minecraftforge.fml.ModList;

public final class CompatibilityManager {

    private CompatibilityManager() {}

    public static void initialize() {
        if (ModList.get().isLoaded("cataclysm")) {
            CataclysmCompat.initialize();
        }
    }
}