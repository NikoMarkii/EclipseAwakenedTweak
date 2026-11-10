package com.Niko.EclipseAwakenedTweak.compatibility.netherexp;

import com.Niko.EclipseAwakenedTweak.compatibility.netherexp.azazelhuman.AzazelReworkHandler;
import net.minecraftforge.common.MinecraftForge;

public final class NetherExpCompat {
    private NetherExpCompat() {}

    public static void initialize() {
        MinecraftForge.EVENT_BUS.register(AzazelReworkHandler.class);
    }
}
