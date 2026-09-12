package com.enovak.lotrmoremobs.siege.tile;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.access.GateAccess;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.banner.SiegeGateBannerAttachmentData;
import com.enovak.lotrmoremobs.siege.gate.GateAnimation;
import com.enovak.lotrmoremobs.siege.gate.GateConfigurationValidator;
import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateHingeSide;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.gate.GatePrototype;
import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import com.enovak.lotrmoremobs.siege.gate.GateState;
import com.enovak.lotrmoremobs.siege.gate.GateControlMode;
import com.enovak.lotrmoremobs.siege.gate.GateStructureValidator;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateOwnershipData;
import com.enovak.lotrmoremobs.siege.gate.SiegeGateBreachRallyManager;
import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import com.enovak.lotrmoremobs.siege.repair.GateRepairStartResult;
import com.enovak.lotrmoremobs.siege.repair.SiegeCurrency;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.block.LOTRBlockGateDwarvenIthildin;
import lotr.common.fac.LOTRFaction;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.WorldServer;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.init.Blocks;

public class TileEntitySiegeGate extends TileEntity {

    public static final int DEFAULT_MAX_HEALTH = 1000;
    public static final int REPAIR_HP_PER_COIN = 10;
    public static final int REPAIR_HEALTH_PER_SECOND = 5;
    public static final int REPAIR_TICKS_PER_HEALTH =
            20 / REPAIR_HEALTH_PER_SECOND;

    /*
     * Siege damage temporarily interrupts an already-paid repair job.
     * Five seconds = 100 server ticks.
     */
    public static final int REPAIR_DAMAGE_PAUSE_TICKS = 100;
    public static final int BREACHED_RECOVERY_PERCENT = 20;
    public static final String DEFAULT_GATE_NAME = "Siege Gate";
    public static final int DEFAULT_REQUIRED_ALIGNMENT = 1;
    public static final int MAX_GATE_NAME_LENGTH = 32;
    public static final int MAX_ACCESS_ENTRIES = 128;
    public static final int PLAYER_ACCESS_LEVEL_ACCESS =
            0;

    public static final int PLAYER_ACCESS_LEVEL_EDITOR =
            1;
    public static final int MAX_REQUIRED_ALIGNMENT = 1000000;
    public static final int MAX_HEALTH_OVERRIDE = 1000000;
    public static final int RAM_RESERVATION_TIMEOUT_TICKS = 200;

    public static int getConfiguredDefaultMaxHealth() {
        return Math.max(
                1,
                Math.min(
                        MAX_HEALTH_OVERRIDE,
                        MumakilConfig.defaultGateHealth
                )
        );
    }

    private static final double BREACH_MESSAGE_RADIUS = 64.0D;
    private static final int MAX_BREACH_PARTICLE_POSITIONS = 6;

    private static final int BREACH_VISUAL_HOLD_TICKS = 10;
    private static final int BREACH_VISUAL_OPEN_TICKS = 30;

    private static final int CLIENT_HIT_RECOIL_TICKS = 6;
    private static final int CLIENT_BREACH_RECOIL_TICKS = 12;

    private static final float CLIENT_HIT_RECOIL_DISTANCE = 0.045F;
    private static final float CLIENT_BREACH_RECOIL_DISTANCE = 0.085F;
    private static final float CLIENT_BREACH_SAG_DISTANCE = 0.055F;
    private static final int REPAIR_SYNC_INTERVAL_TICKS = 20;
    private static final int REPAIR_BUILD_BURST_MIN_GAP_TICKS = 120;
    private static final int REPAIR_BUILD_BURST_MAX_GAP_TICKS = 240;
    private static final int REPAIR_BUILD_BURST_MIN_HIT_SPACING_TICKS = 4;
    private static final int REPAIR_BUILD_BURST_MAX_HIT_SPACING_TICKS = 7;
    private static final float CLIENT_HIT_GATE_FLEX_MAX = 0.03F;
    private static final String SOUND_GATE_BREACH =
            "lotrmoremobs:siege.gate_breach";
    private static final String SOUND_GATE_OPEN =
            "lotrmoremobs:siege.gate_open";
    private static final String SOUND_GATE_CLOSE =
            "lotrmoremobs:siege.gate_close";
    private static final String SOUND_GATE_REPAIR =
            "lotrmoremobs:siege.repair";
    private static final String SOUND_GATE_REPAIR_BUILD =
            "lotrmoremobs:siege.repair_build";
    private static final String NBT_CONTROLLER_APPEARANCE_BLOCK =
            "ControllerAppearanceBlock";

    private static final String NBT_CONTROLLER_APPEARANCE_META =
            "ControllerAppearanceMeta";

    private static final String DEFAULT_CONTROLLER_APPEARANCE_BLOCK =
            "minecraft:iron_block";
    private static final int MAX_SOURCE_BLOCK_NAME_LENGTH = 256;

    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private static final String NBT_GATE_UUID = "GateUUID";
    private static final String NBT_GATE_STATE = "GateState";
    private static final String NBT_GATE_CONTROL_MODE = "GateControlMode";
    private static final String NBT_GATE_STATE_START_TICK =
            "GateStateStartTick";
    private static final String NBT_CURRENT_HEALTH = "CurrentHealth";
    private static final String NBT_MAX_HEALTH = "MaxHealth";
    private static final String NBT_REPAIR_ACTIVE = "RepairActive";
    private static final String NBT_REPAIR_PURCHASED_HEALTH =
            "RepairPurchasedHealth";
    private static final String NBT_REPAIR_APPLIED_HEALTH =
            "RepairAppliedHealth";
    private static final String NBT_REPAIR_ACTIVE_TICKS =
            "RepairActiveTicks";
    private static final String NBT_REPAIR_PAUSE_UNTIL =
            "RepairPauseUntilTick";
    private static final String NBT_REPAIR_COIN_VALUE =
            "RepairPurchasedCoinValue";
    private static final String NBT_GATE_NAME = "GateName";
    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_GATE_FACTION = "GateFaction";
    private static final String NBT_REQUIRED_ALIGNMENT =
            "RequiredAlignment";
    private static final String NBT_FACTION_ACCESS_ENABLED =
            "FactionAccessEnabled";
    private static final String NBT_EDITORS = "Editors";
    private static final String NBT_OPERATORS = "Operators";
    private static final String NBT_ACCESS_WHITELIST = "AccessWhitelist";
    private static final String NBT_RESERVED_RAM_UUID = "ReservedRamUUID";
    private static final String NBT_GATE_PARTS = "GateParts";
    private static final String NBT_GATE_BANNER_RENDER_ATTACHMENTS =
            "GateBannerRenderAttachments";
    private static final String NBT_RELATIVE_X = "RelativeX";
    private static final String NBT_RELATIVE_Y = "RelativeY";
    private static final String NBT_RELATIVE_Z = "RelativeZ";
    private static final String NBT_LEAF = "Leaf";
    private static final String NBT_SOURCE_BLOCK = "SourceBlock";
    private static final String NBT_SOURCE_META = "SourceMeta";
    private static final String NBT_SOURCE_TILE_ENTITY =
            "SourceTileEntity";
    private static final String NBT_GATE_HINGES = "GateHinges";
    private static final String NBT_LEFT_HINGE = "Left";
    private static final String NBT_RIGHT_HINGE = "Right";
    private static final String NBT_HINGE_X = "RelativeX";
    private static final String NBT_HINGE_Z = "RelativeZ";
    private static final String NBT_HINGE_SIDE = "Side";
    private static final String NBT_GATE_ORIENTATION = "GateOrientation";
    private static final String NBT_OPENING_DIRECTION = "OpeningDirection";
    private static final String NBT_GATE_BORDER_TEXTURE_ENABLED =
            "GateBorderTextureEnabled";
    private static final String NBT_STRUCTURE_QUARANTINED =
            "GateStructureQuarantined";
    private static final String NBT_STRUCTURE_QUARANTINE_REASON =
            "GateStructureQuarantineReason";
    private static final String NBT_STRUCTURE_REVISION =
            "StructureRevision";
    private String controllerAppearanceBlockName =
            DEFAULT_CONTROLLER_APPEARANCE_BLOCK;
    private static final String NBT_CONTROLLER_APPEARANCE_CUSTOMIZED =
            "ControllerAppearanceCustomized";

    private int controllerAppearanceMetadata;

    private UUID gateUuid;
    private GateState gateState = GateState.CLOSED;
    private GateControlMode gateControlMode = GateControlMode.AUTOMATIC;
    private long gateStateStartTick;
    private boolean gateStateTimingPresent;
    private int currentHealth = getConfiguredDefaultMaxHealth();
    private int maxHealth = getConfiguredDefaultMaxHealth();
    private boolean repairActive;
    private int repairPurchasedHealth;
    private int repairAppliedHealth;
    private int repairActiveTicks;
    private long repairPauseUntilTick;
    private int repairPurchasedCoinValue;
    private int repairBuildSoundTicksUntilNext;
    private int repairBuildSoundHitsRemaining;
    private String gateName = DEFAULT_GATE_NAME;
    private UUID ownerUuid;
    private LOTRFaction gateFaction;
    private int requiredAlignment = DEFAULT_REQUIRED_ALIGNMENT;
    private boolean factionAccessEnabled = true;
    private final Set<UUID> editorUuids = new HashSet<UUID>();
    private final Set<UUID> operatorUuids = new HashSet<UUID>();
    private final Set<UUID> accessWhitelistUuids = new HashSet<UUID>();
    private UUID reservedRamUuid;
    private long reservedRamLastSeenTick;
    private final List<GatePartData> gateParts =
            new ArrayList<GatePartData>();
    private final List<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>
            bannerRenderAttachments =
            new ArrayList<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>();
    private final Map<RelativePosition, GatePartData> gatePartsByPosition =
            new HashMap<RelativePosition, GatePartData>();
    private GateHinge leftHinge;
    private GateHinge rightHinge;
    private GateOrientation gateOrientation;
    private GateOpeningDirection openingDirection;
    private boolean gateBorderTextureEnabled = true;
    private boolean hingeConfigurationValid;
    private boolean gateStructureTagPresent;
    private boolean gateStructureQuarantined;
    private String gateStructureQuarantineReason;
    private boolean quarantineDiagnosticLogged;
    private boolean persistentOwnershipSuspended;
    private int structureRevision;
    private int renderDataRevision;
    private AxisAlignedBB cachedRenderBoundingBox;
    private boolean controllerAppearanceCustomized;

    private long clientImpactStartTick = Long.MIN_VALUE;
    private boolean clientImpactBreached;
    private float clientImpactStrength = 1.0F;

    @Override
    public void validate() {
        super.validate();
        ensureGateUuid();
        if (worldObj != null && !worldObj.isRemote) {
            if (!gateStructureQuarantined) {
                GatePrototype.migrateLegacyPrototype(this);
                normalizeLoadedHealthState();
                repairLoadedAnimationState();
                ensureStructureRevision();
                if (reservedRamUuid != null) {
                    reservedRamLastSeenTick = worldObj.getTotalWorldTime();
                }
                SiegeGateOwnershipData ownership =
                        SiegeGateOwnershipData.get(worldObj, true);
                if (ownership != null && !gateParts.isEmpty()) {
                    ownership.recoverLoadedController(
                            worldObj,
                            getGateUuid(),
                            structureRevision,
                            xCoord,
                            yCoord,
                            zCoord
                    );
                    persistentOwnershipSuspended =
                            !ownership.registerOrUpdateController(this);
                }
            } else {
                persistentOwnershipSuspended = true;
                logQuarantineOnce();
                SiegeGateOwnershipData ownership =
                        SiegeGateOwnershipData.get(worldObj, false);
                if (ownership != null) {
                    ownership.markControllerQuarantined(
                            worldObj.provider.dimensionId,
                            getGateUuid(),
                            xCoord,
                            yCoord,
                            zCoord
                    );
                }
            }
        }
        rebuildRegistryLinks();
        refreshGateWorldLighting();
        markGatePartsForRenderUpdate();
    }

    @Override
    public void invalidate() {
        Main.proxy.releaseGateRenderCache(this);
        if (worldObj != null) {
            GateRegistry.unregisterController(
                    worldObj,
                    xCoord,
                    yCoord,
                    zCoord
            );
            markGatePartsForRenderUpdate();
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        Main.proxy.releaseGateRenderCache(this);
        if (worldObj != null) {
            GateRegistry.unregisterController(
                    worldObj,
                    xCoord,
                    yCoord,
                    zCoord
            );
            markGatePartsForRenderUpdate();
        }
        super.onChunkUnload();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        UUID persistentUuid = ensureGateUuid();
        if (persistentUuid != null) {
            nbt.setString(NBT_GATE_UUID, persistentUuid.toString());
        }
        nbt.setString(NBT_GATE_STATE, gateState.name());
        nbt.setString(NBT_GATE_CONTROL_MODE, gateControlMode.name());
        nbt.setLong(NBT_GATE_STATE_START_TICK, gateStateStartTick);
        nbt.setInteger(NBT_CURRENT_HEALTH, currentHealth);
        nbt.setInteger(NBT_MAX_HEALTH, maxHealth);
        writeRepairStateToNBT(nbt);
        writeAccessStateToNBT(nbt);
        writeControllerAppearanceToNBT(nbt);
        writeGateStructureToNBT(nbt, true, true);
        writeBannerRenderAttachmentsToNBT(nbt);
        writeGateConfigurationToNBT(nbt);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);

        gateUuid = readGateUuid(nbt);
        ensureGateUuid();

        gateState = GateState.fromSerializedName(nbt.getString(NBT_GATE_STATE));
        gateControlMode = GateControlMode.fromSerializedName(
                nbt.getString(NBT_GATE_CONTROL_MODE)
        );
        gateStateTimingPresent = nbt.hasKey(
                NBT_GATE_STATE_START_TICK,
                TAG_LONG
        );
        gateStateStartTick = gateStateTimingPresent
                ? nbt.getLong(NBT_GATE_STATE_START_TICK)
                : 0L;
        readHealthFromNBT(nbt);
        readRepairStateFromNBT(nbt);
        readAccessStateFromNBT(nbt);
        readControllerAppearanceFromNBT(nbt);
        readGateDataFromNBT(nbt);
        readBannerRenderAttachmentsFromNBT(nbt);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound syncData = new NBTTagCompound();
        syncData.setString(NBT_GATE_STATE, gateState.name());
        syncData.setString(NBT_GATE_CONTROL_MODE, gateControlMode.name());
        syncData.setLong(NBT_GATE_STATE_START_TICK, gateStateStartTick);
        syncData.setInteger(NBT_CURRENT_HEALTH, currentHealth);
        syncData.setInteger(NBT_MAX_HEALTH, maxHealth);
        writeRepairStateToNBT(syncData);
        writeAccessStateToNBT(syncData);
        writeControllerAppearanceToNBT(syncData);
        writeGateStructureToNBT(syncData, true, false);
        writeBannerRenderAttachmentsToNBT(syncData);
        writeGateConfigurationToNBT(syncData);
        return new S35PacketUpdateTileEntity(
                xCoord,
                yCoord,
                zCoord,
                0,
                syncData
        );
    }

    @Override
    public void onDataPacket(
            NetworkManager networkManager,
            S35PacketUpdateTileEntity packet
    ) {
        markGatePartsForRenderUpdate();
        NBTTagCompound syncData = packet.func_148857_g();
        gateState = GateState.fromSerializedName(
                syncData.getString(NBT_GATE_STATE)
        );
        gateControlMode = GateControlMode.fromSerializedName(
                syncData.getString(NBT_GATE_CONTROL_MODE)
        );
        readSynchronizedStateTiming(syncData);
        readHealthFromNBT(syncData);
        readRepairStateFromNBT(syncData);
        readAccessStateFromNBT(syncData);
        readControllerAppearanceFromNBT(syncData);
        readGateDataFromNBT(syncData);
        readBannerRenderAttachmentsFromNBT(syncData);
        rebuildRegistryLinks();
        refreshGateWorldLighting();
        markGatePartsForRenderUpdate();
        if (worldObj != null) {
            worldObj.markBlockRangeForRenderUpdate(
                    xCoord,
                    yCoord,
                    zCoord,
                    xCoord,
                    yCoord,
                    zCoord
            );
        }
    }

    public UUID getGateUuid() {
        return ensureGateUuid();
    }

