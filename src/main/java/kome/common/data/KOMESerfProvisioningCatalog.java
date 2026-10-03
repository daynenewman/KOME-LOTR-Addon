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
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/** Weighted v36.15 miniquest and local trade resources; saved Foods also holds supplies. */
public final class KOMESerfProvisioningCatalog {
    private static final Set<String> EVIL=new HashSet<String>(Arrays.asList("mordor","angmar","isengard","dol_guldur","dolguldur","gundabad","near_harad","nearharad","far_harad","farharad","rhun","utumno","half_troll","halftroll"));
    private KOMESerfProvisioningCatalog(){}
    public static List<KOMESerfProvisioningAssignment.Candidate> foodsForFaction(String faction){
        String key=KOMEAlliance.normalizeFactionKey(faction);
        List<KOMESerfProvisioningAssignment.Candidate> p=new ArrayList<KOMESerfProvisioningAssignment.Candidate>();
        // Metadata follows LOTRMiniQuestFactory and native wood subtype definitions.
        // Staples have weight 4, ordinary supplies 3, rare/forged goods 1.
        switch(key){
            case "rohan":
                bulk(p,Blocks.log,0);bulk(p,Blocks.planks,0);material(p,Items.iron_ingot);gear(p,LOTRMod.swordRohan);break;
            case "gondor":
                bulk(p,LOTRMod.brick,1);bulk(p,LOTRMod.rock,1);bulk(p,LOTRMod.wood2,0);material(p,Items.iron_ingot);gear(p,LOTRMod.swordGondor);gear(p,LOTRMod.helmetGondor);break;
            case "durinsfolk":case "bluemountains":
                material(p,Items.coal);bulk(p,Blocks.iron_ore,0);material(p,LOTRMod.silver);small(p,LOTRMod.sapphire);small(p,LOTRMod.amethyst);
                material(p,"bluemountains".equals(key)?LOTRMod.blueDwarfSteel:LOTRMod.dwarfSteel);gear(p,"bluemountains".equals(key)?LOTRMod.hammerBlueDwarven:LOTRMod.hammerDwarven);break;
            case "dorwinion":
                bulk(p,Blocks.planks,0);bulk(p,LOTRMod.barrel,0,2,4);material(p,Items.stick);material(p,Items.string);break;
            case "highelves":
                bulk(p,Blocks.log,2);bulk(p,LOTRMod.sapling2,1,4,12);material(p,LOTRMod.quenditeCrystal);material(p,LOTRMod.elfSteel);gear(p,LOTRMod.swordHighElven);break;
            case "woodelf":
                bulk(p,LOTRMod.wood,2);bulk(p,LOTRMod.sapling,2,4,12);material(p,Items.string);material(p,Items.arrow);gear(p,LOTRMod.swordWoodElven);break;
            case "lothlorien":
                bulk(p,LOTRMod.wood,1);bulk(p,LOTRMod.sapling,1,4,12);bulk(p,LOTRMod.elanor,0,4,12);material(p,LOTRMod.quenditeCrystal);gear(p,LOTRMod.swordElven);break;
            case "mordor":case "angmar":case "gundabad":case "dolguldur":case "isengard":
                material(p,Items.coal);material(p,"isengard".equals(key)?LOTRMod.urukSteel:LOTRMod.orcSteel);material(p,Items.bone);bulk(p,Blocks.cobblestone,0);
                gear(p,"isengard".equals(key)?LOTRMod.scimitarUruk:"angmar".equals(key)?LOTRMod.swordAngmar:"dolguldur".equals(key)?LOTRMod.swordDolGuldur:LOTRMod.scimitarOrc);
                if("mordor".equals(key))material(p,LOTRMod.nauriteGem);if("dolguldur".equals(key))small(p,LOTRMod.guldurilCrystal);break;
            case "harad":
                bulk(p,LOTRMod.rock,0);material(p,Items.leather);bulk(p,Blocks.planks,0);gear(p,LOTRMod.swordGulfHarad);break;
            case "morwaith":
                material(p,LOTRMod.gemsbokHide);material(p,LOTRMod.lionFur);bulk(p,Blocks.log2,0);small(p,LOTRMod.rhinoHorn);gear(p,LOTRMod.spearMoredain);break;
            case "taurethrim":
                bulk(p,Blocks.log,3);material(p,Items.leather);material(p,LOTRMod.tauredainDart);material(p,LOTRMod.obsidianShard);gear(p,LOTRMod.swordTauredain);break;
            case "halftroll":
                material(p,LOTRMod.gemsbokHide);material(p,Items.bone);bulk(p,Blocks.log,3);gear(p,LOTRMod.scimitarHalfTroll);break;
            case "rhudel":
                material(p,LOTRMod.gildedIron);bulk(p,LOTRMod.sapling8,1,4,10);material(p,Items.leather);gear(p,LOTRMod.swordRhun);break;
            case "dunedain":
                bulk(p,Blocks.stonebrick,0);bulk(p,LOTRMod.brick2,3);material(p,Items.leather);material(p,Items.arrow);gear(p,LOTRMod.rangerBow);break;
            case "dale":
                material(p,Items.arrow);material(p,Items.iron_ingot);material(p,LOTRMod.silver);bulk(p,Blocks.planks,0);gear(p,LOTRMod.swordDale);break;
            case "dunland":
                bulk(p,Blocks.log,1);bulk(p,Blocks.cobblestone,0);material(p,Items.coal);material(p,Items.leather);gear(p,Items.iron_sword);break;
            case "fangorn":
                bulk(p,Blocks.sapling,0,4,12);bulk(p,LOTRMod.fangornPlant,0,4,12);bulk(p,LOTRMod.fangornPlant,2,4,12);bulk(p,LOTRMod.fangornPlant,5,4,12);break;
            case "hobbit":
                bulk(p,Blocks.log,0);material(p,Items.wheat);gear(p,Items.iron_hoe);break;
            case "bree":
                bulk(p,Blocks.log,0);material(p,Items.leather);material(p,Items.iron_ingot);gear(p,Items.iron_sword);break;
            default:
                bulk(p,Blocks.log,0);bulk(p,Blocks.cobblestone,0);material(p,Items.iron_ingot);material(p,Items.leather);
        }
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
    private static void foods(List<KOMESerfProvisioningAssignment.Candidate> p,Item... items){for(Item item:items)add(p,item,0,4,0,0);}
    private static void material(List<KOMESerfProvisioningAssignment.Candidate> p,Item item){add(p,item,0,3,6,16);}
    private static void small(List<KOMESerfProvisioningAssignment.Candidate> p,Item item){add(p,item,0,1,1,3);}
    private static void gear(List<KOMESerfProvisioningAssignment.Candidate> p,Item item){add(p,item,0,1,1,2);}
    private static void bulk(List<KOMESerfProvisioningAssignment.Candidate> p,Block block,int meta){bulk(p,block,meta,16,32);}
    private static void bulk(List<KOMESerfProvisioningAssignment.Candidate> p,Block block,int meta,int min,int max){if(block!=null)add(p,Item.getItemFromBlock(block),meta,3,min,max);}
    private static void add(List<KOMESerfProvisioningAssignment.Candidate> p,Item item,int meta,int weight,int min,int max){if(item==null)return;KOMESerfProvisioningAssignment.Candidate c=KOMESerfProvisioningAssignment.Candidate.runtime(item,meta);c.weight=weight;c.minimum=min;c.maximum=max;p.add(c);}
}
