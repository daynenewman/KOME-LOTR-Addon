package kome.client;

/** Shared GUI-space affine transform for mask presentation and inverse hover coordinates. */
final class KOMEMapViewport {
    final double left, top, right, bottom, posX, posY, zoom;

    KOMEMapViewport(double left, double top, double right, double bottom,
            double posX, double posY, double zoom) {
        if (!Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right)
                || !Double.isFinite(bottom) || !Double.isFinite(posX) || !Double.isFinite(posY)
                || !Double.isFinite(zoom) || right <= left || bottom <= top || zoom <= 0) {
            throw new IllegalArgumentException("Invalid conquest map viewport");
        }
        this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        this.posX = posX; this.posY = posY; this.zoom = zoom;
    }

    double screenX(double mapX) { return (left + right) / 2 + (mapX - posX) * zoom; }
    double screenY(double mapY) { return (top + bottom) / 2 + (mapY - posY) * zoom; }
    double mapX(double screenX) { return posX + (screenX - (left + right) / 2) / zoom; }
    double mapY(double screenY) { return posY + (screenY - (top + bottom) / 2) / zoom; }
    boolean contains(double x, double y) { return x >= left && x < right && y >= top && y < bottom; }
}
