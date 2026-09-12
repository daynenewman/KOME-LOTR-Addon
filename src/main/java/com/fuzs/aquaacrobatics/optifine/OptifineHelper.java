package com.fuzs.aquaacrobatics.optifine;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.fuzs.aquaacrobatics.AquaAcrobatics;

public class OptifineHelper {

    public static boolean isOFPresent;

    public static void init() {
        Class<?> reflectorMainClass;
        try {
            reflectorMainClass = Class.forName("net.optifine.reflect.Reflector");
        } catch (ClassNotFoundException e) {
            return;
        }
        AquaAcrobatics.LOGGER.info("OptiFine detected. Performing highly invasive tweaks to fix water issues.");
        try {
            Class<?> reflectorMethod = Class.forName("net.optifine.reflect.ReflectorMethod");
            Object newReflectorMethod = reflectorMethod
                .getConstructor(Class.forName("net.optifine.reflect.ReflectorClass"), String.class)
                .newInstance(
                    reflectorMainClass.getDeclaredField("ForgeBiome")
                        .get(null),
                    "aqua$waterColorMultiplier");
            Field biomeMethodField = reflectorMainClass.getDeclaredField("ForgeBiome_getWaterColorMultiplier");
            biomeMethodField.setAccessible(true);
            biomeMethodField.set(null, newReflectorMethod);
        } catch (ReflectiveOperationException e) {
            AquaAcrobatics.LOGGER.error("An error occured while patching OptiFine", e);
            return;
        }

        isOFPresent = true;
    }

    /**
     * Force a reload of the block aliases when FML ID mappings change.
     */
    public static void reloadBlockAliases() {
        try {
            Class<?> blockAliasesClass = Class.forName("net.optifine.shaders.BlockAliases");
            Class<?> shadersClass = Class.forName("net.optifine.shaders.Shaders");
            Method getShaderPackMethod = shadersClass.getDeclaredMethod("getShaderPack");
            Method updateMethod = blockAliasesClass
                .getDeclaredMethod("update", Class.forName("net.optifine.shaders.IShaderPack"));
            updateMethod.invoke(null, getShaderPackMethod.invoke(null));
        } catch (Exception e) {
            AquaAcrobatics.LOGGER.error("Error reloading OptiFine block aliases", e);
        }
    }

    /**
     * Rewrite an OptiFine shader block alias to use the same metadata filters but a different main block ID.
     */
    public static Object rewriteBlockAliasForNewId(int mainId, Object blockAlias) {
        if (blockAlias == null) {
            return null;
        }
        try {
            Field blockAliasIdField = blockAlias.getClass()
                .getDeclaredField("blockAliasId");
            blockAliasIdField.setAccessible(true);
            int blockAliasId = blockAliasIdField.getInt(blockAlias);
            Field matchField = blockAlias.getClass()
                .getDeclaredField("matchBlocks");
            matchField.setAccessible(true);
            Object matchArray = matchField.get(blockAlias);
            int numMatches = Array.getLength(matchArray);
            Class<?> matchBlockClass = Class.forName("net.optifine.config.MatchBlock");
            Constructor<?> matchBlockConstructor = matchBlockClass.getDeclaredConstructor(int.class, int[].class);
            Field blockIdField = matchBlockClass.getDeclaredField("blockId");
            blockIdField.setAccessible(true);
            Field metadatasField = matchBlockClass.getDeclaredField("metadatas");
            metadatasField.setAccessible(true);
            Object newMatchArray = Array.newInstance(matchBlockClass, numMatches);
            for (int i = 0; i < numMatches; i++) {
                Object match = Array.get(matchArray, i);
                int[] metadatas = (int[]) metadatasField.get(match);
                Object newMatch = matchBlockConstructor.newInstance(mainId, metadatas);
                Array.set(newMatchArray, i, newMatch);
            }
            Constructor<?> blockAliasConstructor = blockAlias.getClass()
                .getDeclaredConstructor(int.class, newMatchArray.getClass());
            return blockAliasConstructor.newInstance(blockAliasId, newMatchArray);
        } catch (Exception e) {
            AquaAcrobatics.LOGGER.error("An error occured while patching OptiFine", e);
            return blockAlias;
        }
    }
}
