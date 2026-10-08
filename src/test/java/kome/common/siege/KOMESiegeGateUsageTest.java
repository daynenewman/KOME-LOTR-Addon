package kome.common.siege;

import java.util.*;
import kome.common.siege.validation.*;
import kome.common.tactical.KOMEForceDeploymentArea;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.siege.KOMESiegeReadinessFixtures.*;

public class KOMESiegeGateUsageTest {
    private KOMEDefensiveGateRef gate(String build, String id) { return new KOMEDefensiveGateRef(build,id); }
    private KOMESiegeComplex parallel(KOMEDefensiveGateRef first, KOMEDefensiveGateRef second) {
        KOMESiegeComplex base = minimal("FORT","T100",0,null);
        return new KOMESiegeComplex("FORT","T100",0,17,base.getNormalSegments(),base.getWallZones(),
            Arrays.asList(base.getTransitionZones().get(0),new KOMETransitionZone("T_SECOND","",prism(-2,6,0,8))),null,
            Arrays.asList(new KOMESiegeConnection("ENTRY",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"T_ENTRY",first),
                new KOMESiegeConnection("SECOND",KOMESiegeAreaRef.normal("A"),KOMESiegeAreaRef.exterior(),"T_SECOND",second)));
    }
    @Test public void distinctCompositeGatesAndOpenParallelCorridorsAreValid() {
        for (KOMESiegeComplex c : Arrays.asList(parallel(gate("B7","G1"),null), parallel(null,null),
                parallel(gate("B7","G1"),gate("B7","G2")),parallel(gate("B7","G1"),gate("B8","G1")),
                parallel(gate("B7","G1"),gate("B7","g1")))) {
            assertTrue(new KOMESiegeComplexValidator().validate(c).isValid());
            KOMESiegeGateUsage.requireNoNewConflicts(null,c);
        }
    }
    @Test public void canonicalBuildAliasesAreOneGateWhileRecordIdsRemainCaseSensitive() {
        KOMESiegeComplex c=parallel(gate("B7","G1"),gate("b7","G1"));
        assertFalse(KOMESiegeGateUsage.validate(c).isValid());
        assertThrows(IllegalArgumentException.class,()->KOMESiegeGateUsage.requireNoNewConflicts(null,c));
        assertEquals(Collections.singletonList("SECOND"),KOMESiegeGateUsage.otherConnectionIds(c,"ENTRY",gate(" B7 ","G1")));
    }
    @Test public void diagnosticsIdentifyAllConflictsDeterministicallyWithoutMutatingAuthoring() {
        KOMESiegeComplex c=parallel(gate("B7","G1"),gate("B7","G1"));
        List<KOMESiegeConnection> before=new ArrayList<>(c.getConnections());
        List<KOMESiegeConnection> reversed=new ArrayList<>(c.getConnections()); Collections.reverse(reversed);
        KOMESiegeComplex other=new KOMESiegeComplex("FORT","T100",0,17,c.getNormalSegments(),c.getWallZones(),c.getTransitionZones(),null,reversed);
        KOMEValidationIssue issue=KOMESiegeGateUsage.validate(c).getIssues().get(0);
        assertEquals(KOMEValidationCode.CONNECTION_GATE_REUSED,issue.getCode());
        assertEquals(Arrays.asList("B7","ENTRY","FORT","G1","SECOND"),issue.getSubjectIds());
        assertEquals(issue.getMessage(),KOMESiegeGateUsage.validate(other).getIssues().get(0).getMessage());
        assertTrue(issue.getMessage().contains("[ENTRY, SECOND]"));
        assertEquals(before,c.getConnections()); assertEquals(17,c.getRevision()); assertEquals(2,c.getConnections().size());
        assertThrows(UnsupportedOperationException.class,()->KOMESiegeGateUsage.otherConnectionIds(c,null,gate("B7","G1")).clear());
    }
    @Test public void retainingOwnGateAndRepairingHistoricalDuplicatesAreAllowedButExpansionIsRejected() {
        KOMESiegeComplex valid=parallel(gate("B7","G1"),null), bad=parallel(gate("B7","G1"),gate("B7","G1"));
        KOMESiegeGateUsage.requireNoNewConflicts(valid,valid);
        assertThrows(IllegalArgumentException.class,()->KOMESiegeGateUsage.requireNoNewConflicts(valid,bad));
        KOMESiegeGateUsage.requireNoNewConflicts(bad,bad); KOMESiegeGateUsage.requireNoNewConflicts(bad,valid);
        KOMESiegeComplex renamed=new KOMESiegeComplex("FORT","T100",0,17,bad.getNormalSegments(),bad.getWallZones(),bad.getTransitionZones(),null,
            Arrays.asList(bad.getConnections().get(0),new KOMESiegeConnection("THIRD",KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal("A"),"T_SECOND",gate("B7","G1"))));
        assertThrows(IllegalArgumentException.class,()->KOMESiegeGateUsage.requireNoNewConflicts(bad,renamed));
    }
    @Test public void gateUsageIsComplexLocal() {
        KOMESiegeComplex a=parallel(gate("B7","G1"),null), b=parallel(gate("B7","G1"),null);
        assertTrue(KOMESiegeGateUsage.validate(a).isValid()); assertTrue(KOMESiegeGateUsage.validate(b).isValid());
    }
    @Test public void duplicateBlocksReadinessAsConfigurationAndRepairRestoresReady() {
        KOMESiegeReadinessEvaluator.Context context=new KOMESiegeReadinessEvaluator.Context() {
            public Collection<KOMESiegeReadinessEvaluator.AssignedBuild> getAssignedBuilds() {
                return Collections.singletonList(new KOMESiegeReadinessEvaluator.AssignedBuild("B7",Collections.emptyList()));
            }
            public List<KOMESiegeReadinessEvaluator.Diagnostic> inspectGate(KOMESiegeConnection c) { return Collections.emptyList(); }
            public KOMEForceDeploymentArea getPreferredForceDeploymentArea() { return null; }
        };
        KOMESiegeReadinessEvaluator evaluator=new KOMESiegeReadinessEvaluator();
        KOMESiegeReadinessEvaluator.Report bad=evaluator.evaluate(parallel(gate("B7","G1"),gate("B7","G1")),context);
        assertFalse(bad.isReady()); assertTrue(bad.getGeometryValidation().isValid());
        assertTrue(bad.getBlockingDiagnostics().stream().anyMatch(d->d.getCode().equals("CONNECTION_GATE_REUSED")
            &&d.getSource()==KOMESiegeReadinessEvaluator.Source.CONFIGURATION&&d.getSubjectIds().containsAll(Arrays.asList("B7","G1","ENTRY","SECOND"))));
        assertTrue(evaluator.evaluate(parallel(gate("B7","G1"),gate("B7","G2")),context).isReady());
        assertTrue(evaluator.evaluate(parallel(gate("B7","G1"),null),context).isReady());
    }
}
