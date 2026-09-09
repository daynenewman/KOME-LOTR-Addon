package com.enovak.lotrmoremobs.siege.client.gui;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.management.FinalizedGateSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-only, immutable-derived presentation data for the finalized-gate
 * structure page. It deliberately reads only the detached inspection snapshot.
 */
final class GateStructureInspectionViewModel {

    private final FinalizedGateSnapshot snapshot;
    private final boolean orientationAvailable;
    private final int leftCount;
    private final int rightCount;
    private final int splitCenterCount;
    private final int minimumWidth;
    private final int maximumWidth;
    private final int minimumY;
    private final int maximumY;
    private final List<Integer> depthValues;
    private final List<List<FinalizedGateSnapshot.PartEntry>> partsByLayer;

    GateStructureInspectionViewModel(FinalizedGateSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Inspection snapshot is required.");
        }
        this.snapshot = snapshot;
        GateOrientation orientation = snapshot.getOrientation();
        orientationAvailable = orientation != null;

        int countedLeft = 0;
        int countedRight = 0;
        int countedSplitCenter = 0;
        int minWidth = Integer.MAX_VALUE;
        int maxWidth = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        List<Integer> collectedDepths = new ArrayList<Integer>();

        for (FinalizedGateSnapshot.PartEntry part : snapshot.getParts()) {
            if (part.getLeaf() == GateLeaf.LEFT) {
                ++countedLeft;
            } else if (part.getLeaf() == GateLeaf.RIGHT) {
                ++countedRight;
            } else if (part.getLeaf() == GateLeaf.SPLIT_CENTER) {
                ++countedSplitCenter;
            }
            if (orientationAvailable) {
                int widthCoordinate = getWidthCoordinate(part);
                int depthCoordinate = getDepthCoordinate(part);
                minWidth = Math.min(minWidth, widthCoordinate);
                maxWidth = Math.max(maxWidth, widthCoordinate);
                minY = Math.min(minY, part.getRelativeY());
                maxY = Math.max(maxY, part.getRelativeY());
                if (!collectedDepths.contains(Integer.valueOf(depthCoordinate))) {
                    collectedDepths.add(Integer.valueOf(depthCoordinate));
                }
            }
        }

        leftCount = countedLeft;
        rightCount = countedRight;
        splitCenterCount = countedSplitCenter;
        if (!orientationAvailable) {
            minimumWidth = 0;
            maximumWidth = -1;
            minimumY = 0;
            maximumY = -1;
            depthValues = Collections.emptyList();
            partsByLayer = Collections.emptyList();
            return;
        }

        Collections.sort(collectedDepths);
        List<List<FinalizedGateSnapshot.PartEntry>> collectedLayers =
                new ArrayList<List<FinalizedGateSnapshot.PartEntry>>(
                        collectedDepths.size()
                );
        for (int i = 0; i < collectedDepths.size(); ++i) {
            collectedLayers.add(new ArrayList<FinalizedGateSnapshot.PartEntry>());
        }
        for (FinalizedGateSnapshot.PartEntry part : snapshot.getParts()) {
            int layerIndex = collectedDepths.indexOf(
                    Integer.valueOf(getDepthCoordinate(part))
            );
            collectedLayers.get(layerIndex).add(part);
        }
        for (int i = 0; i < collectedLayers.size(); ++i) {
            collectedLayers.set(
                    i,
                    Collections.unmodifiableList(collectedLayers.get(i))
            );
        }

        minimumWidth = minWidth;
        maximumWidth = maxWidth;
        minimumY = minY;
        maximumY = maxY;
        depthValues = Collections.unmodifiableList(collectedDepths);
        partsByLayer = Collections.unmodifiableList(collectedLayers);
    }

    FinalizedGateSnapshot getSnapshot() {
        return snapshot;
    }

    boolean hasOrientation() {
        return orientationAvailable;
    }

    int getLeftCount() {
        return leftCount;
    }

    int getRightCount() {
        return rightCount;
    }

    int getSplitCenterCount() {
        return splitCenterCount;
    }

    int getWidth() {
        return maximumWidth - minimumWidth + 1;
    }

    int getHeight() {
        return maximumY - minimumY + 1;
    }

    int getThickness() {
        return depthValues.size();
    }

    int getLayerCount() {
        return partsByLayer.size();
    }

    int getDepthValue(int layerIndex) {
        return depthValues.get(layerIndex).intValue();
    }

    List<FinalizedGateSnapshot.PartEntry> getPartsForLayer(int layerIndex) {
        return partsByLayer.get(layerIndex);
    }

    int getScreenColumn(FinalizedGateSnapshot.PartEntry part) {
        return getWidthCoordinate(part) - minimumWidth;
    }

    int getScreenRow(FinalizedGateSnapshot.PartEntry part) {
        return maximumY - part.getRelativeY();
    }

    boolean isAtHinge(
            FinalizedGateSnapshot.PartEntry part,
            GateHinge hinge
    ) {
        return hinge != null
                && part.getRelativeX() == hinge.getRelativeX()
                && part.getRelativeZ() == hinge.getRelativeZ();
    }

    private int getWidthCoordinate(FinalizedGateSnapshot.PartEntry part) {
        return snapshot.getOrientation() == GateOrientation.WIDTH_X
                ? part.getRelativeX()
                : part.getRelativeZ();
    }

    private int getDepthCoordinate(FinalizedGateSnapshot.PartEntry part) {
        return snapshot.getOrientation() == GateOrientation.WIDTH_X
                ? part.getRelativeZ()
                : part.getRelativeX();
    }
}
