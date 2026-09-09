package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.banner.SiegeGateBannerAttachmentData;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateSourceTileEntitySnapshot;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

final class GateCreationFinalizer {

    static final int MAX_GATE_PARTS =
            GateStructureValidator.MAX_GATE_PARTS;

    private GateCreationFinalizer() {
    }

    static String finalizeGate(
            GateCreationSession session,
            TileEntitySiegeGate controller
    ) {
        String validationError =
                validate(session, controller);

        if (validationError != null) {
            return validationError;
        }

        World world =
                controller.getWorldObj();

        /*
         * Capture every authoritative source snapshot BEFORE modifying any
         * world block.
         *
         * This ensures the finalized structure and rollback path both use
         * exactly the same source state.
         */
        List<GateSelectionData> finalSelections =
                new ArrayList<GateSelectionData>();

        for (GateSelectionData selection
                : session.getSelections()) {
            GateBlockPosition position =
                    selection.getPosition();

            TileEntity sourceTileEntity =
                    world.getTileEntity(
                            position.getX(),
                            position.getY(),
                            position.getZ()
                    );

            NBTTagCompound sourceTileEntityNbt =
                    null;

            if (sourceTileEntity != null) {
                try {
                    sourceTileEntityNbt =
                            GateSourceTileEntitySnapshot
                                    .captureForGateStorage(
                                            sourceTileEntity
                                    );

                } catch (RuntimeException exception) {
                    return "Gate conversion stopped because a source "
                            + "TileEntity could not be captured safely.";
                }

            } else if (
                    selection.hasSourceTileEntityNbt()
            ) {
                /*
                 * The selected block had a TileEntity when it was selected,
                 * but it no longer does. Do not finalize from stale state.
                 */
                return "A selected source TileEntity changed before finalization.";
            }

            finalSelections.add(
                    selection.withSourceTileEntityNbt(
                            sourceTileEntityNbt
                    )
            );
        }

        List<GatePartData> gateParts =
                buildGateParts(
                        finalSelections,
                        controller
                );

        GateStructureValidator.ValidationResult
                structure =
                GateStructureValidator
                        .validateFinalized(
                                gateParts,
                                toRelativeHinge(
                                        session
                                                .getLeftHingePosition(),
                                        controller
                                ),
                                toRelativeHinge(
                                        session
                                                .getRightHingePosition(),
                                        controller
                                ),
                                null,
                                session
                                        .getOpeningDirection(),
                                controller.xCoord,
                                controller.yCoord,
                                controller.zCoord
                        );

        if (!structure.isValid()) {
            return structure.getMessage();
        }

        if (!controller
                .canPersistFinalizedGateData(
                        gateParts
                )) {
            return "Durable Siege Gate ownership capacity "
                    + "or identity validation failed.";
        }

        /*
         * LOTR banners are entities, not source blocks. Capture them into a
         * separate durable sidecar before their support blocks disappear so
         * native banner drop/protection behavior cannot run during movement.
         */
        SiegeGateBannerAttachmentData.PrepareResult bannerPrepare =
                SiegeGateBannerAttachmentData.prepareFinalization(
                        world,
                        controller,
                        gateParts
                );
        if (!bannerPrepare.isSuccessful()) {
            return bannerPrepare.getError();
        }
        java.util.UUID bannerGateUuid = controller.getExistingGateUuid();

        List<GateSelectionData> converted =
                new ArrayList<GateSelectionData>();

        for (GateSelectionData selection
                : finalSelections) {
            GateBlockPosition position =
                    selection.getPosition();

            converted.add(selection);

            boolean placed;

            try {
                placed =
                        world.setBlock(
                                position.getX(),
                                position.getY(),
                                position.getZ(),
                                SiegeRegistry.gatePart,
                                0,
                                2
                        );

            } catch (RuntimeException exception) {
                rollback(
                        world,
                        converted
                );
                SiegeGateBannerAttachmentData.rollbackFinalization(
                        world,
                        bannerGateUuid
                );

                return "Gate conversion failed; "
                        + "all converted blocks were restored.";
            }

            if (!placed
                    || world.getBlock(
                    position.getX(),
                    position.getY(),
                    position.getZ()
            ) != SiegeRegistry.gatePart) {
                rollback(
                        world,
                        converted
                );
                SiegeGateBannerAttachmentData.rollbackFinalization(
                        world,
                        bannerGateUuid
                );

                return "Gate conversion failed; "
                        + "all converted blocks were restored.";
            }
        }

        if (!controller.setFinalizedGateData(
                gateParts,
                structure.getLeftHinge(),
                structure.getRightHinge(),
                structure.getOrientation(),
                session.getOpeningDirection(),
                session.isBorderTextureEnabled()
        )) {
            rollback(
                    world,
                    converted
            );
            SiegeGateBannerAttachmentData.rollbackFinalization(
                    world,
                    bannerGateUuid
            );

            return "The controller rejected "
                    + "the finalized structure.";
        }

        SiegeGateBannerAttachmentData.commitFinalization(
                world,
                bannerGateUuid
        );

        controller.setOwnerOnFinalization(
                session.getCreatorUuid()
        );

        world.playSoundEffect(
                controller.xCoord + 0.5D,
                controller.yCoord + 0.5D,
                controller.zCoord + 0.5D,
                "lotrmoremobs:siege.gate_finalize",
                1.0F,
                1.0F
        );

        return null;
    }

