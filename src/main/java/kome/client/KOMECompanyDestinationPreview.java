package kome.client;

import java.util.*;
import java.util.function.Function;

/** Cached map-space display points for the exact projected tile sequence; missing anchors remain gaps. */
final class KOMECompanyDestinationPreview {
    static final KOMECompanyDestinationPreview EMPTY=new KOMECompanyDestinationPreview(Collections.<Point>emptyList());
    static final class Point {
        final double x,y;
        Point(double x,double y) {this.x=x;this.y=y;}
    }
    final List<Point> points;
    private KOMECompanyDestinationPreview(List<Point> points) {this.points=Collections.unmodifiableList(points);}
    static KOMECompanyDestinationPreview of(List<String> route,Function<String,double[]> anchors) {
        List<Point> points=new ArrayList<Point>();
        for(String tile:route) {
            double[] anchor=anchors.apply(tile);
            points.add(anchor==null?null:new Point(anchor[0],anchor[1]));
        }
        return new KOMECompanyDestinationPreview(points);
    }
}
