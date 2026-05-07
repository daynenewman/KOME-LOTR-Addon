package kome.common.data;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketHireType;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.inventory.LOTRContainerUnitTrade;
import lotr.common.LOTRMod;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public class KOMEEvents {
    public static int defaultUnitCost = 25;
    private final Map<UUID, Integer> lastCoinValues = new HashMap<>();
    private final Map<UUID, int[]> lastCoinCounts = new HashMap<>();

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(event.player));
            data.syncTerritories((EntityPlayerMP) event.player);
            KOMEPacketHandler.network.sendTo(new KOMEPacketHireType(data.getPopulation(KOMEReflection.getEntityUUID(event.player)).hireType), (EntityPlayerMP) event.player);
            cacheCoinValue((EntityPlayer) event.player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !KOMEReflection.getWorld(event.player).isRemote) {
            cacheCoinValue(event.player);
        }
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!event.world.isRemote && event.entity instanceof LOTREntityNPC) {
            handleHiredUnit((LOTREntityNPC) event.entity);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!KOMEReflection.getWorld(event.entityLiving).isRemote && event.entityLiving instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC) event.entityLiving;
            if (!npc.isEntityAlive()) {
                releaseIfTracked(npc);
                releaseLinkedInactiveUnits(npc);
                return;
            }
            if (npc.hiredNPCInfo.isActive) {
                handleHiredUnit(npc);
                updateTrackedPopulationCost(npc);
            } else {
                releaseIfTracked(npc);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!KOMEReflection.getWorld(event.entityLiving).isRemote && event.entityLiving instanceof LOTREntityNPC) {
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
        KOMEPlayerPopulation pop = data.getPopulation(info.getHiringPlayerUUID());
        KOMEPopulationType hireType = pop.hireType == null ? KOMEPopulationType.OFFENSIVE : pop.hireType;
        if (isFarmhand) {
            int limit = pop.getFarmhandLimit();
            int used = data.getFarmhandsUsed(info.getHiringPlayerUUID());
            if (used >= limit) {
                int refund = refundDeniedHire(owner, npc);
                owner.addChatMessage(new ChatComponentText("You do not have enough farmhand slots. Farmhands used: " + used + "/" + limit + (refund > 0 ? ". Refunded " + refund + " coins." : "")));
                KOMEReflection.setDead(npc);
                return;
            }
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.entity = entityID;
            record.owner = info.getHiringPlayerUUID();
            record.type = KOMEPopulationType.OFFENSIVE;
            record.cost = 1;
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
            owner.addChatMessage(new ChatComponentText("You do not have sufficient " + hireType.key + " population to hire another worker. Required: " + populationCost + ", available: " + Math.max(0, armyTotal - armyUsed) + "/" + armyTotal + (refund > 0 ? ". Refunded " + refund + " coins." : "")));
            KOMEReflection.setDead(npc);
            return;
        }
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = entityID;
        record.owner = info.getHiringPlayerUUID();
        record.type = hireType;
        record.cost = populationCost;
        record.baseCost = rawPopulationCost;
        record.mounted = mounted;
        record.unitName = getUnitName(npc);
        data.hiredUnits.put(entityID, record);
        data.markDirty();
    }

    private void updateTrackedPopulationCost(LOTREntityNPC npc) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(npc));
        KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(npc));
        if (record == null || record.farmhand) {
            return;
        }
        record.unitName = getUnitName(npc);
        record.mounted = record.mounted || isMountedUnit(npc, null);
        int rawPopulationCost = getRawPopulationCost(npc, record.mounted);
        int currentCost = applyHireTypeCost(rawPopulationCost, record.type);
        if (currentCost == record.cost && rawPopulationCost == record.baseCost) {
            return;
        }
        record.baseCost = rawPopulationCost;
        record.cost = currentCost;
        data.markDirty();
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
