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

    static KOMEProgressionTrackerSnapshot opening(LOTRFaction pledge) {
        return shown("progression", pledge!=null&&pledge.isPlayableAlignmentFaction()
            ?"Find a Master to serve":"Pledge to a faction", "", 0F);
    }

    static KOMEProgressionTrackerSnapshot seekLiege() {
        return shown("standing_trial_ready", "Seek a prospective Liege", "", 0F);
    }

    public boolean hasNumericProgress() { return !progress.isEmpty(); }

    public static KOMEProgressionTrackerSnapshot project(
            EntityPlayerMP player,
            KOMEPlayerProgression progression) {
        if(progression!=null&&progression.getCanonicalRank()==KOMEProgressionRank.WANDERER
                &&!progression.getSerfKnightProgression().getSerfdomMaster().isSet())
            return opening(player==null?null:LOTRLevelData.getData(player).getPledgeFaction());
        if(player!=null&&progression!=null&&progression.getCanonicalRank()==KOMEProgressionRank.KNIGHT){
            LOTRFaction faction=LOTRLevelData.getData(player).getPledgeFaction();String key=faction==null?"":faction.codeName();
            double alignment=faction==null?0:LOTRLevelData.getData(player).getAlignment(faction);
            KOMELordshipTrial trial=progression.getLordship().assignment();
            if(trial!=null)return lordship(trial);
            KOMEKnightCommission commission=progression.getKnightService().assignment();
            if(commission==null){
                if(KOMELordshipTrialService.prerequisites(progression,alignment,key))return shown("commission","Seek a Prince for your Trial of Lordship","",0F);
                int credits=progression.getKnightService().qualifyingTypes(key).size();
                return shown("commission",KOMELordshipTrialPresentation.status(progression,alignment,key),credits<3?credits+" / 3 commissions":(int)alignment+" / 2000 alignment",credits<3?credits/3F:(float)alignment/2000F);
            }
            return commission(commission);
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
                "",
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
            "",
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
                "",
                0F);
        }

        for(KOMESerfProfessionAssignment.Requirement requirement:
                assignment.requirements) {
            if(!requirement.complete()) {
                return requirement(
                    "profession",
                    requirement.required+" "+KOMEProgressionGoodsRules.name(requirement.requestedStack(),requirement.displayName),
                    requirement.delivered,
                    requirement.required,
                    professionStack(requirement));
            }
        }

        return shown(
            "profession",
            "Return to your Master to finish this duty.",
            "",
            1F);
    }

    static KOMEProgressionTrackerSnapshot commission(KOMEKnightCommission a){
        if(a.activationPending)return shown(a.type==KOMEKnightCommission.Type.RELIEF?"provisioning":"commission",KOMEKnightCommissionPresentation.objective(a),"",0F);
        if(a.stage==KOMEKnightCommission.Stage.FAILED)return shown("commission",KOMEKnightCommissionPresentation.objective(a),"",0F);
        int required=KOMEKnightCommissionPresentation.required(a),done=KOMEKnightCommissionPresentation.completed(a);
        String objective=KOMEKnightCommissionPresentation.objective(a);
        if(a.type==KOMEKnightCommission.Type.RELIEF&&a.stage==KOMEKnightCommission.Stage.ACTIVE&&a.encounterCreated)objective="Deliver the requested supplies";
        ItemStack item=a.type==KOMEKnightCommission.Type.STOLEN_GOODS?KOMEProgressionLostItems.stack(a):null;
        if(a.type==KOMEKnightCommission.Type.RELIEF&&a.stage==KOMEKnightCommission.Stage.ACTIVE&&a.encounterCreated){
            done=0;required=0;for(KOMEKnightCommission.Goods g:a.goods){done+=g.delivered;required+=g.required;}
        } else if(!KOMEKnightCommissionPresentation.combatCounter(a))required=0;
        return new KOMEProgressionTrackerSnapshot(true,a.type==KOMEKnightCommission.Type.RELIEF?"provisioning":"commission",objective,required>0?done+"/"+required:"",required>0?done/(float)required:0F,item);
    }
    static KOMEProgressionTrackerSnapshot lordship(KOMELordshipTrial t){
        if(!t.nativeInvasion)return t.ready()?shown("commission","Report to your Prince","",0F):commission(t.objective);
        boolean combat=t.objective.stage==KOMEKnightCommission.Stage.ACTIVE&&t.objective.encounterCreated&&!t.objective.activationPending;
        return shown("commission",t.ready()?"Report to your Prince":t.objective.activationPending?"Return to your Prince for reassignment":t.objective.stage==KOMEKnightCommission.Stage.FAILED?"Request another trial from your Prince":combat?"Defeat the invasion and survive":"Travel to the invasion ground",combat?t.invasionRemaining+" / "+t.invasionSize+" strength remaining":"",combat&&t.invasionSize>0?1-t.invasionRemaining/(float)t.invasionSize:0F);
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
                "",
                0F);
        }

        if(assignment.confirmedRecipientDeath) {
            return shown("courier","Return to your Master for new instructions.","",0F);
        }
        if(assignment.stage==
                KOMESerfCourierAssignment.Stage.DELIVERED) {
            return shown(
                "courier",
                "Return to your Master and report the delivery.",
                "",
                0F);
        }

        if(assignment.recipient!=null
                &&assignment.recipient.isSet()) {
            return shown(
                "courier",
                "Deliver the message",
                "",
                0F);
        }

        return shown(
            "courier",
            "Deliver the message",
            "",
            0F);
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
                "",
                0F);
        }

        if(assignment.stage==
                KOMESerfKnightTrialAssignment.Stage.FAILED) {
            return shown(
                assignment.trialId,
                "This trial is lost. Seek a new Liege.",
                "",
                0F);
        }

        if("escort".equals(assignment.trialId)) {
            if(assignment.stage==
                    KOMESerfKnightTrialAssignment.Stage.ASSIGNED) {
                return shown(
                    "escort",
                    "Meet the traveler",
                    "",
                    0F);
            }

            return shown(
                "escort",
                assignment.stage==KOMESerfKnightTrialAssignment.Stage.OBJECTIVE_COMPLETE?"Report to your Liege":"Escort the traveler to safety",
                "",0F);
        }

        if("recovery".equals(assignment.trialId)) {
            boolean retrieved=
                assignment.data.getBoolean(
                    KOMESerfKnightRecoveryService.DATA_RETRIEVED);

            ItemStack item=assignment.data.hasKey("RecoveryItem",10)?ItemStack.loadItemStackFromNBT(assignment.data.getCompoundTag("RecoveryItem")):null;
            return new KOMEProgressionTrackerSnapshot(true,"recovery",retrieved?"Return the recovered item to your Liege.":"Recover the lost item.","",0F,item);
        }

        if("defense".equals(assignment.trialId)) {
            if(assignment.stage==
                    KOMESerfKnightTrialAssignment.Stage.ASSIGNED) {
                return shown(
                    "defense",
                    "Defend your people from the attack.",
                    "",
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
                    "",
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
            "",
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
                "",
                0F);
        }
        if(state.isTrialCompleted())return state.hasLiege()?promotion(player):shown("standing_trial_ready","Seek an eligible replacement Liege to hear your completed trial report.","",0F);

        if(!state.getSerfdomMaster().isSet()) {
            return shown(
                "progression",
                "Find a Master in your pledged faction.",
                "",
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
                "Talk with your Master",
                "",0F);
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

            return seekLiege();
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
                "",
                0.7F);
        }

        return shown(
            "progression",
            "Complete your Trial of Standing.",
            "",
            0.75F);
    }

    private static KOMEProgressionTrackerSnapshot promotion(EntityPlayerMP player) {
            double alignment=currentAlignment(player);

            if(alignment<
                    KOMESerfKnightService.REQUIRED_ALIGNMENT) {
                int current=
                    (int)Math.floor(Math.max(0D,alignment));

                return shown(
                    "progression",
                    "Reach 150 faction alignment before returning to your Liege.",
                    current+" / "
                        +KOMESerfKnightService.REQUIRED_ALIGNMENT,
                    current/(float)
                        KOMESerfKnightService.REQUIRED_ALIGNMENT);
            }

            return shown(
                "progression",
                "Return to your Liege to receive your new standing.",
                "",
                1F);
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
            "",
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
