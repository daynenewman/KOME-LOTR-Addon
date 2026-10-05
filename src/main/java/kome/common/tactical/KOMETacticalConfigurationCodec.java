package kome.common.tactical;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMENormalSegment;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMESiegeZone;
import kome.common.siege.KOMETransitionZone;
import kome.common.siege.KOMEWallZone;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Pure codec for the value of a future TacticalConfiguration NBT section, not a world-root codec.
 * Strict encoded types/metadata are distinct from geometry/readiness validation.
 * Authoritative rows are sorted by canonical ID. Domain lists (including duplicate local IDs)
 * retain authored order because local first-match lookups and query order can depend on it;
 * wall access sets retain their domain's sorted order. Polygon vertices are never reordered.
 */
public final class KOMETacticalConfigurationCodec {
    public static final int SCHEMA_VERSION = 1;
    private static final String ROOT = "TacticalConfiguration";

    private KOMETacticalConfigurationCodec() {}

    /** Returns a fresh detached section and observes all authorities/revision under one store lock. */
    public static NBTTagCompound encode(KOMETacticalConfiguration configuration) {
        if (configuration == null) throw corrupt(ROOT, "configuration is required");
        synchronized (configuration) {
            NBTTagCompound section = new NBTTagCompound();
            section.setInteger("SchemaVersion", SCHEMA_VERSION);
            section.setLong("Revision", configuration.getRevision());
            NBTTagList complexes = new NBTTagList();
            for (KOMESiegeComplex complex : configuration.getComplexesById().values()) {
                complexes.appendTag(writeComplex(complex));
            }
            section.setTag("SiegeComplexes", complexes);
            NBTTagList areas = new NBTTagList();
            for (KOMEForceDeploymentArea area : configuration.getForceDeploymentAreasById().values()) {
                NBTTagCompound row = new NBTTagCompound();
                row.setString("AreaId", area.getAreaId());
                row.setString("TileId", area.getTileId());
                row.setInteger("DimensionId", area.getDimensionId());
                row.setString("Label", area.getLabel());
                writeRevision(row, area.getRevision(), ROOT + ".ForceDeploymentAreas[" + area.getAreaId() + "]");
                row.setTag("Geometry", writeGeometry(area.getPrism()));
                areas.appendTag(row);
            }
            section.setTag("ForceDeploymentAreas", areas);
            NBTTagList assignments = new NBTTagList();
            for (Map.Entry<String, String> assignment : configuration.getBuildAssignmentsByBuildId().entrySet()) {
                NBTTagCompound row = new NBTTagCompound();
                row.setString("BuildId", assignment.getKey());
                row.setString("ComplexId", assignment.getValue());
                assignments.appendTag(row);
            }
            section.setTag("DefensiveBuildAssignments", assignments);
            return section;
        }
    }

