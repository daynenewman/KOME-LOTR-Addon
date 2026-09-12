package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.siege.network.SiegeNetwork;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.world.map.LOTRAbstractWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public class RamControlEventHandler {

    private final Map<World, Map<UUID, WeakReference<EntityBattleRam>>>
            loadedRams = new WeakHashMap<
            World,
            Map<UUID, WeakReference<EntityBattleRam>>>();
    /*
     * LivingDeathEvent changes durable ownership before vanilla finishes
     * removing the dying entity. Keep that exact, already-validated entity
     * identity transiently so its final update callbacks cannot reinterpret
     * the intentionally pending slot as corrupt ownership.
     */
    private final Map<LOTREntityNPC, TerminalGenuineDeath>
            terminalGenuineDeaths = new WeakHashMap<
            LOTREntityNPC, TerminalGenuineDeath>();

    /*
     * LOTR's native fast travel directly moves the player and native hired
     * NPCs inside LOTRPlayerData.fastTravelTo(). Battle Rams are not
     * LOTREntityNPC instances, so arm a small transient snapshot while LOTR's
     * target waypoint is active and complete the ram transfer only after the
     * player actually arrives at that waypoint.
     */
    private static final double FAST_TRAVEL_DESTINATION_TOLERANCE = 4.0D;
    private static final double FAST_TRAVEL_MIN_DISPLACEMENT = 8.0D;
    private final Map<EntityPlayerMP, PendingFastTravel>
            pendingFastTravels = new WeakHashMap<
            EntityPlayerMP, PendingFastTravel>();

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            RamControlManager.processQueuedActions();
        }
    }

    private void updatePlayerFastTravelState(
            EntityPlayerMP player
    ) {
        if (player == null
                || player.worldObj == null
                || player.worldObj.isRemote
                || player.isDead) {
            if (player != null) {
                pendingFastTravels.remove(player);
            }
            return;
        }

        LOTRPlayerData playerData =
                LOTRLevelData.getData(player);
        LOTRAbstractWaypoint target =
                playerData == null
                        ? null
                        : playerData.getTargetFTWaypoint();

        PendingFastTravel pending =
                pendingFastTravels.get(player);

        if (target != null) {
            int dimension =
                    player.worldObj.provider.dimensionId;
            int targetX = target.getXCoord();
            int targetZ = target.getZCoord();

            if (pending == null
                    || !pending.matchesTarget(
                    dimension,
                    targetX,
                    targetZ
            )) {
                pending = new PendingFastTravel(
                        dimension,
                        player.posX,
                        player.posZ,
                        targetX,
                        targetZ
                );
                pendingFastTravels.put(
                        player,
                        pending
                );
            }

            captureFastTravelFollowRams(
                    player,
                    pending
            );
            return;
        }

        if (pending == null) {
            return;
        }

        pendingFastTravels.remove(player);

        /*
         * Target removal also happens when fast travel is cancelled. Only
         * transfer rams if the player actually made LOTR's waypoint jump.
         */
        if (!pending.matchesCompletedTravel(player)) {
            return;
        }

        for (WeakReference<EntityBattleRam> reference
                : pending.rams.values()) {
            EntityBattleRam ram =
                    reference == null
                            ? null
                            : reference.get();

            if (!isFastTravelFollowEligible(
                    ram,
                    player
            )) {
                continue;
            }

            ram.teleportFollowFormationAfterFastTravel(
                    player
            );
        }
    }

    private void captureFastTravelFollowRams(
            EntityPlayerMP player,
            PendingFastTravel pending
    ) {
        if (player == null
                || pending == null
                || player.worldObj == null) {
            return;
        }

        Map<UUID, WeakReference<EntityBattleRam>>
                worldRams =
                loadedRams.get(player.worldObj);

        if (worldRams == null) {
            return;
        }

        for (Map.Entry<
                UUID,
                WeakReference<EntityBattleRam>> entry
                : worldRams.entrySet()) {
            WeakReference<EntityBattleRam> reference =
                    entry.getValue();
            EntityBattleRam ram =
                    reference == null
                            ? null
                            : reference.get();

            if (isFastTravelFollowEligible(
                    ram,
                    player
            )) {
                pending.rams.put(
                        ram.getUniqueID(),
                        new WeakReference<EntityBattleRam>(
                                ram
                        )
                );
            }
        }
    }

    private static boolean isFastTravelFollowEligible(
            EntityBattleRam ram,
            EntityPlayerMP player
    ) {
        return ram != null
                && player != null
                && !ram.isDead
                && ram.worldObj == player.worldObj
                && player.getUniqueID().equals(
                ram.getCommanderUuid()
        )
                && ram.getRamState()
                == BattleRamState.FOLLOW_COMMANDER
                && !ram.hasGateTarget()
                && ram.getTargetQueueSize() == 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCarrierInteract(EntityInteractEvent event) {
        if (event == null
                || event.entityPlayer == null
                || event.entityPlayer.worldObj == null
                || event.entityPlayer.worldObj.isRemote
                || !(event.target instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC crew = (LOTREntityNPC)event.target;
        if (!EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }

        /*
         * Attached ram carriers are not independent LOTR interaction NPCs.
         * Cancel the normal NPC interaction path so they cannot offer
         * miniquests, trades, speech GUIs, or other unrelated interaction.
         */
        event.setCanceled(true);

        if (!(event.entityPlayer instanceof EntityPlayerMP)) {
            return;
        }

        EntityBattleRam ram = getLoadedRam(
                crew.worldObj,
                EntityBattleRam.getTaggedRamUuid(crew)
        );
        if (ram == null) {
            ram = attachToLoadedRamIfValid(crew);
        }
        if (ram != null) {
            /*
             * Forward both interaction modes to the parent machine:
             * normal right-click pauses/resumes; shift-right-click opens the
             * same control GUI as interacting with the ram itself.
             */
            ram.interact(event.entityPlayer);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCarrierSuffocationAttack(LivingAttackEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.source != DamageSource.inWall
                || !(event.entityLiving instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC crew = (LOTREntityNPC)event.entityLiving;
        if (!EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }

        /*
         * Ram carriers are rigidly positioned formation components. Their
         * bodies can overlap nearby architecture even when the ram itself has
         * a valid path, so vanilla in-wall suffocation is not meaningful for
         * them. Cancel only that exact damage source; combat, fire, falls,
         * projectiles, etc. remain untouched.
         */
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null
                || event.world == null
                || event.world.isRemote) {
            return;
        }
        if (event.entity instanceof EntityBattleRam) {
            EntityBattleRam ram = (EntityBattleRam)event.entity;
            if (ram.ensureDurableOwnership()) {
                registerRam(ram);
            }
        } else if (event.entity instanceof LOTREntityNPC) {
            reconcileCarrierJoin((LOTREntityNPC)event.entity);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (event.entityLiving instanceof EntityPlayerMP) {
            updatePlayerFastTravelState(
                    (EntityPlayerMP)event.entityLiving
            );
            return;
        }
        if (event.entityLiving instanceof EntityBattleRam) {
            EntityBattleRam ram = (EntityBattleRam)event.entityLiving;
            if (ram.worldObj != null && !ram.worldObj.isRemote) {
                if (ram.ensureDurableOwnership()) {
                    registerRam(ram);
                }
            }
            return;
        }
        if (!(event.entityLiving instanceof LOTREntityNPC)
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote) {
            return;
        }
        LOTREntityNPC crew = (LOTREntityNPC)event.entityLiving;
        if (!EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }
        /* Absence from the weak loaded-ram cache is not ownership evidence. */
        reconcileCarrierJoin(crew);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event == null
                || event.phase != TickEvent.Phase.END
                || event.world == null
                || event.world.isRemote) {
            return;
        }
        Map<UUID, WeakReference<EntityBattleRam>> worldRams =
                loadedRams.get(event.world);
        if (worldRams != null) {
            Iterator<Map.Entry<UUID, WeakReference<EntityBattleRam>>> iterator =
                    worldRams.entrySet().iterator();
            while (iterator.hasNext()) {
                EntityBattleRam ram = iterator.next().getValue().get();
                if (ram == null
                        || ram.worldObj != event.world
                        || ram.isDead
                        || !ram.addedToChunk) {
                    iterator.remove();
                    continue;
                }
                ram.reconcileAttachedCrewFormation();
            }
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                event.world, false
        );
        if (data != null) {
            for (SiegeRamCrewOwnershipData.RamSlotKey key
                    : data.pollDeferred(
                    SiegeRamCrewOwnershipData
                            .MAX_RECONCILIATION_KEYS_PER_TICK)) {
                EntityBattleRam ram = getLoadedRam(event.world, key.ramUuid);
                if (ram != null) {
                    ram.reconcilePreparedSpawnSlot(key.slot);
                }
            }
        }
        if (worldRams != null && worldRams.isEmpty()) {
            loadedRams.remove(event.world);
        }
    }

    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP)
                || event.target == null
                || event.target.worldObj == null
                || event.target.worldObj.isRemote) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP)event.entityPlayer;
        if (event.target instanceof EntityBattleRam) {
            ((EntityBattleRam)event.target).syncAttachedCrewTo(player);
        } else if (event.target instanceof LOTREntityNPC) {
            LOTREntityNPC crew = (LOTREntityNPC)event.target;
            EntityBattleRam ram = attachToLoadedRamIfValid(crew);
            if (ram != null) {
                int slot = ram.getValidAttachedCrewSlot(crew);
                if (slot >= 0) {
                    SiegeNetwork.syncRamCrewAttachmentTo(
                            player,
                            ram,
                            crew,
                            slot,
                            true
                    );
                }
            }
        }
    }

    @SubscribeEvent
    public void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.entityPlayer instanceof EntityPlayerMP
                && event.target instanceof LOTREntityNPC) {
            SiegeNetwork.syncRamCrewDetachmentTo(
                    (EntityPlayerMP)event.entityPlayer,
                    (LOTREntityNPC)event.target
            );
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.entityLiving instanceof LOTREntityNPC
                && event.entityLiving.worldObj != null
                && !event.entityLiving.worldObj.isRemote) {
            LOTREntityNPC crew = (LOTREntityNPC)event.entityLiving;
            if (markValidatedGenuineDeath(crew)) {
                SiegeNetwork.syncRamCrewDetachment(crew);
                EntityBattleRam.restoreAttachedCrewWeaponForDeath(crew);
            }
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event != null && event.world != null && !event.world.isRemote) {
            SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                    event.world, false
            );
            if (data != null) {
                data.enqueueChunk(
                        event.getChunk().xPosition, event.getChunk().zPosition
                );
            }
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event != null && event.world != null) {
            Map<UUID, WeakReference<EntityBattleRam>> worldRams =
                    loadedRams.get(event.world);
            if (worldRams != null) {
                for (WeakReference<EntityBattleRam> reference
                        : worldRams.values()) {
                    EntityBattleRam ram = reference.get();
                    if (ram != null) {
                        ram.markWorldUnloading();
                    }
                }
            }
            loadedRams.remove(event.world);

            Iterator<Map.Entry<
                    EntityPlayerMP,
                    PendingFastTravel>> pendingIterator =
                    pendingFastTravels.entrySet().iterator();
            while (pendingIterator.hasNext()) {
                EntityPlayerMP player =
                        pendingIterator.next().getKey();
                if (player == null
                        || player.worldObj == event.world) {
                    pendingIterator.remove();
                }
            }

            if (!event.world.isRemote) {
                clearTerminalGenuineDeaths(event.world);
                SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                        event.world, false
                );
                if (data != null) {
                    data.clearTransientQueue();
                }
                SiegeRamDiagnostics.clearServerDimension(
                        event.world.provider.dimensionId
                );
            }
        }
    }

    private void registerRam(EntityBattleRam ram) {
        if (ram == null || ram.worldObj == null || ram.worldObj.isRemote) {
            return;
        }
        Map<UUID, WeakReference<EntityBattleRam>> worldRams =
                loadedRams.get(ram.worldObj);
        if (worldRams == null) {
            worldRams = new HashMap<UUID, WeakReference<EntityBattleRam>>();
            loadedRams.put(ram.worldObj, worldRams);
        }
        worldRams.put(
                ram.getUniqueID(),
                new WeakReference<EntityBattleRam>(ram)
        );
    }

    private EntityBattleRam attachToLoadedRamIfValid(LOTREntityNPC crew) {
        if (!EntityBattleRam.hasRamCrewTag(crew)
                || crew.worldObj == null
                || crew.worldObj.isRemote) {
            return null;
        }
        EntityBattleRam ram = getLoadedRam(
                crew.worldObj,
                EntityBattleRam.getTaggedRamUuid(crew)
        );
        if (ram != null && ram.attachLoadedCrewIfValid(crew)) {
            return ram;
        }
        return null;
    }

    private EntityBattleRam getLoadedRam(World world, UUID ramUuid) {
        if (world == null || ramUuid == null) {
            return null;
        }
        Map<UUID, WeakReference<EntityBattleRam>> worldRams =
                loadedRams.get(world);
        if (worldRams == null) {
            return null;
        }
        WeakReference<EntityBattleRam> reference = worldRams.get(ramUuid);
        EntityBattleRam ram = reference == null ? null : reference.get();
        if (ram == null
                || ram.worldObj != world
                || ram.isDead) {
            worldRams.remove(ramUuid);
            return null;
        }
        return ram.addedToChunk ? ram : null;
    }

    private void reconcileCarrierJoin(LOTREntityNPC crew) {
        if (crew == null || crew.worldObj == null || crew.worldObj.isRemote
                || !EntityBattleRam.hasRamCrewTag(crew)) {
            return;
        }
        UUID ramUuid = EntityBattleRam.getTaggedRamUuid(crew);
        int slot = EntityBattleRam.getTaggedCrewSlot(crew);
        EntityBattleRam loadedRam = getLoadedRam(crew.worldObj, ramUuid);
        if (!EntityBattleRam.hasDurableRamCrewTag(crew)) {
            if (loadedRam != null) {
                loadedRam.migrateLegacyCarrierIfValid(crew);
            }
            return;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                crew.worldObj, false
        );
        if (data == null) {
            return;
        }
        long generation = EntityBattleRam.getTaggedRamGeneration(crew);
        if (isValidatedTerminalGenuineDeath(
                crew, data, ramUuid, generation, slot
        )) {
            SiegeRamDiagnostics.serverOnce(
                    crew.worldObj.provider.dimensionId,
                    "death-terminal-ignored:" + ramUuid + ":" + generation
                            + ":" + slot + ":" + crew.getUniqueID(),
                    "DEATH_TERMINAL_IGNORED",
                    carrierFields(crew, ramUuid, slot)
                            + " state=DEAD_RESPAWN_PENDING"
            );
            return;
        }
        SiegeRamCrewOwnershipData.SlotSnapshot before = data.getSlot(
                ramUuid, generation, slot
        );
        SiegeRamCrewOwnershipData.CarrierResolution resolution =
                data.classifyCarrier(
                        crew.worldObj, ramUuid,
                        generation, slot,
                        crew.getUniqueID(), crew.getFaction(), crew.getClass(),
                        crew.ridingEntity != null
                );
        if (resolution == SiegeRamCrewOwnershipData.CarrierResolution.CURRENT) {
            if (loadedRam != null) {
                boolean attached = loadedRam.attachLoadedCrewIfValid(crew);
                String event = before != null && before.state
                        == SiegeRamCrewOwnershipData.SlotState.SPAWN_PREPARED
                        ? "JOIN_PROMOTE_CURRENT" : "JOIN_CURRENT_ATTACH";
                SiegeRamDiagnostics.serverOnce(
                        crew.worldObj.provider.dimensionId,
                        "join-current:" + event + ":" + ramUuid + ":"
                                + EntityBattleRam.getTaggedRamGeneration(crew)
                                + ":" + slot + ":" + crew.getUniqueID(),
                        event,
                        carrierFields(crew, ramUuid, slot)
                                + " previousState="
                                + (before == null ? "null" : before.state)
                                + " attached=" + attached
                                + " resulting=ALIVE_EXPECTED"
                );
            }
        } else if (resolution
                == SiegeRamCrewOwnershipData.CarrierResolution.STALE
                || resolution
                == SiegeRamCrewOwnershipData.CarrierResolution.TOMBSTONED) {
            String event = resolution
                    == SiegeRamCrewOwnershipData.CarrierResolution.STALE
                    ? "JOIN_STALE_RETIRE" : "JOIN_TOMBSTONE_RETIRE";
            SiegeRamDiagnostics.serverOnce(
                    crew.worldObj.provider.dimensionId,
                    "join-retire:" + event + ":" + ramUuid + ":"
                            + EntityBattleRam.getTaggedRamGeneration(crew)
                            + ":" + slot + ":" + crew.getUniqueID(),
                    event,
                    carrierFields(crew, ramUuid, slot)
                            + " previousState="
                            + (before == null ? "null" : before.state)
            );
            EntityBattleRam.retireCrewWithoutDeath(crew);
        } else if (resolution
                == SiegeRamCrewOwnershipData.CarrierResolution.CORRUPT) {
            data.quarantineCarrier(
                    crew.worldObj, ramUuid,
                    EntityBattleRam.getTaggedRamGeneration(crew), slot,
                    "CORRUPT_CARRIER_ASSOCIATION"
            );
            logJoinQuarantine(crew, data, ramUuid, slot,
                    "CORRUPT_CARRIER_ASSOCIATION");
        } else if (resolution
                == SiegeRamCrewOwnershipData.CarrierResolution.QUARANTINED) {
            logJoinQuarantine(crew, data, ramUuid, slot,
                    "DURABLE_SLOT_QUARANTINED");
        }
    }

    private boolean markValidatedGenuineDeath(LOTREntityNPC crew) {
        if (crew == null || crew.worldObj == null) {
            return false;
        }
        /* Do not emit for every ordinary LOTR NPC. A malformed carrier marker
         * is still a diagnostic candidate even if its UUID cannot be parsed. */
        if (!EntityBattleRam.hasRamCrewTag(crew)
                && !crew.getEntityData().hasKey("SiegeRamUUID")
                && !crew.getEntityData().getBoolean("SiegeRamCarrier")) {
            return false;
        }
        if (!EntityBattleRam.hasDurableRamCrewTag(crew)) {
            UUID badRam = EntityBattleRam.getTaggedRamUuid(crew);
            int badSlot = EntityBattleRam.getTaggedCrewSlot(crew);
            String reason = badRam == null ? "BAD_RAM_UUID"
                    : !crew.getEntityData().getBoolean("SiegeRamCarrier")
                    ? "NO_CARRIER_MARKER"
                    : EntityBattleRam.getTaggedRamGeneration(crew) <= 0L
                    ? "BAD_GENERATION" : "BAD_SLOT";
            logDeathReject(crew, badRam, badSlot, reason, null);
            return false;
        }
        UUID ramUuid = EntityBattleRam.getTaggedRamUuid(crew);
        int slot = EntityBattleRam.getTaggedCrewSlot(crew);
        long generation = EntityBattleRam.getTaggedRamGeneration(crew);
        if (ramUuid == null) {
            logDeathReject(crew, null, slot, "BAD_RAM_UUID", null);
            return false;
        }
        if (slot < 0 || slot >= EntityBattleRam.CREW_SLOT_COUNT) {
            logDeathReject(crew, ramUuid, slot, "BAD_SLOT", null);
            return false;
        }
        SiegeRamCrewOwnershipData data = SiegeRamCrewOwnershipData.get(
                crew.worldObj, false
        );
        if (data == null) {
            logDeathReject(crew, ramUuid, slot, "NO_OWNERSHIP_DATA", null);
            return false;
        }
        SiegeRamCrewOwnershipData.RamSnapshot record = data.getRam(ramUuid);
        if (record == null) {
            logDeathReject(crew, ramUuid, slot, "RAM_RECORD_MISSING", null);
            return false;
        }
        if (record.dimension != crew.worldObj.provider.dimensionId) {
            logDeathReject(crew, ramUuid, slot, "DIMENSION_MISMATCH", record);
            return false;
        }
        if (record.generation != generation) {
            logDeathReject(crew, ramUuid, slot, "GENERATION_MISMATCH", record);
            return false;
        }
        if (record.status != SiegeRamCrewOwnershipData.RamStatus.ACTIVE) {
            logDeathReject(crew, ramUuid, slot, "RAM_NOT_ACTIVE", record);
            return false;
        }
        if (crew.ridingEntity != null) {
            logDeathReject(crew, ramUuid, slot, "MOUNTED", record);
            return false;
        }
        if (!BattleRamCrewTypes.isApprovedGroundCrew(
                crew.getFaction(), crew)) {
            logDeathReject(crew, ramUuid, slot, "CLASS_MISMATCH", record);
            return false;
        }
        String predictedClassifyReject = null;
        if (lotr.common.fac.LOTRFaction.forName(record.factionCode)
                != crew.getFaction()) {
            predictedClassifyReject = "FACTION_MISMATCH";
        } else if (crew.getClass() != BattleRamCrewTypes.getCrewClass(
                lotr.common.fac.LOTRFaction.forName(record.factionCode))) {
            predictedClassifyReject = "CLASS_MISMATCH";
        }
        SiegeRamCrewOwnershipData.SlotSnapshot before = data.getSlot(
                ramUuid, generation, slot
        );
        if (before == null || before.state
                != SiegeRamCrewOwnershipData.SlotState.ALIVE_EXPECTED) {
            predictedClassifyReject = "SLOT_NOT_ALIVE_EXPECTED";
        } else if (!crew.getUniqueID().equals(before.expectedCrewUuid)) {
            predictedClassifyReject = "EXPECTED_UUID_MISMATCH";
        }
        SiegeRamCrewOwnershipData.CarrierResolution resolution =
                data.classifyCarrier(
                        crew.worldObj, ramUuid, generation, slot,
                        crew.getUniqueID(), crew.getFaction(), crew.getClass(),
                        false
                );
        if (resolution != SiegeRamCrewOwnershipData.CarrierResolution.CURRENT) {
            logDeathReject(crew, ramUuid, slot,
                    predictedClassifyReject == null
                            ? "CLASSIFY_NOT_CURRENT" : predictedClassifyReject,
                    record);
            return false;
        }
        if (!data.markGenuineDeath(
                crew.worldObj, ramUuid, generation, slot, crew.getUniqueID()
        )) {
            logDeathReject(crew, ramUuid, slot,
                    "MARK_GENUINE_DEATH_FAILED", record);
            return false;
        }
        SiegeRamCrewOwnershipData.SlotSnapshot after = data.getSlot(
                ramUuid, generation, slot
        );
        terminalGenuineDeaths.put(
                crew,
                new TerminalGenuineDeath(
                        ramUuid,
                        generation,
                        slot,
                        crew.getUniqueID(),
                        crew.worldObj.provider.dimensionId
                )
        );
        SiegeRamDiagnostics.server(
                "DEATH_ACCEPT",
                carrierFields(crew, ramUuid, slot)
                        + " status=" + record.status
                        + " previousState=" + before.state
                        + " now=" + crew.worldObj.getTotalWorldTime()
                        + " respawnAt="
                        + (after == null ? -1L : after.respawnAt)
        );
        return true;
    }

    private boolean isValidatedTerminalGenuineDeath(
            LOTREntityNPC crew, SiegeRamCrewOwnershipData data,
            UUID ramUuid, long generation, int slot
    ) {
        TerminalGenuineDeath terminal = terminalGenuineDeaths.get(crew);
        if (terminal == null) {
            return false;
        }
        if (!terminal.matches(
                crew, ramUuid, generation, slot,
                crew.worldObj.provider.dimensionId
        ) || (!crew.isDead && crew.getHealth() > 0.0F)) {
            terminalGenuineDeaths.remove(crew);
            return false;
        }
        SiegeRamCrewOwnershipData.RamSnapshot ram = data.getRam(ramUuid);
        SiegeRamCrewOwnershipData.SlotSnapshot value = data.getSlot(
                ramUuid, generation, slot
        );
        if (ram != null
                && ram.status == SiegeRamCrewOwnershipData.RamStatus.ACTIVE
                && ram.generation == generation
                && ram.dimension == crew.worldObj.provider.dimensionId
                && value != null
                && value.state
                == SiegeRamCrewOwnershipData.SlotState.DEAD_RESPAWN_PENDING
                && value.expectedCrewUuid == null) {
            return true;
        }
        terminalGenuineDeaths.remove(crew);
        return false;
    }

    private void clearTerminalGenuineDeaths(World world) {
        Iterator<Map.Entry<LOTREntityNPC, TerminalGenuineDeath>> iterator =
                terminalGenuineDeaths.entrySet().iterator();
        while (iterator.hasNext()) {
            LOTREntityNPC crew = iterator.next().getKey();
            if (crew == null || crew.worldObj == world) {
                iterator.remove();
            }
        }
    }

    private void logDeathReject(
            LOTREntityNPC crew, UUID ramUuid, int slot, String reason,
            SiegeRamCrewOwnershipData.RamSnapshot record
    ) {
        SiegeRamDiagnostics.serverOnce(
                crew.worldObj.provider.dimensionId,
                "death-reject:" + crew.getUniqueID() + ":" + reason,
                "DEATH_REJECT",
                carrierFields(crew, ramUuid, slot) + " reason=" + reason
                        + " status="
                        + (record == null ? "unknown" : record.status)
        );
    }

    private void logJoinQuarantine(
            LOTREntityNPC crew, SiegeRamCrewOwnershipData data, UUID ramUuid,
            int slot, String fallbackReason
    ) {
        SiegeRamCrewOwnershipData.SlotSnapshot value = data.getSlot(
                ramUuid, EntityBattleRam.getTaggedRamGeneration(crew), slot
        );
        String reason = value == null || value.reason == null
                || value.reason.isEmpty() ? fallbackReason : value.reason;
        SiegeRamDiagnostics.serverOnce(
                crew.worldObj.provider.dimensionId,
                "join-quarantine:" + ramUuid + ":"
                        + EntityBattleRam.getTaggedRamGeneration(crew) + ":"
                        + slot + ":" + crew.getUniqueID() + ":" + reason,
                "JOIN_QUARANTINE",
                carrierFields(crew, ramUuid, slot) + " reason=" + reason
        );
    }

    private static String carrierFields(
            LOTREntityNPC crew, UUID ramUuid, int slot
    ) {
        return "ram=" + ramUuid + " gen="
                + EntityBattleRam.getTaggedRamGeneration(crew) + " slot="
                + slot + " crew=" + crew.getUniqueID();
    }

    private static final class PendingFastTravel {
        private final int dimension;
        private final double originX;
        private final double originZ;
        private final int targetX;
        private final int targetZ;
        private final Map<
                UUID,
                WeakReference<EntityBattleRam>> rams =
                new LinkedHashMap<
                        UUID,
                        WeakReference<EntityBattleRam>>();

        private PendingFastTravel(
                int dimension,
                double originX,
                double originZ,
                int targetX,
                int targetZ
        ) {
            this.dimension = dimension;
            this.originX = originX;
            this.originZ = originZ;
            this.targetX = targetX;
            this.targetZ = targetZ;
        }

        private boolean matchesTarget(
                int candidateDimension,
                int candidateTargetX,
                int candidateTargetZ
        ) {
            return dimension == candidateDimension
                    && targetX == candidateTargetX
                    && targetZ == candidateTargetZ;
        }

        private boolean matchesCompletedTravel(
                EntityPlayerMP player
        ) {
            if (player == null
                    || player.worldObj == null
                    || player.worldObj.provider.dimensionId
                    != dimension) {
                return false;
            }

            double destinationX =
                    targetX + 0.5D;
            double destinationZ =
                    targetZ + 0.5D;
            double destinationDeltaX =
                    player.posX - destinationX;
            double destinationDeltaZ =
                    player.posZ - destinationZ;

            if (destinationDeltaX * destinationDeltaX
                    + destinationDeltaZ * destinationDeltaZ
                    > FAST_TRAVEL_DESTINATION_TOLERANCE
                    * FAST_TRAVEL_DESTINATION_TOLERANCE) {
                return false;
            }

            double travelDeltaX =
                    player.posX - originX;
            double travelDeltaZ =
                    player.posZ - originZ;

            return travelDeltaX * travelDeltaX
                    + travelDeltaZ * travelDeltaZ
                    >= FAST_TRAVEL_MIN_DISPLACEMENT
                    * FAST_TRAVEL_MIN_DISPLACEMENT;
        }
    }

    private static final class TerminalGenuineDeath {
        private final UUID ramUuid;
        private final long generation;
        private final int slot;
        private final UUID crewUuid;
        private final int dimension;

        private TerminalGenuineDeath(
                UUID ramUuid, long generation, int slot, UUID crewUuid,
                int dimension
        ) {
            this.ramUuid = ramUuid;
            this.generation = generation;
            this.slot = slot;
            this.crewUuid = crewUuid;
            this.dimension = dimension;
        }

        private boolean matches(
                LOTREntityNPC crew, UUID candidateRamUuid,
                long candidateGeneration, int candidateSlot,
                int candidateDimension
        ) {
            return crew != null
                    && ramUuid.equals(candidateRamUuid)
                    && generation == candidateGeneration
                    && slot == candidateSlot
                    && crewUuid.equals(crew.getUniqueID())
                    && dimension == candidateDimension;
        }
    }
}
