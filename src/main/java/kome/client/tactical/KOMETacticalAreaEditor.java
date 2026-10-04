package kome.client.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import kome.common.siege.geometry.*;
import kome.common.siege.*;
import kome.client.tactical.KOMETacticalComplexDraft.ZoneType;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMEForceDeploymentAreaValidator;
import kome.common.siege.validation.*;
import kome.common.tactical.edit.*;

/** Client advisory edit state for the shared editor shell; authoritative changes remain server sessions. */
public final class KOMETacticalAreaEditor {
    public enum Selection { NONE, VERTICES, LOWER_Y, UPPER_Y, TOPOLOGY }
    private final Consumer<KOMETacticalEditRequest> transport;
    private KOMETacticalAreaCatalog catalog;
    private KOMETacticalEditSnapshot snapshot;
    private KOMEForceDeploymentArea local;
    private KOMESiegeComplex localComplex;
    private boolean complexBrowser;
    private KOMETacticalComplexCatalog complexCatalog, options;
    private KOMETacticalComplexCatalog.Kind pendingKind;
    private ZoneType zoneType = ZoneType.NORMAL;
    private String zoneId;
    private String connectionId;
    private KOMEConnectionEndpointInference.Result inference;
    private KOMESiegeComplex inferenceSource;
    private KOMETacticalComplexCatalog connectionCatalog;
    private KOMESiegeComplex topologySource;
    private KOMEValidationResult topologyValidation;
    private KOMESiegeComplex comparedComplex;
    private KOMETacticalEditDraft comparedDraft;
    private boolean sameSnapshotDefinition;
    private UUID cancelled;
    private KOMETacticalEditRequest.Action afterUpdate;
    private boolean busy, openingNew, creating, waitingCatalog;
    private int requestedPage;
    private Selection selection = Selection.NONE;
    private String message = "";
    private long lastWorldRefresh;
    private long catalogueVersion;
    private GeometryFeedback feedback;
    private KOMETacticalPolygonPreview.Corner corner = KOMETacticalPolygonPreview.Corner.NW;
    private KOMEPolygon fillSource;
    private List<KOMEXZPoint> fillTriangles;
    public KOMETacticalAreaEditor(Consumer<KOMETacticalEditRequest> transport) { this.transport = transport; }
    public KOMETacticalAreaCatalog getCatalog() { return catalog; }
    public KOMEForceDeploymentArea getDraft() { return local; }
    public KOMESiegeComplex getComplexDraft() { return localComplex; }
    public boolean isComplexBrowser() { return complexBrowser; }
    public KOMETacticalComplexCatalog getComplexCatalog() { return complexCatalog; }
    public KOMETacticalComplexCatalog getOptions() { return options; }
    public long getCatalogueVersion() { return catalogueVersion; }
    public ZoneType getZoneType() { return zoneType; }
    public String getZoneId() { return zoneId; }
    public String getConnectionId() { return connectionId; }
    public List<KOMESiegeConnection> getConnections() { return localComplex == null ? java.util.Collections.emptyList() : KOMETacticalConnectionDraft.list(localComplex); }
    public KOMESiegeConnection getConnection() { return localComplex == null || connectionId == null ? null : KOMETacticalConnectionDraft.find(localComplex, connectionId); }
    public List<KOMESiegeAreaRef> endpointChoices() { return KOMETacticalConnectionDraft.endpoints(localComplex); }
    public KOMEConnectionEndpointInference.Result getInference() { return inferenceSource == localComplex ? inference : null; }
    public boolean isEditing() { return snapshot != null && !snapshot.isClosed() && (local != null || localComplex != null); }
    public boolean isComplexEditing() { return isEditing() && localComplex != null; }
    public KOMESiegeZone getZone() { return localComplex == null || zoneId == null ? null : KOMETacticalComplexDraft.find(localComplex, zoneType, zoneId); }
    public KOMEPolygonPrism getGeometry() { return local != null ? local.getPrism() : getZone() == null ? null : getZone().getPrism(); }
    public String getGeometryLabel() { return local != null ? local.getLabel() : getZone() == null ? "" : getZone().getLabel(); }
    public String getGeometryId() { return local != null ? local.getAreaId() : zoneId == null ? "" : zoneId; }
    public String getTargetId() { return local != null ? local.getAreaId() : localComplex == null ? "" : localComplex.getComplexId(); }
    public int getAssignedBuildCount() { return snapshot == null ? 0 : snapshot.getTotalAssignedBuildCount(); }
    public long displayedStoreRevision() {
        long value = catalog == null ? 0 : catalog.revision;
        if (complexCatalog != null) value = Math.max(value, complexCatalog.revision);
        if (options != null) value = Math.max(value, options.revision);
        return snapshot == null ? value : Math.max(value, snapshot.getCurrentRevision());
    }
    public String readinessSummary() {
        if (localComplex == null || snapshot.getPreflight() == null || !definitionMatchesSnapshot()) return "Validate for readiness";
        return snapshot.getPreflight().isReady() ? "READY" : snapshot.getPreflight().canSave() ? "NOT READY (saveable authoring)" : "NOT READY / save blocked";
    }
    public boolean isBusy() { return busy; }
    public boolean isCreating() { return creating; }
    public String getMessage() { return message; }
    public Selection getSelection() { return selection; }
    public boolean acceptCatalog(KOMETacticalAreaCatalog page) {
        if (isEditing() && !page.tileId.equals(catalog.tileId)) { message = "Cancel this edit before changing tiles."; return false; }
        if (waitingCatalog && page.page != requestedPage) return false;
        if (catalog != null && catalog.tileId.equals(page.tileId) && page.revision < catalog.revision) return false;
        if (catalog != null && !catalog.tileId.equals(page.tileId)) { complexBrowser = false; complexCatalog = null; options = null; pendingKind = null; }
        catalog = page; ++catalogueVersion; waitingCatalog = false; busy = false; return true;
    }
    public boolean acceptComplexCatalog(KOMETacticalComplexCatalog page) {
        if (catalog == null || !catalog.tileId.equals(page.tileId) || page.dimension != catalog.dimension || page.revision < catalog.revision) return false;
        if (pendingKind == null || pendingKind != page.kind || page.page != requestedPage) return false;
        KOMETacticalComplexCatalog previous = page.kind == KOMETacticalComplexCatalog.Kind.COMPLEXES ? complexCatalog
            : page.kind == KOMETacticalComplexCatalog.Kind.CONNECTIONS ? connectionCatalog : options;
        if (previous != null && previous.kind == page.kind && previous.revision > page.revision) return false;
        if (page.kind != KOMETacticalComplexCatalog.Kind.COMPLEXES && (!isComplexEditing() || !localComplex.getComplexId().equals(page.complexId))) return false;
        if (page.kind == KOMETacticalComplexCatalog.Kind.COMPLEXES) complexCatalog = page;
        else if (page.kind == KOMETacticalComplexCatalog.Kind.CONNECTIONS) connectionCatalog = page; else options = page;
        ++catalogueVersion; pendingKind = null; busy = false; return true;
    }
    public void switchBrowser(boolean complexes) {
        if (busy || isEditing()) return;
        complexBrowser = complexes; options = null;
        if (complexes) complexPage(KOMETacticalComplexCatalog.Kind.COMPLEXES, 0); else browse(0);
    }
    public void complexPage(KOMETacticalComplexCatalog.Kind kind, int page) {
        if (busy || catalog == null) return;
        if (kind != KOMETacticalComplexCatalog.Kind.COMPLEXES && (!isComplexEditing() || creating && kind != KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS))
            throw new IllegalArgumentException("Save and reopen the new complex first.");
        requestedPage = page; pendingKind = kind; busy = true;
        transport.accept(KOMETacticalEditRequest.complexPage(kind, catalog.tileId, catalog.dimension,
            localComplex == null ? null : localComplex.getComplexId(), page));
    }
    public void openComplex(String id, boolean create) {
        if (busy || isEditing() || catalog == null) return;
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.SIEGE_COMPLEX, catalog.tileId, id, id, catalog.dimension);
        complexBrowser = true; openingNew = create; busy = true; message = "Opening complex...";
        transport.accept(create ? KOMETacticalEditRequest.create(scope) : KOMETacticalEditRequest.open(scope));
    }
    public void browse(int page) {
        if (busy || catalog == null || isEditing()) return;
        waitingCatalog = true; requestedPage = page; busy = true;
        transport.accept(KOMETacticalEditRequest.browse(catalog.tileId, catalog.dimension, page));
    }
    public void open(String id, boolean create) {
        if (busy || isEditing() || catalog == null) return;
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA,
            catalog.tileId, null, id, catalog.dimension);
        openingNew = create; busy = true; message = "Opening area...";
        transport.accept(create ? KOMETacticalEditRequest.create(scope) : KOMETacticalEditRequest.open(scope));
    }
    public void accept(KOMETacticalEditSnapshot value, KOMETacticalEditSessionManager.Status status) {
        if (value != null && value.getToken().equals(cancelled) && !value.isClosed()) {
            if (status == KOMETacticalEditSessionManager.Status.STALE_SEQUENCE)
                transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, value));
            return;
        }
        if (value != null && catalog != null && !value.getScope().getTileId().equals(catalog.tileId)) return;
        if (status == KOMETacticalEditSessionManager.Status.REFRESHED && afterUpdate != null) { snapshot = value; return; }
        if (status == KOMETacticalEditSessionManager.Status.RATE_LIMITED && afterUpdate != null) {
            busy = false; afterUpdate = null; message = statusMessage(status); return;
        }
        busy = false;
        // A keepalive must not erase the confirmation/duplicate-corner feedback from local input.
        if (status != KOMETacticalEditSessionManager.Status.REFRESHED) message = statusMessage(status);
        if (value == null) {
            afterUpdate = null;
            if (status == KOMETacticalEditSessionManager.Status.DENIED || status == KOMETacticalEditSessionManager.Status.INVALID_SESSION) clearDraft();
            return;
        }
        boolean fresh = snapshot == null || !snapshot.getToken().equals(value.getToken());
        snapshot = value;
        if (value.isClosed()) {
            if (value.getPreflight() != null && !value.getPreflight().getDiagnostics().isEmpty())
                message += " " + String.join(" ", value.getPreflight().getDiagnostics());
            clearDraft(); afterUpdate = null;
            if (catalog != null && (status == KOMETacticalEditSessionManager.Status.SAVED || status == KOMETacticalEditSessionManager.Status.DELETED)) {
                if (complexBrowser) complexPage(KOMETacticalComplexCatalog.Kind.COMPLEXES, 0); else browse(0);
            }
            return;
        }
        if (fresh || status == KOMETacticalEditSessionManager.Status.UPDATED) {
            local = value.getDraft().getArea();
            localComplex = value.getDraft().getComplex();
            if (localComplex != null) complexBrowser = true;
            if (fresh) { creating = openingNew; cancelled = null; clearGeometryPreview(); }
            feedback = null;
        }
        if (value.getPreflight() != null && status != KOMETacticalEditSessionManager.Status.REFRESHED) {
            KOMETacticalEditPreflight p = value.getPreflight();
            if (status == KOMETacticalEditSessionManager.Status.VALIDATED) {
                message = p.isStructurallyValid() ? "Geometry valid." : "Geometry needs attention.";
                if (localComplex != null && p.getState() == KOMETacticalEditPreflight.State.INCOMPLETE) message += " Setup incomplete.";
                else if (localComplex != null && p.isStructurallyValid() && p.getState() == KOMETacticalEditPreflight.State.INVALID) message += " Configuration needs attention.";
                if (localComplex != null) message += p.isReady() ? " READY." : p.canSave() ? " NOT READY; authoring progress can be saved." : " NOT READY.";
                if (!p.canSave()) message = "Save blocked. " + message;
            }
            if (!p.getDiagnostics().isEmpty()) message += " " + String.join(" ", p.getDiagnostics());
            if (p.isSummaryTruncated()) message += " (" + p.getTotalDiagnosticCount() + " diagnostics; showing a summary.)";
        }
        if (status == KOMETacticalEditSessionManager.Status.UPDATED && afterUpdate != null) {
            KOMETacticalEditRequest.Action next = afterUpdate; afterUpdate = null; send(next);
        } else if (status != KOMETacticalEditSessionManager.Status.REFRESHED) afterUpdate = null;
    }
    public void setLabel(String label) { requireEditable(); replace(label, geometry()); }
    public void setYRangeInclusive(int bottom, int top) {
        requireEditable();
        if (top == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
        replace(getGeometryLabel(), new KOMEPolygonPrism(geometry().getPolygon(), bottom, top + 1));
    }
    public void addVertex(int x, int z) {
        requireEditable(); List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(geometry().getPolygon().getVertices());
        KOMEXZPoint vertex = new KOMEXZPoint(x, z);
        if (points.contains(vertex)) throw new IllegalArgumentException("That corner is already a polygon vertex (" + x + ", " + z + "). The polygon closes automatically.");
        if (points.size() >= KOMETacticalEditWire.MAX_VERTICES) throw new IllegalArgumentException("Maximum 128 vertices.");
        points.add(vertex); polygon(points);
        message = "Vertex " + points.size() + " confirmed at " + x + ", " + z + ". Tab chooses the next block corner.";
    }
    /** The draft input itself is the preview, count and save source; no separate anchor list exists. */
    public List<KOMEXZPoint> getConfirmedVertices() {
        return getGeometry() == null ? java.util.Collections.emptyList() : getGeometry().getPolygon().getVertices();
    }
    public int getConfirmedVertexCount() { return getConfirmedVertices().size(); }
    public int getDistinctVertexCount() { return new java.util.HashSet<KOMEXZPoint>(getConfirmedVertices()).size(); }
    public List<KOMEXZPoint> getFillTriangles() {
        KOMEPolygon polygon = geometry().getPolygon();
        if (!polygon.equals(fillSource)) { fillSource = polygon; fillTriangles = KOMETacticalPolygonPreview.triangles(polygon); }
        return fillTriangles;
    }
    public KOMETacticalPolygonPreview.Corner getCorner() { return corner; }
    public void cycleCorner() { if (selection == Selection.VERTICES) corner = corner.next(); }
    public KOMEXZPoint aimedCorner(int x, int z) { return corner.point(x,z); }
    private void clearGeometryPreview() { corner = KOMETacticalPolygonPreview.Corner.NW; fillSource = null; fillTriangles = null; feedback = null; topologySource = null; topologyValidation = null; comparedComplex = null; comparedDraft = null; sameSnapshotDefinition = false; }
    public void undo() {
        requireEditable();
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(geometry().getPolygon().getVertices());
        if (!points.isEmpty()) points.remove(points.size() - 1); polygon(points);
    }
    public void clearVertices() { requireEditable(); polygon(new ArrayList<KOMEXZPoint>()); }
    private void polygon(List<KOMEXZPoint> points) {
        replace(getGeometryLabel(), new KOMEPolygonPrism(new KOMEPolygon(points), geometry().getMinYInclusive(), geometry().getMaxYExclusive()));
    }
    private void replace(String label, KOMEPolygonPrism prism) {
        if (localComplex != null) localComplex = KOMETacticalComplexDraft.edit(localComplex, zoneType, zoneId, label, prism);
        else local = new KOMEForceDeploymentArea(local.getAreaId(), local.getTileId(), local.getDimensionId(), label, prism, local.getRevision());
        feedback = null;
        message = "Unsaved draft. Validate or Save when finished.";
    }
    private void requireEditable() { if (!isEditing() || busy) throw new IllegalArgumentException("Wait for the editor response."); }
    private KOMEPolygonPrism geometry() { if (getGeometry() == null) throw new IllegalArgumentException("Select a zone first."); return getGeometry(); }
    public void select(Selection mode) {
        requireEditable(); if (mode != Selection.NONE && mode != Selection.TOPOLOGY) geometry();
        if (mode == Selection.TOPOLOGY && !isComplexEditing()) throw new IllegalArgumentException("Open a Siege Complex to view its Connections.");
        selection = mode;
        if (mode == Selection.VERTICES) message = "Aim at a block. Tab chooses NW/NE/SE/SW; right-click confirms a vertex. Backspace undoes; Delete clears.";
    }
    public void zoneType(ZoneType type) { requireEditable(); zoneType = type; zoneId = null; connectionId = null; inference = null; selection = Selection.NONE; feedback = null; }
    public void selectZone(String id) { requireEditable(); if (KOMETacticalComplexDraft.find(localComplex, zoneType, id) == null) throw new IllegalArgumentException("Unknown zone."); zoneId = id; feedback = null; }
    public void createZone(String id) { requireEditable(); localComplex = KOMETacticalComplexDraft.create(localComplex, zoneType, id); zoneId = id.trim(); message = "New unsaved zone."; feedback = null; }
    public void deleteZone() { requireEditable(); localComplex = KOMETacticalComplexDraft.remove(localComplex, zoneType, zoneId); feedback = null; zoneId = null; selection = Selection.NONE; message = "Zone removed from draft; references are retained for validation."; }
    public void toggleWallAccess(String normalId) { requireEditable(); localComplex = KOMETacticalComplexDraft.wallAccess(localComplex, zoneId, normalId); }
    public void selectConnection(String id) {
        requireEditable(); if (KOMETacticalConnectionDraft.find(localComplex, id) == null) throw new IllegalArgumentException("Unknown Connection.");
        connectionId = id; inference = null; selection = Selection.NONE; focusTransition();
    }
    public void createConnection(String id) {
        requireEditable(); localComplex = KOMETacticalConnectionDraft.create(localComplex, id);
        connectionId = id.trim(); inference = null; focusTransition(); message = "New unsaved Connection. Choose endpoints and Transition; gate is optional.";
    }
    private void focusTransition() { zoneType = ZoneType.TRANSITION; zoneId = getConnection().getTransitionZoneId(); feedback = null; }
    private void connection(KOMESiegeAreaRef a, KOMESiegeAreaRef b, String transition, KOMEDefensiveGateRef gate) {
        localComplex = KOMETacticalConnectionDraft.edit(localComplex, new KOMESiegeConnection(connectionId, a, b, transition, gate));
        inference = null; focusTransition(); message = "Unsaved Connection. Validate for geometry, gate status and readiness.";
    }
    public void setConnectionEndpoint(boolean first, KOMESiegeAreaRef endpoint) {
        requireEditable(); if (!endpointChoices().contains(endpoint)) throw new IllegalArgumentException("Select EXTERIOR or a Normal Segment from this complex.");
        KOMESiegeConnection c = getConnection(); connection(first ? endpoint : c.getEndpointA(), first ? c.getEndpointB() : endpoint,
            c.getTransitionZoneId(), c.getGateRef().orElse(null));
    }
    public void setConnectionTransition(String id) {
        requireEditable(); if (localComplex.findTransitionZone(id) == null) throw new IllegalArgumentException("Select a Transition from this complex.");
        KOMESiegeConnection c = getConnection(); connection(c.getEndpointA(), c.getEndpointB(), id, c.getGateRef().orElse(null));
    }
    public void setConnectionGate(KOMETacticalComplexCatalog.Row gate) {
        requireEditable();
        if (gate != null && (options == null || options.kind != KOMETacticalComplexCatalog.Kind.GATES || !options.rows.contains(gate)))
            throw new IllegalArgumentException("Select a gate from the server's assigned-Build list.");
        if (gate != null && !canSelectConnectionGate(gate)) throw new IllegalArgumentException(gateChoiceUsage(gate));
        KOMESiegeConnection c = getConnection(); connection(c.getEndpointA(), c.getEndpointB(), c.getTransitionZoneId(),
            gate == null ? null : new KOMEDefensiveGateRef(gate.relatedId, gate.id));
        if (gate != null) message += " " + gate.relatedId + " / " + gate.id + ": " + gate.detail;
    }
    public boolean canSelectConnectionGate(KOMETacticalComplexCatalog.Row gate) {
        KOMESiegeConnection c = getConnection();
        if (c == null || gate == null || gate.relatedId == null) return false;
        KOMEDefensiveGateRef ref = new KOMEDefensiveGateRef(gate.relatedId, gate.id);
        return KOMESiegeGateUsage.sameGate(c.getGateRef().orElse(null), ref)
            || KOMESiegeGateUsage.otherConnectionIds(localComplex, c.getId(), ref).isEmpty();
    }
    public String gateChoiceUsage(KOMETacticalComplexCatalog.Row gate) {
        KOMESiegeConnection c = getConnection(); if (c == null) return "Select a Connection first.";
        List<String> ids = KOMESiegeGateUsage.otherConnectionIds(localComplex, c.getId(), new KOMEDefensiveGateRef(gate.relatedId, gate.id));
        return ids.isEmpty() ? "Available for this Connection" : "Used by " + String.join(", ", ids)
            + (canSelectConnectionGate(gate) ? " (also authored here; repair duplicate usage)" : "; choose another gate or Gateless");
    }
    public void deleteConnection() {
        requireEditable(); localComplex = KOMETacticalConnectionDraft.remove(localComplex, connectionId);
        connectionId = null; inference = null; zoneId = null; feedback = null;
        message = "Connection removed from draft. Transition, Build and gate record retained.";
    }
    public void inferEndpoints() {
        requireEditable(); inference = KOMEConnectionEndpointInference.infer(localComplex, getConnection().getTransitionZoneId()); inferenceSource = localComplex;
        message = inference.isSuccessful() ? "Suggestion: " + inference.getEndpointA() + " <-> " + inference.getEndpointB() + ". Click Accept suggestion or choose endpoints manually."
            : inferenceMessage(inference.getStatus());
        if (inference.getStatus() == KOMEConnectionEndpointInference.Status.INVALID_NORMAL_GEOMETRY
                || inference.getStatus() == KOMEConnectionEndpointInference.Status.INVALID_TRANSITION_GEOMETRY) {
            for (KOMEValidationIssue issue : new KOMESiegeComplexValidator().validate(localComplex).getIssues())
                if (issue.getCode().name().startsWith("POLYGON_") || issue.getCode().name().startsWith("PRISM_")) message += " " + issue.getMessage() + " " + issue.getSubjectIds();
        }
    }
    public void acceptInference() {
        requireEditable(); KOMEConnectionEndpointInference.Result result = getInference();
        if (result == null || !result.isSuccessful()) throw new IllegalArgumentException("Infer again before accepting a suggestion.");
        KOMESiegeConnection c = getConnection(); connection(result.getEndpointA(), result.getEndpointB(), c.getTransitionZoneId(), c.getGateRef().orElse(null));
    }
    public void rejectInference() { inference = null; message = "Suggestion dismissed. Manual endpoints retained."; }
    private static String inferenceMessage(KOMEConnectionEndpointInference.Status status) {
        switch (status) {
            case TRANSITION_NOT_FOUND: return "Selected Transition no longer exists. Choose a Transition.";
            case INVALID_TRANSITION_GEOMETRY: return "Inference blocked: selected Transition geometry is malformed.";
            case INVALID_NORMAL_GEOMETRY: return "Inference blocked: potentially relevant Normal geometry is malformed.";
            case DUPLICATE_NORMAL_ID: return "Inference blocked: duplicate Normal IDs make endpoints ambiguous.";
            case NO_ENDPOINT_CANDIDATES: return "No endpoints found: Transition does not contact a Normal Segment.";
            case EXTERIOR_NOT_ESTABLISHED: return "No exterior established: Transition is contained inside its only Normal candidate.";
            case TOO_MANY_NORMAL_ENDPOINTS: return "Ambiguous: Transition contacts more than two Normal Segments.";
            default: return "Ambiguous corridor: Transition overlaps another Transition or Wall. Validate for details.";
        }
    }
    public List<String> connectionDiagnostics() {
        List<String> result = new ArrayList<>(); KOMESiegeConnection c = getConnection(); if (c == null) return result;
        for (KOMEValidationIssue issue : topologyValidation().getIssues())
            if (concerns(issue, c))
                result.add(issue.getCode() + ": " + issue.getMessage() + " " + issue.getSubjectIds());
        if (snapshot != null && snapshot.getPreflight() != null && definitionMatchesSnapshot())
            for (String diagnostic : snapshot.getPreflight().getDiagnostics()) if (diagnostic.contains("connection " + c.getId() + ",")
                || diagnostic.contains("connection " + c.getId() + ")")) result.add(diagnostic);
        return java.util.Collections.unmodifiableList(result);
    }
    public String connectionStatus() {
        return connectionStatus(connectionId);
    }
    public String connectionStatus(String id) {
        KOMESiegeConnection c = id == null || localComplex == null ? null : KOMETacticalConnectionDraft.find(localComplex, id); if (c == null) return "Select a Connection.";
        if (connectionCatalog != null && snapshot != null && connectionCatalog.revision == snapshot.getCurrentRevision()
                && snapshot.getDraft().getComplex() != null && c.equals(KOMETacticalConnectionDraft.find(snapshot.getDraft().getComplex(), c.getId())))
            for (KOMETacticalComplexCatalog.Row row : connectionCatalog.rows) if (row.id.equals(c.getId()) && row.revision == localComplex.getRevision()) return row.detail;
        return "Draft status: Validate for current gate diagnostics and readiness.";
    }
    public boolean connectionHasErrors(KOMESiegeConnection c) {
        for (KOMEValidationIssue issue : topologyValidation().getIssues()) if (concerns(issue, c)) return true;
        String status = connectionStatus(c.getId());
        if (c.isGated() && options != null && options.kind == KOMETacticalComplexCatalog.Kind.GATES
                && options.revision == snapshot.getCurrentRevision())
            for (KOMETacticalComplexCatalog.Row row : options.rows) if (row.id.equals(c.getGateRef().get().getGateRecordId())
                    && row.relatedId.equalsIgnoreCase(c.getGateRef().get().getBuildId())) status += " " + row.detail;
        if (snapshot.getPreflight() != null && definitionMatchesSnapshot())
            for (String diagnostic : snapshot.getPreflight().getDiagnostics()) if (diagnostic.contains("connection " + c.getId() + ",")
                    || diagnostic.contains("connection " + c.getId() + ")")) status += " " + diagnostic;
        return status.contains("BUILD_") || status.contains("GATE_RECORD_MISSING") || status.contains("PHYSICAL_BINDING_");
    }
    private static boolean concerns(KOMEValidationIssue issue, KOMESiegeConnection c) {
        return issue.getSubjectIds().contains(c.getId()) || issue.getSubjectIds().contains(c.getTransitionZoneId())
            || c.getEndpointA().isNormal() && issue.getSubjectIds().contains(c.getEndpointA().getNormalSegmentId())
            || c.getEndpointB().isNormal() && issue.getSubjectIds().contains(c.getEndpointB().getNormalSegmentId());
    }
    private KOMEValidationResult topologyValidation() {
        if (topologySource != localComplex) { topologySource = localComplex; topologyValidation = new KOMESiegeComplexValidator().validate(localComplex); }
        return topologyValidation;
    }
    private boolean definitionMatchesSnapshot() {
        if (snapshot == null || localComplex == null) return false;
        if (comparedComplex != localComplex || comparedDraft != snapshot.getDraft()) {
            comparedComplex = localComplex; comparedDraft = snapshot.getDraft();
            sameSnapshotDefinition = new KOMETacticalEditDraft(localComplex).sameDefinition(comparedDraft);
        }
        return sameSnapshotDefinition;
    }
    public static final class ConnectionOverlay {
        public final KOMESiegeConnection connection;
        public final KOMEPolygonPrism transition, normalA, normalB;
        public final boolean selected, invalid;
        private ConnectionOverlay(KOMESiegeConnection c, KOMEPolygonPrism transition, KOMEPolygonPrism a,
                KOMEPolygonPrism b, boolean selected, boolean invalid) {
            this.connection = c; this.transition = transition; normalA = a; normalB = b; this.selected = selected; this.invalid = invalid;
        }
    }
    /** Context cues only: EXTERIOR is labelled, never resolved as geometry or a tile area. */
    public List<ConnectionOverlay> connectionOverlays(UUID player, int dimension) {
        List<ConnectionOverlay> result = new ArrayList<>();
        if (!hasSession(player, dimension) || localComplex == null) return java.util.Collections.emptyList();
        for (KOMESiegeConnection c : getConnections()) {
            boolean invalid = connectionHasErrors(c);
            KOMETransitionZone transition = localComplex.findTransitionZone(c.getTransitionZoneId());
            KOMENormalSegment a = c.getEndpointA().isNormal() ? localComplex.findNormalSegment(c.getEndpointA().getNormalSegmentId()) : null;
            KOMENormalSegment b = c.getEndpointB().isNormal() ? localComplex.findNormalSegment(c.getEndpointB().getNormalSegmentId()) : null;
            result.add(new ConnectionOverlay(c, transition == null ? null : transition.getPrism(), a == null ? null : a.getPrism(),
                b == null ? null : b.getPrism(), c.getId().equals(connectionId), invalid));
        }
        return java.util.Collections.unmodifiableList(result);
    }
    public void setPreferredArea(String id) {
        requireEditable();
        if (id != null && (options == null || options.kind != KOMETacticalComplexCatalog.Kind.PREFERRED_AREAS
                || options.rows.stream().noneMatch(row -> row.id.equals(id)))) throw new IllegalArgumentException("Select a tile-area choice from the server list.");
        localComplex = KOMETacticalComplexDraft.preferred(localComplex, id); message = "Preferred staging area changed in draft.";
    }
    public void membership(KOMETacticalComplexCatalog.Row build, KOMETacticalEditDraft.MembershipAction action) {
        requireEditable();
        if (creating || !new KOMETacticalEditDraft(localComplex).sameDefinition(snapshot.getDraft()))
            throw new IllegalArgumentException("Save or cancel definition edits before changing membership.");
        if (options == null || options.kind != KOMETacticalComplexCatalog.Kind.BUILDS || !options.rows.contains(build))
            throw new IllegalArgumentException("Refresh the Build list first.");
        KOMETacticalEditDraft intent = new KOMETacticalEditDraft(localComplex).withMembership(action, build.id,
            action == KOMETacticalEditDraft.MembershipAction.REASSIGN ? build.relatedId : null);
        afterUpdate = KOMETacticalEditRequest.Action.SAVE; busy = true;
        transport.accept(KOMETacticalEditRequest.update(snapshot, intent));
    }
    public boolean consumesClicks(UUID player, int dimension) {
        return selection != Selection.NONE && selection != Selection.TOPOLOGY && isEditing() && snapshot.getPlayerId().equals(player) && snapshot.getScope().getDimensionId() == dimension;
    }
    public void worldPoint(int x, int y, int z) {
        if (busy || !isEditing()) return;
        switch (selection) {
            case VERTICES:
                KOMEXZPoint vertex = aimedCorner(x,z); addVertex(vertex.getX(),vertex.getZ()); break;
            case LOWER_Y: replace(getGeometryLabel(), new KOMEPolygonPrism(geometry().getPolygon(), y, geometry().getMaxYExclusive())); break;
            case UPPER_Y:
                if (y == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
                replace(getGeometryLabel(), new KOMEPolygonPrism(geometry().getPolygon(), geometry().getMinYInclusive(), y + 1)); break;
            default: return;
        }
        // Local input is activity, not a persistent edit. Preserve local geometry on the refresh reply.
        long now = System.nanoTime();
        if (now - lastWorldRefresh >= 1000000000L) {
            lastWorldRefresh = now;
            transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.REFRESH, snapshot));
        }
    }
    public KOMEForceDeploymentArea overlay(UUID player, int dimension) {
        return hasSession(player, dimension) ? local : null;
    }
    public boolean hasSession(UUID player, int dimension) { return isEditing() && snapshot.getPlayerId().equals(player) && snapshot.getScope().getDimensionId() == dimension; }
    public static final class Overlay {
        public final KOMEPolygonPrism prism; public final ZoneType type; public final boolean selected, invalid;
        Overlay(KOMEPolygonPrism prism, ZoneType type, boolean selected, boolean invalid) { this.prism = prism; this.type = type; this.selected = selected; this.invalid = invalid; }
    }
    /** Cached read-only feedback from the SAME validator used by server preflight, not a second rule set. */
    public GeometryFeedback geometryFeedback() {
        if (feedback != null) return feedback;
        List<String> messages = new ArrayList<String>(); java.util.Set<String> conflicts = new java.util.TreeSet<String>();
        if (getGeometry() != null) {
            KOMEValidationResult validation = localComplex == null ? new KOMEForceDeploymentAreaValidator().validate(local)
                : new KOMESiegeComplexValidator().validate(localComplex);
            for (KOMEValidationIssue issue : validation.getIssues()) {
                String code = issue.getCode().name();
                boolean relation = code.endsWith("_OVERLAP") || code.equals("CONNECTION_TRANSITION_MISSES_ENDPOINT")
                    || code.equals("CONNECTION_TRANSITION_THIRD_NORMAL") || code.equals("CONNECTION_EXTERIOR_NOT_ESTABLISHED");
                if (!(code.startsWith("POLYGON_") || code.startsWith("PRISM_") || relation)) continue;
                if (localComplex != null && !issue.getSubjectIds().contains(zoneId)) continue;
                messages.add(code + ": " + issue.getMessage() + " " + issue.getSubjectIds());
                if (relation) conflicts.addAll(issue.getSubjectIds());
            }
        }
        return feedback = new GeometryFeedback(messages, conflicts);
    }
    public static final class GeometryFeedback {
        public final List<String> messages; public final java.util.Set<String> conflictingZoneIds;
        private GeometryFeedback(List<String> messages, java.util.Set<String> ids) {
            this.messages = java.util.Collections.unmodifiableList(messages);
            conflictingZoneIds = java.util.Collections.unmodifiableSet(ids);
        }
        public boolean isInvalid() { return !messages.isEmpty(); }
    }
    public List<Overlay> overlays(UUID player, int dimension) {
        List<Overlay> result = new ArrayList<Overlay>();
        if (hasSession(player, dimension)) {
            GeometryFeedback validation = geometryFeedback();
            if (local != null) result.add(new Overlay(local.getPrism(), null, true, validation.isInvalid()));
            else for (ZoneType kind : ZoneType.values()) for (KOMESiegeZone zone : KOMETacticalComplexDraft.zones(localComplex, kind)) {
                KOMESiegeConnection c = getConnection();
                boolean selected = kind == zoneType && zone.getId().equals(zoneId) || c != null && kind == ZoneType.NORMAL
                    && (c.getEndpointA().equals(KOMESiegeAreaRef.normal(zone.getId())) || c.getEndpointB().equals(KOMESiegeAreaRef.normal(zone.getId())));
                result.add(new Overlay(zone.getPrism(), kind, selected, selected && validation.isInvalid() || validation.conflictingZoneIds.contains(zone.getId())));
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }
    public void validate() { updateThen(KOMETacticalEditRequest.Action.PREFLIGHT); }
    public void save() {
        selection = Selection.NONE; updateThen(KOMETacticalEditRequest.Action.SAVE);
    }
    private void updateThen(KOMETacticalEditRequest.Action action) {
        requireEditable(); afterUpdate = action; busy = true;
        try { transport.accept(KOMETacticalEditRequest.update(snapshot, localComplex == null ? new KOMETacticalEditDraft(local) : new KOMETacticalEditDraft(localComplex))); }
        catch (RuntimeException invalid) { busy = false; afterUpdate = null; throw invalid; }
    }
    public void delete() { if (creating) throw new IllegalArgumentException("Cancel a new area instead."); selection = Selection.NONE; send(KOMETacticalEditRequest.Action.DELETE); }
    private void send(KOMETacticalEditRequest.Action action) { requireEditable(); busy = true; transport.accept(KOMETacticalEditRequest.action(action, snapshot)); }
    public void cancel() {
        if (snapshot != null && !snapshot.isClosed()) {
            cancelled = snapshot.getToken();
            transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, snapshot));
        }
        clearDraft(); busy = false; afterUpdate = null; message = "Draft cancelled.";
    }
    private void clearDraft() { selection = Selection.NONE; snapshot = null; local = null; localComplex = null; zoneId = null; connectionId = null; inference = null; connectionCatalog = null; options = null; creating = false; clearGeometryPreview(); }
    public void reset() { clearDraft(); catalog = null; complexCatalog = null; pendingKind = null; complexBrowser = false; busy = false; waitingCatalog = false; afterUpdate = null; cancelled = null; message = ""; }
    public void error(String text) { message = text; }
    public static String statusMessage(KOMETacticalEditSessionManager.Status status) {
        switch (status) {
            case STALE_STORE: case STALE_OBJECT: return "Another admin changed this configuration. Cancel, refresh and reopen; your draft was kept.";
            case STALE_SEQUENCE: return "A newer draft exists. Retry after the current response.";
            case DUPLICATE_ID: return "That ID already exists. Choose another ID.";
            case DENIED: return "Creative or operator level 2 is required.";
            case EXPIRED: return "Editor session expired. Reopen the area.";
            case SAVED: return "Saved.";
            case DELETED: return "Deleted.";
            case NO_CHANGE: return "No changes to save.";
            case CANCELLED: return "Draft cancelled.";
            case SESSION_ACTIVE: return "Cancel the current editing session first.";
            case INVALID_SESSION: return "Session closed. Reopen the area.";
            case WRONG_DIMENSION: return "Open this tile's editor in its map dimension.";
            case LIMIT_REACHED: return "Editor capacity reached. Wait briefly and reopen.";
            case RATE_LIMITED: return "Too many requests. Wait briefly and retry.";
            case INVALID_DRAFT: return "Draft rejected. Check the area ID, fields and size limits.";
            case REJECTED: case COMMIT_FAILED: return "Request failed. No change was published; refresh and retry.";
            default: return "Unsaved draft.";
        }
    }
}
