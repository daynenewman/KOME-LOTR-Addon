package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Server-authored presentation snapshot for the movable progression HUD tracker. */
public final class KOMEProgressionTrackerSnapshot {
    public static final KOMEProgressionTrackerSnapshot EMPTY =
        new KOMEProgressionTrackerSnapshot(false,"","","",0F);

    public final boolean visible;
    public final String iconKey, objective, progress;
    public final float completion;
    public final ItemStack requestedItem;

    public KOMEProgressionTrackerSnapshot(
            boolean visible,
            String iconKey,
            String objective,
            String progress,
            float completion) {
        this(visible,iconKey,objective,progress,completion,null);
    }

    public KOMEProgressionTrackerSnapshot(boolean visible,String iconKey,String objective,String progress,float completion,ItemStack requestedItem) {
        this.visible=visible;
        this.iconKey=safe(iconKey);
        this.objective=safe(objective);
        this.progress=safe(progress);
        this.completion=clamp(completion);
        this.requestedItem=requestedItem==null||requestedItem.getItem()==null?null:requestedItem.copy();
    }

    public String signature() {
        return (visible?"1":"0")
            +'|'+iconKey
            +'|'+objective
            +'|'+progress
            +'|'+Float.floatToIntBits(completion)
            +'|'+(requestedItem==null?"":requestedItem.writeToNBT(new net.minecraft.nbt.NBTTagCompound()).toString());
    }

    /** Client-safe hint derived entirely from this server-authored snapshot. */
    public boolean isReadyForStandingTrial() {
        return visible
            && "standing_trial_ready".equals(iconKey);
    }

    public static KOMEProgressionTrackerSnapshot project(
            EntityPlayerMP player,
            KOMEPlayerProgression progression) {
        if(player!=null&&progression!=null&&progression.getCanonicalRank()==KOMEProgressionRank.KNIGHT){
            LOTRFaction faction=LOTRLevelData.getData(player).getPledgeFaction();String key=faction==null?"":faction.codeName();
            double alignment=faction==null?0:LOTRLevelData.getData(player).getAlignment(faction);
            String standing=KOMELordshipTrialPresentation.standing(progression,alignment,key);
            KOMELordshipTrial trial=progression.getLordship().assignment();
            if(trial!=null)return shown("commission",KOMELordshipTrialPresentation.objective(trial),KOMELordshipTrialPresentation.title(trial),trial.ready()?1F:0F);
            KOMEKnightCommission commission=progression.getKnightService().assignment();
            if(commission==null)return shown("commission",KOMEProgressionFactionResolver.missingNativeLiege(key)?KOMEKnightCommissionPresentation.unavailableLiege():KOMELordshipTrialPresentation.status(progression,alignment,key),standing,progression.getKnightService().qualifyingTypes(key).size()/3F);
            return shown("commission",KOMEKnightCommissionPresentation.objective(commission),
                KOMEKnightCommissionPresentation.title(commission.type),
                commission.stage==KOMEKnightCommission.Stage.READY_TO_REPORT?1F:0F);
        }
        if(player==null
                ||progression==null
                ||progression.getCanonicalRank()!=KOMEProgressionRank.SERF) {
            return EMPTY;
        }

        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        String active=state.getActiveAssignmentKind();

        if("provisioning".equals(active))return provisioning(state);
        if("profession".equals(active))return profession(state);
        if("courier".equals(active))return courier(player,state);
        if("trial".equals(active))return trial(player,state);

        return nextStep(player,state);
    }

