package com.enovak.lotrmoremobs.siege.gate;

import java.util.Collection;

public final class GateConfigurationValidator {

    private GateConfigurationValidator() {
    }

    public static GateOrientation determineOrientation(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge
    ) {
        Bounds bounds = getBounds(parts);
        if (bounds == null) {
            return null;
        }

        boolean validX = isValidForOrientation(
                parts,
                leftHinge,
                rightHinge,
                GateOrientation.WIDTH_X,
                bounds
        );
        boolean validZ = isValidForOrientation(
                parts,
                leftHinge,
                rightHinge,
                GateOrientation.WIDTH_Z,
                bounds
        );
        if (validX && !validZ) {
            return GateOrientation.WIDTH_X;
        }
        if (validZ && !validX) {
            return GateOrientation.WIDTH_Z;
        }
        if (validX && validZ) {
            int spanX = bounds.maxX - bounds.minX + 1;
            int spanZ = bounds.maxZ - bounds.minZ + 1;
            if (spanX > spanZ) {
                return GateOrientation.WIDTH_X;
            }
            if (spanZ > spanX) {
                return GateOrientation.WIDTH_Z;
            }
        }
        return null;
    }

    public static boolean isValid(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge,
            GateOrientation orientation
    ) {
        Bounds bounds = getBounds(parts);
        return bounds != null
                && orientation != null
                && leftHinge != null
                && rightHinge != null
                && leftHinge.getSide() != null
                && rightHinge.getSide() != null
                && isValidForOrientation(
                        parts,
                        leftHinge,
                        rightHinge,
                        orientation,
                        bounds
                );
    }

    public static GateHinge configureHinge(
            Collection<GatePartData> parts,
            GateHinge hinge,
            GateOrientation orientation
    ) {
        Bounds bounds = getBounds(parts);
        GateHingeSide side = bounds == null || hinge == null
                ? null
                : getHingeSide(hinge, orientation, bounds);
        return side == null
                ? null
                : new GateHinge(
                        hinge.getRelativeX(),
                        hinge.getRelativeZ(),
                        side
                );
    }

    private static boolean isValidForOrientation(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge,
            GateOrientation orientation,
            Bounds bounds
    ) {
        if (leftHinge == null
                || rightHinge == null
                || !containsHinge(parts, GateLeaf.LEFT, leftHinge)
                || !containsHinge(parts, GateLeaf.RIGHT, rightHinge)) {
            return false;
        }

        GateHingeSide leftSide = getHingeSide(
                leftHinge,
                orientation,
                bounds
        );
        GateHingeSide rightSide = getHingeSide(
                rightHinge,
                orientation,
                bounds
        );
        return leftSide != null
                && rightSide != null
                && leftSide != rightSide
                && (leftHinge.getSide() == null
                || leftHinge.getSide() == leftSide)
                && (rightHinge.getSide() == null
                || rightHinge.getSide() == rightSide);
    }

    private static GateHingeSide getHingeSide(
            GateHinge hinge,
            GateOrientation orientation,
            Bounds bounds
    ) {
        if (orientation == null || hinge == null) {
            return null;
        }
        int position = orientation == GateOrientation.WIDTH_X
                ? hinge.getRelativeX()
                : hinge.getRelativeZ();
        int minimum = orientation == GateOrientation.WIDTH_X
                ? bounds.minX
                : bounds.minZ;
        int maximum = orientation == GateOrientation.WIDTH_X
                ? bounds.maxX
                : bounds.maxZ;
        if (minimum >= maximum) {
            return null;
        }
        if (position == minimum) {
            return GateHingeSide.MINIMUM;
        }
        if (position == maximum) {
            return GateHingeSide.MAXIMUM;
        }
        return null;
    }

    private static boolean containsHinge(
            Collection<GatePartData> parts,
            GateLeaf leaf,
            GateHinge hinge
    ) {
        for (GatePartData part : parts) {
            if (part != null
                    && ((leaf == GateLeaf.LEFT
                    && part.getLeaf().isActualLeft())
                    || (leaf == GateLeaf.RIGHT
                    && part.getLeaf().isActualRight()))
                    && part.getRelativeX() == hinge.getRelativeX()
                    && part.getRelativeZ() == hinge.getRelativeZ()) {
                return true;
            }
        }
        return false;
    }

    private static Bounds getBounds(Collection<GatePartData> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }
        Bounds bounds = new Bounds();
        boolean foundPart = false;
        for (GatePartData part : parts) {
            if (part == null) {
                continue;
            }
            bounds.minX = Math.min(bounds.minX, part.getRelativeX());
            bounds.maxX = Math.max(bounds.maxX, part.getRelativeX());
            bounds.minZ = Math.min(bounds.minZ, part.getRelativeZ());
            bounds.maxZ = Math.max(bounds.maxZ, part.getRelativeZ());
            foundPart = true;
        }
        return foundPart ? bounds : null;
    }

    private static final class Bounds {
        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;
    }
}