    /**
     * Read-only identity access for fail-closed validation paths. Unlike
     * getGateUuid(), this never creates an identifier or marks the controller.
     */
    public UUID getExistingGateUuid() {
        return gateUuid;
    }

    /**
     * Reads only the durable ownership indexes. It never creates a GateUUID or
     * touches world blocks, and is the common Phase 4B gameplay lock hook.
     */
    public SiegeGateOwnershipData.GateMutationState getPersistentGateMutationState() {
        if (worldObj == null || worldObj.isRemote) {
            return SiegeGateOwnershipData.GateMutationState.NONE;
        }
        SiegeGateOwnershipData ownership = SiegeGateOwnershipData.get(
                worldObj, false
        );
        return ownership == null
                ? SiegeGateOwnershipData.GateMutationState.NONE
                : ownership.getGateMutationState(
                getExistingGateUuid(), worldObj.provider.dimensionId,
                xCoord, yCoord, zCoord
        );
    }

    public boolean isPersistentGateMutationLocked() {
        return getPersistentGateMutationState()
                != SiegeGateOwnershipData.GateMutationState.NONE;
    }

    public GateState getGateState() {
        return gateState;
    }

    public GateControlMode getGateControlMode() {
        return gateControlMode;
    }

    public boolean setGateControlMode(
            EntityPlayerMP player,
            GateControlMode requestedMode
    ) {
        if (requestedMode == null
                || worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()
                || !canManage(player)) {

            return false;
        }

        if (gateControlMode == requestedMode) {
            return true;
        }

        gateControlMode = requestedMode;

        /*
         * Switching back to Automatic while already open should begin a fresh
         * normal hold period instead of immediately closing because the OPEN
         * state's original timer may be minutes old.
         */
        if (requestedMode == GateControlMode.AUTOMATIC
                && gateState == GateState.OPEN) {

            gateStateStartTick = worldObj.getTotalWorldTime();
            gateStateTimingPresent = true;
            SiegeNetwork.syncGateState(this);
        }

        enforceGateControlMode();

        markDirty();
        SiegeNetwork.syncGateAccess(this);

        return true;
    }

    public long getGateStateStartTick() {
        return gateStateStartTick;
    }

    public boolean setGateState(GateState newGateState) {
        if (newGateState == null
                || worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || isPersistentGateMutationLocked()
                || gateState == newGateState) {
            return false;
        }

        if (newGateState == GateState.CLOSED) {
            relocateEntitiesBeforeClosing();
        }
        return beginGateState(newGateState);
    }

    public void applySynchronizedGateState(
            GateState synchronizedState,
            long synchronizedStartTick
    ) {
        if (worldObj == null
                || !worldObj.isRemote
                || synchronizedState == null) {

            return;
        }

        boolean wasClosed =
                gateState == GateState.CLOSED;

        boolean willBeClosed =
                synchronizedState == GateState.CLOSED;

        gateState =
                synchronizedState;

        gateStateStartTick =
                synchronizedStartTick;

        gateStateTimingPresent =
                true;

        if (wasClosed != willBeClosed) {
            refreshGateWorldLighting();
        }
    }

    public boolean isOpen() {
        return gateState == GateState.OPEN;
    }

    public boolean toggleOpenState() {
        if (gateStructureQuarantined
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()) {
            return false;
        }

        if (gateState == GateState.CLOSED
                && gateControlMode == GateControlMode.LOCKED_CLOSED) {
            return false;
        }

        if (gateState == GateState.OPEN
                && gateControlMode == GateControlMode.HELD_OPEN) {
            return false;
        }

        if (!hasCompleteHingeConfiguration()) {
            if (gateState == GateState.CLOSED) {
                return setGateState(GateState.OPEN);
            }
            if (gateState == GateState.OPEN) {
                return setGateState(GateState.CLOSED);
            }
            return false;
        }
        if (gateState == GateState.CLOSED) {
            return beginGateState(GateState.OPENING);
        }
        if (gateState == GateState.OPEN) {
            return beginGateState(GateState.CLOSING);
        }
        return false;
    }