    private static String validate(
            GateCreationSession session,
            TileEntitySiegeGate controller
    ) {
        if (controller == null
                || controller.getWorldObj() == null
                || controller
                .getWorldObj()
                .isRemote
                || controller.isInvalid()) {
            return "The Siege Gate Controller "
                    + "is no longer available.";
        }

        if (controller.isFinalized()) {
            return "This controller already owns "
                    + "a finalized gate.";
        }

        Collection<GateSelectionData> selections =
                session.getSelections();

        if (selections.isEmpty()
                || selections.size()
                > MAX_GATE_PARTS) {
            return "Select between 1 and " + MAX_GATE_PARTS + " "
                    + "total gate blocks.";
        }

        World world =
                controller.getWorldObj();

        for (GateSelectionData selection
                : selections) {
            GateBlockPosition position =
                    selection.getPosition();

            if (!world.blockExists(
                    position.getX(),
                    position.getY(),
                    position.getZ()
            )) {
                return "Every selected gate chunk "
                        + "must be loaded.";
            }

            if (!GateSourceBlockValidator
                    .matchesSelection(
                            world,
                            selection
                    )) {
                return "A selected source block changed "
                        + "or is no longer valid.";
            }
        }

        if (!isHingeSelectionValid(
                session,
                GateLeaf.LEFT,
                session.getLeftHingePosition()
        )) {
            return "Select a LEFT hinge block "
                    + "from the LEFT leaf.";
        }

        if (!isHingeSelectionValid(
                session,
                GateLeaf.RIGHT,
                session.getRightHingePosition()
        )) {
            return "Select a RIGHT hinge block "
                    + "from the RIGHT leaf.";
        }

        /*
         * Validation uses the current selection collection directly.
         * This is the corrected call for the new buildGateParts signature.
         */
        List<GatePartData> gateParts =
                buildGateParts(
                        session.getSelections(),
                        controller
                );

        GateStructureValidator.ValidationResult
                structure =
                GateStructureValidator
                        .validateFinalized(
                                gateParts,
                                toRelativeHinge(
                                        session
                                                .getLeftHingePosition(),
                                        controller
                                ),
                                toRelativeHinge(
                                        session
                                                .getRightHingePosition(),
                                        controller
                                ),
                                null,
                                session
                                        .getOpeningDirection(),
                                controller.xCoord,
                                controller.yCoord,
                                controller.zCoord
                        );

        return structure.isValid()
                ? null
                : structure.getMessage();
    }