    /** Returns a complete detached store or throws; never edits the input or an existing store. */
    public static KOMETacticalConfiguration decode(NBTTagCompound section) {
        if (section == null) throw corrupt(ROOT, "section is required");
        int schema = integer(section, "SchemaVersion", ROOT);
        if (schema != SCHEMA_VERSION) throw corrupt(ROOT + ".SchemaVersion", "unsupported schema " + schema);
        long revision = revision(section, ROOT);
        Map<String, KOMESiegeComplex> complexes = new TreeMap<String, KOMESiegeComplex>();
        NBTTagList complexRows = rows(section, "SiegeComplexes", ROOT);
        for (int i = 0; i < complexRows.tagCount(); i++) {
            String path = ROOT + ".SiegeComplexes[" + i + "]";
            KOMESiegeComplex complex = readComplex(complexRows.getCompoundTagAt(i), path);
            if (complexes.put(complex.getComplexId(), complex) != null) throw corrupt(path, "duplicate canonical ComplexId " + complex.getComplexId());
        }
        Map<String, KOMEForceDeploymentArea> areas = new TreeMap<String, KOMEForceDeploymentArea>();
        NBTTagList areaRows = rows(section, "ForceDeploymentAreas", ROOT);
        for (int i = 0; i < areaRows.tagCount(); i++) {
            String path = ROOT + ".ForceDeploymentAreas[" + i + "]";
            NBTTagCompound row = areaRows.getCompoundTagAt(i);
            KOMEForceDeploymentArea area = new KOMEForceDeploymentArea(identity(row, "AreaId", path),
                string(row, "TileId", path), integer(row, "DimensionId", path), string(row, "Label", path),
                readGeometry(compound(row, "Geometry", path), path + ".Geometry"), revision(row, path));
            if (areas.put(area.getAreaId(), area) != null) throw corrupt(path, "duplicate canonical AreaId " + area.getAreaId());
        }
        Map<String, String> assignments = new TreeMap<String, String>();
        NBTTagList assignmentRows = rows(section, "DefensiveBuildAssignments", ROOT);
        for (int i = 0; i < assignmentRows.tagCount(); i++) {
            String path = ROOT + ".DefensiveBuildAssignments[" + i + "]";
            NBTTagCompound row = assignmentRows.getCompoundTagAt(i);
            String build = KOMETacticalIds.buildLookup(identity(row, "BuildId", path));
            String target = KOMETacticalIds.lookup(identity(row, "ComplexId", path));
            if (assignments.put(build, target) != null) throw corrupt(path, "duplicate canonical BuildId " + build);
            if (!complexes.containsKey(target)) throw corrupt(path, "assignment targets absent complex " + target);
        }
        try {
            return KOMETacticalConfiguration.reconstruct(revision, complexes.values(), areas.values(), assignments);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(ROOT + ": " + invalid.getMessage(), invalid);
        }
    }

