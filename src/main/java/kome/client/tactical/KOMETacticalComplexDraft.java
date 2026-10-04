package kome.client.tactical;

import java.util.*;
import kome.common.siege.*;
import kome.common.siege.geometry.*;
import kome.common.tactical.edit.*;

/** Detached local zone operations. Connection editing uses the shared complex draft separately. */
public final class KOMETacticalComplexDraft {
    public enum ZoneType { NORMAL, WALL, TRANSITION }
    private KOMETacticalComplexDraft() { }
    public static List<KOMESiegeZone> zones(KOMESiegeComplex c, ZoneType type) {
        List<KOMESiegeZone> result = new ArrayList<KOMESiegeZone>();
        if (type == ZoneType.NORMAL) result.addAll(c.getNormalSegments());
        else if (type == ZoneType.WALL) result.addAll(c.getWallZones()); else result.addAll(c.getTransitionZones());
        result.sort(Comparator.comparing(KOMESiegeZone::getId));
        return Collections.unmodifiableList(result);
    }
    public static KOMESiegeZone find(KOMESiegeComplex c, ZoneType type, String id) {
        for (KOMESiegeZone zone : zones(c, type)) if (zone.getId().equals(id)) return zone;
        return null;
    }
    public static KOMESiegeComplex create(KOMESiegeComplex c, ZoneType type, String rawId) {
        KOMETacticalEditScope.validateId(rawId);
        String id = rawId.trim(); // domain zone constructors retain this local case
        for (ZoneType kind : ZoneType.values()) if (find(c, kind, id) != null) throw new IllegalArgumentException("Zone ID already exists in this complex.");
        return change(c, type, id, "", new KOMEPolygonPrism(new KOMEPolygon(Collections.emptyList()), 0, 1), Collections.emptySet(), false);
    }
    public static KOMESiegeComplex edit(KOMESiegeComplex c, ZoneType type, String id, String label, KOMEPolygonPrism prism) {
        KOMESiegeZone current = find(c, type, id);
        if (current == null) throw new IllegalArgumentException("Select an existing zone.");
        return change(c, type, id, label, prism, current instanceof KOMEWallZone
            ? ((KOMEWallZone) current).getAccessibleFromNormalSegmentIds() : Collections.emptySet(), false);
    }
    public static KOMESiegeComplex remove(KOMESiegeComplex c, ZoneType type, String id) {
        if (find(c, type, id) == null) throw new IllegalArgumentException("Select an existing zone.");
        return change(c, type, id, "", null, Collections.emptySet(), true);
    }
    public static KOMESiegeComplex wallAccess(KOMESiegeComplex c, String wallId, String normalId) {
        KOMEWallZone wall = c.findWallZone(wallId);
        if (wall == null || c.findNormalSegment(normalId) == null) throw new IllegalArgumentException("Wall access must select an authored Normal Segment.");
        Set<String> ids = new TreeSet<String>(wall.getAccessibleFromNormalSegmentIds());
        if (!ids.remove(normalId)) ids.add(normalId);
        return change(c, ZoneType.WALL, wallId, wall.getLabel(), wall.getPrism(), ids, false);
    }
    public static KOMESiegeComplex preferred(KOMESiegeComplex c, String areaId) {
        return copy(c, c.getNormalSegments(), c.getWallZones(), c.getTransitionZones(), areaId);
    }
    private static KOMESiegeComplex change(KOMESiegeComplex c, ZoneType type, String id, String label,
            KOMEPolygonPrism prism, Collection<String> access, boolean remove) {
        if (zones(c, type).stream().filter(zone -> zone.getId().equals(id)).count() > 1)
            throw new IllegalArgumentException("Zone ID " + id + " is ambiguous; repair duplicate IDs before editing it.");
        List<KOMENormalSegment> normals = new ArrayList<KOMENormalSegment>(c.getNormalSegments());
        List<KOMEWallZone> walls = new ArrayList<KOMEWallZone>(c.getWallZones());
        List<KOMETransitionZone> transitions = new ArrayList<KOMETransitionZone>(c.getTransitionZones());
        if (type == ZoneType.NORMAL) { normals.removeIf(z -> z.getId().equals(id)); if (!remove) normals.add(new KOMENormalSegment(id, label, prism)); }
        else if (type == ZoneType.WALL) { walls.removeIf(z -> z.getId().equals(id)); if (!remove) walls.add(new KOMEWallZone(id, label, prism, access)); }
        else { transitions.removeIf(z -> z.getId().equals(id)); if (!remove) transitions.add(new KOMETransitionZone(id, label, prism)); }
        KOMESiegeComplex next = copy(c, normals, walls, transitions, c.getPreferredForceDeploymentAreaId().orElse(null));
        KOMETacticalEditWire.encodeDraft(new KOMETacticalEditDraft(next)); // enforce the same shared packet/collection bounds
        return next;
    }
    private static KOMESiegeComplex copy(KOMESiegeComplex c, Collection<KOMENormalSegment> normals,
            Collection<KOMEWallZone> walls, Collection<KOMETransitionZone> transitions, String preferred) {
        return new KOMESiegeComplex(c.getComplexId(), c.getTileId(), c.getDimensionId(), c.getRevision(), normals, walls, transitions, preferred, c.getConnections());
    }
}
