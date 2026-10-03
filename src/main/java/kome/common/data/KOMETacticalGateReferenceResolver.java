package kome.common.data;

import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalIds;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** Read-only world-aware diagnostics. Logical identity is always the Build/local gate-record pair. */
public final class KOMETacticalGateReferenceResolver {
    public enum Code {
        BUILD_MISSING(true), BUILD_INACTIVE(true), BUILD_NOT_DEFENSIVE(true),
        BUILD_UNASSIGNED(true), BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX(true),
        BUILD_TILE_MISMATCH(true), BUILD_DIMENSION_MISMATCH(true), GATE_RECORD_MISSING(true),
        PHYSICAL_BINDING_INVALID(false), PHYSICAL_BINDING_BROKEN(false),
        PHYSICAL_AVAILABILITY_UNKNOWN(false), PHYSICAL_METADATA_STALE(false);

        private final boolean logicalError;
        Code(boolean logicalError) { this.logicalError = logicalError; }
        public boolean isLogicalError() { return logicalError; }
    }

    public enum PhysicalStatus { NOT_EVALUATED, UNKNOWN, VERIFIED, BROKEN }

    private static final PhysicalLookup LIVE_PHYSICAL_LOOKUP = new LoadedPhysicalLookup();
    private static final PhysicalLookup DEFERRED_PHYSICAL_LOOKUP = new PhysicalLookup() {
        public boolean isDimensionAvailable(int dimension) { return false; }
        public boolean isControllerAvailable(Binding binding) { return false; }
        public PhysicalObservation inspect(Binding binding) { return PhysicalObservation.unknown("Inspection deferred."); }
    };

    private KOMETacticalGateReferenceResolver() { }

    public static List<Diagnostic> listReferences(KOMEWorldData data) {
        return listReferences(data, LIVE_PHYSICAL_LOOKUP);
    }

    /** The adapter must be read-only and must never load a dimension or chunk. */
    public static List<Diagnostic> listReferences(KOMEWorldData data, PhysicalLookup lookup) {
        requireData(data, lookup);
        synchronized (data) {
            return references(data, data.getTacticalConfigurationSnapshot(), null, null, null, lookup);
        }
    }

    public static List<Diagnostic> listReferencesForBuild(KOMEWorldData data, String buildId) {
        requireData(data, LIVE_PHYSICAL_LOOKUP);
        synchronized (data) {
            return references(data, data.getTacticalConfigurationSnapshot(), requireBuildId(buildId), null, null,
                LIVE_PHYSICAL_LOOKUP);
        }
    }

    public static List<Diagnostic> listReferencesForGate(KOMEWorldData data, String buildId, String gateRecordId) {
        requireData(data, LIVE_PHYSICAL_LOOKUP);
        if (gateRecordId == null || gateRecordId.trim().isEmpty()) throw new IllegalArgumentException("Gate record ID is required.");
        synchronized (data) {
            return references(data, data.getTacticalConfigurationSnapshot(), requireBuildId(buildId),
                gateRecordId.trim(), null, LIVE_PHYSICAL_LOOKUP);
        }
    }

    /** Uses actual containing-complex identity; ambiguous duplicate connection IDs are never chosen silently. */
    public static Diagnostic resolve(KOMEWorldData data, String complexId, String connectionId, PhysicalLookup lookup) {
        requireData(data, lookup);
        synchronized (data) {
            KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
            KOMESiegeComplex complex = configuration.findComplex(complexId);
            if (complex == null) throw new IllegalArgumentException("Siege Complex does not exist.");
            KOMESiegeConnection selected = null;
            String id = connectionId == null ? "" : connectionId.trim();
            for (KOMESiegeConnection connection : complex.getConnections()) {
                if (!connection.getId().equals(id)) continue;
                if (selected != null) throw new IllegalArgumentException("Connection ID is ambiguous in this complex.");
                selected = connection;
            }
            if (selected == null || !selected.getGateRef().isPresent()) {
                throw new IllegalArgumentException("A gated connection is required.");
            }
            return diagnose(data, configuration, complex, selected, lookup);
        }
    }

