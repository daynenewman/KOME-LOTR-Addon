package com.enovak.lotrmoremobs.spawning;



import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.config.MumakilConfig;

import java.lang.reflect.Field;

import java.lang.reflect.Modifier;

import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import net.minecraft.entity.EnumCreatureType;

import net.minecraft.world.biome.BiomeGenBase;



/**

 * Registers natural Mumakil herds only in the selected southern regions.

 *

 * MUMAKIL_REGION_FIELD_WHITELIST_V4

 */

public final class MumakilNaturalSpawnRegistry {

    private static boolean registered;



    private MumakilNaturalSpawnRegistry() {

    }



    public static void register() {

        if (!MumakilConfig.enableMumakil
                || !MumakilConfig.enableNaturalMumakSpawning) {
            return;
        }

        if (registered) {

            return;

        }



        registered = true;
List eligibleBiomes = collectEligibleRegionBiomes();

        int registeredBiomeCount = 0;



        for (int i = 0; i < eligibleBiomes.size(); ++i) {

            BiomeGenBase biome =

                    (BiomeGenBase)eligibleBiomes.get(i);



            List spawnList = biome.getSpawnableList(

                    EnumCreatureType.creature

            );



            if (containsMumakilEntry(spawnList)) {

                continue;

            }



            spawnList.add(

                    new BiomeGenBase.SpawnListEntry(

                            LOTREntityMumakil.class,

                            MumakilConfig.mumakNaturalSpawnWeight,

                            MumakilConfig.mumakNaturalSpawnMinGroupSize,

                            MumakilConfig.mumakNaturalSpawnMaxGroupSize

                    )

            );



            ++registeredBiomeCount;



            System.out.println(

                    "[LOTRMoreMobs] Natural Mumakil spawning enabled:"

                            + " biomeId=" + biome.biomeID

                            + " biomeName=" + biome.biomeName

                            + " biomeClass="

                            + biome.getClass().getName()

                            + " weight=" + MumakilConfig.mumakNaturalSpawnWeight

                            + " group=" + MumakilConfig.mumakNaturalSpawnMinGroupSize

                            + "-" + MumakilConfig.mumakNaturalSpawnMaxGroupSize

            );

        }



        System.out.println(

                "[LOTRMoreMobs] Natural Mumakil region registration"

                        + " complete. Eligible biome objects="

                        + registeredBiomeCount

        );

    }



    private static List collectEligibleRegionBiomes() {

        /*

         * Use LOTRBiome's static field names as the geographic whitelist.

         * This is intentionally stricter than matching generic biome names

         * such as "Near Harad" or "semi-desert".

         */

        List result = new ArrayList();



        try {

            Class lotrBiomeClass = Class.forName(

                    "lotr.common.world.biome.LOTRBiome"

            );

            Field[] fields = lotrBiomeClass.getDeclaredFields();



            for (int i = 0; i < fields.length; ++i) {

                Field field = fields[i];



                if (!Modifier.isStatic(field.getModifiers())

                        || !BiomeGenBase.class.isAssignableFrom(

                        field.getType()

                )) {

                    continue;

                }



                field.setAccessible(true);

                Object value = field.get(null);



                if (!(value instanceof BiomeGenBase)) {

                    continue;

                }



                BiomeGenBase biome = (BiomeGenBase)value;

                String sourceKey = normalize(field.getName());



                if (isDiagnosticSouthernField(sourceKey)) {

                    System.out.println(

                            "[LOTRMoreMobs] Southern biome field:"

                                    + " source=" + field.getName()

                                    + " biomeId=" + biome.biomeID

                                    + " biomeName=" + biome.biomeName

                                    + " biomeClass="

                                    + biome.getClass().getName()

                                    + " eligible="

                                    + isEligibleRegionField(sourceKey)

                    );

                }



                if (isEligibleRegionField(sourceKey)

                        && !result.contains(biome)) {

                    result.add(biome);



                    System.out.println(

                            "[LOTRMoreMobs] Matched Mumakil region biome:"

                                    + " source=" + field.getName()

                                    + " biomeId=" + biome.biomeID

                                    + " biomeName=" + biome.biomeName

                                    + " biomeClass="

                                    + biome.getClass().getName()

                    );

                }

            }

        } catch (Exception e) {

            System.err.println(

                    "[LOTRMoreMobs] Could not inspect LOTRBiome fields: "

                            + e

            );

        }



        return result;

    }



