package kome.client.tactical;

import java.util.*;
import kome.common.siege.*;
import kome.common.tactical.edit.*;

/** Complex-local detached topology authoring; geometry, membership and KOM-10 records are never changed. */
public final class KOMETacticalConnectionDraft {
    private KOMETacticalConnectionDraft() { }
    public static List<KOMESiegeConnection> list(KOMESiegeComplex complex) {
        List<KOMESiegeConnection> result = new ArrayList<>(complex.getConnections());
        result.sort(Comparator.comparing(KOMESiegeConnection::getId));
        return Collections.unmodifiableList(result);
    }
    public static KOMESiegeConnection find(KOMESiegeComplex complex, String id) {
        KOMESiegeConnection found = null;
        for (KOMESiegeConnection c : complex.getConnections()) if (c.getId().equals(id)) {
            if (found != null) throw new IllegalArgumentException("Connection ID is ambiguous; repair duplicate IDs first.");
            found = c;
        }
        return found;
    }
    public static List<KOMESiegeAreaRef> endpoints(KOMESiegeComplex complex) {
        SortedSet<KOMESiegeAreaRef> result = new TreeSet<>(); result.add(KOMESiegeAreaRef.exterior());
        for (KOMENormalSegment normal : complex.getNormalSegments()) result.add(KOMESiegeAreaRef.normal(normal.getId()));
        return Collections.unmodifiableList(new ArrayList<>(result));
    }
    public static KOMESiegeComplex create(KOMESiegeComplex complex, String rawId) {
        KOMETacticalEditScope.validateId(rawId); String id = rawId.trim();
        if (find(complex, id) != null) throw new IllegalArgumentException("Connection ID already exists in this complex.");
        List<KOMESiegeAreaRef> endpoints = endpoints(complex);
        List<KOMESiegeZone> transitions = KOMETacticalComplexDraft.zones(complex, KOMETacticalComplexDraft.ZoneType.TRANSITION);
        if (endpoints.size() < 2 || transitions.isEmpty()) throw new IllegalArgumentException("Create a Normal Segment and a Transition Zone first.");
        return change(complex, new KOMESiegeConnection(id, endpoints.get(0), endpoints.get(1), transitions.get(0).getId(), null), false);
    }
    public static KOMESiegeComplex edit(KOMESiegeComplex complex, KOMESiegeConnection connection) {
        if (find(complex, connection.getId()) == null) throw new IllegalArgumentException("Select an existing Connection.");
        return change(complex, connection, true);
    }
    public static KOMESiegeComplex remove(KOMESiegeComplex complex, String id) {
        KOMESiegeConnection selected = find(complex, id);
        if (selected == null) throw new IllegalArgumentException("Select an existing Connection.");
        List<KOMESiegeConnection> connections = new ArrayList<>(complex.getConnections()); connections.remove(selected);
        return copy(complex, connections);
    }
    private static KOMESiegeComplex change(KOMESiegeComplex complex, KOMESiegeConnection connection, boolean replace) {
        List<KOMESiegeConnection> connections = new ArrayList<>(complex.getConnections());
        if (replace) connections.remove(find(complex, connection.getId())); connections.add(connection);
        return copy(complex, connections);
    }
    private static KOMESiegeComplex copy(KOMESiegeComplex c, List<KOMESiegeConnection> connections) {
        KOMESiegeComplex next = new KOMESiegeComplex(c.getComplexId(), c.getTileId(), c.getDimensionId(), c.getRevision(),
            c.getNormalSegments(), c.getWallZones(), c.getTransitionZones(), c.getPreferredForceDeploymentAreaId().orElse(null), connections);
        KOMESiegeGateUsage.requireNoNewConflicts(c, next);
        KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(next));
        return next;
    }
}
