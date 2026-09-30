package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/**
 * Turns canonical Master/Liege relationships into native-feeling world
 * interactions. KOME consumes the click only when there is an actionable
 * progression event; otherwise ordinary LOTR NPC interaction remains free.
 */
public final class KOMEProgressionNpcInteractionService {
    private KOMEProgressionNpcInteractionService() {
    }

    public static boolean interact(
            EntityPlayerMP player,
            KOMEWorldData data,
            LOTREntityNPC npc) {
        if(player==null||data==null||npc==null) {
            return false;
        }

        KOMEPlayerProgression progression=
            data.getProgression(
                KOMEReflection.getEntityUUID(player));

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        KOMEProgressionNpcRef clicked=
            KOMEProgressionNpcRankService.referenceOf(npc);

        if(state.getSerfdomMaster()
                .hasSameIdentity(clicked)) {
            return interactMaster(
                player,
                data,
                progression,
                state,
                npc);
        }

        if(state.getProspectiveLiege()
                .hasSameIdentity(clicked)) {
            return interactLiege(
                player,
                data,
                progression,
                state,
                npc);
        }

        return false;
    }

    private static boolean interactMaster(
            EntityPlayerMP player,
            KOMEWorldData data,
            KOMEPlayerProgression progression,
            KOMESerfKnightProgression state,
            LOTREntityNPC npc) {
        KOMESerfdomMasterService.Result validation=
            KOMESerfdomMasterService
                .validateCurrentMasterInteraction(
                    player,
                    data,
                    npc,
                    true);

        if(!validation.success) {
            return false;
        }

        if(canReceivePartingGift(player,state)) {
            KOMESerfdomMasterService.Result result=
                KOMESerfdomMasterService.conferKnighthood(
                    player,
                    data,
                    npc);

            if(!result.success) {
                player.addChatMessage(
                    new ChatComponentText(result.reason));
                return true;
            }

            KOMEProgressionNpcSpeech.say(
                player,
                npc,
                "You have fulfilled your service and proved yourself before your liege. "
                    +"Take my blessing as you leave my household. Rise now as a knight.");

            KOMEProgressionAutoCompleter.syncPlayer(
                player,
                progression);

            KOMEProgressionTitles.updatePlayerTitle(player);
            return true;
        }

        String active=state.getActiveAssignmentKind();

        if("provisioning".equals(active)) {
            return deliverProvisioning(
                player,
                data,
                progression,
                state,
                npc);
        }

        if("profession".equals(active)) {
            return deliverProfession(
                player,
                data,
                progression,
                state,
                npc);
        }

        if("courier".equals(active)) {
            return handleCourierAtMaster(
                player,
                data,
                progression,
                state,
                npc);
        }

        // An active Trial belongs to the Liege; do not steal the Master's
        // ordinary LOTR interaction while the player is carrying it out.
        if(active.length()!=0) {
            return false;
        }

        KOMESerfKnightDutyType next=
            KOMESerfKnightService.nextDuty(state);

        if(next==null) {
            return false;
        }

        long day=KOMESerfKnightService.calendarDayNow();

        if(!KOMESerfKnightService.mayIssueAssignment(
                state,
                day,
                player.getUniqueID())) {
            return false;
        }

        KOMESerfdomMasterService.Result result=
            KOMESerfdomMasterService.requestDuty(
                player,
                data,
                npc);

        if(!result.success) {
            player.addChatMessage(
                new ChatComponentText(result.reason));
            return true;
        }

        KOMESerfKnightDutyType requested=
            KOMESerfKnightDutyType.forKey(
                state.getActiveAssignmentKind());

        if(requested==KOMESerfKnightDutyType.COURIER) {
            KOMESerfCourierAssignment assignment=
                KOMESerfCourierAssignment.readFromNBT(
                    state.getDuty(requested)
                        .getAssignmentData());

            if(assignment!=null) {
                KOMECourierService.dropMessageFromMaster(
                    player,
                    npc,
                    assignment,
                    state.getSerfdomMaster());
            }

            KOMEProgressionNpcSpeech.assignCourier(
                player,
                npc);
        } else {
            KOMEProgressionNpcSpeech.assignDuty(
                player,
                npc,
                requested);
        }

        KOMEProgressionAutoCompleter.syncPlayer(
            player,
            progression);

        return true;
    }

