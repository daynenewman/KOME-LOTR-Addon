package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.spawning.MumakilFormationReplacementService;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationSpawnRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Captures LOTR's transient conquest-spawn flag at join time. Classification
 * remains conquest-specific; admitted NPCs use the common replacement queue.
 */
public final class MumakilConquestUnitRollEventHandler {
    private static final boolean DEBUG_CONQUEST_DETAIL = false;
    private static final String LOTR_CONQUEST_ORIGIN_CAPTURED_KEY =
            "lotrmoremobs_mumakLOTRConquestOriginCaptured";

    private static boolean conquestFieldResolved;
    private static boolean conquestFieldAvailable;
    private static boolean conquestFieldFailureLogged;
    private static Field conquestSpawningField;

    private static final MumakilFormationReplacementService
            .RevalidationPolicy REVALIDATION_POLICY =
            new MumakilFormationReplacementService
                    .RevalidationPolicy() {
                @Override
                public boolean isStillEligible(
                        LOTREntityNPC candidate,
                        MumakilFormationReplacementService
                                .ReplacementContext context
                ) {
                    if (!isEligibleConquestCandidate(candidate)) {
                        return false;
                    }
                    int x = MathHelper.floor_double(candidate.posX);
                    int z = MathHelper.floor_double(candidate.posZ);
                    float directConquest =
                            MumakilWarFormationSpawnRegistry
                            .getDirectNearHaradConquest(
                                    candidate.worldObj,
                                    x,
                                    z
                            );
                    context.setRollDenominator(
                            MumakilWarFormationSpawnRegistry
                            .getConquestSpawnDenominator(
                                    directConquest
                            )
                    );
                    return true;
                }
            };

    private final MumakilFormationReplacementService replacementService;

    public MumakilConquestUnitRollEventHandler(
            MumakilFormationReplacementService replacementService
    ) {
        this.replacementService = replacementService;
    }

    public static boolean isCapturedConquestCandidate(
            LOTREntityNPC npc
    ) {
        return npc != null
                && npc.getEntityData().getBoolean(
                LOTR_CONQUEST_ORIGIN_CAPTURED_KEY
        );
    }

