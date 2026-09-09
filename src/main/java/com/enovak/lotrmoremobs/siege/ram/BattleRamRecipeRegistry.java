package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import java.util.List;
import lotr.common.fac.LOTRFaction;
import lotr.common.recipe.LOTRRecipes;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.OreDictionary;

public final class BattleRamRecipeRegistry {

    private static boolean registered;

    private BattleRamRecipeRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        register(LOTRRecipes.morgulRecipes, LOTRFaction.MORDOR);
        register(LOTRRecipes.elvenRecipes, LOTRFaction.LOTHLORIEN);
        register(LOTRRecipes.dwarvenRecipes, LOTRFaction.DURINS_FOLK);
        register(LOTRRecipes.urukRecipes, LOTRFaction.ISENGARD);
        register(LOTRRecipes.woodElvenRecipes, LOTRFaction.WOOD_ELF);
        register(LOTRRecipes.gondorianRecipes, LOTRFaction.GONDOR);
        register(LOTRRecipes.rohirricRecipes, LOTRFaction.ROHAN);
        register(LOTRRecipes.dunlendingRecipes, LOTRFaction.DUNLAND);
        register(LOTRRecipes.angmarRecipes, LOTRFaction.ANGMAR);
        register(LOTRRecipes.nearHaradRecipes, LOTRFaction.NEAR_HARAD);
        register(LOTRRecipes.highElvenRecipes, LOTRFaction.HIGH_ELF);
        register(
                LOTRRecipes.blueMountainsRecipes,
                LOTRFaction.BLUE_MOUNTAINS
        );
        register(LOTRRecipes.rangerRecipes, LOTRFaction.RANGER_NORTH);
        register(LOTRRecipes.dolGuldurRecipes, LOTRFaction.DOL_GULDUR);
        register(LOTRRecipes.gundabadRecipes, LOTRFaction.GUNDABAD);
        register(LOTRRecipes.halfTrollRecipes, LOTRFaction.HALF_TROLL);
        register(LOTRRecipes.dolAmrothRecipes, LOTRFaction.GONDOR);
        register(LOTRRecipes.moredainRecipes, LOTRFaction.MORWAITH);
        register(LOTRRecipes.tauredainRecipes, LOTRFaction.TAURETHRIM);
        register(LOTRRecipes.daleRecipes, LOTRFaction.DALE);
        register(LOTRRecipes.dorwinionRecipes, LOTRFaction.DORWINION);
        register(LOTRRecipes.hobbitRecipes, LOTRFaction.HOBBIT);
        register(LOTRRecipes.rhunRecipes, LOTRFaction.RHUDEL);
        register(LOTRRecipes.rivendellRecipes, LOTRFaction.HIGH_ELF);
        register(LOTRRecipes.umbarRecipes, LOTRFaction.NEAR_HARAD);
        register(LOTRRecipes.gulfRecipes, LOTRFaction.NEAR_HARAD);
        register(LOTRRecipes.breeRecipes, LOTRFaction.BREE);
        registered = true;
    }

    private static void register(
            List<IRecipe> recipes,
            LOTRFaction faction
    ) {
        if (!BattleRamCrewTypes.isSupported(faction)
                || containsRamRecipe(recipes, faction)) {
            return;
        }
        recipes.add(new ShapedOreRecipe(
                ItemBattleRam.createForFaction(
                        SiegeRegistry.battleRamItem,
                        faction
                ),
                "ILI",
                "LWL",
                "ILI",
                Character.valueOf('I'),
                Items.iron_ingot,
                Character.valueOf('L'),
                "logWood",
                Character.valueOf('W'),
                new ItemStack(
                        Blocks.wool,
                        1,
                        OreDictionary.WILDCARD_VALUE
                )
        ));
    }

    private static boolean containsRamRecipe(
            List<IRecipe> recipes,
            LOTRFaction faction
    ) {
        for (IRecipe recipe : recipes) {
            if (recipe == null) {
                continue;
            }
            ItemStack output = recipe.getRecipeOutput();
            if (output != null
                    && output.getItem() == SiegeRegistry.battleRamItem
                    && faction == ItemBattleRam.getFaction(output)) {
                return true;
            }
        }
        return false;
    }
}
