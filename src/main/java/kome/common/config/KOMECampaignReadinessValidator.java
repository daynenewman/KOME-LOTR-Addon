package kome.common.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import kome.common.siege.KOMESiegeReadinessEvaluator.Diagnostic;
import kome.common.siege.KOMESiegeReadinessEvaluator.Report;

/**
 * Read-only campaign reference validation. Pending campaign systems supply this
 * service through {@link CampaignReadinessData}; it owns no world persistence.
 */
public final class KOMECampaignReadinessValidator {
    public ReadinessResult validate(CampaignReadinessData data) {
        List<ValidationFailure> failures = new ArrayList<ValidationFailure>();
        List<ValidationFailure> warnings = new ArrayList<ValidationFailure>();
        Map<String, Report> reports = new TreeMap<String, Report>();
        for (Report report : data.getSiegeComplexReadinessReports()) {
            if (reports.put(report.getComplexId(), report) != null) {
                throw new IllegalArgumentException("Duplicate Siege Complex readiness report: " + report.getComplexId());
            }
        }
        validateCapitals(data, failures);
        validateDefenses(data, reports, failures, warnings);
        validateMapAssets(data, failures);
        Collections.sort(failures, FAILURE_ORDER);
        Collections.sort(warnings, FAILURE_ORDER);
        return new ReadinessResult(failures, warnings, new ArrayList<Report>(reports.values()));
    }

    private void validateCapitals(CampaignReadinessData data,
            List<ValidationFailure> failures) {
        for (String faction : data.getPlayableFactions()) {
            String capitalTileId = data.getCapitalTileId(faction);
            if (blank(capitalTileId)) {
                failure(failures, "capital", faction, "capitalTileId", capitalTileId,
                        "playable faction has no capital reference");
                continue;
            }
            if (data.findStrategicTile(capitalTileId) == null) {
                failure(failures, "capital", faction, "capitalTileId", capitalTileId,
                        "capital reference does not resolve to a strategic tile");
            }
        }
    }

    private void validateDefenses(CampaignReadinessData data, Map<String, Report> reports,
            List<ValidationFailure> failures, List<ValidationFailure> warnings) {
        // Consume each scoped report once, irrespective of how many Builds support that complex.
        for (Report report : reports.values()) {
            for (Diagnostic diagnostic : report.getBlockingDiagnostics()) addSiegeDiagnostic(failures, diagnostic);
            for (Diagnostic diagnostic : report.getWarnings()) addSiegeDiagnostic(warnings, diagnostic);
        }
        for (DefensiveBuild build : data.getDefensiveBuilds()) {
            // Unassigned authoring is legitimate unless this particular campaign Build requires a tactical entry.
            if (blank(build.getSiegeComplexId()) && !build.requiresEntry() && blank(build.getEntryConnectionId())) continue;
            Report report = reports.get(build.getSiegeComplexId() == null ? ""
                : build.getSiegeComplexId().trim().toUpperCase(Locale.ROOT));
            if (report == null) {
                failure(failures, "defensiveBuild", build.getId(), "siegeComplexId",
                        build.getSiegeComplexId(), "defensive build references no known Siege Complex");
                continue;
            }
            if (build.requiresEntry() && blank(build.getEntryConnectionId())) {
                failure(failures, "defensiveBuild", build.getId(), "entryConnectionId",
                        build.getEntryConnectionId(), "defensive build requires an entry connection");
            } else if (!blank(build.getEntryConnectionId())
                    && !report.getAuthoredConnectionIds().contains(build.getEntryConnectionId().trim())) {
                failure(failures, "defensiveBuild", build.getId(), "entryConnectionId",
                        build.getEntryConnectionId(), "entry connection does not belong to Siege Complex");
            }
        }
    }

