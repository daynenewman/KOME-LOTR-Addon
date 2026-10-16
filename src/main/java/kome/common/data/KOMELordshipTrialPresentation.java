package kome.common.data;

import java.util.Locale;
import net.minecraft.util.StatCollector;
import static kome.common.data.KOMEKnightCommission.Stage;

/** Localized commission language; the rank screen uses the same live values as promotion. */
public final class KOMELordshipTrialPresentation {
    private KOMELordshipTrialPresentation() {}
    private static String text(String key,Object... args) { return KOMEProgressionLanguage.text("kome.lordship."+key,args); }
    public static String title(KOMELordshipTrial t) { return t.nativeInvasion?"Repel an Invasion":text("title."+t.scenario.name().toLowerCase(Locale.ROOT))+" (saved commission)"; }
    public static String objective(KOMELordshipTrial t) {
        if(t.ready())return "Report your victory to "+(t.authority.isSet()?"Lord "+t.authority.displayName:"a Lord of your faction")+" to report this commission. The final qualifying report earns Lord standing.";
        if(t.objective.stage==Stage.FAILED)return t.playerDied?"You fell during the commission. Return to your Lord to request another attempt.":text("failed");
        if(t.objective.activationPending)return "The encounter could not be prepared safely. Return to your Lord for reassignment, or approach the marked ground to retry.";
        if(t.nativeInvasion)return (t.objective.stage==Stage.OFFERED?"Accept the Lord's commission.":!t.objective.encounterCreated?"Travel to the invasion ground marked on your map.":"Defeat the native invasion: "+t.invasionRemaining+" / "+t.invasionSize+" invasion strength remaining.")
            +"\nDestination: "+t.objective.place+" ("+(int)t.objective.x+", "+(int)t.objective.z+")."
            +"\nSurvive the commission and take part in defeating the invasion. Your soldiers may fall; your own death ends the attempt.";
        return KOMEKnightCommissionPresentation.objective(t.objective)
            +"\nComplete your saved field objective and survive. Soldier losses do not end the commission.";
    }
    public static String speech(KOMELordshipTrial t,String event) {
        if(t.nativeInvasion&&("offer".equals(event)||"assigned".equals(event))){
            lotr.common.fac.LOTRFaction enemy=KOMEProgressionFactionResolver.resolve(t.objective.enemyFaction);
            String opening="rohan".equals(KOMEAlliance.normalizeFactionKey(t.objective.faction))?"Our riders bring word of a hostile host.":
                "gondor".equals(KOMEAlliance.normalizeFactionKey(t.objective.faction))?"The watch brings word of an enemy assault.":"A hostile host threatens our lands.";
            return opening+" Face "+(enemy==null?"the assigned invaders":enemy.factionName())+" on the marked ground in "+t.objective.place
                +" ("+(int)t.objective.x+", "+(int)t.objective.z+"). Four of our soldiers will follow your commands. Travel and prepare before the invasion begins."
                +" Survive, take part in defeating the host, and bring me your account. Soldier losses alone will not end your commission.";
        }
        if("promoted".equals(event)){
            String outcome=t.acknowledgedLosses==0?"You brought victory without a recorded loss among the entrusted soldiers.":
                t.acknowledgedLosses>=t.guardClasses.size()?"This was a costly victory. We shall remember the soldiers who fell.":"You won the field, and we shall honour those who fell.";
            return outcome+" You have shown courage and responsibility. I confer "+KOMEFactionProgressionTitles.title(t.objective.faction,KOMEProgressionRank.LORD)+" standing upon you.";
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
        if(!p.getSerfKnightProgression().hasLiege())return "Your recommendation introduces you to a Lord when you are ready. Recover it with /progression recommendation.";
        if(credits>=3)return alignment>=2000?"Report to your current Lord for Lord standing.":"Your service is complete. Reach 2000 faction alignment, then report to your Lord.";
        return "Complete three varied commissions. Their final eligible report earns Lord standing. Required faction alignment: 2000.";
    }
}
