package kome.common.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import static kome.common.data.KOMEKnightCommission.*;

/** All new charge language is localized here, outside server decisions. */
public final class KOMEKnightCommissionPresentation {
    private KOMEKnightCommissionPresentation() {}
    public static String title(Type type) { return type==Type.NATIVE_INVASION?"Repel an Invasion":type==Type.RESCUE_PATROL?"Rescue the Missing Patrol":type==Type.PROTECT_LIEGE?"Protect Your Liege":type==Type.RAID_CAMP?"Raid an Enemy Field Post":type==Type.WAR_PLANS?"Steal the War Plans":type==Type.FIELD_COMMAND?"Lead the Patrol":type==Type.GO_FOR_HELP?"Go for Help!":text("title."+type.name().toLowerCase(java.util.Locale.ROOT)); }
    private static String text(String key,Object... args) { return KOMEProgressionLanguage.text("kome.commission."+key,args); }
    public static String objective(KOMEKnightCommission a) {
        if(a.activationPending)return a.type==Type.FIELD_COMMAND
            ?"The patrol could not be prepared safely. Return to your Liege for fresh orders."
            :"The encounter could not be prepared safely. Return to your Liege for fresh orders, or approach the marked ground to retry.";
        if(a.stage==Stage.FAILED)return a.failureReason.isEmpty()?text("failed"):a.failureReason;
        if(a.stage==Stage.READY_TO_REPORT)return text("report");
        if(a.stage==Stage.OFFERED)return text("receive_orders");
        if(a.type==Type.WAR_PLANS&&a.detected&&!a.dispatchStolen)return "The guards are alerted. Withdraw to your Liege and crouch when requesting fresh orders. A kill cannot replace the stolen dispatch.";
        if(a.type==Type.GO_FOR_HELP&&a.encounterCreated&&a.phase!=Phase.ENGAGE)return a.phase==Phase.INVESTIGATE?"Speak to the people at the threatened refuge.":a.phase==Phase.SEEK_HELP?"Ask "+a.helpCommander.displayName+" at the marked garrison for reinforcements.":"Lead the four soldiers back to the threatened refuge. At least one must arrive with you before the defense begins.";
        if(a.type==Type.WAR_PLANS)return a.dispatchStolen?"Escape with the sealed dispatch. Leave the enemy post and lose pursuers, then return it in your hand to your Liege.":a.encounterCreated?"Find the officer at the marked field post. Approach from behind, sneak, and interact with an empty hand. Avoid the guards' gaze.":"Find the enemy military post marked in hostile territory.";
        if(a.type==Type.RESCUE_PATROL&&a.phase==Phase.EXTRACT)return "Lead the surviving patrol member to the marked refuge. Keep close; speak to follow, or sneak and speak to wait.";
        if(a.type==Type.RAID_CAMP&&a.phase==Phase.ESCAPE)return "Carry the recovered property out of the enemy post, then return it to your Liege.";
        if(a.type==Type.PROTECT_LIEGE&&!a.encounterCreated)return "Return to your Liege and stay nearby. Raiders threaten the household; the attack begins only with you and your Liege present.";
        if(a.type==Type.RELIEF&&a.tier>0&&!a.threatResolved&&a.encounterCreated)return "Defend the people at the marked refuge and hand the requested supplies to their representative. Both tasks must be fulfilled.";
        if(!a.encounterCreated)return text("travel");
        if(a.type!=Type.RELIEF&&!a.threatResolved)return a.complicationTriggered?"The enemy has circled behind you. Break the counterattack and protect those entrusted to you.":text("repel");
        String result=text("objective."+a.type.name().toLowerCase(java.util.Locale.ROOT),location(a));
        if(a.type==Type.RELIEF){for(Goods good:a.goods){Item item=(Item)Item.itemRegistry.getObject(good.itemKey);String name=item==null?text("provisions"):new ItemStack(item,1,good.damage).getDisplayName();result+="\n"+name+": "+good.delivered+" / "+good.required;}}
        if(KOMEKnightCommissionService.usesProperty(a)&&a.threatResolved)result=text("recover");
        return result;
    }
    public static int required(KOMEKnightCommission a){
        if(a.stage==Stage.ACTIVE&&a.encounterCreated&&!a.threatResolved&&a.type!=Type.RELIEF){
            int total=0;for(Actor actor:a.actors)if(actor.role==Role.ENEMY)total++;return Math.max(1,total);
        }
        return 1;
    }
    public static boolean combatCounter(KOMEKnightCommission a) {
        return a.stage==Stage.ACTIVE&&a.encounterCreated&&!a.threatResolved&&a.type!=Type.RELIEF&&a.type!=Type.WAR_PLANS;
    }
    static String stageLabel(KOMEKnightCommission a){
        if(a.activationPending)return "Preparing";
        if(a.stage==Stage.OFFERED)return "Receive orders";
        if(a.stage==Stage.READY_TO_REPORT)return "Ready to report";
        if(a.stage==Stage.FAILED)return "Failed";
        if(!a.encounterCreated)return "Traveling";
        return combatCounter(a)?"Battle":"Complete the field objective";
    }
    public static int completed(KOMEKnightCommission a){
        if(a.stage==Stage.ACTIVE&&a.encounterCreated&&!a.threatResolved&&a.type!=Type.RELIEF){
            int dead=0;for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&actor.dead)dead++;return dead;
        }
        return a.stage==Stage.REPORTED?1:0;
    }
    public static String speech(KOMEKnightCommission a,String event) {
        if("assigned".equals(event)||"offer".equals(event)){
            if(a.tier>0){String orders=a.type==Type.RESCUE_PATROL?"Our patrol is missing near the marked ground. Find the survivors, break the ambush, and lead them to the marked refuge.":a.type==Type.PROTECT_LIEGE?"My scouts have found signs of an attack upon this household. Stay at my side and meet the raiders before they reach me.":a.type==Type.RAID_CAMP?"Cross into enemy lands. Strike their field post, defeat its officer, recover our captured property, and bring it safely out.":a.type==Type.WAR_PLANS?"Their officer carries sealed orders. Enter the marked enemy post, approach unseen, and lift the dispatch with an empty hand while crouching. Escape and bring it to me; bloodshed alone will not serve.":a.type==Type.FIELD_COMMAND?"I entrust four of our soldiers to you. Lead them against the raiders at the marked ground. Bring back whom you can, and account for the fallen.":a.type==Type.GO_FOR_HELP?"Visit the threatened refuge. Hear what aid they need, seek the marked commander, and lead a relief party back to defend our folk.":"";
                if(!orders.isEmpty())return confidence(a)+" "+orders;
            }
            lotr.common.fac.LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(a.enemyFaction);
            String foe=enemy==null?"the raiders":enemy.factionName();
            String opening="rohan".equals(KOMEAlliance.normalizeFactionKey(a.faction))?text("rohan_orders"):
                "gondor".equals(KOMEAlliance.normalizeFactionKey(a.faction))?text("gondor_orders"):"";
            return (a.tier>0?confidence(a)+" ":"")+(opening.isEmpty()?"":opening+" ")+text("speech."+a.type.name().toLowerCase(java.util.Locale.ROOT),location(a),foe,KOMEProgressionLostItems.name(a));
        }
        if("progress".equals(event))return objective(a);
        return text("speech."+event,location(a));
    }
    static String confidence(KOMEKnightCommission a){
        String f=KOMEAlliance.normalizeFactionKey(a.faction);
        if(f.equals("rohan"))return a.tier==1?"Let us see whether your deeds match your oath, rider.":a.tier==2?"You have kept faith with our folk. I trust you with this road.":"I would trust this charge only to a rider of proven courage.";
        if(f.equals("gondor"))return a.tier==1?"Prove that your oath can be trusted beyond these walls.":a.tier==2?"Your service has been steadfast. Our people now depend upon your judgment.":"For Gondor, I entrust you with a matter worthy of a lord's courage.";
        if(f.contains("dwarf")||f.equals("durinsfolk")||f.equals("bluemountains"))return a.tier==1?"An oath must be hammered into deeds.":a.tier==2?"Your work has held true. I place this charge in your hands.":"You have earned my trust. See this dangerous work through, for our halls and kin.";
        if(f.contains("elf")||f.contains("elv")||f.equals("lothlorien"))return a.tier==1?"Let your care for our people be shown upon the road.":a.tier==2?"Your watch has been faithful. I ask more of you now.":"I entrust this peril to your courage and discernment. Return safely to us.";
        if(f.equals("mordor")||f.equals("isengard")||f.equals("gundabad")||f.equals("angmar")||f.equals("dolguldur"))return a.tier==1?"We shall see whether you are useful outside these walls.":a.tier==2?"You have proved useful. Do not waste the trust I give you.":"This charge calls for steel and cunning. Succeed, and your standing will be remembered.";
        return a.tier==1?"Show me that I may rely upon you in the field.":a.tier==2?"Your earlier service gives me confidence in you.":"I entrust you with this peril because you have shown courage and sound judgment.";
    }
    static String location(KOMEKnightCommission a){return "EXISTING_VERIFIED".equals(a.destinationProof.getString("Status"))?"the shelter marked on your map":"the ground marked on your map";}
    public static String summary(KOMEPlayerProgression p) {return summary(p,p.getSerfKnightProgression().getLiege().factionKey);}
    public static String summary(KOMEPlayerProgression p,String faction) {
        KOMEKnightServiceRecord s=p.getKnightService();KOMEKnightCommission a=s.assignment();KOMEProgressionNpcRef liege=p.getSerfKnightProgression().getLiege();
        String summary="\n"+text("liege",liege.isSet()?liege.displayName:text("none"))+"\n"+text("credits",Math.min(3,s.qualifyingTypes(faction).size()))+"\nThree reports earn Lord standing. Required faction alignment: 2000.";
        if(!s.completedTypes().isEmpty()){
            summary+="\n"+text("services");java.util.Set<Type> attributed=java.util.EnumSet.noneOf(Type.class);
            for(KOMEKnightCommission deed:s.history())if(deed.stage==Stage.REPORTED){
                lotr.common.fac.LOTRFaction served=KOMEProgressionFactionResolver.resolve(deed.faction);attributed.add(deed.type);
                summary+="\n"+text("historical_service",served==null?text("unrecorded_faction"):served.factionName(),title(deed.type));
            }
            for(Type type:s.completedTypes())if(!attributed.contains(type))summary+="\n"+text("historical_service",text("unrecorded_faction"),title(type));
        }
        if(KOMEProgressionFactionResolver.missingNativeLiege(faction))summary+="\n"+unavailableLiege();
        KOMELordshipTrial trial=p.getLordship().assignment();
        if(trial!=null)return summary+"\n"+KOMELordshipTrialPresentation.title(trial)+"\n"+KOMELordshipTrialPresentation.objective(trial);
        if(a==null)summary+="\n"+text(liege.isSet()?"seek_charge":"seek_liege");
        else summary+="\n"+title(a.type)+"\n"+objective(a)+"\n"+text(a.stage==Stage.READY_TO_REPORT?"field_complete":"field_pending");return summary;
    }
    public static String unavailableLiege(){return text("native_liege_unavailable");}
    public static String details(KOMEKnightCommission a){
        if(a.stage==Stage.READY_TO_REPORT)return "Report to "+a.liege.displayName+"."+(a.type==Type.STOLEN_GOODS?" Hold the recovered "+KOMEProgressionLostItems.name(a)+" for handover.":"");
        if(a.activationPending)return objective(a);
        String result="Stage: "+stageLabel(a)+"\n"+objective(a)
            +"\nDestination: "+location(a)+" ("+(int)a.destinationX+", "+(int)a.destinationZ+")";
        Actor charge=a.protectedActor();if(charge!=null&&!charge.dead)result+="\nCharge: "+(charge.displayName.isEmpty()?"the marked traveler":charge.displayName);
        if(a.type==Type.STOLEN_GOODS)result+="\nProperty: "+KOMEProgressionLostItems.name(a)+". Identify the thief carrying it, then return it in your hand.";
        if(a.type==Type.DANGEROUS_ESCORT)result+="\nKeep your traveler close. Speak to follow or recalculate; sneak and speak to wait. Arrival must include your traveler.";
        if(a.type==Type.WAR_PLANS)result+="\nReturn the authentic dispatch in your hand. If it is lost, crouch and request orders from your Liege to recover through a fresh attempt.";
        return result+"\nReport personally to "+a.liege.displayName+".";
    }
}