    private static void addSiegeDiagnostic(List<ValidationFailure> diagnostics, Diagnostic diagnostic) {
        List<String> subjects = new ArrayList<String>();
        if (!blank(diagnostic.getSegmentId())) subjects.add("segment=" + diagnostic.getSegmentId());
        if (!blank(diagnostic.getConnectionId())) subjects.add("connection=" + diagnostic.getConnectionId());
        if (!blank(diagnostic.getBuildId())) subjects.add("build=" + diagnostic.getBuildId());
        if (!blank(diagnostic.getGateRecordId())) subjects.add("gateRecord=" + diagnostic.getGateRecordId());
        subjects.addAll(diagnostic.getSubjectIds());
        failure(diagnostics, "siegeComplex", diagnostic.getComplexId(), diagnostic.getCode(),
            subjects.toString(), diagnostic.getMessage());
    }

    private void validateMapAssets(CampaignReadinessData data,
            List<ValidationFailure> failures) {
        for (String assetId : data.getRequiredMapAssetIds()) {
            if (!data.hasMapAsset(assetId)) {
                failure(failures, "mapAsset", "", "assetId", assetId,
                        "required strategic map asset does not resolve");
            }
        }
    }

    private static void failure(List<ValidationFailure> failures, String category,
            String owner, String reference, String value, String reason) {
        failures.add(new ValidationFailure(category, owner, reference, value, reason));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static final Comparator<ValidationFailure> FAILURE_ORDER =
            new Comparator<ValidationFailure>() {
                @Override
                public int compare(ValidationFailure first, ValidationFailure second) {
                    int result = first.category.compareTo(second.category);
                    if (result == 0) result = first.ownerId.compareTo(second.ownerId);
                    if (result == 0) result = first.reference.compareTo(second.reference);
                    if (result == 0) result = first.value.compareTo(second.value);
                    if (result == 0) result = first.reason.compareTo(second.reason);
                    return result;
                }
            };

    public interface CampaignReadinessData {
        Collection<String> getPlayableFactions();
        String getCapitalTileId(String factionId);
        StrategicTile findStrategicTile(String tileId);
        Collection<DefensiveBuild> getDefensiveBuilds();
        /** Scoped reports supplied once, e.g. by KOMESiegeReadinessResolver.evaluateAll(worldData).values(). */
        Collection<Report> getSiegeComplexReadinessReports();
        Collection<String> getRequiredMapAssetIds();
        boolean hasMapAsset(String assetId);
    }

    /**
     * Resolution marker only. KOM-39's authoritative CapitalRecord/capital-state
     * system will validate designated faction association and legal deployment
     * locations. This validator must not infer either fact from mutable conquest
     * ownership.
     */
    public interface StrategicTile {
    }

    public interface DefensiveBuild {
        String getId();
        String getSiegeComplexId();
        boolean requiresEntry();
        String getEntryConnectionId();
    }

    public static final class ReadinessResult {
        private final List<ValidationFailure> failures;
        private final List<ValidationFailure> warnings;
        private final List<Report> siegeComplexReports;

        private ReadinessResult(List<ValidationFailure> failures, List<ValidationFailure> warnings, List<Report> reports) {
            this.failures = Collections.unmodifiableList(
                    new ArrayList<ValidationFailure>(failures));
            this.warnings = Collections.unmodifiableList(new ArrayList<ValidationFailure>(warnings));
            siegeComplexReports = Collections.unmodifiableList(new ArrayList<Report>(reports));
        }

        public boolean isReady() { return failures.isEmpty(); }
        public List<ValidationFailure> getFailures() { return failures; }
        public List<ValidationFailure> getWarnings() { return warnings; }
        public List<Report> getSiegeComplexReports() { return siegeComplexReports; }
    }

    public static final class ValidationFailure {
        private final String category;
        private final String ownerId;
        private final String reference;
        private final String value;
        private final String reason;

        private ValidationFailure(String category, String ownerId, String reference,
                String value, String reason) {
            this.category = category == null ? "" : category;
            this.ownerId = ownerId == null ? "" : ownerId;
            this.reference = reference == null ? "" : reference;
            this.value = value == null ? "" : value;
            this.reason = reason == null ? "" : reason;
        }

        public String getCategory() { return category; }
        public String getOwnerId() { return ownerId; }
        public String getReference() { return reference; }
        public String getValue() { return value; }
        public String getReason() { return reason; }
    }
}
