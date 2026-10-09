package kome.common.data;

import java.util.*;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;

/** Read-only decisions from canonical strategic authority; no entity location or player presence. */
public final class KOMECampaignHealingPolicy {
    private KOMECampaignHealingPolicy() { }

    /** Future battle lifecycles can supply their authoritative participation here. No siege behavior is inferred. */
    public interface BattleParticipationAuthority {
        boolean participates(KOMEWorldData data, KOMEHiredUnitRecord unit, KOMEConflictRecord conflict);
    }
    private static final BattleParticipationAuthority CURRENT_BATTLES = KOMECampaignHealingPolicy::ordinaryParticipation;

    public static boolean activelyParticipatingInBattle(KOMEWorldData data, KOMEHiredUnitRecord unit) {
        return activelyParticipatingInBattle(data,unit,CURRENT_BATTLES);
    }
    public static boolean activelyParticipatingInBattle(KOMEWorldData data, KOMEHiredUnitRecord unit, BattleParticipationAuthority authority) {
        if (!KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(data,unit)) return false;
        KOMEConflictRecord conflict = data.getConflictService().get(unit.currentTile);
        return conflict != null && conflict.isActive() && authority.participates(data,unit,conflict);
    }
    private static boolean ordinaryParticipation(KOMEWorldData data, KOMEHiredUnitRecord unit, KOMEConflictRecord conflict) {
        if (conflict.getState() != KOMEConflictRecord.State.ORDINARY
                || conflict.getCombatEpisode().state != KOMEConflictRecord.EpisodeState.ACTIVE) return false;
        Target rider = data.getOrdinaryBattleRecovery().targetForPhysical(unit.entity);
        Episode episode = data.getOrdinaryBattleRecovery().get(conflict.getConflictId(),conflict.getCombatEpisode().episodeId);
        Participant participant = rider == null || episode == null ? null : episode.participants.get(rider.id);
        if (participant != null) return episode.endedAt == null && participant.disposition == Disposition.PARTICIPATING;
        // Legacy/early enrollment can still prove current participation through exact strategic commitment.
        KOMEArmyCompany company = data.armyCompanies.get(unit.companyId);
        KOMEConflictRecord.Commitment commitment = conflict.getCommitments().get(unit.companyId);
        if (company == null || commitment == null || !company.units.contains(unit.entity)) return false;
        KOMEConflictContracts.DetachmentResolution resolution = KOMEConflictService.resolveProductionDetachment(data,unit.companyId);
        KOMEConflictRecord.FactionParticipation faction = conflict.getFactionParticipation().get(resolution.factionId);
        return resolution.status == KOMEConflictContracts.ReferenceStatus.RESOLVED
            && resolution.classification == KOMEHiredUnitClass.CAMPAIGN && conflict.getTileId().equals(resolution.strategicTileId)
            && faction != null && faction.isActive();
    }

    public static boolean blocksNormalHealing(Entity entity) {
        KOMEConfigRegistry.BattleSettings settings = KOMEConfigRegistry.battle();
        if (!enabled(settings) || entity == null || entity.worldObj == null || entity.worldObj.isRemote) return false;
        return blocksNormalHealing(KOMEWorldData.get(entity.worldObj),entity.getUniqueID(),settings);
    }
    public static boolean blocksNormalHealing(KOMEWorldData data, UUID physical) {
        return blocksNormalHealing(data,physical,KOMEConfigRegistry.battle());
    }
    private static boolean enabled(KOMEConfigRegistry.BattleSettings s) {
        return s.isNormalHealingDisabledInBattle() || s.isNormalHealingDisabledDuringCampaignCommitment();
    }
    private static boolean blockedUnit(KOMEWorldData data, KOMEHiredUnitRecord unit, KOMEConfigRegistry.BattleSettings s) {
        return KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(data,unit)
            && (s.isNormalHealingDisabledDuringCampaignCommitment()
                || s.isNormalHealingDisabledInBattle() && activelyParticipatingInBattle(data,unit));
    }
    private static boolean blocksNormalHealing(KOMEWorldData data, UUID physical, KOMEConfigRegistry.BattleSettings settings) {
        if (!enabled(settings) || data == null || physical == null) return false;
        KOMEHiredUnitRecord rider = data.hiredUnits.get(physical);
        if (rider != null) return blockedUnit(data,rider,settings);
        Target target = data.getOrdinaryBattleRecovery().targetForPhysical(physical);
        if (target != null && (target.component != Component.MOUNT || target.terminal || !target.physicalId.equals(physical))) return false;
        Collection<KOMEHiredUnitRecord> candidates = target != null && data.recoveryStartup != null && data.recoveryStartup.runtime != null
            ? data.recoveryStartup.runtime.owners(target.id) : data.hiredUnits.values();
        for (KOMEHiredUnitRecord unit : candidates) {
            if (!blockedUnit(data,unit,settings)) continue;
            NBTTagCompound saved = KOMECampaignHealth.mountHealth(unit.survivingHealth,physical);
            if (!KOMECampaignHealth.versioned(saved)) continue;
            KOMECampaignHealthProjection mount = KOMECampaignHealthProjection.read(saved);
            if (mount.terminal) continue;
            if (verifiedMount(data,unit,mount)) return true;
        }
        return false;
    }
    private static boolean verifiedMount(KOMEWorldData data, KOMEHiredUnitRecord unit, KOMECampaignHealthProjection mount) {
        if (mount.component != Component.MOUNT) return false;
        Target target = data.getOrdinaryBattleRecovery().target(mount.target);
        Target rider = data.getOrdinaryBattleRecovery().targetForPhysical(unit.entity);
        boolean capturedRider = false;
        if (rider != null) {
            for (Episode episode : data.getOrdinaryBattleRecovery().episodes().values()) {
                Participant participant = episode.participants.get(rider.id);
                if (participant == null) continue;
                capturedRider = true;
                if (target != null && target.physicalId.equals(mount.alias) && !target.terminal
                        && participant.mountIds.contains(mount.target)) return true;
            }
        }
        // A newer physical riding snapshot cannot replace already captured logical mount association.
        if (capturedRider) return false;
        // Before any battle history exists, canonical deployment/movement geometry can bind the mount.
        return snapshotMount(unit.stationedEntityData,mount) || snapshotMount(unit.movingEntityData,mount);
    }
    private static boolean snapshotMount(NBTTagCompound snapshot, KOMECampaignHealthProjection mount) {
        if (snapshot == null || !snapshot.hasKey("Riding",10)) return false;
        NBTTagCompound riding = snapshot.getCompoundTag("Riding");
        if (!riding.hasKey("UUIDMost",4) || !riding.hasKey("UUIDLeast",4)) return false;
        UUID alias = new UUID(riding.getLong("UUIDMost"),riding.getLong("UUIDLeast"));
        if (!alias.equals(mount.alias)) return false;
        if (!riding.hasKey(KOMECampaignHealthProjection.ENTITY_TAG)) return true;
        KOMECampaignHealthProjection projection = KOMECampaignHealthProjection.read(riding.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
        return projection.component == Component.MOUNT && projection.alias.equals(alias) && projection.target.equals(mount.target);
    }
}
