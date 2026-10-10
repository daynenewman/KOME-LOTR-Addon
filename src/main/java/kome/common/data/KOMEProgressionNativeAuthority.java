package kome.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.world.World;

/** v36.15 native structure hosts, not invented settlement spawns. See hierarchy audit. */
public final class KOMEProgressionNativeAuthority {
    public static final class Definition {
        public final String faction,npcClass,lordTitle,princeTitle,rulerTitle,places,structure;
        Definition(String f,String c,String l,String p,String k,String places,String source){
            faction=f;npcClass="lotr.common.entity.npc.LOTREntity"+c;lordTitle=l;princeTitle=p;rulerTitle=k;this.places=places;structure=source;
        }
        public LOTREntityNPC create(World world){
            try{LOTREntityNPC npc=(LOTREntityNPC)Class.forName(npcClass).getConstructor(World.class).newInstance(world);
                return KOMEProgressionFactionResolver.matches(faction,npc.getFaction())&&KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)?npc:null;
            }catch(ReflectiveOperationException e){return null;}
        }
        public String title(KOMEProgressionNpcRank rank){return rank==KOMEProgressionNpcRank.KING?rulerTitle:rank==KOMEProgressionNpcRank.PRINCE?princeTitle:lordTitle;}
    }
    private static final Map<String,Definition> DEFINITIONS;
    static {
        Map<String,Definition> m=new LinkedHashMap<String,Definition>();
        add(m,"hobbit","HobbitShirriff","Shirriff","Chief Shirriff","Thain","the taverns of the Shire","HobbitTavern");
        add(m,"bree","BreeCaptain","Captain","High Captain","Mayor","the watch offices of Bree-land","BreeOffice");
        add(m,"dunedain","RangerNorthCaptain","Captain","High Captain","Chieftain","the Ranger camps of the northern lands","RangerCamp");
        add(m,"bluemountains","BlueDwarfCommander","Commander","High Lord","Lord","the strongholds of the Blue Mountains","BlueMountainsStronghold");
        add(m,"highelves","HighElfLord","Lord","High Lord","High King","the High Elven towers of Lindon, or the halls of Rivendell","HighElvenTower,RivendellHall");
        add(m,"gundabad","GundabadOrcMercenaryCaptain","Captain","Warlord","Overlord","the Orc camps of Gundabad and ruined Dwarven towers","GundabadCamp,RuinedDwarvenTower");
        add(m,"angmar","AngmarOrcMercenaryCaptain","Captain","Warlord","Overlord","the towers of Angmar and the hillmen's chieftain houses","AngmarTower,AngmarHillmanChieftainHouse");
        add(m,"woodelf","WoodElfCaptain","Captain","High Captain","King","the watchtowers of the Woodland Realm","WoodElfTower");
        add(m,"dolguldur","DolGuldurOrcChieftain","Chieftain","High Chieftain","Overlord","the Orc towers of Dol Guldur","DolGuldurTower");
        add(m,"dale","DaleCaptain","Captain","Marshal","King","the fortresses of Dale","DaleFortress");
        add(m,"durinsfolk","DwarfCommander","Commander","High Lord","King","the Dwarven towers of Durin's Folk","DwarvenTower");
        add(m,"lothlorien","GaladhrimLord","Lord","High Lord","Lord","the lord-houses of Lothlorien","ElfLordHouse");
        add(m,"dunland","DunlendingWarlord","Warlord","High Warlord","Chieftain","the hill-forts of Dunland","DunlandHillFort");
        add(m,"isengard","UrukHaiMercenaryCaptain","Captain","High Captain","Overlord","the Uruk camps of Isengard","UrukCamp");
        add(m,"rohan","RohirrimMarshal","Marshal","High Marshal","King","the fortresses of Rohan","RohanFortress");
        add(m,"gondor","GondorianCaptain","Captain","Lord","Steward","the military settlements of Gondor and its fiefdoms","GondorStructure$GondorFiefdom");
        add(m,"mordor","MordorOrcMercenaryCaptain","Captain","High Captain","Overlord","the towers of Mordor and Black Uruk forts","MordorTower,BlackUrukFort");
        add(m,"dorwinion","DorwinionCaptain","Captain","High Captain","Master","the captains' tents of Dorwinion","DorwinionCaptainTent");
        add(m,"rhudel","EasterlingWarlord","Warlord","High Warlord","King","the Easterling fortresses of Rhun","EasterlingFortress");
        add(m,"harad","UmbarCaptain","Captain","High Captain","Lord","the fortresses of Umbar, Southron fortresses and war camps of Harad","UmbarFortress,SouthronFortress,GulfWarCamp");
        add(m,"morwaith","MoredainChieftain","Chieftain","High Chieftain","High Chieftain","the chieftains' huts among the Moredain","MoredainHutChieftain");
        add(m,"taurethrim","TauredainChieftain","Chieftain","High Chieftain","King","the chieftains' pyramids of the Tauredain","TauredainChieftainPyramid");
        add(m,"halftroll","HalfTrollWarlord","Warlord","High Warlord","High Chieftain","the warlords' houses of the Half-trolls","HalfTrollWarlordHouse");
        DEFINITIONS=Collections.unmodifiableMap(m);
    }
    private static void add(Map<String,Definition> m,String f,String c,String l,String p,String k,String place,String source){m.put(f,new Definition(f,c,l,p,k,place,source));}
    private KOMEProgressionNativeAuthority(){}
    public static Map<String,Definition> definitions(){return DEFINITIONS;}
    public static Definition definition(String faction){return DEFINITIONS.get(KOMEAlliance.normalizeFactionKey(faction));}
    public static String guidance(String faction){
        Definition d=definition(faction);
        return d==null?"No captain among the Ents can yet receive this service."
            :"You have done well. Seek out one of greater standing among your people and prove yourself worthy of their service. "
            +"Look for a "+d.lordTitle+" among "+d.places+".";
    }
    public static String name(String faction,KOMEProgressionNpcRank rank,String personal){
        Definition d=definition(faction);String title=d==null?rank.displayName:d.title(rank);
        lotr.common.fac.LOTRFaction nativeFaction=KOMEProgressionFactionResolver.resolve(faction);
        String territory=nativeFaction==null?"":nativeFaction.factionName();
        if(personal==null||personal.trim().isEmpty())return title;
        for(KOMEProgressionNpcRank old:KOMEProgressionNpcRank.values()){
            String prefix=(d==null?old.displayName:d.title(old))+" ";
            if(personal.startsWith(prefix))personal=personal.substring(prefix.length());
        }
        int suffix=personal.indexOf(", ");if(suffix>=0)personal=personal.substring(0,suffix);
        return personal+", "+title+(territory.isEmpty()?"":" of "+territory);
    }
    /** Native family-name synchronization avoids vanilla custom-tag overhead rendering. */
    public static void applyName(LOTREntityNPC npc,KOMEProgressionNpcRankRecord record){
        String name=name(record.factionKey,record.rank,record.displayName.isEmpty()?npc.getNPCName():record.displayName);
        if(npc.familyInfo!=null){
            if(!name.equals(npc.familyInfo.getName()))npc.familyInfo.setName(name);
            String custom=npc.getCustomNameTag();boolean owned=custom.equals(name)||custom.equals(record.displayName);
            Definition d=definition(record.factionKey);
            for(KOMEProgressionNpcRank rank:KOMEProgressionNpcRank.values()){
                String title=d==null?rank.displayName:d.title(rank);
                if(custom.startsWith(title+" ")||custom.contains(", "+title+" of "))owned=true;
            }
            if(owned)npc.getDataWatcher().updateObject(10,"");
        }
    }
}
