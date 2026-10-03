package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTRHireableBase;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeable;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.List;

public class KOMEProgressionLords {
    public static boolean pledgeToLord(EntityPlayerMP player, LOTRHireableBase lord) {
        if(player!=null) {
            player.addChatMessage(new ChatComponentText(
                "Legacy pledged-lord selection is retired. Use canonical Master and Liege relationships instead."));
        }
        return false;
    }

    public static void openOfferings(EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerProgression progression = data.progressions.get(KOMEReflection.getEntityUUID(player));
        if (progression == null || !progression.getSerfKnightProgression().hasLiege()) {
            throw new WrongUsageException("A committed Liege relationship is required for rank offerings.");
        }
        if (findNearbyCanonicalLiege(player, progression) == null) {
            throw new WrongUsageException("Stand near your Liege to open rank offerings.");
        }
        KOMEProgressionQuotas.processDeposits(progression);
        KOMEProgressionQuotas.applyCompletedQuotas(progression);
        data.markDirty();
        KOMEProgressionQuotas.sendQuotaLedger(player, progression);
        player.displayGUIChest(new KOMEProgressionOfferingInventory(data, progression, player));
    }

    public static LOTREntityNPC findNearbyCanonicalLiege(EntityPlayerMP player, KOMEPlayerProgression progression) {
        if (player == null || progression == null) return null;
        KOMEProgressionNpcRef liege = progression.getSerfKnightProgression().getLiege();
        if (!liege.isSet()) return null;
        World world = KOMEReflection.getWorld(player);
        List entities = world.getEntitiesWithinAABB(LOTREntityNPC.class, player.boundingBox.expand(8.0D, 4.0D, 8.0D));
        for (Object object : entities) {
            if (!(object instanceof LOTREntityNPC)) continue;
            LOTREntityNPC npc = (LOTREntityNPC) object;
            if (player.getDistanceSqToEntity(npc) <= 64.0D
                    && liege.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))) {
                return npc;
            }
        }
        return null;
    }

    public static boolean isCanonicalLiege(Entity target, KOMEPlayerProgression progression) {
        if (!(target instanceof LOTREntityNPC) || progression == null) return false;
        KOMEProgressionNpcRef liege = progression.getSerfKnightProgression().getLiege();
        return liege.isSet()
            && liege.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf((LOTREntityNPC) target));
    }

    public static LOTRHireableBase findNearbyPledgeLord(EntityPlayerMP player) {
        World world = KOMEReflection.getWorld(player);
        List entities = world.getEntitiesWithinAABB(LOTREntityNPC.class, player.boundingBox.expand(8.0D, 4.0D, 8.0D));
        LOTRHireableBase nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Object object : entities) {
            if (!(object instanceof LOTRHireableBase) || !(object instanceof Entity)) {
                continue;
            }
            LOTRHireableBase hireable = (LOTRHireableBase) object;
            Entity entity = (Entity) object;
            if (!isPledgeLord(hireable)) {
                continue;
            }
            double distance = player.getDistanceSqToEntity(entity);
            if (distance < nearestDistance) {
                nearest = hireable;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static boolean isPledgeLord(LOTRHireableBase hireable) {
        return isCombatUnitHiringNpc(hireable);
    }

    /** Shared noble-hiring predicate: farmer-only traders are never noble lieges. */
    public static boolean isCombatUnitHiringNpc(LOTRHireableBase hireable) {
        return isCombatUnitHiringNpc((Object) hireable);
    }

    /** Supports base LOTR NPC references as well as the narrower hireable interaction type. */
    public static boolean isCombatUnitHiringNpc(LOTREntityNPC npc) {
        return isCombatUnitHiringNpc((Object) npc);
    }

    /**
     * Standing Trials must use an NPC that can actually open LOTR's unit-trader
     * Talk/Hire GUI. Class-name heuristics are intentionally not accepted here:
     * otherwise an NPC can receive the native quest indicator without having a
     * GUI capable of hosting KOME's Quest button.
     */
    public static boolean isStandingTrialLiegeCandidate(LOTREntityNPC npc) {
        return npc instanceof LOTRUnitTradeable
            && hasCombatUnitTrades((LOTRUnitTradeable) npc);
    }

    private static boolean hasCombatUnitTrades(LOTRUnitTradeable hireable) {
        if (hireable == null || hireable.getUnits() == null) return false;
        LOTRUnitTradeEntry[] entries = hireable.getUnits().tradeEntries;
        if (entries == null) return false;
        boolean hasEntry = false;
        for (LOTRUnitTradeEntry entry : entries) {
            hasEntry |= entry != null;
            if (entry != null && entry.task != LOTRHiredNPCInfo.Task.FARMER) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCombatUnitHiringNpc(Object hireable) {
        if (hireable instanceof LOTRUnitTradeable) {
            LOTRUnitTradeable unitTradeable = (LOTRUnitTradeable) hireable;
            if (hasCombatUnitTrades(unitTradeable)) return true;
            if (unitTradeable.getUnits() != null && unitTradeable.getUnits().tradeEntries != null) {
                for (LOTRUnitTradeEntry entry : unitTradeable.getUnits().tradeEntries) {
                    if (entry != null) return false;
                }
            }
        }
        String className = hireable == null ? "" : hireable.getClass().getSimpleName().toLowerCase();
        return className.contains("captain") || className.contains("commander") || className.contains("lord") || className.contains("warlord") || className.contains("chieftain");
    }

    public static boolean isPledgedLord(Entity target, KOMEPlayerProgression progression) {
        if (target == null || progression == null || !progression.hasPledgedLord()) {
            return false;
        }
        String pledgedID = progression.getPledgedLordID();
        return pledgedID != null && pledgedID.equals(String.valueOf(KOMEReflection.getEntityUUID(target)));
    }
}
