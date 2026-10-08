package kome.common.data;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.world.World;

/** Targeted read-only diagnostics. Derived rates use the normal authoritative aggregation. */
public final class KOMEAdminDiagnostics {
    public static final int MAX_LINES = 20, MAX_LINE_LENGTH = 512;
    private KOMEAdminDiagnostics() { }

    public static List<String> inspect(KOMEWorldData data, String domain, String subject,
            String other, World capitalWorld) {
        if (data == null) throw new IllegalArgumentException("World authority is unavailable");
        List<String> lines = new ArrayList<String>();
        if ("population".equals(domain)) {
            String faction = faction(subject);
            lines.add("Available centi-population=" + KOMEPopulationService.getAvailablePopulationCenti(data, faction));
            BigInteger rate = KOMEPopulationRateService.getExactDailyPopulationRates(data,
                KOMEConfigRegistry.population()).get(faction);
            lines.add("Derived daily rate units=" + (rate == null ? BigInteger.ZERO : rate));
            lines.add("Rate is derived from developed Builds and tile control; no stored rate to repair.");
            lines.add("Payout failure=" + data.populationPayoutLastFailure);
            int omitted = 0;
            for (KOMEPopulationRateContribution row : KOMEPopulationService.getPopulationRateContributions(data)) {
                if (!faction.equals(row.receivingFaction) && !faction.equals(row.populationFaction)) continue;
                if (lines.size() < MAX_LINES - 1) lines.add(row.buildId + ": " + row.status
                    + ";recipient=" + row.receivingFaction + ";rate=" + row.currentRateUnits);
                else omitted++;
            }
            if (omitted > 0) lines.add("Omitted " + omitted + " contribution rows; inspect individual Builds.");
        } else if ("ruler".equals(domain)) {
            String faction = faction(subject);
            UUID ruler = KOMERulerService.getRuler(data, faction);
            lines.add("Ruler=" + ruler + ";cachedName=" + KOMERulerService.getRulerName(data, faction));
            lines.add(ruler == null ? "No ruler is valid kingless state; assign explicitly if intended."
                : "Cached names can be repaired only using the matching online player's identity.");
        } else if ("capital".equals(domain)) {
            String faction = faction(subject);
            KOMEFactionCapitalRecord r = KOMEFactionCapitalService.getCapital(data, faction);
            lines.add(r == null ? "Capital missing; no safe automatic default inference."
                : "tile=" + r.getCapitalTileId() + ";anchor=" + r.getDeploymentDimensionId()
                    + ":" + r.getDeploymentX() + "," + r.getDeploymentY() + "," + r.getDeploymentZ());
            lines.add(KOMEFactionCapitalService.inspectionReadiness(data, faction, capitalWorld));
            lines.add(KOMEFactionCapitalService.relocationBlock(data, faction).reason);
            lines.add("Use /kome capital relocate <faction> here for explicit validated relocation.");
        } else if ("diplomacy".equals(domain)) {
            String a = faction(subject), b = faction(other);
            String key = KOMEDiplomacyRecord.pairKey(a, b);
            KOMEDiplomacyRecord r = data.canonicalDiplomacyRecords.get(key);
            KOMEDiplomacyRelation current = KOMEDiplomacyService.getRelation(data, a, b);
            lines.add("LOTR effective relation=" + current.key);
            lines.add(r == null ? "No pending workflow record; this is valid."
                : "pending=" + r.pendingTarget + ";requester=" + r.requestingFaction
                    + ";receiver=" + r.receivingFaction);
            lines.add(r != null && r.pendingTarget != null && r.pendingTarget.rank() <= current.rank()
                ? "Obsolete pending request can be previewed for removal."
                : "Effective relations are changed through /alliance; no repair inferred.");
        } else if ("ownership".equals(domain)) {
            KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(subject));
            if (tile == null) throw new IllegalArgumentException("Unknown tile; no ownership invented");
            lines.add("current=" + tile.currentRulingFaction + ";legacyAlias=" + tile.ownerFaction
                + ";effective=" + tile.projectRulingFaction());
            lines.add("default=" + tile.defaultRulingFaction + ";pending="
                + tile.pendingTransferFromFaction + " -> " + tile.pendingTransferToFaction);
            lines.add("Only an empty legacy alias with a supported current owner is repairable; conflicts require review.");
        } else if ("waypoint".equals(domain)) {
            UUID id = UUID.fromString(subject);
            KOMEPublicWaypoint r = data.publicWaypoints.get(id);
            if (r == null) {
                lines.add("No approved destination for " + id + "; no approval inferred.");
                List<String> reasons = data.publicWaypoints.quarantineReasons(id, MAX_LINES - 2);
                lines.add(reasons.isEmpty() ? "No matching quarantine evidence; use /kome waypoint list or history <uuid>."
                    : "Quarantined evidence is retained; manual review required. Reason sample:");
                lines.addAll(reasons);
                return bounded(lines);
            }
            lines.add("name=" + r.name + ";tile=" + r.tileId + ";source=" + r.source + ";level=" + r.level);
            String invalid = KOMEPublicWaypointRegistry.invalidAssociation(data, r);
            lines.add(invalid == null ? "Association valid; no repair needed." : "Association unavailable: " + invalid);
            KOMEPublicWaypointRegistry.View view = data.publicWaypoints.view(data, r.id);
            if (view != null) lines.add("defaultOwner=" + view.defaultOwner + ";currentOwner=" + view.currentOwner);
            lines.add("Only public links with a uniquely resolved, unoccupied tile can be repaired; quarantine is preserved.");
        } else if ("governance".equals(domain)) {
            UUID player = UUID.fromString(subject);
            lines.add("Native faction=" + data.getPlayerFactionKey(player) + "; season=" + data.warSeason.seasonId);
            for (KOMEPlayerGovernance record : KOMEGovernanceService.records(data, player)) {
                if (lines.size() >= MAX_LINES - 2) { lines.add("Additional history omitted; use paged /kome audit list."); break; }
                KOMEWar war = data.wars.get(record.warId);
                lines.add(record.warId + ";season=" + record.season + ";state=" + record.state + ";origin=" + record.origin
                    + ";host=" + record.host + ";revision=" + record.revision);
                if (record.season == data.warSeason.seasonId && war != null)
                    lines.add("Permission: " + permission(KOMEGovernanceService.participation(data, player, war,
                        record.host.isEmpty() ? record.origin : record.host)));
            }
            lines.add("Player chooses /governance submit or exile; invalid hosts reconcile to Submitted without asset cleanup.");
        } else if ("muster".equals(domain)) {
            String faction = faction(subject);
            List<KOMEMusterRecord> records = KOMEMusterService.records(data, faction);
            if (records.isEmpty()) lines.add("No reserve call recorded for " + faction);
            for (KOMEMusterRecord record : records) {
                if (lines.size() >= MAX_LINES - 4) { lines.add("Additional seasons omitted; use paged /kome audit list."); break; }
                lines.add(record.key() + ";status=" + record.getStatus() + ";due=" + record.dueAtMillis + ";seed=" + record.seed);
                lines.add("budget=" + record.budgetUnits + ";spent=" + record.spentUnits + ";receipt=" + record.getArrivalReceipt());
                lines.add("pending=" + record.getPendingReason() + ";capital=" + KOMEMusterArrivalAuthority.INSTANCE.capitalState(data, record.capital));
                if (record.getStatus() != KOMEMusterRecord.Status.ARRIVED)
                    lines.add("Deployment: " + KOMEMusterArrivalAuthority.INSTANCE.deliveryBlockReason(data, record));
            }
            lines.add("No force is inferred from a roster. Uncertain delivery requires verified entity/save reconciliation; no automatic retry.");
        } else if ("daily".equals(domain)) {
            if (!"status".equals(subject)) throw new IllegalArgumentException("/kome diagnostics daily status");
            java.time.Instant now = java.time.Instant.now();
            KOMEDailyBoundary schedule = KOMEDailyBoundary.from(KOMEConfigRegistry.dailyBatch());
            KOMEDailyJournal journal = data.dailyJournal;
            lines.add("Now=" + now + ";cadence=" + schedule.signature());
            lines.add("Next=" + schedule.nextBoundary(schedule.latestBoundaryAtOrBefore(now)) + ";lastComplete=" + journal.lastComplete());
            lines.add("Status=" + journal.status() + ";boundary=" + journal.boundary() + ";nextStage=" + journal.stage());
            lines.add("Reason=" + journal.reason());
            String unavailable = KOMEDailyCoordinator.unavailable(data, now.toEpochMilli());
            lines.add(unavailable.isEmpty() ? "Required gameplay stages currently available or inapplicable" : unavailable);
            for (KOMEDailyJournal.Stage stage : KOMEDailyJournal.Stage.values())
                lines.add(stage + "=" + (stage.ordinal() < journal.completedStages() ? "canonical receipt recorded" : "not completed in current journal"));
            lines.add("Blocked batches retain legacy processing. No manual completion or replay repair is authorized.");
        } else if ("company".equals(domain)) {
            KOMEArmyCompany company = data.armyCompanies.get(subject);
            if (company == null) throw new IllegalArgumentException("Unknown company; no location inferred");
            lines.add(company.id + ";faction=" + company.faction + ";native=" + company.nativeFaction + ";owner=" + company.owner);
            lines.add("tile=" + company.currentTile + ";status=" + company.status + ";order=" + company.movementOrderId);
            lines.add("controller=" + company.temporaryController + ";authority=" + company.controllerAuthority + ";cleanup=" + company.withdrawalState);
            int missing = 0; for (UUID id : company.units) if (!data.hiredUnits.containsKey(id)) missing++;
            lines.add("members=" + company.units.size() + ";missingCanonicalUnits=" + missing);
            lines.add("Movement credit=" + company.movementAllowance + ";entitlement=" + company.getTilesPerDay()
                + ";initialized=" + company.movementAllowanceInitialized);
            lines.add("Movement boundary=" + company.movementBoundaryMillis + ";schedule=" + company.movementBoundarySchedule);
            KOMECompanyCoherenceService.Assessment coherence = KOMECompanyCoherenceService.INSTANCE.assess(data, company);
            lines.add("Coherence=" + coherence.status + ";phase=" + coherence.movementPhase
                + ";route=" + coherence.routeOrderStatus);
            lines.add("Cached physical observations: confirmed=" + coherence.physicallyConfirmedMembers
                + ";unknown=" + coherence.physicallyUnknownMembers + ";contradictory=" + coherence.physicallyContradictoryMembers
                + ";no world or chunk is loaded for inspection");
            int shown = 0;
            for (KOMECompanyCoherenceService.Issue issue : coherence.issues) {
                if (lines.size() >= MAX_LINES - 3) break;
                lines.add(issue.code + ": " + issue.detail); shown++;
            }
            if (shown < coherence.issues.size()) lines.add("Additional coherence issues omitted=" + (coherence.issues.size() - shown));
            lines.add("Use existing movement/withdrawal and stewardship services. Missing entities are not proof of death; no teleport/repair inferred.");
        } else if ("gate".equals(domain)) {
            KOMEPlayerBuild build = data.builds.get(subject);
            if (build == null) throw new IllegalArgumentException("Unknown Build; use /build inspect <id>");
            lines.add("Build=" + build.id + ";tile=" + build.tileId + ";type=" + build.type);
            for (KOMEDefensiveGateRecord record : build.getDefensiveGateRecords()) {
                if (lines.size() >= MAX_LINES - 2) { lines.add("Additional links omitted; inspect in gate management."); break; }
                lines.add(record.getId() + ";uuid=" + record.getGateUuid() + ";dimension=" + record.getGateDimension()
                    + ";controller=" + record.getControllerX() + "," + record.getControllerY() + "," + record.getControllerZ()
                    + ";revision=" + record.getCapturedStructureRevision() + ";bound=" + record.hasPhysicalBinding());
            }
            lines.add("Physical state=UNINSPECTED. Use existing gate management inspection/relink with a loaded controller; no dimension is loaded here.");
        } else throw new IllegalArgumentException("Unknown diagnostic domain; use population, ruler, capital, diplomacy, ownership, waypoint, governance, muster, daily, company or gate");
        if (data.isWriteBlocked()) lines.add("World is write-blocked; repairs are disabled.");
        return bounded(lines);
    }

    public static String faction(String value) {
        String key = KOMEAlliance.normalizeFactionKey(value);
        if (!KOMEAlliance.allFactionKeys().contains(key))
            throw new IllegalArgumentException("Unknown supported faction: " + value);
        return key;
    }

    private static String permission(KOMEGovernanceService.Decision decision) {
        return decision.allowed ? "allowed under " + decision.faction : decision.reason;
    }

    public static String line(String value) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replace('\u00a7', '?');
        return clean.length() <= MAX_LINE_LENGTH ? clean : clean.substring(0, MAX_LINE_LENGTH - 3) + "...";
    }

    public static List<String> bounded(List<String> source) {
        List<String> result = new ArrayList<String>();
        for (String value : source) {
            if (result.size() == MAX_LINES) break;
            result.add(line(value));
        }
        return Collections.unmodifiableList(result);
    }
}
