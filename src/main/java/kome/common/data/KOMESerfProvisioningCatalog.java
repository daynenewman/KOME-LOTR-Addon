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

/** Small data-driven common fallback catalog; faction overrides can be added without delivery changes. */
public final class KOMESerfProvisioningCatalog {
    private static final Set<String> EVIL=new HashSet<String>(Arrays.asList("mordor","angmar","isengard","dol_guldur","dolguldur","gundabad","near_harad","nearharad","far_harad","farharad","rhun","utumno","half_troll","halftroll"));
    private KOMESerfProvisioningCatalog(){}
    public static List<KOMESerfProvisioningAssignment.Candidate> foodsForFaction(String key){List<KOMESerfProvisioningAssignment.Candidate> result=new ArrayList<KOMESerfProvisioningAssignment.Candidate>();for(Item item:Arrays.asList(Items.bread,Items.apple,Items.cooked_beef,Items.cooked_porkchop,Items.cooked_chicken))result.add(KOMESerfProvisioningAssignment.Candidate.runtime(item,0));return result;}
    public static List<KOMESerfProvisioningAssignment.Candidate> drinksForFaction(String key){List<KOMESerfProvisioningAssignment.Candidate> result=new ArrayList<KOMESerfProvisioningAssignment.Candidate>();for(Item item:new Item[]{LOTRMod.mugAle,LOTRMod.mugCider,LOTRMod.mugMead,LOTRMod.mugAppleJuice})if(item!=null)result.add(KOMESerfProvisioningAssignment.Candidate.runtime(item,0));if(result.isEmpty())throw new IllegalStateException("No canonical Provisioning drinks are registered.");return result;}
    public static List<String> vesselsForFaction(String key){List<String> result=new ArrayList<String>();for(LOTRItemMug.Vessel vessel:new LOTRItemMug.Vessel[]{LOTRItemMug.Vessel.SKIN,LOTRItemMug.Vessel.BOTTLE,LOTRItemMug.Vessel.GOBLET_WOOD,LOTRItemMug.Vessel.MUG,LOTRItemMug.Vessel.HORN,LOTRItemMug.Vessel.GOBLET_COPPER})result.add(vessel.name());result.add((EVIL.contains(normalize(key))?LOTRItemMug.Vessel.SKULL:LOTRItemMug.Vessel.GLASS).name());return result;}
    private static String normalize(String value){return value==null?"":value.trim().toLowerCase();}
}
