package kome.common.tactical;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeComplexValidator;
import kome.common.siege.validation.KOMEValidationResult;

/**
 * Pure in-memory ownership of reusable tactical configuration, without world or persistence access.
 * Exactly three maps are authoritative; tile and membership indexes are derived on read.
 * Operations synchronize on this store and return immutable, deterministically ordered snapshots.
 * Authored geometry may remain incomplete or malformed; geometry/readiness validation is separate.
 */
public final class KOMETacticalConfiguration {
    private static final KOMESiegeComplexValidator REFERENCE_VALIDATOR = new KOMESiegeComplexValidator();
    private final Map<String, KOMESiegeComplex> complexesById = new TreeMap<String, KOMESiegeComplex>();
    private final Map<String, KOMEForceDeploymentArea> forceDeploymentAreasById = new TreeMap<String, KOMEForceDeploymentArea>();
    private final Map<String, String> buildAssignmentsByBuildId = new TreeMap<String, String>();
    private long revision;

    /** Configuration revision starts at zero and is independent of all definition revisions. */
    public synchronized long getRevision() { return revision; }

    public synchronized KOMESiegeComplex findComplex(String complexId) {
        return complexesById.get(KOMETacticalIds.lookup(complexId));
    }

    public synchronized List<KOMESiegeComplex> listComplexesForTile(String tileId) {
        String tile = KOMETacticalIds.lookup(tileId);
        List<KOMESiegeComplex> result = new ArrayList<KOMESiegeComplex>();
        for (KOMESiegeComplex complex : complexesById.values()) {
            if (tile.equals(complex.getTileId())) result.add(complex);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized KOMEForceDeploymentArea findForceDeploymentArea(String areaId) {
        return forceDeploymentAreasById.get(KOMETacticalIds.lookup(areaId));
    }

    public synchronized List<KOMEForceDeploymentArea> listForceDeploymentAreasForTile(String tileId) {
        String tile = KOMETacticalIds.lookup(tileId);
        List<KOMEForceDeploymentArea> result = new ArrayList<KOMEForceDeploymentArea>();
        for (KOMEForceDeploymentArea area : forceDeploymentAreasById.values()) {
            if (tile.equals(area.getTileId())) result.add(area);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized Optional<String> findAssignedComplexId(String buildId) {
        return Optional.ofNullable(buildAssignmentsByBuildId.get(KOMETacticalIds.buildLookup(buildId)));
    }

    public synchronized List<String> listAssignedBuildIds(String complexId) {
        String id = KOMETacticalIds.lookup(complexId);
        List<String> result = new ArrayList<String>();
        for (Map.Entry<String, String> assignment : buildAssignmentsByBuildId.entrySet()) {
            if (id.equals(assignment.getValue())) result.add(assignment.getKey());
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized Map<String, KOMESiegeComplex> getComplexesById() {
        return Collections.unmodifiableMap(new TreeMap<String, KOMESiegeComplex>(complexesById));
    }

    public synchronized Map<String, KOMEForceDeploymentArea> getForceDeploymentAreasById() {
        return Collections.unmodifiableMap(new TreeMap<String, KOMEForceDeploymentArea>(forceDeploymentAreasById));
    }

    public synchronized Map<String, String> getBuildAssignmentsByBuildId() {
        return Collections.unmodifiableMap(new TreeMap<String, String>(buildAssignmentsByBuildId));
    }

    /** Add rejects an existing canonical identity; replacement must be explicit. */
    public synchronized void addComplex(KOMESiegeComplex complex) {
        requireComplex(complex);
        String id = complex.getComplexId();
        if (complexesById.containsKey(id)) throw new IllegalStateException("Siege Complex already exists: " + id);
        validateKnownPreferredArea(complex);
        long next = nextRevision();
        complexesById.put(id, complex);
        revision = next;
    }

    /** Republishing the identical immutable object is a no-op; a new definition is a replacement. */
    public synchronized boolean replaceComplex(KOMESiegeComplex complex) {
        requireComplex(complex);
        String id = complex.getComplexId();
        KOMESiegeComplex previous = complexesById.get(id);
        if (previous == null) throw new IllegalArgumentException("Siege Complex does not exist: " + id);
        if (previous == complex) return false;
        validateKnownPreferredArea(complex);
        long next = nextRevision();
        complexesById.put(id, complex);
        revision = next;
        return true;
    }

    /** Builds must be explicitly unassigned/reassigned before their complex can be removed. */
    public synchronized boolean removeComplex(String complexId) {
        String id = requireLookup(complexId, "Siege Complex ID");
        if (!complexesById.containsKey(id)) return false;
        List<String> assigned = listAssignedBuildIds(id);
        if (!assigned.isEmpty()) throw new IllegalStateException("Siege Complex " + id + " still has assigned Builds: " + assigned);
        long next = nextRevision();
        complexesById.remove(id);
        revision = next;
        return true;
    }

    public synchronized void addForceDeploymentArea(KOMEForceDeploymentArea area) {
        requireArea(area);
        String id = area.getAreaId();
        if (forceDeploymentAreasById.containsKey(id)) throw new IllegalStateException("Force Deployment Area already exists: " + id);
        validateReferencingComplexes(area);
        long next = nextRevision();
        forceDeploymentAreasById.put(id, area);
        revision = next;
    }

    public synchronized boolean replaceForceDeploymentArea(KOMEForceDeploymentArea area) {
        requireArea(area);
        String id = area.getAreaId();
        KOMEForceDeploymentArea previous = forceDeploymentAreasById.get(id);
        if (previous == null) throw new IllegalArgumentException("Force Deployment Area does not exist: " + id);
        if (previous == area) return false;
        validateReferencingComplexes(area);
        long next = nextRevision();
        forceDeploymentAreasById.put(id, area);
        revision = next;
        return true;
    }

    /** Preferred references must be explicitly cleared/repointed before area removal. */
    public synchronized boolean removeForceDeploymentArea(String areaId) {
        String id = requireLookup(areaId, "Force Deployment Area ID");
        if (!forceDeploymentAreasById.containsKey(id)) return false;
        List<String> referring = new ArrayList<String>();
        for (KOMESiegeComplex complex : complexesById.values()) {
            if (complex.getPreferredForceDeploymentAreaId().filter(id::equals).isPresent()) referring.add(complex.getComplexId());
        }
        if (!referring.isEmpty()) throw new IllegalStateException("Force Deployment Area " + id + " is still referenced by complexes: " + referring);
        long next = nextRevision();
        forceDeploymentAreasById.remove(id);
        revision = next;
        return true;
    }

    /** Does not resolve Build existence/type/location. An existing different assignment requires reassignBuild. */
    public synchronized boolean assignBuild(String buildId, String complexId) {
        String build = requireBuild(buildId);
        String target = requireExistingComplex(complexId).getComplexId();
        String previous = buildAssignmentsByBuildId.get(build);
        if (target.equals(previous)) return false;
        if (previous != null) throw new IllegalStateException("Build " + build + " is assigned to " + previous + "; use explicit reassignment.");
        long next = nextRevision();
        buildAssignmentsByBuildId.put(build, target);
        revision = next;
        return true;
    }

    public synchronized boolean unassignBuild(String buildId) {
        String build = requireBuild(buildId);
        if (!buildAssignmentsByBuildId.containsKey(build)) return false;
        long next = nextRevision();
        buildAssignmentsByBuildId.remove(build);
        revision = next;
        return true;
    }

    /** Checks the expected owner atomically and preserves every connection and gate reference. */
    public synchronized BuildReassignment reassignBuild(String buildId, String expectedOldComplexId, String newComplexId) {
        String build = requireBuild(buildId);
        String expected = requireLookup(expectedOldComplexId, "Expected old Siege Complex ID");
        String current = buildAssignmentsByBuildId.get(build);
        if (!expected.equals(current)) throw new IllegalStateException("Build " + build + " is not assigned to expected complex " + expected + ".");
        KOMESiegeComplex target = requireExistingComplex(newComplexId);
        KOMESiegeComplex oldComplex = complexesById.get(current);
        boolean changed = !current.equals(target.getComplexId());
        BuildReassignment result = new BuildReassignment(build, oldComplex, target.getComplexId(), changed);
        if (!changed) return result;
        long next = nextRevision();
        buildAssignmentsByBuildId.put(build, target.getComplexId());
        revision = next;
        return result;
    }

    private void validateKnownPreferredArea(KOMESiegeComplex complex) {
        if (!complex.getPreferredForceDeploymentAreaId().isPresent()) return;
        KOMEForceDeploymentArea area = forceDeploymentAreasById.get(complex.getPreferredForceDeploymentAreaId().get());
        // Unresolved explicit references remain authored configuration, never silently nulled.
        if (area != null) validatePreferredReference(complex, area);
    }

    private void validateReferencingComplexes(KOMEForceDeploymentArea area) {
        for (KOMESiegeComplex complex : complexesById.values()) {
            if (complex.getPreferredForceDeploymentAreaId().filter(area.getAreaId()::equals).isPresent()) {
                validatePreferredReference(complex, area);
            }
        }
    }

    private static void validatePreferredReference(KOMESiegeComplex complex, KOMEForceDeploymentArea area) {
        KOMEValidationResult result = REFERENCE_VALIDATOR.validatePreferredForceDeploymentArea(complex, area);
        if (!result.isValid()) throw new IllegalArgumentException("Complex " + complex.getComplexId() + ", area "
            + area.getAreaId() + ": " + result.getIssues().get(0).getMessage());
    }

    private KOMESiegeComplex requireExistingComplex(String complexId) {
        String id = requireLookup(complexId, "Siege Complex ID");
        KOMESiegeComplex complex = complexesById.get(id);
        if (complex == null) throw new IllegalArgumentException("Siege Complex does not exist: " + id);
        return complex;
    }

    private static String requireBuild(String buildId) {
        String id = KOMETacticalIds.buildLookup(buildId);
        if (id.isEmpty()) throw new IllegalArgumentException("Build ID is required.");
        return id;
    }

    private static String requireLookup(String value, String description) {
        String id = KOMETacticalIds.lookup(value);
        if (id.isEmpty()) throw new IllegalArgumentException(description + " is required.");
        return id;
    }

    private static void requireComplex(KOMESiegeComplex complex) {
        if (complex == null) throw new IllegalArgumentException("A Siege Complex is required.");
    }

    private static void requireArea(KOMEForceDeploymentArea area) {
        if (area == null) throw new IllegalArgumentException("A Force Deployment Area is required.");
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("Tactical configuration revision is exhausted.");
        return revision + 1L;
    }

    /** Immutable old-definition snapshot lets later callers inspect references affected by reassignment. */
    public static final class BuildReassignment {
        private final String buildId;
        private final KOMESiegeComplex oldComplex;
        private final String newComplexId;
        private final boolean changed;

        private BuildReassignment(String buildId, KOMESiegeComplex oldComplex, String newComplexId, boolean changed) {
            this.buildId = buildId;
            this.oldComplex = oldComplex;
            this.newComplexId = newComplexId;
            this.changed = changed;
        }

        public String getBuildId() { return buildId; }
        public KOMESiegeComplex getOldComplex() { return oldComplex; }
        public String getNewComplexId() { return newComplexId; }
        public boolean isChanged() { return changed; }
    }
}
