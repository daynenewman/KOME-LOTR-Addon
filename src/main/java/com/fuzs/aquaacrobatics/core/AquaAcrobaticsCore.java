package com.fuzs.aquaacrobatics.core;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fuzs.aquaacrobatics.AquaAcrobatics;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

@SuppressWarnings("unused")
@IFMLLoadingPlugin.Name(AquaAcrobaticsCore.NAME)
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class AquaAcrobaticsCore implements IFMLLoadingPlugin {

    public static final String MODID = AquaAcrobatics.MODID;
    public static final String NAME = AquaAcrobatics.NAME + " Transformer";
    public static final String VERSION = AquaAcrobatics.VERSION;
    public static final Logger LOGGER = LogManager.getLogger(AquaAcrobaticsCore.NAME);
    private static Boolean isDevEnv;

    public static boolean isDevEnv() {
        return isDevEnv;
    }
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {
            "com.fuzs.aquaacrobatics.core.asm.AquaEntityPlayerTransformer",
            "com.fuzs.aquaacrobatics.core.asm.AquaServerPlayerTransformer",
            "com.fuzs.aquaacrobatics.core.asm.AquaBiomeTransformer",
            "com.fuzs.aquaacrobatics.core.asm.AquaCommonWorldTransformer",
            "com.fuzs.aquaacrobatics.core.asm.AquaClientEntityTransformer",
            "com.fuzs.aquaacrobatics.core.asm.AquaLateClientPlayerTransformer" };
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
        isDevEnv = !(boolean) data.get("runtimeDeobfuscationEnabled");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
