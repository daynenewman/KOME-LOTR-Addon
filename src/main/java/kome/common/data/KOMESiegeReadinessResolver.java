package kome.common.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMESiegeReadinessEvaluator;
import kome.common.siege.KOMESiegeReadinessEvaluator.AssignedBuild;
import kome.common.siege.KOMESiegeReadinessEvaluator.Diagnostic;
import kome.common.siege.KOMESiegeReadinessEvaluator.Report;
import kome.common.siege.KOMESiegeReadinessEvaluator.Source;
import kome.common.siege.validation.KOMEValidationSeverity;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;

/** Read-only KOMEWorldData adapter; each report uses one detached tactical snapshot and actual KOM-10 facts. */
public final class KOMESiegeReadinessResolver {
    private KOMESiegeReadinessResolver() { }

    public static Report evaluate(KOMEWorldData data, String complexId) { return evaluateScoped(data, complexId, null); }
    public static Report evaluate(KOMEWorldData data, String complexId, KOMETacticalGateReferenceResolver.PhysicalLookup lookup) {
        if (lookup == null) throw new IllegalArgumentException("Physical lookup is required.");
        return evaluateScoped(data, complexId, lookup);
    }
    private static Report evaluateScoped(KOMEWorldData data, String complexId, KOMETacticalGateReferenceResolver.PhysicalLookup lookup) {
        requireData(data);
        synchronized (data) {
            KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
            KOMESiegeComplex complex = configuration.findComplex(complexId);
            if (complex == null) throw new IllegalArgumentException("Siege Complex does not exist: " + complexId);
            return evaluate(data, configuration, complex, lookup);
        }
    }
    public static Map<String, Report> evaluateAll(KOMEWorldData data) { return evaluateAllScoped(data, null); }
    public static Map<String, Report> evaluateAll(KOMEWorldData data, KOMETacticalGateReferenceResolver.PhysicalLookup lookup) {
        if (lookup == null) throw new IllegalArgumentException("Physical lookup is required.");
        return evaluateAllScoped(data, lookup);
    }
    private static Map<String, Report> evaluateAllScoped(KOMEWorldData data, KOMETacticalGateReferenceResolver.PhysicalLookup lookup) {
        requireData(data);
        synchronized (data) {
            KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
            Map<String, Report> reports = new TreeMap<String, Report>();
            for (KOMESiegeComplex complex : configuration.getComplexesById().values()) {
                reports.put(complex.getComplexId(), evaluate(data, configuration, complex, lookup));
            }
            return Collections.unmodifiableMap(reports);
        }
    }
    /** Package-scoped detached draft preflight; callers hold the world-data lock. */
    static Report evaluate(final KOMEWorldData data, final KOMETacticalConfiguration configuration,
            final KOMESiegeComplex complex, final KOMETacticalGateReferenceResolver.PhysicalLookup lookup) {
        final List<AssignedBuild> assigned = new ArrayList<AssignedBuild>();
        for (String buildId : configuration.listAssignedBuildIds(complex.getComplexId())) {
            List<String> problems = new ArrayList<String>();
            for (KOMETacticalGateReferenceResolver.Code code : KOMETacticalGateReferenceResolver.buildProblems(data.getBuild(buildId), complex)) {
                problems.add(code.name());
            }
            assigned.add(new AssignedBuild(buildId, problems));
        }
        return new KOMESiegeReadinessEvaluator().evaluate(complex, new KOMESiegeReadinessEvaluator.Context() {
            public Collection<AssignedBuild> getAssignedBuilds() { return assigned; }
            public KOMEForceDeploymentArea getPreferredForceDeploymentArea() {
                return complex.getPreferredForceDeploymentAreaId().map(configuration::findForceDeploymentArea).orElse(null);
            }
            public List<Diagnostic> inspectGate(KOMESiegeConnection connection) {
                KOMETacticalGateReferenceResolver.Diagnostic resolution =
                    KOMETacticalGateReferenceResolver.resolveReference(data, configuration, complex, connection, lookup);
                List<Diagnostic> issues = new ArrayList<Diagnostic>();
                for (KOMETacticalGateReferenceResolver.Code code : resolution.getCodes()) {
                    boolean blocking = code.isLogicalError() || code == KOMETacticalGateReferenceResolver.Code.PHYSICAL_BINDING_INVALID
                        || code == KOMETacticalGateReferenceResolver.Code.PHYSICAL_BINDING_BROKEN;
                    issues.add(new Diagnostic(blocking ? KOMEValidationSeverity.ERROR : KOMEValidationSeverity.WARNING,
                        Source.CONFIGURATION, code.name(), code.isLogicalError() ? "Tactical gate reference failed: " + code
                            : code == KOMETacticalGateReferenceResolver.Code.PHYSICAL_METADATA_STALE
                                ? "Physical identity verified; captured KOM-10 metadata needs refresh." : resolution.getPhysicalDetail(),
                        complex.getComplexId(), null, connection.getId(), resolution.getBuildId(), resolution.getGateRecordId(),
                        Collections.emptyList()));
                }
                return issues;
            }
        });
    }
    private static void requireData(KOMEWorldData data) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
    }
}
