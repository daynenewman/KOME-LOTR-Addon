package kome.common.data;

import java.util.Locale;
import net.minecraft.util.StatCollector;
import static kome.common.data.KOMEKnightCommission.Stage;

/** Localized trial language; the rank screen uses the same live values as promotion. */
public final class KOMELordshipTrialPresentation {
    private KOMELordshipTrialPresentation() {}
    private static String text(String key,Object... args) { return KOMEProgressionLanguage.text("kome.lordship."+key,args); }
    public static String title(KOMELordshipTrial t) { return text("title."+t.scenario.name().toLowerCase(Locale.ROOT)); }
    public static String objective(KOMELordshipTrial t) {
        if(t.ready())return text(t.forceReleased?"report_recalled":"report");
        if(t.objective.stage==Stage.FAILED)return text("failed");
        return KOMEKnightCommissionPresentation.objective(t.objective)
            +"\n"+text("survivors",t.requiredSurvivors,t.guardClasses.size())+"\n"+text("commands");
    }
    public static String speech(KOMELordshipTrial t,String event) {
        if("offer".equals(event)||"assigned".equals(event))return text("speech.offer",title(t),objective(t));
        if("progress".equals(event))return objective(t);
        if("promoted".equals(event))return text("speech.promoted",KOMEFactionProgressionTitles.title(t.objective.faction,KOMEProgressionRank.LORD));
        return text("speech."+event);
    }
    public static String command(boolean halted) { return text(halted?"wait":"follow"); }
    public static String standing(KOMEPlayerProgression p,double alignment,String faction){return text("standing",Math.min(3,p.getKnightService().qualifyingTypes(faction).size()),Double.isFinite(alignment)?(int)Math.floor(alignment):0);}
    public static String status(KOMEPlayerProgression p,double alignment) {return status(p,alignment,p.getSerfKnightProgression().getLiege().factionKey);}
    public static String status(KOMEPlayerProgression p,double alignment,String faction) {
        KOMELordshipTrial t=p.getLordship().assignment();
        if(t!=null)return objective(t);
        if(!p.getSerfKnightProgression().hasLiege())return text("seek_liege");
        return text(KOMELordshipTrialService.prerequisites(p,alignment,faction)?"ready":"preparing");
    }
}