    public boolean tryToggleOpenState(EntityPlayerMP player) {
        if (!canOperate(player)) {
            GateAccess.deny(player, this);
            return false;
        }

        if (gateState == GateState.CLOSED
                && gateControlMode == GateControlMode.LOCKED_CLOSED) {
            player.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                            "This Siege Gate is locked closed."
                    )
            );
            return false;
        }

        if (gateState == GateState.OPEN
                && gateControlMode == GateControlMode.HELD_OPEN) {
            player.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                            "This Siege Gate is being held open."
                    )
            );
            return false;
        }

        return toggleOpenState();
    }

    @Override
    public void updateEntity() {
        if (!MumakilConfig.enableSiegeGates) {
            return;
        }
        if (worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()) {
            return;
        }

        updateRepairJob();
        updateRamReservation();

        if (!hasCompleteHingeConfiguration()) {
            enforceGateControlMode();
            return;
        }

        long elapsed = getElapsedStateTicks(worldObj.getTotalWorldTime());
        if (gateState == GateState.CLOSED) {
            if (gateControlMode == GateControlMode.HELD_OPEN) {
                beginGateState(GateState.OPENING);
            }
        } else if (gateState == GateState.OPENING
                && elapsed >= GateAnimation.OPENING_DURATION_TICKS) {
            beginGateState(GateState.OPEN);
        } else if (gateState == GateState.OPEN) {
            if (gateControlMode == GateControlMode.LOCKED_CLOSED
                    || (gateControlMode == GateControlMode.AUTOMATIC
                    && elapsed >= GateAnimation.AUTO_CLOSE_DELAY_TICKS)) {

                beginGateState(GateState.CLOSING);
            }
        } else if (gateState == GateState.CLOSING
                && elapsed >= GateAnimation.CLOSING_DURATION_TICKS) {
            relocateEntitiesBeforeClosing();
            beginGateState(GateState.CLOSED);
        }
    }

    public float getRenderOpenProgress(float partialTicks) {
        float rawProgress;

        if (gateState == GateState.CLOSED) {
            rawProgress = 0.0F;

        } else if (gateState == GateState.OPEN) {
            rawProgress = 1.0F;

        } else if (gateState == GateState.BREACHED) {
            if (worldObj == null) {
                rawProgress = 1.0F;

            } else {
                double elapsed =
                        worldObj.getTotalWorldTime()
                                - gateStateStartTick
                                + Math.max(0.0F, partialTicks);

                if (elapsed <= BREACH_VISUAL_HOLD_TICKS) {
                    rawProgress = 0.0F;

                } else {
                    rawProgress =
                            (float)((elapsed - BREACH_VISUAL_HOLD_TICKS)
                                    / BREACH_VISUAL_OPEN_TICKS);
                }
            }

        } else if (worldObj == null) {
            rawProgress =
                    gateState == GateState.CLOSING
                            ? 1.0F
                            : 0.0F;

        } else {
            double elapsed =
                    worldObj.getTotalWorldTime()
                            - gateStateStartTick
                            + Math.max(0.0F, partialTicks);

            if (gateState == GateState.OPENING) {
                rawProgress =
                        (float)(elapsed
                                / GateAnimation.OPENING_DURATION_TICKS);

            } else if (gateState == GateState.CLOSING) {
                rawProgress =
                        1.0F
                                - (float)(elapsed
                                / GateAnimation.CLOSING_DURATION_TICKS);

            } else {
                rawProgress = 0.0F;
            }
        }

        return GateAnimation.getSmoothedProgress(
                rawProgress
        );
    }

    public int getCurrentHealth() {
        return currentHealth;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public String getControllerAppearanceBlockName() {
        return controllerAppearanceCustomized
                ? controllerAppearanceBlockName
                : "";
    }

    public int getControllerAppearanceMetadata() {
        return controllerAppearanceCustomized
                ? controllerAppearanceMetadata
                : 0;
    }

    public Block getControllerAppearanceBlock() {
        if (!controllerAppearanceCustomized) {
            return null;
        }

        return resolveControllerAppearanceBlock(
                controllerAppearanceBlockName
        );
    }

    public boolean setControllerAppearance(
            EntityPlayerMP player,
            String requestedBlockName,
            int requestedMetadata
    ) {
        if (worldObj == null
                || worldObj.isRemote
                || isPersistentGateMutationLocked()
                || !canManage(player)
                || requestedMetadata < 0
                || requestedMetadata > 15) {

            return false;
        }

        /*
         * Selecting the Gate Controller itself in the texture picker means:
         *
         * reset the cosmetic skin back to the controller's native
         * iron-and-gear texture.
         *
         * Do this BEFORE the normal appearance validator because the
         * validator deliberately rejects gateController as a cosmetic
         * source to prevent recursive appearance lookup.
         */
        String controllerRegistryName =
                SiegeRegistry.gateController == null
                        ? null
                        : Block.blockRegistry
                        .getNameForObject(
                                SiegeRegistry.gateController
                        );

        if (controllerRegistryName != null
                && controllerRegistryName.equals(
                requestedBlockName
        )) {

            resetControllerAppearanceToDefault();

            markDirty();

            worldObj.markBlockForUpdate(
                    xCoord,
                    yCoord,
                    zCoord
            );

            return true;
        }

        Block block =
                resolveControllerAppearanceBlock(
                        requestedBlockName
                );

        if (block == null) {
            return false;
        }

        String canonicalName =
                Block.blockRegistry
                        .getNameForObject(
                                block
                        );

        if (canonicalName == null
                || canonicalName.length()
                > MAX_SOURCE_BLOCK_NAME_LENGTH) {

            return false;
        }

        if (controllerAppearanceCustomized
                && canonicalName.equals(
                controllerAppearanceBlockName
        )
                && requestedMetadata
                == controllerAppearanceMetadata) {

            return true;
        }

        controllerAppearanceCustomized =
                true;

        controllerAppearanceBlockName =
                canonicalName;

        controllerAppearanceMetadata =
                requestedMetadata;

        markDirty();

        worldObj.markBlockForUpdate(
                xCoord,
                yCoord,
                zCoord
        );

        return true;
    }

    /**
     * A cosmetic controller skin must still behave visually like a solid,
     * opaque full cube, but it does not need to use vanilla render type 0.
     *
     * LOTR has full-cube wood/log/beam blocks which use custom render IDs
     * only to orient or decorate their textures. The controller renderer
     * asks the selected block for its face icons and tint, so requiring
     * render type 0 hid otherwise safe LOTR texture choices from the picker.
     *
     * Keeping the solid + normal-block + opaque checks continues to reject
     * stairs, slabs, fences, panes, chests, plants, carpets, leaves, glass,
     * doors, and other non-full-cube appearances.
     */
    public static boolean isValidControllerAppearanceBlock(
            Block block
    ) {
        if (block == null
                || block == Blocks.air
                || block == SiegeRegistry.gateController
                || block == SiegeRegistry.gatePart) {

            return false;
        }

        try {
            return block.getMaterial() != null
                    && block.getMaterial().isSolid()
                    && block.renderAsNormalBlock()
                    && block.isOpaqueCube();

        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Block resolveControllerAppearanceBlock(
            String registryName
    ) {
        if (registryName == null
                || registryName.isEmpty()
                || registryName.length()
                > MAX_SOURCE_BLOCK_NAME_LENGTH) {

            return null;
        }

        Object registered =
                Block.blockRegistry
                        .getObject(
                                registryName
                        );

        if (!(registered instanceof Block)) {
            return null;
        }

        Block block =
                (Block)registered;

        return isValidControllerAppearanceBlock(
                block
        )
                ? block
                : null;
    }

    private void writeControllerAppearanceToNBT(
            NBTTagCompound nbt
    ) {
        nbt.setBoolean(
                NBT_CONTROLLER_APPEARANCE_CUSTOMIZED,
                controllerAppearanceCustomized
        );

        if (!controllerAppearanceCustomized) {
            nbt.removeTag(
                    NBT_CONTROLLER_APPEARANCE_BLOCK
            );

            nbt.removeTag(
                    NBT_CONTROLLER_APPEARANCE_META
            );

            return;
        }

        nbt.setString(
                NBT_CONTROLLER_APPEARANCE_BLOCK,
                controllerAppearanceBlockName
        );

        nbt.setInteger(
                NBT_CONTROLLER_APPEARANCE_META,
                controllerAppearanceMetadata
        );
    }

    private void resetControllerAppearanceToDefault() {
        controllerAppearanceCustomized =
                false;

        controllerAppearanceBlockName =
                DEFAULT_CONTROLLER_APPEARANCE_BLOCK;

        controllerAppearanceMetadata =
                0;
    }

    private void readControllerAppearanceFromNBT(
            NBTTagCompound nbt
    ) {
        boolean hasCustomizedFlag =
                nbt.hasKey(
                        NBT_CONTROLLER_APPEARANCE_CUSTOMIZED
                );

        if (hasCustomizedFlag
                && !nbt.getBoolean(
                NBT_CONTROLLER_APPEARANCE_CUSTOMIZED
        )) {

            resetControllerAppearanceToDefault();

            return;
        }

        String registryName =
                nbt.hasKey(
                        NBT_CONTROLLER_APPEARANCE_BLOCK,
                        TAG_STRING
                )
                        ? nbt.getString(
                        NBT_CONTROLLER_APPEARANCE_BLOCK
                )
                        : DEFAULT_CONTROLLER_APPEARANCE_BLOCK;

        int metadata =
                nbt.hasKey(
                        NBT_CONTROLLER_APPEARANCE_META,
                        TAG_INT
                )
                        ? nbt.getInteger(
                        NBT_CONTROLLER_APPEARANCE_META
                )
                        : 0;

        /*
         * Migration from the first cosmetic-picker implementation:
         * plain iron/meta 0 was the old implicit default. Treat it as the
         * new gear-textured default unless the new customized flag exists.
         */
        if (!hasCustomizedFlag
                && DEFAULT_CONTROLLER_APPEARANCE_BLOCK
                .equals(
                        registryName
                )
                && metadata == 0) {

            resetControllerAppearanceToDefault();

            return;
        }

        Block resolved =
                resolveControllerAppearanceBlock(
                        registryName
                );

        if (resolved == null
                || metadata < 0
                || metadata > 15) {

            resetControllerAppearanceToDefault();

            return;
        }

        controllerAppearanceCustomized =
                true;

        controllerAppearanceBlockName =
                Block.blockRegistry
                        .getNameForObject(
                                resolved
                        );

        controllerAppearanceMetadata =
                metadata;
    }

    /**
     * Applies damage from an explicit siege-machine impact. Normal Minecraft
     * damage and block-breaking paths never call this method.
     */
    public boolean applySiegeDamage(int amount) {
        if (!MumakilConfig.enableSiegeGates) {
            return false;
        }
        if (amount <= 0
                || worldObj == null
                || worldObj.isRemote
                || !isFinalized()
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()
                || gateState == GateState.BREACHED) {
            return false;
        }

        int previousHealth = currentHealth;
        currentHealth = (int)Math.max(
                0L,
                (long)currentHealth - (long)amount
        );
        boolean newlyBreached = currentHealth == 0
                && gateState != GateState.BREACHED;
        if (repairActive) {
            repairPauseUntilTick = worldObj.getTotalWorldTime()
                    + REPAIR_DAMAGE_PAUSE_TICKS;
        }
        if (newlyBreached) {
            clearRamReservation(null);
            beginGateState(GateState.BREACHED);
        } else {
            markDirty();
        }
        SiegeNetwork.syncGateHealth(this);
        if (repairActive) {
            SiegeNetwork.syncGateRepair(this);
        }
        if (newlyBreached) {
            sendBreachFeedback();
            SiegeGateBreachRallyManager.beginRally(this);
        }
        return currentHealth != previousHealth || newlyBreached;
    }

    public void applySynchronizedGateHealth(
            int synchronizedCurrentHealth,
            int synchronizedMaxHealth
    ) {
        if (worldObj == null
                || !worldObj.isRemote) {

            return;
        }

        int previousHealth =
                currentHealth;

        maxHealth =
                synchronizedMaxHealth > 0
                        ? synchronizedMaxHealth
                        : DEFAULT_MAX_HEALTH;

        currentHealth =
                Math.max(
                        0,
                        Math.min(
                                synchronizedCurrentHealth,
                                maxHealth
                        )
                );

        if (currentHealth < previousHealth) {
            clientImpactStartTick =
                    worldObj.getTotalWorldTime();

            clientImpactBreached =
                    currentHealth <= 0;

            float healthRatio =
                    maxHealth <= 0
                            ? 1.0F
                            : (float)currentHealth
                            / (float)maxHealth;

            clientImpactStrength =
                    clientImpactBreached
                            ? 1.0F
                            : 0.85F
                            + (1.0F - healthRatio) * 0.35F;
        }
    }

    @SideOnly(Side.CLIENT)
    public float getRenderImpactRecoil(
            float partialTicks
    ) {
        if (worldObj == null
                || !worldObj.isRemote
                || clientImpactStartTick == Long.MIN_VALUE) {

            return 0.0F;
        }

        int duration =
                clientImpactBreached
                        ? CLIENT_BREACH_RECOIL_TICKS
                        : CLIENT_HIT_RECOIL_TICKS;

        double elapsed =
                worldObj.getTotalWorldTime()
                        - clientImpactStartTick
                        + Math.max(0.0F, partialTicks);

        if (elapsed < 0.0D
                || elapsed >= duration) {

            return 0.0F;
        }

        float progress =
                (float)(elapsed / duration);

        float envelope =
                (1.0F - progress)
                        * (1.0F - progress);

        float oscillation =
                (float)Math.sin(
                        progress
                                * Math.PI
                                * 2.5D
                );

        float distance =
                clientImpactBreached
                        ? CLIENT_BREACH_RECOIL_DISTANCE
                        : CLIENT_HIT_RECOIL_DISTANCE
                        * clientImpactStrength;

        return oscillation
                * envelope
                * distance;
    }

    @SideOnly(Side.CLIENT)
    public float getRenderImpactGateFlex(
            float partialTicks
    ) {
        if (worldObj == null
                || !worldObj.isRemote
                || clientImpactStartTick == Long.MIN_VALUE
                || clientImpactBreached
                || gateState != GateState.CLOSED) {

            return 0.0F;
        }

        double elapsed =
                worldObj.getTotalWorldTime()
                        - clientImpactStartTick
                        + Math.max(0.0F, partialTicks);

        if (elapsed < 0.0D
                || elapsed >= CLIENT_HIT_RECOIL_TICKS) {

            return 0.0F;
        }

        float progress =
                (float)(elapsed
                        / CLIENT_HIT_RECOIL_TICKS);

        /*
         * Pushes slightly open, reaches its maximum around the middle
         * of the impact, then springs back shut.
         */
        float flex =
                (float)Math.sin(
                        progress * Math.PI
                );

        return flex
                * CLIENT_HIT_GATE_FLEX_MAX
                * clientImpactStrength;
    }

    @SideOnly(Side.CLIENT)
    public float getRenderBreachSag(
            float partialTicks
    ) {
        if (worldObj == null
                || !worldObj.isRemote
                || gateState != GateState.BREACHED) {

            return 0.0F;
        }

        double elapsed =
                worldObj.getTotalWorldTime()
                        - gateStateStartTick
                        + Math.max(0.0F, partialTicks);

        float progress =
                (float)Math.max(
                        0.0D,
                        Math.min(
                                elapsed / BREACH_VISUAL_HOLD_TICKS,
                                1.0D
                        )
                );

        progress =
                GateAnimation.getSmoothedProgress(
                        progress
                );

        return -CLIENT_BREACH_SAG_DISTANCE
                * progress;
    }

    public GateRepairStartResult beginRepair(EntityPlayerMP player) {
        if (player == null
                || worldObj == null
                || worldObj.isRemote
                || player.worldObj != worldObj
                || !isFinalized()
                || isPersistentGateMutationLocked()
                || !canRepair(player)) {
            return GateRepairStartResult.INVALID_GATE;
        }
        if (repairActive) {
            return GateRepairStartResult.ALREADY_ACTIVE;
        }

        int missingHealth = getMissingHealth();
        if (missingHealth <= 0) {
            return GateRepairStartResult.FULL_HEALTH;
        }
        int coinValue = getRepairCostForHealth(missingHealth);
        if (!SiegeCurrency.tryTakeCoinValue(player, coinValue)) {
            return GateRepairStartResult.INSUFFICIENT_FUNDS;
        }

        repairActive = true;
        repairPurchasedHealth = missingHealth;
        repairAppliedHealth = 0;
        repairActiveTicks = 0;
        repairPauseUntilTick = 0L;
        repairPurchasedCoinValue = coinValue;
        repairBuildSoundHitsRemaining = 0;
        repairBuildSoundTicksUntilNext = randomRepairBuildBurstGapTicks();

        /*
         * The successful Repair click gets one of repair1..repair4. Those
         * sounds are the "work begins" cue; ongoing HP gains use the separate
         * hammer/build event below.
         */
        worldObj.playSoundEffect(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D,
                SOUND_GATE_REPAIR,
                0.8F,
                0.95F + worldObj.rand.nextFloat() * 0.1F
        );

        markDirty();
        SiegeNetwork.syncGateRepair(this);
        return GateRepairStartResult.STARTED;
    }

    public boolean isRepairActive() {
        return repairActive;
    }

    public boolean isRepairPaused() {
        return repairActive
                && worldObj != null
                && repairPauseUntilTick > worldObj.getTotalWorldTime();
    }

    public int getRepairPurchasedHealth() {
        return repairPurchasedHealth;
    }

    public int getRepairAppliedHealth() {
        return repairAppliedHealth;
    }

    public int getRepairActiveTicks() {
        return repairActiveTicks;
    }

    public long getRepairPauseUntilTick() {
        return repairPauseUntilTick;
    }

    public int getRepairPurchasedCoinValue() {
        return repairPurchasedCoinValue;
    }

    public int getMissingHealth() {
        return Math.max(0, maxHealth - currentHealth);
    }

    public int getRepairCostToFull() {
        return getRepairCostForHealth(getMissingHealth());
    }

    public static int getRepairCostForHealth(int health) {
        return health <= 0
                ? 0
                : (int)(((long)health + REPAIR_HP_PER_COIN - 1L)
                / REPAIR_HP_PER_COIN);
    }

    public void applySynchronizedGateRepair(
            boolean active,
            int purchasedHealth,
            int appliedHealth,
            int activeTicks,
            long pauseUntilTick,
            int purchasedCoinValue
    ) {
        if (worldObj == null || !worldObj.isRemote) {
            return;
        }
        repairActive = active;
        repairPurchasedHealth = Math.max(0, purchasedHealth);
        repairAppliedHealth = Math.max(
                0,
                Math.min(appliedHealth, repairPurchasedHealth)
        );
        repairActiveTicks = Math.max(
                0,
                activeTicks
        );
        repairPauseUntilTick = Math.max(0L, pauseUntilTick);
        repairPurchasedCoinValue = Math.max(0, purchasedCoinValue);
        if (!repairActive || repairPurchasedHealth <= 0) {
            clearRepairState();
        }
    }

    public String getGateName() {
        return gateName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public LOTRFaction getGateFaction() {
        return gateFaction;
    }

    public int getRequiredAlignment() {
        return requiredAlignment;
    }

    public boolean isFactionAccessEnabled() {
        return factionAccessEnabled;
    }

    public Set<UUID> getEditorUuids() {
        return Collections.unmodifiableSet(
                new HashSet<UUID>(editorUuids)
        );
    }

    public Set<UUID> getOperatorUuids() {
        return Collections.unmodifiableSet(
                new HashSet<UUID>(operatorUuids)
        );
    }

    public Set<UUID> getAccessWhitelistUuids() {
        return Collections.unmodifiableSet(
                new HashSet<UUID>(accessWhitelistUuids)
        );
    }

    public UUID getReservedRamUuid() {
        return reservedRamUuid;
    }

    public boolean tryReserveForRam(UUID ramUuid) {
        if (ramUuid == null
                || worldObj == null
                || worldObj.isRemote
                || !isFinalized()
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()
                || gateState == GateState.BREACHED) {
            return false;
        }
        long worldTick = worldObj.getTotalWorldTime();
        if (reservedRamUuid != null
                && !reservedRamUuid.equals(ramUuid)
                && worldTick - reservedRamLastSeenTick
                <= RAM_RESERVATION_TIMEOUT_TICKS) {
            return false;
        }
        boolean changed = !ramUuid.equals(reservedRamUuid);
        reservedRamUuid = ramUuid;
        reservedRamLastSeenTick = worldTick;
        if (changed) {
            markDirty();
            SiegeNetwork.syncGateAccess(this);
        }
        return true;
    }

    public boolean refreshRamReservation(UUID ramUuid) {
        if (ramUuid == null
                || !ramUuid.equals(reservedRamUuid)
                || worldObj == null
                || worldObj.isRemote
                || isPersistentGateMutationLocked()) {
            return false;
        }
        reservedRamLastSeenTick = worldObj.getTotalWorldTime();
        return true;
    }

    public boolean clearRamReservation(UUID ramUuid) {
        if (worldObj == null
                || worldObj.isRemote
                || reservedRamUuid == null
                || (ramUuid != null
                && !ramUuid.equals(reservedRamUuid))) {
            return false;
        }
        reservedRamUuid = null;
        reservedRamLastSeenTick = 0L;
        markDirty();
        SiegeNetwork.syncGateAccess(this);
        return true;
    }

    public boolean canOperate(EntityPlayerMP player) {
        if (player == null || persistentOwnershipSuspended) {
            return false;
        }
        if (GateAccess.isAdministrativePlayer(player)) {
            return true;
        }

        UUID playerUuid = player.getUniqueID();

        /*
         * Owner and Editor are trusted gate roles. Both may operate/repair the
         * gate regardless of the selected faction alignment requirement.
         */
        if ((ownerUuid != null && ownerUuid.equals(playerUuid))
                || editorUuids.contains(playerUuid)) {
            return true;
        }

        /*
         * Explicit Access is the player-specific override for operation.
         * Legacy Operator entries are treated as Access so old saves keep the
         * same intent without granting broader configuration authority.
         */
        if (accessWhitelistUuids.contains(playerUuid)
                || operatorUuids.contains(playerUuid)) {
            return true;
        }

        /*
         * A neutral gate has no faction restriction, so it is public.
         */
        if (gateFaction == null) {
            return true;
        }

        /*
         * Faction identity and faction-wide operation are separate settings.
         *
         * Existing gates default this switch to ON for compatibility. When it
         * is OFF, the faction still identifies the gate for rendering, ram
         * hostility, announcements, etc., but alignment alone grants no player
         * operation rights.
         */
        if (!factionAccessEnabled) {
            return false;
        }

        return LOTRLevelData.getData(player).getAlignment(gateFaction)
                >= requiredAlignment;
    }

    public boolean canManage(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        if (GateAccess.isAdministrativePlayer(player)) {
            return true;
        }
        UUID playerUuid = player.getUniqueID();
        return ownerUuid != null
                && (ownerUuid.equals(playerUuid)
                || editorUuids.contains(playerUuid));
    }

    public boolean canManagePlayerAccess(EntityPlayerMP player) {
        return canManage(player);
    }

    public boolean canManageEditors(EntityPlayerMP player) {
        if (player == null) {
            return false;
        }
        return GateAccess.isAdministrativePlayer(player)
                || (ownerUuid != null
                && ownerUuid.equals(player.getUniqueID()));
    }

    public boolean canDismantle(EntityPlayerMP player) {
        if (player == null || gateStructureQuarantined) {
            return false;
        }
        return GateAccess.isAdministrativePlayer(player)
                || (ownerUuid != null
                && ownerUuid.equals(player.getUniqueID()));
    }

    public boolean canRepair(EntityPlayerMP player) {
        return canOperate(player);
    }

    public boolean setOwnerOnFinalization(UUID creatorUuid) {
        if (creatorUuid == null
                || worldObj == null
                || worldObj.isRemote
                || !isFinalized()
                || ownerUuid != null) {
            return false;
        }
        ownerUuid = creatorUuid;

        /*
         * New gates inherit the creator's active LOTR pledge as their default
         * faction. Existing/explicit faction choices are never overwritten.
         */
        if (gateFaction == null) {
            LOTRFaction pledgedFaction =
                    LOTRLevelData.getData(creatorUuid).getPledgeFaction();
            if (pledgedFaction != null
                    && LOTRFaction.getPlayableAlignmentFactions().contains(
                    pledgedFaction
            )) {
                gateFaction = pledgedFaction;
            }
        }

        onAccessStateChanged();
        return true;
    }

    public boolean claimOwnerlessGate(EntityPlayerMP player) {
        if (ownerUuid != null
                || isPersistentGateMutationLocked()
                || !GateAccess.isAdministrativePlayer(player)) {
            return false;
        }
        ownerUuid = player.getUniqueID();
        onAccessStateChanged();
        return true;
    }

    public boolean setGateName(
            EntityPlayerMP player,
            String requestedName
    ) {
        if (isPersistentGateMutationLocked() || !canManage(player)) {
            return false;
        }
        String sanitizedName = sanitizeGateName(requestedName);
        if (gateName.equals(sanitizedName)) {
            return true;
        }
        gateName = sanitizedName;
        onAccessStateChanged();
        return true;
    }

    public boolean setGateFaction(
            EntityPlayerMP player,
            String factionName
    ) {
        if (isPersistentGateMutationLocked() || !canManage(player)) {
            return false;
        }
        LOTRFaction requestedFaction = factionName == null
                || factionName.isEmpty()
                ? null
                : LOTRFaction.forName(factionName);
        if (factionName != null
                && !factionName.isEmpty()
                && requestedFaction == null) {
            return false;
        }
        if (requestedFaction != null
                && !LOTRFaction.getPlayableAlignmentFactions().contains(
                requestedFaction
        )) {
            return false;
        }
        if (requestedFaction != null
                && LOTRLevelData
                .getData(player)
                .getAlignment(
                        requestedFaction
                )
                < 100.0F) {

            return false;
        }
        gateFaction = requestedFaction;
        onAccessStateChanged();
        return true;
    }

    public boolean setRequiredAlignment(
            EntityPlayerMP player,
            int alignment
    ) {
        if (isPersistentGateMutationLocked()
                || !canManage(player)
                || gateFaction == null
                || alignment < 1
                || alignment > MAX_REQUIRED_ALIGNMENT) {

            return false;
        }

        requiredAlignment =
                alignment;

        onAccessStateChanged();

        return true;
    }

    public boolean setFactionAccessEnabled(
            EntityPlayerMP player,
            boolean enabled
    ) {
        if (isPersistentGateMutationLocked()
                || !canManage(player)
                || gateFaction == null) {

            return false;
        }

        if (factionAccessEnabled == enabled) {
            return true;
        }

        factionAccessEnabled =
                enabled;

        onAccessStateChanged();

        return true;
    }

    public boolean toggleEditor(
            EntityPlayerMP player,
            UUID targetUuid
    ) {
        if (!canManageEditors(player)) {
            return false;
        }
        return toggleAccessEntry(player, targetUuid, editorUuids);
    }

    public boolean toggleOperator(
            EntityPlayerMP player,
            UUID targetUuid
    ) {
        return toggleAccessEntry(player, targetUuid, operatorUuids);
    }

    public boolean toggleWhitelist(
            EntityPlayerMP player,
            UUID targetUuid
    ) {
        return toggleAccessEntry(
                player,
                targetUuid,
                accessWhitelistUuids
        );
    }

    public boolean setPlayerAccessLevel(
            EntityPlayerMP player,
            UUID targetUuid,
            int accessLevel
    ) {
        if (isPersistentGateMutationLocked()
                || !canManagePlayerAccess(player)
                || targetUuid == null
                || targetUuid.equals(
                ownerUuid
        )
                || (accessLevel
                != PLAYER_ACCESS_LEVEL_ACCESS
                && accessLevel
                != PLAYER_ACCESS_LEVEL_EDITOR)) {

            return false;
        }

        /*
         * Managers may manage ordinary Access entries, but only the Owner or a
         * server administrator may create, remove, or demote Managers.
         */
        if ((accessLevel == PLAYER_ACCESS_LEVEL_EDITOR
                || editorUuids.contains(targetUuid))
                && !canManageEditors(player)) {
            return false;
        }

        boolean alreadyPresent =
                editorUuids.contains(
                        targetUuid
                )
                        || operatorUuids.contains(
                        targetUuid
                )
                        || accessWhitelistUuids.contains(
                        targetUuid
                );

        if (!alreadyPresent
                && getDistinctPlayerAccessEntryCount()
                >= MAX_ACCESS_ENTRIES) {

            return false;
        }

        boolean changed =
                false;

        if (accessLevel
                == PLAYER_ACCESS_LEVEL_EDITOR) {

            changed |=
                    operatorUuids.remove(
                            targetUuid
                    );

            changed |=
                    accessWhitelistUuids.remove(
                            targetUuid
                    );

            changed |=
                    editorUuids.add(
                            targetUuid
                    );

        } else {
            /*
             * ACCESS replaces both the old Operator and Whitelist concepts.
             *
             * Old saved Operator entries remain readable, but any newly-edited
             * entry is normalized into the whitelist set.
             */
            changed |=
                    editorUuids.remove(
                            targetUuid
                    );

            changed |=
                    operatorUuids.remove(
                            targetUuid
                    );

            changed |=
                    accessWhitelistUuids.add(
                            targetUuid
                    );
        }

        if (changed) {
            onAccessStateChanged();
        }

        /*
         * Idempotent SET operations count as successful.
         */
        return true;
    }

    public boolean removePlayerAccessEntry(
            EntityPlayerMP player,
            UUID targetUuid
    ) {
        if (isPersistentGateMutationLocked()
                || !canManagePlayerAccess(player)
                || targetUuid == null
                || targetUuid.equals(
                ownerUuid
        )) {

            return false;
        }

        if (editorUuids.contains(targetUuid)
                && !canManageEditors(player)) {
            return false;
        }

        boolean changed =
                false;

        changed |=
                editorUuids.remove(
                        targetUuid
                );

        changed |=
                operatorUuids.remove(
                        targetUuid
                );

        changed |=
                accessWhitelistUuids.remove(
                        targetUuid
                );

        if (changed) {
            onAccessStateChanged();
        }

        return changed;
    }

    /**
     * Administrative/debug health setter used by /siegegate health.
     *
     * This is intentionally server-authoritative and preserves the normal
     * breached recovery rule: zero health breaches the gate, while a breached
     * gate only returns to OPEN after the configured recovery threshold has
     * been reached.
     */
    public boolean setHealthForCommand(
            int requestedHealth
    ) {
        if (worldObj == null
                || worldObj.isRemote
                || !isFinalized()
                || isPersistentGateMutationLocked()) {

            return false;
        }

        int clampedHealth =
                Math.max(
                        0,
                        Math.min(
                                requestedHealth,
                                maxHealth
                        )
                );

        boolean healthChanged =
                currentHealth != clampedHealth;

        currentHealth =
                clampedHealth;

        if (repairActive) {
            clearRepairState();
        }

        boolean stateChanged =
                false;

        if (currentHealth <= 0
                && gateState != GateState.BREACHED) {

            clearRamReservation(
                    null
            );

            stateChanged =
                    beginGateState(
                            GateState.BREACHED
                    );

        } else if (gateState == GateState.BREACHED
                && currentHealth >= getBreachedRecoveryHealth()) {

            stateChanged =
                    beginGateState(
                            GateState.OPEN
                    );
        }

        if (!stateChanged) {
            markDirty();
        }

        SiegeNetwork.syncGateHealth(
                this
        );

        SiegeNetwork.syncGateRepair(
                this
        );

        return healthChanged
                || stateChanged;
    }

    public boolean setMaxHealthOverride(
            EntityPlayerMP player,
            int requestedMaxHealth
    ) {
        if (isPersistentGateMutationLocked()
                || player == null
                || !player.capabilities.isCreativeMode
                || requestedMaxHealth < 1
                || requestedMaxHealth > MAX_HEALTH_OVERRIDE) {

            return false;
        }

        maxHealth =
                requestedMaxHealth;

        currentHealth =
                Math.max(
                        0,
                        Math.min(
                                currentHealth,
                                maxHealth
                        )
                );

        if (currentHealth <= 0
                && gateState != GateState.BREACHED) {

            beginGateState(
                    GateState.BREACHED
            );
        }

        markDirty();

        SiegeNetwork.syncGateHealth(
                this
        );

        SiegeNetwork.syncGateRepair(
                this
        );

        return true;
    }

    public void applySynchronizedAccessState(
            String synchronizedName,
            UUID synchronizedOwner,
            String synchronizedFaction,
            int synchronizedRequiredAlignment,
            boolean synchronizedFactionAccessEnabled,
            int synchronizedGateControlMode,
            Collection<UUID> synchronizedEditors,
            Collection<UUID> synchronizedOperators,
            Collection<UUID> synchronizedWhitelist
    ) {
        if (worldObj == null || !worldObj.isRemote) {
            return;
        }
        gateName = sanitizeGateName(synchronizedName);
        ownerUuid = synchronizedOwner;
        gateFaction = synchronizedFaction == null
                || synchronizedFaction.isEmpty()
                ? null
                : LOTRFaction.forName(synchronizedFaction);
        requiredAlignment = Math.max(
                1,
                Math.min(
                        synchronizedRequiredAlignment,
                        MAX_REQUIRED_ALIGNMENT
                )
        );
        factionAccessEnabled =
                synchronizedFactionAccessEnabled;

        GateControlMode synchronizedMode =
                GateControlMode.fromNetworkId(
                        synchronizedGateControlMode
                );
        gateControlMode = synchronizedMode == null
                ? GateControlMode.AUTOMATIC
                : synchronizedMode;

        replaceUuidSet(editorUuids, synchronizedEditors);
        replaceUuidSet(operatorUuids, synchronizedOperators);
        replaceUuidSet(accessWhitelistUuids, synchronizedWhitelist);
    }

    public void applySynchronizedRamReservation(UUID ramUuid) {
        if (worldObj != null && worldObj.isRemote) {
            reservedRamUuid = ramUuid;
        }
    }

    public boolean isFinalized() {
        return !gateStructureQuarantined && !gateParts.isEmpty();
    }

    public boolean hasCompleteHingeConfiguration() {
        return hingeConfigurationValid;
    }

    public GateHinge getLeftHinge() {
        return leftHinge;
    }

    public GateHinge getRightHinge() {
        return rightHinge;
    }

    public GateOrientation getGateOrientation() {
        return gateOrientation;
    }

    public GateOpeningDirection getOpeningDirection() {
        return openingDirection;
    }

    public boolean isGateBorderTextureEnabled() {
        return gateBorderTextureEnabled;
    }

    public int getRenderDataRevision() {
        return renderDataRevision;
    }

    public int getStructureRevision() {
        return structureRevision;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (cachedRenderBoundingBox == null) {
            cachedRenderBoundingBox = calculateRenderBoundingBox();
        }
        return cachedRenderBoundingBox;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        return 256.0D * 256.0D;
    }

    public List<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>
            getBannerRenderAttachments() {
        return Collections.unmodifiableList(
                new ArrayList<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>(
                        bannerRenderAttachments
                )
        );
    }

    /**
     * Server-side bridge from the durable banner sidecar to the controller's
     * inert client render state. Protection/owner/whitelist NBT never enters
     * this list.
     */
    public void setBannerRenderAttachments(
            Collection<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot> snapshots
    ) {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }

        List<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot> normalized =
                new ArrayList<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>();
        if (snapshots != null) {
            if (snapshots.size() > GateStructureValidator.MAX_GATE_PARTS * 4) {
                return;
            }
            for (SiegeGateBannerAttachmentData.RenderAttachmentSnapshot snapshot
                    : snapshots) {
                if (snapshot == null) {
                    return;
                }
                normalized.add(snapshot);
            }
        }

        if (bannerRenderAttachments.equals(normalized)) {
            return;
        }

        bannerRenderAttachments.clear();
        bannerRenderAttachments.addAll(normalized);
        markRenderDataChanged();
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public List<GatePartData> getGateParts() {
        return Collections.unmodifiableList(
                new ArrayList<GatePartData>(gateParts)
        );
    }

    public List<GatePartData> getGatePartsForLeaf(GateLeaf leaf) {
        if (leaf == null) {
            return Collections.emptyList();
        }

        List<GatePartData> matchingParts =
                new ArrayList<GatePartData>();
        for (GatePartData part : gateParts) {
            if (part.getLeaf().contributesTo(leaf)) {
                matchingParts.add(part);
            }
        }
        return Collections.unmodifiableList(matchingParts);
    }

    public List<GatePartData> getRenderableGatePartsForLeaf(
            GateLeaf leaf
    ) {
        if (leaf == null || worldObj == null) {
            return Collections.emptyList();
        }
        List<GatePartData> matchingParts =
                new ArrayList<GatePartData>();
        for (GatePartData part : gateParts) {
            if (part.getLeaf().contributesTo(leaf)
                    && isGatePartLoadedAndPresent(part)) {
                matchingParts.add(part);
            }
        }
        return Collections.unmodifiableList(matchingParts);
    }

    public boolean isGatePartLoadedAndPresent(GatePartData part) {
        if (part == null
                || worldObj == null
                || !part.hasValidAbsolutePosition(xCoord, yCoord, zCoord)) {
            return false;
        }
        int partX = part.getAbsoluteX(xCoord);
        int partY = part.getAbsoluteY(yCoord);
        int partZ = part.getAbsoluteZ(zCoord);
        return worldObj.blockExists(partX, partY, partZ)
                && worldObj.getBlock(partX, partY, partZ)
                == SiegeRegistry.gatePart;
    }

    public void onPartChunkAvailabilityChanged() {
        if (worldObj != null && worldObj.isRemote) {
            markRenderDataChanged();
            Main.proxy.releaseGateRenderCache(this);
        }
    }

    public boolean containsGatePart(
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        return getGatePartData(relativeX, relativeY, relativeZ) != null;
    }

    public GatePartData getGatePartData(
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        return gatePartsByPosition.get(new RelativePosition(
                relativeX,
                relativeY,
                relativeZ
        ));
    }

    public boolean addGatePart(
            int relativeX,
            int relativeY,
            int relativeZ,
            GateLeaf leaf
    ) {
        if (!canModifyStructure()
                || structureRevision == Integer.MAX_VALUE
                || leaf == null
                || containsGatePart(relativeX, relativeY, relativeZ)) {
            return false;
        }

        GatePartData part = new GatePartData(
                relativeX,
                relativeY,
                relativeZ,
                leaf
        );
        if (!part.hasValidAbsolutePosition(xCoord, yCoord, zCoord)) {
            return false;
        }

        gateParts.add(part);
        onGateStructureChanged();
        return true;
    }

    public boolean removeGatePart(
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        if (!canModifyStructure()
                || structureRevision == Integer.MAX_VALUE) {
            return false;
        }

        int partIndex = findGatePartIndex(
                relativeX,
                relativeY,
                relativeZ
        );
        if (partIndex < 0) {
            return false;
        }

        gateParts.remove(partIndex);
        onGateStructureChanged();
        return true;
    }

    /**
     * Server-authoritative Creative-mode removal of one finalized GatePart.
     *
     * The controller structure/persistent ownership is changed before the
     * proxy block disappears so a Creative break cannot leave a live world
     * block that is no longer represented by the durable gate record. The
     * prospective sparse structure must still satisfy the ordinary structural
     * invariants (both leaves present/connected and controller adjacency).
     */
    public boolean removeGatePartForCreativeBreak(
            EntityPlayer player,
            int partX,
            int partY,
            int partZ
    ) {
        if (player == null
                || !player.capabilities.isCreativeMode
                || worldObj == null
                || worldObj.isRemote
                || player.worldObj != worldObj
                || !isFinalized()
                || gateState != GateState.CLOSED
                || gateStructureQuarantined
                || persistentOwnershipSuspended
                || isPersistentGateMutationLocked()
                || repairActive
                || reservedRamUuid != null
                || structureRevision == Integer.MAX_VALUE
                || worldObj.getBlock(partX, partY, partZ)
                != SiegeRegistry.gatePart) {
            return false;
        }

        int relativeX = partX - xCoord;
        int relativeY = partY - yCoord;
        int relativeZ = partZ - zCoord;
        GatePartData part = getGatePartData(
                relativeX,
                relativeY,
                relativeZ
        );
        if (part == null
                || SiegeGateBannerAttachmentData.hasAttachmentAtSupport(
                        worldObj,
                        getExistingGateUuid(),
                        relativeX,
                        relativeY,
                        relativeZ
                )) {
            return false;
        }

        List<GatePartData> originalParts =
                new ArrayList<GatePartData>(gateParts);
        List<GatePartData> remainingParts =
                new ArrayList<GatePartData>(gateParts);

        int removeIndex = -1;
        for (int i = 0; i < remainingParts.size(); ++i) {
            GatePartData candidate = remainingParts.get(i);
            if (candidate.getRelativeX() == relativeX
                    && candidate.getRelativeY() == relativeY
                    && candidate.getRelativeZ() == relativeZ) {
                removeIndex = i;
                break;
            }
        }
        if (removeIndex < 0) {
            return false;
        }
        remainingParts.remove(removeIndex);

        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateStructure(
                        remainingParts,
                        xCoord,
                        yCoord,
                        zCoord
                );
        if (!validation.isValid()
                || !setGateParts(remainingParts)) {
            return false;
        }

        /*
         * setGateParts() has already advanced the structure revision, rebuilt
         * the runtime index, and synchronized durable ownership. Removing the
         * proxy now causes BlockSiegeGatePart.breakBlock() to clean any stale
         * runtime link without being able to resurrect this part.
         */
        if (worldObj.getBlock(partX, partY, partZ)
                != SiegeRegistry.gatePart
                || worldObj.setBlockToAir(partX, partY, partZ)) {
            return true;
        }

        /*
         * Extremely defensive rollback: if the physical block could not be
         * removed, restore the controller's original membership instead of
         * leaving structure data and the world out of sync.
         */
        setGateParts(originalParts);
        worldObj.markBlockForUpdate(partX, partY, partZ);
        return false;
    }

    public boolean clearGateParts() {
        if (!canModifyStructure()
                || structureRevision == Integer.MAX_VALUE
                || gateParts.isEmpty()) {
            return false;
        }

        gateParts.clear();
        onGateStructureChanged();
        return true;
    }

    public boolean setGateParts(Collection<GatePartData> parts) {
        if (!canModifyStructure() || parts == null) {
            return false;
        }

        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateStructure(
                        parts,
                        xCoord,
                        yCoord,
                        zCoord
                );
        if (!validation.isValid()) {
            return false;
        }
        List<GatePartData> acceptedParts =
                new ArrayList<GatePartData>(parts);
        if (gateParts.equals(acceptedParts)) {
            return false;
        }
        if (!canPersistProspectiveStructure(
                acceptedParts,
                nextStructureRevision()
        )) {
            return false;
        }

        gateParts.clear();
        gateParts.addAll(acceptedParts);
        clearGateStructureQuarantine();
        onGateStructureChanged();
        return true;
    }

    public boolean setFinalizedGateData(
            Collection<GatePartData> parts,
            GateHinge leftHinge,
            GateHinge rightHinge,
            GateOrientation orientation,
            GateOpeningDirection openingDirection,
            boolean gateBorderTextureEnabled
    ) {
        if (!canModifyStructure()
                || parts == null
                || openingDirection == null) {
            return false;
        }

        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateFinalized(
                        parts,
                        leftHinge,
                        rightHinge,
                        orientation,
                        openingDirection,
                        xCoord,
                        yCoord,
                        zCoord
                );
        if (!validation.isValid()) {
            return false;
        }
        if (!canPersistProspectiveStructure(
                parts,
                nextStructureRevision()
        )) {
            return false;
        }

        gateParts.clear();
        gateParts.addAll(parts);
        this.leftHinge = validation.getLeftHinge();
        this.rightHinge = validation.getRightHinge();
        this.gateOrientation = validation.getOrientation();
        this.openingDirection = openingDirection;
        this.gateBorderTextureEnabled = gateBorderTextureEnabled;
        hingeConfigurationValid = true;
        clearGateStructureQuarantine();
        onGateStructureChanged();
        return true;
    }

    /**
     * Server-only exact controller application for a durable EDIT_EXISTING
     * transaction. Unlike normal structure setters, this method never derives
     * a revision, never synchronizes durable ownership, and never writes world
     * blocks. The WSD caller owns transaction proof and conflict handling.
     */
    public EditCommitTargetApplyResult applyEditCommitTarget(
            UUID expectedGateUuid,
            int baseRevision,
            int targetRevision,
            Collection<GatePartData> originalParts,
            GateHinge originalLeftHinge,
            GateHinge originalRightHinge,
            GateOrientation fixedOrientation,
            GateOpeningDirection originalOpeningDirection,
            boolean originalBorderTextureEnabled,
            Collection<GatePartData> targetParts,
            GateHinge targetLeftHinge,
            GateHinge targetRightHinge,
            GateOpeningDirection targetOpeningDirection,
            boolean targetBorderTextureEnabled
    ) {
        if (worldObj == null
                || worldObj.isRemote
                || expectedGateUuid == null
                || baseRevision <= 0
                || baseRevision == Integer.MAX_VALUE
                || targetRevision != baseRevision + 1
                || originalParts == null
                || targetParts == null
                || fixedOrientation == null
                || originalOpeningDirection == null
                || targetOpeningDirection == null) {
            return EditCommitTargetApplyResult.UNEXPECTED;
        }

        GateStructureValidator.ValidationResult originalValidation =
                GateStructureValidator.validateFinalized(
                        originalParts,
                        originalLeftHinge,
                        originalRightHinge,
                        fixedOrientation,
                        originalOpeningDirection,
                        xCoord,
                        yCoord,
                        zCoord
                );
        GateStructureValidator.ValidationResult targetValidation =
                GateStructureValidator.validateFinalized(
                        targetParts,
                        targetLeftHinge,
                        targetRightHinge,
                        fixedOrientation,
                        targetOpeningDirection,
                        xCoord,
                        yCoord,
                        zCoord
                );
        if (!originalValidation.isValid() || !targetValidation.isValid()) {
            return EditCommitTargetApplyResult.UNEXPECTED;
        }

        if (matchesExactEditCommitStructure(
                expectedGateUuid,
                targetRevision,
                targetParts,
                targetValidation.getLeftHinge(),
                targetValidation.getRightHinge(),
                targetValidation.getOrientation(),
                targetOpeningDirection,
                targetBorderTextureEnabled
        )) {
            return EditCommitTargetApplyResult.ALREADY_AFTER;
        }
        if (!matchesExactEditCommitStructure(
                expectedGateUuid,
                baseRevision,
                originalParts,
                originalValidation.getLeftHinge(),
                originalValidation.getRightHinge(),
                originalValidation.getOrientation(),
                originalOpeningDirection,
                originalBorderTextureEnabled
        )) {
            return EditCommitTargetApplyResult.UNEXPECTED;
        }

        gateParts.clear();
        gateParts.addAll(new ArrayList<GatePartData>(targetParts));
        leftHinge = copyHinge(targetValidation.getLeftHinge());
        rightHinge = copyHinge(targetValidation.getRightHinge());
        gateOrientation = targetValidation.getOrientation();
        openingDirection = targetOpeningDirection;
        gateBorderTextureEnabled = targetBorderTextureEnabled;
        structureRevision = targetRevision;
        gateStructureTagPresent = true;
        hingeConfigurationValid = true;
        rebuildGatePartIndex();
        markRenderDataChanged();
        markDirty();
        GateRegistry.registerController(this);
        refreshGateWorldLighting();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);

        return matchesExactEditCommitStructure(
                expectedGateUuid,
                targetRevision,
                targetParts,
                targetValidation.getLeftHinge(),
                targetValidation.getRightHinge(),
                targetValidation.getOrientation(),
                targetOpeningDirection,
                targetBorderTextureEnabled
        ) ? EditCommitTargetApplyResult.BEFORE_APPLIED
                : EditCommitTargetApplyResult.UNEXPECTED;
    }

    public enum EditCommitTargetApplyResult {
        BEFORE_APPLIED,
        ALREADY_AFTER,
        UNEXPECTED
    }

    public boolean canPersistFinalizedGateData(
            Collection<GatePartData> parts
    ) {
        return canPersistProspectiveStructure(
                parts,
                nextStructureRevision()
        );
    }

    public boolean hasGateStructureData() {
        return gateStructureTagPresent;
    }

    public boolean isGateStructureQuarantined() {
        return gateStructureQuarantined;
    }

    public void rebuildRegistryLinks() {
        if (worldObj != null
                && !gateStructureQuarantined
                && (worldObj.isRemote
                || !persistentOwnershipSuspended)) {
            GateRegistry.registerController(this);
        }
    }

    public boolean canPrepareControllerRemovalTransaction() {
        if (worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || gateParts.isEmpty()) {
            return false;
        }
        SiegeGateOwnershipData.GateMutationState mutationState =
                getPersistentGateMutationState();
        if (mutationState != SiegeGateOwnershipData.GateMutationState.NONE
                && mutationState != SiegeGateOwnershipData.GateMutationState.LEGACY_REMOVAL) {
            return false;
        }
        ensureStructureRevision();
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, true);
        return ownership != null
                && ownership.registerOrUpdateController(this)
                && ownership.canBeginRemoval(this);
    }

    public boolean prepareControllerRemovalTransaction() {
        if (!canPrepareControllerRemovalTransaction()) {
            return false;
        }
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, true);
        if (ownership == null || !ownership.prepareRemoval(
                this,
                SiegeGateOwnershipData.TransactionType.CONTROLLER_REMOVAL
        )) {
            return false;
        }
        if (!SiegeGateBannerAttachmentData.beginRestoration(
                worldObj,
                getGateUuid()
        )) {
            ownership.abortPreparedRemoval(
                    getGateUuid(),
                    structureRevision
            );
            return false;
        }
        return true;
    }

    public void abortPreparedControllerRemovalTransaction() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, false);
        if (ownership != null) {
            ownership.abortPreparedRemoval(
                    getGateUuid(),
                    structureRevision
            );
        }
        SiegeGateBannerAttachmentData.abortRestoration(
                worldObj,
                getExistingGateUuid()
        );
    }

    public boolean dismantleGateParts() {
        if (worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || gateParts.isEmpty()) {
            return false;
        }
        SiegeGateOwnershipData.GateMutationState mutationState =
                getPersistentGateMutationState();
        if (mutationState != SiegeGateOwnershipData.GateMutationState.NONE
                && mutationState != SiegeGateOwnershipData.GateMutationState.LEGACY_REMOVAL) {
            return false;
        }
        ensureStructureRevision();
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, true);
        if (ownership == null
                || !ownership.prepareRemoval(
                this,
                SiegeGateOwnershipData.TransactionType
                        .DISMANTLE_RESTORE
        )) {
            if (ownership != null) {
                ownership.quarantineMissingController(
                        worldObj.provider.dimensionId,
                        getGateUuid(),
                        xCoord,
                        yCoord,
                        zCoord
                );
            }
            return false;
        }
        if (!SiegeGateBannerAttachmentData.beginRestoration(
                worldObj,
                getGateUuid()
        )) {
            ownership.abortPreparedRemoval(
                    getGateUuid(),
                    structureRevision
            );
            return false;
        }
        ownership.activateRemoval(
                worldObj,
                getGateUuid(),
                structureRevision
        );
        GateRegistry.unregisterController(
                worldObj,
                xCoord,
                yCoord,
                zCoord
        );
        return true;
    }

    private boolean canModifyStructure() {
        return worldObj != null && !worldObj.isRemote;
    }

    private boolean matchesExactEditCommitStructure(
            UUID expectedGateUuid,
            int expectedRevision,
            Collection<GatePartData> expectedParts,
            GateHinge expectedLeftHinge,
            GateHinge expectedRightHinge,
            GateOrientation expectedOrientation,
            GateOpeningDirection expectedOpeningDirection,
            boolean expectedBorderTextureEnabled
    ) {
        if (expectedGateUuid == null
                || expectedRevision <= 0
                || expectedParts == null
                || expectedLeftHinge == null
                || expectedRightHinge == null
                || expectedOrientation == null
                || expectedOpeningDirection == null
                || !expectedGateUuid.equals(getExistingGateUuid())
                || structureRevision != expectedRevision
                || gateStructureQuarantined
                || !gateStructureTagPresent
                || !hingeConfigurationValid
                || gateState != GateState.CLOSED
                || repairActive
                || reservedRamUuid != null
                || !leftHinge.equals(expectedLeftHinge)
                || !rightHinge.equals(expectedRightHinge)
                || gateOrientation != expectedOrientation
                || openingDirection != expectedOpeningDirection
                || gateBorderTextureEnabled != expectedBorderTextureEnabled
                || gateParts.size() != expectedParts.size()
                || !gateParts.containsAll(expectedParts)
                || !expectedParts.containsAll(gateParts)) {
            return false;
        }
        GateStructureValidator.ValidationResult validation =
                GateStructureValidator.validateFinalized(
                        gateParts,
                        leftHinge,
                        rightHinge,
                        gateOrientation,
                        openingDirection,
                        xCoord,
                        yCoord,
                        zCoord
                );
        return validation.isValid();
    }

    private static GateHinge copyHinge(GateHinge hinge) {
        return hinge == null ? null : new GateHinge(
                hinge.getRelativeX(), hinge.getRelativeZ(), hinge.getSide()
        );
    }

    private int nextStructureRevision() {
        if (structureRevision == Integer.MAX_VALUE) {
            return -1;
        }
        return Math.max(1, structureRevision + 1);
    }

    private void ensureStructureRevision() {
        if (!gateParts.isEmpty() && structureRevision <= 0) {
            structureRevision = 1;
            markDirty();
        }
    }

    private boolean canPersistProspectiveStructure(
            Collection<GatePartData> parts,
            int prospectiveRevision
    ) {
        if (worldObj == null
                || worldObj.isRemote
                || parts == null
                || parts.isEmpty()
                || prospectiveRevision <= 0) {
            return false;
        }
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, true);
        return ownership != null && ownership.canAcceptController(
                worldObj.provider.dimensionId,
                getGateUuid(),
                xCoord,
                yCoord,
                zCoord,
                prospectiveRevision,
                parts
        );
    }

    private boolean synchronizePersistentOwnership() {
        if (worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || gateParts.isEmpty()) {
            return false;
        }
        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(worldObj, true);
        boolean registered = ownership != null
                && ownership.registerOrUpdateController(this);
        persistentOwnershipSuspended = !registered;
        return registered;
    }

    private void onGateStructureChanged() {
        if (structureRevision < Integer.MAX_VALUE) {
            structureRevision =
                    Math.max(
                            1,
                            structureRevision + 1
                    );
        }

        gateStructureTagPresent =
                true;

        rebuildGatePartIndex();

        markRenderDataChanged();

        hingeConfigurationValid =
                GateConfigurationValidator.isValid(
                        gateParts,
                        leftHinge,
                        rightHinge,
                        gateOrientation
                )
                        && openingDirection != null;

        if (!hingeConfigurationValid) {
            clearGateConfiguration();
            settleUnconfiguredAnimationState();
        }

        markDirty();

        synchronizePersistentOwnership();

        rebuildRegistryLinks();

        refreshGateWorldLighting();

        worldObj.markBlockForUpdate(
                xCoord,
                yCoord,
                zCoord
        );
    }

    private int findGatePartIndex(
            int relativeX,
            int relativeY,
            int relativeZ
    ) {
        for (int i = 0; i < gateParts.size(); ++i) {
            if (gateParts.get(i).hasSameRelativePosition(
                    relativeX,
                    relativeY,
                    relativeZ
            )) {
                return i;
            }
        }
        return -1;
    }

    private void writeBannerRenderAttachmentsToNBT(
            NBTTagCompound nbt
    ) {
        NBTTagList list = new NBTTagList();
        for (SiegeGateBannerAttachmentData.RenderAttachmentSnapshot snapshot
                : bannerRenderAttachments) {
            if (snapshot != null) {
                list.appendTag(snapshot.writeToNBT());
            }
        }
        nbt.setTag(NBT_GATE_BANNER_RENDER_ATTACHMENTS, list);
    }

    private void readBannerRenderAttachmentsFromNBT(
            NBTTagCompound nbt
    ) {
        bannerRenderAttachments.clear();
        if (nbt == null
                || !nbt.hasKey(NBT_GATE_BANNER_RENDER_ATTACHMENTS)) {
            cachedRenderBoundingBox = null;
            return;
        }
        if (!nbt.hasKey(NBT_GATE_BANNER_RENDER_ATTACHMENTS, TAG_LIST)) {
            cachedRenderBoundingBox = null;
            return;
        }

        NBTTagList list = (NBTTagList)nbt.getTag(
                NBT_GATE_BANNER_RENDER_ATTACHMENTS
        );
        if (list.tagCount() > GateStructureValidator.MAX_GATE_PARTS * 4
                || (list.tagCount() > 0
                && list.func_150303_d() != TAG_COMPOUND)) {
            cachedRenderBoundingBox = null;
            return;
        }

        List<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot> parsed =
                new ArrayList<SiegeGateBannerAttachmentData.RenderAttachmentSnapshot>(
                        list.tagCount()
                );
        for (int i = 0; i < list.tagCount(); ++i) {
            SiegeGateBannerAttachmentData.RenderAttachmentSnapshot snapshot =
                    SiegeGateBannerAttachmentData.RenderAttachmentSnapshot.fromNBT(
                            list.getCompoundTagAt(i)
                    );
            if (snapshot == null) {
                cachedRenderBoundingBox = null;
                return;
            }
            parsed.add(snapshot);
        }

        bannerRenderAttachments.addAll(parsed);
        cachedRenderBoundingBox = null;
    }

    private void writeGateStructureToNBT(
            NBTTagCompound nbt,
            boolean includeSourceAppearance,
            boolean includeFullSourceTileEntityNbt
    ) {
        nbt.setInteger(
                NBT_STRUCTURE_REVISION,
                structureRevision
        );

        if (gateStructureQuarantined) {
            nbt.setBoolean(
                    NBT_STRUCTURE_QUARANTINED,
                    true
            );

            nbt.setString(
                    NBT_STRUCTURE_QUARANTINE_REASON,
                    gateStructureQuarantineReason == null
                            ? "Invalid persisted gate structure."
                            : gateStructureQuarantineReason
            );
        }

        NBTTagList partList =
                new NBTTagList();

        for (GatePartData part
                : gateStructureQuarantined
                ? Collections.<GatePartData>emptyList()
                : gateParts) {

            NBTTagCompound partNbt =
                    new NBTTagCompound();

            partNbt.setInteger(
                    NBT_RELATIVE_X,
                    part.getRelativeX()
            );

            partNbt.setInteger(
                    NBT_RELATIVE_Y,
                    part.getRelativeY()
            );

            partNbt.setInteger(
                    NBT_RELATIVE_Z,
                    part.getRelativeZ()
            );

            partNbt.setString(
                    NBT_LEAF,
                    part.getLeaf().name()
            );

            if (includeSourceAppearance
                    && part.hasStoredSourceAppearance()) {

                partNbt.setString(
                        NBT_SOURCE_BLOCK,
                        part.getSourceBlockName()
                );

                partNbt.setInteger(
                        NBT_SOURCE_META,
                        part.getSourceMetadata()
                );

                if (part.hasSourceTileEntityNbt()) {
                    NBTTagCompound sourceTileEntityNbt =
                            includeFullSourceTileEntityNbt
                                    ? part.getSourceTileEntityNbt()
                                    : getClientRenderSourceTileEntityNbt(
                                            part
                                    );

                    if (sourceTileEntityNbt != null) {
                        partNbt.setTag(
                                NBT_SOURCE_TILE_ENTITY,
                                sourceTileEntityNbt
                        );
                    }
                }
            }

            partList.appendTag(
                    partNbt
            );
        }

        nbt.setTag(
                NBT_GATE_PARTS,
                partList
        );
    }

    /**
     * Client structure sync deliberately excludes arbitrary captured
     * TileEntity restoration NBT. The server-side persistent snapshot remains
     * complete, while the client receives TE state only for explicitly
     * approved visuals that require it.
     */
    private NBTTagCompound getClientRenderSourceTileEntityNbt(
            GatePartData part
    ) {
        if (part == null
                || !(part.getSourceBlock()
                instanceof LOTRBlockGateDwarvenIthildin)) {
            return null;
        }

        return part.getSourceTileEntityNbt();
    }

    private void readGateDataFromNBT(
            NBTTagCompound nbt
    ) {
        structureRevision =
                nbt.hasKey(
                        NBT_STRUCTURE_REVISION,
                        TAG_INT
                )
                        ? Math.max(
                        0,
                        nbt.getInteger(
                                NBT_STRUCTURE_REVISION
                        )
                )
                        : 0;

        gateParts.clear();
        gatePartsByPosition.clear();

        clearGateConfiguration();
        gateBorderTextureEnabled =
                !nbt.hasKey(
                        NBT_GATE_BORDER_TEXTURE_ENABLED
                )
                        || nbt.getBoolean(
                        NBT_GATE_BORDER_TEXTURE_ENABLED
                );
        clearGateStructureQuarantine();
        markRenderDataChanged();

        gateStructureTagPresent =
                nbt.hasKey(
                        NBT_GATE_PARTS
                );

        if (nbt.getBoolean(
                NBT_STRUCTURE_QUARANTINED
        )) {
            quarantineGateStructure(
                    nbt.hasKey(
                            NBT_STRUCTURE_QUARANTINE_REASON,
                            TAG_STRING
                    )
                            ? nbt.getString(
                            NBT_STRUCTURE_QUARANTINE_REASON
                    )
                            : "Invalid persisted gate structure."
            );

            return;
        }

        if (!nbt.hasKey(
                NBT_GATE_PARTS,
                TAG_LIST
        )) {
            if (gateStructureTagPresent) {
                quarantineGateStructure(
                        "GateParts is not a list."
                );
            }

            return;
        }

        NBTTagList partList =
                (NBTTagList)nbt.getTag(
                        NBT_GATE_PARTS
                );

        int partCount =
                partList.tagCount();

        if (partCount > 0
                && partList.func_150303_d()
                != TAG_COMPOUND) {

            quarantineGateStructure(
                    "GateParts contains non-compound entries."
            );

            return;
        }

        if (partCount
                > GateStructureValidator.MAX_GATE_PARTS) {

            quarantineGateStructure(
                    "GateParts contains "
                            + partCount
                            + " entries; maximum is "
                            + GateStructureValidator.MAX_GATE_PARTS + "."
            );

            return;
        }

        if (partCount == 0) {
            if (nbt.hasKey(
                    NBT_GATE_HINGES
            )
                    || nbt.hasKey(
                    NBT_GATE_ORIENTATION
            )
                    || nbt.hasKey(
                    NBT_OPENING_DIRECTION
            )) {

                quarantineGateStructure(
                        "An empty GateParts list has "
                                + "persisted gate configuration."
                );
            }

            return;
        }

        List<GatePartData> loadedParts =
                new ArrayList<GatePartData>(
                        partCount
                );

        for (int i = 0;
             i < partList.tagCount();
             ++i) {

            NBTTagCompound partNbt =
                    partList.getCompoundTagAt(
                            i
                    );

            if (!partNbt.hasKey(
                    NBT_RELATIVE_X,
                    TAG_INT
            )
                    || !partNbt.hasKey(
                    NBT_RELATIVE_Y,
                    TAG_INT
            )
                    || !partNbt.hasKey(
                    NBT_RELATIVE_Z,
                    TAG_INT
            )
                    || !partNbt.hasKey(
                    NBT_LEAF,
                    TAG_STRING
            )) {

                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " is missing required fields."
                );

                return;
            }

            String serializedLeaf =
                    partNbt.getString(
                            NBT_LEAF
                    );

            if (serializedLeaf.length() > 16) {
                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has an oversized leaf identifier."
                );

                return;
            }

            GateLeaf leaf =
                    GateLeaf.fromSerializedName(
                            serializedLeaf
                    );

            if (leaf == null) {
                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has an invalid leaf."
                );

                return;
            }

            boolean hasSourceName =
                    partNbt.hasKey(
                            NBT_SOURCE_BLOCK,
                            TAG_STRING
                    );

            boolean hasSourceMetadata =
                    partNbt.hasKey(
                            NBT_SOURCE_META,
                            TAG_INT
                    );

            boolean hasSourceTileEntity =
                    partNbt.hasKey(
                            NBT_SOURCE_TILE_ENTITY,
                            TAG_COMPOUND
                    );

            /*
             * Fail closed if somebody persisted SourceTileEntity with
             * the wrong NBT type.
             */
            if (partNbt.hasKey(
                    NBT_SOURCE_TILE_ENTITY
            )
                    && !hasSourceTileEntity) {

                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has invalid source TileEntity data."
                );

                return;
            }

            /*
             * TE data is meaningless without the block definition it belongs to.
             */
            if (hasSourceTileEntity
                    && (!hasSourceName
                    || !hasSourceMetadata)) {

                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has TileEntity data "
                                + "without source appearance."
                );

                return;
            }

            if (hasSourceName
                    != hasSourceMetadata) {

                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has incomplete source appearance data."
                );

                return;
            }

            String sourceBlockName =
                    hasSourceName
                            ? partNbt.getString(
                            NBT_SOURCE_BLOCK
                    )
                            : null;

            if (sourceBlockName != null
                    && sourceBlockName.length()
                    > MAX_SOURCE_BLOCK_NAME_LENGTH) {

                quarantineGateStructure(
                        "GateParts entry "
                                + i
                                + " has an oversized "
                                + "source block identifier."
                );

                return;
            }

            NBTTagCompound sourceTileEntityNbt =
                    hasSourceTileEntity
                            ? partNbt.getCompoundTag(
                            NBT_SOURCE_TILE_ENTITY
                    )
                            : null;

            /*
             * GateParts here are persisted controller state, not a request to
             * admit a new source block. Decode their already-accepted source
             * definition through the persisted compatibility path so a future
             * live source-policy change cannot make an otherwise healthy
             * controller disagree with its durable ownership record.
             *
             * Persisted controller GateParts only write source appearance for
             * exact source snapshots. Legacy fallback parts omit SourceBlock /
             * SourceMeta entirely and continue through the constructor below.
             */
            GatePartData part =
                    hasSourceName
                            ? GatePartData.fromPersistedSourceSnapshot(
                            partNbt.getInteger(
                                    NBT_RELATIVE_X
                            ),
                            partNbt.getInteger(
                                    NBT_RELATIVE_Y
                            ),
                            partNbt.getInteger(
                                    NBT_RELATIVE_Z
                            ),
                            leaf,
                            sourceBlockName,
                            partNbt.getInteger(
                                    NBT_SOURCE_META
                            ),
                            sourceTileEntityNbt,
                            true
                    )
                            : new GatePartData(
                            partNbt.getInteger(
                                    NBT_RELATIVE_X
                            ),
                            partNbt.getInteger(
                                    NBT_RELATIVE_Y
                            ),
                            partNbt.getInteger(
                                    NBT_RELATIVE_Z
                            ),
                            leaf
                    );

            loadedParts.add(
                    part
            );
        }

        boolean hasHinges =
                nbt.hasKey(
                        NBT_GATE_HINGES
                );

        boolean hasOrientation =
                nbt.hasKey(
                        NBT_GATE_ORIENTATION
                );

        boolean hasDirection =
                nbt.hasKey(
                        NBT_OPENING_DIRECTION
                );

        boolean hasAnyConfiguration =
                hasHinges
                        || hasOrientation
                        || hasDirection;

        boolean hasCompleteConfiguration =
                nbt.hasKey(
                        NBT_GATE_HINGES,
                        TAG_COMPOUND
                )
                        && nbt.hasKey(
                        NBT_GATE_ORIENTATION,
                        TAG_STRING
                )
                        && nbt.hasKey(
                        NBT_OPENING_DIRECTION,
                        TAG_STRING
                );

        GateStructureValidator.ValidationResult
                validation;

        if (!hasAnyConfiguration) {
            validation =
                    GateStructureValidator
                            .validateStructure(
                                    loadedParts,
                                    xCoord,
                                    yCoord,
                                    zCoord
                            );

        } else if (!hasCompleteConfiguration) {
            quarantineGateStructure(
                    "Persisted gate configuration is incomplete."
            );

            return;

        } else {
            NBTTagCompound hinges =
                    nbt.getCompoundTag(
                            NBT_GATE_HINGES
                    );

            if (!hinges.hasKey(
                    NBT_LEFT_HINGE,
                    TAG_COMPOUND
            )
                    || !hinges.hasKey(
                    NBT_RIGHT_HINGE,
                    TAG_COMPOUND
            )) {

                quarantineGateStructure(
                        "Persisted gate hinges are incomplete."
                );

                return;
            }

            GateHinge loadedLeft =
                    readHingeFromNBT(
                            hinges.getCompoundTag(
                                    NBT_LEFT_HINGE
                            )
                    );

            GateHinge loadedRight =
                    readHingeFromNBT(
                            hinges.getCompoundTag(
                                    NBT_RIGHT_HINGE
                            )
                    );

            GateOrientation loadedOrientation =
                    GateOrientation
                            .fromSerializedName(
                                    nbt.getString(
                                            NBT_GATE_ORIENTATION
                                    )
                            );

            GateOpeningDirection loadedDirection =
                    GateOpeningDirection
                            .fromSerializedName(
                                    nbt.getString(
                                            NBT_OPENING_DIRECTION
                                    )
                            );

            validation =
                    GateStructureValidator
                            .validateFinalized(
                                    loadedParts,
                                    loadedLeft,
                                    loadedRight,
                                    loadedOrientation,
                                    loadedDirection,
                                    xCoord,
                                    yCoord,
                                    zCoord
                            );
        }

        if (!validation.isValid()) {
            quarantineGateStructure(
                    validation.getMessage()
            );

            return;
        }

        gateParts.addAll(
                loadedParts
        );

        rebuildGatePartIndex();

        if (hasCompleteConfiguration) {
            leftHinge =
                    validation.getLeftHinge();

            rightHinge =
                    validation.getRightHinge();

            gateOrientation =
                    validation.getOrientation();

            openingDirection =
                    GateOpeningDirection
                            .fromSerializedName(
                                    nbt.getString(
                                            NBT_OPENING_DIRECTION
                                    )
                            );

            hingeConfigurationValid =
                    true;
        }
    }

    private void writeGateConfigurationToNBT(NBTTagCompound nbt) {
        if (gateStructureQuarantined || !hasCompleteHingeConfiguration()) {
            return;
        }

        NBTTagCompound hinges = new NBTTagCompound();
        hinges.setTag(NBT_LEFT_HINGE, writeHingeToNBT(leftHinge));
        hinges.setTag(NBT_RIGHT_HINGE, writeHingeToNBT(rightHinge));
        nbt.setTag(NBT_GATE_HINGES, hinges);
        nbt.setString(NBT_GATE_ORIENTATION, gateOrientation.name());
        nbt.setString(NBT_OPENING_DIRECTION, openingDirection.name());
        nbt.setBoolean(
                NBT_GATE_BORDER_TEXTURE_ENABLED,
                gateBorderTextureEnabled
        );
    }

    private static NBTTagCompound writeHingeToNBT(GateHinge hinge) {
        NBTTagCompound hingeNbt = new NBTTagCompound();
        hingeNbt.setInteger(NBT_HINGE_X, hinge.getRelativeX());
        hingeNbt.setInteger(NBT_HINGE_Z, hinge.getRelativeZ());
        hingeNbt.setString(NBT_HINGE_SIDE, hinge.getSide().name());
        return hingeNbt;
    }

    private static GateHinge readHingeFromNBT(NBTTagCompound hingeNbt) {
        if (!hingeNbt.hasKey(NBT_HINGE_X, TAG_INT)
                || !hingeNbt.hasKey(NBT_HINGE_Z, TAG_INT)
                || !hingeNbt.hasKey(NBT_HINGE_SIDE, TAG_STRING)) {
            return null;
        }
        GateHingeSide side = GateHingeSide.fromSerializedName(
                hingeNbt.getString(NBT_HINGE_SIDE)
        );
        if (side == null) {
            return null;
        }
        return new GateHinge(
                hingeNbt.getInteger(NBT_HINGE_X),
                hingeNbt.getInteger(NBT_HINGE_Z),
                side
        );
    }

    private void rebuildGatePartIndex() {
        gatePartsByPosition.clear();
        for (GatePartData part : gateParts) {
            gatePartsByPosition.put(new RelativePosition(
                    part.getRelativeX(),
                    part.getRelativeY(),
                    part.getRelativeZ()
            ), part);
        }
    }

    private void clearGateConfiguration() {
        leftHinge = null;
        rightHinge = null;
        gateOrientation = null;
        openingDirection = null;
        gateBorderTextureEnabled = true;
        hingeConfigurationValid = false;
        cachedRenderBoundingBox = null;
    }

    private void quarantineGateStructure(String reason) {
        gateParts.clear();
        gatePartsByPosition.clear();
        clearGateConfiguration();
        gateStructureTagPresent = true;
        gateStructureQuarantined = true;
        String sanitizedReason = reason == null ? "" : reason.trim();
        if (sanitizedReason.length() > 256) {
            sanitizedReason = sanitizedReason.substring(0, 256);
        }
        gateStructureQuarantineReason = sanitizedReason.isEmpty()
                ? "Invalid persisted gate structure."
                : sanitizedReason;
        quarantineDiagnosticLogged = false;
        markRenderDataChanged();
    }

    private void clearGateStructureQuarantine() {
        gateStructureQuarantined = false;
        gateStructureQuarantineReason = null;
        quarantineDiagnosticLogged = false;
    }

    private void logQuarantineOnce() {
        if (quarantineDiagnosticLogged || !gateStructureQuarantined) {
            return;
        }
        quarantineDiagnosticLogged = true;
        FMLLog.warning(
                "[LOTRMoreMobs] Quarantined Siege Gate controller at "
                        + "%d,%d,%d in dimension %d: %s",
                Integer.valueOf(xCoord),
                Integer.valueOf(yCoord),
                Integer.valueOf(zCoord),
                Integer.valueOf(worldObj == null
                        ? 0
                        : worldObj.provider.dimensionId),
                gateStructureQuarantineReason
        );
    }

    private void settleUnconfiguredAnimationState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        if (gateState == GateState.CLOSING) {
            relocateEntitiesBeforeClosing();
            gateState = GateState.CLOSED;
        } else if (gateState == GateState.OPENING) {
            gateState = GateState.OPEN;
        } else {
            return;
        }
        gateStateStartTick = worldObj.getTotalWorldTime();
        gateStateTimingPresent = true;
    }

    private void markGatePartsForRenderUpdate() {
        if (worldObj == null || !worldObj.isRemote || gateParts.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (GatePartData part : gateParts) {
            minX = Math.min(minX, part.getAbsoluteX(xCoord));
            minY = Math.min(minY, part.getAbsoluteY(yCoord));
            minZ = Math.min(minZ, part.getAbsoluteZ(zCoord));
            maxX = Math.max(maxX, part.getAbsoluteX(xCoord));
            maxY = Math.max(maxY, part.getAbsoluteY(yCoord));
            maxZ = Math.max(maxZ, part.getAbsoluteZ(zCoord));
        }
        worldObj.markBlockRangeForRenderUpdate(
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
        );
    }

    private void enforceGateControlMode() {
        if (worldObj == null
                || worldObj.isRemote
                || gateState == GateState.BREACHED) {

            return;
        }

        if (gateControlMode == GateControlMode.LOCKED_CLOSED) {
            if (gateState == GateState.OPEN) {
                if (hasCompleteHingeConfiguration()) {
                    beginGateState(GateState.CLOSING);
                } else {
                    setGateState(GateState.CLOSED);
                }
            }
        } else if (gateControlMode == GateControlMode.HELD_OPEN) {
            if (gateState == GateState.CLOSED) {
                if (hasCompleteHingeConfiguration()) {
                    beginGateState(GateState.OPENING);
                } else {
                    setGateState(GateState.OPEN);
                }
            }
        }
    }

    private boolean beginGateState(
            GateState newGateState
    ) {
        if (newGateState == null
                || worldObj == null
                || worldObj.isRemote
                || gateStructureQuarantined
                || isPersistentGateMutationLocked()
                || gateState == newGateState) {

            return false;
        }

        GateState previousGateState =
                gateState;

        boolean wasClosed =
                previousGateState
                        == GateState.CLOSED;

        boolean willBeClosed =
                newGateState
                        == GateState.CLOSED;

        gateState =
                newGateState;

        gateStateStartTick =
                worldObj.getTotalWorldTime();

        gateStateTimingPresent =
                true;

        /*
         * GateParts change their effective emission and opacity when entering or
         * leaving CLOSED, so Minecraft's saved light field must be recalculated.
         */
        if (wasClosed != willBeClosed) {
            refreshGateWorldLighting();
        }

        markDirty();

        SiegeGateOwnershipData ownership =
                SiegeGateOwnershipData.get(
                        worldObj,
                        false
                );

        if (ownership != null) {
            ownership.synchronizeGateState(
                    this
            );
        }

        SiegeNetwork.syncGateState(
                this
        );

        if (previousGateState == GateState.CLOSED
                && newGateState == GateState.OPENING) {

            worldObj.playSoundEffect(
                    xCoord + 0.5D,
                    yCoord + 0.5D,
                    zCoord + 0.5D,
                    SOUND_GATE_OPEN,
                    1.0F,
                    1.0F
            );
        } else if (previousGateState == GateState.CLOSING
                && newGateState == GateState.CLOSED) {

            /*
             * Fire the close/latch sound at the terminal state transition,
             * immediately after the closing animation completes.
             */
            worldObj.playSoundEffect(
                    xCoord + 0.5D,
                    yCoord + 0.5D,
                    zCoord + 0.5D,
                    SOUND_GATE_CLOSE,
                    1.0F,
                    0.97F + worldObj.rand.nextFloat() * 0.06F
            );
        }

        return true;
    }

    private long getElapsedStateTicks(long currentWorldTick) {
        if (!gateStateTimingPresent
                || gateStateStartTick < 0L
                || gateStateStartTick > currentWorldTick) {
            gateStateStartTick = currentWorldTick;
            gateStateTimingPresent = true;
            markDirty();
            SiegeNetwork.syncGateState(this);
            return 0L;
        }
        return currentWorldTick - gateStateStartTick;
    }

    private void repairLoadedAnimationState() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        boolean changed = false;
        if (!hasCompleteHingeConfiguration()) {
            if (gateState == GateState.OPENING) {
                gateState = GateState.OPEN;
                changed = true;
            } else if (gateState == GateState.CLOSING) {
                gateState = GateState.CLOSED;
                changed = true;
            }
        }

        long currentWorldTick = worldObj.getTotalWorldTime();
        if (!gateStateTimingPresent
                || gateStateStartTick < 0L
                || gateStateStartTick > currentWorldTick) {
            gateStateStartTick = currentWorldTick;
            gateStateTimingPresent = true;
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    private void readSynchronizedStateTiming(NBTTagCompound nbt) {
        if (nbt.hasKey(NBT_GATE_STATE_START_TICK, TAG_LONG)) {
            gateStateStartTick = nbt.getLong(NBT_GATE_STATE_START_TICK);
            gateStateTimingPresent = true;
        } else {
            gateStateStartTick = worldObj == null
                    ? 0L
                    : worldObj.getTotalWorldTime();
            gateStateTimingPresent = false;
        }
    }

    private void readHealthFromNBT(NBTTagCompound nbt) {
        maxHealth = nbt.hasKey(NBT_MAX_HEALTH)
                ? nbt.getInteger(NBT_MAX_HEALTH)
                : getConfiguredDefaultMaxHealth();
        if (maxHealth <= 0) {
            maxHealth = getConfiguredDefaultMaxHealth();
        }
        currentHealth = nbt.hasKey(NBT_CURRENT_HEALTH)
                ? nbt.getInteger(NBT_CURRENT_HEALTH)
                : maxHealth;
        currentHealth = Math.max(0, Math.min(currentHealth, maxHealth));
    }

    private void writeRepairStateToNBT(NBTTagCompound nbt) {
        nbt.setBoolean(NBT_REPAIR_ACTIVE, repairActive);
        nbt.setInteger(
                NBT_REPAIR_PURCHASED_HEALTH,
                repairPurchasedHealth
        );
        nbt.setInteger(NBT_REPAIR_APPLIED_HEALTH, repairAppliedHealth);
        nbt.setInteger(NBT_REPAIR_ACTIVE_TICKS, repairActiveTicks);
        nbt.setLong(NBT_REPAIR_PAUSE_UNTIL, repairPauseUntilTick);
        nbt.setInteger(NBT_REPAIR_COIN_VALUE, repairPurchasedCoinValue);
    }

    private void writeAccessStateToNBT(NBTTagCompound nbt) {
        nbt.setString(NBT_GATE_NAME, gateName);
        if (ownerUuid != null) {
            nbt.setString(NBT_OWNER_UUID, ownerUuid.toString());
        }
        if (gateFaction != null) {
            nbt.setString(NBT_GATE_FACTION, gateFaction.codeName());
        }
        nbt.setInteger(NBT_REQUIRED_ALIGNMENT, requiredAlignment);
        nbt.setBoolean(
                NBT_FACTION_ACCESS_ENABLED,
                factionAccessEnabled
        );
        nbt.setTag(NBT_EDITORS, writeUuidList(editorUuids));
        nbt.setTag(NBT_OPERATORS, writeUuidList(operatorUuids));
        nbt.setTag(
                NBT_ACCESS_WHITELIST,
                writeUuidList(accessWhitelistUuids)
        );
        if (reservedRamUuid != null) {
            nbt.setString(
                    NBT_RESERVED_RAM_UUID,
                    reservedRamUuid.toString()
            );
        }
    }

    private void readRepairStateFromNBT(NBTTagCompound nbt) {
        repairActive = nbt.getBoolean(NBT_REPAIR_ACTIVE);
        repairPurchasedHealth = Math.max(
                0,
                nbt.getInteger(NBT_REPAIR_PURCHASED_HEALTH)
        );
        repairAppliedHealth = Math.max(
                0,
                Math.min(
                        nbt.getInteger(NBT_REPAIR_APPLIED_HEALTH),
                        repairPurchasedHealth
                )
        );
        /*
         * RepairAppliedHealth is authoritative for persisted progress.
         *
         * Reconstruct active time from it so saves made under the old
         * fixed-duration repair system do not suddenly jump forward after
         * switching to the 5 HP/sec model.
         */
        repairActiveTicks =
                Math.max(
                        0,
                        repairAppliedHealth
                                * REPAIR_TICKS_PER_HEALTH
                );
        repairPauseUntilTick = Math.max(
                0L,
                nbt.getLong(NBT_REPAIR_PAUSE_UNTIL)
        );
        repairPurchasedCoinValue = Math.max(
                0,
                nbt.getInteger(NBT_REPAIR_COIN_VALUE)
        );
        if (!repairActive
                || repairPurchasedHealth <= 0
                || repairAppliedHealth
                >= repairPurchasedHealth
                || currentHealth >= maxHealth) {

            clearRepairState();
        } else {
            /*
             * Sound-only burst timing is intentionally transient. After a
             * reload, wait a full quiet interval instead of immediately
             * emitting a hammer burst.
             */
            repairBuildSoundHitsRemaining = 0;
            repairBuildSoundTicksUntilNext =
                    REPAIR_BUILD_BURST_MIN_GAP_TICKS;
        }
    }

    private void readAccessStateFromNBT(NBTTagCompound nbt) {
        gateName = sanitizeGateName(nbt.getString(NBT_GATE_NAME));
        ownerUuid = readUuid(nbt.getString(NBT_OWNER_UUID));
        String factionName = nbt.getString(NBT_GATE_FACTION);
        LOTRFaction loadedFaction = factionName.isEmpty()
                ? null
                : LOTRFaction.forName(factionName);
        gateFaction = loadedFaction != null
                && LOTRFaction.getPlayableAlignmentFactions().contains(
                loadedFaction
        )
                ? loadedFaction
                : null;
        requiredAlignment = nbt.hasKey(NBT_REQUIRED_ALIGNMENT, TAG_INT)
                ? nbt.getInteger(NBT_REQUIRED_ALIGNMENT)
                : DEFAULT_REQUIRED_ALIGNMENT;
        requiredAlignment = Math.max(
                1,
                Math.min(requiredAlignment, MAX_REQUIRED_ALIGNMENT)
        );

        /*
         * Missing key means this is a gate saved before faction access became
         * independently configurable. Preserve its historical behavior.
         */
        factionAccessEnabled =
                !nbt.hasKey(
                        NBT_FACTION_ACCESS_ENABLED
                )
                        || nbt.getBoolean(
                        NBT_FACTION_ACCESS_ENABLED
                );
        readUuidList(nbt, NBT_EDITORS, editorUuids);
        readUuidList(nbt, NBT_OPERATORS, operatorUuids);
        readUuidList(nbt, NBT_ACCESS_WHITELIST, accessWhitelistUuids);
        if (ownerUuid != null) {
            editorUuids.remove(ownerUuid);
            operatorUuids.remove(ownerUuid);
            accessWhitelistUuids.remove(ownerUuid);
        }
        reservedRamUuid = readUuid(nbt.getString(NBT_RESERVED_RAM_UUID));
        reservedRamLastSeenTick = 0L;
    }

    private void normalizeLoadedHealthState() {
        if (worldObj == null || worldObj.isRemote || !isFinalized()) {
            return;
        }

        boolean changed = false;
        if (gateState == GateState.BREACHED) {
            if (repairActive
                    && currentHealth >= getBreachedRecoveryHealth()) {
                gateState = GateState.OPEN;
                gateStateStartTick = worldObj.getTotalWorldTime();
                gateStateTimingPresent = true;
                changed = true;
            } else if (!repairActive && currentHealth != 0) {
                currentHealth = 0;
                changed = true;
            }
        } else if (currentHealth <= 0) {
            gateState = GateState.BREACHED;
            gateStateStartTick = worldObj.getTotalWorldTime();
            gateStateTimingPresent = true;
            changed = true;
        }
        if (changed) {
            markDirty();
        }
    }

    private void updateRepairJob() {
        if (!repairActive
                || isPersistentGateMutationLocked()) {

            return;
        }

        long worldTick =
                worldObj.getTotalWorldTime();

        /*
         * Damage pauses the repair without consuming repair progress.
         */
        if (repairPauseUntilTick > worldTick) {
            return;
        }

        ++repairActiveTicks;

        updateRepairBuildSoundBurst();

        boolean healthChanged =
                false;

        /*
         * 20 ticks/sec / 5 HP/sec = one HP every four active ticks.
         */
        if (repairActiveTicks
                % REPAIR_TICKS_PER_HEALTH
                == 0) {

            int remainingPurchasedHealth =
                    Math.max(
                            0,
                            repairPurchasedHealth
                                    - repairAppliedHealth
                    );

            if (remainingPurchasedHealth > 0
                    && currentHealth < maxHealth) {

                int previousHealth =
                        currentHealth;

                currentHealth =
                        Math.min(
                                maxHealth,
                                currentHealth + 1
                        );

                if (currentHealth
                        > previousHealth) {

                    ++repairAppliedHealth;

                    healthChanged =
                            true;

                }
            }
        }

        boolean recoveredFromBreach =
                gateState == GateState.BREACHED
                        && currentHealth
                        >= getBreachedRecoveryHealth();

        if (recoveredFromBreach) {
            beginGateState(
                    GateState.OPEN
            );
        }

        boolean completed =
                repairAppliedHealth
                        >= repairPurchasedHealth
                        || currentHealth
                        >= maxHealth;

        boolean intervalSync =
                repairActiveTicks
                        % REPAIR_SYNC_INTERVAL_TICKS
                        == 0;

        if (completed) {
            clearRepairState();
        }

        if (healthChanged
                || intervalSync
                || completed
                || recoveredFromBreach) {

            markDirty();
        }

        if (intervalSync
                || completed
                || recoveredFromBreach) {

            SiegeNetwork.syncGateRepair(
                    this
            );

            SiegeNetwork.syncGateHealth(
                    this
            );
        }
    }

    private void updateRamReservation() {
        if (reservedRamUuid == null) {
            return;
        }
        long worldTick = worldObj.getTotalWorldTime();
        if (reservedRamLastSeenTick <= 0L) {
            reservedRamLastSeenTick = worldTick;
        } else if (worldTick - reservedRamLastSeenTick
                > RAM_RESERVATION_TIMEOUT_TICKS) {
            clearRamReservation(reservedRamUuid);
        }
    }

    private int getBreachedRecoveryHealth() {
        return (int)Math.max(
                1L,
                ((long)maxHealth * BREACHED_RECOVERY_PERCENT + 99L)
                        / 100L
        );
    }

    private void clearRepairState() {
        repairActive = false;
        repairPurchasedHealth = 0;
        repairAppliedHealth = 0;
        repairActiveTicks = 0;
        repairPauseUntilTick = 0L;
        repairPurchasedCoinValue = 0;
        repairBuildSoundTicksUntilNext = 0;
        repairBuildSoundHitsRemaining = 0;
    }

    private void updateRepairBuildSoundBurst() {
        if (worldObj == null
                || worldObj.isRemote
                || !repairActive) {

            return;
        }

        if (repairBuildSoundTicksUntilNext > 0) {
            --repairBuildSoundTicksUntilNext;
            return;
        }

        if (repairBuildSoundHitsRemaining <= 0) {
            /*
             * Each work cue is a short, irregular 2-3 hammer cluster rather
             * than a single isolated hit. This reads as active repair while
             * leaving long quiet gaps so the sound does not become annoying.
             */
            repairBuildSoundHitsRemaining =
                    2 + worldObj.rand.nextInt(2);
        }

        worldObj.playSoundEffect(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D,
                SOUND_GATE_REPAIR_BUILD,
                0.5F,
                0.94F + worldObj.rand.nextFloat() * 0.12F
        );

        --repairBuildSoundHitsRemaining;

        if (repairBuildSoundHitsRemaining > 0) {
            repairBuildSoundTicksUntilNext =
                    randomRepairBuildHitSpacingTicks();
        } else {
            repairBuildSoundTicksUntilNext =
                    randomRepairBuildBurstGapTicks();
        }
    }

    private int randomRepairBuildBurstGapTicks() {
        if (worldObj == null) {
            return REPAIR_BUILD_BURST_MIN_GAP_TICKS;
        }

        return REPAIR_BUILD_BURST_MIN_GAP_TICKS
                + worldObj.rand.nextInt(
                REPAIR_BUILD_BURST_MAX_GAP_TICKS
                        - REPAIR_BUILD_BURST_MIN_GAP_TICKS
                        + 1
        );
    }

    private int randomRepairBuildHitSpacingTicks() {
        if (worldObj == null) {
            return REPAIR_BUILD_BURST_MIN_HIT_SPACING_TICKS;
        }

        return REPAIR_BUILD_BURST_MIN_HIT_SPACING_TICKS
                + worldObj.rand.nextInt(
                REPAIR_BUILD_BURST_MAX_HIT_SPACING_TICKS
                        - REPAIR_BUILD_BURST_MIN_HIT_SPACING_TICKS
                        + 1
        );
    }

    private void refreshGateWorldLighting() {
        if (worldObj == null
                || gateParts.isEmpty()) {

            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;

        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        boolean foundPart = false;

        for (GatePartData part : gateParts) {
            if (part == null
                    || !part.hasValidAbsolutePosition(
                    xCoord,
                    yCoord,
                    zCoord
            )) {

                continue;
            }

            int partX =
                    part.getAbsoluteX(xCoord);

            int partY =
                    part.getAbsoluteY(yCoord);

            int partZ =
                    part.getAbsoluteZ(zCoord);

            minX = Math.min(minX, partX);
            minY = Math.min(minY, partY);
            minZ = Math.min(minZ, partZ);

            maxX = Math.max(maxX, partX);
            maxY = Math.max(maxY, partY);
            maxZ = Math.max(maxZ, partZ);

            foundPart = true;
        }

        if (!foundPart) {
            return;
        }

        /*
         * Include the lighting boundary around the gate. This is especially
         * important for the bottom row, whose lighting depends on the cells
         * immediately below and beside the GateParts.
         */
        --minX;
        minY = Math.max(0, minY - 1);
        --minZ;

        ++maxX;
        maxY = Math.min(
                worldObj.getHeight() - 1,
                maxY + 1
        );
        ++maxZ;

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {

                    if (!worldObj.blockExists(
                            x,
                            y,
                            z
                    )) {

                        continue;
                    }

                    worldObj.func_147451_t(
                            x,
                            y,
                            z
                    );
                }
            }
        }

        if (worldObj.isRemote) {
            /*
             * Do not discard the TESR cache for a lighting refresh alone.
             * RenderSiegeGate already detects CLOSED/non-CLOSED envelope
             * changes and lighting-signature changes, and can rebuild while
             * retaining the previous GateRenderBlockAccess. That retention is
             * required for Ithildin doors because their glow interpolation is
             * transient TileEntity state and is not present in captured NBT.
             *
             * Releasing the cache here destroyed that transient state exactly
             * when CLOSED <-> moving transitions refreshed world lighting,
             * causing the rune glow to restart from zero. Structural changes
             * still invalidate through render-data revision changes, while
             * chunk unload/invalidation continue to release the cache normally.
             */
            markGatePartsForRenderUpdate();
        }
    }

    private boolean toggleAccessEntry(
            EntityPlayerMP player,
            UUID targetUuid,
            Set<UUID> entries
    ) {
        if (isPersistentGateMutationLocked() || !canManagePlayerAccess(player)
                || targetUuid == null
                || targetUuid.equals(ownerUuid)) {
            return false;
        }
        if (!entries.remove(targetUuid)) {
            if (entries.size() >= MAX_ACCESS_ENTRIES) {
                return false;
            }
            entries.add(targetUuid);
        }
        onAccessStateChanged();
        return true;
    }

    private int getDistinctPlayerAccessEntryCount() {
        Set<UUID> entries =
                new HashSet<UUID>();

        entries.addAll(
                editorUuids
        );

        entries.addAll(
                operatorUuids
        );

        entries.addAll(
                accessWhitelistUuids
        );

        return entries.size();
    }

    private void onAccessStateChanged() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        markDirty();
        SiegeNetwork.syncGateAccess(this);
    }

    private static String sanitizeGateName(String requestedName) {
        if (requestedName == null) {
            return DEFAULT_GATE_NAME;
        }
        StringBuilder sanitized = new StringBuilder();
        for (int i = 0; i < requestedName.length()
                && sanitized.length() < MAX_GATE_NAME_LENGTH; ++i) {
            char character = requestedName.charAt(i);
            if (character >= 32
                    && character != 127
                    && character != '\u00a7') {
                sanitized.append(character);
            }
        }
        String result = sanitized.toString().trim();
        return result.isEmpty() ? DEFAULT_GATE_NAME : result;
    }

    private static NBTTagList writeUuidList(Collection<UUID> uuids) {
        NBTTagList list = new NBTTagList();
        int count = 0;
        for (UUID uuid : uuids) {
            if (uuid != null && count++ < MAX_ACCESS_ENTRIES) {
                list.appendTag(new NBTTagString(uuid.toString()));
            }
        }
        return list;
    }

    private static void readUuidList(
            NBTTagCompound nbt,
            String key,
            Set<UUID> destination
    ) {
        destination.clear();
        if (!nbt.hasKey(key, TAG_LIST)) {
            return;
        }
        NBTTagList list = nbt.getTagList(key, TAG_STRING);
        for (int i = 0; i < list.tagCount()
                && destination.size() < MAX_ACCESS_ENTRIES; ++i) {
            UUID uuid = readUuid(list.getStringTagAt(i));
            if (uuid != null) {
                destination.add(uuid);
            }
        }
    }

    private static void replaceUuidSet(
            Set<UUID> destination,
            Collection<UUID> values
    ) {
        destination.clear();
        if (values == null) {
            return;
        }
        for (UUID value : values) {
            if (value != null
                    && destination.size() < MAX_ACCESS_ENTRIES) {
                destination.add(value);
            }
        }
    }

    private static UUID readUuid(String serializedUuid) {
        if (serializedUuid != null && !serializedUuid.isEmpty()) {
            try {
                return UUID.fromString(serializedUuid);
            } catch (IllegalArgumentException ignored) {
                // Malformed access-list UUIDs are ignored safely.
            }
        }
        return null;
    }

    private void sendBreachFeedback() {
        worldObj.playSoundEffect(
                xCoord + 0.5D,
                yCoord + 0.5D,
                zCoord + 0.5D,
                SOUND_GATE_BREACH,
                1.5F,
                0.92F + worldObj.rand.nextFloat() * 0.08F
        );
        spawnBreachParticles();

        double radiusSquared = BREACH_MESSAGE_RADIUS
                * BREACH_MESSAGE_RADIUS;
        for (Object object : worldObj.playerEntities) {
            if (object instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer)object;
                if (player.getDistanceSq(
                        xCoord + 0.5D,
                        yCoord + 0.5D,
                        zCoord + 0.5D
                ) <= radiusSquared) {
                    ChatComponentText message =
                            new ChatComponentText("[");
                    message.appendSibling(new ChatComponentText(gateName)
                            .setChatStyle(new ChatStyle().setColor(
                                    EnumChatFormatting.GOLD
                            )));
                    message.appendSibling(new ChatComponentText(
                            "] has been "
                    ));
                    message.appendSibling(new ChatComponentText(
                            "BREACHED!"
                    ).setChatStyle(new ChatStyle()
                            .setColor(EnumChatFormatting.RED)
                            .setBold(Boolean.TRUE)));
                    player.addChatMessage(message);
                }
            }
        }
    }

    private void spawnBreachParticles() {
        if (!(worldObj instanceof WorldServer) || gateParts.isEmpty()) {
            return;
        }

        WorldServer serverWorld = (WorldServer)worldObj;
        int stride = Math.max(
                1,
                (gateParts.size() + MAX_BREACH_PARTICLE_POSITIONS - 1)
                        / MAX_BREACH_PARTICLE_POSITIONS
        );
        for (int i = 0; i < gateParts.size(); i += stride) {
            GatePartData part = gateParts.get(i);
            Block sourceBlock = part.getSourceBlock();
            String particleName = "blockcrack_"
                    + Block.getIdFromBlock(sourceBlock)
                    + "_"
                    + part.getSourceMetadata();
            serverWorld.func_147487_a(
                    particleName,
                    part.getAbsoluteX(xCoord) + 0.5D,
                    part.getAbsoluteY(yCoord) + 0.5D,
                    part.getAbsoluteZ(zCoord) + 0.5D,
                    3,
                    0.35D,
                    0.35D,
                    0.35D,
                    0.08D
            );

            serverWorld.func_147487_a(
                    "largesmoke",
                    xCoord + 0.5D,
                    yCoord + 1.0D,
                    zCoord + 0.5D,
                    5,
                    0.45D,
                    0.35D,
                    0.45D,
                    0.025D
            );
        }
    }

    private void relocateEntitiesBeforeClosing() {
        if (worldObj == null || worldObj.isRemote || gateParts.isEmpty()) {
            return;
        }
        AxisAlignedBB closedBounds = getClosedGateBounds();
        if (closedBounds == null) {
            return;
        }

        List entities = worldObj.getEntitiesWithinAABBExcludingEntity(
                null,
                closedBounds
        );
        for (Object object : entities) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity)object;
            if (entity.isDead
                    || entity.boundingBox == null
                    || !overlapsClosedGatePart(entity.boundingBox)) {
                continue;
            }

            EntityDisplacement displacement = findSafeDisplacement(
                    entity,
                    closedBounds
            );
            if (displacement != null) {
                moveEntity(entity, displacement);
            }
        }
    }

    private boolean overlapsClosedGatePart(AxisAlignedBB entityBounds) {
        for (GatePartData part : gateParts) {
            int partX = part.getAbsoluteX(xCoord);
            int partY = part.getAbsoluteY(yCoord);
            int partZ = part.getAbsoluteZ(zCoord);
            if (worldObj.blockExists(partX, partY, partZ)
                    && worldObj.getBlock(partX, partY, partZ)
                    == SiegeRegistry.gatePart
                    && GateRegistry.isPartOwnedBy(
                    worldObj,
                    partX,
                    partY,
                    partZ,
                    this
            )
                    && entityBounds.intersectsWith(
                    AxisAlignedBB.getBoundingBox(
                            partX,
                            partY,
                            partZ,
                            partX + 1.0D,
                            partY + 1.0D,
                            partZ + 1.0D
                    )
            )) {
                return true;
            }
        }
        return false;
    }

    private EntityDisplacement findSafeDisplacement(
            Entity entity,
            AxisAlignedBB gateBounds
    ) {
        boolean normalIsX = gateOrientation == GateOrientation.WIDTH_Z
                || (gateOrientation == null
                && gateBounds.maxX - gateBounds.minX
                <= gateBounds.maxZ - gateBounds.minZ);
        double negativeBase = normalIsX
                ? gateBounds.minX - entity.boundingBox.maxX - 0.01D
                : gateBounds.minZ - entity.boundingBox.maxZ - 0.01D;
        double positiveBase = normalIsX
                ? gateBounds.maxX - entity.boundingBox.minX + 0.01D
                : gateBounds.maxZ - entity.boundingBox.minZ + 0.01D;
        double[] normalBases = negativeBase * negativeBase
                <= positiveBase * positiveBase
                ? new double[] {negativeBase, positiveBase}
                : new double[] {positiveBase, negativeBase};
        double[] tangentOffsets = {
                0.0D, -1.0D, 1.0D, -2.0D, 2.0D
        };
        double[] verticalOffsets = {0.0D, 1.0D, 2.0D, 3.0D};
        EntityDisplacement best = null;

        for (double normalBase : normalBases) {
            double outwardSign = normalBase < 0.0D ? -1.0D : 1.0D;
            for (int outward = 0; outward <= 3; ++outward) {
                double normalOffset = normalBase
                        + outwardSign * outward;
                for (double verticalOffset : verticalOffsets) {
                    for (double tangentOffset : tangentOffsets) {
                        double offsetX = normalIsX
                                ? normalOffset
                                : tangentOffset;
                        double offsetZ = normalIsX
                                ? tangentOffset
                                : normalOffset;
                        EntityDisplacement candidate =
                                new EntityDisplacement(
                                        offsetX,
                                        verticalOffset,
                                        offsetZ
                                );
                        if ((best == null
                                || candidate.distanceSquared
                                < best.distanceSquared)
                                && isSafeEntityDestination(
                                entity,
                                candidate
                        )) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        if (best != null) {
            return best;
        }
        return normalIsX
                ? new EntityDisplacement(normalBases[0], 0.0D, 0.0D)
                : new EntityDisplacement(0.0D, 0.0D, normalBases[0]);
    }

    private boolean isSafeEntityDestination(
            Entity entity,
            EntityDisplacement displacement
    ) {
        AxisAlignedBB destination = entity.boundingBox.getOffsetBoundingBox(
                displacement.x,
                displacement.y,
                displacement.z
        );
        return worldObj.getCollidingBoundingBoxes(
                entity,
                destination
        ).isEmpty();
    }

    private static void moveEntity(
            Entity entity,
            EntityDisplacement displacement
    ) {
        double destinationX = entity.posX + displacement.x;
        double destinationY = entity.posY + displacement.y;
        double destinationZ = entity.posZ + displacement.z;
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.fallDistance = 0.0F;
        if (entity instanceof EntityPlayerMP) {
            ((EntityPlayerMP)entity).setPositionAndUpdate(
                    destinationX,
                    destinationY,
                    destinationZ
            );
        } else {
            entity.setPosition(destinationX, destinationY, destinationZ);
        }
    }

    private AxisAlignedBB getClosedGateBounds() {
        if (gateParts.isEmpty()) {
            return null;
        }
        RenderBounds bounds = new RenderBounds();
        for (GatePartData part : gateParts) {
            bounds.include(
                    part.getAbsoluteX(xCoord),
                    part.getAbsoluteY(yCoord),
                    part.getAbsoluteZ(zCoord),
                    part.getAbsoluteX(xCoord) + 1.0D,
                    part.getAbsoluteY(yCoord) + 1.0D,
                    part.getAbsoluteZ(zCoord) + 1.0D
            );
        }
        return bounds.toAxisAlignedBB(0.0D);
    }

    private AxisAlignedBB calculateRenderBoundingBox() {
        RenderBounds bounds = new RenderBounds();
        bounds.include(
                xCoord,
                yCoord,
                zCoord,
                xCoord + 1.0D,
                yCoord + 1.0D,
                zCoord + 1.0D
        );
        if (hasCompleteHingeConfiguration()) {
            includeLeafSweepBounds(bounds, GateLeaf.LEFT, leftHinge);
            includeLeafSweepBounds(bounds, GateLeaf.RIGHT, rightHinge);
        } else {
            for (GatePartData part : gateParts) {
                bounds.include(
                        part.getAbsoluteX(xCoord),
                        part.getAbsoluteY(yCoord),
                        part.getAbsoluteZ(zCoord),
                        part.getAbsoluteX(xCoord) + 1.0D,
                        part.getAbsoluteY(yCoord) + 1.0D,
                        part.getAbsoluteZ(zCoord) + 1.0D
                );
            }
        }
        return bounds.toAxisAlignedBB(
                bannerRenderAttachments.isEmpty() ? 0.25D : 3.25D
        );
    }

    private void includeLeafSweepBounds(
            RenderBounds bounds,
            GateLeaf leaf,
            GateHinge hinge
    ) {
        double pivotX = xCoord
                + hinge.getPivotRelativeX(gateOrientation);
        double pivotZ = zCoord
                + hinge.getPivotRelativeZ(gateOrientation);
        double radiusSquared = 0.0D;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (GatePartData part : gateParts) {
            if (!part.getLeaf().contributesTo(leaf)) {
                continue;
            }
            double minX = part.getAbsoluteX(xCoord);
            double maxX = minX + 1.0D;
            double minZ = part.getAbsoluteZ(zCoord);
            double maxZ = minZ + 1.0D;
            radiusSquared = Math.max(radiusSquared, distanceSquaredXZ(
                    minX,
                    minZ,
                    pivotX,
                    pivotZ
            ));
            radiusSquared = Math.max(radiusSquared, distanceSquaredXZ(
                    minX,
                    maxZ,
                    pivotX,
                    pivotZ
            ));
            radiusSquared = Math.max(radiusSquared, distanceSquaredXZ(
                    maxX,
                    minZ,
                    pivotX,
                    pivotZ
            ));
            radiusSquared = Math.max(radiusSquared, distanceSquaredXZ(
                    maxX,
                    maxZ,
                    pivotX,
                    pivotZ
            ));
            minY = Math.min(minY, part.getAbsoluteY(yCoord));
            maxY = Math.max(maxY, part.getAbsoluteY(yCoord) + 1.0D);
        }
        if (minY != Double.POSITIVE_INFINITY) {
            double radius = Math.sqrt(radiusSquared);
            bounds.include(
                    pivotX - radius,
                    minY,
                    pivotZ - radius,
                    pivotX + radius,
                    maxY,
                    pivotZ + radius
            );
        }
    }

    private static double distanceSquaredXZ(
            double x,
            double z,
            double pivotX,
            double pivotZ
    ) {
        double deltaX = x - pivotX;
        double deltaZ = z - pivotZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private void markRenderDataChanged() {
        ++renderDataRevision;
        cachedRenderBoundingBox = null;
    }

    private UUID ensureGateUuid() {
        if (gateUuid == null && worldObj != null && !worldObj.isRemote) {
            gateUuid = UUID.randomUUID();
            markDirty();
        }
        return gateUuid;
    }

    private static UUID readGateUuid(NBTTagCompound nbt) {
        String serializedUuid = nbt.getString(NBT_GATE_UUID);
        if (!serializedUuid.isEmpty()) {
            try {
                return UUID.fromString(serializedUuid);
            } catch (IllegalArgumentException ignored) {
                // A malformed UUID cannot be reused safely.
            }
        }
        return null;
    }

    private static final class EntityDisplacement {
        private final double x;
        private final double y;
        private final double z;
        private final double distanceSquared;

        private EntityDisplacement(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.distanceSquared = x * x + y * y + z * z;
        }
    }

    private static final class RenderBounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;

        private void include(
                double includedMinX,
                double includedMinY,
                double includedMinZ,
                double includedMaxX,
                double includedMaxY,
                double includedMaxZ
        ) {
            minX = Math.min(minX, includedMinX);
            minY = Math.min(minY, includedMinY);
            minZ = Math.min(minZ, includedMinZ);
            maxX = Math.max(maxX, includedMaxX);
            maxY = Math.max(maxY, includedMaxY);
            maxZ = Math.max(maxZ, includedMaxZ);
        }

        private AxisAlignedBB toAxisAlignedBB(double margin) {
            return AxisAlignedBB.getBoundingBox(
                    minX - margin,
                    minY - margin,
                    minZ - margin,
                    maxX + margin,
                    maxY + margin,
                    maxZ + margin
            );
        }
    }

    private static final class RelativePosition {
        private final int x;
        private final int y;
        private final int z;

        private RelativePosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RelativePosition)) {
                return false;
            }

            RelativePosition position = (RelativePosition)other;
            return x == position.x
                    && y == position.y
                    && z == position.z;
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
