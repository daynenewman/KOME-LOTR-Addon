package kome.common.siege;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.siege.validation.KOMEValidationSeverity;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMEForceDeploymentAreaValidator;
import kome.common.tactical.KOMETacticalIds;

/** Pure authoring readiness: immutable definitions plus caller-supplied membership/reference facts. */
public final class KOMESiegeReadinessEvaluator {
    public enum Source { GEOMETRY, CONFIGURATION, READINESS }

    /** Implementations supply read-only facts for this containing complex, never live siege progression. */
    public interface Context {
        Collection<AssignedBuild> getAssignedBuilds();
        List<Diagnostic> inspectGate(KOMESiegeConnection connection);
        KOMEForceDeploymentArea getPreferredForceDeploymentArea();
    }

    public Report evaluate(KOMESiegeComplex complex, Context context) {
        if (complex == null || context == null) throw new IllegalArgumentException("Complex and readiness context are required.");
        List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
        KOMEValidationResult definition = new KOMESiegeComplexValidator().validate(complex);
        List<KOMEValidationIssue> geometryIssues = new ArrayList<KOMEValidationIssue>();
        for (KOMEValidationIssue issue : definition.getIssues()) {
            String code = issue.getCode().name();
            boolean geometric = code.startsWith("POLYGON_") || code.startsWith("PRISM_") || code.endsWith("_OVERLAP");
            if (geometric) geometryIssues.add(issue);
            addValidation(diagnostics, complex, new KOMEValidationResult(Collections.singletonList(issue)),
                geometric ? Source.GEOMETRY : Source.CONFIGURATION, true);
        }
        KOMEValidationResult geometry = new KOMEValidationResult(geometryIssues);

        Collection<AssignedBuild> builds = context.getAssignedBuilds();
        boolean validBuild = false;
        for (AssignedBuild build : builds) if (build.isValid()) validBuild = true;
        if (!validBuild) diagnostics.add(rule(complex, "NO_VALID_DEFENSIVE_BUILD",
            "At least one valid assigned Defensive Build is required.", null));
        for (AssignedBuild build : builds) for (String code : build.getProblemCodes()) {
            // An invalid unused assignment contributes no support. Gate usage still independently blocks below.
            diagnostics.add(new Diagnostic(validBuild ? KOMEValidationSeverity.WARNING : KOMEValidationSeverity.ERROR,
                Source.CONFIGURATION, code, "Assigned Build cannot contribute tactical support.", complex.getComplexId(),
                null, null, build.getBuildId(), null, Collections.singletonList(build.getBuildId())));
        }
        if (complex.getNormalSegments().isEmpty()) diagnostics.add(rule(complex, "NO_NORMAL_SEGMENTS",
            "At least one valid Normal Segment is required.", null));

        KOMEForceDeploymentArea area = context.getPreferredForceDeploymentArea();
        addValidation(diagnostics, complex, new KOMESiegeComplexValidator()
            .validatePreferredForceDeploymentArea(complex, area), Source.CONFIGURATION, false);
        if (complex.getPreferredForceDeploymentAreaId().isPresent() && area != null
                && complex.getPreferredForceDeploymentAreaId().get().equals(area.getAreaId())) {
            addValidation(diagnostics, complex, new KOMEForceDeploymentAreaValidator().validate(area), Source.CONFIGURATION, false);
        }

        Map<KOMESiegeAreaRef, Set<KOMESiegeAreaRef>> graph = new TreeMap<KOMESiegeAreaRef, Set<KOMESiegeAreaRef>>();
        graph.put(KOMESiegeAreaRef.exterior(), new TreeSet<KOMESiegeAreaRef>());
        for (KOMENormalSegment normal : complex.getNormalSegments()) {
            graph.put(KOMESiegeAreaRef.normal(normal.getId()), new TreeSet<KOMESiegeAreaRef>());
        }
        Set<String> connectionIds = new TreeSet<String>();
        for (KOMESiegeConnection connection : complex.getConnections()) {
            connectionIds.add(connection.getId());
            boolean legalGate = true;
            if (connection.isGated()) {
                List<Diagnostic> gateIssues = context.inspectGate(connection);
                if (gateIssues == null) {
                    KOMEDefensiveGateRef ref = connection.getGateRef().get();
                    gateIssues = Collections.singletonList(new Diagnostic(KOMEValidationSeverity.ERROR,
                        Source.CONFIGURATION, "GATE_REFERENCE_UNRESOLVED", "Gate reference has no resolution facts.",
                        complex.getComplexId(), null, connection.getId(), ref.getBuildId(), ref.getGateRecordId(),
                        Collections.emptyList()));
                }
                for (Diagnostic issue : gateIssues) {
                    diagnostics.add(issue);
                    if (issue.getSeverity() == KOMEValidationSeverity.ERROR) legalGate = false;
                }
            }
            // Geometry validation is authoritative for endpoint/corridor legality; invalid topology is not traversed.
            if (definition.isValid() && legalGate) {
                graph.get(connection.getEndpointA()).add(connection.getEndpointB());
                graph.get(connection.getEndpointB()).add(connection.getEndpointA());
            }
        }
        Set<String> reachable = new TreeSet<String>();
        if (definition.isValid()) {
            Set<KOMESiegeAreaRef> visited = new TreeSet<KOMESiegeAreaRef>();
            ArrayDeque<KOMESiegeAreaRef> pending = new ArrayDeque<KOMESiegeAreaRef>();
            visited.add(KOMESiegeAreaRef.exterior()); pending.add(KOMESiegeAreaRef.exterior());
            while (!pending.isEmpty()) {
                KOMESiegeAreaRef endpoint = pending.removeFirst();
                if (endpoint.isNormal()) reachable.add(endpoint.getNormalSegmentId());
                for (KOMESiegeAreaRef next : graph.get(endpoint)) if (visited.add(next)) pending.addLast(next);
            }
            if (reachable.isEmpty()) diagnostics.add(rule(complex, "NO_EXTERIOR_PATH",
                "At least one legal authored connection path from EXTERIOR is required.", null));
            for (KOMENormalSegment normal : complex.getNormalSegments()) if (!reachable.contains(normal.getId())) {
                diagnostics.add(rule(complex, "NORMAL_SEGMENT_UNREACHABLE",
                    "Authored Normal Segment is not reachable from EXTERIOR through legal connections.", normal.getId()));
            }
        }
        return new Report(complex.getComplexId(), definition, geometry, diagnostics, reachable, connectionIds);
    }

