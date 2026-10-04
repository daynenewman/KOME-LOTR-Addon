package kome.common.data;

import java.util.*;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeReadinessEvaluator;
import kome.common.tactical.*;
import kome.common.tactical.edit.KOMETacticalComplexCatalog;
import kome.common.tactical.edit.KOMETacticalComplexCatalog.*;
import kome.common.tactical.edit.KOMETacticalEditScope;
import static kome.common.tactical.edit.KOMETacticalComplexCatalog.PAGE_SIZE;

/** Read-only tile/complex catalogue. Readiness is evaluated only for the bounded visible complex page. */
public final class KOMETacticalComplexAccess {
    private KOMETacticalComplexAccess() { }
    public static KOMETacticalComplexCatalog catalog(KOMEWorldData data, Kind kind, String tileId,
            String complexId, int dimension, int page) {
        String tile = KOMETacticalAreaAccess.requireTile(tileId, dimension);
        synchronized (data) {
            KOMETacticalConfiguration config = data.getTacticalConfigurationSnapshot();
            KOMESiegeComplex owner = complexId == null ? null : config.findComplex(complexId);
            if (kind != Kind.COMPLEXES && (kind == Kind.BUILDS && owner == null
                    || owner != null && (!tile.equals(owner.getTileId()) || owner.getDimensionId() != dimension)))
                throw new IllegalArgumentException("Save and reopen this complex before editing membership or choosing an area.");
            String context = kind == Kind.COMPLEXES ? null : KOMETacticalEditScope.canonicalId(complexId);
            List<Row> rows = new ArrayList<Row>();
            int total;
            if (kind == Kind.COMPLEXES) {
                List<KOMESiegeComplex> complexes = config.listComplexesForTile(tile);
                total = complexes.size(); int start = start(page, total);
                for (int i = start; i < Math.min(total, start + PAGE_SIZE); i++) {
                    KOMESiegeComplex c = complexes.get(i);
                    KOMESiegeReadinessEvaluator.Report report = KOMESiegeReadinessResolver.evaluate(data, config, c, null);
                    String detail = report.isReady() ? "READY" : !report.getGeometryValidation().isValid()
                        ? "NOT READY / geometry needs attention" : !report.isConfigurationValid()
                        ? "NOT READY / configuration needs attention" : "NOT READY / incomplete";
                    rows.add(new Row(c.getComplexId(), "", detail, c.getPreferredForceDeploymentAreaId().orElse(null),
                        c.getRevision(), config.listAssignedBuildIds(c.getComplexId()).size()));
                }
            } else if (kind == Kind.PREFERRED_AREAS) {
                List<KOMEForceDeploymentArea> areas = new ArrayList<KOMEForceDeploymentArea>();
                for (KOMEForceDeploymentArea area : config.listForceDeploymentAreasForTile(tile))
                    if (area.getDimensionId() == dimension) areas.add(area);
                total = areas.size(); int start = start(page, total);
                for (int i = start; i < Math.min(total, start + PAGE_SIZE); i++) {
                    KOMEForceDeploymentArea area = areas.get(i);
                    rows.add(new Row(area.getAreaId(), text(area.getLabel(), 256), "Tile-owned staging area", null, area.getRevision(), 0));
                }
            } else {
                SortedSet<String> ids = new TreeSet<String>(config.listAssignedBuildIds(owner.getComplexId()));
                for (KOMEPlayerBuild build : data.builds.values()) if (build != null && build.tileId != null && tile.equalsIgnoreCase(build.tileId.trim()) && build.isDefensive())
                    ids.add(KOMETacticalIds.buildLookup(build.id));
                List<String> ordered = new ArrayList<String>(ids);
                total = ordered.size(); int start = start(page, total);
                for (int i = start; i < Math.min(total, start + PAGE_SIZE); i++) {
                    String id = ordered.get(i); KOMEPlayerBuild build = data.getBuild(id);
                    String assignment = config.findAssignedComplexId(id).orElse(null);
                    String detail = assignment == null ? "Unassigned" : assignment.equals(owner.getComplexId())
                        ? "Assigned here" : "Assigned to " + assignment;
                    EnumSet<KOMETacticalGateReferenceResolver.Code> problems = KOMETacticalGateReferenceResolver.buildProblems(build, owner);
                    if (!problems.isEmpty()) detail += " / " + problems;
                    rows.add(new Row(id, build == null ? "Missing Build" : text(build.displayName, 256), text(detail, 512), assignment, 0L, 0));
                }
            }
            return new KOMETacticalComplexCatalog(kind, tile, context,
                dimension, config.getRevision(), page, total, rows);
        }
    }
    private static int start(int page, int total) {
        if (page < 0 || (long) page * PAGE_SIZE > total) throw new IllegalArgumentException("Page changed; refresh.");
        return page * PAGE_SIZE;
    }
    private static String text(String value, int max) { return value == null ? "" : value.substring(0, Math.min(max, value.length())); }
}
