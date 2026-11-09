package com.Niko.EclipseAwakenedTweak.compatibility.cataclysm;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

public final class CataclysmCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CataclysmCompat() {}

    public static void initialize() {
        if (ModList.get().isLoaded("cataclysm")) {
            MinecraftForge.EVENT_BUS.register(MaledictusLastStandHandler.class);
        }
        LOGGER.info("Cataclysm compatibility initialized!");
    }
}
