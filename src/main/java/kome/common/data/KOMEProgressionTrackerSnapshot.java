package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/** Server-authored presentation snapshot for the movable progression HUD tracker. */
public final class KOMEProgressionTrackerSnapshot {
    public static final KOMEProgressionTrackerSnapshot EMPTY =
        new KOMEProgressionTrackerSnapshot(false,"","","",0F);

    public final boolean visible;
    public final String iconKey, objective, progress;
    public final float completion;

    public KOMEProgressionTrackerSnapshot(
            boolean visible,
            String iconKey,
            String objective,
            String progress,
            float completion) {
        this.visible=visible;
        this.iconKey=safe(iconKey);
        this.objective=safe(objective);
        this.progress=safe(progress);
        this.completion=clamp(completion);
    }

    public String signature() {
        return (visible?"1":"0")
            +'|'+iconKey
            +'|'+objective
            +'|'+progress
            +'|'+Float.floatToIntBits(completion);
    }

    public static KOMEProgressionTrackerSnapshot project(
            EntityPlayerMP player,
            KOMEPlayerProgression progression) {
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
        if("courier".equals(active))return courier(state);
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
                    requirement.required);
            }
        }

        if(assignment.drink!=null&&!assignment.drink.complete()) {
            return requirement(
                "provisioning",
                assignment.drink.description(),
                assignment.drink.delivered,
                assignment.drink.required);
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
                    requirement.required);
            }
        }

        return shown(
            "profession",
            "Return to your Master to finish this duty.",
            "Ready",
            1F);
    }

    private static KOMEProgressionTrackerSnapshot courier(
            KOMESerfKnightProgression state) {
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
                "Recipient found",
                0.75F);
        }

        return shown(
            "courier",
            "Carry the dispatch to "
                +assignment.destinationName+".",
            "En route",
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
                "Complete your Trial of Knighthood.",
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
            "Complete your Trial of Knighthood.",
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
                "Find a Serfdom Master in your pledged faction.",
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
            String objective=
                KOMESerfKnightService.mayIssueAssignment(
                    state,
                    day,
                    player.getUniqueID())
                    ?"Request another duty from your Master."
                    :"Return to your Master when more work is available.";

            return shown(
                "progression",
                objective,
                completed+" / "+total+" duties",
                completed/(float)total);
        }

        if(!state.getProspectiveLiege().isSet()) {
            return shown(
                "progression",
                "Seek a prospective Liege for your Trial of Knighthood.",
                "Duties complete",
                0.6F);
        }

        if(state.getTrialId().length()==0) {
            String objective=
                KOMESerfKnightService.mayIssueAssignment(
                    state,
                    day,
                    player.getUniqueID())
                    ?"Request your Trial of Knighthood from your Liege."
                    :"Return to your Liege when another assignment is available.";

            return shown(
                "progression",
                objective,
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
                "Return to your Master for Knighthood.",
                "Ready",
                1F);
        }

        return shown(
            "progression",
            "Complete your Trial of Knighthood.",
            "Trial",
            0.75F);
    }

    private static KOMEProgressionTrackerSnapshot requirement(
            String icon,
            String description,
            int current,
            int required) {
        return shown(
            icon,
            "Bring "+description+" to your Master.",
            current+" / "+required,
            required<=0?0F:current/(float)required);
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
