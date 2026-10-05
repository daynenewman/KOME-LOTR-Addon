package kome.common.siege;
import kome.common.siege.geometry.KOMEPolygonPrism;
public interface KOMESiegeZone {
    String getId();
    String getLabel();
    KOMEPolygonPrism getPrism();
}
