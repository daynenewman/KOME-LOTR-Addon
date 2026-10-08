package kome.common.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import static kome.common.data.KOMEKnightCommission.*;

/** All new charge language is localized here, outside server decisions. */
public final class KOMEKnightCommissionPresentation {
    private KOMEKnightCommissionPresentation() {}
    public static String title(Type type) { return text("title."+type.name().toLowerCase(java.util.Locale.ROOT)); }
    private static String text(String key,Object... args) { return KOMEProgressionLanguage.text("kome.commission."+key,args); }
    public static String objective(KOMEKnightCommission a) {
        if(a.stage==Stage.FAILED)return text("failed");
        if(a.stage==Stage.READY_TO_REPORT)return text("report");
        if(a.stage==Stage.OFFERED)return text("receive_orders");
        if(!a.encounterCreated)return text("travel");
        if(a.type!=Type.RELIEF&&!a.threatResolved)return text("repel");
        String result=text("objective."+a.type.name().toLowerCase(java.util.Locale.ROOT),location(a));
        if(a.type==Type.RELIEF){for(Goods good:a.goods){Item item=(Item)Item.itemRegistry.getObject(good.itemKey);String name=item==null?text("provisions"):new ItemStack(item,1,good.damage).getDisplayName();result+="\n"+name+": "+good.delivered+" / "+good.required;}}
        if(a.type==Type.STOLEN_GOODS&&a.threatResolved)result=text("recover");
        return result;
    }
    public static int required(KOMEKnightCommission a){
        if(a.stage==Stage.ACTIVE&&a.encounterCreated&&!a.threatResolved&&a.type!=Type.RELIEF){
            int total=0;for(Actor actor:a.actors)if(actor.role==Role.ENEMY)total++;return Math.max(1,total);
        }
        return 1;
    }
    public static int completed(KOMEKnightCommission a){
        if(a.stage==Stage.ACTIVE&&a.encounterCreated&&!a.threatResolved&&a.type!=Type.RELIEF){
            int dead=0;for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&actor.dead)dead++;return dead;
        }
        return a.stage==Stage.REPORTED?1:0;
    }
    public static String speech(KOMEKnightCommission a,String event) {
        if("assigned".equals(event)||"offer".equals(event)){
            lotr.common.fac.LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(a.enemyFaction);
            String foe=enemy==null?"the raiders":enemy.factionName();
            String opening="rohan".equals(KOMEAlliance.normalizeFactionKey(a.faction))?text("rohan_orders"):
                "gondor".equals(KOMEAlliance.normalizeFactionKey(a.faction))?text("gondor_orders"):"";
            return (opening.isEmpty()?"":opening+" ")+text("speech."+a.type.name().toLowerCase(java.util.Locale.ROOT),location(a),foe,KOMEProgressionLostItems.name(a));
        }
        if("progress".equals(event))return objective(a);
        return text("speech."+event,location(a));
    }
    static String location(KOMEKnightCommission a){return "EXISTING_VERIFIED".equals(a.destinationProof.getString("Status"))?"the shelter marked on your map":"the ground marked on your map";}
    public static String summary(KOMEPlayerProgression p) {return summary(p,p.getSerfKnightProgression().getLiege().factionKey);}
    public static String summary(KOMEPlayerProgression p,String faction) {
        KOMEKnightServiceRecord s=p.getKnightService();KOMEKnightCommission a=s.assignment();KOMEProgressionNpcRef liege=p.getSerfKnightProgression().getLiege();
        String summary="\n"+text("liege",liege.isSet()?liege.displayName:text("none"))+"\n"+text("credits",Math.min(3,s.qualifyingTypes(faction).size()))+"\n"+text("preparing");
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
}
