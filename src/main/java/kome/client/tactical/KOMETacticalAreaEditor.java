package kome.client.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import kome.common.siege.geometry.*;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.*;

/** Client advisory edit state for the shared editor shell; authoritative changes remain server sessions. */
public final class KOMETacticalAreaEditor {
    public enum Selection { NONE, VERTICES, LOWER_Y, UPPER_Y }
    private final Consumer<KOMETacticalEditRequest> transport;
    private KOMETacticalAreaCatalog catalog;
    private KOMETacticalEditSnapshot snapshot;
    private KOMEForceDeploymentArea local;
    private UUID cancelled;
    private KOMETacticalEditRequest.Action afterUpdate;
    private boolean busy, openingNew, creating, waitingCatalog;
    private int requestedPage;
    private Selection selection = Selection.NONE;
    private String message = "";
    private long lastWorldRefresh;
    public KOMETacticalAreaEditor(Consumer<KOMETacticalEditRequest> transport) { this.transport = transport; }
    public KOMETacticalAreaCatalog getCatalog() { return catalog; }
    public KOMEForceDeploymentArea getDraft() { return local; }
    public boolean isEditing() { return snapshot != null && !snapshot.isClosed() && local != null; }
    public boolean isBusy() { return busy; }
    public boolean isCreating() { return creating; }
    public String getMessage() { return message; }
    public Selection getSelection() { return selection; }
    public boolean acceptCatalog(KOMETacticalAreaCatalog page) {
        if (isEditing() && !page.tileId.equals(catalog.tileId)) { message = "Cancel this edit before changing tiles."; return false; }
        if (waitingCatalog && page.page != requestedPage) return false;
        if (catalog != null && catalog.tileId.equals(page.tileId) && page.revision < catalog.revision) return false;
        catalog = page; waitingCatalog = false; busy = false; return true;
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
        if (value != null && value.getScope().getType() != KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA) return;
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
        busy = false; message = statusMessage(status);
        if (value == null) {
            afterUpdate = null;
            if (status == KOMETacticalEditSessionManager.Status.DENIED || status == KOMETacticalEditSessionManager.Status.INVALID_SESSION) clearDraft();
            return;
        }
        boolean fresh = snapshot == null || !snapshot.getToken().equals(value.getToken());
        snapshot = value;
        if (value.isClosed()) {
            clearDraft(); afterUpdate = null;
            if (catalog != null && (status == KOMETacticalEditSessionManager.Status.SAVED || status == KOMETacticalEditSessionManager.Status.DELETED)) browse(0);
            return;
        }
        if (fresh || status == KOMETacticalEditSessionManager.Status.UPDATED) {
            local = value.getDraft().getArea();
            if (fresh) { creating = openingNew; cancelled = null; }
        }
        if (value.getPreflight() != null) {
            KOMETacticalEditPreflight p = value.getPreflight();
            if (status == KOMETacticalEditSessionManager.Status.VALIDATED) {
                message = p.isStructurallyValid() ? "Area geometry valid." : "Geometry needs attention.";
                if (!p.canSave()) message = "Save blocked. " + message;
            }
            if (!p.getDiagnostics().isEmpty()) message += " " + String.join(" ", p.getDiagnostics());
        }
        if (status == KOMETacticalEditSessionManager.Status.UPDATED && afterUpdate != null) {
            KOMETacticalEditRequest.Action next = afterUpdate; afterUpdate = null; send(next);
        } else if (status != KOMETacticalEditSessionManager.Status.REFRESHED) afterUpdate = null;
    }
    public void setLabel(String label) { requireEditable(); replace(label, local.getPrism()); }
    public void setYRangeInclusive(int bottom, int top) {
        requireEditable();
        if (top == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
        replace(local.getLabel(), new KOMEPolygonPrism(local.getPrism().getPolygon(), bottom, top + 1));
    }
    public void addVertex(int x, int z) {
        requireEditable(); List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(local.getPrism().getPolygon().getVertices());
        if (points.size() >= KOMETacticalEditWire.MAX_VERTICES) throw new IllegalArgumentException("Maximum 128 vertices.");
        points.add(new KOMEXZPoint(x, z)); polygon(points);
    }
    public void undo() {
        requireEditable(); List<KOMEXZPoint> points = new ArrayList<KOMEXZPoint>(local.getPrism().getPolygon().getVertices());
        if (!points.isEmpty()) points.remove(points.size() - 1); polygon(points);
    }
    public void clearVertices() { requireEditable(); polygon(new ArrayList<KOMEXZPoint>()); }
    private void polygon(List<KOMEXZPoint> points) {
        replace(local.getLabel(), new KOMEPolygonPrism(new KOMEPolygon(points), local.getPrism().getMinYInclusive(), local.getPrism().getMaxYExclusive()));
    }
    private void replace(String label, KOMEPolygonPrism prism) {
        local = new KOMEForceDeploymentArea(local.getAreaId(), local.getTileId(), local.getDimensionId(), label, prism, local.getRevision());
        message = "Unsaved draft. Validate or Save when finished.";
    }
    private void requireEditable() { if (!isEditing() || busy) throw new IllegalArgumentException("Wait for the editor response."); }
    public void select(Selection mode) { requireEditable(); selection = mode; }
    public boolean consumesClicks(UUID player, int dimension) {
        return selection != Selection.NONE && isEditing() && snapshot.getPlayerId().equals(player) && snapshot.getScope().getDimensionId() == dimension;
    }
    public void worldPoint(int x, int y, int z) {
        if (busy || !isEditing()) return;
        switch (selection) {
            case VERTICES: addVertex(x, z); break;
            case LOWER_Y: replace(local.getLabel(), new KOMEPolygonPrism(local.getPrism().getPolygon(), y, local.getPrism().getMaxYExclusive())); break;
            case UPPER_Y:
                if (y == Integer.MAX_VALUE) throw new IllegalArgumentException("Top block Y is too large.");
                replace(local.getLabel(), new KOMEPolygonPrism(local.getPrism().getPolygon(), local.getPrism().getMinYInclusive(), y + 1)); break;
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
        return isEditing() && snapshot.getPlayerId().equals(player) && local.getDimensionId() == dimension ? local : null;
    }
    public void validate() { updateThen(KOMETacticalEditRequest.Action.PREFLIGHT); }
    public void save() { selection = Selection.NONE; updateThen(KOMETacticalEditRequest.Action.SAVE); }
    private void updateThen(KOMETacticalEditRequest.Action action) {
        requireEditable(); afterUpdate = action; busy = true;
        try { transport.accept(KOMETacticalEditRequest.update(snapshot, new KOMETacticalEditDraft(local))); }
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
    private void clearDraft() { selection = Selection.NONE; snapshot = null; local = null; creating = false; }
    public void reset() { clearDraft(); catalog = null; busy = false; waitingCatalog = false; afterUpdate = null; cancelled = null; message = ""; }
    public void error(String text) { message = text; }
    public static String statusMessage(KOMETacticalEditSessionManager.Status status) {
        switch (status) {
            case STALE_STORE: case STALE_OBJECT: return "Another admin changed this configuration. Cancel, refresh and reopen; your draft was kept.";
            case STALE_SEQUENCE: return "A newer draft exists. Retry after the current response.";
            case DUPLICATE_ID: return "That area ID already exists. Choose another ID.";
            case DENIED: return "Creative or operator level 2 is required.";
            case EXPIRED: return "Editor session expired. Reopen the area.";
            case SAVED: return "Area saved.";
            case DELETED: return "Area deleted.";
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