    public static Diagnostic resolve(KOMEWorldData data, String complexId, String connectionId) {
        return resolve(data, complexId, connectionId, LIVE_PHYSICAL_LOOKUP);
    }

    /** Scoped read-only adapter entry point; the caller holds the world-data lock and its detached snapshot. */
    static Diagnostic resolveReference(KOMEWorldData data, KOMETacticalConfiguration configuration,
            KOMESiegeComplex complex, KOMESiegeConnection connection, PhysicalLookup lookup) {
        return diagnose(data, configuration, complex, connection, lookup == null ? LIVE_PHYSICAL_LOOKUP : lookup);
    }

    /** Candidate membership impacts are logical diagnostics; no live physical inspection is needed. */
    static List<Diagnostic> membershipImpact(KOMEWorldData data, KOMETacticalConfiguration candidate,
            String buildId, String oldComplexId) {
        return references(data, candidate, buildId, null, oldComplexId, DEFERRED_PHYSICAL_LOOKUP);
    }

    private static List<Diagnostic> references(KOMEWorldData data, KOMETacticalConfiguration configuration,
            String buildFilter, String gateFilter, String complexFilter, PhysicalLookup lookup) {
        List<Diagnostic> result = new ArrayList<Diagnostic>();
        for (KOMESiegeComplex complex : configuration.getComplexesById().values()) {
            if (complexFilter != null && !complexFilter.equals(complex.getComplexId())) continue;
            for (KOMESiegeConnection connection : complex.getConnections()) {
                if (!connection.getGateRef().isPresent()) continue;
                KOMEDefensiveGateRef ref = connection.getGateRef().get();
                if (buildFilter != null && !buildFilter.equals(KOMETacticalIds.buildLookup(ref.getBuildId()))) continue;
                if (gateFilter != null && !gateFilter.equals(ref.getGateRecordId())) continue;
                result.add(diagnose(data, configuration, complex, connection, lookup));
            }
        }
        Collections.sort(result, Comparator.comparing(Diagnostic::getComplexId)
            .thenComparing(Diagnostic::getConnectionId).thenComparing(Diagnostic::getBuildId)
            .thenComparing(Diagnostic::getGateRecordId));
        return Collections.unmodifiableList(result);
    }

    /** Shared authoritative Build checks for membership and reference resolution. */
    static EnumSet<Code> buildProblems(KOMEPlayerBuild build, KOMESiegeComplex complex) {
        EnumSet<Code> codes = EnumSet.noneOf(Code.class);
        if (build == null) {
            codes.add(Code.BUILD_MISSING);
            return codes;
        }
        if (!build.active) codes.add(Code.BUILD_INACTIVE);
        if (!build.isDefensive()) codes.add(Code.BUILD_NOT_DEFENSIVE);
        String tile = KOMEConquestTile.normalizeId(build.tileId);
        if (tile.isEmpty() || !tile.equals(KOMEConquestTile.normalizeId(complex.getTileId()))) {
            codes.add(Code.BUILD_TILE_MISMATCH);
        }
        if (build.dimension != complex.getDimensionId()) codes.add(Code.BUILD_DIMENSION_MISMATCH);
        return codes;
    }

