package kome.client;

import kome.common.data.KOMEConquestTile;

/** One active map-detail intent. A stale reply may cause at most two safe read-only retries. */
public final class KOMEMapDetailRequests {
    public enum Decision { DROP, OPEN, REFRESH }
    private static final int MAX_REFRESHES = 2;

    private Object connection;
    private long nextId;
    private Request pending;

    public synchronized void start(Object connection) {
        this.connection = connection;
        pending = null;
    }

    public synchronized void invalidateWorld() { pending = null; }

    /** A dismissed origin screen cannot authorize a later response or retry. */
    public synchronized void cancelFromScreen(Object screen) {
        if (pending != null && screen != null && pending.screen == screen) pending = null;
    }

    public synchronized long begin(String kind, String tile, String focus, Object screen,
            boolean mayCloseForNavigation) {
        if (connection == null || kind == null) return 0L;
        if (nextId == Long.MAX_VALUE) throw new IllegalStateException("Map detail request IDs exhausted");
        pending = new Request(++nextId, kind, KOMEConquestTile.normalizeId(tile),
            focus == null ? "" : focus, screen, mayCloseForNavigation);
        return pending.id;
    }

    public synchronized Decision resolve(long id, String kind, String tile, String focus,
            long publicationSerial, Object source, Object screen,
            KOMEConquestSnapshotPublisher snapshots) {
        Request request = pending;
        if (id <= 0L || request == null || request.id != id || source == null
                || source != connection || !request.kind.equals(kind)
                || !request.tile.equals(KOMEConquestTile.normalizeId(tile))
                || !request.focus.equals(focus == null ? "" : focus)) return Decision.DROP;
        if (screen != request.screen && !(request.mayCloseForNavigation && screen == null)) {
            pending = null;
            return Decision.DROP;
        }
        if (snapshots.allowsMapDetail(publicationSerial, source)) {
            pending = null;
            return Decision.OPEN;
        }
        if (publicationSerial > 0L && publicationSerial < snapshots.appliedPublicationSerial(source)
                && request.refreshes < MAX_REFRESHES) {
            request.refreshes++;
            return Decision.REFRESH;
        }
        return Decision.DROP;
    }

    private static final class Request {
        final long id;
        final String kind, tile, focus;
        final Object screen;
        final boolean mayCloseForNavigation;
        int refreshes;
        Request(long id, String kind, String tile, String focus, Object screen, boolean mayClose) {
            this.id=id; this.kind=kind; this.tile=tile; this.focus=focus;
            this.screen=screen; this.mayCloseForNavigation=mayClose;
        }
    }
}
