package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;

/** Canonical relationship reconciliation for real LOTR pledge changes. */
public final class KOMEProgressionRelationshipLifecycle {
    private KOMEProgressionRelationshipLifecycle() {
    }

    /** Only loaded evidence invalidates a reference; chunk absence is never death. */
    public static boolean reconcileLoadedRelationships(KOMEWorldData data,EntityPlayerMP player) {
        if(data==null||player==null||player.worldObj.isRemote)return false;
        KOMEPlayerProgression progression=data.getProgression(player.getUniqueID());
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        boolean masterInvalid=false,liegeInvalid=false;
        for(Object value:player.worldObj.loadedEntityList) {
            if(!(value instanceof lotr.common.entity.npc.LOTREntityNPC))continue;
            lotr.common.entity.npc.LOTREntityNPC npc=(lotr.common.entity.npc.LOTREntityNPC)value;
            String id=npc.getUniqueID().toString();
            boolean valid=npc.isEntityAlive()&&!npc.isChild()&&npc.getFaction()!=null;
            if(id.equals(state.getSerfdomMaster().entityUuid))masterInvalid=!valid
                ||!KOMEProgressionFactionResolver.matches(state.getSerfdomMaster().factionKey,npc.getFaction())
                ||KOMEProgressionNpcRankService.effectiveRank(data,npc)!=KOMEProgressionNpcRank.UNRANKED;
            if(id.equals(state.getLiege().entityUuid))liegeInvalid=!KOMECurrentLiege.validNpc(data,npc,state.getLiege());
        }
        if(!masterInvalid&&!liegeInvalid)return false;
        KOMEProgressionEncounterCleanup.cleanup(player,progression);
        if(masterInvalid)state.handleMasterDeath(false);
        if(liegeInvalid)state.handleLiegeDeath(false);
        KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());data.markDirty();
        return true;
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
            state.hasLiege()
            &&!sameFaction(state.getLiege().factionKey,current);

        boolean legacyMismatch=
            progression.hasPledgedLord()
            &&!sameFaction(progression.getPledgedLordFaction(),current);

        KOMESerfKnightTrialAssignment trial=state.getTrialAssignment();
        boolean trialMismatch=progression.getCanonicalRank()==KOMEProgressionRank.SERF
            &&trial!=null&&!sameFaction(trial.factionKey,current);

        boolean serviceChanged=KOMEKnightCommissionService.reconcileAllegiance(player.worldObj,player.getUniqueID(),progression,current);
        serviceChanged=KOMELordshipTrialService.reconcileAllegiance(player.worldObj,player.getUniqueID(),progression,current)||serviceChanged;

        if(!masterMismatch&&!liegeMismatch&&!legacyMismatch&&!trialMismatch)return serviceChanged;

        if(masterMismatch||liegeMismatch||trialMismatch)
            KOMEProgressionEncounterCleanup.cleanup(player,progression);

        if(progression.getCanonicalRank().order<KOMEProgressionRank.KNIGHT.order) {
            if(masterMismatch) {
                state.leaveSerfdomMaster();
            }
            if(liegeMismatch) {
                state.leaveLiege();
            }
            // Historical completion survives ordinary loss, not a different pledge.
            if(trialMismatch)state.cancelTrial();
        } else {
            if(masterMismatch)state.retireSerfdomMasterAfterPromotion();
            if(liegeMismatch)state.releaseLiegeAfterPromotion();
        }

        if(legacyMismatch)progression.clearPledgedLord();

        KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());
        data.markDirty();
        return true;
    }

    static boolean sameFaction(String first,String second) {
        lotr.common.fac.LOTRFaction faction=KOMEProgressionFactionResolver.resolve(first);
        if(faction!=null)return KOMEProgressionFactionResolver.matches(second,faction);
        String a=KOMEAlliance.normalizeFactionKey(first);
        String b=KOMEAlliance.normalizeFactionKey(second);
        return a.length()>0&&a.equals(b);
    }
}