    public static void markConquestUnitRollEvaluated(
            LOTREntityNPC npc
    ) {
        MumakilFormationReplacementService
                .markReplacementEvaluated(npc);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null
                || event.world == null
                || event.world.isRemote
                || event.isCanceled()
                || !(event.entity instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC npc = (LOTREntityNPC)event.entity;
        replacementService.recordJoined(
                event.world,
                MumakilFormationOrigin.CONQUEST_NEAR_HARAD
        );
        if (MumakilFormationReplacementService
                .isReplacementEvaluated(npc)) {
            replacementService.recordAlreadyEvaluated(
                    event.world,
                    MumakilFormationOrigin.CONQUEST_NEAR_HARAD
            );
            return;
        }

        boolean captured = isCapturedConquestCandidate(npc);
        boolean liveConquestSpawn =
                captured || readLiveLOTRConquestSpawnMarker(npc);
        if (!liveConquestSpawn) {
            replacementService.recordClassificationReject(
                    event.world,
                    MumakilFormationOrigin.CONQUEST_NEAR_HARAD
            );
            return;
        }
        if (!captured) {
            npc.getEntityData().setBoolean(
                    LOTR_CONQUEST_ORIGIN_CAPTURED_KEY,
                    true
            );
        }

        if (!MumakilConfig.enableMumakWarFormationsInConquest
                || !isEligibleConquestCandidate(npc)) {
            replacementService.recordClassificationReject(
                    event.world,
                    MumakilFormationOrigin.CONQUEST_NEAR_HARAD
            );
            debugCandidate("classificationReject", npc);
            return;
        }

        int x = MathHelper.floor_double(npc.posX);
        int z = MathHelper.floor_double(npc.posZ);
        float conquest =
                MumakilWarFormationSpawnRegistry
                        .getDirectNearHaradConquest(
                                event.world,
                                x,
                                z
                        );
        int denominator =
                MumakilWarFormationSpawnRegistry
                        .getConquestSpawnDenominator(conquest);
        replacementService.queueCandidate(
                npc,
                new MumakilFormationReplacementService
                        .ReplacementContext(
                        MumakilFormationOrigin.CONQUEST_NEAR_HARAD,
                        denominator,
                        null,
                        REVALIDATION_POLICY
                )
        );
        debugCandidate("queued", npc);
    }

    private static boolean isEligibleConquestCandidate(
            LOTREntityNPC npc
    ) {
        if (!MumakilConfig.enableMumakWarFormationsInConquest
                || npc == null
                || npc.worldObj == null
                || npc.worldObj.isRemote
                || npc.isDead
                || !npc.isEntityAlive()
                || !isCapturedConquestCandidate(npc)
                || npc.getFaction() != LOTRFaction.NEAR_HARAD
                || MumakilFormationReplacementService
                .isGeneratedFormationMember(npc)
                || npc.isInvasionSpawned()
                || npc.getInvasionID() != null
                || npc.hiredNPCInfo.isActive
                || npc.isTrader()
                || npc.isTraderEscort
                || npc.isNPCPersistent
                || npc.shouldTraderRespawn()
                || npc.liftSpawnRestrictions
                || MumakilWarFormationSpawnRegistry
                .isNativeNearHaradHomeTerritory(npc)
                || !MumakilWarFormationSpawnRegistry
                .isNearHaradConquestMilitaryCandidate(npc)) {
            return false;
        }

        int x = MathHelper.floor_double(npc.posX);
        int z = MathHelper.floor_double(npc.posZ);
        return MumakilWarFormationSpawnRegistry
                .getDirectNearHaradConquest(npc.worldObj, x, z)
                >= MumakilConfig
                .conquestFormationMinimumConquest;
    }

    private static boolean readLiveLOTRConquestSpawnMarker(
            LOTREntityNPC npc
    ) {
        if (npc == null || !resolveConquestSpawningField()) {
            return false;
        }
        try {
            return conquestSpawningField.getBoolean(npc);
        } catch (Exception e) {
            logConquestFieldFailureOnce(
                    e.getClass().getSimpleName()
                            + ": "
                            + e.getMessage()
            );
            return false;
        }
    }

    private static boolean resolveConquestSpawningField() {
        if (conquestFieldResolved) {
            return conquestFieldAvailable;
        }
        synchronized (MumakilConquestUnitRollEventHandler.class) {
            if (!conquestFieldResolved) {
                try {
                    conquestSpawningField =
                            LOTREntityNPC.class.getDeclaredField(
                                    "isConquestSpawning"
                            );
                    conquestSpawningField.setAccessible(true);
                    conquestFieldAvailable = true;
                } catch (Exception e) {
                    conquestFieldAvailable = false;
                    logConquestFieldFailureOnce(
                            e.getClass().getSimpleName()
                                    + ": "
                                    + e.getMessage()
                    );
                }
                conquestFieldResolved = true;
            }
        }
        return conquestFieldAvailable;
    }

    private static void logConquestFieldFailureOnce(String reason) {
        if (conquestFieldFailureLogged) {
            return;
        }
        conquestFieldFailureLogged = true;
        System.err.println(
                "[LOTRMoreMobs] Mumak conquest-unit capture disabled:"
                        + " LOTREntityNPC.isConquestSpawning unavailable ("
                        + reason
                        + ")"
        );
    }

    private static void debugCandidate(
            String stage,
            LOTREntityNPC npc
    ) {
        if (!DEBUG_CONQUEST_DETAIL
                || npc == null
                || npc.worldObj == null) {
            return;
        }
        int x = MathHelper.floor_double(npc.posX);
        int z = MathHelper.floor_double(npc.posZ);
        System.out.println(
                "[LOTRMoreMobs] Mumak conquest detail:"
                        + " stage=" + stage
                        + " entity=" + npc.getClass().getSimpleName()
                        + " id=" + npc.getEntityId()
                        + " faction=" + npc.getFaction()
                        + " x=" + x
                        + " z=" + z
                        + " directNearHaradConquest="
                        + MumakilWarFormationSpawnRegistry
                        .getDirectNearHaradConquest(
                                npc.worldObj,
                                x,
                                z
                        )
                        + " minimum="
                        + MumakilConfig
                        .conquestFormationMinimumConquest
        );
    }
}