    private static Diagnostic rule(KOMESiegeComplex complex, String code, String message, String segmentId) {
        return new Diagnostic(KOMEValidationSeverity.ERROR, Source.READINESS, code, message, complex.getComplexId(),
            segmentId, null, null, null, segmentId == null ? Collections.emptyList() : Collections.singletonList(segmentId));
    }

    private static void addValidation(List<Diagnostic> diagnostics, KOMESiegeComplex complex,
            KOMEValidationResult validation, Source source, boolean complexOwned) {
        for (KOMEValidationIssue issue : validation.getIssues()) {
            String segment = null, connection = null;
            if (complexOwned) {
                // Existing validator subjects are untyped and sorted. Name a role only when it is unambiguous.
                if (source == Source.GEOMETRY) {
                    Set<String> matches = new TreeSet<String>();
                    for (KOMENormalSegment normal : complex.getNormalSegments()) {
                        if (issue.getSubjectIds().contains(normal.getId())) matches.add(normal.getId());
                    }
                    if (matches.size() == 1) segment = matches.iterator().next();
                }
                String code = issue.getCode().name();
                if (code.contains("CONNECTION") || code.startsWith("GATE_REF_")) {
                    Set<String> matches = new TreeSet<String>();
                    for (KOMESiegeConnection edge : complex.getConnections()) {
                        if (issue.getSubjectIds().contains(edge.getId())) matches.add(edge.getId());
                    }
                    if (matches.size() == 1) connection = matches.iterator().next();
                }
            }
            diagnostics.add(new Diagnostic(issue.getSeverity(), source, issue.getCode().name(), issue.getMessage(),
                complex.getComplexId(), segment, connection, null, null, issue.getSubjectIds()));
        }
    }

    /** Detached membership facts; no Build accounting or mutable Build record is copied here. */
    public static final class AssignedBuild {
        private final String buildId;
        private final List<String> problemCodes;
        public AssignedBuild(String buildId, Collection<String> problemCodes) {
            this.buildId = KOMETacticalIds.buildLookup(buildId);
            if (this.buildId.isEmpty() || problemCodes == null) throw new IllegalArgumentException("Build identity and assessment are required.");
            this.problemCodes = Collections.unmodifiableList(new ArrayList<String>(new TreeSet<String>(problemCodes)));
        }
        public String getBuildId() { return buildId; }
        public List<String> getProblemCodes() { return problemCodes; }
        public boolean isValid() { return problemCodes.isEmpty(); }
    }

