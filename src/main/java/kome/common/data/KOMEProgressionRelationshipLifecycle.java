package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;

/** Canonical relationship reconciliation for real LOTR pledge changes. */
public final class KOMEProgressionRelationshipLifecycle {
    private KOMEProgressionRelationshipLifecycle() {
    }

    public static boolean reconcilePledgeChange(
            KOMEWorldData data,
            EntityPlayerMP player,
            String currentFaction) {
        if(data==null||player==null)return false;

        KOMEPlayerProgression progression=
            data.getProgression(KOMEReflection.getEntityUUID(player));
        KOMESerfKnightProgression state=
            progression.getSerfKnightProgression();

        String current=KOMEAlliance.normalizeFactionKey(currentFaction);

        boolean masterMismatch=
            state.getSerfdomMaster().isSet()
            &&!sameFaction(state.getSerfdomMaster().factionKey,current);

        boolean liegeMismatch=
            state.getProspectiveLiege().isSet()
            &&!sameFaction(state.getProspectiveLiege().factionKey,current);

        boolean legacyMismatch=
            progression.hasPledgedLord()
            &&!sameFaction(progression.getPledgedLordFaction(),current);

        if(!masterMismatch&&!liegeMismatch&&!legacyMismatch)return false;

        if(masterMismatch||liegeMismatch)
            KOMEProgressionEncounterCleanup.cleanup(player,progression);

        if(progression.getCanonicalRank().order<KOMEProgressionRank.KNIGHT.order) {
            if(masterMismatch) {
                state.leaveSerfdomMaster();
            } else if(liegeMismatch) {
                state.leaveProspectiveLiege();
            }
        } else {
            if(masterMismatch)state.retireSerfdomMasterAfterPromotion();
            if(liegeMismatch)state.releaseProspectiveLiegeAfterPromotion();
        }

        if(legacyMismatch)progression.clearPledgedLord();

        KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());
        data.markDirty();
        return true;
    }

    static boolean sameFaction(String first,String second) {
        String a=KOMEAlliance.normalizeFactionKey(first);
        String b=KOMEAlliance.normalizeFactionKey(second);
        return a.length()>0&&a.equals(b);
    }
}
