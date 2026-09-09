package com.enovak.lotrmoremobs.siege.banner;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lotr.common.entity.item.LOTREntityBanner;
import lotr.common.entity.item.LOTREntityBannerWall;
import lotr.common.item.LOTRItemBanner;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Durable sidecar for native LOTR banners attached to Siege Gate source
 * blocks.
 *
 * <p>The live LOTR banner entity is deliberately absent while the gate is a
 * finalized moving structure. Only inert entity NBT is retained here. That
 * keeps native appearance/ownership data available for restoration without
 * allowing a moving {@link LOTREntityBanner} to participate in LOTR banner
 * protection scans.</p>
 *
 * <p>This is intentionally separate from {@code SiegeGateOwnershipData}. The
 * gate ownership/removal journal remains the sole authority for block
 * restoration; this sidecar waits for those support blocks to be restored and
 * only then recreates the native banner entity.</p>
 */
public final class SiegeGateBannerAttachmentData extends WorldSavedData {

    public static final String DATA_NAME =
            "lotrmoremobs_siege_gate_banner_attachments";
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_GATE_RECORDS = 4096;
    private static final int MAX_ATTACHMENTS_PER_GATE =
            GateStructureValidator.MAX_GATE_PARTS * 4;
    private static final int MAX_RESTORES_PER_TICK = 16;

    private static final int TAG_INT = 3;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private static final String NBT_FORMAT_VERSION = "FormatVersion";
    private static final String NBT_GATES = "Gates";
    private static final String NBT_GATE_UUID = "GateUUID";
    private static final String NBT_DIMENSION = "Dimension";
    private static final String NBT_CONTROLLER_X = "ControllerX";
    private static final String NBT_CONTROLLER_Y = "ControllerY";
    private static final String NBT_CONTROLLER_Z = "ControllerZ";
    private static final String NBT_STATE = "State";
    private static final String NBT_ATTACHMENTS = "Attachments";
    private static final String NBT_KIND = "Kind";
    private static final String NBT_RELATIVE_SUPPORT_X = "RelativeSupportX";
    private static final String NBT_RELATIVE_SUPPORT_Y = "RelativeSupportY";
    private static final String NBT_RELATIVE_SUPPORT_Z = "RelativeSupportZ";
    private static final String NBT_LEAF = "Leaf";
    private static final String NBT_ENTITY_UUID = "EntityUUID";
    private static final String NBT_ENTITY = "EntityNBT";

    private final Map<UUID, GateRecord> recordsByGateUuid =
            new HashMap<UUID, GateRecord>();
    private boolean readOnlyDueToInvalidData;
    private int boundDimension = Integer.MIN_VALUE;

    public SiegeGateBannerAttachmentData(String name) {
        super(name);
    }

    public static SiegeGateBannerAttachmentData get(
            World world,
            boolean create
    ) {
        if (world == null || world.isRemote || world.perWorldStorage == null) {
            return null;
        }

        SiegeGateBannerAttachmentData data =
                (SiegeGateBannerAttachmentData)world.perWorldStorage.loadData(
                        SiegeGateBannerAttachmentData.class,
                        DATA_NAME
                );

        if (data == null && create) {
            data = new SiegeGateBannerAttachmentData(DATA_NAME);
            world.perWorldStorage.setData(DATA_NAME, data);
            data.markDirty();
        }

        if (data != null) {
            data.bindToDimension(world.provider.dimensionId);
        }

        return data;
    }

    /** Captures/removes native banners before any source block is converted. */
    public static PrepareResult prepareFinalization(
            World world,
            TileEntitySiegeGate controller,
            Collection<GatePartData> parts
    ) {
        if (world == null
                || world.isRemote
                || controller == null
                || parts == null) {
            return PrepareResult.failure(
                    "Gate banner capture could not start safely."
            );
        }

        SiegeGateBannerAttachmentData data = get(world, true);
        if (data == null) {
            return PrepareResult.failure(
                    "Gate banner attachment storage is unavailable."
            );
        }

        return data.prepareFinalizationInternal(world, controller, parts);
    }

