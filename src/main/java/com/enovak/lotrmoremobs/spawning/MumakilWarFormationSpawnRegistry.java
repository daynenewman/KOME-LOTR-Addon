package com.enovak.lotrmoremobs.spawning;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import java.lang.reflect.Field;
import java.util.List;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.map.LOTRConquestGrid;
import lotr.common.world.map.LOTRConquestZone;
import lotr.common.world.spawning.LOTRBiomeSpawnList;
import lotr.common.world.spawning.LOTRSpawnEntry;
import lotr.common.world.spawning.LOTRSpawnList;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.util.MathHelper;

/**
 * Classifies ordinary native-home and conquest-region Near Harad NPC spawns.
 *
 * Eligibility is derived from the current biome's LOTR faction containers:
 * no biome-name or biome-class whitelist is maintained here.
 */
public final class MumakilWarFormationSpawnRegistry {
    public enum HomeCandidateClassification {
        NOT_NEAR_HARAD,
        WRONG_DIMENSION_OR_BIOME,
        NOT_HOME_TERRITORY,
        CIVILIAN,
        MILITARY_CLASS_REJECTED,
        ACCEPTED
    }

    private static boolean handlerRegistered;
    private static boolean reflectionResolved;
    private static boolean reflectionAvailable;
    private static Field factionContainersField;
    private static Field factionSpawnListsField;
    private static Field containedSpawnListField;

    private MumakilWarFormationSpawnRegistry() {
    }

    public static void register() {
        if (handlerRegistered) {
            return;
        }

        handlerRegistered = true;
        /*
         * LOTR does not expose a universal static container which can be
         * populated for whichever faction is currently conquering a region.
         * The old world-load injection therefore found no Near Harad conquest
         * containers in v36.15. Conquest formation replacement is now driven
         * by LOTR's per-NPC conquest-spawn marker at EntityJoinWorldEvent.
         */
        System.out.println(
                "[LOTRMoreMobs] Mumak war-formation NPC spawning:"
                        + " homeTrigger=native-unit-replacement"
                        + " conquestTrigger=LOTR-conquest-unit-replacement"
                        + " invasionTrigger=invasion-unit-replacement"
                        + " sharedPipeline=true"
                        + " homeChance=1/"
                        + MumakilConfig
                        .homeUnitRollDenominator
                        + " conquestBaseChance=1/"
                        + MumakilConfig.conquestUnitRollDenominator
                        + " conquestMinimumDenominator=1/"
                        + MumakilConfig.conquestMinimumDenominator
                        + " conquestThreshold=>="
                        + MumakilConfig
                        .conquestFormationMinimumConquest
                        + " invasionChance=1/"
                        + MumakilConfig.invasionUnitRollDenominator
                        + " conquestEnabled="
                        + MumakilConfig
                        .enableMumakWarFormationsInConquest
                        + " invasionEnabled="
                        + MumakilConfig
                        .enableMumakWarFormationsInInvasions
                        + " oldConquestBootstrapInjection=removed"
        );
    }

    /**
     * Returns true only when this NPC class is compatible with an entry in a
     * non-conquest Near Harad faction container belonging to the entity's
     * current LOTR biome. Assignability preserves native/UCP subclasses.
     *
     * Persistence, hiring, invasion, and artificial-spawn state are checked
     * by the delayed home-unit handler after native initialization finishes.
     */
    public static boolean isNativeNearHaradHomeMilitaryCandidate(
            LOTREntityNPC npc
    ) {
        return classifyNativeNearHaradHomeMilitaryCandidate(npc)
                == HomeCandidateClassification.ACCEPTED;
    }

    /**
     * A non-conquest Near Harad faction container is LOTR's authoritative
     * indication that Near Harad is natively present in this biome. This
     * broader test intentionally includes civilians and rejected military
     * classes so they cannot be misclassified as foreign conquest spawns.
     */
    public static boolean isNativeNearHaradHomeTerritory(
            LOTREntityNPC npc
    ) {
        HomeCandidateClassification classification =
                classifyNativeNearHaradHomeMilitaryCandidate(npc);
        return classification == HomeCandidateClassification.CIVILIAN
                || classification
                == HomeCandidateClassification.MILITARY_CLASS_REJECTED
                || classification == HomeCandidateClassification.ACCEPTED;
    }

