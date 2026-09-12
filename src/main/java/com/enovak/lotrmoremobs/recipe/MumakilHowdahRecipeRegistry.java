package com.enovak.lotrmoremobs.recipe;

import com.enovak.lotrmoremobs.Main;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRItemBanner;
import lotr.common.recipe.LOTRRecipes;
import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * MUMAKIL_NEAR_HARAD_HOWDAH_RECIPE_V1_1
 *
 * Registers the Mumakil howdah as a Near Harad faction-table recipe only.
 */
public final class MumakilHowdahRecipeRegistry {
    private static final String HOWDAH_LEATHER_ORE =
            "lotrmoremobsMumakilHowdahLeather";
    private static final String HOWDAH_LOG_BEAM_ORE =
            "lotrmoremobsMumakilHowdahLogBeam";

    private static boolean registered;

    private MumakilHowdahRecipeRegistry() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        if (Main.mumakilHowdah == null) {
            throw new IllegalStateException(
                    "The Mumakil howdah item must be registered before its recipe."
            );
        }

        registerLeatherVariants();
        int beamVariantCount = registerAllLogBeamVariants();

        if (beamVariantCount == 0) {
            throw new IllegalStateException(
                    "No LOTR woodBeam blocks were found for the Mumakil howdah recipe."
            );
        }

        IRecipe nearHaradRecipe = createHowdahRecipe();
        IRecipe gulfRecipe = createHowdahRecipe();

        /*
         * These are the two native faction recipe lists requested for the
         * howdah. Neither list is the vanilla CraftingManager.
         */
        boolean addedNearHarad = addIfMissing(
                LOTRRecipes.nearHaradRecipes,
                nearHaradRecipe
        );
        boolean addedGulf = addIfMissing(
                LOTRRecipes.gulfRecipes,
                gulfRecipe
        );
        registered = true;

        System.out.println(
                "[LOTRMoreMobs] Ensured Mumak howdah recipe in Near Harad"
                        + " and Gulf Harad tables (nearAdded="
                        + addedNearHarad + ", gulfAdded="
                        + addedGulf + ", beamBlocks="
                        + beamVariantCount + ")."
        );
    }

    private static IRecipe createHowdahRecipe() {
        return new ShapedOreRecipe(
                new ItemStack(Main.mumakilHowdah),
                "BLB",
                "RDR",
                "WWW",
                Character.valueOf('B'),
                new ItemStack(
                        LOTRMod.banner,
                        1,
                        LOTRItemBanner.BannerType.NEAR_HARAD.bannerID
                ),
                Character.valueOf('L'),
                HOWDAH_LEATHER_ORE,
                Character.valueOf('R'),
                LOTRMod.rope,
                Character.valueOf('D'),
                LOTRMod.reedBars,
                Character.valueOf('W'),
                HOWDAH_LOG_BEAM_ORE
        );
    }

    private static boolean addIfMissing(
            List<IRecipe> recipes,
            IRecipe recipe
    ) {
        for (int i = 0; i < recipes.size(); ++i) {
            IRecipe existing = recipes.get(i);
            if (existing == null) {
                continue;
            }

            ItemStack output = existing.getRecipeOutput();
            if (output != null
                    && output.getItem() == Main.mumakilHowdah) {
                return false;
            }
        }

        recipes.add(recipe);
        return true;
    }

    private static void registerLeatherVariants() {
        registerLeatherVariant(Items.leather);
        registerLeatherVariant(LOTRMod.fur);
        registerLeatherVariant(LOTRMod.lionFur);
        registerLeatherVariant(LOTRMod.gemsbokHide);
    }

    private static void registerLeatherVariant(Item item) {
        if (item != null) {
            OreDictionary.registerOre(
                    HOWDAH_LEATHER_ORE,
                    new ItemStack(
                            item,
                            1,
                            OreDictionary.WILDCARD_VALUE
                    )
            );
        }
    }

    /**
     * Accept every public LOTR block field whose name begins with
     * "woodBeam", including all wood species and orientation metadata.
     */
    private static int registerAllLogBeamVariants() {
        int registeredBeamBlocks = 0;
        Field[] fields = LOTRMod.class.getFields();

        for (int i = 0; i < fields.length; ++i) {
            Field field = fields[i];

            if (!Modifier.isStatic(field.getModifiers())
                    || !Block.class.isAssignableFrom(field.getType())
                    || !field.getName().startsWith("woodBeam")) {
                continue;
            }

            try {
                Block beamBlock = (Block)field.get(null);

                if (beamBlock == null) {
                    continue;
                }

                OreDictionary.registerOre(
                        HOWDAH_LOG_BEAM_ORE,
                        new ItemStack(
                                beamBlock,
                                1,
                                OreDictionary.WILDCARD_VALUE
                        )
                );
                ++registeredBeamBlocks;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Unable to access LOTR log-beam field "
                                + field.getName(),
                        e
                );
            }
        }

        return registeredBeamBlocks;
    }
}
