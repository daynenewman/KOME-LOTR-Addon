package kome.common.data;

/** Public identity presentation only; political office does not replace canonical standing. */
public final class KOMEProgressionIdentity {
    private KOMEProgressionIdentity() {}
    public static String suffix(String faction,KOMEProgressionRank rank) {
        lotr.common.fac.LOTRFaction nativeFaction=KOMEProgressionFactionResolver.resolve(faction);
        if(nativeFaction==null)return ", Wanderer of the Lands";
        return ", "+standing(faction,rank);
    }
    public static String standing(String faction,KOMEProgressionRank rank){
        String title=KOMEFactionProgressionTitles.title(faction,rank),name=factionName(faction),ending=" of "+name;
        return title.toLowerCase(java.util.Locale.ROOT).endsWith(ending.toLowerCase(java.util.Locale.ROOT))?title:title+ending;
    }
    public static String factionName(String faction){lotr.common.fac.LOTRFaction f=KOMEProgressionFactionResolver.resolve(faction);if(f==null)return "the Lands";String name=f.factionName();if(name.startsWith("lotr.")){String english=KOMEProgressionGoodsRules.nativeText(name);if(english!=null)return english;}return name;}
}