    private static boolean deliverProvisioning(
            EntityPlayerMP player,
            KOMEWorldData data,
            KOMEPlayerProgression progression,
            KOMESerfKnightProgression state,
            LOTREntityNPC npc) {
        KOMESerfProvisioningAssignment assignment=
            KOMESerfProvisioningAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.PROVISIONING)
                    .getAssignmentData());

        if(assignment==null) {
            return false;
        }

        int delivered=
            KOMESerfProvisioningService.deliver(
                assignment,
                player.inventory);

        if(delivered<=0) {
            KOMEProgressionNpcSpeech.noMatchingProvisions(
                player,
                npc);
            return true;
        }

        state.setDutyAssignmentData(
            KOMESerfKnightDutyType.PROVISIONING,
            assignment.writeToNBT());

        if(assignment.complete()) {
            KOMESerfKnightService.completeDuty(
                progression,
                KOMESerfKnightDutyType.PROVISIONING);
        }

        data.markDirty();

        player.inventoryContainer.detectAndSendChanges();

        KOMEProgressionAutoCompleter.syncPlayer(
            player,
            progression);

        if(assignment.complete()) {
            KOMEProgressionNpcSpeech.completedProvisions(
                player,
                npc);
        } else {
            KOMEProgressionNpcSpeech.partialProvisions(
                player,
                npc);
        }

        return true;
    }

    private static boolean deliverProfession(
            EntityPlayerMP player,
            KOMEWorldData data,
            KOMEPlayerProgression progression,
            KOMESerfKnightProgression state,
            LOTREntityNPC npc) {
        KOMESerfProfessionAssignment assignment=
            KOMESerfProfessionAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.PROFESSION)
                    .getAssignmentData());

        if(assignment==null) {
            return false;
        }

        int delivered=
            KOMESerfProfessionService.deliver(
                assignment,
                player.inventory);

        if(delivered<=0) {
            KOMEProgressionNpcSpeech
                .noMatchingProfessionMaterials(
                    player,
                    npc);
            return true;
        }

        state.setDutyAssignmentData(
            KOMESerfKnightDutyType.PROFESSION,
            assignment.writeToNBT());

        KOMESerfProfessionService.completeIfReady(
            progression,
            assignment);

        data.markDirty();

        player.inventoryContainer.detectAndSendChanges();

        KOMEProgressionAutoCompleter.syncPlayer(
            player,
            progression);

        if(assignment.complete()) {
            KOMEProgressionNpcSpeech
                .completedProfessionMaterials(
                    player,
                    npc);
        } else {
            KOMEProgressionNpcSpeech
                .partialProfessionMaterials(
                    player,
                    npc);
        }

        return true;
    }

    private static boolean handleCourierAtMaster(
            EntityPlayerMP player,
            KOMEWorldData data,
            KOMEPlayerProgression progression,
            KOMESerfKnightProgression state,
            LOTREntityNPC npc) {
        KOMESerfCourierAssignment assignment=
            KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.COURIER)
                    .getAssignmentData());

        if(assignment==null) {
            return false;
        }

        if(assignment.stage==
                KOMESerfCourierAssignment.Stage.DELIVERED) {
            if(KOMECourierService.reportToMaster(
                    player,
                    data,
                    progression)) {
                KOMEProgressionNpcSpeech.completeCourier(
                    player,
                    npc);
            } else {
                player.addChatMessage(
                    new ChatComponentText(
                        "Deliver the dispatch, then return to your Master."));
            }

            return true;
        }

        if(assignment.stage==
                KOMESerfCourierAssignment.Stage.OUTBOUND
                &&!KOMECourierService.hasDispatch(
                    player,
                    assignment,
                    state.getSerfdomMaster())) {
            KOMECourierService.dropMessageFromMaster(
                player,
                npc,
                assignment,
                state.getSerfdomMaster());

            KOMEProgressionNpcSpeech.replaceCourierMessage(
                player,
                npc);

            return true;
        }

        return false;
    }

    private static boolean interactLiege(
            EntityPlayerMP player,
            KOMEWorldData data,
            KOMEPlayerProgression progression,
            KOMESerfKnightProgression state,
            LOTREntityNPC npc) {
        if(!validLiegeInteraction(
                player,
                progression,
                data,
                npc)) {
            return false;
        }

        if(state.getTrialId().length()!=0) {
            KOMESerfKnightTrialAssignment assignment=
                state.getTrialAssignment();

            if(assignment==null
                    ||assignment.stage==
                        KOMESerfKnightTrialAssignment.Stage.FAILED
                    ||state.isTrialCompleted()) {
                return false;
            }

            if("recovery".equals(assignment.trialId)
                    &&assignment.stage==
                        KOMESerfKnightTrialAssignment.Stage.ACTIVE
                    &&assignment.data.getBoolean(
                        KOMESerfKnightRecoveryService.DATA_RETRIEVED)) {
                return KOMESerfKnightRecoveryService.deliver(
                    player,
                    progression,
                    npc);
            }

            if(assignment.stage!=
                    KOMESerfKnightTrialAssignment.Stage.ASSIGNED) {
                return false;
            }

            activateTrial(
                player,
                progression,
                npc,
                assignment.trialId);

            KOMEProgressionNpcSpeech.say(
                player,
                npc,
                KOMESerfKnightService.existingTrialSpeech(
                    state.getTrialAssignment()));

            return true;
        }

        long day=KOMESerfKnightService.calendarDayNow();

        if(!KOMESerfKnightService.mayIssueAssignment(
                state,
                day,
                player.getUniqueID())) {
            return false;
        }

        KOMESerfKnightService.Result result=
            KOMESerfKnightService.assignTrial(
                state,
                player.worldObj.rand,
                day,
                player.getUniqueID());

        if(!result.success) {
            player.addChatMessage(
                new ChatComponentText(result.reason));
            return true;
        }

        activateTrial(
            player,
            progression,
            npc,
            state.getTrialId());

        data.markDirty();

        KOMEProgressionAutoCompleter.syncPlayer(
            player,
            progression);

        KOMEProgressionNpcSpeech.say(
            player,
            npc,
            KOMESerfKnightService.trialSpeech(
                state.getTrialAssignment()));

        return true;
    }

    private static void activateTrial(
            EntityPlayerMP player,
            KOMEPlayerProgression progression,
            LOTREntityNPC liege,
            String trialId) {
        if("recovery".equals(trialId)) {
            KOMESerfKnightRecoveryService.activate(
                player,
                progression,
                liege);
        } else if("defense".equals(trialId)) {
            KOMESerfKnightDefenseService.activate(
                player,
                progression,
                liege);
        } else if("escort".equals(trialId)) {
            KOMESerfKnightEscortService.activate(
                player,
                progression,
                liege);
        }
    }

    private static boolean canReceivePartingGift(
            EntityPlayerMP player,
            KOMESerfKnightProgression state) {
        if(state==null
                ||!state.isTrialCompleted()
                ||state.hasPartingGift()) {
            return false;
        }

        LOTRFaction pledge=
            LOTRLevelData.getData(player)
                .getPledgeFaction();

        if(pledge==null) {
            return false;
        }

        return LOTRLevelData.getData(player)
            .getAlignment(pledge)
            >=KOMESerfKnightService.REQUIRED_ALIGNMENT;
    }

    private static boolean validLiegeInteraction(
            EntityPlayerMP player,
            KOMEPlayerProgression progression,
            KOMEWorldData data,
            LOTREntityNPC npc) {
        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        LOTRFaction pledge=
            LOTRLevelData.getData(player)
                .getPledgeFaction();

        return progression.getCanonicalRank()==
                    KOMEProgressionRank.SERF
            &&state.getSerfdomMaster().isSet()
            &&KOMESerfKnightService.allDutiesComplete(state)
            &&(!state.hasActiveAssignment()
                ||state.getTrialId().length()!=0)
            &&!state.isLockedOut(
                KOMESerfKnightService.calendarDayNow())
            &&pledge!=null
            &&pledge==npc.getFaction()
            &&KOMEProgressionFactionResolver.matches(
                state.getSerfdomMaster().factionKey,
                npc.getFaction())
            &&!npc.isChild()
            &&KOMEProgressionNpcRankService
                .isValidFactionNpc(npc)
            &&KOMEProgressionLords
                .isCombatUnitHiringNpc(npc)
            &&KOMEProgressionNpcRankService
                .effectiveRank(data,npc)==
                    KOMEProgressionNpcRank.LORD
            &&npc.hiredNPCInfo!=null
            &&!npc.hiredNPCInfo.isActive;
    }
}
