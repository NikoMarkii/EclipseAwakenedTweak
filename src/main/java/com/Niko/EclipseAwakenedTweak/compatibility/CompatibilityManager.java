package com.Niko.EclipseAwakenedTweak.compatibility;

import com.Niko.EclipseAwakenedTweak.compatibility.cataclysm.CataclysmCompat;
import com.Niko.EclipseAwakenedTweak.compatibility.netherexp.NetherExpCompat;
import net.minecraftforge.fml.ModList;

public final class CompatibilityManager {

    private CompatibilityManager() {}

    public static void initialize() {
        if (ModList.get().isLoaded("cataclysm")) {
            CataclysmCompat.initialize();
        }
        if (ModList.get().isLoaded("netherman")) {
            NetherExpCompat.initialize();
        }
    }
}
