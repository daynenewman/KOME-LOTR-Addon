package kome.core;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/** Loads KOME, LOTRMoreMobs, and integrated Aqua Acrobatics transformers. */
@IFMLLoadingPlugin.TransformerExclusions({
        "kome.core",
        "com.enovak.lotrmoremobs.coremod"
})
@IFMLLoadingPlugin.SortingIndex(1100)
@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class KOMECorePlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        System.out.println(
                "[KOME] Registered waypoint, LOTRMoreMobs, and Aqua Acrobatics transformers."
        );

        return new String[] {
                KOMEWaypointTransformer.class.getName(),

                com.enovak.lotrmoremobs.coremod.MortalGandalfTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.RespawnMarkerProjectileCollisionTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.EntitySensesGateSightTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.PathFinderGatePartTransformer.class.getName(),

                "com.fuzs.aquaacrobatics.core.asm.AquaEntityPlayerTransformer",
                "com.fuzs.aquaacrobatics.core.asm.AquaServerPlayerTransformer",
                "com.fuzs.aquaacrobatics.core.asm.AquaBiomeTransformer",
                "com.fuzs.aquaacrobatics.core.asm.AquaCommonWorldTransformer",
                "com.fuzs.aquaacrobatics.core.asm.AquaClientEntityTransformer",
                "com.fuzs.aquaacrobatics.core.asm.AquaLateClientPlayerTransformer"
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
        new com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore()
                .injectData(data);
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}