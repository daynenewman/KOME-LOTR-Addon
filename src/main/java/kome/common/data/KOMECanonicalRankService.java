package kome.common.data;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/** Controlled mutation boundary for revised player-rank authority. */
public final class KOMECanonicalRankService {
    private KOMECanonicalRankService() { }
    /** Reads committed native allegiance; observation/reconciliation never creates a pledge. */
    public static boolean recognizePledge(EntityPlayerMP player, boolean earned) {
        if(player==null||player.worldObj.isRemote)return false;
        lotr.common.fac.LOTRFaction faction=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
        if(faction==null||!faction.isPlayableAlignmentFaction())return false;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);
        synchronized(data){
            if(!recognizeCommittedPledge(data,player.getUniqueID(),faction.codeName()))return false;
            if(earned)KOMEProgressionAnnouncements.earned(player,KOMEProgressionRank.WANDERER,false);
            KOMEProgressionAutoCompleter.syncPlayer(player,data.getProgression(player.getUniqueID()));
            KOMEProgressionTitles.updatePlayerTitle(player);return true;
        }
    }
    static boolean recognizeCommittedPledge(KOMEWorldData data,UUID owner,String factionKey) {
        lotr.common.fac.LOTRFaction faction=KOMEProgressionFactionResolver.resolve(factionKey);
        if(data==null||owner==null||faction==null||!faction.isPlayableAlignmentFaction())return false;
        KOMEPlayerProgression p=data.getProgression(owner);
        if(p.getCanonicalRank()!=KOMEProgressionRank.WANDERER)return false;
        setCanonicalRank(data,owner,KOMEProgressionRank.SERF);
        p.grant("wanderer.find_serf_lord");KOMEProgressionAutoCompleter.applyUnlocks(p);return true;
    }
    public static boolean setCanonicalRank(KOMEWorldData data, UUID playerId, KOMEProgressionRank rank) {
        if (data == null || playerId == null || rank == null) return false;
        KOMEPlayerProgression progression = data.getProgression(playerId);
        if (progression.getCanonicalRank() == rank) return false;
        progression.setCanonicalRank(rank);
        data.markDirty();
        return true;
    }

    /** Staff structural override: active Serf encounters never survive an incompatible rank change. */
    public static boolean overrideCanonicalRank(
            KOMEWorldData data,
            EntityPlayerMP player,
            KOMEProgressionRank rank) {
        if(data==null||player==null||rank==null)return false;

        UUID playerId=player.getUniqueID();
        KOMEPlayerProgression progression=data.getProgression(playerId);

        if(progression.getCanonicalRank()==rank)return false;

        KOMELordshipTrialService.cancel(player.worldObj,playerId,progression);
        KOMEProgressionEncounterCleanup.cleanup(player,progression);
        progression.getSerfKnightProgression().reset();
        progression.setCanonicalRank(rank);
        reconcileOverrideRankMarkers(progression,rank);

        KOMEProgressionAutoCompleter.recomputeUnlocks(progression);
        KOMEProgressionNpcRoles.syncPlayer(data,playerId);

        data.markDirty();
        return true;
    }
    private static void reconcileOverrideRankMarkers(
            KOMEPlayerProgression progression,
            KOMEProgressionRank rank) {
        setOverrideMarker(
            progression,
            "wanderer.find_serf_lord",
            rank.order>=KOMEProgressionRank.SERF.order);

        setOverrideMarker(
            progression,
            "serf.title_knight",
            rank.order>=KOMEProgressionRank.KNIGHT.order);

        setOverrideMarker(
            progression,
            "knight.title_lord",
            rank.order>=KOMEProgressionRank.LORD.order);

        setOverrideMarker(
            progression,
            "lord.title_prince_king",
            rank.order>=KOMEProgressionRank.PRINCE.order);
    }

    private static void setOverrideMarker(
            KOMEPlayerProgression progression,
            String id,
            boolean completed) {
        if(completed) progression.grant(id);
        else progression.revoke(id);
    }
    /** Retired Master-driven transition. Only confirmed native pledge observation can grant Serf. */
    @Deprecated
    public static boolean enterSerfdom(KOMEWorldData data,UUID playerId){return false;}
}