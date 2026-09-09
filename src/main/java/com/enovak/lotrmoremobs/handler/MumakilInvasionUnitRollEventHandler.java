package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.spawning.MumakilFormationReplacementService;
import com.enovak.lotrmoremobs.spawning.MumakilInvasionFormationRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.UUID;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.LOTRBoss;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Classifies ordinary members of eligible Near Harad invasions. The delayed
 * roll and all formation construction stages are owned by the shared service.
 */
public final class MumakilInvasionUnitRollEventHandler {
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
                    return isEligibleInvasionCandidate(
                            candidate,
                            context.getInvasionId()
                    );
                }
            };

    private final MumakilFormationReplacementService replacementService;

    public MumakilInvasionUnitRollEventHandler(
            MumakilFormationReplacementService replacementService
    ) {
        this.replacementService = replacementService;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null
                || event.world == null
                || event.world.isRemote
                || event.isCanceled()
                || !MumakilConfig.enableMumakWarFormationsInInvasions
                || !(event.entity instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC npc = (LOTREntityNPC)event.entity;
        if (!npc.isInvasionSpawned()
                || npc.getInvasionID() == null) {
            return;
        }

        replacementService.recordJoined(
                event.world,
                MumakilFormationOrigin.INVASION_NEAR_HARAD
        );
        if (MumakilFormationReplacementService
                .isReplacementEvaluated(npc)) {
            replacementService.recordAlreadyEvaluated(
                    event.world,
                    MumakilFormationOrigin.INVASION_NEAR_HARAD
            );
            return;
        }

        UUID invasionId = npc.getInvasionID();
        if (!isEligibleInvasionCandidate(npc, invasionId)) {
            replacementService.recordClassificationReject(
                    event.world,
                    MumakilFormationOrigin.INVASION_NEAR_HARAD
            );
            return;
        }

        replacementService.queueCandidate(
                npc,
                new MumakilFormationReplacementService
                        .ReplacementContext(
                        MumakilFormationOrigin.INVASION_NEAR_HARAD,
                        MumakilConfig.invasionUnitRollDenominator,
                        invasionId,
                        REVALIDATION_POLICY
                )
        );
    }

    private static boolean isEligibleInvasionCandidate(
            LOTREntityNPC npc,
            UUID expectedInvasionId
    ) {
        if (!MumakilConfig.enableMumakWarFormationsInInvasions
                || npc == null
                || npc.worldObj == null
                || npc.worldObj.isRemote
                || npc.isDead
                || !npc.isEntityAlive()
                || expectedInvasionId == null
                || !npc.isInvasionSpawned()
                || !expectedInvasionId.equals(npc.getInvasionID())
                || npc.getFaction() != LOTRFaction.NEAR_HARAD
                || MumakilFormationReplacementService
                .isGeneratedFormationMember(npc)
                || npc instanceof LOTRBoss
                || npc.isCivilianNPC()
                || npc.isTrader()
                || npc.isTraderEscort
                || npc.hiredNPCInfo.isActive
                || npc.getEntityData().getInteger(
                MumakilInvasionFormationRegistry
                        .INVASION_MEMBER_WEIGHT_KEY
        ) > 0) {
            return false;
        }

        LOTREntityInvasionSpawner spawner =
                LOTREntityInvasionSpawner.locateInvasionNearby(
                        npc,
                        expectedInvasionId
                );
        return spawner != null
                && !spawner.isDead
                && spawner.worldObj == npc.worldObj
                && expectedInvasionId.equals(spawner.getInvasionID())
                && MumakilInvasionFormationRegistry
                .isEligibleInvasion(spawner.getInvasionType());
    }
}
