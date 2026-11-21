package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;

/** Small curated templates from actual role/culture, never an invented named location. */
public final class KOMEProgressionDialogue {
    private KOMEProgressionDialogue() {}
    static String culture(LOTREntityNPC npc,String human,String elf,String dwarf,String orc){
        String f=KOMEAlliance.normalizeFactionKey(npc.getFaction().codeName());
        if(java.util.Arrays.asList("mordor","dolguldur","gundabad","angmar","isengard").contains(f))return orc;
        if(java.util.Arrays.asList("highelves","woodelf","lothlorien").contains(f))return elf;
        if(java.util.Arrays.asList("durinsfolk","bluemountains").contains(f))return dwarf;
        return human;
    }
    public static String promotion(LOTREntityNPC npc,String faction){
        String title=KOMEFactionProgressionTitles.title(faction,KOMEProgressionRank.KNIGHT);
        return culture(npc,"You kept your charge safe. I welcome you as ","You have shown patience and resolve. Serve as ","You have proved your worth. I name you ","You obeyed and prevailed. You have earned the standing of ")+title+". Honour the duty it carries.";
    }
    public static String materials(LOTREntityNPC npc,boolean complete){
        String role=KOMESerfProfessionClassifier.classify(npc).key;
        if("smith".equals(role))return complete?"Good work. My forge has what it needs.":"Bring the fuel and materials listed in your book.";
        if("farmer".equals(role)||"farmhand".equals(role))return complete?"Good. The fields will be better for it.":"The fields need supplies. Bring what I have listed.";
        return culture(npc,complete?"That will serve my trade. Well done.":"Bring the materials listed in your book.",complete?"You have served us well.":"Bring what is listed in your book.",complete?"All accounted for. Well done.":"Good work begins with sound materials. Bring what I listed.",complete?"Everything accounted for. Go on.":"Bring every supply I ordered.");
    }
    public static String warning(LOTREntityNPC npc,int variant){return culture(npc,new String[]{"Wait for me!","A moment! I cannot keep up!","Do not leave me behind!"}[variant%3],new String[]{"Wait. Our paths must remain together.","A moment, if you please.","Keep your charge close."}[variant%3],new String[]{"Slow your pace!","Wait for me!","We must keep together!"}[variant%3],new String[]{"Wait!","Slow down!","Do not leave me here!"}[variant%3]);}
    public static String thanks(LOTREntityNPC npc){return culture(npc,"You brought me through safely. My thanks.","My thanks for your care on the road.","You kept your word. My thanks.","We made it. You did well.");}
}