    private static Diagnostic diagnose(KOMEWorldData data, KOMETacticalConfiguration configuration,
            KOMESiegeComplex complex, KOMESiegeConnection connection, PhysicalLookup lookup) {
        KOMEDefensiveGateRef ref = connection.getGateRef().get();
        KOMEPlayerBuild build = data.getBuild(ref.getBuildId());
        EnumSet<Code> codes = buildProblems(build, complex);
        String assignment = configuration.findAssignedComplexId(ref.getBuildId()).orElse(null);
        if (assignment == null) codes.add(Code.BUILD_UNASSIGNED);
        else if (!assignment.equals(complex.getComplexId())) codes.add(Code.BUILD_ASSIGNED_TO_DIFFERENT_COMPLEX);
        KOMEDefensiveGateRecord record = build == null ? null : build.getDefensiveGateRecord(ref.getGateRecordId());
        if (build != null && record == null) codes.add(Code.GATE_RECORD_MISSING);
        PhysicalStatus physical = PhysicalStatus.NOT_EVALUATED;
        String detail = "";
        if (record != null) {
            if (!record.hasPhysicalBinding() || record.getGateDimension().intValue() != build.dimension
                    || record.getGateDimension().intValue() != complex.getDimensionId()) {
                codes.add(Code.PHYSICAL_BINDING_INVALID);
                physical = PhysicalStatus.BROKEN;
                detail = "Physical binding metadata is incomplete or has an inconsistent dimension.";
            } else if (codes.isEmpty()) {
                Binding binding = new Binding(record);
                PhysicalObservation observation;
                try {
                    if (!lookup.isDimensionAvailable(binding.getDimension())) {
                        observation = PhysicalObservation.unknown("Physical dimension is unavailable; inspection deferred.");
                    } else if (!lookup.isControllerAvailable(binding)) {
                        observation = PhysicalObservation.unknown("Controller chunk is unavailable; inspection deferred.");
                    } else {
                        observation = lookup.inspect(binding);
                        if (observation == null) observation = PhysicalObservation.unknown("Physical inspection is unavailable.");
                    }
                } catch (RuntimeException unavailable) {
                    observation = PhysicalObservation.unknown("Physical inspection could not be completed.");
                }
                detail = observation.detail;
                if (observation.status == PhysicalStatus.UNKNOWN) {
                    physical = PhysicalStatus.UNKNOWN;
                    codes.add(Code.PHYSICAL_AVAILABILITY_UNKNOWN);
                } else if (observation.status == PhysicalStatus.BROKEN) {
                    physical = PhysicalStatus.BROKEN;
                    codes.add(Code.PHYSICAL_BINDING_BROKEN);
                } else {
                    KOMEPhysicalGateInspection.Result inspection = observation.inspection;
                    if (!inspection.isLinkable() || !KOMEDefensiveGateLinkService.isSamePhysicalController(record, inspection)) {
                        physical = PhysicalStatus.BROKEN;
                        codes.add(Code.PHYSICAL_BINDING_BROKEN);
                        detail = inspection.isLinkable() ? "Physical controller identity no longer matches the record."
                            : inspection.getDiagnostic();
                    } else {
                        physical = PhysicalStatus.VERIFIED;
                        if (!capturedMetadataMatches(record, inspection)) codes.add(Code.PHYSICAL_METADATA_STALE);
                    }
                }
            }
        }
        return new Diagnostic(complex.getComplexId(), connection.getId(), ref, codes, physical, detail);
    }

    private static boolean capturedMetadataMatches(KOMEDefensiveGateRecord record, KOMEPhysicalGateInspection.Result inspection) {
        return record.getCapturedStructureRevision() == inspection.getStructureRevision()
            && record.getDetectedOrientation().equals(inspection.getOrientation())
            && record.getDetectedWidth() == inspection.getDetectedWidth() && record.getDetectedHeight() == inspection.getDetectedHeight()
            && record.getDetectedProjectedArea() == inspection.getProjectedArea()
            && record.getDimensionDetectionStatus() == inspection.getStatus();
    }

    private static String requireBuildId(String id) {
        String build = KOMETacticalIds.buildLookup(id);
        if (build.isEmpty()) throw new IllegalArgumentException("Build ID is required.");
        return build;
    }

    private static void requireData(KOMEWorldData data, PhysicalLookup lookup) {
        if (data == null || lookup == null) throw new IllegalArgumentException("World data and a physical lookup are required.");
    }

    public static final class Diagnostic {
        private final String complexId;
        private final String connectionId;
        private final KOMEDefensiveGateRef reference;
        private final Set<Code> codes;
        private final PhysicalStatus physicalStatus;
        private final String physicalDetail;

