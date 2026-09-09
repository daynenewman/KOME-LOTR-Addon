package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.spawning.MumakilFormationReplacementService;
import com.enovak.lotrmoremobs.spawning.MumakilWarFormationSpawnRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Classifies naturally spawned native Near Harad military NPCs, then hands
 * admitted candidates to the shared delayed replacement service.
 */
public final class MumakilHomeUnitRollEventHandler {
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
                    return isEligibleHomeCandidate(candidate);
                }
            };

    private final MumakilFormationReplacementService replacementService;

    public MumakilHomeUnitRollEventHandler(
            MumakilFormationReplacementService replacementService
    ) {
        this.replacementService = replacementService;
    }

    /**
     * Detached passenger conversion uses the same marker so the replacement
     * ground archer cannot become a new home/conquest/invasion trigger.
     */
    public static void markHomeUnitRollEvaluated(
            LOTREntityNPC npc
    ) {
        MumakilFormationReplacementService
                .markReplacementEvaluated(npc);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null
                || event.world == null
                || event.world.isRemote
                || event.isCanceled()
                || !(event.entity instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC npc = (LOTREntityNPC)event.entity;
        if (npc.getFaction() != LOTRFaction.NEAR_HARAD) {
            return;
        }

        replacementService.recordJoined(
                event.world,
                MumakilFormationOrigin.NATURAL_NEAR_HARAD
        );
        if (MumakilFormationReplacementService
                .isReplacementEvaluated(npc)) {
            replacementService.recordAlreadyEvaluated(
                    event.world,
                    MumakilFormationOrigin.NATURAL_NEAR_HARAD
            );
            return;
        }

        if (!isEligibleHomeCandidate(npc)) {
            replacementService.recordClassificationReject(
                    event.world,
                    MumakilFormationOrigin.NATURAL_NEAR_HARAD
            );
            return;
        }

        replacementService.queueCandidate(
                npc,
                new MumakilFormationReplacementService
                        .ReplacementContext(
                        MumakilFormationOrigin.NATURAL_NEAR_HARAD,
                        MumakilConfig.homeUnitRollDenominator,
                        null,
                        REVALIDATION_POLICY
                )
        );
    }

    private static boolean isEligibleHomeCandidate(
            LOTREntityNPC npc
    ) {
        return npc != null
                && npc.worldObj != null
                && !npc.worldObj.isRemote
                && !npc.isDead
                && npc.isEntityAlive()
                && npc.getFaction() == LOTRFaction.NEAR_HARAD
                && !MumakilFormationReplacementService
                .isGeneratedFormationMember(npc)
                && !MumakilConquestUnitRollEventHandler
                .isCapturedConquestCandidate(npc)
                && !npc.isInvasionSpawned()
                && npc.getInvasionID() == null
                && !npc.hiredNPCInfo.isActive
                && !npc.isTrader()
                && !npc.isTraderEscort
                && !npc.isNPCPersistent
                && !npc.shouldTraderRespawn()
                && !npc.liftSpawnRestrictions
                && MumakilWarFormationSpawnRegistry
                .classifyNativeNearHaradHomeMilitaryCandidate(npc)
                == MumakilWarFormationSpawnRegistry
                .HomeCandidateClassification.ACCEPTED;
    }
}
