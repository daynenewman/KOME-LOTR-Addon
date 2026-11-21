package kome.common.data;

import java.util.Locale;
import net.minecraft.util.StatCollector;
import static kome.common.data.KOMEKnightCommission.Stage;

/** Localized commission language; the rank screen uses the same live values as promotion. */
public final class KOMELordshipTrialPresentation {
    private KOMELordshipTrialPresentation() {}
    private static String text(String key,Object... args) { return KOMEProgressionLanguage.text("kome.lordship."+key,args); }
    public static String title(KOMELordshipTrial t) { return t.nativeInvasion?"Repel an Invasion":text("title."+t.scenario.name().toLowerCase(Locale.ROOT))+" (legacy commission)"; }
    public static String objective(KOMELordshipTrial t) {
        if(t.ready())return "Report to "+(t.authority.isSet()?"Lord "+t.authority.displayName:"a Lord of your faction")+" with news of your victory.";
        if(t.objective.stage==Stage.FAILED)return t.playerDied?"You fell in battle. Ask your Liege for another commission.":text("failed");
        if(t.objective.activationPending)return "The encounter is unavailable. Return to your Liege or revisit the marked site.";
        if(t.nativeInvasion)return (t.objective.stage==Stage.OFFERED?"Accept your commission.":!t.objective.encounterCreated?"Travel to the invasion site on your map.":"Defeat the invaders: "+t.invasionRemaining+" / "+t.invasionSize+" strength remaining.")
            +"\nDestination: "+t.objective.place+" ("+(int)t.objective.x+", "+(int)t.objective.z+")."
            +"\nFight and survive. Fallen allies do not end the mission; your death does.";
        return KOMEKnightCommissionPresentation.objective(t.objective)
            +"\nComplete the objective and survive. Fallen allies do not end the mission.";
    }
    public static String speech(KOMELordshipTrial t,String event) {
        if(t.nativeInvasion&&("offer".equals(event)||"assigned".equals(event))){
            lotr.common.fac.LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(t.objective.enemyFaction);
            String opening="rohan".equals(KOMEAlliance.normalizeFactionKey(t.objective.faction))?"Our riders report an enemy host.":
                "gondor".equals(KOMEAlliance.normalizeFactionKey(t.objective.faction))?"The watch reports an enemy assault.":"An enemy host threatens our lands.";
            return opening+" Face "+(enemy==null?"the invaders":enemy.factionName())+" on the marked ground in "+t.objective.place
                +" ("+(int)t.objective.x+", "+(int)t.objective.z+"). Four of our soldiers will follow your commands. Travel and prepare before the invasion begins."
                +" Survive, take part in defeating the host, and bring me your account. Soldier losses alone will not end your commission.";
        }
        if("promoted".equals(event)){
            String outcome=t.acknowledgedLosses==0?"You won the day, and every soldier returned.":
                t.acknowledgedLosses>=t.guardClasses.size()?"Victory came dearly. We shall remember the fallen.":"The field is ours. We shall honour the fallen.";
            return outcome+" You have proved your courage. I confer "+KOMEFactionProgressionTitles.title(t.objective.faction,KOMEProgressionRank.LORD)+" standing upon you.";
        }
        if("offer".equals(event)||"assigned".equals(event))return text("speech.offer",title(t),objective(t));
        if("progress".equals(event))return objective(t);
        return text("speech."+event);
    }
    public static String command(boolean halted) { return text(halted?"wait":"follow"); }
    public static String standing(KOMEPlayerProgression p,double alignment,String faction){return text("standing",Math.min(3,p.getKnightService().qualifyingTypes(faction).size()),Double.isFinite(alignment)?(int)Math.floor(alignment):0);}
    public static String status(KOMEPlayerProgression p,double alignment) {return status(p,alignment,p.getSerfKnightProgression().getLiege().factionKey);}
    public static String status(KOMEPlayerProgression p,double alignment,String faction) {
        KOMELordshipTrial t=p.getLordship().assignment();
        if(t!=null)return objective(t);
        int credits=p.getKnightService().qualifyingTypes(faction).size();
        if(!p.getSerfKnightProgression().hasLiege())return "Present your recommendation to an eligible regional Liege. Recover it with /progression recommendation.";
        if(credits>=3)return alignment>=2000?"Report to your Liege for Lord standing.":"Reach 2,000 alignment, then report to your Liege.";
        return "Report three distinct commissions and reach 2,000 alignment to earn Lord standing.";
    }
}
