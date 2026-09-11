package kome.common.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only campaign reference validation. Pending campaign systems supply this
 * service through {@link CampaignReadinessData}; it owns no world persistence.
 */
public final class KOMECampaignReadinessValidator {
    public ReadinessResult validate(CampaignReadinessData data) {
        List<ValidationFailure> failures = new ArrayList<ValidationFailure>();
        validateCapitals(data, failures);
        validateDefenses(data, failures);
        validateMapAssets(data, failures);
        Collections.sort(failures, FAILURE_ORDER);
        return new ReadinessResult(failures);
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

    private void validateDefenses(CampaignReadinessData data,
            List<ValidationFailure> failures) {
        for (DefensiveBuild build : data.getDefensiveBuilds()) {
            SiegeComplex complex = data.findSiegeComplex(build.getSiegeComplexId());
            if (complex == null) {
                failure(failures, "defensiveBuild", build.getId(), "siegeComplexId",
                        build.getSiegeComplexId(), "defensive build references no known Siege Complex");
                continue;
            }
            for (Connection connection : complex.getConnections()) {
                validateConnection(failures, complex, connection);
            }
            if (build.requiresEntry() && blank(build.getEntryConnectionId())) {
                failure(failures, "defensiveBuild", build.getId(), "entryConnectionId",
                        build.getEntryConnectionId(), "defensive build requires an entry connection");
            } else if (!blank(build.getEntryConnectionId())
                    && !complex.hasConnection(build.getEntryConnectionId())) {
                failure(failures, "defensiveBuild", build.getId(), "entryConnectionId",
                        build.getEntryConnectionId(), "entry connection does not belong to Siege Complex");
            }
        }
    }

    private void validateConnection(List<ValidationFailure> failures, SiegeComplex complex,
            Connection connection) {
        if (!complex.hasSegment(connection.getFromSegmentId())) {
            failure(failures, "siegeConnection", complex.getId(), "fromSegmentId",
                    connection.getFromSegmentId(), "connection references an unknown segment");
        }
        if (!complex.hasSegment(connection.getToSegmentId())) {
            failure(failures, "siegeConnection", complex.getId(), "toSegmentId",
                    connection.getToSegmentId(), "connection references an unknown segment");
        }
        if (connection.requiresGate() && !complex.hasGate(connection.getGateId())) {
            failure(failures, "siegeConnection", complex.getId(), "gateId",
                    connection.getGateId(), "gated connection references an unknown gate");
        }
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
        SiegeComplex findSiegeComplex(String complexId);
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

    public interface SiegeComplex {
        String getId();
        boolean hasSegment(String segmentId);
        boolean hasGate(String gateId);
        boolean hasConnection(String connectionId);
        Collection<Connection> getConnections();
    }

    public interface Connection {
        String getFromSegmentId();
        String getToSegmentId();
        boolean requiresGate();
        String getGateId();
    }

    public static final class ReadinessResult {
        private final List<ValidationFailure> failures;

        private ReadinessResult(List<ValidationFailure> failures) {
            this.failures = Collections.unmodifiableList(
                    new ArrayList<ValidationFailure>(failures));
        }

        public boolean isReady() { return failures.isEmpty(); }
        public List<ValidationFailure> getFailures() { return failures; }
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
