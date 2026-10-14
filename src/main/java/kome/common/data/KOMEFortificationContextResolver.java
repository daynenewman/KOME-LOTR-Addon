package kome.common.data;

import java.util.Map;

import kome.common.siege.KOMESiegeComplex;
import kome.common.tactical.KOMETacticalConfiguration;

/** Shared conservative defensive-context decision for hostile arrival and ordinary resolution. */
public final class KOMEFortificationContextResolver {
    public enum Status { CLEAR, FORTIFIED, AMBIGUOUS }

    public static final class Decision {
        public final Status status;
        public final String reason;

        private Decision(Status status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public boolean requiresFortifiedResolution() {
            return status == Status.FORTIFIED;
        }

        public static Decision clear() {
            return new Decision(Status.CLEAR, "No authoritative defensive context is present.");
        }

        public static Decision fortified(String reason) {
            return new Decision(Status.FORTIFIED, reason);
        }

        public static Decision ambiguous(String reason) {
            return new Decision(Status.AMBIGUOUS, reason);
        }
    }

    private KOMEFortificationContextResolver() { }

    public static Decision evaluate(KOMEWorldData data, String tileId) {
        if (data == null) return Decision.ambiguous("World defensive authority is unavailable.");
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (!KOMEConquestTile.isCanonicalTileId(tile))
            return Decision.ambiguous("Conquest tile identity is invalid.");
        try {
            for (KOMEPlayerBuild build : KOMEBuildService.activeDefensiveBuilds(data)) {
                if (tile.equals(KOMEConquestTile.normalizeId(build.tileId)))
                    return Decision.fortified("Active Defensive Build " + build.id
                        + " requires fortified resolution.");
            }

            KOMETacticalConfiguration configuration =
                data.getTacticalConfigurationSnapshot();
            for (Map.Entry<String, String> assignment
                    : configuration.getBuildAssignmentsByBuildId().entrySet()) {
                KOMESiegeComplex complex = configuration.findComplex(assignment.getValue());
                if (complex == null || !tile.equals(complex.getTileId())) continue;
                KOMEPlayerBuild build = data.getBuild(assignment.getKey());
                if (build == null || !build.active || build.type != KOMEBuildType.DEFENSIVE
                        || !tile.equals(KOMEConquestTile.normalizeId(build.tileId)))
                    return Decision.ambiguous("Siege Complex " + complex.getComplexId()
                        + " has unresolved Defensive Build assignment "
                        + assignment.getKey() + ".");
                return Decision.fortified("Siege Complex " + complex.getComplexId()
                    + " has active Defensive Build " + build.id + ".");
            }
            return Decision.clear();
        } catch (RuntimeException malformed) {
            String message = malformed.getMessage();
            return Decision.ambiguous("Defensive context is malformed"
                + (message == null || message.length() == 0 ? "." : ": " + message));
        }
    }

    /** Same fail-closed authority used by hostile-arrival classification. */
    static boolean requireForArrival(KOMEWorldData data, String tileId) {
        Decision decision = evaluate(data, tileId);
        if (decision.status == Status.AMBIGUOUS)
            throw new IllegalStateException(decision.reason);
        return decision.requiresFortifiedResolution();
    }
}
