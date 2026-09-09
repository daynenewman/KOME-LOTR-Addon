package com.enovak.lotrmoremobs.siege;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.block.BlockSiegeGateController;
import com.enovak.lotrmoremobs.siege.block.BlockSiegeGatePart;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.ram.ItemBattleRam;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import lotr.common.entity.LOTREntities;
import lotr.common.LOTRMod;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

public final class SiegeRegistry {

    public static final String GATE_CONTROLLER_NAME = "siege_gate_controller";
    public static final String GATE_PART_NAME = "siege_gate_part";
    public static final String GATE_TILE_ENTITY_ID = Main.MODID + ":siege_gate";
    public static final int BATTLE_RAM_ENTITY_ID = 813;

    public static Block gateController;
    public static Block gatePart;
    public static Item battleRamItem;

    private SiegeRegistry() {
    }

    public static void register() {
        gateController = new BlockSiegeGateController();
        GameRegistry.registerBlock(gateController, GATE_CONTROLLER_NAME);

        if (MumakilConfig.enableSiegeGates) {
            GameRegistry.addRecipe(
                    new ItemStack(
                            gateController,
                            1
                    ),
                    " G ",
                    "GIG",
                    " G ",
                    Character.valueOf('G'),
                    LOTRMod.gateGear,
                    Character.valueOf('I'),
                    Blocks.iron_block
            );
        }

        gatePart = new BlockSiegeGatePart();
        GameRegistry.registerBlock(gatePart, GATE_PART_NAME);

        /*
         * Creative-only faction spawn egg. No Battle Ram crafting recipe is
         * registered; normal gameplay acquisition remains faction hiring.
         */
        battleRamItem = new ItemBattleRam();
        GameRegistry.registerItem(battleRamItem, "battle_ram");

        GameRegistry.registerTileEntity(
                TileEntitySiegeGate.class,
                GATE_TILE_ENTITY_ID
        );

        LOTREntities.registerCreature(
                EntityBattleRam.class,
                "BattleRam",
                BATTLE_RAM_ENTITY_ID
        );
    }
}
