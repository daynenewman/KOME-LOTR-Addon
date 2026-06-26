package kome.common.data;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEReflection;
import kome.common.command.KOMECommandTroops;
import kome.common.network.KOMEPacketAllianceData;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketHireType;
import kome.common.network.KOMEPacketLordMenu;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRNPCMount;
import lotr.common.entity.npc.LOTRTradeable;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.inventory.LOTRContainerUnitTrade;
import lotr.common.inventory.LOTRContainerChestWithPouch;
import lotr.common.inventory.LOTRContainerHobbitOven;
import lotr.common.inventory.LOTRContainerPouch;
import lotr.common.inventory.LOTRContainerTrade;
import lotr.common.LOTRMod;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.item.LOTRItemCoin;
import lotr.common.item.LOTRItemMug;
import lotr.common.item.LOTRItemPouch;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.ContainerWorkbench;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class KOMEEvents {
    public static int defaultUnitCost = 25;
    private static final int CIVIL_TRADER_REQUIRED = 500;
    private static final int MILITARY_KILLS_REQUIRED = 2000;
    private static final int TRADE_T2_COINS_REQUIRED = 10000;
    private static final int TRADE_T2_FARMER_POP_REQUIRED = 50;
    private final Map<UUID, Integer> lastCoinValues = new HashMap<>();
    private final Map<UUID, int[]> lastCoinCounts = new HashMap<>();
    private final Map<UUID, Long> lastStoneCraftDenials = new HashMap<>();
    private long nextMovementArrivalCheckMillis;
    private long automaticWaypointLinkCheckMillis;
    private boolean automaticWaypointLinksEnsured;

    public void resetSessionState() {
        lastCoinValues.clear();
        lastCoinCounts.clear();
        lastStoneCraftDenials.clear();
        automaticWaypointLinkCheckMillis = 0L;
        automaticWaypointLinksEnsured = false;
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(event.player));
            KOMECommandTroops.removeStaleMovingEntities(data, KOMEReflection.getWorld(event.player));
            data.rememberPlayerName(KOMEReflection.getEntityUUID(event.player), event.player.getCommandSenderName());
            data.syncConquestTiles((EntityPlayerMP) event.player);
            KOMEPacketHandler.network.sendTo(new KOMEPacketHireType(data.getPopulation(KOMEReflection.getEntityUUID(event.player)).hireType), (EntityPlayerMP) event.player);
            KOMEProgressionAutoCompleter.runForPlayer((EntityPlayerMP) event.player, true);
            KOMEProgressionAutoCompleter.syncPlayer((EntityPlayerMP) event.player, data.getProgression(KOMEReflection.getEntityUUID(event.player)));
            KOMEProgressionTitles.updatePlayerTitle((EntityPlayerMP) event.player);
            cacheCoinValue((EntityPlayer) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !KOMEReflection.isRemote(KOMEReflection.getWorld(event.player))) {
            enforceOpenContainerRestrictions(event.player);
            if (event.player instanceof EntityPlayerMP && KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(event.player)) % 20L == 0L) {
                enforceMiniQuestPermission((EntityPlayerMP) event.player);
                enforceMountPermission((EntityPlayerMP) event.player);
            }
            if (event.player instanceof EntityPlayerMP && KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(event.player)) % 100L == 0L) {
                KOMEProgressionAutoCompleter.runForPlayer((EntityPlayerMP) event.player, true);
                KOMEProgressionTitles.updatePlayerTitle((EntityPlayerMP) event.player);
                updateAllianceMilitaryKillProgress((EntityPlayerMP) event.player);
                updateAllianceTradeT2Progress((EntityPlayerMP) event.player);
            }
            trackAllianceCivilTraderProgress(event.player);
            cacheCoinValue(event.player);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextMovementArrivalCheckMillis) {
            return;
        }
        nextMovementArrivalCheckMillis = now + 1000L;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worldServers == null) {
            return;
        }
        if (!automaticWaypointLinksEnsured) {
            if (automaticWaypointLinkCheckMillis <= 0L) {
                automaticWaypointLinkCheckMillis = now + 15000L;
            } else if (now >= automaticWaypointLinkCheckMillis) {
                ensureAutomaticWaypointLinks(server);
                automaticWaypointLinksEnsured = true;
            }
        }
        for (WorldServer world : server.worldServers) {
            if (world == null || KOMEReflection.isRemote(world)) {
                continue;
            }
            KOMEWorldData data = KOMEWorldData.get(world);
            if (!data.armyMovements.isEmpty()) {
                KOMECommandTroops.processMovementTick(data, world, now);
            }
        }
    }

    private void ensureAutomaticWaypointLinks(MinecraftServer server) {
        if (server == null || server.worldServers == null) {
            return;
        }
        for (WorldServer world : server.worldServers) {
            if (world == null || KOMEReflection.isRemote(world)) {
                continue;
            }
            KOMEWorldData data = KOMEWorldData.get(world);
            data.ensureAutomaticTileWaypointLinks();
            data.syncConquestTiles();
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!KOMEReflection.isRemote(event.world) && event.entity instanceof LOTREntityNPC) {
            KOMEWorldData data = KOMEWorldData.get(event.world);
            KOMEHiredUnitRecord movingRecord = data.hiredUnits.get(KOMEReflection.getEntityUUID(event.entity));
            if (movingRecord != null && movingRecord.isMoving()) {
                if (KOMECommandTroops.isArrivalSpawnInProgress(data, movingRecord)) {
                    return;
                }
                KOMECommandTroops.removeStaleMovingEntities(data, event.world);
                KOMEReflection.setDead(event.entity);
                event.setCanceled(true);
                return;
            }
            handleHiredUnit((LOTREntityNPC) event.entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(EntityInteractEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityPlayer))) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        if (isMountEntity(event.target) && !KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.MOUNTS)) {
            event.setCanceled(true);
            return;
        }
        if (event.target instanceof EntityAnimal && isBreedingInteraction((EntityAnimal) event.target, player.getCurrentEquippedItem())
            && !KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.FARMING)) {
            event.setCanceled(true);
            return;
        }
        if (event.entityPlayer.isSneaking() && event.target instanceof LOTRHireableBase) {
            LOTRHireableBase lord = (LOTRHireableBase) event.target;
            if (isPledgeLord(lord)) {
                KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
                KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
                LOTRFaction faction = lord.getFaction();
                KOMEPacketHandler.network.sendTo(new KOMEPacketLordMenu(event.target.getEntityId(), lord.getNPCName(), faction == null ? "" : faction.factionName(), isPledgedLord(event.target, progression)), player);
                event.setCanceled(true);
                return;
            }
        }
        if (event.target instanceof LOTREntityNPC && !KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.MINIQUESTS)) {
            LOTREntityNPC npc = (LOTREntityNPC) event.target;
            if (npc.questInfo != null && npc.questInfo.canOfferQuestsTo(player) && npc.questInfo.getOfferFor(player) != null) {
                KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.MINIQUESTS);
                event.setCanceled(true);
                return;
            }
        }
        if (!(event.target instanceof LOTRTradeable)) {
            return;
        }
        if (!KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.NPC_TRADE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.entityPlayer == null || KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityPlayer))) {
            return;
        }
        ItemStack held = event.entityPlayer.getCurrentEquippedItem();
        if (held == null) {
            return;
        }
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && isHoe(held)
            && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.FARMING)) {
            event.setCanceled(true);
            return;
        }
        if ((event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
            && isPouch(held)
            && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.POUCHES)) {
            event.setCanceled(true);
            return;
        }
        if ((event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
            && isFishingRod(held)
            && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.FARMING)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerOpenContainer(PlayerOpenContainerEvent event) {
        if (event.entityPlayer == null || KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityPlayer))) {
            return;
        }
        if (event.entityPlayer.openContainer instanceof LOTRContainerTrade
            && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.NPC_TRADE)) {
            event.setResult(Event.Result.DENY);
            event.entityPlayer.closeScreen();
            return;
        }
        if ((event.entityPlayer.openContainer instanceof LOTRContainerPouch || event.entityPlayer.openContainer instanceof LOTRContainerChestWithPouch)
            && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.POUCHES)) {
            event.setResult(Event.Result.DENY);
            event.entityPlayer.closeScreen();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (event.player == null || KOMEReflection.isRemote(event.world)) {
            return;
        }
        if (isFireOrLightBlock(event.placedBlock)
            && !KOMEProgressionPermissions.require(event.player, KOMEProgressionPermissions.FIRE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerUseItemStart(PlayerUseItemEvent.Start event) {
        if (event.entityPlayer == null || KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityPlayer)) || event.item == null) {
            return;
        }
        if (isFireOrLightItem(event.item) && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.FIRE)) {
            event.duration = 0;
            event.setCanceled(true);
            return;
        }
        if (isMeat(event.item) && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.MEAT)) {
            event.duration = 0;
            event.setCanceled(true);
            return;
        }
        if (isAlcoholicDrink(event.item) && !KOMEProgressionPermissions.require(event.entityPlayer, KOMEProgressionPermissions.ALCOHOL_PIPEWEED)) {
            event.duration = 0;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        if (event.player == null || KOMEReflection.isRemote(KOMEReflection.getWorld(event.player)) || event.smelting == null) {
            return;
        }
        if (!(event.smelting.getItem() instanceof ItemFood)) {
            return;
        }
        if (KOMEProgressionPermissions.has(event.player, KOMEProgressionPermissions.COOKING)) {
            return;
        }
        removeMatchingItems(event.player, event.smelting, event.smelting.stackSize);
        KOMEProgressionPermissions.deny(event.player, "You have not unlocked Cooking yet. Cooked food removed.");
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.player == null || KOMEReflection.isRemote(KOMEReflection.getWorld(event.player)) || event.crafting == null) {
            return;
        }
        if (isStoneTool(event.crafting) && !KOMEProgressionPermissions.has(event.player, KOMEProgressionPermissions.STONEWORK)) {
            removeMatchingItems(event.player, event.crafting, event.crafting.stackSize);
            KOMEProgressionPermissions.deny(event.player, "You have not unlocked Stonework yet. Stone tool removed.");
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityLiving)) && event.entityLiving instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC) event.entityLiving;
            if (!npc.isEntityAlive()) {
                releaseIfTracked(npc);
                releaseLinkedInactiveUnits(npc);
                return;
            }
            if (npc.hiredNPCInfo.isActive) {
                handleHiredUnit(npc);
                KOMEUnitLevelCapHooks.enforceCap(npc);
                updateTrackedPopulationCost(npc);
            } else {
                releaseIfTracked(npc);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!KOMEReflection.isRemote(KOMEReflection.getWorld(event.entityLiving)) && event.entityLiving instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC) event.entityLiving;
            trackAllianceMilitaryKill(npc, event.source);
            releaseIfTracked(npc);
            releaseLinkedInactiveUnits(npc);
        }
    }

    private void handleHiredUnit(LOTREntityNPC npc) {
        LOTRHiredNPCInfo info = npc.hiredNPCInfo;
        if (!info.isActive || info.getHiringPlayerUUID() == null) {
            return;
        }
        boolean isFarmhand = info.getTask() == LOTRHiredNPCInfo.Task.FARMER;
        if (!isFarmhand && info.getTask() != LOTRHiredNPCInfo.Task.WARRIOR) {
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        UUID entityID = KOMEReflection.getEntityUUID(npc);
        if (data.hiredUnits.containsKey(entityID)) {
            KOMEHiredUnitRecord existing = data.hiredUnits.get(entityID);
            if (existing != null && !existing.isMoving()) {
                existing.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
                data.markDirty();
            }
            return;
        }
        EntityPlayer owner = info.getHiringPlayer();
        if (owner == null) {
            return;
        }
        if (!KOMEProgressionPermissions.has(owner, KOMEProgressionPermissions.HIRE_UNITS)) {
            int refund = refundDeniedHire(owner, npc);
            KOMEProgressionPermissions.deny(owner, "You have not unlocked Hire Units yet" + (refund > 0 ? ". Refunded " + refund + " coins." : "."));
            KOMEReflection.setDead(npc);
            return;
        }
        KOMEPlayerPopulation pop = data.getPopulation(info.getHiringPlayerUUID());
        KOMEPopulationType hireType = pop.hireType == null ? KOMEPopulationType.OFFENSIVE : pop.hireType;
        if (isFarmhand) {
            int limit = data.getFarmhandLimit(info.getHiringPlayerUUID());
            int used = data.getFarmhandsUsed(info.getHiringPlayerUUID());
            if (used >= limit) {
                int refund = refundDeniedHire(owner, npc);
                KOMEProgressionPermissions.deny(owner, "You do not have enough farmhand slots. Farmhands used: " + used + "/" + limit + (refund > 0 ? ". Refunded " + refund + " coins." : ""));
                KOMEReflection.setDead(npc);
                return;
            }
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = entityID;
            record.owner = info.getHiringPlayerUUID();
            record.type = KOMEPopulationType.OFFENSIVE;
            record.cost = 1;
            record.level = Math.max(1, info.xpLevel);
            record.farmhand = true;
            record.unitName = getFarmhandName(npc);
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
            data.hiredUnits.put(entityID, record);
            data.markDirty();
            return;
        }

        LOTRUnitTradeEntry trade = getMatchingTrade(owner, npc);
        boolean mounted = isMountedUnit(npc, trade);
        int rawPopulationCost = getRawPopulationCost(npc, mounted);
        int populationCost = applyHireTypeCost(rawPopulationCost, hireType);
        String ownerFaction = getPlayerFactionKey(owner, data);
        UUID hiringPlayer = info.getHiringPlayerUUID();
        String activeRecruitmentTile = data.getActiveRecruitmentTile(hiringPlayer, ownerFaction);
        KOMETilePopulation originPopulation = activeRecruitmentTile.length() == 0 ? null
            : data.findPlayerAllocatedPopulationInTile(activeRecruitmentTile, ownerFaction, hiringPlayer, hireType, populationCost);
        if (originPopulation == null) {
            originPopulation = data.findPlayerAllocatedTileWithPopulation(ownerFaction, hiringPlayer, hireType, populationCost);
        }
        String originTile = originPopulation != null ? originPopulation.tileId
            : activeRecruitmentTile.length() > 0 ? activeRecruitmentTile : data.findFactionControlledTile(ownerFaction);
        boolean tileFunded = originPopulation != null && originPopulation.tryUseEffective(hireType, populationCost, ownerFaction);
        KOMEPlayerTilePopulationAllocation allocation = tileFunded ? data.getAllocation(originPopulation.tileId, ownerFaction, hiringPlayer) : null;
        if (tileFunded && (allocation == null || !allocation.tryUse(hireType, populationCost))) {
            originPopulation.release(hireType, populationCost);
            tileFunded = false;
        }
        boolean reserveFunded = false;
        if (!tileFunded && originTile.length() > 0) {
            reserveFunded = pop.tryUse(hireType, populationCost);
        }
        if (!tileFunded && !reserveFunded) {
            int refund = refundDeniedHire(owner, npc);
            int factionTotal = data.getFactionTilePopulationTotal(ownerFaction, hireType);
            int factionUsed = data.getFactionTilePopulationUsed(ownerFaction, hireType);
            String reason = originTile.length() == 0
                ? "No controlled conquest tile is available as a physical origin."
                : "Not enough tile or player reserve " + hireType.key + " population. Required: " + populationCost
                    + ", allocated tile available: " + getFactionPlayerAllocationAvailable(data, ownerFaction, hiringPlayer, hireType)
                    + ", faction tile available: " + Math.max(0, factionTotal - factionUsed) + "/" + factionTotal
                    + ", reserve available: " + pop.getAvailable(hireType) + "/" + pop.getTotal(hireType) + ".";
            KOMEProgressionPermissions.deny(owner, reason + (refund > 0 ? " Refunded " + refund + " coins." : ""));
            KOMEReflection.setDead(npc);
            return;
        }
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = entityID;
        record.owner = info.getHiringPlayerUUID();
        record.type = hireType;
        record.cost = populationCost;
        record.baseCost = rawPopulationCost;
        record.level = Math.max(1, info.xpLevel);
        record.mounted = mounted;
        record.unitName = getUnitName(npc);
        record.sourceType = tileFunded ? KOMEHiredUnitRecord.SOURCE_TILE_POOL : KOMEHiredUnitRecord.SOURCE_PLAYER_RESERVE;
        record.sourcePlayer = info.getHiringPlayerUUID();
        record.sourceTileId = tileFunded ? originPopulation.tileId : "";
        record.sourceFaction = tileFunded ? originPopulation.sourceFaction : KOMEAlliance.normalizeFactionKey(ownerFaction);
        if (tileFunded) {
            record.allocationTileId = originPopulation.tileId;
            record.allocationFaction = KOMEAlliance.normalizeFactionKey(ownerFaction);
            record.allocationPlayer = hiringPlayer;
        }
        record.currentTile = originTile;
        record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
        data.hiredUnits.put(entityID, record);
        data.markDirty();
        data.syncConquestTiles();
        String funding = tileFunded ? "allocated tile population" : "player reserve population";
        String fallback = activeRecruitmentTile.length() > 0 && !activeRecruitmentTile.equals(originTile)
            ? " Active recruitment tile " + activeRecruitmentTile + " could not fund this hire, so fallback tile " + originTile + " was used."
            : "";
        owner.addChatMessage(new ChatComponentText(record.unitName + " recruited at " + originTile + " using " + funding + "." + fallback));
    }

    private void enforceMiniQuestPermission(EntityPlayerMP player) {
        if (KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.MINIQUESTS)) {
            return;
        }
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        List active = new ArrayList(lotrData.getActiveMiniQuests());
        if (active.isEmpty()) {
            return;
        }
        for (Object object : active) {
            if (object instanceof LOTRMiniQuest) {
                lotrData.removeMiniQuest((LOTRMiniQuest) object, false);
            }
        }
        lotrData.setTrackingMiniQuestID(null);
        KOMEProgressionPermissions.deny(player, "You have not unlocked NPC Mini-Quests yet. Active mini-quest removed.");
    }

    private void enforceMountPermission(EntityPlayerMP player) {
        if (KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.MOUNTS)) {
            return;
        }
        Entity riding = KOMEReflection.getRidingEntity(player);
        if (!isMountEntity(riding)) {
            return;
        }
        player.mountEntity(null);
        KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.MOUNTS);
    }

    private void updateTrackedPopulationCost(LOTREntityNPC npc) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(npc));
        if (record == null || record.farmhand) {
            return;
        }
        record.unitName = getUnitName(npc);
        record.mounted = record.mounted || isMountedUnit(npc, null);
        if (!record.isMoving()) {
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
        }
        if (record.level <= 0) {
            record.level = Math.max(1, npc.hiredNPCInfo.xpLevel);
        }
        int rawPopulationCost = getRawPopulationCost(npc, record.mounted);
        int currentCost = applyHireTypeCost(rawPopulationCost, record.type);
        if (currentCost > record.cost && npc.hiredNPCInfo.xpLevel > record.level && !hasPopulationForCostIncrease(data, record, currentCost - record.cost)) {
            denyLevelUpForPopulation(npc, data, record, currentCost - record.cost);
            rawPopulationCost = getRawPopulationCost(npc, record.mounted);
            currentCost = applyHireTypeCost(rawPopulationCost, record.type);
        }
        int currentLevel = Math.max(1, npc.hiredNPCInfo.xpLevel);
        if (currentCost == record.cost && rawPopulationCost == record.baseCost && currentLevel == record.level) {
            return;
        }
        int previousCost = record.cost;
        record.level = currentLevel;
        record.baseCost = rawPopulationCost;
        record.cost = currentCost;
        if (record.isPlayerReserveFunded()) {
            data.getPopulation(record.sourcePlayer == null ? record.owner : record.sourcePlayer).adjustUsed(record.type, currentCost - previousCost);
        } else {
            KOMETilePopulation population = data.getFundingPool(record);
            if (population != null) {
                population.addUsed(record.type, currentCost - previousCost);
            }
            if (record.allocationPlayer != null) {
                KOMEPlayerTilePopulationAllocation allocation = data.getAllocation(record.allocationTileId, record.allocationFaction, record.allocationPlayer);
                if (allocation != null) {
                    allocation.addUsed(record.type, currentCost - previousCost);
                }
            }
        }
        data.markDirty();
        data.syncConquestTiles();
    }

    private boolean hasPopulationForCostIncrease(KOMEWorldData data, KOMEHiredUnitRecord record, int extraCost) {
        if (extraCost <= 0) {
            return true;
        }
        if (record.isPlayerReserveFunded()) {
            KOMEPlayerPopulation pop = data.getPopulation(record.sourcePlayer == null ? record.owner : record.sourcePlayer);
            return pop.getAvailable(record.type) >= extraCost;
        }
            KOMETilePopulation population = data.getFundingPool(record);
            if (population != null) {
                KOMEConquestTile sourceTile = data.conquestTiles.get(KOMEConquestTile.normalizeId(record.sourceTileId));
                String controller = sourceTile == null ? "" : sourceTile.currentRulingFaction();
                String ownerFaction = data.getPlayerFactionKey(record.owner);
                KOMEPlayerTilePopulationAllocation allocation = record.allocationPlayer == null ? null : data.getAllocation(record.allocationTileId, record.allocationFaction, record.allocationPlayer);
                return KOMEAlliance.normalizeFactionKey(ownerFaction).equals(KOMEAlliance.normalizeFactionKey(controller))
                && population.getEffectiveAvailable(record.type, controller) >= extraCost
                && (record.allocationPlayer == null || allocation != null && allocation.getAvailable(record.type) >= extraCost);
        }
        return false;
    }

    private void denyLevelUpForPopulation(LOTREntityNPC npc, KOMEWorldData data, KOMEHiredUnitRecord record, int extraCost) {
        int targetLevel = Math.max(1, record.level);
        int levelsLost = Math.max(0, npc.hiredNPCInfo.xpLevel - targetLevel);
        if (levelsLost > 0) {
            IAttributeInstance attrHealth = npc.getEntityAttribute(SharedMonsterAttributes.maxHealth);
            attrHealth.setBaseValue(Math.max(attrHealth.getBaseValue() - levelsLost, 1.0));
            npc.setHealth(Math.min(npc.getHealth(), npc.getMaxHealth()));
        }
        npc.hiredNPCInfo.xpLevel = targetLevel;
        npc.hiredNPCInfo.xp = Math.min(npc.hiredNPCInfo.xp, Math.max(0, LOTRHiredNPCInfo.totalXPForLevel(targetLevel + 1) - 1));
        KOMEReflection.markHiredInfoDirty(npc.hiredNPCInfo);
        KOMEReflection.sendHiredInfoClientPacket(npc.hiredNPCInfo, false);
        EntityPlayer owner = KOMEReflection.getWorld(npc).func_152378_a(record.owner);
        if (owner != null) {
            if (record.isPlayerReserveFunded()) {
                KOMEPlayerPopulation pop = data.getPopulation(record.sourcePlayer == null ? record.owner : record.sourcePlayer);
                owner.addChatMessage(new ChatComponentText(getUnitName(npc) + " cannot level up: needs " + extraCost + " more player reserve " + record.type.key + " population, available " + pop.getAvailable(record.type) + "/" + pop.getTotal(record.type) + "."));
            } else {
                KOMETilePopulation population = data.getFundingPool(record);
                if (population != null) {
                    KOMEConquestTile sourceTile = data.conquestTiles.get(KOMEConquestTile.normalizeId(record.sourceTileId));
                    String controller = sourceTile == null ? "" : sourceTile.currentRulingFaction();
                    owner.addChatMessage(new ChatComponentText(getUnitName(npc) + " cannot level up: needs " + extraCost + " more " + record.type.key + " population from source tile " + record.sourceTileId + ", available " + population.getEffectiveAvailable(record.type, controller) + "/" + population.getEffectiveTotal(record.type, controller) + "."));
                }
            }
        }
    }

    private void releaseIfTracked(LOTREntityNPC npc) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        UUID entityId = KOMEReflection.getEntityUUID(npc);
        KOMEHiredUnitRecord record = data.hiredUnits.get(entityId);
        if (record == null) {
            return;
        }
        if (record.isMoving()) {
            return;
        }
        data.hiredUnits.remove(entityId);
        data.removeUnitFromCompany(record);
        EntityPlayer owner = KOMEReflection.getWorld(npc).func_152378_a(record.owner);
        if (record.farmhand) {
            if (owner != null) {
                owner.addChatMessage(new ChatComponentText("Farmhand slot freed: " + data.getFarmhandsUsed(record.owner) + "/" + data.getFarmhandLimit(record.owner) + " used"));
            }
        } else {
            KOMETilePopulation population = null;
            KOMEPlayerPopulation reserve = null;
            if (record.isPlayerReserveFunded()) {
                reserve = data.getPopulation(record.sourcePlayer == null ? record.owner : record.sourcePlayer);
                reserve.release(record.type, record.cost);
            } else {
                population = data.getFundingPool(record);
                if (population != null) {
                    population.release(record.type, record.cost);
                }
                data.releaseAllocationUsed(record);
            }
            if (owner != null) {
                if (reserve != null) {
                    owner.addChatMessage(new ChatComponentText("Player reserve population freed: " + record.cost + " " + record.type.key + " (" + reserve.getAvailable(record.type) + "/" + reserve.getTotal(record.type) + " available)"));
                } else if (population != null) {
                    KOMEConquestTile sourceTile = data.conquestTiles.get(KOMEConquestTile.normalizeId(record.sourceTileId));
                    String controller = sourceTile == null ? "" : sourceTile.currentRulingFaction();
                    owner.addChatMessage(new ChatComponentText("Population freed from source tile " + record.sourceTileId + ": " + record.cost + " " + record.type.key + " (" + population.getEffectiveAvailable(record.type, controller) + "/" + population.getEffectiveTotal(record.type, controller) + " available)"));
                }
            }
        }
        data.markDirty();
        data.syncConquestTiles();
    }

    private int getFactionPlayerAllocationAvailable(KOMEWorldData data, String faction, UUID playerId, KOMEPopulationType type) {
        int available = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : data.getAllocationsForFaction(faction)) {
            if (playerId.equals(allocation.playerUuid)) {
                available += allocation.getAvailable(type);
            }
        }
        return available;
    }

    private void releaseLinkedInactiveUnits(LOTREntityNPC npc) {
        releaseLinkedInactiveUnit(KOMEReflection.getRidingEntity(npc));
        releaseLinkedInactiveUnit(KOMEReflection.getRiddenByEntity(npc));
    }

    private void releaseLinkedInactiveUnit(Entity entity) {
        if (!(entity instanceof LOTREntityNPC)) {
            return;
        }
        LOTREntityNPC linkedNPC = (LOTREntityNPC) entity;
        if (!linkedNPC.isEntityAlive() || !linkedNPC.hiredNPCInfo.isActive) {
            releaseIfTracked(linkedNPC);
        }
    }

    private int refundDeniedHire(EntityPlayer owner, LOTREntityNPC hiredNPC) {
        UUID ownerID = KOMEReflection.getEntityUUID(owner);
        Integer lastCoins = lastCoinValues.get(ownerID);
        int[] lastCounts = lastCoinCounts.get(ownerID);
        if (lastCoins != null && lastCounts != null) {
            int currentCoins = LOTRItemCoin.getInventoryValue(owner, false);
            int spentCoins = lastCoins - currentCoins;
            if (spentCoins > 0) {
                restoreCoinCounts(owner, lastCounts);
                cacheCoinValue(owner);
                return spentCoins;
            }
        }
        int refund = getRefundCost(owner, hiredNPC);
        if (refund > 0) {
            LOTRItemCoin.giveCoins(refund, owner);
            cacheCoinValue(owner);
        }
        return refund;
    }

    private int getRefundCost(EntityPlayer owner, LOTREntityNPC hiredNPC) {
        UUID ownerID = KOMEReflection.getEntityUUID(owner);
        Integer lastCoins = lastCoinValues.get(ownerID);
        if (lastCoins != null) {
            int currentCoins = LOTRItemCoin.getInventoryValue(owner, false);
            int spentCoins = lastCoins - currentCoins;
            if (spentCoins > 0) {
                return spentCoins;
            }
        }
        LOTRUnitTradeEntry bestMatch = getMatchingTrade(owner, hiredNPC);
        if (bestMatch == null) {
            return 0;
        }
        Container container = owner.openContainer;
        LOTRHireableBase trader = ((LOTRContainerUnitTrade) container).theUnitTrader;
        return bestMatch.getCost(owner, trader);
    }

    private void cacheCoinValue(EntityPlayer player) {
        lastCoinValues.put(KOMEReflection.getEntityUUID(player), LOTRItemCoin.getInventoryValue(player, false));
        lastCoinCounts.put(KOMEReflection.getEntityUUID(player), getCoinCounts(player));
    }

    private void trackAllianceCivilTraderProgress(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || !(player.openContainer instanceof LOTRContainerTrade)) {
            return;
        }
        UUID playerID = KOMEReflection.getEntityUUID(player);
        Integer previous = lastCoinValues.get(playerID);
        if (previous == null) {
            return;
        }
        int current = LOTRItemCoin.getInventoryValue(player, false);
        int tradeValue = Math.abs(current - previous.intValue());
        if (tradeValue <= 0) {
            return;
        }
        LOTREntityNPC trader = ((LOTRContainerTrade) player.openContainer).theTraderNPC;
        LOTRFaction traderFaction = trader == null ? null : trader.getFaction();
        if (traderFaction == null) {
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        String playerFaction = getPlayerFactionKey(player, data);
        String traderFactionKey = traderFaction.codeName();
        if (playerFaction.length() == 0 || traderFactionKey == null || traderFactionKey.length() == 0) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.civilTier != 1) {
                continue;
            }
            if (!factionMatches(alliance.factionA, playerFaction) || !factionMatches(alliance.factionB, traderFactionKey)) {
                continue;
            }
            int delivered = alliance.getDelivered("civil.trade");
            if (delivered >= CIVIL_TRADER_REQUIRED) {
                continue;
            }
            int credited = Math.min(tradeValue, CIVIL_TRADER_REQUIRED - delivered);
            alliance.addDelivered("civil.trade", credited);
            changed = true;
            if (alliance.getDelivered("civil.trade") >= CIVIL_TRADER_REQUIRED) {
                alliance.setTier(KOMEAlliance.CIVIL, 2, "Trader progress", KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)));
                player.addChatMessage(new ChatComponentText("Civil alliance upgraded to T2 with " + traderFaction.factionName() + "."));
            }
        }
        if (changed) {
            data.markDirty();
            if (player instanceof EntityPlayerMP) {
                sendAllianceRefresh((EntityPlayerMP) player, data);
            }
        }
    }

    private void updateAllianceMilitaryKillProgress(EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        String playerFaction = getPlayerFactionKey(player, data);
        if (playerFaction.length() == 0) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.militaryTier != 1 || !factionMatches(alliance.factionA, playerFaction)) {
                continue;
            }
            LOTRFaction receiver = findFaction(alliance.factionB);
            if (receiver == null) {
                continue;
            }
            int current = LOTRLevelData.getData(player).getFactionData(receiver).getEnemiesKilled();
            int previous = alliance.getDelivered("military.kills");
            if (current > previous) {
                alliance.setDelivered("military.kills", Math.min(current, MILITARY_KILLS_REQUIRED));
                changed = true;
                int progress = alliance.getDelivered("military.kills");
                if (progress <= 5 || progress % 25 == 0) {
                    player.addChatMessage(new ChatComponentText("Military alliance kill progress with " + receiver.factionName() + ": " + progress + "/" + MILITARY_KILLS_REQUIRED + "."));
                }
            }
            if (alliance.getDelivered("military.kills") >= MILITARY_KILLS_REQUIRED) {
                alliance.setTier(KOMEAlliance.MILITARY, 2, "Enemy kills", KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)));
                player.addChatMessage(new ChatComponentText("Military alliance upgraded to T2 with " + receiver.factionName() + "."));
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
            sendAllianceRefresh(player, data);
        }
    }

    private void trackAllianceMilitaryKill(LOTREntityNPC npc, DamageSource source) {
        if (source == null || !(source.getEntity() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) source.getEntity();
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        String playerFaction = getPlayerFactionKey(player, data);
        LOTRFaction killedFaction = npc.getFaction();
        if (playerFaction.length() == 0 || killedFaction == null) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.militaryTier != 1 || !factionMatches(alliance.factionA, playerFaction)) {
                continue;
            }
            LOTRFaction receiver = findFaction(alliance.factionB);
            if (receiver == null || !isEnemyOf(receiver, killedFaction)) {
                continue;
            }
            int current = LOTRLevelData.getData(player).getFactionData(receiver).getEnemiesKilled();
            int next = Math.min(MILITARY_KILLS_REQUIRED, Math.max(current, alliance.getDelivered("military.kills") + 1));
            if (next > alliance.getDelivered("military.kills")) {
                alliance.setDelivered("military.kills", next);
                changed = true;
                if (next <= 5 || next % 25 == 0) {
                    player.addChatMessage(new ChatComponentText("Military alliance kill progress with " + receiver.factionName() + ": " + next + "/" + MILITARY_KILLS_REQUIRED + "."));
                }
                if (next >= MILITARY_KILLS_REQUIRED) {
                    alliance.setTier(KOMEAlliance.MILITARY, 2, "Enemy kills", KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)));
                    player.addChatMessage(new ChatComponentText("Military alliance upgraded to T2 with " + receiver.factionName() + "."));
                }
            }
        }
        if (changed) {
            data.markDirty();
            sendAllianceRefresh(player, data);
        }
    }

    private void sendAllianceRefresh(EntityPlayerMP player, KOMEWorldData data) {
        KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(data, player)), player);
    }

    private void updateAllianceTradeT2Progress(EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        String playerFaction = getPlayerFactionKey(player, data);
        if (playerFaction.length() == 0) {
            return;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.tradeTier != 1 || !factionMatches(alliance.factionA, playerFaction)) {
                continue;
            }
            if (alliance.getDelivered("trade.t2.coins") >= TRADE_T2_COINS_REQUIRED
                && data.getFactionFarmerPop(alliance.factionA) >= TRADE_T2_FARMER_POP_REQUIRED) {
                alliance.setTier(KOMEAlliance.TRADE, 2, "Trade requirements", KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player)));
                player.addChatMessage(new ChatComponentText("Trade alliance upgraded to T2 with " + displayFaction(alliance.factionB) + "."));
                changed = true;
            }
        }
        if (changed) {
            data.markDirty();
        }
    }

    private String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private boolean isEnemyOf(LOTRFaction faction, LOTRFaction possibleEnemy) {
        if (faction == null || possibleEnemy == null) {
            return false;
        }
        LOTRFactionRelations.Relation relation = LOTRFactionRelations.getRelations(faction, possibleEnemy);
        return relation == LOTRFactionRelations.Relation.ENEMY || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY;
    }

    private LOTRFaction findFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()
                && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
    }

    private String getPlayerFactionKey(EntityPlayer player, KOMEWorldData data) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge != null) {
            return KOMEAlliance.normalizeFactionKey(pledge.codeName());
        }
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        String faction = progression.getPledgedLordFaction();
        return faction == null ? "" : faction;
    }

    private boolean factionMatches(String a, String b) {
        return KOMEAlliance.normalizeFactionKey(a).equals(KOMEAlliance.normalizeFactionKey(b));
    }

    private int[] getCoinCounts(EntityPlayer player) {
        int[] counts = new int[LOTRItemCoin.values.length];
        InventoryPlayer inv = player.inventory;
        countCoinStack(inv.getItemStack(), counts);
        for (ItemStack stack : inv.mainInventory) {
            countCoinStack(stack, counts);
        }
        return counts;
    }

    private void countCoinStack(ItemStack stack, int[] counts) {
        if (stack != null && stack.getItem() instanceof LOTRItemCoin) {
            int coinType = stack.getItemDamage();
            if (coinType >= 0 && coinType < counts.length) {
                counts[coinType] += stack.stackSize;
            }
        }
    }

    private void restoreCoinCounts(EntityPlayer player, int[] targetCounts) {
        int[] currentCounts = getCoinCounts(player);
        for (int i = 0; i < currentCounts.length && i < targetCounts.length; i++) {
            int extra = currentCounts[i] - targetCounts[i];
            if (extra > 0) {
                removeCoinCount(player, i, extra);
            }
        }
        currentCounts = getCoinCounts(player);
        for (int i = 0; i < currentCounts.length && i < targetCounts.length; i++) {
            int missing = targetCounts[i] - currentCounts[i];
            if (missing > 0) {
                addCoinCount(player, i, missing);
            }
        }
    }

    private void removeCoinCount(EntityPlayer player, int coinType, int count) {
        InventoryPlayer inv = player.inventory;
        ItemStack held = inv.getItemStack();
        if (isCoinType(held, coinType)) {
            int taken = Math.min(count, held.stackSize);
            held.stackSize -= taken;
            count -= taken;
            if (held.stackSize <= 0) {
                inv.setItemStack(null);
            }
        }
        for (int slot = 0; slot < inv.mainInventory.length && count > 0; slot++) {
            ItemStack stack = inv.mainInventory[slot];
            if (!isCoinType(stack, coinType)) {
                continue;
            }
            int taken = Math.min(count, stack.stackSize);
            stack.stackSize -= taken;
            count -= taken;
            if (stack.stackSize <= 0) {
                inv.mainInventory[slot] = null;
            }
        }
    }

    private void addCoinCount(EntityPlayer player, int coinType, int count) {
        while (count > 0) {
            int stackSize = Math.min(count, 64);
            ItemStack stack = new ItemStack(LOTRMod.silverCoin, stackSize, coinType);
            if (!player.inventory.addItemStackToInventory(stack)) {
                player.dropPlayerItemWithRandomChoice(stack, false);
            }
            count -= stackSize;
        }
    }

    private boolean isCoinType(ItemStack stack, int coinType) {
        return stack != null && stack.getItem() instanceof LOTRItemCoin && stack.getItemDamage() == coinType;
    }

    private void enforceOpenContainerRestrictions(EntityPlayer player) {
        Container container = player.openContainer;
        if (container instanceof LOTRContainerTrade && !KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.NPC_TRADE)) {
            player.closeScreen();
            KOMEProgressionPermissions.deny(player, "You have not unlocked NPC Trade yet.");
            return;
        }
        if (!KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.COOKING)) {
            if (container instanceof LOTRContainerHobbitOven) {
                rejectCookableInputs(player, container, 0, 9);
            } else if (container instanceof ContainerFurnace) {
                rejectCookableInputs(player, container, 0, 1);
            }
        }
        if (!KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.STONEWORK)) {
            clearBlockedStoneToolResult(player, container);
        }
    }

    private void clearBlockedStoneToolResult(EntityPlayer player, Container container) {
        IInventory craftResult = null;
        if (container instanceof ContainerWorkbench) {
            craftResult = ((ContainerWorkbench) container).craftResult;
        } else if (container instanceof ContainerPlayer) {
            craftResult = ((ContainerPlayer) container).craftResult;
        }
        if (craftResult == null) {
            return;
        }
        ItemStack result = craftResult.getStackInSlot(0);
        if (!isStoneTool(result)) {
            return;
        }
        craftResult.setInventorySlotContents(0, null);
        UUID playerID = KOMEReflection.getEntityUUID(player);
        long now = KOMEReflection.getTotalWorldTime(KOMEReflection.getWorld(player));
        Long last = lastStoneCraftDenials.get(playerID);
        if (last == null || now - last.longValue() >= 40L) {
            lastStoneCraftDenials.put(playerID, now);
            KOMEProgressionPermissions.deny(player, "You have not unlocked Stonework yet.");
        }
    }

    private void rejectCookableInputs(EntityPlayer player, Container container, int startSlot, int endSlot) {
        boolean rejected = false;
        for (int i = startSlot; i < endSlot && i < container.inventorySlots.size(); i++) {
            Slot slot = (Slot) container.inventorySlots.get(i);
            ItemStack stack = slot == null ? null : slot.getStack();
            if (stack == null || !isCookableFood(stack)) {
                continue;
            }
            ItemStack returned = stack.copy();
            slot.putStack(null);
            slot.onSlotChanged();
            if (!player.inventory.addItemStackToInventory(returned)) {
                player.dropPlayerItemWithRandomChoice(returned, false);
            }
            rejected = true;
        }
        if (rejected) {
            KOMEProgressionPermissions.deny(player, "You have not unlocked Cooking yet. Cookable food returned.");
        }
    }

    private boolean isCookableFood(ItemStack stack) {
        ItemStack result = FurnaceRecipes.smelting().getSmeltingResult(stack);
        return result != null && result.getItem() instanceof ItemFood;
    }

    private boolean isFireOrLightItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.getItem() instanceof ItemBlock) {
            return isFireOrLightBlock(Block.getBlockFromItem(stack.getItem()));
        }
        String text = getItemText(stack);
        return text.contains("flintandsteel") || text.contains("firecharge") || text.contains("match");
    }

    private boolean isFireOrLightBlock(Block block) {
        if (block == null) {
            return false;
        }
        String name = String.valueOf(Block.blockRegistry.getNameForObject(block)).toLowerCase();
        return block == Blocks.torch
            || block == Blocks.fire
            || block == Blocks.lit_pumpkin
            || name.contains("torch")
            || name.contains("lantern")
            || name.contains("chandelier")
            || name.contains("brazier")
            || name.contains("lamp");
    }

    private boolean isMeat(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemFood)) {
            return false;
        }
        Item item = stack.getItem();
        return item == net.minecraft.init.Items.beef
            || item == net.minecraft.init.Items.cooked_beef
            || item == net.minecraft.init.Items.porkchop
            || item == net.minecraft.init.Items.cooked_porkchop
            || item == net.minecraft.init.Items.chicken
            || item == net.minecraft.init.Items.cooked_chicken
            || item == net.minecraft.init.Items.fish
            || item == net.minecraft.init.Items.cooked_fished
            || containsMeatWord(stack);
    }

    private boolean isAlcoholicDrink(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof LOTRItemMug)) {
            return false;
        }
        LOTRItemMug mug = (LOTRItemMug) stack.getItem();
        return mug.isFullMug && mug.alcoholicity > 0.0f;
    }

    private boolean isHoe(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemHoe;
    }

    private boolean isFishingRod(ItemStack stack) {
        return stack != null && (stack.getItem() == Items.fishing_rod || stack.getItem() instanceof ItemFishingRod);
    }

    private boolean isPouch(ItemStack stack) {
        return stack != null && stack.getItem() instanceof LOTRItemPouch;
    }

    private boolean isBreedingInteraction(EntityAnimal animal, ItemStack stack) {
        return animal != null
            && stack != null
            && animal.getGrowingAge() == 0
            && !animal.isInLove()
            && animal.isBreedingItem(stack);
    }

    private boolean isMountEntity(Entity entity) {
        return entity instanceof EntityHorse || entity instanceof LOTRNPCMount;
    }

    private boolean isStoneTool(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item == net.minecraft.init.Items.stone_sword
            || item == net.minecraft.init.Items.stone_pickaxe
            || item == net.minecraft.init.Items.stone_axe
            || item == net.minecraft.init.Items.stone_shovel
            || item == net.minecraft.init.Items.stone_hoe
            || item == LOTRMod.spearStone) {
            return true;
        }
        String text = getItemText(stack);
        return text.contains("stone")
            && (item instanceof ItemTool || item instanceof ItemSword || item instanceof ItemHoe)
            && (text.contains("pickaxe") || text.contains("axe") || text.contains("shovel") || text.contains("hoe") || text.contains("sword"));
    }

    private boolean containsMeatWord(ItemStack stack) {
        String text = getItemText(stack);
        return text.contains("meat")
            || text.contains("mutton")
            || text.contains("venison")
            || text.contains("deer")
            || text.contains("rabbit")
            || text.contains("gammon")
            || text.contains("kebab")
            || text.contains("fish")
            || text.contains("salmon")
            || text.contains("pork")
            || text.contains("beef")
            || text.contains("steak")
            || text.contains("chicken")
            || text.contains("camel")
            || text.contains("lion")
            || text.contains("rhino");
    }

    private String getItemText(ItemStack stack) {
        return (String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())) + " " + stack.getDisplayName() + " " + stack.getUnlocalizedName()).toLowerCase();
    }

    private void removeMatchingItems(EntityPlayer player, ItemStack target, int amount) {
        if (target == null || amount <= 0) {
            return;
        }
        InventoryPlayer inv = player.inventory;
        ItemStack held = inv.getItemStack();
        if (matchesItem(held, target)) {
            int removed = Math.min(amount, held.stackSize);
            held.stackSize -= removed;
            amount -= removed;
            if (held.stackSize <= 0) {
                inv.setItemStack(null);
            }
        }
        for (int slot = 0; slot < inv.mainInventory.length && amount > 0; slot++) {
            ItemStack stack = inv.mainInventory[slot];
            if (!matchesItem(stack, target)) {
                continue;
            }
            int removed = Math.min(amount, stack.stackSize);
            stack.stackSize -= removed;
            amount -= removed;
            if (stack.stackSize <= 0) {
                inv.mainInventory[slot] = null;
            }
        }
    }

    private boolean matchesItem(ItemStack stack, ItemStack target) {
        return stack != null
            && target != null
            && stack.getItem() == target.getItem()
            && stack.getItemDamage() == target.getItemDamage();
    }

    private boolean isPledgeLord(LOTRHireableBase hireable) {
        if (hireable instanceof LOTRUnitTradeable) {
            LOTRUnitTradeEntry[] entries = ((LOTRUnitTradeable) hireable).getUnits().tradeEntries;
            if (entries != null) {
                for (LOTRUnitTradeEntry entry : entries) {
                    if (entry != null && entry.task != LOTRHiredNPCInfo.Task.FARMER) {
                        return true;
                    }
                }
            }
        }
        String className = hireable.getClass().getSimpleName().toLowerCase();
        return className.contains("captain") || className.contains("commander") || className.contains("lord")
            || className.contains("warlord") || className.contains("chieftain");
    }

    private boolean isPledgedLord(Entity target, KOMEPlayerProgression progression) {
        if (target == null || progression == null || !progression.hasPledgedLord()) {
            return false;
        }
        String pledgedID = progression.getPledgedLordID();
        return pledgedID != null && pledgedID.equals(String.valueOf(KOMEReflection.getEntityUUID(target)));
    }

    private LOTRUnitTradeEntry getMatchingTrade(EntityPlayer owner, LOTREntityNPC hiredNPC) {
        Container container = owner.openContainer;
        if (!(container instanceof LOTRContainerUnitTrade)) {
            return null;
        }
        LOTRHireableBase trader = ((LOTRContainerUnitTrade) container).theUnitTrader;
        if (!(trader instanceof LOTRUnitTradeable)) {
            return null;
        }
        LOTRUnitTradeEntry bestMatch = null;
        boolean mountedNPC = KOMEReflection.getRidingEntity(hiredNPC) != null;
        for (LOTRUnitTradeEntry entry : ((LOTRUnitTradeable) trader).getUnits().tradeEntries) {
            if (entry.entityClass != null && entry.entityClass.isAssignableFrom(hiredNPC.getClass())) {
                bestMatch = entry;
                if (mountedNPC == isMountedTrade(entry)) {
                    break;
                }
            }
        }
        return bestMatch;
    }

    private int getRawPopulationCost(LOTREntityNPC npc, boolean mounted) {
        int healthCost = Math.max(1, MathHelper.ceiling_float_int(KOMEReflection.getMaxHealthOrFallback(npc, defaultUnitCost)));
        return mounted ? healthCost + 25 : healthCost;
    }

    private int applyHireTypeCost(int rawCost, KOMEPopulationType hireType) {
        return Math.max(1, rawCost);
    }

    private boolean isMountedUnit(LOTREntityNPC npc, LOTRUnitTradeEntry trade) {
        return KOMEReflection.getRidingEntity(npc) != null || isMountedTrade(trade) || isMountedName(npc);
    }

    private boolean isMountedTrade(LOTRUnitTradeEntry trade) {
        return trade != null && trade.mountClass != null;
    }

    private boolean isMountedName(LOTREntityNPC npc) {
        String className = npc.getClass().getSimpleName().toLowerCase();
        String displayName = getUnitName(npc).toLowerCase();
        return className.contains("outrider")
            || displayName.contains("outrider")
            || displayName.contains("mounted")
            || displayName.contains("horse")
            || displayName.contains("warg")
            || displayName.contains("boar")
            || displayName.contains("elk")
            || displayName.contains("camel")
            || displayName.contains("rhino")
            || displayName.contains("zebra")
            || displayName.contains("giraffe")
            || displayName.contains("spider rider");
    }

    private String getUnitName(LOTREntityNPC npc) {
        String name = npc.getCommandSenderName();
        if (name == null || name.trim().isEmpty()) {
            name = npc.getClass().getSimpleName();
        }
        return name;
    }

    private String getFarmhandName(LOTREntityNPC npc) {
        String name = getUnitName(npc);
        return isNumberOnly(name) ? "Farmhand" : name;
    }

    private boolean isNumberOnly(String value) {
        return value != null && value.trim().matches("[0-9]+");
    }
}
