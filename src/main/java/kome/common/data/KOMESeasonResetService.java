package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kome.common.command.KOMECommandTroops;
import net.minecraft.world.World;
import static kome.common.data.KOMEConflictContracts.*;

/** Server-thread reset coordinator. The season clock remains a pure phase state machine. */
public final class KOMESeasonResetService {
    private KOMESeasonResetService() { }

    public interface Deployment {
        /** Empty means every required physical effect has been verified; otherwise retry later. */
        String apply(KOMEWorldData data, KOMESeasonResetState.Return entry, String token);
        /** Persist intent before any physical effect. Failure must stop this attempt. */
        void checkpoint(KOMEWorldData data);
    }

    public static boolean active(KOMEWorldData data) {
        return data != null && data.warSeason.phase == KOMEWarSeasonState.Phase.RESET;
    }

    public static String token(KOMEWorldData data, KOMESeasonResetState.Return entry) {
        return data.seasonReset.seasonId + ":" + entry.companyId;
    }

    public static KOMEWarSeasonState.TransitionResult begin(KOMEWorldData data, long now) {
        data.ensureWritable();
        if (active(data)) return KOMEWarSeasonState.TransitionResult.allowed();
        KOMEWarSeasonState.TransitionResult result = data.warSeason.beginReset(now);
        if (result.allowed) data.markDirty();
        return result;
    }

    /** Completion is published only with a durable canonical checkpoint. Restore RESET on failure. */
    public static KOMEWarSeasonState.TransitionResult finish(KOMEWorldData data, long now,
            KOMEDailyCoordinator.Checkpoint checkpoint) {
        net.minecraft.nbt.NBTTagCompound before = new net.minecraft.nbt.NBTTagCompound();
        data.warSeason.writeToNBT(before);
        List<KOMEAuditEntry> audit = new ArrayList<KOMEAuditEntry>(data.centralAudit);
        KOMEWarSeasonState.TransitionResult result = finish(data, now);
        if (!result.allowed) return result;
        try {
            checkpoint.save(data);
            return result;
        } catch (Exception failure) {
            data.warSeason.readFromNBT(before);
            data.centralAudit.clear();
            data.centralAudit.addAll(audit);
            data.markDirty();
            return KOMEWarSeasonState.TransitionResult.denied("Reset completion checkpoint failed; retry /season complete-reset: " + failure.getMessage());
        }
    }

    static KOMEWarSeasonState.TransitionResult finish(KOMEWorldData data, long now) {
        data.ensureWritable();
        if (data.warSeason.phase == KOMEWarSeasonState.Phase.MAINTENANCE
                && data.seasonReset.seasonId + 1 == data.warSeason.seasonId
                && data.seasonReset.complete()) return KOMEWarSeasonState.TransitionResult.allowed();
        if (!active(data) || data.seasonReset.seasonId != data.warSeason.seasonId
                || !data.seasonReset.complete())
            return KOMEWarSeasonState.TransitionResult.denied("Reset is pending; inspect /season status and retry after resolving its reasons.");
        if (!data.armyCompanies.keySet().equals(data.seasonReset.companies.keySet()))
            return KOMEWarSeasonState.TransitionResult.denied("Company registry changed during reset; resolve pending company identity before completion");
        try { for (KOMESeasonResetState.Return entry : data.seasonReset.companies.values()) requireUnchangedCompany(data, entry); }
        catch (IllegalStateException invalid) { return KOMEWarSeasonState.TransitionResult.denied(invalid.getMessage()); }
        KOMEWarSeasonState.TransitionResult result = data.warSeason.completeReset(now);
        if (result.allowed) {
            KOMEAuditService.record(data, now, "SEASON", "RESET_COMPLETE", "system",
                "season:" + data.seasonReset.seasonId, "Ownership and company reset completed", "companies=" + data.seasonReset.companies.size());
            data.markDirty();
        }
        return result;
    }

    public static void process(KOMEWorldData data, World world, long now) {
        process(data, world == null ? 0L : world.getTotalWorldTime(), now,
            new KOMESeasonResetDeployment(world));
    }

