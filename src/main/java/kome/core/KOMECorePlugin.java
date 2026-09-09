package kome.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** Loads KOME's waypoint and integrated LOTRMoreMobs transformers. */
@IFMLLoadingPlugin.TransformerExclusions({
        "kome.core",
        "com.enovak.lotrmoremobs.coremod"
})
@IFMLLoadingPlugin.SortingIndex(1100)
@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class KOMECorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        System.out.println("[KOME] Registered the v36.15 waypoint and LOTRMoreMobs compatibility transformers.");
        return new String[] {
                KOMEWaypointTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.MortalGandalfTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.RespawnMarkerProjectileCollisionTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.EntitySensesGateSightTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.PathFinderGatePartTransformer.class.getName()
        };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
