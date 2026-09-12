package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Server-authoritative future draft.
 */
public final class GateEditDraft {

    private final Map<GateEditCoordinate, GateEditDraftPart> parts =
            new LinkedHashMap<GateEditCoordinate, GateEditDraftPart>();

    private final GateOrientation orientation;

    private GateOpeningDirection openingDirection;
    private boolean borderTextureEnabled;
    private GateHinge leftHinge;
    private GateHinge rightHinge;

    GateEditDraft(
            GateEditOriginalSnapshot original
    ) {
        orientation =
                original.getOrientation();

        openingDirection =
                original.getOpeningDirection();

        borderTextureEnabled =
                original.isBorderTextureEnabled();

        leftHinge =
                original.getLeftHinge();

        rightHinge =
                original.getRightHinge();

        for (GateEditOriginalPart part
                : original.getParts()) {

            GateEditCoordinate key =
                    new GateEditCoordinate(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            if (parts.put(
                    key,
                    new GateEditDraftPart(part)
            ) != null
                    || parts.size()
                    > GateStructureValidator.MAX_GATE_PARTS) {

                throw new IllegalArgumentException(
                        "Invalid gate edit draft."
                );
            }
        }
    }

    public Collection<GateEditDraftPart> getParts() {
        return Collections.unmodifiableCollection(
                parts.values()
        );
    }

    public int getPartCount() {
        return parts.size();
    }

    public GateOrientation getOrientation() {
        return orientation;
    }

    public GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    public boolean isBorderTextureEnabled() {
        return borderTextureEnabled;
    }

    public GateHinge getLeftHinge() {
        return leftHinge;
    }

    public GateHinge getRightHinge() {
        return rightHinge;
    }

    GateEditDraftPart getPart(
            GateEditCoordinate key
    ) {
        return parts.get(key);
    }

    GateEditDraftPart addPart(
            int x,
            int y,
            int z,
            GateLeaf leaf,
            String sourceName,
            int sourceMeta,
            NBTTagCompound sourceTileEntityNbt,
            boolean restorable
    ) {
        GateEditCoordinate key =
                new GateEditCoordinate(
                        x,
                        y,
                        z
                );

        if (parts.containsKey(key)
                || parts.size()
                >= GateStructureValidator.MAX_GATE_PARTS) {
            return null;
        }

        GateEditAddedSource source =
                new GateEditAddedSource(
                        sourceName,
                        sourceMeta,
                        sourceTileEntityNbt,
                        restorable
                );

        GateEditDraftPart result =
                new GateEditDraftPart(
                        x,
                        y,
                        z,
                        leaf,
                        source
                );

        parts.put(
                key,
                result
        );

        return result;
    }

    GateEditDraftPart restoreOriginal(
            GateEditOriginalPart original,
            GateLeaf leaf
    ) {
        if (original == null
                || leaf == null) {
            return null;
        }

        GateEditCoordinate key =
                new GateEditCoordinate(
                        original.getRelativeX(),
                        original.getRelativeY(),
                        original.getRelativeZ()
                );

        if (parts.containsKey(key)
                || parts.size()
                >= GateStructureValidator.MAX_GATE_PARTS) {
            return null;
        }

        GateEditDraftPart result =
                new GateEditDraftPart(
                        original
                );

        result.setLeaf(
                leaf
        );

        parts.put(
                key,
                result
        );

        return result;
    }

    GateEditDraftPart removePart(
            GateEditCoordinate key
    ) {
        GateEditDraftPart removed =
                parts.remove(key);

        if (removed != null) {
            clearInvalidHinges(
                    key,
                    null
            );
        }

        return removed;
    }

    boolean setRole(
            GateEditCoordinate key,
            GateLeaf leaf
    ) {
        GateEditDraftPart part =
                parts.get(key);

        if (part == null
                || leaf == null) {
            return false;
        }

        part.setLeaf(
                leaf
        );

        clearInvalidHinges(
                key,
                leaf
        );

        return true;
    }

    void setLeftHinge(
            GateHinge hinge
    ) {
        leftHinge =
                copy(hinge);
    }

    void setRightHinge(
            GateHinge hinge
    ) {
        rightHinge =
                copy(hinge);
    }

    void setOpeningDirection(
            GateOpeningDirection direction
    ) {
        if (direction != null) {
            openingDirection =
                    direction;
        }
    }

    void setBorderTextureEnabled(
            boolean enabled
    ) {
        borderTextureEnabled =
                enabled;
    }

    private void clearInvalidHinges(
            GateEditCoordinate key,
            GateLeaf ignoredLeaf
    ) {
        if (key == null) {
            return;
        }

        if (leftHinge != null
                && leftHinge.getRelativeX()
                == key.x
                && leftHinge.getRelativeZ()
                == key.z) {

            boolean leftStillPresent =
                    false;

            for (GateEditDraftPart part
                    : parts.values()) {

                if (part != null
                        && part.getRelativeX()
                        == leftHinge.getRelativeX()
                        && part.getRelativeZ()
                        == leftHinge.getRelativeZ()
                        && part.getLeaf() != null
                        && part.getLeaf()
                        .isActualLeft()) {

                    leftStillPresent =
                            true;

                    break;
                }
            }

            if (!leftStillPresent) {
                leftHinge = null;
            }
        }

        if (rightHinge != null
                && rightHinge.getRelativeX()
                == key.x
                && rightHinge.getRelativeZ()
                == key.z) {

            boolean rightStillPresent =
                    false;

            for (GateEditDraftPart part
                    : parts.values()) {

                if (part != null
                        && part.getRelativeX()
                        == rightHinge.getRelativeX()
                        && part.getRelativeZ()
                        == rightHinge.getRelativeZ()
                        && part.getLeaf() != null
                        && part.getLeaf()
                        .isActualRight()) {

                    rightStillPresent =
                            true;

                    break;
                }
            }

            if (!rightStillPresent) {
                rightHinge = null;
            }
        }
    }

    private static GateHinge copy(
            GateHinge hinge
    ) {
        return hinge == null
                ? null
                : new GateHinge(
                hinge.getRelativeX(),
                hinge.getRelativeZ(),
                hinge.getSide()
        );
    }

    boolean matchesOriginal(
            GateEditOriginalSnapshot original
    ) {
        if (original == null
                || parts.size()
                != original.getParts().size()
                || orientation
                != original.getOrientation()
                || openingDirection
                != original.getOpeningDirection()
                || borderTextureEnabled
                != original.isBorderTextureEnabled()
                || !same(
                leftHinge,
                original.getLeftHinge()
        )
                || !same(
                rightHinge,
                original.getRightHinge()
        )) {

            return false;
        }

        for (GateEditOriginalPart part
                : original.getParts()) {

            GateEditDraftPart draft =
                    parts.get(
                            new GateEditCoordinate(
                                    part.getRelativeX(),
                                    part.getRelativeY(),
                                    part.getRelativeZ()
                            )
                    );

            if (draft == null
                    || draft.getLeaf()
                    != part.getLeaf()
                    || !draft.originatesFromOriginal()) {

                return false;
            }
        }

        return true;
    }

    private static boolean same(
            GateHinge first,
            GateHinge second
    ) {
        return first == null
                ? second == null
                : first.equals(second);
    }
}