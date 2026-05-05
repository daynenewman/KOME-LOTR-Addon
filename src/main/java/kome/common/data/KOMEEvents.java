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
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;

import java.util.UUID;

public class KOMEEvents {
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
            record.unitName = getFarmhandName(npc);
            data.hiredUnits.put(entityID, record);
            data.markDirty();
            return;
        }

        LOTRUnitTradeEntry trade = getMatchingTrade(owner, npc);
        boolean mounted = isMountedUnit(npc, trade);
        int baseCost = getBasePopulationCost(npc, trade, mounted);
        int populationCost = getPopulationCost(npc, baseCost);
        int armyUsed = data.getArmyPopulationUsed(info.getHiringPlayerUUID());
        int armyTotal = pop.getCombinedTotal();
        if (armyTotal - armyUsed < populationCost) {
            int refund = getRefundCost(owner, npc);
            if (refund > 0) {
                LOTRItemCoin.giveCoins(refund, owner);
            }
            owner.addChatMessage(new ChatComponentText("You do not have sufficient total population to hire another worker. Required: " + populationCost + ", available: " + Math.max(0, armyTotal - armyUsed) + "/" + armyTotal + (refund > 0 ? ". Refunded " + refund + " coins." : "")));
            KOMEReflection.setDead(npc);
            return;
        }
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = entityID;
        record.owner = info.getHiringPlayerUUID();
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = populationCost;
        record.baseCost = baseCost;
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
        if (record.baseCost <= 0) {
            record.baseCost = getBasePopulationCost(npc, null, record.mounted);
        } else if (record.mounted && record.baseCost < 50) {
            record.baseCost = 50;
        }
        int currentCost = getPopulationCost(npc, record.baseCost);
        if (currentCost == record.cost) {
            return;
        }
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
                int armyUsed = data.getArmyPopulationUsed(record.owner);
                int armyTotal = pop.getCombinedTotal();
                owner.addChatMessage(new ChatComponentText("Population freed: " + record.cost + " total (" + Math.max(0, armyTotal - armyUsed) + "/" + armyTotal + " available)"));
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

    private int getPopulationCost(LOTREntityNPC npc, int baseCost) {
        int healthCost = Math.max(1, MathHelper.ceiling_float_int(KOMEReflection.getMaxHealthOrFallback(npc, defaultUnitCost)));
        return Math.max(Math.max(1, baseCost), healthCost);
    }

    private int getBasePopulationCost(LOTREntityNPC npc, LOTRUnitTradeEntry trade, boolean mounted) {
        String name = npc.getClass().getSimpleName();
        if (name.contains("OlogHai") || name.contains("Troll") && !name.contains("HalfTroll")) {
            return 125;
        }
        if (name.contains("Huorn")) {
            return 75;
        }
        if (name.contains("WargBombardier") || mounted || isMountedTrade(trade) || isMountedName(npc)) {
            return 50;
        }
        return defaultUnitCost;
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
