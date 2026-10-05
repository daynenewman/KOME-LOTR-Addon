package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.KOMETacticalAreaCatalog;
import kome.common.tactical.edit.KOMETacticalEditScope;

/** Read-only tile selection and paged tile-area inspection; never fabricates a tile/complex. */
public final class KOMETacticalAreaAccess {
    private KOMETacticalAreaAccess() { }
    public static String requireTile(String id, int dimension) {
        String tile = KOMETacticalEditScope.canonicalId(id);
        KOMETileRasterSnapshot raster = KOMETileWorldResolver.INSTANCE.snapshot()
            .orElseThrow(() -> new IllegalArgumentException("Conquest tile map is unavailable."));
        if (raster.transform.dimension != dimension) throw new IllegalArgumentException("Open the editor in the conquest map's dimension.");
        if (!raster.colorsById().containsKey(tile)) throw new IllegalArgumentException("Unknown conquest tile: " + tile);
        return tile;
    }
    public static KOMETacticalAreaCatalog catalog(KOMEWorldData data, String id, int dimension, int page) {
        String tile = requireTile(id, dimension);
        KOMETacticalConfiguration configuration = data.getTacticalConfigurationSnapshot();
        List<KOMEForceDeploymentArea> areas = configuration.listForceDeploymentAreasForTile(tile);
        if (page < 0 || (long) page * KOMETacticalAreaCatalog.PAGE_SIZE > areas.size()) throw new IllegalArgumentException("Area list page no longer exists; refresh.");
        List<KOMETacticalAreaCatalog.Row> rows = new ArrayList<KOMETacticalAreaCatalog.Row>();
        int start = page * KOMETacticalAreaCatalog.PAGE_SIZE;
        for (int i = start; i < Math.min(areas.size(), start + KOMETacticalAreaCatalog.PAGE_SIZE); i++) {
            KOMEForceDeploymentArea area = areas.get(i);
            rows.add(new KOMETacticalAreaCatalog.Row(area.getAreaId(), area.getLabel(), area.getRevision()));
        }
        return new KOMETacticalAreaCatalog(tile, dimension, configuration.getRevision(), page, areas.size(), rows);
    }
}
