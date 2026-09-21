package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityQuestInfo;
import lotr.common.fac.LOTRFaction;
import lotr.common.network.LOTRPacketHandler;
import lotr.common.network.LOTRPacketMiniquestOffer;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/** Narrow adapter for KOME-authored offers hosted by LOTR's NPC quest presentation. */
public final class KOMEProgressionOfferBridge {
    public static final int OFFER_DENOMINATOR = 3;
    public static final long OPPORTUNITY_WINDOW_DAYS = 3L;
    private static Field npcField;
    private static boolean registered;

    private KOMEProgressionOfferBridge() { }

    /** Called on both physical sides before packet decoding can load this type. */
    public static synchronized void registerQuestType() {
        if (registered) return;
        try {
            Method method = LOTRMiniQuest.class.getDeclaredMethod("registerQuestType", String.class, Class.class);
            method.setAccessible(true);
            method.invoke(null, KOMESerfdomOfferQuest.TYPE, KOMESerfdomOfferQuest.class);
            registered = true;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to register KOME Serfdom miniquest type", e);
        }
    }

    public static long opportunityWindow(long day) { return Math.max(0L, day) / OPPORTUNITY_WINDOW_DAYS; }
    public static boolean isSelected(UUID player, UUID npc, long window) {
        if (player == null || npc == null) return false;
        long value = player.getMostSignificantBits() ^ player.getLeastSignificantBits()
            ^ npc.getMostSignificantBits() ^ Long.rotateLeft(npc.getLeastSignificantBits(), 17) ^ window;
        value ^= value >>> 33; value *= 0xff51afd7ed558ccdL; value ^= value >>> 33;
        return Math.floorMod(value, OFFER_DENOMINATOR) == 0;
    }

    public static boolean isExternalOffer(LOTRMiniQuest offer) { return offer instanceof KOMESerfdomOfferQuest; }
    public static boolean canOffer(LOTREntityQuestInfo info, EntityPlayer player) {
        LOTRMiniQuest offer = info == null || player == null ? null : info.getOfferFor(player);
        return offer instanceof KOMESerfdomOfferQuest && isOfferActive((KOMESerfdomOfferQuest) offer, player, npc(info));
    }