    public static void process(KOMEWorldData data, long worldTime, long now, Deployment deployment) {
        if (!active(data)) return;
        data.ensureWritable();
        if (data.seasonReset.seasonId != data.warSeason.seasonId) {
            data.seasonReset = new KOMESeasonResetState();
            data.seasonReset.seasonId = data.warSeason.seasonId;
            data.markDirty();
        }
        KOMESeasonResetState state = data.seasonReset;
        if (state.complete() && "READY_TO_COMPLETE".equals(data.warSeason.resetStatus)) return;
        try {
            state.failure = "";
            if (!state.ownershipReset) {
                data.resetConquestOwnershipToDefaults(worldTime);
                state.ownershipReset = true;
                KOMEAuditService.record(data, now, "SEASON", "RESET_OWNERSHIP", "system",
                    "season:" + state.seasonId, "Restored canonical ownership defaults", "");
                data.markDirty();
            }
            if (!state.prepared) prepare(data);
            // Persist receipts for every planned unit before any return can load a source chunk.
            // A crash after an earlier company must not admit stale copies of later virtual units.
            for (KOMESeasonResetState.Return planned : state.companies.values()) {
                for (UUID id : planned.units) {
                    if (!planned.returnRequired && !planned.virtualUnits.contains(id)) continue;
                    KOMEHiredUnitRecord unit = data.hiredUnits.get(id);
                    if (unit == null) throw new IllegalStateException("Reset unit identity is unavailable: " + id);
                    unit.seasonReturnToken = token(data, planned);
                    unit.seasonReturnVirtual |= planned.virtualUnits.contains(id);
                }
            }
            // End the canonical record, including its route handoff; historical commitments and
            // siege checkpoints remain diagnostic history. No future siege outcome is inferred.
            for (KOMEConflictRecord conflict : data.getConflictService().records().values()) {
                if (!conflict.isActive()) continue;
                KOMEConflictService.EndResult ended = data.getConflictService().endWithMovementHandoff(data,
                    conflict.getTileId(), ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()),
                    new Context(now, "season-reset", "Season " + state.seasonId + " reset"),
                    KOMEConflictService.EndSource.LIFECYCLE);
                if (!ended.isSuccess()) throw new IllegalStateException("Conflict " + conflict.getConflictId()
                    + " needs repair before reset: " + ended.conflictResult.reason);
            }
            for (KOMESeasonResetState.Return entry : state.companies.values()) {
                if (entry.complete) continue;
                KOMEArmyCompany company = requireUnchangedCompany(data, entry);
                if (!entry.cleared) {
                    KOMECommandTroops.clearSeasonResetMovement(data, company, now);
                    KOMEWartimeStewardshipService.clearSeasonResetTransients(data, company);
                    entry.cleared = true;
                    data.markDirty();
                }
                if (entry.returnRequired) {
                    KOMEFactionCapitalRecord current = KOMEFactionCapitalService.getCapital(data, entry.nativeFaction);
                    if (current == null) { entry.reason = "Configure the authoritative capital for " + entry.nativeFaction; continue; }
                    if (entry.capital == null) {
                        entry.capital = current;
                        entry.destination = current.getCapitalTileId();
                    } else if (!sameDeployment(entry.capital, current)) {
                        entry.reason = "Capital authority changed during this return; restore the journaled capital before retrying";
                        continue;
                    }
                }
                for (UUID id : entry.units) {
                    if (!entry.returnRequired && !entry.virtualUnits.contains(id)) continue;
                    KOMEHiredUnitRecord unit = data.hiredUnits.get(id);
                    unit.seasonReturnToken = token(data, entry);
                    unit.seasonReturnVirtual |= entry.virtualUnits.contains(id);
                }
                data.markDirty();
                deployment.checkpoint(data);
                String pending = deployment.apply(data, entry, token(data, entry));
                if (pending != null && !pending.isEmpty()) { entry.reason = pending; continue; }
                // One publication boundary, only after every physical unit was verified.
                company.currentTile = entry.destination;
                company.updatedAtMillis = now;
                for (UUID id : entry.units) data.hiredUnits.get(id).currentTile = entry.destination;
                entry.complete = true;
                entry.reason = entry.returnRequired ? "Outside native territory after ownership reset" : "Already inside valid native territory";
                if (entry.returnRequired) KOMEAuditService.record(data, now, "SEASON", "COMPANY_RETURN", "system",
                    entry.companyId, entry.reason, "season=" + state.seasonId + ";origin=" + entry.origin
                        + ";destination=" + entry.destination + ";native=" + entry.nativeFaction);
                data.markDirty();
                deployment.checkpoint(data);
            }
            data.warSeason.resetStatus = state.complete() ? "READY_TO_COMPLETE" : "PENDING_COMPANIES";
            data.markDirty();
            deployment.checkpoint(data);
        } catch (RuntimeException pending) {
            state.failure = pending.getMessage() == null ? pending.getClass().getSimpleName() : pending.getMessage();
        }
        data.warSeason.resetStatus = state.complete() ? "READY_TO_COMPLETE" : "PENDING_COMPANIES";
        data.markDirty();
    }

    private static boolean sameDeployment(KOMEFactionCapitalRecord first, KOMEFactionCapitalRecord second) {
        return first.getFactionId().equals(second.getFactionId())
            && first.getCapitalTileId().equals(second.getCapitalTileId())
            && first.getDeploymentDimensionId() == second.getDeploymentDimensionId()
            && Double.compare(first.getDeploymentX(), second.getDeploymentX()) == 0
            && Double.compare(first.getDeploymentY(), second.getDeploymentY()) == 0
            && Double.compare(first.getDeploymentZ(), second.getDeploymentZ()) == 0;
    }

    private static void prepare(KOMEWorldData data) {
        Map<String, KOMESeasonResetState.Return> prepared = new LinkedHashMap<String, KOMESeasonResetState.Return>();
        List<String> ids = new ArrayList<String>(data.armyCompanies.keySet());
        Collections.sort(ids);
        for (String id : ids) {
            KOMEArmyCompany company = data.armyCompanies.get(id);
            KOMECompanyCoherenceService.Assessment assessment = KOMECompanyCoherenceService.INSTANCE.assess(data, company);
            if (assessment.status == KOMECompanyCoherenceService.Status.INCOHERENT)
                throw new IllegalStateException("Company " + id + " needs identity/location repair: " + assessment.issues.get(0).detail);
            String nativeFaction = KOMEWartimeStewardshipService.nativeFaction(company);
            if (!KOMEAlliance.allFactionKeys().contains(nativeFaction))
                throw new IllegalStateException("Company " + id + " has an unrecognized native faction");
            KOMEConquestTile tile = data.conquestTiles.get(company.currentTile);
            if (tile == null || !KOMEConquestTileDefaults.getKnownTileIds().contains(company.currentTile))
                throw new IllegalStateException("Company " + id + " has no canonical current tile; repair its location");
            KOMESeasonResetState.Return entry = new KOMESeasonResetState.Return();
            entry.companyId = id;
            entry.owner = company.owner;
            entry.nativeFaction = nativeFaction;
            entry.origin = company.currentTile;
            entry.returnRequired = !nativeFaction.equals(tile.projectRulingFaction());
            entry.destination = entry.returnRequired ? "" : entry.origin;
            entry.units.addAll(company.units);
            for (UUID unit : company.units) if (data.isVirtualMovingHiredUnit(data.hiredUnits.get(unit))) entry.virtualUnits.add(unit);
            prepared.put(id, entry);
        }
        data.seasonReset.companies.putAll(prepared);
        data.seasonReset.prepared = true;
        data.markDirty();
    }

    private static KOMEArmyCompany requireUnchangedCompany(KOMEWorldData data, KOMESeasonResetState.Return entry) {
        KOMEArmyCompany company = data.armyCompanies.get(entry.companyId);
        if (company == null || !entry.owner.equals(company.owner)
                || !entry.nativeFaction.equals(KOMEWartimeStewardshipService.nativeFaction(company))
                || !entry.units.equals(new LinkedHashSet<UUID>(company.units)))
            throw new IllegalStateException("Reset company membership/identity changed: " + entry.companyId);
        for (UUID id : entry.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(id);
            if (record == null || !entry.companyId.equals(record.companyId) || !entry.owner.equals(record.owner))
                throw new IllegalStateException("Reset unit identity is unavailable: " + id);
        }
        return company;
    }

    public static KOMESeasonResetState.Return entryFor(KOMEWorldData data, UUID unit) {
        for (KOMESeasonResetState.Return entry : data.seasonReset.companies.values())
            if (entry.units.contains(unit)) return entry;
        return null;
    }

    public static List<String> status(KOMEWorldData data) {
        List<String> lines = new ArrayList<String>();
        KOMESeasonResetState state = data.seasonReset;
        if (state.seasonId == 0) return lines;
        int pending = 0;
        for (KOMESeasonResetState.Return entry : state.companies.values()) if (!entry.complete) pending++;
        lines.add("Company reset season " + state.seasonId + ": " + pending + " pending / " + state.companies.size()
            + "; ownership reset=" + state.ownershipReset);
        if (!state.failure.isEmpty()) lines.add("Reset pending: " + state.failure);
        for (KOMESeasonResetState.Return entry : state.companies.values()) {
            if (!entry.complete && lines.size() < 10) lines.add(entry.companyId + ": " + entry.origin + " -> "
                + (entry.destination.isEmpty() ? "capital unavailable" : entry.destination) + "; " + entry.reason);
        }
        return lines;
    }
}
