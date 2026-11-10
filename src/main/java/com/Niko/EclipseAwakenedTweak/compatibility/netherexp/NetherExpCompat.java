package com.Niko.EclipseAwakenedTweak.compatibility.netherexp;

import net.minecraftforge.common.MinecraftForge;

public final class NetherExpCompat {
    private NetherExpCompat() {}

    public static void initialize() {
        MinecraftForge.EVENT_BUS.register(AzazelReworkHandler.class);
    }
}