    private static NBTTagCompound writeComplex(KOMESiegeComplex complex) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("ComplexId", complex.getComplexId());
        row.setString("TileId", complex.getTileId());
        row.setInteger("DimensionId", complex.getDimensionId());
        writeRevision(row, complex.getRevision(), ROOT + ".SiegeComplexes[" + complex.getComplexId() + "]");
        if (complex.getPreferredForceDeploymentAreaId().isPresent()) {
            row.setString("PreferredForceDeploymentAreaId", complex.getPreferredForceDeploymentAreaId().get());
        }
        NBTTagList normals = new NBTTagList();
        for (KOMENormalSegment normal : complex.getNormalSegments()) normals.appendTag(writeZone(normal));
        row.setTag("NormalSegments", normals);
        NBTTagList walls = new NBTTagList();
        for (KOMEWallZone wall : complex.getWallZones()) {
            NBTTagCompound zone = writeZone(wall);
            NBTTagList access = new NBTTagList();
            for (String id : wall.getAccessibleFromNormalSegmentIds()) {
                NBTTagCompound source = new NBTTagCompound();
                source.setString("NormalSegmentId", id);
                access.appendTag(source);
            }
            zone.setTag("AccessibleFromNormalSegmentIds", access);
            walls.appendTag(zone);
        }
        row.setTag("WallZones", walls);
        NBTTagList transitions = new NBTTagList();
        for (KOMETransitionZone transition : complex.getTransitionZones()) transitions.appendTag(writeZone(transition));
        row.setTag("TransitionZones", transitions);
        NBTTagList connections = new NBTTagList();
        for (KOMESiegeConnection connection : complex.getConnections()) {
            NBTTagCompound edge = new NBTTagCompound();
            edge.setString("ConnectionId", connection.getId());
            edge.setTag("EndpointA", writeEndpoint(connection.getEndpointA()));
            edge.setTag("EndpointB", writeEndpoint(connection.getEndpointB()));
            edge.setString("TransitionZoneId", connection.getTransitionZoneId());
            if (connection.getGateRef().isPresent()) {
                KOMEDefensiveGateRef gate = connection.getGateRef().get();
                NBTTagCompound ref = new NBTTagCompound();
                ref.setString("BuildId", gate.getBuildId());
                ref.setString("DefensiveGateRecordId", gate.getGateRecordId());
                edge.setTag("GateRef", ref);
            }
            connections.appendTag(edge);
        }
        row.setTag("Connections", connections);
        return row;
    }

    private static KOMESiegeComplex readComplex(NBTTagCompound row, String path) {
        String id = identity(row, "ComplexId", path);
        String tile = string(row, "TileId", path);
        int dimension = integer(row, "DimensionId", path);
        long revision = revision(row, path);
        String preferred = row.hasKey("PreferredForceDeploymentAreaId")
            ? identity(row, "PreferredForceDeploymentAreaId", path) : null;
        List<KOMENormalSegment> normals = new ArrayList<KOMENormalSegment>();
        NBTTagList normalRows = rows(row, "NormalSegments", path);
        for (int i = 0; i < normalRows.tagCount(); i++) {
            String zonePath = path + ".NormalSegments[" + i + "]";
            NBTTagCompound zone = normalRows.getCompoundTagAt(i);
            normals.add(new KOMENormalSegment(string(zone, "Id", zonePath), string(zone, "Label", zonePath), zoneGeometry(zone, zonePath)));
        }
        List<KOMEWallZone> walls = new ArrayList<KOMEWallZone>();
        NBTTagList wallRows = rows(row, "WallZones", path);
        for (int i = 0; i < wallRows.tagCount(); i++) {
            String zonePath = path + ".WallZones[" + i + "]";
            NBTTagCompound zone = wallRows.getCompoundTagAt(i);
            Set<String> access = new TreeSet<String>();
            NBTTagList accessRows = rows(zone, "AccessibleFromNormalSegmentIds", zonePath);
            for (int j = 0; j < accessRows.tagCount(); j++) {
                String accessPath = zonePath + ".AccessibleFromNormalSegmentIds[" + j + "]";
                String source = string(accessRows.getCompoundTagAt(j), "NormalSegmentId", accessPath).trim();
                if (!access.add(source)) throw corrupt(accessPath, "duplicate wall access source " + source);
            }
            walls.add(new KOMEWallZone(string(zone, "Id", zonePath), string(zone, "Label", zonePath), zoneGeometry(zone, zonePath), access));
        }
        List<KOMETransitionZone> transitions = new ArrayList<KOMETransitionZone>();
        NBTTagList transitionRows = rows(row, "TransitionZones", path);
        for (int i = 0; i < transitionRows.tagCount(); i++) {
            String zonePath = path + ".TransitionZones[" + i + "]";
            NBTTagCompound zone = transitionRows.getCompoundTagAt(i);
            transitions.add(new KOMETransitionZone(string(zone, "Id", zonePath), string(zone, "Label", zonePath), zoneGeometry(zone, zonePath)));
        }
        List<KOMESiegeConnection> connections = new ArrayList<KOMESiegeConnection>();
        NBTTagList connectionRows = rows(row, "Connections", path);
        for (int i = 0; i < connectionRows.tagCount(); i++) {
            String edgePath = path + ".Connections[" + i + "]";
            NBTTagCompound edge = connectionRows.getCompoundTagAt(i);
            KOMEDefensiveGateRef gate = null;
            if (edge.hasKey("GateRef")) {
                NBTTagCompound ref = compound(edge, "GateRef", edgePath);
                gate = new KOMEDefensiveGateRef(string(ref, "BuildId", edgePath + ".GateRef"),
                    string(ref, "DefensiveGateRecordId", edgePath + ".GateRef"));
            }
            connections.add(new KOMESiegeConnection(string(edge, "ConnectionId", edgePath),
                readEndpoint(compound(edge, "EndpointA", edgePath), edgePath + ".EndpointA"),
                readEndpoint(compound(edge, "EndpointB", edgePath), edgePath + ".EndpointB"),
                string(edge, "TransitionZoneId", edgePath), gate));
        }
        return new KOMESiegeComplex(id, tile, dimension, revision, normals, walls, transitions, preferred, connections);
    }

    private static NBTTagCompound writeZone(KOMESiegeZone zone) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("Id", zone.getId());
        row.setString("Label", zone.getLabel());
        row.setTag("Geometry", writeGeometry(zone.getPrism()));
        return row;
    }

    private static NBTTagCompound writeGeometry(KOMEPolygonPrism prism) {
        NBTTagCompound geometry = new NBTTagCompound();
        NBTTagList vertices = new NBTTagList();
        for (KOMEXZPoint point : prism.getPolygon().getVertices()) {
            NBTTagCompound vertex = new NBTTagCompound();
            vertex.setInteger("X", point.getX());
            vertex.setInteger("Z", point.getZ());
            vertices.appendTag(vertex);
        }
        geometry.setTag("Vertices", vertices);
        geometry.setInteger("MinYInclusive", prism.getMinYInclusive());
        geometry.setInteger("MaxYExclusive", prism.getMaxYExclusive());
        return geometry;
    }

    private static KOMEPolygonPrism zoneGeometry(NBTTagCompound zone, String path) {
        return readGeometry(compound(zone, "Geometry", path), path + ".Geometry");
    }

    private static KOMEPolygonPrism readGeometry(NBTTagCompound geometry, String path) {
        NBTTagList rows = rows(geometry, "Vertices", path);
        List<KOMEXZPoint> vertices = new ArrayList<KOMEXZPoint>();
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound vertex = rows.getCompoundTagAt(i);
            String vertexPath = path + ".Vertices[" + i + "]";
            vertices.add(new KOMEXZPoint(integer(vertex, "X", vertexPath), integer(vertex, "Z", vertexPath)));
        }
        return new KOMEPolygonPrism(new KOMEPolygon(vertices), integer(geometry, "MinYInclusive", path),
            integer(geometry, "MaxYExclusive", path));
    }

    private static NBTTagCompound writeEndpoint(KOMESiegeAreaRef endpoint) {
        NBTTagCompound row = new NBTTagCompound();
        row.setString("Type", endpoint.getType().name());
        if (endpoint.isNormal()) row.setString("NormalSegmentId", endpoint.getNormalSegmentId());
        return row;
    }

    private static KOMESiegeAreaRef readEndpoint(NBTTagCompound row, String path) {
        String type = string(row, "Type", path);
        if ("NORMAL".equals(type)) {
            endpointKeys(row, path, true);
            return KOMESiegeAreaRef.normal(string(row, "NormalSegmentId", path));
        }
        if ("EXTERIOR".equals(type)) {
            endpointKeys(row, path, false);
            return KOMESiegeAreaRef.exterior();
        }
        throw corrupt(path + ".Type", "unknown endpoint type " + type);
    }

    private static void endpointKeys(NBTTagCompound row, String path, boolean normal) {
        for (Object key : row.func_150296_c()) {
            if (!"Type".equals(key) && !(normal && "NormalSegmentId".equals(key))) {
                throw corrupt(path, "unexpected endpoint field " + key);
            }
        }
    }

    private static NBTBase required(NBTTagCompound row, String key, int type, String path) {
        NBTBase tag = row.getTag(key);
        if (tag == null || tag.getId() != type) throw corrupt(path + "." + key, "required NBT type " + type);
        return tag;
    }

    private static String string(NBTTagCompound row, String key, String path) {
        required(row, key, 8, path);
        return row.getString(key);
    }

    private static String identity(NBTTagCompound row, String key, String path) {
        String value = string(row, key, path);
        if (value.trim().isEmpty()) throw corrupt(path + "." + key, "identity cannot be blank");
        return value;
    }

    private static int integer(NBTTagCompound row, String key, String path) {
        required(row, key, 3, path);
        return row.getInteger(key);
    }

    private static long revision(NBTTagCompound row, String path) {
        required(row, "Revision", 4, path);
        long value = row.getLong("Revision");
        if (value < 0) throw corrupt(path + ".Revision", "revision cannot be negative");
        return value;
    }

    private static void writeRevision(NBTTagCompound row, long revision, String path) {
        if (revision < 0) throw corrupt(path + ".Revision", "revision cannot be negative");
        row.setLong("Revision", revision);
    }

    private static NBTTagCompound compound(NBTTagCompound row, String key, String path) {
        return (NBTTagCompound) required(row, key, 10, path);
    }

    private static NBTTagList rows(NBTTagCompound row, String key, String path) {
        NBTTagList list = (NBTTagList) required(row, key, 9, path);
        int type = list.func_150303_d();
        // END is valid only for a genuinely empty list; typed empty compound lists are also valid.
        if (type != 10 && !(type == 0 && list.tagCount() == 0)) {
            throw corrupt(path + "." + key, "list must contain compound rows");
        }
        // Every caller requires fields on each projected compound. Minecraft substitutes an empty
        // compound for a wrong actual element type; required fields then reject that element too.
        return list;
    }

    private static IllegalArgumentException corrupt(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }
}