    private static boolean isEligibleRegionField(String sourceKey) {
        /*
         * FINAL_MUMAKIL_HARAD_BIOME_WHITELIST_V5
         *
         * Intended regions and all of their dedicated biome/sub-biome
         * fields:
         *
         * - Harnennor (including legacy Harnedor naming)
         * - Harondor
         * - Southron Coasts
         * - Umbar
         * - Half-deserts
         * - Far Harad Arid Grasslands
         * - Far Harad Grasslands
         * - Far Harad Bushland
         * - Far Harad Mangrove
         *
         * Generic nearHarad is deliberately omitted so the Great Desert
         * remains excluded.
         */
        if (sourceKey == null || sourceKey.isEmpty()) {
            return false;
        }

        /*
         * Dedicated region families: allow every separate field whose
         * source name begins with the region name.
         */
        if (sourceKey.startsWith("harnennor")
                || sourceKey.startsWith("harnedor")
                || sourceKey.startsWith("harondor")
                || sourceKey.startsWith("umbar")) {
            return true;
        }

        /*
         * Southron Coasts and its known sub-biome fields.
         */
        if (sourceKey.equals("nearharadfertile")
                || sourceKey.equals("nearharadfertileforest")
                || sourceKey.equals("nearharadriverbank")
                || sourceKey.equals("nearharadoasis")) {
            return true;
        }

        /*
         * Half-deserts.
         */
        if (sourceKey.equals("nearharadsemidesert")) {
            return true;
        }

        /*
         * Far Harad Grasslands and its dedicated forest/coast sub-biomes.
         */
        if (sourceKey.equals("farharad")
                || sourceKey.equals("farharadforest")
                || sourceKey.equals("farharadcoast")) {
            return true;
        }

        /*
         * Far Harad Arid Grasslands and Bushland include all dedicated
         * fields, such as their hill sub-biomes.
         */
        if (sourceKey.startsWith("farharadarid")
                || sourceKey.startsWith("farharadbushland")) {
            return true;
        }

        /*
         * Gulf of Harad and Gulf Forest.
         *
         * The exact field naming may use forms such as:
         * - gulfHarad
         * - gulfOfHarad
         * - nearHaradGulf
         * - gulfForest
         */
        if ((sourceKey.contains("gulf")
                && sourceKey.contains("harad"))
                || sourceKey.equals("gulfforest")) {
            return true;
        }

        /*
         * Far Harad Mangrove.
         */
        return sourceKey.equals("farharadmangrove");
    }



    private static boolean isDiagnosticSouthernField(

            String sourceKey

    ) {

        return sourceKey.contains("harondor")

                || sourceKey.contains("harnen")

                || sourceKey.contains("umbar")

                || sourceKey.contains("southron")

                || sourceKey.contains("nearharad")

                || sourceKey.contains("greatdesert");

    }



    private static boolean containsMumakilEntry(List spawnList) {

        for (int i = 0; i < spawnList.size(); ++i) {

            Object entryObject = spawnList.get(i);



            if (entryObject

                    instanceof BiomeGenBase.SpawnListEntry) {

                BiomeGenBase.SpawnListEntry entry =

                        (BiomeGenBase.SpawnListEntry)entryObject;



                if (entry.entityClass

                        == LOTREntityMumakil.class) {

                    return true;

                }

            }

        }



        return false;

    }



    private static String normalize(String value) {

        if (value == null) {

            return "";

        }



        return value.toLowerCase(Locale.ENGLISH)

                .replaceAll("[^a-z0-9]", "");

    }

}