    /** Existing validation/resolution code names are retained without coupling the pure evaluator to world services. */
    public static final class Diagnostic implements Comparable<Diagnostic> {
        private final KOMEValidationSeverity severity;
        private final Source source;
        private final String code, message, complexId, segmentId, connectionId, buildId, gateRecordId;
        private final List<String> subjectIds;
        public Diagnostic(KOMEValidationSeverity severity, Source source, String code, String message,
                String complexId, String segmentId, String connectionId, String buildId, String gateRecordId,
                Collection<String> subjectIds) {
            if (severity == null || source == null || code == null || code.trim().isEmpty() || complexId == null) {
                throw new IllegalArgumentException("Diagnostic severity, source, code and owner are required.");
            }
            this.severity = severity; this.source = source; this.code = code; this.message = clean(message);
            this.complexId = complexId; this.segmentId = clean(segmentId); this.connectionId = clean(connectionId);
            this.buildId = clean(buildId); this.gateRecordId = clean(gateRecordId);
            List<String> subjects = new ArrayList<String>(subjectIds);
            Collections.sort(subjects);
            this.subjectIds = Collections.unmodifiableList(subjects);
        }
        public KOMEValidationSeverity getSeverity() { return severity; }
        public Source getSource() { return source; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
        public String getComplexId() { return complexId; }
        public String getSegmentId() { return segmentId; }
        public String getConnectionId() { return connectionId; }
        public String getBuildId() { return buildId; }
        public String getGateRecordId() { return gateRecordId; }
        public List<String> getSubjectIds() { return subjectIds; }
        @Override public int compareTo(Diagnostic other) {
            int order = complexId.compareTo(other.complexId);
            if (order == 0) order = source.compareTo(other.source);
            if (order == 0) order = code.compareTo(other.code);
            if (order == 0) order = segmentId.compareTo(other.segmentId);
            if (order == 0) order = connectionId.compareTo(other.connectionId);
            if (order == 0) order = buildId.compareTo(other.buildId);
            if (order == 0) order = gateRecordId.compareTo(other.gateRecordId);
            if (order == 0) {
                int shared = Math.min(subjectIds.size(), other.subjectIds.size());
                for (int i = 0; i < shared && order == 0; i++) order = subjectIds.get(i).compareTo(other.subjectIds.get(i));
                if (order == 0) order = Integer.compare(subjectIds.size(), other.subjectIds.size());
            }
            if (order == 0) order = severity.compareTo(other.severity);
            return order == 0 ? message.compareTo(other.message) : order;
        }
        private static String clean(String value) { return value == null ? "" : value; }
    }

    public static final class Report {
        private final String complexId;
        private final KOMEValidationResult definitionValidation, geometryValidation;
        private final List<Diagnostic> blockingDiagnostics, warnings;
        private final Set<String> reachableNormalSegmentIds, authoredConnectionIds;
        private Report(String complexId, KOMEValidationResult definitionValidation, KOMEValidationResult geometryValidation, List<Diagnostic> diagnostics,
                Set<String> reachable, Set<String> connections) {
            this.complexId = complexId; this.definitionValidation = definitionValidation; this.geometryValidation = geometryValidation;
            List<Diagnostic> errors = new ArrayList<Diagnostic>(), deferred = new ArrayList<Diagnostic>();
            Collections.sort(diagnostics);
            for (Diagnostic issue : diagnostics) (issue.getSeverity() == KOMEValidationSeverity.ERROR ? errors : deferred).add(issue);
            blockingDiagnostics = Collections.unmodifiableList(errors); warnings = Collections.unmodifiableList(deferred);
            reachableNormalSegmentIds = Collections.unmodifiableSet(new TreeSet<String>(reachable));
            authoredConnectionIds = Collections.unmodifiableSet(new TreeSet<String>(connections));
        }
        public String getComplexId() { return complexId; }
        public boolean isReady() { return blockingDiagnostics.isEmpty(); }
        public KOMEValidationResult getDefinitionValidation() { return definitionValidation; }
        public KOMEValidationResult getGeometryValidation() { return geometryValidation; }
        /** Required configuration has no blocking reference errors; warnings remain separately inspectable. */
        public boolean isConfigurationValid() {
            for (Diagnostic issue : blockingDiagnostics) if (issue.getSource() == Source.CONFIGURATION) return false;
            return true;
        }
        public List<Diagnostic> getBlockingDiagnostics() { return blockingDiagnostics; }
        public List<Diagnostic> getWarnings() { return warnings; }
        public Set<String> getReachableNormalSegmentIds() { return reachableNormalSegmentIds; }
        public Set<String> getAuthoredConnectionIds() { return authoredConnectionIds; }
    }
}
