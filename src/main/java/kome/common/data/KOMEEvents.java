package kome.common.data;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketHireType;
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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerOpenContainerEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.BlockEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class KOMEEvents {
    public static int defaultUnitCost = 25;
    private final Map<UUID, Integer> lastCoinValues = new HashMap<>();
    private final Map<UUID, int[]> lastCoinCounts = new HashMap<>();
    private final Map<UUID, Long> lastStoneCraftDenials = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(event.player));
            data.rememberPlayerName(KOMEReflection.getEntityUUID(event.player), event.player.getCommandSenderName());
            data.syncTerritories((EntityPlayerMP) event.player);
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
            }
            cacheCoinValue(event.player);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!KOMEReflection.isRemote(event.world) && event.entity instanceof LOTREntityNPC) {
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
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
            if (isPledgedLord(event.target, progression)) {
                KOMEProgressionQuotas.processDeposits(progression);
                KOMEProgressionQuotas.applyCompletedQuotas(progression);
                data.markDirty();
                KOMEProgressionQuotas.sendQuotaLedger(player, progression);
                player.displayGUIChest(new KOMEProgressionOfferingInventory(data, progression, player));
                player.addChatMessage(new ChatComponentText("Opened offerings for " + progression.getPledgedLordDisplay() + "."));
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
            int limit = pop.getFarmhandLimit();
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
            data.hiredUnits.put(entityID, record);
            data.markDirty();
            return;
        }

        LOTRUnitTradeEntry trade = getMatchingTrade(owner, npc);
        boolean mounted = isMountedUnit(npc, trade);
        int rawPopulationCost = getRawPopulationCost(npc, mounted);
        int populationCost = applyHireTypeCost(rawPopulationCost, hireType);
        int armyUsed = data.getArmyPopulationUsed(info.getHiringPlayerUUID(), hireType);
        int armyTotal = pop.getTotal(hireType);
        if (armyTotal - armyUsed < populationCost) {
            int refund = refundDeniedHire(owner, npc);
            KOMEProgressionPermissions.deny(owner, "You do not have sufficient " + hireType.key + " population to hire another worker. Required: " + populationCost + ", available: " + Math.max(0, armyTotal - armyUsed) + "/" + armyTotal + (refund > 0 ? ". Refunded " + refund + " coins." : ""));
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
        data.hiredUnits.put(entityID, record);
        data.markDirty();
    }

    private boolean isPledgedLord(Entity target, KOMEPlayerProgression progression) {
        if (progression == null || !progression.hasPledgedLord()) {
            return false;
        }
        String pledgedID = progression.getPledgedLordID();
        return pledgedID != null && pledgedID.equals(String.valueOf(KOMEReflection.getEntityUUID(target)));
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
        record.level = currentLevel;
        record.baseCost = rawPopulationCost;
        record.cost = currentCost;
        data.markDirty();
    }

    private boolean hasPopulationForCostIncrease(KOMEWorldData data, KOMEHiredUnitRecord record, int extraCost) {
        if (extraCost <= 0) {
            return true;
        }
        KOMEPlayerPopulation pop = data.getPopulation(record.owner);
        int used = data.getArmyPopulationUsed(record.owner, record.type);
        int available = Math.max(0, pop.getTotal(record.type) - used);
        return available >= extraCost;
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
            KOMEPlayerPopulation pop = data.getPopulation(record.owner);
            int used = data.getArmyPopulationUsed(record.owner, record.type);
            int available = Math.max(0, pop.getTotal(record.type) - used);
            owner.addChatMessage(new ChatComponentText(getUnitName(npc) + " cannot level up: needs " + extraCost + " more " + record.type.key + " population, available " + available + "/" + pop.getTotal(record.type) + "."));
        }
    }

    private void releaseIfTracked(LOTREntityNPC npc) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        KOMEHiredUnitRecord record = data.hiredUnits.remove(KOMEReflection.getEntityUUID(npc));
        if (record == null) {
            return;
        }
        KOMEPlayerPopulation pop = data.getPopulation(record.owner);
        EntityPlayer owner = KOMEReflection.getWorld(npc).func_152378_a(record.owner);
        if (record.farmhand) {
            if (owner != null) {
                owner.addChatMessage(new ChatComponentText("Farmhand slot freed: " + data.getFarmhandsUsed(record.owner) + "/" + pop.getFarmhandLimit() + " used"));
            }
        } else {
            if (owner != null) {
                int armyUsed = data.getArmyPopulationUsed(record.owner, record.type);
                int armyTotal = pop.getTotal(record.type);
                owner.addChatMessage(new ChatComponentText("Population freed: " + record.cost + " " + record.type.key + " (" + Math.max(0, armyTotal - armyUsed) + "/" + armyTotal + " available)"));
            }
        }
        data.markDirty();
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
        rawCost = Math.max(1, rawCost);
        if (hireType == KOMEPopulationType.DEFENSIVE) {
            return Math.max(1, MathHelper.ceiling_float_int(rawCost / 2.0f));
        }
        return rawCost;
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
