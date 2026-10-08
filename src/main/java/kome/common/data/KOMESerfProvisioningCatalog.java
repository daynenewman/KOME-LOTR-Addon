package kome.common.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRItemMug;
import net.minecraft.init.Items;
import net.minecraft.item.Item;



/** Native local harvests and drinks only; occupation supplies belong to Profession. */
public final class KOMESerfProvisioningCatalog {
    private static final Set<String> EVIL=new HashSet<String>(Arrays.asList("mordor","angmar","isengard","dol_guldur","dolguldur","gundabad","near_harad","nearharad","far_harad","farharad","rhun","utumno","half_troll","halftroll"));
    private KOMESerfProvisioningCatalog(){}
    public static List<KOMESerfProvisioningAssignment.Candidate> foodsForFaction(String faction){
        String key=KOMEAlliance.normalizeFactionKey(faction);
        List<KOMESerfProvisioningAssignment.Candidate> p=new ArrayList<KOMESerfProvisioningAssignment.Candidate>();
        foods(p,KOMELocalProvisionFoods.forFaction(key));
        return p;
    }
    public static List<KOMESerfProvisioningAssignment.Candidate> drinksForFaction(String faction){
        String key=KOMEAlliance.normalizeFactionKey(faction);Item[] items;
        switch(key){
            case "rohan":items=new Item[]{LOTRMod.mugMead};break;
            case "durinsfolk":case "bluemountains":items=new Item[]{LOTRMod.mugWater};break;
            case "dorwinion":items=new Item[]{LOTRMod.mugRedWine,LOTRMod.mugWhiteWine,LOTRMod.mugRedGrapeJuice,LOTRMod.mugWhiteGrapeJuice};break;
            case "lothlorien":items=new Item[]{LOTRMod.mugMiruvor};break;
            case "highelves":items=new Item[]{LOTRMod.mugAppleJuice};break;
            case "woodelf":items=new Item[]{LOTRMod.mugWater};break;
            case "mordor":items=new Item[]{LOTRMod.mugOrcDraught};break;
            case "angmar":case "gundabad":case "dolguldur":case "isengard":case "halftroll":items=new Item[]{LOTRMod.mugWater};break;
            case "harad":items=new Item[]{LOTRMod.mugOrangeJuice,LOTRMod.mugLemonLiqueur};break;
            case "taurethrim":items=new Item[]{LOTRMod.mugBananaBeer};break;
            case "morwaith":items=new Item[]{LOTRMod.mugWater};break;
            case "rhudel":items=new Item[]{LOTRMod.mugAppleJuice};break;
            case "dale":case "gondor":items=new Item[]{LOTRMod.mugAppleJuice};break;
            case "fangorn":items=new Item[]{LOTRMod.mugAppleJuice};break;
            default:items=new Item[]{LOTRMod.mugWater};
        }
        List<KOMESerfProvisioningAssignment.Candidate> result=new ArrayList<KOMESerfProvisioningAssignment.Candidate>();for(Item item:items)if(item!=null)result.add(KOMESerfProvisioningAssignment.Candidate.runtime(item,0));if(result.isEmpty())throw new IllegalStateException("No canonical Provisioning drinks are registered.");return result;
    }
    public static List<String> vesselsForFaction(String key){List<String> result=new ArrayList<String>();for(LOTRItemMug.Vessel vessel:new LOTRItemMug.Vessel[]{LOTRItemMug.Vessel.SKIN,LOTRItemMug.Vessel.BOTTLE,LOTRItemMug.Vessel.GOBLET_WOOD,LOTRItemMug.Vessel.MUG,LOTRItemMug.Vessel.HORN,LOTRItemMug.Vessel.GOBLET_COPPER})result.add(vessel.name());result.add((EVIL.contains(normalize(key))?LOTRItemMug.Vessel.SKULL:LOTRItemMug.Vessel.GLASS).name());return result;}
    private static String normalize(String value){return value==null?"":value.trim().toLowerCase();}
    private static void foods(List<KOMESerfProvisioningAssignment.Candidate> pool,Item... items){for(Item item:items)if(KOMEProgressionGoodsRules.consumable(item))pool.add(KOMESerfProvisioningAssignment.Candidate.runtime(item,0));}
}