        private Diagnostic(String complexId, String connectionId, KOMEDefensiveGateRef reference,
                EnumSet<Code> codes, PhysicalStatus physicalStatus, String physicalDetail) {
            this.complexId = complexId;
            this.connectionId = connectionId;
            this.reference = reference;
            this.codes = Collections.unmodifiableSet(EnumSet.copyOf(codes));
            this.physicalStatus = physicalStatus;
            this.physicalDetail = physicalDetail;
        }
        public String getComplexId() { return complexId; }
        public String getConnectionId() { return connectionId; }
        public String getBuildId() { return reference.getBuildId(); }
        public String getGateRecordId() { return reference.getGateRecordId(); }
        public Set<Code> getCodes() { return codes; }
        public PhysicalStatus getPhysicalStatus() { return physicalStatus; }
        public String getPhysicalDetail() { return physicalDetail; }
        public boolean isLogicallyValid() {
            for (Code code : codes) if (code.isLogicalError()) return false;
            return true;
        }
    }

    /** Immutable identity captured from KOM-10, without exposing its mutable record or health/accounting. */
    public static final class Binding {
        private final int dimension, x, y, z;
        private final UUID gateUuid;
        private Binding(KOMEDefensiveGateRecord record) {
            dimension = record.getGateDimension(); x = record.getControllerX();
            y = record.getControllerY(); z = record.getControllerZ(); gateUuid = record.getGateUuid();
        }
        public int getDimension() { return dimension; }
        public int getControllerX() { return x; }
        public int getControllerY() { return y; }
        public int getControllerZ() { return z; }
        public UUID getGateUuid() { return gateUuid; }
    }

    /** inspect is called only after both availability checks succeed; unavailable is never proof of breakage. */
    public interface PhysicalLookup {
        boolean isDimensionAvailable(int dimension);
        boolean isControllerAvailable(Binding binding);
        PhysicalObservation inspect(Binding binding);
    }

    public static final class PhysicalObservation {
        private final PhysicalStatus status;
        private final KOMEPhysicalGateInspection.Result inspection;
        private final String detail;
        private PhysicalObservation(PhysicalStatus status, KOMEPhysicalGateInspection.Result inspection, String detail) {
            this.status = status; this.inspection = inspection; this.detail = detail == null ? "" : detail;
        }
        public static PhysicalObservation unknown(String detail) {
            return new PhysicalObservation(PhysicalStatus.UNKNOWN, null, detail);
        }
        public static PhysicalObservation broken(String detail) {
            return new PhysicalObservation(PhysicalStatus.BROKEN, null, detail);
        }
        public static PhysicalObservation inspected(KOMEPhysicalGateInspection.Result inspection) {
            if (inspection == null) throw new IllegalArgumentException("Inspection is required.");
            return new PhysicalObservation(PhysicalStatus.VERIFIED, inspection, "");
        }
    }

    /** Mirrors gate-management safety, checking availability before any TileEntity access. */
    private static final class LoadedPhysicalLookup implements PhysicalLookup {
        public boolean isDimensionAvailable(int dimension) { return DimensionManager.getWorld(dimension) != null; }
        public boolean isControllerAvailable(Binding binding) {
            WorldServer world = DimensionManager.getWorld(binding.getDimension());
            return world != null && world.blockExists(binding.x, binding.y, binding.z);
        }
        public PhysicalObservation inspect(Binding binding) {
            WorldServer world = DimensionManager.getWorld(binding.getDimension());
            if (world == null || !world.blockExists(binding.x, binding.y, binding.z)) {
                return PhysicalObservation.unknown("Controller became unavailable; inspection deferred.");
            }
            TileEntity tile = world.getTileEntity(binding.x, binding.y, binding.z);
            if (!(tile instanceof TileEntitySiegeGate)) return PhysicalObservation.broken("No Siege Gate at the loaded controller.");
            TileEntitySiegeGate gate = (TileEntitySiegeGate) tile;
            if (gate.isInvalid() || !gate.isFinalized() || gate.isGateStructureQuarantined()
                    || !binding.gateUuid.equals(gate.getExistingGateUuid())) {
                return PhysicalObservation.broken("The loaded physical gate binding is broken or replaced.");
            }
            if (gate.isPersistentGateMutationLocked()) return PhysicalObservation.unknown("Physical gate mutation is in progress.");
            return PhysicalObservation.inspected(KOMEPhysicalGateInspection.inspect(gate));
        }
    }
}
