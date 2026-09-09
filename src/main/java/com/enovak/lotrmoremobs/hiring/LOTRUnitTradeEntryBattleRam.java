package com.enovak.lotrmoremobs.hiring;

import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHirePreviewDriver;
import com.enovak.lotrmoremobs.siege.ram.BattleRamCrewTypes;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.fac.LOTRFaction;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Native LOTR unit-trade bridge for a complete Battle Ram formation.
 *
 * The invisible preview driver exists only because LOTR's legacy hiring GUI
 * expects every trade to have a primary LOTREntityNPC. The actual purchased
 * unit is the Battle Ram, which owns and spawns its ten faction carriers.
 */
public final class LOTRUnitTradeEntryBattleRam extends LOTRUnitTradeEntry {

    public static final int HIRE_COST = 75;
    public static final float ALIGNMENT_REQUIRED = 100.0F;

    private static final int PLACEMENT_RADIUS = 10;
    private static final int PLACEMENT_VERTICAL_RANGE = 2;

    private final LOTRFaction faction;

    public LOTRUnitTradeEntryBattleRam(LOTRFaction faction) {
        super(
                LOTREntityMumakilHirePreviewDriver.class,
                EntityBattleRam.class,
                "BattleRam",
                HIRE_COST,
                ALIGNMENT_REQUIRED
        );
        this.faction = faction;
        this.setPledgeExclusive();
        this.setExtraInfo("BattleRam");
    }

    public LOTRFaction getRamFaction() {
        return faction;
    }

    /** Keep the displayed and charged price exactly 75 coins. */
    @Override
    public int getCost(EntityPlayer player, LOTRHireableBase trader) {
        return HIRE_COST;
    }

    @Override
    public String getUnitTradeName() {
        return "Battle Ram";
    }

    @Override
    public String getFormattedExtraInfo() {
        return "Designed to breach Siege Gates. Includes a full crew of ten ram carriers.";
    }

    @Override
    public LOTREntityNPC getOrCreateHiredNPC(World world) {
        LOTREntityMumakilHirePreviewDriver preview =
                new LOTREntityMumakilHirePreviewDriver(world);
        preview.initCreatureForHire(null);
        preview.refreshCurrentAttackMode();
        preview.setCurrentItemOrArmor(0, null);
        return preview;
    }

    @Override
    public EntityLiving createHiredMount(World world) {
        EntityBattleRam ram = new EntityBattleRam(world);
        if (world != null && world.isRemote) {
            ram.configureHirePreview(faction);
        } else {
            ram.setRamFaction(faction);
        }
        return ram;
    }

    @Override
    public void hireUnit(
            EntityPlayer player,
            LOTRHireableBase hireable,
            String squadron
    ) {
        if (player == null
                || player.worldObj == null
                || player.worldObj.isRemote
                || hireable == null
                || !(hireable instanceof LOTREntityNPC)
                || !BattleRamCrewTypes.isSupported(faction)
                || !this.hasRequiredCostAndAlignment(player, hireable)) {
            return;
        }

        LOTREntityNPC hiringNpc = (LOTREntityNPC)hireable;
        World world = player.worldObj;
        EntityBattleRam ram = new EntityBattleRam(world);
        float yaw = getPlacementYaw(player.rotationYaw);

        Placement placement = findPlacement(
                world,
                hiringNpc,
                ram,
                yaw
        );
        if (placement == null) {
            player.addChatMessage(new ChatComponentText(
                    "There is not enough clear ground near this hiring NPC "
                            + "for a Battle Ram."
            ));
            ram.setDead();
            return;
        }

        ram.setLocationAndAngles(
                placement.x,
                placement.y,
                placement.z,
                yaw,
                0.0F
        );

        if (!ram.initializeForCommander(player, faction)) {
            player.addChatMessage(new ChatComponentText(
                    "Battle Ram ownership storage is unavailable."
            ));
            ram.setDead();
            return;
        }

        hireable.onUnitTrade(player);
        LOTRItemCoin.takeCoins(HIRE_COST, player);
        hiringNpc.playTradeSound();

        if (!world.spawnEntityInWorld(ram)) {
            LOTRItemCoin.giveCoins(HIRE_COST, player);
            ram.cancelUnspawnedHireInitialization();
            player.addChatMessage(new ChatComponentText(
                    "The Battle Ram could not be deployed. Your coins were returned."
            ));
        }
    }

    private Placement findPlacement(
            World world,
            LOTREntityNPC hiringNpc,
            EntityBattleRam ram,
            float yaw
    ) {
        int originX = MathHelper.floor_double(hiringNpc.posX);
        int originY = MathHelper.floor_double(hiringNpc.boundingBox.minY);
        int originZ = MathHelper.floor_double(hiringNpc.posZ);

        for (int radius = 2; radius <= PLACEMENT_RADIUS; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (Math.abs(dx) != radius
                            && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = PLACEMENT_VERTICAL_RANGE;
                            dy >= -PLACEMENT_VERTICAL_RANGE;
                            --dy) {
                        int blockX = originX + dx;
                        int blockY = originY + dy;
                        int blockZ = originZ + dz;
                        if (isPlacementClear(
                                world,
                                ram,
                                blockX,
                                blockY,
                                blockZ,
                                yaw
                        )) {
                            return new Placement(
                                    blockX + 0.5D,
                                    blockY,
                                    blockZ + 0.5D
                            );
                        }
                    }
                }
            }
        }
        return null;
    }

    private boolean isPlacementClear(
            World world,
            EntityBattleRam ram,
            int x,
            int y,
            int z,
            float yaw
    ) {
        if (y <= 0
                || y >= world.getHeight() - 2
                || !world.blockExists(x, y, z)
                || !world.blockExists(x, y - 1, z)
                || !World.doesBlockHaveSolidTopSurface(
                        world,
                        x,
                        y - 1,
                        z
                )) {
            return false;
        }

        ram.setLocationAndAngles(
                x + 0.5D,
                y,
                z + 0.5D,
                yaw,
                0.0F
        );

        return world.checkNoEntityCollision(ram.boundingBox)
                && world.getCollidingBoundingBoxes(
                        ram,
                        ram.boundingBox
                ).isEmpty();
    }

    private static float getPlacementYaw(float playerYaw) {
        int quadrant = MathHelper.floor_double(
                playerYaw * 4.0F / 360.0F + 0.5D
        ) & 3;
        return quadrant * 90.0F;
    }

    private static final class Placement {
        private final double x;
        private final double y;
        private final double z;

        private Placement(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
