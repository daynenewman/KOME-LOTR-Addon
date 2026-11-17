package kome.client.tactical;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiScreen;
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
    private KOMETacticalEditDraft openedSource;
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
    public static final long KEEPALIVE_INTERVAL_TICKS = 20L * 60L;
    public static final long RECOVERY_WAIT_TICKS = 20L * 30L;
    private long clientTicks, lastSessionActivityTick;
    private boolean keepalivePending;
    private String beforeKeepaliveMessage;
    private long catalogueVersion;
    private GeometryFeedback feedback;
    private KOMETacticalPolygonPreview.Corner corner = KOMETacticalPolygonPreview.Corner.NW;
    private KOMEPolygon fillSource;
    private List<KOMEXZPoint> fillTriangles;
    private static final int NEW_DRAFT_MIN_Y = 64, NEW_DRAFT_MAX_Y = 70;
    private KOMESiegeComplex savedComplex;
    private boolean reopeningAfterSave, returningToAreaList, returningToComplexList;
    private UUID savingToken;
    private boolean retainedDraft, recoveryPending, recoveryClosing, recoveryDisconnected;
    private boolean recoveryRetryAfterClose, recoveryPriorClosed;
    private KOMETacticalEditScope retainedScope;
    private KOMETacticalEditDraft retainedSource;
    private UUID retainedPlayer, retainedToken, recoveryClosingToken;
    private long retainedGeneration, retainedBaseRevision, retainedObjectRevision;
    private long recoveryStartedTick;
    private boolean retainedCreating, retainedGeometryView;
    private UnappliedFields unappliedFields;
    private final Map<String, UnappliedFields> unappliedByGeometry = new HashMap<String, UnappliedFields>();
    private GuiScreen worldViewOrigin;
    private UUID worldViewToken, worldViewPlayer;
    private KOMETacticalEditScope worldViewScope;
    private boolean worldViewInvalidated, worldViewWholeComplex, worldPolygonSelected;
    private final Map<String, Deque<KOMEPolygonPrism>> geometryHistory = new HashMap<String, Deque<KOMEPolygonPrism>>();
    private int selectedVertex = -1, heldVertex = -1;
    private Selection worldHeightMode = Selection.NONE;
    private boolean worldVertexPlacementActive;
    private static final int MAX_GEOMETRY_HISTORY = 10;
    private String geometryHistoryKey() {
        return localComplex == null ? local == null ? null : "area:" + local.getAreaId()
            : zoneId == null ? null : "zone:" + zoneType.name() + ":" + zoneId;
    }
    private Deque<KOMEPolygonPrism> currentGeometryHistory(boolean create) {
        String key = geometryHistoryKey();
        if (key == null) return null;
        Deque<KOMEPolygonPrism> steps = geometryHistory.get(key);
        if (steps == null && create) { steps = new ArrayDeque<KOMEPolygonPrism>(); geometryHistory.put(key, steps); }
        return steps;
    }
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
    /** Counts belong to the authorized definition at OPEN, never the changing local draft. */
    public int savedZoneCount(ZoneType type) { return savedComplex == null ? 0 : KOMETacticalComplexDraft.zones(savedComplex, type).size(); }
    public int savedConnectionCount() { return savedComplex == null ? 0 : savedComplex.getConnections().size(); }
    private void initializeNewAreaY() {
        if (local == null) return;
        KOMEPolygonPrism prism = local.getPrism();
        if (!prism.getPolygon().getVertices().isEmpty() || prism.getMinYInclusive() != 0 || prism.getMaxYExclusive() != 1) return;
        replace(getGeometryLabel(), new KOMEPolygonPrism(prism.getPolygon(), NEW_DRAFT_MIN_Y, NEW_DRAFT_MAX_Y + 1));
    }
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
    public boolean isBusy() { return busy || keepalivePending; }
    public boolean isKeepalivePending() { return keepalivePending; }
    public boolean hasRetainedDraft() { return retainedDraft; }
    public boolean canRecoverRetainedDraft() { return retainedDraft && !recoveryDisconnected && catalog != null
        && retainedScope != null && catalog.tileId.equals(retainedScope.getTileId()); }
    public KOMETacticalEditScope getRetainedScope() { return retainedScope; }
    public long getRetainedBaseRevision() { return retainedBaseRevision; }
    public long getRetainedObjectRevision() { return retainedObjectRevision; }
    public boolean isRecoveringDraft() { return recoveryPending || recoveryClosing; }
    public boolean isRetainedGeometryView() { return retainedDraft && retainedGeometryView; }
    /** Verbatim GUI input is separate from the detached geometry until the usual fields() action succeeds. */
    public static final class UnappliedFields {
        public final String label, bottom, top;
        public final String baseLabel, baseBottom, baseTop;
        private final ZoneType zoneType;
        private final String zoneId;
        private UnappliedFields(String label, String bottom, String top,
                String baseLabel, String baseBottom, String baseTop, ZoneType zoneType, String zoneId) {
            this.label = label; this.bottom = bottom; this.top = top;
            this.baseLabel = baseLabel; this.baseBottom = baseBottom; this.baseTop = baseTop;
            this.zoneType = zoneType; this.zoneId = zoneId;
        }
    }
    public UnappliedFields getUnappliedFields() { return unappliedFields; }
    public UnappliedFields getCurrentUnappliedFields() { return unappliedByGeometry.get(geometryHistoryKey()); }
    public void rememberUnappliedFields(String label, String bottom, String top,
            String baseLabel, String baseBottom, String baseTop, ZoneType fieldType, String fieldZoneId) {
        if ((!retainedDraft && !isEditing()) || local == null && localComplex == null
                || localComplex != null && (fieldType != zoneType || !java.util.Objects.equals(fieldZoneId, zoneId))) return;
        unappliedFields = new UnappliedFields(label, bottom, top, baseLabel, baseBottom, baseTop,
            localComplex == null ? null : fieldType, fieldZoneId);
        unappliedByGeometry.put(geometryHistoryKey(), unappliedFields);
    }
    public boolean unappliedFieldsMatchCurrentGeometry() {
        return getCurrentUnappliedFields() != null;
    }
    public void appliedUnappliedFields() {
        UnappliedFields current = unappliedByGeometry.remove(geometryHistoryKey());
        if (unappliedFields == current) unappliedFields = null;
    }
    public List<String> retainedInspectionLines() {
        List<String> lines = new ArrayList<String>();
        if (!retainedDraft) return lines;
        lines.add((localComplex == null ? "Area " : "Complex ") + getTargetId() + " | tile " + retainedScope.getTileId()
            + " | dimension " + retainedScope.getDimensionId());
        lines.add("Source store rev " + retainedBaseRevision + " | object rev " + retainedObjectRevision);
        if (unappliedFields != null) {
            if (unappliedFields.label != null) lines.add("Typed label text: " + unappliedFields.label);
            if (unappliedFields.bottom != null) lines.add("Typed Min Y text: " + unappliedFields.bottom);
            if (unappliedFields.top != null) lines.add("Typed Max Y text: " + unappliedFields.top);
            lines.add("Text above is unapplied; correct it after authorized recovery.");
        }
        for (Map.Entry<String, UnappliedFields> entry : new java.util.TreeMap<String, UnappliedFields>(unappliedByGeometry).entrySet()) {
            if (entry.getValue() == unappliedFields) continue;
            if (entry.getValue().label != null) lines.add("Unapplied " + entry.getKey() + " label: " + entry.getValue().label);
            if (entry.getValue().bottom != null) lines.add("Unapplied " + entry.getKey() + " Min Y: " + entry.getValue().bottom);
            if (entry.getValue().top != null) lines.add("Unapplied " + entry.getKey() + " Max Y: " + entry.getValue().top);
        }
        if (local != null) appendInspection(lines, "Area", local.getAreaId(), local.getLabel(), local.getPrism());
        else for (ZoneType type : ZoneType.values()) for (KOMESiegeZone zone : KOMETacticalComplexDraft.zones(localComplex, type))
            appendInspection(lines, type.name(), zone.getId(), zone.getLabel(), zone.getPrism());
        if (localComplex != null && lines.size() == 2) lines.add("No zones in this unsaved complex draft.");
        return java.util.Collections.unmodifiableList(lines);
    }
    private static void appendInspection(List<String> lines, String kind, String id, String label, KOMEPolygonPrism prism) {
        lines.add(kind + " " + id + " | " + label + " | Y " + prism.getMinYInclusive() + ".." + ((long) prism.getMaxYExclusive() - 1L));
        List<KOMEXZPoint> points = prism.getPolygon().getVertices();
        if (points.isEmpty()) lines.add("  No polygon vertices");
        for (int i = 0; i < points.size(); ++i) lines.add("  " + (i + 1) + ": X " + points.get(i).getX() + ", Z " + points.get(i).getZ());
    }
    /** Called once per END client tick by the existing tactical input handler. */
    public void tickSession(UUID player, int dimension) {
        if (clientTicks < Long.MAX_VALUE) ++clientTicks;
        if (retainedDraft && (recoveryPending || recoveryClosing)
                && clientTicks - recoveryStartedTick >= RECOVERY_WAIT_TICKS) {
            recoveryPending = recoveryClosing = recoveryRetryAfterClose = false;
            recoveryClosingToken = null; busy = false;
            message = "Recovery response timed out. Unsaved local draft kept; retry when ready.";
        }
        if (!hasSession(player, dimension) || busy || keepalivePending || retainedDraft || reopeningAfterSave
                || clientTicks - lastSessionActivityTick < KEEPALIVE_INTERVAL_TICKS) return;
        keepalivePending = true;
        beforeKeepaliveMessage = message; message = "Refreshing editor session...";
        try { transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.REFRESH, snapshot)); }
        catch (RuntimeException failure) { keepalivePending = false; beforeKeepaliveMessage = null; lastSessionActivityTick = clientTicks;
            message = "Session refresh could not be sent; retrying in about a minute."; }
    }
    /** The server must issue a new token and unchanged source before any retained local draft is editable. */
    public void recoverRetainedDraft(UUID player, int dimension) {
        if (!retainedDraft || busy || recoveryPending || recoveryClosing || recoveryDisconnected || retainedScope == null
                || !retainedPlayer.equals(player) || retainedScope.getDimensionId() != dimension
                || catalog == null || !catalog.tileId.equals(retainedScope.getTileId()))
            throw new IllegalArgumentException("Reconnect to the same tile and player before recovering this local draft.");
        recoveryPending = true; busy = true; recoveryStartedTick = clientTicks;
        message = "Requesting a new authorized session for the unsaved local draft...";
        requestRecoveryOpen();
    }
    private void requestRecoveryOpen() {
        try { transport.accept(retainedCreating ? KOMETacticalEditRequest.create(retainedScope) : KOMETacticalEditRequest.open(retainedScope)); }
        catch (RuntimeException failure) { recoveryPending = false; busy = false; message = "Recovery request could not be sent; local draft kept. Retry when ready."; }
    }
    public void discardRetainedDraft() {
        if (!retainedDraft || busy || recoveryPending || recoveryClosing) throw new IllegalArgumentException("Wait for recovery to finish before discarding.");
        clearDraft(); message = "Unsaved local draft discarded.";
    }
    private void retainUnexpectedClose(KOMETacticalEditSnapshot source, KOMETacticalEditSessionManager.Status status) {
        if (source == null || source.isClosed() || local == null && localComplex == null) return;
        retainedDraft = true; recoveryDisconnected = recoveryPriorClosed = false; retainedScope = source.getScope();
        retainedSource = openedSource == null ? source.getDraft() : openedSource;
        retainedPlayer = source.getPlayerId(); retainedToken = source.getToken(); retainedGeneration = source.getGeneration();
        retainedBaseRevision = source.getBaseRevision(); retainedObjectRevision = source.getBaseObjectRevision();
        retainedCreating = creating; retainedGeometryView = hasWorldViewContext() && !worldViewWholeComplex && zoneId != null;
        cancelled = retainedToken;
        snapshot = null; selection = Selection.NONE; busy = false; keepalivePending = false;
        beforeKeepaliveMessage = null;
        afterUpdate = null; savingToken = null; reopeningAfterSave = false;
        if (hasWorldViewContext()) worldViewInvalidated = true;
        message = statusMessage(status) + " Unsaved local draft retained for inspection. Recover or Discard it.";
    }
    private void acceptRecovery(KOMETacticalEditSnapshot value, KOMETacticalEditSessionManager.Status status) {
        if (recoveryClosing) {
            if (value == null) {
                recoveryClosing = recoveryRetryAfterClose = false; recoveryClosingToken = null; busy = false;
                message = "Could not close the prior session: " + statusMessage(status) + " Local draft kept. Retry recovery later.";
                return;
            }
            if (!value.getToken().equals(recoveryClosingToken) || !value.getScope().equals(retainedScope)
                    || !value.getPlayerId().equals(retainedPlayer)) return;
            if (!value.isClosed() || status != KOMETacticalEditSessionManager.Status.CANCELLED
                    && status != KOMETacticalEditSessionManager.Status.EXPIRED) {
                if (status != KOMETacticalEditSessionManager.Status.OPENED
                        && status != KOMETacticalEditSessionManager.Status.REFRESHED
                        && status != KOMETacticalEditSessionManager.Status.UPDATED
                        && status != KOMETacticalEditSessionManager.Status.VALIDATED) {
                    recoveryClosing = recoveryRetryAfterClose = false; recoveryClosingToken = null; busy = false;
                    message = "Could not close the prior session: " + statusMessage(status) + " Local draft kept. Retry recovery later.";
                }
                return; // Ignore delayed OPENED/REFRESHED replies while waiting for close acknowledgment.
            }
            boolean retry = recoveryRetryAfterClose;
            recoveryClosing = recoveryRetryAfterClose = false; recoveryClosingToken = null;
            if (retry) { recoveryPriorClosed = true; recoveryPending = true; busy = true; recoveryStartedTick = clientTicks;
                message = "Prior session closed. Requesting fresh authority..."; requestRecoveryOpen(); }
            else busy = false;
            return;
        }
        if (!recoveryPending) return; // Late publications cannot replace or discard retained work.
        if (value != null && (value.getToken().equals(retainedToken) || value.getGeneration() <= retainedGeneration)) {
            if (recoveryPriorClosed && value.getToken().equals(retainedToken)) return;
            if (status == KOMETacticalEditSessionManager.Status.OPENED) {
                recoveryPending = false;
                if (value.getToken().equals(retainedToken) && value.getGeneration() == retainedGeneration
                        && value.getScope().equals(retainedScope) && value.getPlayerId().equals(retainedPlayer)
                        && !value.isClosed()) {
                    message = "Prior server session is still open. Closing it before requesting fresh authority...";
                    closeRecoverySession(value, true);
                } else { busy = false; message = "Recovery returned an unexpected session. Local draft kept; retry recovery later."; }
            } else if (value.isClosed() && (status == KOMETacticalEditSessionManager.Status.EXPIRED
                    || status == KOMETacticalEditSessionManager.Status.CANCELLED)) {
                recoveryPending = false; busy = false;
                message = "Prior session closed. Local draft kept; retry recovery for a fresh session.";
            }
            return;
        }
        recoveryPending = false; busy = false;
        if (value == null || status != KOMETacticalEditSessionManager.Status.OPENED || value.isClosed()) {
            message = "Recovery refused: " + statusMessage(status) + " Unsaved local draft kept."; return;
        }
        boolean identity = value.getScope().equals(retainedScope) && value.getPlayerId().equals(retainedPlayer);
        boolean unchanged = identity && value.getBaseRevision() == retainedBaseRevision
            && value.getBaseObjectRevision() == retainedObjectRevision && value.getDraft().sameDefinition(retainedSource);
        if (!unchanged) {
            message = "Server geometry or revision changed. Unsaved local draft kept; it was not reapplied.";
            if (identity) closeRecoverySession(value, false);
            return;
        }
        snapshot = value; creating = retainedCreating; savedComplex = creating ? null : value.getDraft().getComplex();
        openedSource = value.getDraft();
        retainedDraft = false; retainedGeometryView = false; retainedScope = null; retainedSource = null; retainedPlayer = retainedToken = null;
        recoveryClosingToken = null; cancelled = null; keepalivePending = false;
        lastSessionActivityTick = clientTicks; clearGeometryPreview(); ++catalogueVersion;
        message = "Authorized session restored. Unsaved local draft is ready to Validate or Save.";
    }
    private void closeRecoverySession(KOMETacticalEditSnapshot value, boolean retryAfterClose) {
        recoveryClosing = true; recoveryRetryAfterClose = retryAfterClose; recoveryClosingToken = value.getToken();
        busy = true; recoveryStartedTick = clientTicks;
        try { transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, value)); }
        catch (RuntimeException failure) {
            recoveryClosing = recoveryRetryAfterClose = false; recoveryClosingToken = null; busy = false;
            message = "Could not send session close. Local draft kept; retry recovery later.";
        }
    }
    public boolean isReopeningAfterSave() { return reopeningAfterSave; }
    /** The world viewer borrows an existing server session; it never creates editing authority. */
    public void beginWorldView(GuiScreen origin, boolean wholeComplex) {
        beginWorldView(origin, wholeComplex, false);
    }
    /** Explicit authoring starts with a candidate corner; ordinary GUI Escape opens a passive view. */
    public void beginWorldView(GuiScreen origin, boolean wholeComplex, boolean editVertices) {
        requireEditable();
        if (origin == null || selection != Selection.NONE) throw new IllegalArgumentException("Return from world selection before opening Geometry View.");
        worldViewOrigin = origin; worldViewToken = snapshot.getToken(); worldViewScope = snapshot.getScope();
        worldViewPlayer = snapshot.getPlayerId(); worldViewInvalidated = false; worldViewWholeComplex = wholeComplex;
        selectedVertex = heldVertex = -1; worldHeightMode = Selection.NONE;
        worldPolygonSelected = !wholeComplex && getGeometry() != null;
        worldVertexPlacementActive = editVertices && worldPolygonSelected;
        message = worldVertexPlacementActive ? "Vertex placement ready. Right-click adds a corner; left-click a vertex to move it."
            : "Geometry View. Shift+right-click a polygon to edit vertices; Esc returns to the editor.";
    }
    public boolean hasWorldViewContext() { return worldViewOrigin != null; }
    public boolean isViewingGeometry() {
        return worldViewOrigin != null && !worldViewInvalidated && !busy && isEditing()
            && worldViewToken.equals(snapshot.getToken()) && worldViewScope.equals(snapshot.getScope());
    }
    public boolean worldViewMatches(UUID player, int dimension) {
        return hasWorldViewContext() && worldViewPlayer.equals(player) && worldViewScope.getDimensionId() == dimension;
    }
    public boolean isWorldViewWholeComplex() { return isViewingGeometry() && worldViewWholeComplex; }
    public boolean canEditWorldGeometry(UUID player, int dimension) {
        return isViewingGeometry() && !isBusy() && worldViewMatches(player, dimension)
            && worldPolygonSelected && getGeometry() != null;
    }
    private void requireWorldEditable() {
        if (!isViewingGeometry() || isBusy() || !worldPolygonSelected || getGeometry() == null)
            throw new IllegalArgumentException("Select a polygon in an active editor session first.");
    }
    /** Only geometry already present in this authorized draft can be focused. Ties use type and ID order. */
    public boolean selectWorldPolygon(UUID player, int dimension, int x, int y, int z) {
        if (!isViewingGeometry() || isBusy() || !worldViewMatches(player, dimension)) return false;
        Overlay best = null;
        for (Overlay candidate : overlays(player, dimension)) {
            if (candidate.prism.classify(x, y, z) == KOMEPointClassification.OUTSIDE) continue;
            if (best == null || compareWorldPick(candidate, best) < 0) best = candidate;
        }
        if (best == null) { message = "No polygon at the targeted block."; return false; }
        focusWorldOverlay(best);
        return true;
    }
    /** Prefer the closest authored prism surface along the editor look ray. */
    public boolean selectWorldPolygonRay(UUID player, int dimension, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        return selectWorldPolygonRay(player, dimension, ox, oy, oz, dx, dy, dz, reach, false);
    }
    public boolean selectOtherWorldPolygonRay(UUID player, int dimension, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        return selectWorldPolygonRay(player, dimension, ox, oy, oz, dx, dy, dz, reach, true);
    }
    private boolean selectWorldPolygonRay(UUID player, int dimension, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach, boolean otherOnly) {
        if (!isViewingGeometry() || isBusy() || !worldViewMatches(player, dimension)) return false;
        Overlay best = null, current = null; double nearest = Double.POSITIVE_INFINITY;
        for (Overlay candidate : overlays(player, dimension)) {
            double distance = KOMETacticalGeometryPicker.distance(candidate.prism, ox, oy, oz, dx, dy, dz, reach);
            if (otherOnly && candidate.selected) { if (Double.isFinite(distance)) current = candidate; continue; }
            if (distance < nearest - 1.0E-7D || Math.abs(distance - nearest) <= 1.0E-7D
                    && best != null && compareWorldPick(candidate, best) < 0) { best = candidate; nearest = distance; }
        }
        if (best == null && otherOnly) best = current;
        if (best == null) return false;
        focusWorldOverlay(best);
        return true;
    }
    private void focusWorldOverlay(Overlay best) {
        if (localComplex != null) { zoneType = best.type; zoneId = best.id; }
        worldViewWholeComplex = false; worldPolygonSelected = true; worldVertexPlacementActive = true;
        selectedVertex = heldVertex = -1; worldHeightMode = Selection.NONE; feedback = null;
        message = "Selected " + best.id + ". Left-click a highlighted vertex to move it; right-click adds a corner.";
    }
    private static int compareWorldPick(Overlay a, Overlay b) {
        long ay = (long) a.prism.getMaxYExclusive() - a.prism.getMinYInclusive();
        long by = (long) b.prism.getMaxYExclusive() - b.prism.getMinYInclusive();
        int c = Long.compare(ay, by);
        if (c != 0) return c;
        c = a.type == b.type ? 0 : a.type == null ? -1 : b.type == null ? 1 : Integer.compare(a.type.ordinal(), b.type.ordinal());
        return c != 0 ? c : a.id.compareTo(b.id);
    }
    public int getSelectedVertex() { return selectedVertex; }
    public int getHeldVertex() { return heldVertex; }
    public boolean isWorldPolygonSelected() { return isViewingGeometry() && worldPolygonSelected; }
    public boolean isWorldVertexPlacementActive() { return isWorldPolygonSelected() && worldVertexPlacementActive; }
    public Selection getWorldHeightMode() { return worldHeightMode; }
    public void toggleWorldHeight(Selection mode) {
        requireWorldEditable();
        if (mode != Selection.LOWER_Y && mode != Selection.UPPER_Y) throw new IllegalArgumentException("Choose top or bottom Y.");
        if (heldVertex >= 0) throw new IllegalArgumentException("Place the vertex or press Esc before picking a height.");
        worldHeightMode = worldHeightMode == mode ? Selection.NONE : mode;
        selectedVertex = heldVertex = -1;
        message = worldHeightMode == Selection.NONE ? "Height picking off." : "Aim at a block; right-click applies its "
            + (mode == Selection.UPPER_Y ? "top" : "bottom") + " Y. Press the shortcut again to exit.";
    }
    public int hoveredVertex(int blockX, int blockZ) {
        if (getGeometry() == null) return -1;
        List<KOMEXZPoint> points = getConfirmedVertices();
        int best = -1;
        for (int i = 0; i < points.size(); i++) {
            KOMEXZPoint p = points.get(i);
            if (p.getX() == blockX || blockX != Integer.MAX_VALUE && p.getX() == blockX + 1)
                if (p.getZ() == blockZ || blockZ != Integer.MAX_VALUE && p.getZ() == blockZ + 1)
                    if (best < 0) best = i;
        }
        return best;
    }
    public int hoveredVertexRay(double ox, double oy, double oz, double dx, double dy, double dz, double reach) {
        return isWorldPolygonSelected() ? KOMETacticalGeometryPicker.vertexIndex(getGeometry(), ox, oy, oz, dx, dy, dz, reach) : -1;
    }
    public void pickUpVertex(int index) {
        requireWorldEditable();
        if (worldHeightMode != Selection.NONE || heldVertex >= 0 || index < 0 || index >= getConfirmedVertexCount()) return;
        selectedVertex = heldVertex = index;
        message = "Moving vertex " + (index + 1) + ". Tab chooses a corner; right-click places it. Esc cancels.";
    }
    public void cancelHeldVertex() { heldVertex = -1; selectedVertex = -1; }
    public void placeHeldVertex(int x, int z) {
        requireWorldEditable();
        if (heldVertex < 0) return;
        KOMEXZPoint target = aimedCorner(x, z);
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(getConfirmedVertices());
        if (points.get(heldVertex).equals(target)) { cancelHeldVertex(); return; }
        if (points.contains(target)) throw new IllegalArgumentException("That corner is already a polygon vertex.");
        points.set(heldVertex, target);
        replaceGeometry(new KOMEPolygonPrism(new KOMEPolygon(points), geometry().getMinYInclusive(), geometry().getMaxYExclusive()));
        message = "Vertex " + (heldVertex + 1) + " moved in the unsaved draft.";
        cancelHeldVertex();
    }
    public void deleteSelectedVertex() {
        requireWorldEditable();
        if (selectedVertex < 0) { removeLastVertexInWorld(); return; }
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(getConfirmedVertices());
        points.remove(selectedVertex);
        replaceGeometry(new KOMEPolygonPrism(new KOMEPolygon(points), geometry().getMinYInclusive(), geometry().getMaxYExclusive()));
        cancelHeldVertex();
    }
    public void selectVertex(int index) { requireWorldEditable(); selectedVertex = index >= 0 && index < getConfirmedVertexCount() ? index : -1; }
    public void addWorldVertex(int x, int z) { requireWorldEditable();
        if (!worldVertexPlacementActive) throw new IllegalArgumentException("Choose Edit Vertices or Shift+right-click a polygon to add corners.");
        if (heldVertex >= 0 || worldHeightMode != Selection.NONE) return;
        KOMEXZPoint point = aimedCorner(x, z); addVertexInternal(point.getX(), point.getZ()); selectedVertex = -1; }
    public void applyWorldHeight(int y) { requireWorldEditable();
        if (worldHeightMode == Selection.NONE) return;
        if (worldHeightMode == Selection.UPPER_Y && y == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
        replaceGeometry(new KOMEPolygonPrism(geometry().getPolygon(), worldHeightMode == Selection.LOWER_Y ? y : geometry().getMinYInclusive(),
            worldHeightMode == Selection.UPPER_Y ? y + 1 : geometry().getMaxYExclusive()));
    }
    public void removeLastVertexInWorld() { requireWorldEditable(); removeLastVertexInternal(); }
    public GuiScreen endWorldView() {
        GuiScreen origin = isViewingGeometry() ? worldViewOrigin : null;
        clearWorldView();
        return origin;
    }
    private void clearWorldView() {
        worldViewOrigin = null; worldViewToken = null; worldViewScope = null; worldViewPlayer = null;
        worldViewInvalidated = false; worldViewWholeComplex = false; worldPolygonSelected = false; worldVertexPlacementActive = false;
        cancelHeldVertex(); worldHeightMode = Selection.NONE;
    }
    public boolean isCreating() { return creating; }
    public String getMessage() { return message; }
    public Selection getSelection() { return selection; }
    public boolean acceptCatalog(KOMETacticalAreaCatalog page) {
        if (isEditing() && !page.tileId.equals(catalog.tileId)) { message = "Cancel this edit before changing tiles."; return false; }
        if (waitingCatalog && page.page != requestedPage) return false;
        if (catalog != null && catalog.tileId.equals(page.tileId) && page.revision < catalog.revision) return false;
        if (catalog != null && !catalog.tileId.equals(page.tileId)) { complexBrowser = false; complexCatalog = null; options = null; pendingKind = null; }
        catalog = page; ++catalogueVersion; waitingCatalog = false;
        if (!recoveryPending && !recoveryClosing) busy = false;
        return true;
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
        if (retainedDraft) { acceptRecovery(value, status); return; }
        KOMETacticalEditSnapshot previous = snapshot;
        if (status == KOMETacticalEditSessionManager.Status.REFRESHED && value != null && snapshot != null
                && value.getToken().equals(snapshot.getToken()) && value.getDraftSequence() < snapshot.getDraftSequence()) return;
        if (value != null && isEditing() && !reopeningAfterSave && value.getGeneration() != snapshot.getGeneration()) return;
        if (value != null && snapshot != null && (value.getGeneration() < snapshot.getGeneration()
                || value.getGeneration() == snapshot.getGeneration() && !value.getToken().equals(snapshot.getToken()))) return;
        if (reopeningAfterSave && value != null && value.getToken().equals(snapshot.getToken())) return;
        if (reopeningAfterSave && value != null && (status != KOMETacticalEditSessionManager.Status.OPENED
                || !value.getScope().equals(snapshot.getScope()) || value.isClosed())) {
            String failure = status == KOMETacticalEditSessionManager.Status.OPENED
                ? "A different geometry session opened." : statusMessage(status);
            clearDraft(); busy = false; message = "Saved, but the editor could not reopen this geometry: " + failure;
            return;
        }
        if ((returningToAreaList || returningToComplexList) && value != null && status != KOMETacticalEditSessionManager.Status.CANCELLED) return;
        if (reopeningAfterSave && value == null) {
            reopeningAfterSave = false; busy = false; clearDraft();
            message = "Saved, but the editor could not reopen: " + statusMessage(status);
            return;
        }
        if (value != null && value.getToken().equals(cancelled) && !value.isClosed()) {
            if (status == KOMETacticalEditSessionManager.Status.STALE_SEQUENCE)
                transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, value));
            return;
        }
        if (value != null && catalog != null && !value.getScope().getTileId().equals(catalog.tileId)) return;
        if (status == KOMETacticalEditSessionManager.Status.REFRESHED && afterUpdate != null) { snapshot = value; return; }
        if (keepalivePending && (value == null || snapshot != null && value.getToken().equals(snapshot.getToken()))) {
            keepalivePending = false;
            if (status == KOMETacticalEditSessionManager.Status.REFRESHED && value != null && !value.isClosed()) {
                snapshot = value; lastSessionActivityTick = clientTicks;
                message = beforeKeepaliveMessage == null ? "" : beforeKeepaliveMessage; beforeKeepaliveMessage = null;
                return;
            }
            beforeKeepaliveMessage = null;
            if (status != KOMETacticalEditSessionManager.Status.EXPIRED) lastSessionActivityTick = clientTicks;
        }
        if (status == KOMETacticalEditSessionManager.Status.RATE_LIMITED && afterUpdate != null) {
            busy = false; afterUpdate = null; message = statusMessage(status); return;
        }
        busy = false;
        boolean resumedSave = reopeningAfterSave && status == KOMETacticalEditSessionManager.Status.OPENED;
        // A keepalive must not erase the confirmation/duplicate-corner feedback from local input.
        if (status != KOMETacticalEditSessionManager.Status.REFRESHED) message = statusMessage(status);
        if (value == null) {
            afterUpdate = null;
            if (status == KOMETacticalEditSessionManager.Status.DENIED || status == KOMETacticalEditSessionManager.Status.INVALID_SESSION
                    || status == KOMETacticalEditSessionManager.Status.EXPIRED) {
                retainUnexpectedClose(previous, status);
                if (!retainedDraft) clearDraft();
            }
            return;
        }
        boolean fresh = snapshot == null || !snapshot.getToken().equals(value.getToken());
        snapshot = value;
        if (value.isClosed()) {
            if (status == KOMETacticalEditSessionManager.Status.EXPIRED || status == KOMETacticalEditSessionManager.Status.INVALID_SESSION
                    || status == KOMETacticalEditSessionManager.Status.DENIED) {
                retainUnexpectedClose(previous, status);
                return;
            }
            boolean reopen = (status == KOMETacticalEditSessionManager.Status.SAVED || status == KOMETacticalEditSessionManager.Status.NO_CHANGE)
                && savingToken != null && savingToken.equals(value.getToken()) && (local != null || localComplex != null);
            KOMETacticalEditScope reopenScope = reopen ? value.getScope() : null;
            if (value.getPreflight() != null && !value.getPreflight().getDiagnostics().isEmpty())
                message += " " + String.join(" ", value.getPreflight().getDiagnostics());
            if (reopen) {
                geometryHistory.clear();
                savingToken = null; afterUpdate = null; openingNew = false; reopeningAfterSave = true; busy = true;
                message = "Saved. Waiting for the server to reopen the editor...";
                transport.accept(KOMETacticalEditRequest.open(reopenScope));
                return;
            }
            boolean backToAreaList = returningToAreaList && status == KOMETacticalEditSessionManager.Status.CANCELLED;
            boolean backToComplexList = returningToComplexList && status == KOMETacticalEditSessionManager.Status.CANCELLED;
            boolean invalidatedView = hasWorldViewContext(); GuiScreen origin = worldViewOrigin;
            UUID viewer = worldViewPlayer; KOMETacticalEditScope viewScope = worldViewScope;
            cancelled = value.getToken(); // A late OPENED/REFRESHED reply from this closed token cannot restore authority.
            clearDraft(); afterUpdate = null;
            if (invalidatedView) { worldViewOrigin = origin; worldViewPlayer = viewer; worldViewScope = viewScope; worldViewInvalidated = true; }
            if (backToAreaList) { browse(0); return; }
            if (backToComplexList) { complexPage(KOMETacticalComplexCatalog.Kind.COMPLEXES, 0); return; }
            if (catalog != null && (status == KOMETacticalEditSessionManager.Status.SAVED || status == KOMETacticalEditSessionManager.Status.DELETED)) {
                if (complexBrowser) complexPage(KOMETacticalComplexCatalog.Kind.COMPLEXES, 0); else browse(0);
            }
            return;
        }
        if (fresh || status == KOMETacticalEditSessionManager.Status.UPDATED) {
            local = value.getDraft().getArea();
            localComplex = value.getDraft().getComplex();
            if (localComplex != null) complexBrowser = true;
            if (fresh) {
                creating = openingNew; cancelled = null; clearGeometryPreview();
                openedSource = value.getDraft();
                savedComplex = creating ? null : localComplex;
                if (creating && local != null) initializeNewAreaY();
            }
            feedback = null;
        }
        if (fresh || status == KOMETacticalEditSessionManager.Status.REFRESHED || status == KOMETacticalEditSessionManager.Status.UPDATED
                || status == KOMETacticalEditSessionManager.Status.VALIDATED) lastSessionActivityTick = clientTicks;
        if (resumedSave) {
            reopeningAfterSave = false; savingToken = null; ++catalogueVersion;
            message = "Saved. Ready to continue editing.";
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
            if (next == KOMETacticalEditRequest.Action.SAVE) message = "Waiting for the server to confirm Save...";
        } else if (status != KOMETacticalEditSessionManager.Status.REFRESHED) afterUpdate = null;
    }
    public void setLabel(String label) { requireEditable(); replace(label, geometry()); }
    public void setYRangeInclusive(int bottom, int top) {
        requireEditable();
        if (top == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
        replaceGeometry(new KOMEPolygonPrism(geometry().getPolygon(), bottom, top + 1));
    }
    public void addVertex(int x, int z) {
        requireEditable(); addVertexInternal(x, z);
    }
    private void addVertexInternal(int x, int z) {
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(geometry().getPolygon().getVertices());
        KOMEXZPoint vertex = new KOMEXZPoint(x, z);
        if (points.contains(vertex)) throw new IllegalArgumentException("That corner is already a polygon vertex (" + x + ", " + z + "). The polygon closes automatically.");
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
    public void cycleWorldCorner() { requireWorldEditable(); corner = corner.next(); }
    public KOMEXZPoint aimedCorner(int x, int z) { return corner.point(x,z); }
    private void clearGeometryPreview() { corner = KOMETacticalPolygonPreview.Corner.NW; fillSource = null; fillTriangles = null; feedback = null; topologySource = null; topologyValidation = null; comparedComplex = null; comparedDraft = null; sameSnapshotDefinition = false; cancelHeldVertex(); worldHeightMode = Selection.NONE; geometryHistory.clear(); }
    public void undo() {
        requireEditable();
        undoGeometry();
    }
    public int getUndoCount() { Deque<KOMEPolygonPrism> steps = currentGeometryHistory(false); return steps == null ? 0 : steps.size(); }
    public void undoWorldGeometry() { requireWorldEditable(); undoGeometry(); }
    private void undoGeometry() {
        Deque<KOMEPolygonPrism> steps = currentGeometryHistory(false);
        if (steps == null || steps.isEmpty()) { message = "No geometry edit to undo for this polygon."; return; }
        KOMEPolygonPrism before = steps.removeLast();
        if (localComplex != null) {
            if (getZone() == null) { geometryHistory.remove(geometryHistoryKey()); return; }
            localComplex = KOMETacticalComplexDraft.edit(localComplex, zoneType, zoneId, getZone().getLabel(), before);
        } else if (local != null) local = new KOMEForceDeploymentArea(local.getAreaId(), local.getTileId(), local.getDimensionId(),
            local.getLabel(), before, local.getRevision());
        feedback = null; cancelHeldVertex(); message = "Last geometry edit undone in the local draft.";
    }
    public void removeLastVertex() { requireEditable(); removeLastVertexInternal(); }
    private void removeLastVertexInternal() {
        List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(geometry().getPolygon().getVertices());
        if (!points.isEmpty()) { points.remove(points.size() - 1); polygon(points); }
    }
    public void clearVertices() { requireEditable(); polygon(new ArrayList<KOMEXZPoint>()); }
    private void polygon(List<KOMEXZPoint> points) {
        replaceGeometry(new KOMEPolygonPrism(new KOMEPolygon(points), geometry().getMinYInclusive(), geometry().getMaxYExclusive()));
    }
    private void replaceGeometry(KOMEPolygonPrism prism) {
        KOMEPolygonPrism before = geometry();
        if (before.equals(prism)) return;
        Deque<KOMEPolygonPrism> steps = currentGeometryHistory(true);
        if (steps.size() == MAX_GEOMETRY_HISTORY) steps.removeFirst();
        steps.addLast(before);
        replace(getGeometryLabel(), prism);
    }
    private void replace(String label, KOMEPolygonPrism prism) {
        if (localComplex != null) localComplex = KOMETacticalComplexDraft.edit(localComplex, zoneType, zoneId, label, prism);
        else local = new KOMEForceDeploymentArea(local.getAreaId(), local.getTileId(), local.getDimensionId(), label, prism, local.getRevision());
        feedback = null;
        message = "Unsaved draft. Validate or Save when finished.";
    }
    private void requireEditable() { if (!isEditing() || isBusy() || hasWorldViewContext()) throw new IllegalArgumentException("Wait for the editor response or return from Geometry View."); }
    private KOMEPolygonPrism geometry() { if (getGeometry() == null) throw new IllegalArgumentException("Select a zone first."); return getGeometry(); }
    public void select(Selection mode) {
        requireEditable(); if (mode != Selection.NONE && mode != Selection.TOPOLOGY) geometry();
        if (mode == Selection.TOPOLOGY && !isComplexEditing()) throw new IllegalArgumentException("Open a Siege Complex to view its Connections.");
        selection = mode;
        if (mode == Selection.VERTICES) message = "Aim at a block. Tab chooses NW/NE/SE/SW; right-click adds a vertex. Backspace removes last; Delete clears.";
    }
    public void zoneType(ZoneType type) { requireEditable(); zoneType = type; zoneId = null; connectionId = null; inference = null; selection = Selection.NONE; feedback = null; cancelHeldVertex(); }
    public void selectZone(String id) { requireEditable(); if (KOMETacticalComplexDraft.find(localComplex, zoneType, id) == null) throw new IllegalArgumentException("Unknown zone."); zoneId = id; feedback = null; cancelHeldVertex(); }
    public void createZone(String id) {
        requireEditable(); localComplex = KOMETacticalComplexDraft.create(localComplex, zoneType, id); zoneId = id.trim();
        replace(getGeometryLabel(), new KOMEPolygonPrism(geometry().getPolygon(), NEW_DRAFT_MIN_Y, NEW_DRAFT_MAX_Y + 1));
        message = "New unsaved zone."; feedback = null;
    }
    public void deleteZone() { requireEditable(); geometryHistory.remove(geometryHistoryKey()); unappliedByGeometry.remove(geometryHistoryKey()); localComplex = KOMETacticalComplexDraft.remove(localComplex, zoneType, zoneId); feedback = null; zoneId = null; selection = Selection.NONE; message = "Zone removed from draft; references are retained for validation."; }
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
        if (isBusy() || !isEditing() || hasWorldViewContext()) return;
        switch (selection) {
            case VERTICES:
                KOMEXZPoint vertex = aimedCorner(x,z); addVertex(vertex.getX(),vertex.getZ()); break;
            case LOWER_Y: replaceGeometry(new KOMEPolygonPrism(geometry().getPolygon(), y, geometry().getMaxYExclusive())); break;
            case UPPER_Y:
                if (y == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
                replaceGeometry(new KOMEPolygonPrism(geometry().getPolygon(), geometry().getMinYInclusive(), y + 1)); break;
            default: return;
        }
        // The client tick keepalive refreshes the session without transmitting this local geometry.
    }
    public KOMEForceDeploymentArea overlay(UUID player, int dimension) {
        return hasSession(player, dimension) ? local : null;
    }
    public boolean hasSession(UUID player, int dimension) { return isEditing() && snapshot.getPlayerId().equals(player) && snapshot.getScope().getDimensionId() == dimension; }
    public static final class Overlay {
        public final KOMEPolygonPrism prism; public final ZoneType type; public final String id, label;
        public final boolean selected, invalid, unsaved;
        Overlay(KOMEPolygonPrism prism, ZoneType type, String id, String label, boolean selected, boolean invalid, boolean unsaved) {
            this.prism = prism; this.type = type; this.id = id; this.label = label;
            this.selected = selected; this.invalid = invalid; this.unsaved = unsaved;
        }
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
            if (local != null) {
                boolean unsaved = snapshot != null && !local.equals(snapshot.getDraft().getArea());
                result.add(new Overlay(local.getPrism(), null, local.getAreaId(), local.getLabel(),
                    !hasWorldViewContext() || worldPolygonSelected || unsaved, validation.isInvalid(), unsaved));
            }
            else for (ZoneType kind : ZoneType.values()) for (KOMESiegeZone zone : KOMETacticalComplexDraft.zones(localComplex, kind)) {
                KOMESiegeConnection c = getConnection();
                KOMESiegeZone saved = snapshot == null || snapshot.getDraft().getComplex() == null ? null
                    : KOMETacticalComplexDraft.find(snapshot.getDraft().getComplex(), kind, zone.getId());
                boolean unsaved = !zone.equals(saved);
                boolean active = kind == zoneType && zone.getId().equals(zoneId);
                boolean selected = active && (!hasWorldViewContext() || worldPolygonSelected || unsaved) || !hasWorldViewContext() && c != null && kind == ZoneType.NORMAL
                    && (c.getEndpointA().equals(KOMESiegeAreaRef.normal(zone.getId())) || c.getEndpointB().equals(KOMESiegeAreaRef.normal(zone.getId())));
                result.add(new Overlay(zone.getPrism(), kind, zone.getId(), zone.getLabel(), selected,
                    selected && validation.isInvalid() || validation.conflictingZoneIds.contains(zone.getId()), unsaved));
            }
        }
        return java.util.Collections.unmodifiableList(result);
    }
    public void validate() { updateThen(KOMETacticalEditRequest.Action.PREFLIGHT); }
    public void save() {
        requireEditable();
        selection = Selection.NONE; savingToken = snapshot == null ? null : snapshot.getToken();
        updateThen(KOMETacticalEditRequest.Action.SAVE);
        message = "Waiting for the server to save this draft...";
    }
    public void backToAreaList() {
        requireEditable();
        if (local == null) throw new IllegalArgumentException("Use the complex category Back button.");
        if (creating || !new KOMETacticalEditDraft(local).sameDefinition(snapshot.getDraft()))
            throw new IllegalArgumentException("Unsaved area edits. Save and wait for confirmation, or use Cancel to discard them.");
        returningToAreaList = true; busy = true; selection = Selection.NONE;
        message = "Returning to area list...";
        transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, snapshot));
    }
    public void backToComplexList() {
        requireEditable();
        if (localComplex == null) throw new IllegalArgumentException("Open a Siege Complex first.");
        if (creating || !new KOMETacticalEditDraft(localComplex).sameDefinition(snapshot.getDraft()))
            throw new IllegalArgumentException("Unsaved complex edits. Save and wait for confirmation, or use Cancel to discard them.");
        returningToComplexList = true; busy = true; selection = Selection.NONE;
        message = "Returning to Siege Complex list...";
        transport.accept(KOMETacticalEditRequest.action(KOMETacticalEditRequest.Action.CANCEL, snapshot));
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
    private void clearDraft() { selection = Selection.NONE; snapshot = null; openedSource = null; local = null; localComplex = null; savedComplex = null; reopeningAfterSave = false; returningToAreaList = false; returningToComplexList = false; savingToken = null; zoneId = null; connectionId = null; inference = null; connectionCatalog = null; options = null; creating = false; keepalivePending = false; beforeKeepaliveMessage = null; unappliedFields = null; unappliedByGeometry.clear(); retainedDraft = recoveryPending = recoveryClosing = recoveryRetryAfterClose = recoveryPriorClosed = retainedGeometryView = recoveryDisconnected = false; retainedScope = null; retainedSource = null; retainedPlayer = retainedToken = recoveryClosingToken = null; clearWorldView(); clearGeometryPreview(); }
    public void reset() {
        // A client lifecycle reset fences network replies, but a previously expired draft remains inspectable in memory.
        boolean preserve = retainedDraft;
        if (preserve) { snapshot = null; busy = recoveryPending = recoveryClosing = recoveryRetryAfterClose = recoveryPriorClosed = keepalivePending = false;
            recoveryDisconnected = true; recoveryClosingToken = null; clearWorldView(); clearGeometryPreview(); }
        else clearDraft();
        catalog = null; complexCatalog = null; pendingKind = null; complexBrowser = false;
        busy = false; waitingCatalog = false; afterUpdate = null; cancelled = null;
        message = preserve ? "Unsaved local draft retained after disconnect. Inspect or Discard it; recovery is unavailable across connections." : "";
    }
    public void error(String text) { message = text; }
    public static String statusMessage(KOMETacticalEditSessionManager.Status status) {
        switch (status) {
            case STALE_STORE: case STALE_OBJECT: return "Another admin changed this configuration. Cancel, refresh and reopen; your draft was kept.";
            case STALE_SEQUENCE: return "A newer draft exists. Retry after the current response.";
            case DUPLICATE_ID: return "That ID already exists. Choose another ID.";
            case DENIED: return "Creative or operator level 2 is required.";
            case EXPIRED: return "Editor session expired.";
            case SAVED: return "Saved.";
            case DELETED: return "Deleted.";
            case NO_CHANGE: return "No changes to save.";
            case CANCELLED: return "Draft cancelled.";
            case SESSION_ACTIVE: return "Cancel the current editing session first.";
            case INVALID_SESSION: return "Editor session closed.";
            case WRONG_DIMENSION: return "Open this tile's editor in its map dimension.";
            case LIMIT_REACHED: return "Editor capacity reached. Wait briefly and reopen.";
            case RATE_LIMITED: return "Too many requests. Wait briefly and retry.";
            case INVALID_DRAFT: return "Draft rejected. Check the area ID, fields and size limits.";
            case ACTIVITY_LOCKED: return "This Siege Complex is activity-locked. Draft kept; wait until activity ends before editing or saving.";
            case REJECTED: case COMMIT_FAILED: return "Request failed. No change was published; refresh and retry.";
            default: return "Unsaved draft.";
        }
    }
}