    private static KOMEProgressionTrackerSnapshot provisioning(
            KOMESerfKnightProgression state) {
        KOMESerfProvisioningAssignment assignment=
            KOMESerfProvisioningAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.PROVISIONING)
                    .getAssignmentData());

        if(assignment==null) {
            return shown(
                "provisioning",
                "Bring the requested provisions to your Master.",
                "In progress",
                0F);
        }

        for(KOMESerfProvisioningAssignment.Requirement requirement:
                assignment.foods) {
            if(!requirement.complete()) {
                return requirement(
                    "provisioning",
                    requirement.description(),
                    requirement.delivered,
                    requirement.required,
                    requirement.requestedStack());
            }
        }

        if(assignment.drink!=null&&!assignment.drink.complete()) {
            return requirement(
                "provisioning",
                assignment.drink.description(),
                assignment.drink.delivered,
                assignment.drink.required,
                assignment.drink.requestedStack());
        }

        return shown(
            "provisioning",
            "Return to your Master to finish this duty.",
            "Ready",
            1F);
    }

    private static KOMEProgressionTrackerSnapshot profession(
            KOMESerfKnightProgression state) {
        KOMESerfProfessionAssignment assignment=
            KOMESerfProfessionAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.PROFESSION)
                    .getAssignmentData());

        if(assignment==null) {
            return shown(
                "profession",
                "Bring the requested materials to your Master.",
                "In progress",
                0F);
        }

        for(KOMESerfProfessionAssignment.Requirement requirement:
                assignment.requirements) {
            if(!requirement.complete()) {
                return requirement(
                    "profession",
                    requirement.required+" "+requirement.displayName,
                    requirement.delivered,
                    requirement.required,
                    professionStack(requirement));
            }
        }

        return shown(
            "profession",
            "Return to your Master to finish this duty.",
            "Ready",
            1F);
    }

    private static KOMEProgressionTrackerSnapshot courier(
            EntityPlayerMP player,KOMESerfKnightProgression state) {
        KOMESerfCourierAssignment assignment=
            KOMESerfCourierAssignment.readFromNBT(
                state.getDuty(
                    KOMESerfKnightDutyType.COURIER)
                    .getAssignmentData());

        if(assignment==null) {
            return shown(
                "courier",
                "Carry out your Master's delivery.",
                "In progress",
                0F);
        }

        if(assignment.confirmedRecipientDeath) {
            long wait=assignment.replacementTicksRemaining(player.worldObj.getTotalWorldTime());
            return shown("courier","The recipient has died. Return the current letter to your Master.",wait>0?"Copy in "+KOMECourierIssuance.cooldownText(wait):assignment.replacements>=3?"No copies remain":"Copy available",0.9F);
        }
        String cooldown=assignment.documentIssued&&assignment.replacementTicksRemaining(player.worldObj.getTotalWorldTime())>0L
            ?"Copy in "+KOMECourierIssuance.cooldownText(assignment.replacementTicksRemaining(player.worldObj.getTotalWorldTime())):assignment.replacements>=3?"No copies remain":"Copy available";
        if(assignment.stage==
                KOMESerfCourierAssignment.Stage.DELIVERED) {
            return shown(
                "courier",
                "Return to your Master and report the delivery.",
                "Delivered",
                1F);
        }

        if(assignment.recipient!=null
                &&assignment.recipient.isSet()) {
            return shown(
                "courier",
                "Deliver the dispatch to "
                    +assignment.recipient.displayName+".",
                cooldown,
                0.75F);
        }

        return shown(
            "courier",
            "Carry the dispatch to "
                +assignment.destinationName+".",
            cooldown,
            0.25F);
    }

    private static KOMEProgressionTrackerSnapshot trial(
            EntityPlayerMP player,
            KOMESerfKnightProgression state) {
        KOMESerfKnightTrialAssignment assignment=
            state.getTrialAssignment();

        if(assignment==null) {
            return shown(
                "progression",
                "Complete your Trial of Standing.",
                "Trial",
                0F);
        }

        if(assignment.stage==
                KOMESerfKnightTrialAssignment.Stage.FAILED) {
            return shown(
                assignment.trialId,
                "This trial is lost. Seek a new Liege.",
                "Failed",
                0F);
        }

        if("escort".equals(assignment.trialId)) {
            if(assignment.stage==
                    KOMESerfKnightTrialAssignment.Stage.ASSIGNED) {
                return shown(
                    "escort",
                    "Meet your charge at your Liege's side.",
                    "Not begun",
                    0F);
            }

            int distance=
                KOMESerfKnightEscortService.trackerDistance(
                    player,
                    assignment);

            return shown(
                "escort",
                "Escort your charge safely through the journey.",
                distance+" / "
                    +KOMESerfKnightEscortService.MIN_ESCORT_DISTANCE
                    +" blocks",
                distance/(float)
                    KOMESerfKnightEscortService.MIN_ESCORT_DISTANCE);
        }

        if("recovery".equals(assignment.trialId)) {
            boolean retrieved=
                assignment.data.getBoolean(
                    KOMESerfKnightRecoveryService.DATA_RETRIEVED);

            return retrieved
                ?shown(
                    "recovery",
                    "Return the recovered item to your Liege.",
                    "Recovered",
                    0.85F)
                :shown(
                    "recovery",
                    "Recover the lost item.",
                    "Searching",
                    0.2F);
        }

        if("defense".equals(assignment.trialId)) {
            if(assignment.stage==
                    KOMESerfKnightTrialAssignment.Stage.ASSIGNED) {
                return shown(
                    "defense",
                    "Defend your people from the attack.",
                    "Awaiting attack",
                    0F);
            }

            int total=
                KOMESerfKnightDefenseService
                    .trackerTotalAttackers(assignment);

            int dead=
                KOMESerfKnightDefenseService
                    .trackerDefeatedAttackers(assignment);

            if(total<=0) {
                return shown(
                    "defense",
                    "Defeat the attackers threatening your people.",
                    "In progress",
                    0F);
            }

            return shown(
                "defense",
                "Defeat the attackers threatening your people.",
                dead+" / "+total+" defeated",
                dead/(float)total);
        }

        return shown(
            "progression",
            "Complete your Trial of Standing.",
            "Trial",
            0F);
    }

    private static KOMEProgressionTrackerSnapshot nextStep(
            EntityPlayerMP player,
            KOMESerfKnightProgression state) {
        long day=KOMESerfKnightService.calendarDayNow();

        if(state.isLockedOut(day)) {
            return shown(
                "progression",
                "Progression service is locked after betrayal.",
                "Locked",
                0F);
        }

        if(!state.getSerfdomMaster().isSet()) {
            return shown(
                "progression",
                "Find a Master in your pledged faction.",
                "Find a Master",
                0F);
        }

        int completed=0;

        for(KOMESerfKnightDutyType type:
                KOMESerfKnightDutyType.values()) {
            if(state.getDuty(type).isCompleted()) {
                completed++;
            }
        }

        int total=KOMESerfKnightDutyType.values().length;

        if(completed<total) {
            if(!KOMESerfKnightService.mayIssueAssignment(
                    state,
                    day,
                    player.getUniqueID())) {
                return dailyComplete(
                    completed,
                    total);
            }

            return shown(
                "progression",
                "Request another duty from your Master.",
                completed+" / "+total+" duties",
                completed/(float)total);
        }

        if(!state.hasLiege()) {
            String factionKey=state.getSerfdomMaster().factionKey;
            int required=KOMEStandingTrialEligibility.requiredAlignment(factionKey);
            double alignment=currentAlignment(player);
            String title=KOMEFactionProgressionTitles.title(factionKey,KOMEProgressionRank.SERF);
            if(!KOMEStandingTrialEligibility.meetsAlignment(state,alignment,factionKey)) {
                int current=(int)Math.floor(alignment);
                return shown("alignment","Earn faction alignment as a "+title+" before seeking a Liege.",current+" / "+required,current/(float)required);
            }
            if(!KOMESerfKnightService.mayIssueTrial(
                    state,
                    day,
                    player.getUniqueID())) {
                return trialDailyComplete();
            }

            return shown(
                "standing_trial_ready",
                "Seek an eligible Liege for your Trial of Standing.\n"+title,
                (int)Math.floor(alignment)+" / "+required,
                0.6F);
        }

        if(state.getTrialId().length()==0) {
            if(!KOMESerfKnightService.mayIssueTrial(
                    state,
                    day,
                    player.getUniqueID())) {
                return trialDailyComplete();
            }

            return shown(
                "standing_trial_ready",
                "Request your Trial of Standing from your Liege.",
                "Ready for trial",
                0.7F);
        }

        if(state.isTrialCompleted()) {
            double alignment=currentAlignment(player);

            if(alignment<
                    KOMESerfKnightService.REQUIRED_ALIGNMENT) {
                int current=
                    (int)Math.floor(Math.max(0D,alignment));

                return shown(
                    "progression",
                    "Reach 150 faction alignment before returning to your Master.",
                    current+" / "
                        +KOMESerfKnightService.REQUIRED_ALIGNMENT,
                    current/(float)
                        KOMESerfKnightService.REQUIRED_ALIGNMENT);
            }

            return shown(
                "progression",
                "Return to your Master to receive your new standing.",
                "Ready",
                1F);
        }

        return shown(
            "progression",
            "Complete your Trial of Standing.",
            "Trial",
            0.75F);
    }

    private static KOMEProgressionTrackerSnapshot dailyComplete(
            int completed,
            int total) {
        return shown(
            "daily_complete",
            "Today's duty is complete.",
            completed+" / "+total+" duties",
            total<=0?1F:completed/(float)total);
    }

    private static KOMEProgressionTrackerSnapshot trialDailyComplete() {
        return shown(
            "daily_complete",
            "You have already received a Trial of Standing today.",
            "Return another day",
            1F);
    }

    private static KOMEProgressionTrackerSnapshot requirement(
            String icon,
            String description,
            int current,
            int required,
            ItemStack target) {
        return new KOMEProgressionTrackerSnapshot(
            true,
            icon,
            "Bring "+description+" to your Master.",
            current+" / "+required,
            required<=0?0F:current/(float)required,
            target);
    }

    private static ItemStack professionStack(KOMESerfProfessionAssignment.Requirement r) {
        Item item=r.itemKey.length()==0?Item.getItemById(r.itemId):(Item)Item.itemRegistry.getObject(r.itemKey);
        return item==null?null:new ItemStack(item,1,r.damage);
    }

    private static KOMEProgressionTrackerSnapshot shown(
            String icon,
            String objective,
            String progress,
            float completion) {
        return new KOMEProgressionTrackerSnapshot(
            true,
            icon,
            objective,
            progress,
            completion);
    }

    private static double currentAlignment(
            EntityPlayerMP player) {
        LOTRFaction pledge=
            LOTRLevelData.getData(player).getPledgeFaction();

        return pledge==null
            ?0D
            :LOTRLevelData.getData(player).getAlignment(pledge);
    }

    private static float clamp(float value) {
        return Math.max(0F,Math.min(1F,value));
    }

    private static String safe(String value) {
        return value==null?"":value;
    }
}
