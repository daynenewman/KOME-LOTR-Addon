package kome.client.tactical;

import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import java.util.List;

/** Client-only ray intersection for the drawn prism. Returns distance along a unit look ray. */
public final class KOMETacticalGeometryPicker {
    private static final double EPSILON = 1.0E-8D;
    private KOMETacticalGeometryPicker() { }

    public static double distance(KOMEPolygonPrism prism, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        if (prism == null || prism.getMaxYExclusive() <= prism.getMinYInclusive() || reach <= 0) return Double.POSITIVE_INFINITY;
        List<KOMEXZPoint> points = prism.getPolygon().getVertices();
        if (points.size() < 3) return Double.POSITIVE_INFINITY;
        double best = Double.POSITIVE_INFINITY;
        // An eye inside a volume is not looking at its interior. Pick the first surface ahead.
        if (Math.abs(dy) > EPSILON) {
            for (double y : new double[] {prism.getMinYInclusive(), prism.getMaxYExclusive()}) {
                double t = (y - oy) / dy;
                if (t >= 0 && t <= reach && contains(points, ox + t * dx, oz + t * dz)) best = Math.min(best, t);
            }
        }
        for (int i = 0; i < points.size(); i++) {
            KOMEXZPoint a = points.get(i), b = points.get((i + 1) % points.size());
            double ex = (double)b.getX() - a.getX(), ez = (double)b.getZ() - a.getZ();
            double denominator = dx * ez - dz * ex;
            if (Math.abs(denominator) <= EPSILON) continue;
            double rx = (double)a.getX() - ox, rz = (double)a.getZ() - oz;
            double t = (rx * ez - rz * ex) / denominator;
            double u = (rx * dz - rz * dx) / denominator;
            double y = oy + t * dy;
            if (t >= 0 && t <= reach && u >= -EPSILON && u <= 1 + EPSILON
                    && y >= prism.getMinYInclusive() - EPSILON && y <= prism.getMaxYExclusive() + EPSILON)
                best = Math.min(best, t);
        }
        return best;
    }
    /** Closest projected authored vertex column to the crosshair, including columns behind terrain. */
    public static int vertexIndex(KOMEPolygonPrism prism, double ox, double oy, double oz,
            double dx, double dy, double dz, double reach) {
        if (prism == null || reach <= 0 || prism.getPolygon().getVertices().isEmpty()) return -1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(length) || length <= EPSILON) return -1;
        dx /= length; dy /= length; dz /= length;
        int best = -1; double bestAngle = Math.sin(Math.toRadians(2.5D));
        double bestDepth = Double.POSITIVE_INFINITY;
        List<KOMEXZPoint> points = prism.getPolygon().getVertices();
        double low = Math.min(prism.getMinYInclusive(), prism.getMaxYExclusive());
        double high = Math.max(prism.getMinYInclusive(), prism.getMaxYExclusive());
        for (int i = 0; i < points.size(); i++) {
            KOMEXZPoint p = points.get(i);
            double hx = (double)p.getX() - ox, hz = (double)p.getZ() - oz;
            double horizontalDot = hx * dx + hz * dz;
            double horizontalSquared = hx * hx + hz * hz;
            double idealY = horizontalDot > EPSILON ? oy + dy * horizontalSquared / horizontalDot : oy;
            double y = Math.max(low, Math.min(high, idealY));
            double vy = y - oy;
            double distance = Math.sqrt(horizontalSquared + vy * vy);
            double depth = hx * dx + vy * dy + hz * dz;
            if (distance <= EPSILON || distance > reach || depth <= 0) continue;
            double sine = Math.sqrt(Math.max(0, distance * distance - depth * depth)) / distance;
            if (sine < bestAngle - EPSILON || Math.abs(sine - bestAngle) <= EPSILON && depth < bestDepth - EPSILON) {
                best = i; bestAngle = sine; bestDepth = depth;
            }
        }
        return best;
    }
    private static boolean contains(List<KOMEXZPoint> points, double x, double z) {
        boolean inside = false;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            KOMEXZPoint a = points.get(j), b = points.get(i);
            double cross = ((double)b.getX() - a.getX()) * (z - a.getZ())
                - ((double)b.getZ() - a.getZ()) * (x - a.getX());
            if (Math.abs(cross) <= EPSILON && x >= Math.min(a.getX(), b.getX()) - EPSILON
                    && x <= Math.max(a.getX(), b.getX()) + EPSILON && z >= Math.min(a.getZ(), b.getZ()) - EPSILON
                    && z <= Math.max(a.getZ(), b.getZ()) + EPSILON) return true;
            if ((a.getZ() > z) != (b.getZ() > z)
                    && x < ((double)b.getX() - a.getX()) * (z - a.getZ()) / ((double)b.getZ() - a.getZ()) + a.getX()) inside = !inside;
        }
        return inside;
    }
}