    private static boolean isHingeSelectionValid(
            GateCreationSession session,
            GateLeaf leaf,
            GateBlockPosition position
    ) {
        if (position == null) {
            return false;
        }

        GateSelectionData selection =
                session.getSelection(
                        position
                );

        return selection != null
                && selection.getLeaf()
                == leaf;
    }

    private static List<GatePartData> buildGateParts(
            Collection<GateSelectionData> selections,
            TileEntitySiegeGate controller
    ) {
        List<GatePartData> gateParts =
                new ArrayList<GatePartData>();

        for (GateSelectionData selection
                : selections) {
            GateBlockPosition position =
                    selection.getPosition();

            gateParts.add(
                    new GatePartData(
                            position.getX()
                                    - controller.xCoord,
                            position.getY()
                                    - controller.yCoord,
                            position.getZ()
                                    - controller.zCoord,
                            selection.getLeaf(),
                            selection
                                    .getSourceBlockName(),
                            selection
                                    .getSourceMetadata(),
                            selection
                                    .getSourceTileEntityNbt()
                    )
            );
        }

        return gateParts;
    }

    private static GateHinge toRelativeHinge(
            GateBlockPosition position,
            TileEntitySiegeGate controller
    ) {
        return position == null
                ? null
                : new GateHinge(
                position.getX()
                        - controller.xCoord,
                position.getZ()
                        - controller.zCoord
        );
    }

    private static void rollback(
            World world,
            List<GateSelectionData> converted
    ) {
        for (GateSelectionData selection
                : converted) {
            GateBlockPosition position =
                    selection.getPosition();

            int x = position.getX();
            int y = position.getY();
            int z = position.getZ();

            if (world.getBlock(x, y, z)
                    != SiegeRegistry.gatePart) {
                continue;
            }

            GateRegistry.unregisterGatePart(
                    world,
                    x,
                    y,
                    z
            );

            boolean restored =
                    world.setBlock(
                            x,
                            y,
                            z,
                            selection.getSourceBlock(),
                            selection
                                    .getSourceMetadata(),
                            3
                    );

            if (!restored) {
                continue;
            }

            restoreSourceTileEntity(
                    world,
                    selection
            );
        }
    }

    private static void restoreSourceTileEntity(
            World world,
            GateSelectionData selection
    ) {
        NBTTagCompound snapshot =
                selection
                        .getSourceTileEntityNbt();

        if (snapshot == null) {
            return;
        }

        GateBlockPosition position =
                selection.getPosition();

        int x = position.getX();
        int y = position.getY();
        int z = position.getZ();

        /*
         * TileEntity coordinates are persisted in normal TE NBT.
         * Force them to the actual restored position.
         */
        snapshot.setInteger(
                "x",
                x
        );

        snapshot.setInteger(
                "y",
                y
        );

        snapshot.setInteger(
                "z",
                z
        );

        try {
            TileEntity restored =
                    world.getTileEntity(
                            x,
                            y,
                            z
                    );

            if (restored != null) {
                GateSourceTileEntitySnapshot
                        .applyForRestoration(
                                restored,
                                snapshot
                        );

                restored.markDirty();

                world.markBlockForUpdate(
                        x,
                        y,
                        z
                );

                return;
            }

            /*
             * Some mod blocks do not create their TileEntity immediately
             * when setBlock is called. Reconstruct one from the registered
             * TE id when necessary.
             */
            restored =
                    GateSourceTileEntitySnapshot
                            .createForRestoration(
                                    snapshot
                            );

            if (restored != null) {
                world.setTileEntity(
                        x,
                        y,
                        z,
                        restored
                );

                restored.markDirty();

                world.markBlockForUpdate(
                        x,
                        y,
                        z
                );
            }

        } catch (RuntimeException ignored) {
            /*
             * The source block itself has already been restored.
             * Never let a malformed third-party TE throw out of rollback.
             */
        }
    }
}
