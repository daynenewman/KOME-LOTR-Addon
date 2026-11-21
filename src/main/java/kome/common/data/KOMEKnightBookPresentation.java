package kome.common.data;

import java.util.*;

/** Compact context for the native book. Active work comes from the rank snapshot only. */
public final class KOMEKnightBookPresentation {
    private KOMEKnightBookPresentation(){}
    public static String context(KOMEPlayerProgression p,String faction){
        KOMEProgressionNpcRef liege=p.getSerfKnightProgression().getLiege();
        KOMELordshipTrial t=p.getLordship().assignment();
        String text="Relationship\n";
        if(t!=null&&t.authority.isSet())text+="Serving Liege: "+t.authority.displayName+".\n";
        text+=liege.isSet()?"Liege: "+liege.displayName+".":"You have earned Knight standing. Present your recommendation at a recognized regional seat.";
        lotr.common.fac.LOTRFaction served=KOMEProgressionFactionResolver.resolve(faction);
        if(served!=null)text+="\nFaction: "+served.factionName()+".";
        Set<KOMEKnightCommission.Type> credits=p.getKnightService().qualifyingTypes(faction);
        if(!credits.isEmpty()){
            text+="\n\nService reported";
            for(KOMEKnightCommission.Type type:credits)text+="\n"+KOMEKnightCommissionPresentation.title(type);
        }
        return text;
    }
    public static boolean isKnight(String title,String faction){return "Knight".equals(title)||KOMEFactionProgressionTitles.title(faction,KOMEProgressionRank.KNIGHT).equals(title);}
}
