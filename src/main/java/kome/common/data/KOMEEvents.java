package kome.common.data;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.inventory.LOTRContainerUnitTrade;
import lotr.common.item.LOTRItemCoin;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.UUID;

public class KOMEEvents {
    public static int maximumHiredUnitLevel = 40;
    public static int defaultUnitCost = 25;

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            KOMEWorldData.get(KOMEReflection.getWorld(event.player)).syncTerritories((EntityPlayerMP) event.player);
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
            capLevel(npc);
            if (npc.hiredNPCInfo.isActive) {
                handleHiredUnit(npc);
            } else {
                releaseIfTracked(npc);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!KOMEReflection.getWorld(event.entityLiving).isRemote && event.entityLiving instanceof LOTREntityNPC) {
            releaseIfTracked((LOTREntityNPC) event.entityLiving);
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
        if (isFarmhand) {
            int limit = pop.getFarmhandLimit();
            int used = data.getFarmhandsUsed(info.getHiringPlayerUUID());
            if (used >= limit) {
                int refund = getRefundCost(owner, npc);
                if (refund > 0) {
                    LOTRItemCoin.giveCoins(refund, owner);
                }
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
            data.hiredUnits.put(entityID, record);
            data.markDirty();
            return;
        }

        LOTRUnitTradeEntry trade = getMatchingTrade(owner, npc);
        int populationCost = getPopulationCost(npc, trade);
        KOMEPopulationType populationType = getPopulationType(trade);
        if (!pop.tryUse(populationType, populationCost)) {
            int refund = getRefundCost(owner, npc);
            if (refund > 0) {
                LOTRItemCoin.giveCoins(refund, owner);
            }
            owner.addChatMessage(new ChatComponentText("You do not have sufficient " + populationType.key + " population to hire another worker. Required: " + populationCost + ", available: " + pop.getAvailable(populationType) + "/" + pop.getTotal(populationType) + (refund > 0 ? ". Refunded " + refund + " coins." : "")));
            KOMEReflection.setDead(npc);
            return;
        }
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = entityID;
        record.owner = info.getHiringPlayerUUID();
        record.type = populationType;
        record.cost = populationCost;
        data.hiredUnits.put(entityID, record);
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
            pop.release(record.type, record.cost);
            if (owner != null) {
            owner.addChatMessage(new ChatComponentText("Population freed: " + record.cost + " " + record.type.key + " (" + pop.getAvailable(record.type) + "/" + pop.getTotal(record.type) + " available)"));
            }
        }
        data.markDirty();
    }

    private void capLevel(LOTREntityNPC npc) {
        if (maximumHiredUnitLevel <= 0 || !npc.hiredNPCInfo.isActive) {
            return;
        }
        if (npc.hiredNPCInfo.xpLevel > maximumHiredUnitLevel) {
            npc.hiredNPCInfo.xpLevel = maximumHiredUnitLevel;
        }
        int capXP = LOTRHiredNPCInfo.totalXPForLevel(maximumHiredUnitLevel);
        if (npc.hiredNPCInfo.xp > capXP) {
            npc.hiredNPCInfo.xp = capXP;
        }
    }

    private int getRefundCost(EntityPlayer owner, LOTREntityNPC hiredNPC) {
        LOTRUnitTradeEntry bestMatch = getMatchingTrade(owner, hiredNPC);
        if (bestMatch == null) {
            return 0;
        }
        Container container = owner.openContainer;
        LOTRHireableBase trader = ((LOTRContainerUnitTrade) container).theUnitTrader;
        return bestMatch.getCost(owner, trader);
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
        for (LOTRUnitTradeEntry entry : ((LOTRUnitTradeable) trader).getUnits().tradeEntries) {
            if (entry.entityClass != null && entry.entityClass.isAssignableFrom(hiredNPC.getClass())) {
                bestMatch = entry;
                break;
            }
        }
        return bestMatch;
    }

    private int getPopulationCost(LOTREntityNPC npc, LOTRUnitTradeEntry trade) {
        String name = npc.getClass().getSimpleName();
        if (name.contains("OlogHai") || name.contains("Troll") && !name.contains("HalfTroll")) {
            return 125;
        }
        if (name.contains("Huorn")) {
            return 75;
        }
        if (name.contains("WargBombardier") || trade != null && trade.mountClass != null) {
            return 50;
        }
        return defaultUnitCost;
    }

    private KOMEPopulationType getPopulationType(LOTRUnitTradeEntry trade) {
        if (trade != null && trade.getPopulationType() != null && "defensive".equalsIgnoreCase(trade.getPopulationType().getDisplayName())) {
            return KOMEPopulationType.DEFENSIVE;
        }
        return KOMEPopulationType.OFFENSIVE;
    }
}
