package kome.common.tactical;

import java.util.Locale;
import kome.common.siege.geometry.KOMEPolygonPrism;

/**
 * Immutable, tile-owned preferred force staging/roundup geometry.
 * It can exist without a Siege Complex and does not bound exterior combat.
 * Its stable identity is independent of its label, geometry and definition revision.
 */
public final class KOMEForceDeploymentArea {
    private final String areaId;
    private final String tileId;
    private final int dimensionId;
    private final String label;
    private final KOMEPolygonPrism prism;
    private final long revision;

    public KOMEForceDeploymentArea(String areaId, String tileId, Integer dimensionId,
            String label, KOMEPolygonPrism prism, long revision) {
        this.areaId = KOMETacticalIds.forceDeploymentArea(areaId);
        if (dimensionId == null) throw new IllegalArgumentException("Explicit dimension metadata is required.");
        if (prism == null) throw new IllegalArgumentException("Force Deployment Area geometry is required.");
        this.tileId = tileId == null ? "" : tileId.trim().toUpperCase(Locale.ROOT);
        this.dimensionId = dimensionId.intValue();
        this.label = label == null ? "" : label.trim();
        this.prism = prism;
        this.revision = revision;
    }

    public String getAreaId() { return areaId; }
    public String getTileId() { return tileId; }
    /** All signed dimension IDs are supported; validating live dimension registration is outside this model. */
    public int getDimensionId() { return dimensionId; }
    public String getLabel() { return label; }
    public KOMEPolygonPrism getPrism() { return prism; }
    public long getRevision() { return revision; }
}