    /**
     * Confirms that this non-civilian NPC class is actually present in the
     * current biome's base-zero Near Harad conquest faction container.
     * Assignability preserves compatible addon subclasses.
     */
    public static boolean isNearHaradConquestMilitaryCandidate(
            LOTREntityNPC npc
    ) {
        if (npc == null
                || npc.worldObj == null
                || npc.worldObj.isRemote
                || npc.getFaction() != LOTRFaction.NEAR_HARAD
                || npc.isCivilianNPC()
                || npc instanceof LOTREntityMumakilHowdahArcher) {
            return false;
        }

        if (!resolveReflection()) {
            return false;
        }

        BiomeGenBase biome = npc.worldObj.getBiomeGenForCoords(
                MathHelper.floor_double(npc.posX),
                MathHelper.floor_double(npc.posZ)
        );
        if (!(biome instanceof LOTRBiome)) {
            return false;
        }

        LOTRBiomeSpawnList biomeSpawnList =
                ((LOTRBiome)biome).npcSpawnList;
        if (biomeSpawnList == null) {
            return false;
        }

        try {
            List factionContainers =
                    (List)factionContainersField.get(biomeSpawnList);
            for (int i = 0; i < factionContainers.size(); ++i) {
                Object object = factionContainers.get(i);
                if (!(object
                        instanceof LOTRBiomeSpawnList.FactionContainer)) {
                    continue;
                }

                LOTRBiomeSpawnList.FactionContainer container =
                        (LOTRBiomeSpawnList.FactionContainer)object;
                if (container.isConquestFaction()
                        && containsFactionSpawnList(
                        container,
                        npc.worldObj,
                        LOTRFaction.NEAR_HARAD
                )
                        && containsAssignableEntityClass(
                        container,
                        npc.getClass()
                )) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    /**
     * Direct Near Harad conquest at the actual spawn coordinate. This is
     * intentionally not FactionContainer's ally-boosted effective strength.
     */
    public static float getDirectNearHaradConquest(
            World world,
            int x,
            int z
    ) {
        if (world == null
                || !LOTRConquestGrid.conquestEnabled(world)) {
            return 0.0F;
        }

        LOTRConquestZone zone =
                LOTRConquestGrid.getZoneByWorldCoords(x, z);
        if (zone == null || zone.isEmpty()) {
            return 0.0F;
        }
        return zone.getConquestStrength(
                LOTRFaction.NEAR_HARAD,
                world
        );
    }

    public static HomeCandidateClassification
    classifyNativeNearHaradHomeMilitaryCandidate(
            LOTREntityNPC npc
    ) {
        if (npc == null
                || npc.worldObj == null
                || npc.worldObj.isRemote
                || npc.getFaction() != LOTRFaction.NEAR_HARAD) {
            return HomeCandidateClassification.NOT_NEAR_HARAD;
        }
        long lookupStart =
                MumakilServerPerformanceDiagnostics.startTimer(
                        npc.worldObj
                );
        try {
            if (LOTRDimension.getCurrentDimension(npc.worldObj)
                    != LOTRDimension.MIDDLE_EARTH) {
                return HomeCandidateClassification
                        .WRONG_DIMENSION_OR_BIOME;
            }
            if (!resolveReflection()) {
                return HomeCandidateClassification.NOT_HOME_TERRITORY;
            }

            BiomeGenBase biome = npc.worldObj.getBiomeGenForCoords(
                    MathHelper.floor_double(npc.posX),
                    MathHelper.floor_double(npc.posZ)
            );
            if (!(biome instanceof LOTRBiome)) {
                return HomeCandidateClassification
                        .WRONG_DIMENSION_OR_BIOME;
            }

            LOTRBiomeSpawnList biomeSpawnList =
                    ((LOTRBiome)biome).npcSpawnList;
            if (biomeSpawnList == null) {
                return HomeCandidateClassification.NOT_HOME_TERRITORY;
            }

            boolean foundHomeContainer = false;
            List factionContainers =
                    (List)factionContainersField.get(biomeSpawnList);
            for (int containerIndex = 0;
                 containerIndex < factionContainers.size();
                 ++containerIndex) {
                Object object = factionContainers.get(containerIndex);
                if (!(object
                        instanceof LOTRBiomeSpawnList.FactionContainer)) {
                    continue;
                }

                LOTRBiomeSpawnList.FactionContainer factionContainer =
                        (LOTRBiomeSpawnList.FactionContainer)object;
                if (factionContainer.isConquestFaction()
                        || !containsFactionSpawnList(
                        factionContainer,
                        npc.worldObj,
                        LOTRFaction.NEAR_HARAD
                )) {
                    continue;
                }
                foundHomeContainer = true;
                if (!(npc
                        instanceof LOTREntityMumakilHowdahArcher)
                        && !npc.isCivilianNPC()
                        && containsAssignableEntityClass(
                        factionContainer,
                        npc.getClass()
                )) {
                    return HomeCandidateClassification.ACCEPTED;
                }
            }
            if (!foundHomeContainer) {
                return HomeCandidateClassification.NOT_HOME_TERRITORY;
            }
            if (npc.isCivilianNPC()) {
                return HomeCandidateClassification.CIVILIAN;
            }
            return HomeCandidateClassification
                    .MILITARY_CLASS_REJECTED;
        } catch (Exception ignored) {
            return HomeCandidateClassification.NOT_HOME_TERRITORY;
        } finally {
            MumakilServerPerformanceDiagnostics
                    .recordBiomeSpawnListLookup(
                            npc.worldObj,
                            System.nanoTime() - lookupStart
                    );
        }
    }

    public static int getConquestSpawnDenominator(
            float directConquest
    ) {
        float extraConquest = Math.max(
                0.0F,
                directConquest
                        - MumakilConfig
                        .conquestFormationMinimumConquest
        );
        int denominatorReduction =
                MathHelper.floor_float(
                        extraConquest
                                / MumakilConfig
                                .conquestStrengthPerStep
                );
        return Math.max(
                MumakilConfig.conquestMinimumDenominator,
                MumakilConfig.conquestUnitRollDenominator
                        - denominatorReduction
        );
    }

    private static boolean containsFactionSpawnList(
            LOTRBiomeSpawnList.FactionContainer factionContainer,
            World world,
            LOTRFaction expectedFaction
    ) throws IllegalAccessException {
        List spawnLists =
                (List)factionSpawnListsField.get(factionContainer);

        for (int i = 0; i < spawnLists.size(); ++i) {
            Object object = spawnLists.get(i);

            if (!(object instanceof
                    LOTRBiomeSpawnList.SpawnListContainer)) {
                continue;
            }

            Object contained =
                    containedSpawnListField.get(object);

            if (!(contained instanceof LOTRSpawnList)) {
                continue;
            }

            LOTRSpawnList spawnList =
                    (LOTRSpawnList)contained;

            if (spawnList.getListCommonFaction(world)
                    == expectedFaction) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsAssignableEntityClass(
            LOTRBiomeSpawnList.FactionContainer factionContainer,
            Class entityClass
    ) throws IllegalAccessException {
        List spawnLists =
                (List)factionSpawnListsField.get(factionContainer);
        for (int i = 0; i < spawnLists.size(); ++i) {
            Object object = spawnLists.get(i);
            if (!(object
                    instanceof LOTRBiomeSpawnList.SpawnListContainer)) {
                continue;
            }

            Object contained = containedSpawnListField.get(object);
            if (!(contained instanceof LOTRSpawnList)) {
                continue;
            }

            List entries = ((LOTRSpawnList)contained).getReadOnlyList();
            for (int entryIndex = 0;
                 entryIndex < entries.size();
                 ++entryIndex) {
                Object entry = entries.get(entryIndex);
                if (entry instanceof LOTRSpawnEntry
                        && ((LOTRSpawnEntry)entry).entityClass
                        .isAssignableFrom(entityClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean resolveReflection() {
        if (reflectionResolved) {
            return reflectionAvailable;
        }

        synchronized (MumakilWarFormationSpawnRegistry.class) {
            if (!reflectionResolved) {
                try {
                    factionContainersField =
                            LOTRBiomeSpawnList.class.getDeclaredField(
                                    "factionContainers"
                            );
                    factionSpawnListsField =
                            LOTRBiomeSpawnList.FactionContainer.class
                                    .getDeclaredField("spawnLists");
                    containedSpawnListField =
                            LOTRBiomeSpawnList.SpawnListContainer.class
                                    .getDeclaredField("spawnList");

                    factionContainersField.setAccessible(true);
                    factionSpawnListsField.setAccessible(true);
                    containedSpawnListField.setAccessible(true);
                    reflectionAvailable = true;
                } catch (Exception e) {
                    reflectionAvailable = false;
                }
                reflectionResolved = true;
            }
        }
        return reflectionAvailable;
    }
}
