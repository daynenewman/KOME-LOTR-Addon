package kome.common.tactical.edit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Save admission is independent of authored validity and tactical readiness. */
public final class KOMETacticalEditPreflight {
    public enum State { VALID, INCOMPLETE, INVALID, READY }
    private final boolean canSave, structurallyValid;
    private final State state;
    private final List<String> diagnostics;
    private final int totalDiagnosticCount;
    private final boolean summaryTruncated;
    public KOMETacticalEditPreflight(boolean canSave, boolean structurallyValid, State state, List<String> diagnostics) {
        this(canSave, structurallyValid, state, diagnostics, diagnostics.size());
    }
    public KOMETacticalEditPreflight(boolean canSave, boolean structurallyValid, State state, List<String> diagnostics, int totalDiagnosticCount) {
        this(canSave, structurallyValid, state, diagnostics, totalDiagnosticCount, totalDiagnosticCount > diagnostics.size());
    }
    public KOMETacticalEditPreflight(boolean canSave, boolean structurallyValid, State state, List<String> diagnostics, int totalDiagnosticCount, boolean summaryTruncated) {
        if (state == null || diagnostics == null) throw new IllegalArgumentException("Preflight state required.");
        if (totalDiagnosticCount < diagnostics.size()) throw new IllegalArgumentException("Invalid diagnostic total.");
        this.totalDiagnosticCount = totalDiagnosticCount;
        this.summaryTruncated = summaryTruncated;
        this.canSave = canSave; this.structurallyValid = structurallyValid; this.state = state;
        List<String> copy = new ArrayList<String>(diagnostics);
        Collections.sort(copy); this.diagnostics = Collections.unmodifiableList(copy);
    }
    public boolean canSave() { return canSave; }
    public boolean isStructurallyValid() { return structurallyValid; }
    public State getState() { return state; }
    public boolean isReady() { return state == State.READY; }
    public List<String> getDiagnostics() { return diagnostics; }
    public int getTotalDiagnosticCount() { return totalDiagnosticCount; }
    public boolean isSummaryTruncated() { return summaryTruncated; }
}
