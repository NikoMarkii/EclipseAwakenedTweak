
package com.Niko.EclipseAwakenedTweak;

import com.Niko.EclipseAwakenedTweak.Network.BossIntroNetwork;
import com.Niko.EclipseAwakenedTweak.compatibility.CompatibilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(EclipseAwakenedTweak.MODID)
public class EclipseAwakenedTweak {

    public static final String MODID = "eclipseawakenedtweak";

    public EclipseAwakenedTweak(FMLJavaModLoadingContext context) {
        var modEventBus = context.getModEventBus();

        BossIntroNetwork.register();
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(CompatibilityManager::initialize);
    }
}
