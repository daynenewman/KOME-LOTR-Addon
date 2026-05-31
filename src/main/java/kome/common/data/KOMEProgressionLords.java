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
        if (player == null || lord == null || !(lord instanceof Entity) || !isPledgeLord(lord)) {
            throw new WrongUsageException("Choose a captain or unit-trading lord.");
        }
        if (!KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.PLEDGE)) {
            return false;
        }
        Entity entity = (Entity) lord;
        if (player.getDistanceSqToEntity(entity) > 64.0D) {
            throw new WrongUsageException("Stand within 8 blocks of the lord you want to pledge to.");
        }
        LOTRFaction faction = lord.getFaction();
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        progression.setPledgedLord(String.valueOf(KOMEReflection.getEntityUUID(entity)), lord.getNPCName(), faction == null ? "" : faction.factionName());
        boolean changed = progression.grant("wanderer.find_serf_lord");
        changed = KOMEProgressionAutoCompleter.applyUnlocks(progression) > 0 || changed;
        data.markDirty();
        KOMEProgressionAutoCompleter.syncPlayer(player, progression);
        KOMEProgressionTitles.updatePlayerTitle(player);
        player.addChatMessage(new ChatComponentText("Pledged loyalty to " + progression.getPledgedLordDisplay() + ". Bring your quotas to this lord."));
        if (changed) {
            player.addChatMessage(new ChatComponentText("Completed: Pledge to a Lord"));
        }
        return true;
    }

    public static void openOfferings(EntityPlayerMP player) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        if (!progression.hasPledgedLord()) {
            throw new WrongUsageException("Pledge to a lord first.");
        }
        KOMEProgressionQuotas.processDeposits(progression);
        KOMEProgressionQuotas.applyCompletedQuotas(progression);
        data.markDirty();
        KOMEProgressionQuotas.sendQuotaLedger(player, progression);
        player.displayGUIChest(new KOMEProgressionOfferingInventory(data, progression, player));
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