    public static void commitFinalization(World world, UUID gateUuid) {
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data != null) {
            data.commitFinalizationInternal(gateUuid);
        }
    }

    public static void rollbackFinalization(World world, UUID gateUuid) {
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data != null) {
            data.rollbackFinalizationInternal(world, gateUuid);
        }
    }

    public static boolean beginRestoration(World world, UUID gateUuid) {
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data == null) {
            return true;
        }
        return data.beginRestorationInternal(gateUuid);
    }

    public static void abortRestoration(World world, UUID gateUuid) {
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data != null) {
            data.abortRestorationInternal(gateUuid);
        }
    }

    /** Read-only presence query used by removal and recovery guards. */
    public static boolean hasAttachments(World world, UUID gateUuid) {
        if (gateUuid == null) {
            return false;
        }
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data == null) {
            return false;
        }
        synchronized (data) {
            if (data.readOnlyDueToInvalidData) {
                return true;
            }
            GateRecord record = data.recordsByGateUuid.get(gateUuid);
            return record != null && !record.attachments.isEmpty();
        }
    }

    public static boolean hasAttachmentAtSupport(
            World world,
            UUID gateUuid,
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        if (gateUuid == null) {
            return false;
        }
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data == null) {
            return false;
        }
        synchronized (data) {
            if (data.readOnlyDueToInvalidData) {
                return true;
            }
            GateRecord record = data.recordsByGateUuid.get(gateUuid);
            if (record == null) {
                return false;
            }
            for (Attachment attachment : record.attachments) {
                if (attachment.relativeSupportX == relativeX
                        && attachment.relativeSupportY == relativeY
                        && attachment.relativeSupportZ == relativeZ) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Read-only edit admission check. Existing attachments may keep their
     * support, change between LEFT/RIGHT, or be detached by removing their
     * support part. A support may not become SPLIT_CENTER, and an edit may not
     * absorb a new source block that currently supports a live native LOTR
     * banner; move/remove that banner first so it cannot remain as a live
     * protection entity while the block becomes part of the moving gate.
     */
    public static boolean isEditTargetCompatible(
            World world,
            UUID gateUuid,
            int controllerX,
            int controllerY,
            int controllerZ,
            Collection<GatePartData> targetParts,
            Collection<GatePartData> addedParts
    ) {
        if (world == null || world.isRemote || gateUuid == null
                || targetParts == null || addedParts == null) {
            return false;
        }

        SiegeGateBannerAttachmentData data = get(world, false);
        if (data != null) {
            synchronized (data) {
                if (data.readOnlyDueToInvalidData) {
                    return false;
                }
                GateRecord record = data.recordsByGateUuid.get(gateUuid);
                if (record != null) {
                    if (record.state != RecordState.ATTACHED) {
                        return false;
                    }
                    Map<SupportPosition, GateLeaf> targetLeaves =
                            buildRelativeLeafMap(targetParts);
                    for (Attachment attachment : record.attachments) {
                        GateLeaf targetLeaf = targetLeaves.get(
                                new SupportPosition(
                                        attachment.relativeSupportX,
                                        attachment.relativeSupportY,
                                        attachment.relativeSupportZ
                                )
                        );
                        if (targetLeaf != null && targetLeaf.isSplitCenter()) {
                            return false;
                        }
                    }
                }
            }
        }

        return !hasLiveBannerOnAddedSupport(
                world,
                controllerX,
                controllerY,
                controllerZ,
                addedParts
        );
    }

    /** Called after the ordinary Siege Gate block journal each server tick. */
    public static void process(World world) {
        SiegeGateBannerAttachmentData data = get(world, false);
        if (data != null) {
            data.processInternal(world);
        }
    }

    private synchronized PrepareResult prepareFinalizationInternal(
            World world,
            TileEntitySiegeGate controller,
            Collection<GatePartData> parts
    ) {
        if (readOnlyDueToInvalidData) {
            return PrepareResult.failure(
                    "Gate banner attachment data is read-only; manual recovery is required."
            );
        }

        UUID gateUuid = controller.getGateUuid();
        if (gateUuid == null) {
            return PrepareResult.failure(
                    "The Siege Gate has no stable identity for banner capture."
            );
        }
        if (recordsByGateUuid.containsKey(gateUuid)) {
            return PrepareResult.failure(
                    "This Siege Gate already has pending banner attachment data."
            );
        }
        if (recordsByGateUuid.size() >= MAX_GATE_RECORDS) {
            return PrepareResult.failure(
                    "Siege Gate banner attachment storage is full."
            );
        }

        Map<SupportPosition, GateLeaf> selectedSupports =
                new HashMap<SupportPosition, GateLeaf>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (GatePartData part : parts) {
            if (part == null
                    || !part.hasValidAbsolutePosition(
                            controller.xCoord,
                            controller.yCoord,
                            controller.zCoord
                    )) {
                return PrepareResult.failure(
                        "Gate banner capture found an invalid gate-part position."
                );
            }

            int x = controller.xCoord + part.getRelativeX();
            int y = controller.yCoord + part.getRelativeY();
            int z = controller.zCoord + part.getRelativeZ();
            selectedSupports.put(
                    new SupportPosition(x, y, z),
                    part.getLeaf()
            );

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        if (selectedSupports.isEmpty()) {
            return PrepareResult.success(false);
        }

        AxisAlignedBB searchBounds = AxisAlignedBB.getBoundingBox(
                minX - 2.0D,
                minY - 2.0D,
                minZ - 2.0D,
                maxX + 3.0D,
                maxY + 5.0D,
                maxZ + 3.0D
        );

        List<Attachment> attachments = new ArrayList<Attachment>();
        List<Entity> entitiesToRemove = new ArrayList<Entity>();

        String captureError = captureStandingBanners(
                world,
                controller,
                selectedSupports,
                searchBounds,
                attachments,
                entitiesToRemove
        );
        if (captureError == null) {
            captureError = captureWallBanners(
                    world,
                    controller,
                    selectedSupports,
                    searchBounds,
                    attachments,
                    entitiesToRemove
            );
        }
        if (captureError != null) {
            return PrepareResult.failure(captureError);
        }
        if (attachments.isEmpty()) {
            return PrepareResult.success(false);
        }
        if (attachments.size() > MAX_ATTACHMENTS_PER_GATE) {
            return PrepareResult.failure(
                    "Too many LOTR banner attachments are connected to this gate."
            );
        }

        GateRecord record = new GateRecord(
                gateUuid,
                world.provider.dimensionId,
                controller.xCoord,
                controller.yCoord,
                controller.zCoord,
                RecordState.PREPARED,
                attachments
        );
        recordsByGateUuid.put(gateUuid, record);
        markDirty();

        try {
            for (Entity entity : entitiesToRemove) {
                if (entity != null && !entity.isDead) {
                    /* Avoid native damage/onBroken/drop paths. */
                    world.removeEntity(entity);
                }
            }
        } catch (RuntimeException exception) {
            record.state = RecordState.RESTORING;
            markDirty();
            restoreRecord(world, record, Integer.MAX_VALUE);
            return PrepareResult.failure(
                    "Gate banner capture failed; captured banners were queued for restoration."
            );
        }

        return PrepareResult.success(true);
    }

    private String captureStandingBanners(
            World world,
            TileEntitySiegeGate controller,
            Map<SupportPosition, GateLeaf> selectedSupports,
            AxisAlignedBB searchBounds,
            List<Attachment> attachments,
            List<Entity> entitiesToRemove
    ) {
        @SuppressWarnings("unchecked")
        List<LOTREntityBanner> banners = world.getEntitiesWithinAABB(
                LOTREntityBanner.class,
                searchBounds
        );

        for (LOTREntityBanner banner : banners) {
            if (banner == null || banner.isDead) {
                continue;
            }

            SupportPosition support = new SupportPosition(
                    MathHelper.floor_double(banner.posX),
                    MathHelper.floor_double(banner.boundingBox.minY) - 1,
                    MathHelper.floor_double(banner.posZ)
            );
            GateLeaf leaf = selectedSupports.get(support);
            if (leaf == null) {
                continue;
            }
            if (leaf.isSplitCenter()) {
                return "A LOTR banner is supported by a split-center gate block; "
                        + "move the banner or assign that block to one leaf before finalizing.";
            }

            Attachment attachment = captureAttachment(
                    banner,
                    AttachmentKind.STANDING,
                    support,
                    leaf,
                    controller
            );
            if (attachment == null) {
                return "A LOTR standing banner could not be captured safely.";
            }
            attachments.add(attachment);
            entitiesToRemove.add(banner);
        }

        return null;
    }

    private String captureWallBanners(
            World world,
            TileEntitySiegeGate controller,
            Map<SupportPosition, GateLeaf> selectedSupports,
            AxisAlignedBB searchBounds,
            List<Attachment> attachments,
            List<Entity> entitiesToRemove
    ) {
        @SuppressWarnings("unchecked")
        List<LOTREntityBannerWall> banners = world.getEntitiesWithinAABB(
                LOTREntityBannerWall.class,
                searchBounds
        );

        for (LOTREntityBannerWall banner : banners) {
            if (banner == null || banner.isDead) {
                continue;
            }

            NBTTagCompound entityNbt = new NBTTagCompound();
            try {
                banner.writeToNBT(entityNbt);
            } catch (RuntimeException exception) {
                return "A LOTR wall banner could not be captured safely.";
            }

            /*
             * EntityHanging persists its authoritative support block as
             * TileX/TileY/TileZ. Read that detached snapshot instead of
             * depending on inherited MCP field visibility.
             */
            if (!entityNbt.hasKey("TileX", TAG_INT)
                    || !entityNbt.hasKey("TileY", TAG_INT)
                    || !entityNbt.hasKey("TileZ", TAG_INT)) {
                return "A LOTR wall banner has no valid hanging anchor.";
            }

            SupportPosition support = new SupportPosition(
                    entityNbt.getInteger("TileX"),
                    entityNbt.getInteger("TileY"),
                    entityNbt.getInteger("TileZ")
            );
            GateLeaf leaf = selectedSupports.get(support);
            if (leaf == null) {
                continue;
            }
            if (leaf.isSplitCenter()) {
                return "A LOTR wall banner is supported by a split-center gate block; "
                        + "move the banner or assign that block to one leaf before finalizing.";
            }

            UUID entityUuid = banner.getUniqueID();
            if (entityUuid == null) {
                return "A LOTR wall banner could not be captured safely.";
            }
            attachments.add(new Attachment(
                    AttachmentKind.WALL,
                    support.x - controller.xCoord,
                    support.y - controller.yCoord,
                    support.z - controller.zCoord,
                    leaf,
                    entityUuid,
                    entityNbt,
                    false
            ));
            entitiesToRemove.add(banner);
        }

        return null;
    }

    private Attachment captureAttachment(
            Entity entity,
            AttachmentKind kind,
            SupportPosition support,
            GateLeaf leaf,
            TileEntitySiegeGate controller
    ) {
        try {
            NBTTagCompound entityNbt = new NBTTagCompound();
            entity.writeToNBT(entityNbt);
            UUID entityUuid = entity.getUniqueID();
            if (entityUuid == null) {
                return null;
            }

            return new Attachment(
                    kind,
                    support.x - controller.xCoord,
                    support.y - controller.yCoord,
                    support.z - controller.zCoord,
                    leaf,
                    entityUuid,
                    entityNbt,
                    false
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private synchronized void commitFinalizationInternal(UUID gateUuid) {
        if (gateUuid == null || readOnlyDueToInvalidData) {
            return;
        }
        GateRecord record = recordsByGateUuid.get(gateUuid);
        if (record != null && record.state == RecordState.PREPARED) {
            record.state = RecordState.ATTACHED;
            markDirty();
        }
    }

    private synchronized void rollbackFinalizationInternal(
            World world,
            UUID gateUuid
    ) {
        if (world == null || gateUuid == null || readOnlyDueToInvalidData) {
            return;
        }
        GateRecord record = recordsByGateUuid.get(gateUuid);
        if (record == null) {
            return;
        }
        record.state = RecordState.RESTORING;
        markDirty();
        restoreRecord(world, record, Integer.MAX_VALUE);
    }

    private synchronized boolean beginRestorationInternal(UUID gateUuid) {
        if (gateUuid == null) {
            return true;
        }
        if (readOnlyDueToInvalidData) {
            return false;
        }
        GateRecord record = recordsByGateUuid.get(gateUuid);
        if (record == null) {
            return true;
        }
        if (record.state == RecordState.PREPARED) {
            return false;
        }
        if (record.state != RecordState.RESTORING) {
            record.state = RecordState.RESTORING;
            markDirty();
        }
        return true;
    }

    private synchronized void abortRestorationInternal(UUID gateUuid) {
        if (gateUuid == null || readOnlyDueToInvalidData) {
            return;
        }
        GateRecord record = recordsByGateUuid.get(gateUuid);
        if (record != null && record.state == RecordState.RESTORING) {
            record.state = RecordState.ATTACHED;
            for (Attachment attachment : record.attachments) {
                attachment.restored = false;
            }
            markDirty();
        }
    }

    private synchronized void processInternal(World world) {
        if (world == null || world.isRemote || readOnlyDueToInvalidData) {
            return;
        }

        int remainingRestores = MAX_RESTORES_PER_TICK;
        List<GateRecord> records =
                new ArrayList<GateRecord>(recordsByGateUuid.values());

        for (GateRecord record : records) {
            if (record.dimension != world.provider.dimensionId) {
                rejectLoadedData(
                        "banner attachment record dimension does not match storage"
                );
                return;
            }

            if (record.state == RecordState.PREPARED) {
                TileEntity tileEntity = world.blockExists(
                        record.controllerX,
                        record.controllerY,
                        record.controllerZ
                )
                        ? world.getTileEntity(
                        record.controllerX,
                        record.controllerY,
                        record.controllerZ
                )
                        : null;

                if (tileEntity instanceof TileEntitySiegeGate) {
                    TileEntitySiegeGate gate = (TileEntitySiegeGate)tileEntity;
                    if (record.gateUuid.equals(gate.getExistingGateUuid())
                            && gate.isFinalized()) {
                        record.state = RecordState.ATTACHED;
                        markDirty();
                        continue;
                    }
                }

                if (hasAnyGatePartSupport(world, record)) {
                    continue;
                }

                record.state = RecordState.RESTORING;
                markDirty();
            }

            if (record.state == RecordState.ATTACHED) {
                int used = reconcileAttachedRecord(
                        world,
                        record,
                        remainingRestores
                );
                remainingRestores -= used;
                if (recordsByGateUuid.get(record.gateUuid) == record) {
                    syncRenderAttachments(world, record);
                }
                if (remainingRestores <= 0) {
                    break;
                }
                continue;
            }

            if (record.state == RecordState.RESTORING
                    && remainingRestores > 0) {
                int used = restoreRecord(
                        world,
                        record,
                        remainingRestores
                );
                remainingRestores -= used;
            }

            if (remainingRestores <= 0) {
                break;
            }
        }
    }

    /**
     * Reconciles durable banner attachments against the controller revision
     * after the existing edit journal has run for this tick. Leaf-only edits
     * update the inert attachment in place. Removing the exact support part
     * detaches the banner and restores the native entity once the edit journal
     * has restored that support block.
     */
    private int reconcileAttachedRecord(
            World world,
            GateRecord record,
            int restoreBudget
    ) {
        if (world == null || record == null || restoreBudget < 0
                || !world.blockExists(
                        record.controllerX,
                        record.controllerY,
                        record.controllerZ
                )) {
            return 0;
        }

        TileEntity tileEntity = world.getTileEntity(
                record.controllerX,
                record.controllerY,
                record.controllerZ
        );
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            return 0;
        }

        TileEntitySiegeGate controller = (TileEntitySiegeGate)tileEntity;
        if (!record.gateUuid.equals(controller.getExistingGateUuid())
                || !controller.isFinalized()) {
            return 0;
        }

        Map<SupportPosition, GateLeaf> liveLeaves =
                buildRelativeLeafMap(controller.getGateParts());
        int used = 0;
        boolean changed = false;

        for (int i = record.attachments.size() - 1; i >= 0; --i) {
            Attachment attachment = record.attachments.get(i);
            SupportPosition support = new SupportPosition(
                    attachment.relativeSupportX,
                    attachment.relativeSupportY,
                    attachment.relativeSupportZ
            );
            GateLeaf liveLeaf = liveLeaves.get(support);

            if (liveLeaf != null) {
                if (liveLeaf.isSplitCenter()) {
                    /* Admission forbids this; preserve the prior leaf fail-safe. */
                    FMLLog.severe(
                            "[LOTRMoreMobs] Siege Gate %s banner support at "
                                    + "%d,%d,%d became SPLIT_CENTER unexpectedly; "
                                    + "the prior banner leaf was preserved.",
                            record.gateUuid,
                            record.controllerX + attachment.relativeSupportX,
                            record.controllerY + attachment.relativeSupportY,
                            record.controllerZ + attachment.relativeSupportZ
                    );
                    continue;
                }
                if (attachment.leaf != liveLeaf) {
                    attachment.leaf = liveLeaf;
                    changed = true;
                }
                continue;
            }

            if (used >= restoreBudget) {
                continue;
            }

            RestoreResult result = restoreAttachment(
                    world,
                    record,
                    attachment
            );
            if (result == RestoreResult.WAIT) {
                continue;
            }

            ++used;
            record.attachments.remove(i);
            changed = true;
        }

        if (changed) {
            record.renderSnapshots = null;
            markDirty();
        }

        if (record.attachments.isEmpty()) {
            controller.setBannerRenderAttachments(
                    Collections.<RenderAttachmentSnapshot>emptyList()
            );
            recordsByGateUuid.remove(record.gateUuid);
            markDirty();
        }

        return used;
    }

    private static Map<SupportPosition, GateLeaf> buildRelativeLeafMap(
            Collection<GatePartData> parts
    ) {
        Map<SupportPosition, GateLeaf> leaves =
                new HashMap<SupportPosition, GateLeaf>();
        if (parts == null) {
            return leaves;
        }
        for (GatePartData part : parts) {
            if (part != null && part.getLeaf() != null) {
                leaves.put(
                        new SupportPosition(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        ),
                        part.getLeaf()
                );
            }
        }
        return leaves;
    }

    private static boolean hasLiveBannerOnAddedSupport(
            World world,
            int controllerX,
            int controllerY,
            int controllerZ,
            Collection<GatePartData> addedParts
    ) {
        if (addedParts == null || addedParts.isEmpty()) {
            return false;
        }

        Map<SupportPosition, Boolean> supports =
                new HashMap<SupportPosition, Boolean>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (GatePartData part : addedParts) {
            if (part == null) {
                continue;
            }
            int x = controllerX + part.getRelativeX();
            int y = controllerY + part.getRelativeY();
            int z = controllerZ + part.getRelativeZ();
            supports.put(new SupportPosition(x, y, z), Boolean.TRUE);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        if (supports.isEmpty()) {
            return false;
        }

        AxisAlignedBB bounds = AxisAlignedBB.getBoundingBox(
                minX - 2.0D,
                minY - 2.0D,
                minZ - 2.0D,
                maxX + 3.0D,
                maxY + 5.0D,
                maxZ + 3.0D
        );

        @SuppressWarnings("unchecked")
        List<LOTREntityBanner> standing = world.getEntitiesWithinAABB(
                LOTREntityBanner.class,
                bounds
        );
        for (LOTREntityBanner banner : standing) {
            if (banner == null || banner.isDead) {
                continue;
            }
            SupportPosition support = new SupportPosition(
                    MathHelper.floor_double(banner.posX),
                    MathHelper.floor_double(banner.boundingBox.minY) - 1,
                    MathHelper.floor_double(banner.posZ)
            );
            if (supports.containsKey(support)) {
                return true;
            }
        }

        @SuppressWarnings("unchecked")
        List<LOTREntityBannerWall> wall = world.getEntitiesWithinAABB(
                LOTREntityBannerWall.class,
                bounds
        );
        for (LOTREntityBannerWall banner : wall) {
            if (banner == null || banner.isDead) {
                continue;
            }
            try {
                NBTTagCompound nbt = new NBTTagCompound();
                banner.writeToNBT(nbt);
                if (nbt.hasKey("TileX", TAG_INT)
                        && nbt.hasKey("TileY", TAG_INT)
                        && nbt.hasKey("TileZ", TAG_INT)
                        && supports.containsKey(new SupportPosition(
                                nbt.getInteger("TileX"),
                                nbt.getInteger("TileY"),
                                nbt.getInteger("TileZ")
                        ))) {
                    return true;
                }
            } catch (RuntimeException exception) {
                /* Fail closed if a nearby wall banner cannot be inspected. */
                return true;
            }
        }

        return false;
    }

    /**
     * Copies only the visual subset of the durable native entity snapshot onto
     * the loaded controller. The controller then persists/synchronizes that
     * inert render data to clients. No LOTR banner entity is spawned here.
     */
    private void syncRenderAttachments(World world, GateRecord record) {
        if (world == null
                || record == null
                || !world.blockExists(
                        record.controllerX,
                        record.controllerY,
                        record.controllerZ
                )) {
            return;
        }

        TileEntity tileEntity = world.getTileEntity(
                record.controllerX,
                record.controllerY,
                record.controllerZ
        );
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            return;
        }

        TileEntitySiegeGate controller = (TileEntitySiegeGate)tileEntity;
        if (!record.gateUuid.equals(controller.getExistingGateUuid())
                || !controller.isFinalized()) {
            return;
        }

        controller.setBannerRenderAttachments(
                buildRenderSnapshots(world, record)
        );
    }

    private List<RenderAttachmentSnapshot> buildRenderSnapshots(
            World world,
            GateRecord record
    ) {
        if (record.renderSnapshots != null) {
            return record.renderSnapshots;
        }

        List<RenderAttachmentSnapshot> snapshots =
                new ArrayList<RenderAttachmentSnapshot>(
                        record.attachments.size()
                );

        for (Attachment attachment : record.attachments) {
            RenderAttachmentSnapshot snapshot =
                    buildRenderSnapshot(world, record, attachment);
            if (snapshot != null) {
                snapshots.add(snapshot);
            } else {
                FMLLog.warning(
                        "[LOTRMoreMobs] Siege Gate banner attachment %s has "
                                + "valid restoration data but no renderable visual snapshot.",
                        attachment.entityUuid
                );
            }
        }

        record.renderSnapshots = Collections.unmodifiableList(snapshots);
        return record.renderSnapshots;
    }

    private RenderAttachmentSnapshot buildRenderSnapshot(
            World world,
            GateRecord record,
            Attachment attachment
    ) {
        if (world == null || record == null || attachment == null) {
            return null;
        }

        Entity detached = attachment.kind == AttachmentKind.STANDING
                ? new LOTREntityBanner(world)
                : new LOTREntityBannerWall(world);
        try {
            detached.readFromNBT(
                    (NBTTagCompound)attachment.entityNbt.copy()
            );

            LOTRItemBanner.BannerType bannerType;
            if (detached instanceof LOTREntityBanner) {
                bannerType = ((LOTREntityBanner)detached).getBannerType();
            } else if (detached instanceof LOTREntityBannerWall) {
                bannerType = ((LOTREntityBannerWall)detached).getBannerType();
            } else {
                return null;
            }

            if (bannerType == null) {
                return null;
            }

            return new RenderAttachmentSnapshot(
                    attachment.kind == AttachmentKind.STANDING
                            ? RenderKind.STANDING
                            : RenderKind.WALL,
                    attachment.relativeSupportX,
                    attachment.relativeSupportY,
                    attachment.relativeSupportZ,
                    attachment.leaf,
                    bannerType.bannerID,
                    detached.posX - record.controllerX,
                    detached.posY - record.controllerY,
                    detached.posZ - record.controllerZ,
                    detached.rotationYaw
            );
        } catch (RuntimeException exception) {
            return null;
        } finally {
            detached.setDead();
        }
    }

    private boolean hasAnyGatePartSupport(World world, GateRecord record) {
        for (Attachment attachment : record.attachments) {
            int x = record.controllerX + attachment.relativeSupportX;
            int y = record.controllerY + attachment.relativeSupportY;
            int z = record.controllerZ + attachment.relativeSupportZ;
            if (world.blockExists(x, y, z)
                    && world.getBlock(x, y, z) == SiegeRegistry.gatePart) {
                return true;
            }
        }
        return false;
    }

    /** Returns the number of actual restore attempts consumed. */
    private int restoreRecord(
            World world,
            GateRecord record,
            int budget
    ) {
        int used = 0;
        boolean changed = false;

        for (Attachment attachment : record.attachments) {
            if (attachment.restored) {
                continue;
            }
            if (used >= budget) {
                break;
            }

            RestoreResult result = restoreAttachment(world, record, attachment);
            if (result == RestoreResult.WAIT) {
                continue;
            }

            ++used;
            attachment.restored = true;
            changed = true;
        }

        if (changed) {
            markDirty();
        }

        boolean complete = true;
        for (Attachment attachment : record.attachments) {
            if (!attachment.restored) {
                complete = false;
                break;
            }
        }

        if (complete) {
            recordsByGateUuid.remove(record.gateUuid);
            markDirty();
        }

        return used;
    }

    private RestoreResult restoreAttachment(
            World world,
            GateRecord record,
            Attachment attachment
    ) {
        int supportX = record.controllerX + attachment.relativeSupportX;
        int supportY = record.controllerY + attachment.relativeSupportY;
        int supportZ = record.controllerZ + attachment.relativeSupportZ;

        if (!world.blockExists(supportX, supportY, supportZ)
                || world.getBlock(supportX, supportY, supportZ)
                == SiegeRegistry.gatePart) {
            return RestoreResult.WAIT;
        }

        AxisAlignedBB searchBounds = AxisAlignedBB.getBoundingBox(
                supportX - 2.0D,
                supportY - 3.0D,
                supportZ - 2.0D,
                supportX + 3.0D,
                supportY + 5.0D,
                supportZ + 3.0D
        );

        Class entityClass = attachment.kind == AttachmentKind.STANDING
                ? LOTREntityBanner.class
                : LOTREntityBannerWall.class;
        @SuppressWarnings("unchecked")
        List<Entity> existing = world.getEntitiesWithinAABB(
                entityClass,
                searchBounds
        );
        for (Entity entity : existing) {
            if (entity != null
                    && !entity.isDead
                    && attachment.entityUuid.equals(entity.getUniqueID())) {
                return RestoreResult.RESTORED;
            }
        }

        try {
            Entity restored;
            if (attachment.kind == AttachmentKind.STANDING) {
                if (!World.doesBlockHaveSolidTopSurface(
                        world,
                        supportX,
                        supportY,
                        supportZ
                )) {
                    return RestoreResult.WAIT;
                }
                restored = new LOTREntityBanner(world);
            } else {
                restored = new LOTREntityBannerWall(world);
            }

            restored.readFromNBT(
                    (NBTTagCompound)attachment.entityNbt.copy()
            );

            if (restored instanceof LOTREntityBannerWall
                    && !((LOTREntityBannerWall)restored).onValidSurface()) {
                restored.setDead();
                return RestoreResult.WAIT;
            }

            if (!world.spawnEntityInWorld(restored)) {
                restored.setDead();
                return RestoreResult.WAIT;
            }

            return RestoreResult.RESTORED;
        } catch (RuntimeException exception) {
            FMLLog.warning(
                    "[LOTRMoreMobs] Siege Gate banner restoration at %d,%d,%d "
                            + "will retry after %s.",
                    supportX,
                    supportY,
                    supportZ,
                    exception.getClass().getSimpleName()
            );
            return RestoreResult.WAIT;
        }
    }

    private synchronized void bindToDimension(int dimension) {
        if (boundDimension == dimension) {
            return;
        }
        if (boundDimension != Integer.MIN_VALUE) {
            rejectLoadedData(
                    "one banner attachment data instance was reused across dimensions"
            );
            return;
        }
        for (GateRecord record : recordsByGateUuid.values()) {
            if (record.dimension != dimension) {
                rejectLoadedData(
                        "banner attachment record dimension does not match storage"
                );
                return;
            }
        }
        boundDimension = dimension;
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        recordsByGateUuid.clear();
        readOnlyDueToInvalidData = false;
        boundDimension = Integer.MIN_VALUE;

        if (nbt == null
                || !nbt.hasKey(NBT_FORMAT_VERSION, TAG_INT)
                || nbt.getInteger(NBT_FORMAT_VERSION) != FORMAT_VERSION
                || !nbt.hasKey(NBT_GATES, TAG_LIST)) {
            rejectLoadedData("missing or unsupported banner attachment format");
            return;
        }

        NBTTagList gateList = (NBTTagList)nbt.getTag(NBT_GATES);
        if (gateList.tagCount() > 0
                && gateList.func_150303_d() != TAG_COMPOUND) {
            rejectLoadedData("banner attachment Gates contains non-compounds");
            return;
        }
        if (gateList.tagCount() > MAX_GATE_RECORDS) {
            rejectLoadedData("too many banner attachment gate records");
            return;
        }

        for (int i = 0; i < gateList.tagCount(); ++i) {
            NBTTagCompound gateNbt = gateList.getCompoundTagAt(i);
            GateRecord record = readGateRecord(gateNbt);
            if (record == null
                    || recordsByGateUuid.put(record.gateUuid, record) != null) {
                rejectLoadedData("invalid or duplicate banner attachment gate record");
                return;
            }
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound nbt) {
        if (nbt == null || readOnlyDueToInvalidData) {
            return;
        }

        nbt.setInteger(NBT_FORMAT_VERSION, FORMAT_VERSION);
        NBTTagList gateList = new NBTTagList();

        for (GateRecord record : recordsByGateUuid.values()) {
            NBTTagCompound gateNbt = new NBTTagCompound();
            gateNbt.setString(NBT_GATE_UUID, record.gateUuid.toString());
            gateNbt.setInteger(NBT_DIMENSION, record.dimension);
            gateNbt.setInteger(NBT_CONTROLLER_X, record.controllerX);
            gateNbt.setInteger(NBT_CONTROLLER_Y, record.controllerY);
            gateNbt.setInteger(NBT_CONTROLLER_Z, record.controllerZ);
            gateNbt.setString(NBT_STATE, record.state.name());

            NBTTagList attachmentList = new NBTTagList();
            for (Attachment attachment : record.attachments) {
                NBTTagCompound attachmentNbt = new NBTTagCompound();
                attachmentNbt.setString(NBT_KIND, attachment.kind.name());
                attachmentNbt.setInteger(
                        NBT_RELATIVE_SUPPORT_X,
                        attachment.relativeSupportX
                );
                attachmentNbt.setInteger(
                        NBT_RELATIVE_SUPPORT_Y,
                        attachment.relativeSupportY
                );
                attachmentNbt.setInteger(
                        NBT_RELATIVE_SUPPORT_Z,
                        attachment.relativeSupportZ
                );
                attachmentNbt.setString(NBT_LEAF, attachment.leaf.name());
                attachmentNbt.setString(
                        NBT_ENTITY_UUID,
                        attachment.entityUuid.toString()
                );
                attachmentNbt.setTag(
                        NBT_ENTITY,
                        attachment.entityNbt.copy()
                );
                attachmentList.appendTag(attachmentNbt);
            }

            gateNbt.setTag(NBT_ATTACHMENTS, attachmentList);
            gateList.appendTag(gateNbt);
        }

        nbt.setTag(NBT_GATES, gateList);
    }

    private GateRecord readGateRecord(NBTTagCompound nbt) {
        if (nbt == null
                || !nbt.hasKey(NBT_GATE_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_X, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Y, TAG_INT)
                || !nbt.hasKey(NBT_CONTROLLER_Z, TAG_INT)
                || !nbt.hasKey(NBT_STATE, TAG_STRING)
                || !nbt.hasKey(NBT_ATTACHMENTS, TAG_LIST)) {
            return null;
        }

        UUID gateUuid = parseUuid(nbt.getString(NBT_GATE_UUID));
        RecordState state = RecordState.fromName(nbt.getString(NBT_STATE));
        if (gateUuid == null || state == null) {
            return null;
        }

        NBTTagList attachmentList = (NBTTagList)nbt.getTag(NBT_ATTACHMENTS);
        if (attachmentList.tagCount() <= 0
                || attachmentList.tagCount() > MAX_ATTACHMENTS_PER_GATE
                || attachmentList.func_150303_d() != TAG_COMPOUND) {
            return null;
        }

        List<Attachment> attachments =
                new ArrayList<Attachment>(attachmentList.tagCount());
        for (int i = 0; i < attachmentList.tagCount(); ++i) {
            NBTTagCompound attachmentNbt =
                    attachmentList.getCompoundTagAt(i);
            Attachment attachment = readAttachment(attachmentNbt);
            if (attachment == null) {
                return null;
            }
            attachments.add(attachment);
        }

        return new GateRecord(
                gateUuid,
                nbt.getInteger(NBT_DIMENSION),
                nbt.getInteger(NBT_CONTROLLER_X),
                nbt.getInteger(NBT_CONTROLLER_Y),
                nbt.getInteger(NBT_CONTROLLER_Z),
                state,
                attachments
        );
    }

    private Attachment readAttachment(NBTTagCompound nbt) {
        if (nbt == null
                || !nbt.hasKey(NBT_KIND, TAG_STRING)
                || !nbt.hasKey(NBT_RELATIVE_SUPPORT_X, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_SUPPORT_Y, TAG_INT)
                || !nbt.hasKey(NBT_RELATIVE_SUPPORT_Z, TAG_INT)
                || !nbt.hasKey(NBT_LEAF, TAG_STRING)
                || !nbt.hasKey(NBT_ENTITY_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_ENTITY, TAG_COMPOUND)) {
            return null;
        }

        AttachmentKind kind = AttachmentKind.fromName(nbt.getString(NBT_KIND));
        GateLeaf leaf = GateLeaf.fromSerializedName(nbt.getString(NBT_LEAF));
        UUID entityUuid = parseUuid(nbt.getString(NBT_ENTITY_UUID));
        if (kind == null
                || leaf == null
                || leaf.isSplitCenter()
                || entityUuid == null) {
            return null;
        }

        NBTTagCompound entityNbt = nbt.getCompoundTag(NBT_ENTITY);
        if (entityNbt == null || entityNbt.hasNoTags()) {
            return null;
        }

        return new Attachment(
                kind,
                nbt.getInteger(NBT_RELATIVE_SUPPORT_X),
                nbt.getInteger(NBT_RELATIVE_SUPPORT_Y),
                nbt.getInteger(NBT_RELATIVE_SUPPORT_Z),
                leaf,
                entityUuid,
                entityNbt,
                false
        );
    }

    private void rejectLoadedData(String reason) {
        readOnlyDueToInvalidData = true;
        FMLLog.severe(
                "[LOTRMoreMobs] Siege Gate banner attachment data is read-only: %s",
                reason == null ? "invalid persisted data" : reason
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static final class PrepareResult {
        private final boolean successful;
        private final boolean attachmentsCaptured;
        private final String error;

        private PrepareResult(
                boolean successful,
                boolean attachmentsCaptured,
                String error
        ) {
            this.successful = successful;
            this.attachmentsCaptured = attachmentsCaptured;
            this.error = error;
        }

        static PrepareResult success(boolean attachmentsCaptured) {
            return new PrepareResult(true, attachmentsCaptured, null);
        }

        static PrepareResult failure(String error) {
            return new PrepareResult(false, false, error);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public boolean hasCapturedAttachments() {
            return attachmentsCaptured;
        }

        public String getError() {
            return error;
        }
    }

    private enum RecordState {
        PREPARED,
        ATTACHED,
        RESTORING;

        static RecordState fromName(String name) {
            if (name == null) {
                return null;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    public enum RenderKind {
        STANDING,
        WALL;

        static RenderKind fromName(String name) {
            if (name == null) {
                return null;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    /**
     * Minimal client-visible banner state. It intentionally excludes LOTR
     * protection, owner, whitelist, and permission NBT. Those values remain
     * only in the durable server-side attachment snapshot used for restoration.
     */
    public static final class RenderAttachmentSnapshot {
        private static final String NBT_RENDER_BANNER_TYPE = "BannerType";
        private static final String NBT_RENDER_ENTITY_X = "EntityX";
        private static final String NBT_RENDER_ENTITY_Y = "EntityY";
        private static final String NBT_RENDER_ENTITY_Z = "EntityZ";
        private static final String NBT_RENDER_YAW = "Yaw";
        private static final double MAX_RENDER_OFFSET = 64.0D;

        private final RenderKind kind;
        private final int relativeSupportX;
        private final int relativeSupportY;
        private final int relativeSupportZ;
        private final GateLeaf leaf;
        private final int bannerTypeId;
        private final double relativeEntityX;
        private final double relativeEntityY;
        private final double relativeEntityZ;
        private final float rotationYaw;

        private RenderAttachmentSnapshot(
                RenderKind kind,
                int relativeSupportX,
                int relativeSupportY,
                int relativeSupportZ,
                GateLeaf leaf,
                int bannerTypeId,
                double relativeEntityX,
                double relativeEntityY,
                double relativeEntityZ,
                float rotationYaw
        ) {
            this.kind = kind;
            this.relativeSupportX = relativeSupportX;
            this.relativeSupportY = relativeSupportY;
            this.relativeSupportZ = relativeSupportZ;
            this.leaf = leaf;
            this.bannerTypeId = bannerTypeId;
            this.relativeEntityX = relativeEntityX;
            this.relativeEntityY = relativeEntityY;
            this.relativeEntityZ = relativeEntityZ;
            this.rotationYaw = rotationYaw;
        }

        public RenderKind getKind() {
            return kind;
        }

        public int getRelativeSupportX() {
            return relativeSupportX;
        }

        public int getRelativeSupportY() {
            return relativeSupportY;
        }

        public int getRelativeSupportZ() {
            return relativeSupportZ;
        }

        public GateLeaf getLeaf() {
            return leaf;
        }

        public int getBannerTypeId() {
            return bannerTypeId;
        }

        public double getRelativeEntityX() {
            return relativeEntityX;
        }

        public double getRelativeEntityY() {
            return relativeEntityY;
        }

        public double getRelativeEntityZ() {
            return relativeEntityZ;
        }

        public float getRotationYaw() {
            return rotationYaw;
        }

        public NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString(NBT_KIND, kind.name());
            nbt.setInteger(NBT_RELATIVE_SUPPORT_X, relativeSupportX);
            nbt.setInteger(NBT_RELATIVE_SUPPORT_Y, relativeSupportY);
            nbt.setInteger(NBT_RELATIVE_SUPPORT_Z, relativeSupportZ);
            nbt.setString(NBT_LEAF, leaf.name());
            nbt.setInteger(NBT_RENDER_BANNER_TYPE, bannerTypeId);
            nbt.setDouble(NBT_RENDER_ENTITY_X, relativeEntityX);
            nbt.setDouble(NBT_RENDER_ENTITY_Y, relativeEntityY);
            nbt.setDouble(NBT_RENDER_ENTITY_Z, relativeEntityZ);
            nbt.setFloat(NBT_RENDER_YAW, rotationYaw);
            return nbt;
        }

        public static RenderAttachmentSnapshot fromNBT(
                NBTTagCompound nbt
        ) {
            if (nbt == null
                    || !nbt.hasKey(NBT_KIND, TAG_STRING)
                    || !nbt.hasKey(NBT_RELATIVE_SUPPORT_X, TAG_INT)
                    || !nbt.hasKey(NBT_RELATIVE_SUPPORT_Y, TAG_INT)
                    || !nbt.hasKey(NBT_RELATIVE_SUPPORT_Z, TAG_INT)
                    || !nbt.hasKey(NBT_LEAF, TAG_STRING)
                    || !nbt.hasKey(NBT_RENDER_BANNER_TYPE, TAG_INT)
                    || !nbt.hasKey(NBT_RENDER_ENTITY_X, TAG_DOUBLE)
                    || !nbt.hasKey(NBT_RENDER_ENTITY_Y, TAG_DOUBLE)
                    || !nbt.hasKey(NBT_RENDER_ENTITY_Z, TAG_DOUBLE)
                    || !nbt.hasKey(NBT_RENDER_YAW, TAG_FLOAT)) {
                return null;
            }

            RenderKind kind = RenderKind.fromName(nbt.getString(NBT_KIND));
            GateLeaf leaf = GateLeaf.fromSerializedName(nbt.getString(NBT_LEAF));
            int supportX = nbt.getInteger(NBT_RELATIVE_SUPPORT_X);
            int supportY = nbt.getInteger(NBT_RELATIVE_SUPPORT_Y);
            int supportZ = nbt.getInteger(NBT_RELATIVE_SUPPORT_Z);
            int bannerTypeId = nbt.getInteger(NBT_RENDER_BANNER_TYPE);
            double entityX = nbt.getDouble(NBT_RENDER_ENTITY_X);
            double entityY = nbt.getDouble(NBT_RENDER_ENTITY_Y);
            double entityZ = nbt.getDouble(NBT_RENDER_ENTITY_Z);
            float yaw = nbt.getFloat(NBT_RENDER_YAW);

            if (kind == null
                    || leaf == null
                    || leaf.isSplitCenter()
                    || Math.abs((long)supportX) > (long)MAX_RENDER_OFFSET
                    || Math.abs((long)supportY) > (long)MAX_RENDER_OFFSET
                    || Math.abs((long)supportZ) > (long)MAX_RENDER_OFFSET
                    || LOTRItemBanner.BannerType.forID(bannerTypeId) == null
                    || !isFiniteAndBounded(entityX)
                    || !isFiniteAndBounded(entityY)
                    || !isFiniteAndBounded(entityZ)
                    || Float.isNaN(yaw)
                    || Float.isInfinite(yaw)) {
                return null;
            }

            return new RenderAttachmentSnapshot(
                    kind,
                    supportX,
                    supportY,
                    supportZ,
                    leaf,
                    bannerTypeId,
                    entityX,
                    entityY,
                    entityZ,
                    yaw
            );
        }

        private static boolean isFiniteAndBounded(double value) {
            return !Double.isNaN(value)
                    && !Double.isInfinite(value)
                    && Math.abs(value) <= MAX_RENDER_OFFSET;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RenderAttachmentSnapshot)) {
                return false;
            }
            RenderAttachmentSnapshot snapshot =
                    (RenderAttachmentSnapshot)other;
            return kind == snapshot.kind
                    && relativeSupportX == snapshot.relativeSupportX
                    && relativeSupportY == snapshot.relativeSupportY
                    && relativeSupportZ == snapshot.relativeSupportZ
                    && leaf == snapshot.leaf
                    && bannerTypeId == snapshot.bannerTypeId
                    && Double.doubleToLongBits(relativeEntityX)
                    == Double.doubleToLongBits(snapshot.relativeEntityX)
                    && Double.doubleToLongBits(relativeEntityY)
                    == Double.doubleToLongBits(snapshot.relativeEntityY)
                    && Double.doubleToLongBits(relativeEntityZ)
                    == Double.doubleToLongBits(snapshot.relativeEntityZ)
                    && Float.floatToIntBits(rotationYaw)
                    == Float.floatToIntBits(snapshot.rotationYaw);
        }

        @Override
        public int hashCode() {
            int result = kind.hashCode();
            result = 31 * result + relativeSupportX;
            result = 31 * result + relativeSupportY;
            result = 31 * result + relativeSupportZ;
            result = 31 * result + leaf.hashCode();
            result = 31 * result + bannerTypeId;
            long bits = Double.doubleToLongBits(relativeEntityX);
            result = 31 * result + (int)(bits ^ (bits >>> 32));
            bits = Double.doubleToLongBits(relativeEntityY);
            result = 31 * result + (int)(bits ^ (bits >>> 32));
            bits = Double.doubleToLongBits(relativeEntityZ);
            result = 31 * result + (int)(bits ^ (bits >>> 32));
            result = 31 * result + Float.floatToIntBits(rotationYaw);
            return result;
        }
    }

    private enum AttachmentKind {
        STANDING,
        WALL;

        static AttachmentKind fromName(String name) {
            if (name == null) {
                return null;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private enum RestoreResult {
        WAIT,
        RESTORED
    }

    private static final class GateRecord {
        private final UUID gateUuid;
        private final int dimension;
        private final int controllerX;
        private final int controllerY;
        private final int controllerZ;
        private RecordState state;
        private final List<Attachment> attachments;
        private List<RenderAttachmentSnapshot> renderSnapshots;

        private GateRecord(
                UUID gateUuid,
                int dimension,
                int controllerX,
                int controllerY,
                int controllerZ,
                RecordState state,
                List<Attachment> attachments
        ) {
            this.gateUuid = gateUuid;
            this.dimension = dimension;
            this.controllerX = controllerX;
            this.controllerY = controllerY;
            this.controllerZ = controllerZ;
            this.state = state;
            this.attachments = attachments;
        }
    }

    private static final class Attachment {
        private final AttachmentKind kind;
        private final int relativeSupportX;
        private final int relativeSupportY;
        private final int relativeSupportZ;
        private GateLeaf leaf;
        private final UUID entityUuid;
        private final NBTTagCompound entityNbt;
        private boolean restored;

        private Attachment(
                AttachmentKind kind,
                int relativeSupportX,
                int relativeSupportY,
                int relativeSupportZ,
                GateLeaf leaf,
                UUID entityUuid,
                NBTTagCompound entityNbt,
                boolean restored
        ) {
            this.kind = kind;
            this.relativeSupportX = relativeSupportX;
            this.relativeSupportY = relativeSupportY;
            this.relativeSupportZ = relativeSupportZ;
            this.leaf = leaf;
            this.entityUuid = entityUuid;
            this.entityNbt = (NBTTagCompound)entityNbt.copy();
            this.restored = restored;
        }
    }

    private static final class SupportPosition {
        private final int x;
        private final int y;
        private final int z;

        private SupportPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SupportPosition)) {
                return false;
            }
            SupportPosition position = (SupportPosition)other;
            return x == position.x && y == position.y && z == position.z;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + z;
            return result;
        }
    }
}
