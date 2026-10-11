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
        return culture(npc,"You have proved your courage and kept faith with your charge. I receive you into my service as ","Your deeds have shown patience and resolve. You shall serve as ","You have proved dependable when it counted. I name you ","You did what I commanded. You have earned the standing of ")+title+". Remember the duty that comes with it.";
    }
    public static String materials(LOTREntityNPC npc,boolean complete){
        String role=KOMESerfProfessionClassifier.classify(npc).key;
        if("smith".equals(role))return complete?"Good supplies make sound work. That is all my forge needs from you.":"My forge needs fuel and sound materials. Bring the supplies recorded in your book.";
        if("farmer".equals(role)||"farmhand".equals(role))return complete?"That will help with the work of the fields. You have earned your keep.":"There is work in the fields. Bring the supplies I have set down for you.";
        return culture(npc,complete?"That will serve my trade. You have done well.":"My work needs supplies. Bring the materials in your book.",complete?"Your care has served our work well.":"Bring what our work requires, as recorded in your book.",complete?"Sound supplies, and all accounted for. Well done.":"Workmanship begins with materials. Bring what I have recorded.",complete?"Everything accounted for. You may go.":"Bring the supplies I ordered. Leave nothing out.");
    }
    public static String warning(LOTREntityNPC npc,int variant){return culture(npc,new String[]{"Wait for me!","A moment! I cannot keep your pace!","Do not leave me behind!"}[variant%3],new String[]{"Wait. Our paths must remain together.","A moment, if you please.","Do not leave your charge behind."}[variant%3],new String[]{"Slow your pace!","Wait for me!","We must keep together!"}[variant%3],new String[]{"Wait!","Slow down!","Do not leave me here!"}[variant%3]);}
    public static String thanks(LOTREntityNPC npc){return culture(npc,"You have seen me safely through. You have my thanks.","I am grateful for your care upon this journey.","You kept your word and brought me safely through. My thanks.","We made it. You did your part.");}
}
