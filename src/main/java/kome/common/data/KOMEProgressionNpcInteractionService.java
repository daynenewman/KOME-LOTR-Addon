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

        if(KOMEPartingGiftService.drop(player,data,npc)){
            KOMEProgressionNpcSpeech.say(player,npc,"You have served me well. Take this gift with my blessing for the road.");
            KOMEProgressionAutoCompleter.syncPlayer(player,progression);
            return true;
        }

        if(progression.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&state.getLiege().hasSameIdentity(clicked)) {
            KOMEKnightCommission commission=progression.getKnightService().assignment();
            return commission!=null&&commission.stage!=KOMEKnightCommission.Stage.OFFERED
                &&KOMEKnightCommissionService.acceptOrReport(player,npc);
        }

        if(state.getSerfdomMaster()
                .hasSameIdentity(clicked)) {
            return interactMaster(
                player,
                data,
                progression,
                state,
                npc);
        }

        if(state.getLiege()
                .hasSameIdentity(clicked)) {
            return interactLiege(
                player,
                data,
                progression,
                state,
                npc);
        }

        if(state.getFormerMaster().hasSameIdentity(clicked)
                &&progression.getCanonicalRank().order>=KOMEProgressionRank.KNIGHT.order
                &&npc.isEntityAlive()&&player.worldObj==npc.worldObj
                &&player.getDistanceSqToEntity(npc)<=64D) {
            if(KOMEPartingGiftService.drop(player,data,npc)){
                KOMEProgressionAutoCompleter.syncPlayer(player,progression);
                KOMEProgressionNpcSpeech.say(player,npc,"Take this pouch with my blessing for the road.");return true;
            }
            KOMEProgressionNpcSpeech.say(player,npc,
                "It is good to see you again. I remember your faithful service in my household.");
            // Acknowledgement must not replace ordinary trading or conversation.
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

        if(canReceiveKnighthood(player,state)) {
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
                    +"Take my blessing as you leave my household. Rise now to your new standing.");

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

        lotr.common.fac.LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(next==null&&!state.hasLiege()&&pledge!=null&&KOMEStandingTrialEligibility.meetsAlignment(state,
                LOTRLevelData.getData(player).getAlignment(pledge),pledge.codeName())){
            KOMEProgressionNpcSpeech.say(player,npc,KOMEProgressionNativeAuthority.guidance(pledge.codeName()));return true;
        }
        if(next==null&&state.getLastAssignmentEpochDay()==KOMESerfKnightService.calendarDayNow()){KOMEProgressionNpcSpeech.sameDay(player,npc);return true;}
        if(next==null) {
            return false;
        }

        long day=KOMESerfKnightService.calendarDayNow();

        if(!KOMESerfKnightService.mayIssueAssignment(
                state,
                day,
                player.getUniqueID())) {
            KOMEProgressionNpcSpeech.sameDay(player,npc);
            return true;
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
            KOMEProgressionServiceRewards.duty(player,state,KOMESerfKnightDutyType.PROVISIONING);
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
            KOMEProgressionServiceRewards.duty(player,state,KOMESerfKnightDutyType.PROFESSION);
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
        return KOMECourierIssuance.interact(player,data,npc);
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

        // A new Trial of Standing is now accepted explicitly through the normal
        // Talk / Hire / Quest interaction GUI. Do not assign it just because the
        // player right-clicked their committed Liege.
        return false;
    }

    public static void activateTrial(
            EntityPlayerMP player,
            KOMEPlayerProgression progression,
            LOTREntityNPC liege,
            String trialId) {
        if(player==null||progression==null||liege==null||player.worldObj.isRemote)return;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);
        if(!validLiegeInteraction(player,progression,world,liege))return;
        KOMESerfKnightTrialAssignment before=progression.getSerfKnightProgression().getTrialAssignment();
        if(before==null||before.stage!=KOMESerfKnightTrialAssignment.Stage.ASSIGNED)return;
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
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        if(state.getTrialAssignment()!=null&&state.getTrialAssignment().stage==KOMESerfKnightTrialAssignment.Stage.ASSIGNED){
            KOMEProgressionEncounterCleanup.failTrial(world,player.worldObj,state);
            KOMEProgressionNpcSpeech.say(player,liege,"I cannot arrange this trial safely here. Seek new service before attempting another trial.");
            KOMEProgressionAutoCompleter.syncPlayer(player,progression);
        }
    }

    private static boolean canReceiveKnighthood(
            EntityPlayerMP player,
            KOMESerfKnightProgression state) {
        if(state==null
                ||!state.isTrialCompleted()) {
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
                    KOMEProgressionLiegePolicy.requiredSuperior(progression.getCanonicalRank())
            &&npc.hiredNPCInfo!=null
            &&!npc.hiredNPCInfo.isActive;
    }
}
