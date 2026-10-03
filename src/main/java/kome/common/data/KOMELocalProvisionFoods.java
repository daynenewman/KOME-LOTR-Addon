package kome.common.data;

import lotr.common.LOTRMod;
import net.minecraft.init.Items;
import net.minecraft.item.Item;

/** Local harvests/hunting, not trader imports. Provenance is recorded in the v36.15 notes. */
public final class KOMELocalProvisionFoods {
    private KOMELocalProvisionFoods() { }
    public static Item[] forFaction(String faction) {
        switch(KOMEAlliance.normalizeFactionKey(faction)) {
            case "rohan": case "gondor": case "dunland":
                return new Item[]{Items.cooked_beef,LOTRMod.muttonCooked,Items.bread};
            case "durinsfolk": case "bluemountains":
                return new Item[]{Items.cooked_porkchop,LOTRMod.muttonCooked,LOTRMod.deerCooked};
            case "dorwinion":
                return new Item[]{LOTRMod.grapeRed,LOTRMod.grapeWhite,LOTRMod.olive,LOTRMod.raisins};
            case "highelves":
                return new Item[]{Items.apple,LOTRMod.pear,LOTRMod.deerCooked};
            case "woodelf":
                return new Item[]{LOTRMod.deerCooked,Items.cooked_porkchop,Items.mushroom_stew};
            case "lothlorien":
                return new Item[]{LOTRMod.mallornNut,LOTRMod.deerCooked};
            case "mordor": case "dolguldur":
                return new Item[]{Items.mushroom_stew};
            case "angmar":
                return new Item[]{LOTRMod.deerCooked,Items.mushroom_stew};
            case "isengard":
                return new Item[]{LOTRMod.rabbitCooked,Items.mushroom_stew};
            case "gundabad":
                return new Item[]{Items.mushroom_stew};
            case "harad":
                return new Item[]{LOTRMod.date,LOTRMod.orange,LOTRMod.lime,LOTRMod.lemon};
            case "morwaith":
                return new Item[]{LOTRMod.zebraCooked,LOTRMod.lionCooked};
            case "taurethrim":
                return new Item[]{LOTRMod.mango,Items.melon,LOTRMod.cornCooked,LOTRMod.banana};
            case "halftroll":
                return new Item[]{LOTRMod.lionRaw,LOTRMod.zebraRaw,LOTRMod.rhinoRaw};
            case "rhudel":
                return new Item[]{LOTRMod.olive,Items.apple,LOTRMod.pear,Items.cooked_beef};
            case "dunedain":
                return new Item[]{LOTRMod.deerCooked,Items.apple,LOTRMod.pear};
            case "dale":
                return new Item[]{Items.apple,LOTRMod.pear,LOTRMod.muttonCooked};
            case "fangorn":
                return new Item[]{Items.apple,Items.mushroom_stew};
            case "hobbit": case "bree":
                return new Item[]{Items.bread,Items.cooked_beef,LOTRMod.pear,Items.apple};
            default:
                // Normal LOTR land biomes inherit deer; caves also generate both mushrooms.
                return new Item[]{LOTRMod.deerCooked,Items.mushroom_stew};
        }
    }
}
