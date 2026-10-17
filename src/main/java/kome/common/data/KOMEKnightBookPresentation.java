package kome.common.data;

import java.util.*;

/** Compact context for the native book. Active work comes from the rank snapshot only. */
public final class KOMEKnightBookPresentation {
    private KOMEKnightBookPresentation(){}
    public static String context(KOMEPlayerProgression p,String faction){
        KOMEProgressionNpcRef liege=p.getSerfKnightProgression().getLiege();
        KOMELordshipTrial t=p.getLordship().assignment();
        String text="Relationship\n";
        if(t!=null&&t.authority.isSet())text+="Service authority: "+t.authority.displayName+".\n";
        text+=liege.isSet()?"Liege: "+liege.displayName+".":"Your Knight standing is earned. When ready, seek sponsored service with a Lord or Prince at a verified regional seat.";
        lotr.common.fac.LOTRFaction served=KOMEProgressionFactionResolver.resolve(faction);
        if(served!=null)text+="\nFaction: "+served.factionName()+".";
        Set<KOMEKnightCommission.Type> credits=p.getKnightService().qualifyingTypes(faction);
        if(!credits.isEmpty()){
            text+="\n\nCredited service";
            for(KOMEKnightCommission.Type type:credits)text+="\n"+KOMEKnightCommissionPresentation.title(type);
        }
        return text;
    }
    public static boolean isKnight(String title,String faction){return "Knight".equals(title)||KOMEFactionProgressionTitles.title(faction,KOMEProgressionRank.KNIGHT).equals(title);}
}
