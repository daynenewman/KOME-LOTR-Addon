package kome.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** Loads KOME's single, version-checked final-travel transformer. */
@IFMLLoadingPlugin.TransformerExclusions({"kome.core"})
@IFMLLoadingPlugin.SortingIndex(1100)
@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class KOMECorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        System.out.println("[KOME] Registered the v36.15 waypoint transformer; the LOTRPlayerData structural check will fail closed when that class loads.");
        return new String[] { KOMEWaypointTransformer.class.getName() };
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