    /** Creates only a deterministic player-specific opportunity. Called as an NPC becomes relevant. */
    public static boolean ensureSerfdomOffer(EntityPlayerMP player, LOTREntityNPC npc) {
        if (player == null || npc == null || npc.questInfo == null || !eligiblePlayer(player) || !eligibleNpc(player, npc)) return false;
        LOTRMiniQuest current = npc.questInfo.getOfferFor(player);
        long window = opportunityWindow(KOMESerfKnightService.calendarDayNow());
        if (current instanceof KOMESerfdomOfferQuest) {
            if (!((KOMESerfdomOfferQuest) current).isExpired(window)) return true;
            npc.questInfo.clearPlayerSpecificOffer(player);
        } else if (current != null) return false;
        if (KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).declinedSerfdomOfferToday(npc.getUniqueID().toString(), KOMESerfKnightService.calendarDayNow())) return false;
        if (!isSelected(player.getUniqueID(), npc.getUniqueID(), window)) return false;
        npc.questInfo.setPlayerSpecificOffer(player, new KOMESerfdomOfferQuest(LOTRLevelData.getData(player), npc, window, storyFor(npc)));
        npc.questInfo.sendData(player);
        return true;
    }

    public static boolean handleInteraction(LOTREntityQuestInfo info, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || !canOffer(info, player)) return false;
        LOTREntityNPC npc = npc(info);
        LOTRMiniQuest offer = info.getOfferFor(player);
        if (npc == null || offer == null) return false;
        NBTTagCompound tag = new NBTTagCompound(); offer.writeToNBT(tag);
        LOTRPacketHandler.networkWrapper.sendTo(new LOTRPacketMiniquestOffer(npc.getEntityId(), tag), (EntityPlayerMP) player);
        info.addOpenOfferPlayer(player);
        return true;
    }

    /** Handles response outside LOTR's tracked-quest lifecycle and reward path. */
    public static boolean handleResponse(LOTREntityQuestInfo info, EntityPlayer player, boolean accepted) {
        LOTREntityNPC npc = npc(info);
        LOTRMiniQuest candidate = info == null || player == null ? null : info.getOfferFor(player);
        if (!(candidate instanceof KOMESerfdomOfferQuest) || npc == null) return false;
        KOMESerfdomOfferQuest offer = (KOMESerfdomOfferQuest) candidate;
        info.removeOpenOfferPlayer(player);
        if (!accepted) { KOMEWorldData data=KOMEWorldData.get(player.worldObj);data.getProgression(player.getUniqueID()).declineSerfdomOffer(npc.getUniqueID().toString(),KOMESerfKnightService.calendarDayNow());data.markDirty();info.clearPlayerSpecificOffer(player); info.sendData((EntityPlayerMP) player); return true; }
        if (!(player instanceof EntityPlayerMP) || !isOfferActive(offer, player, npc) || player.getDistanceSqToEntity(npc) > 64.0D) return true;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        KOMEWorldData data = KOMEWorldData.get(mp.worldObj);
        KOMEPlayerProgression progression = data.getProgression(mp.getUniqueID());
        boolean entered = progression.getCanonicalRank() == KOMEProgressionRank.WANDERER;
        KOMESerfdomMasterService.Result result = KOMESerfdomMasterService.serve(mp, data, npc);
        if (!result.success) return true;
        info.clearPlayerSpecificOffer(mp);
        data.markDirty();
        KOMEProgressionAutoCompleter.syncPlayer(mp, progression);
        KOMEProgressionNpcSpeech.welcomeSerf(mp, npc, entered);
        return true;
    }

    private static boolean isOfferActive(KOMESerfdomOfferQuest offer, EntityPlayer player, LOTREntityNPC npc) {
        return offer != null && player != null && npc != null && !offer.isExpired(opportunityWindow(KOMESerfKnightService.calendarDayNow()))
            && eligiblePlayer(player) && eligibleNpc(player, npc);
    }
    private static boolean eligiblePlayer(EntityPlayer player) {
        if (player == null) return false;
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        if (pledge == null || !pledge.isPlayableAlignmentFaction()) return false;
        KOMEWorldData data = KOMEWorldData.get(player.worldObj);
        KOMEPlayerProgression progression = data.getProgression(player.getUniqueID());
        KOMEProgressionRank rank = progression.getCanonicalRank();
        if (rank == KOMEProgressionRank.WANDERER) return !progression.getSerfKnightProgression().getSerfdomMaster().isSet();
        return rank == KOMEProgressionRank.SERF && !progression.getSerfKnightProgression().getSerfdomMaster().isSet()
            && KOMESerfKnightService.canSelectReplacement(progression.getSerfKnightProgression(), KOMESerfKnightService.calendarDayNow());
    }
    private static boolean eligibleNpc(EntityPlayer player, LOTREntityNPC npc) {
        if (npc == null || !npc.isEntityAlive() || npc.isChild() || !KOMEProgressionNpcRankService.isValidFactionNpc(npc)) return false;
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge != null && pledge == npc.getFaction()
            && KOMEProgressionNpcRankService.effectiveRank(KOMEWorldData.get(npc.worldObj), npc) == KOMEProgressionNpcRank.UNRANKED;
    }
    private static String storyFor(LOTREntityNPC npc) {
        String type = npc.getClass().getSimpleName().toLowerCase();
        if (type.contains("smith")) return "There is more work at my forge than I can manage alone. If you mean to earn your place here, I could use another pair of hands.";
        if (type.contains("farmer") || type.contains("farm")) return "There is always more work than daylight on this holding. I could use someone willing to earn their keep.";
        return "I have work enough for another pair of hands. If you are willing to serve faithfully, I may have a place for you.";
    }
    private static LOTREntityNPC npc(LOTREntityQuestInfo info) {
        if (info == null) return null;
        try {
            if (npcField == null) { npcField = LOTREntityQuestInfo.class.getDeclaredField("theNPC"); npcField.setAccessible(true); }
            return (LOTREntityNPC) npcField.get(info);
        } catch (Exception e) { throw new IllegalStateException("Unable to access LOTR quest NPC", e); }
    }
}
