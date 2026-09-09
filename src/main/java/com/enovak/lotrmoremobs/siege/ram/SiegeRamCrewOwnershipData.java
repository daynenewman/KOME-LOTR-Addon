package com.enovak.lotrmoremobs.siege.ram;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Per-dimension durable authority for Battle Ram carrier identity. Persistent
 * records intentionally contain scalar values and UUIDs only: runtime World,
 * Entity, Chunk and player references belong in event-handler caches, never
 * here. Tombstones are deliberately never evicted automatically.
 */
public final class SiegeRamCrewOwnershipData extends WorldSavedData {

    public static final String DATA_NAME =
            "lotrmoremobs_siege_ram_crew_ownership";
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_RAM_RECORDS = 4096;
    public static final int MAX_PERSISTENT_SLOTS =
            MAX_RAM_RECORDS * EntityBattleRam.CREW_SLOT_COUNT;
    public static final int MAX_DEFERRED_RECONCILIATION_KEYS = 4096;
    public static final int MAX_RECONCILIATION_KEYS_PER_TICK = 32;
    public static final int MAX_REASON_LENGTH = 160;

    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;

    private static final String NBT_FORMAT = "FormatVersion";
    private static final String NBT_NEXT_GENERATION = "NextRamGeneration";
    private static final String NBT_RAMS = "Rams";
    private static final String NBT_RAM_UUID = "RamUUID";
    private static final String NBT_RAM_GENERATION = "RamGeneration";
    private static final String NBT_DIMENSION = "Dimension";
    private static final String NBT_STATUS = "Status";
    private static final String NBT_FACTION = "Faction";
    private static final String NBT_RAM_CHUNK_X = "LastRamChunkX";
    private static final String NBT_RAM_CHUNK_Z = "LastRamChunkZ";
    private static final String NBT_SLOTS = "Slots";
    private static final String NBT_SLOT = "Slot";
    private static final String NBT_SLOT_STATE = "State";
    private static final String NBT_EXPECTED_UUID = "ExpectedCrewUUID";
    private static final String NBT_RESPAWN_AT = "RespawnAt";
    private static final String NBT_PREPARED_CHUNK_X = "PreparedChunkX";
    private static final String NBT_PREPARED_CHUNK_Z = "PreparedChunkZ";
    private static final String NBT_LAST_CREW_CHUNK_X = "LastCrewChunkX";
    private static final String NBT_LAST_CREW_CHUNK_Z = "LastCrewChunkZ";
    private static final String NBT_HAS_LAST_CREW_CHUNK = "HasLastCrewChunk";
    private static final String NBT_REASON = "Reason";

    private final Map<UUID, RamRecord> ramsByUuid =
            new HashMap<UUID, RamRecord>();
    private final Map<UUID, RamSlotKey> expectedCrewIndex =
            new HashMap<UUID, RamSlotKey>();
    private final Map<Long, Set<RamSlotKey>> crewChunkIndex =
            new HashMap<Long, Set<RamSlotKey>>();
    private final LinkedHashSet<RamSlotKey> deferredKeys =
            new LinkedHashSet<RamSlotKey>();

    private long nextRamGeneration = 1L;
    private boolean readOnlyDueToInvalidData;
    private boolean capacityWarningLogged;
    private int boundDimension = Integer.MIN_VALUE;

    public SiegeRamCrewOwnershipData(String name) {
        super(name);
    }

    public static SiegeRamCrewOwnershipData get(World world, boolean create) {
        if (world == null || world.isRemote || world.perWorldStorage == null) {
            return null;
        }
        SiegeRamCrewOwnershipData data =
                (SiegeRamCrewOwnershipData)world.perWorldStorage.loadData(
                        SiegeRamCrewOwnershipData.class,
                        DATA_NAME
                );
        if (data == null && create) {
            data = new SiegeRamCrewOwnershipData(DATA_NAME);
            world.perWorldStorage.setData(DATA_NAME, data);
            data.markDirty();
        }
        if (data != null) {
            data.bindToDimension(world.provider.dimensionId);
        }
        return data;
    }

    private synchronized void bindToDimension(int dimension) {
        if (boundDimension == dimension) {
            return;
        }
        if (boundDimension != Integer.MIN_VALUE) {
            rejectLoadedData("one ownership instance was reused across dimensions");
            return;
        }
        for (RamRecord record : ramsByUuid.values()) {
            if (record.dimension != dimension) {
                rejectLoadedData("ram record dimension does not match storage");
                return;
            }
        }
        boundDimension = dimension;
    }

    public synchronized boolean isReadOnlyDueToInvalidData() {
        return readOnlyDueToInvalidData;
    }

    public synchronized RamSnapshot registerNewRam(
            World world,
            UUID ramUuid,
            LOTRFaction faction
    ) {
        if (!canWrite(world, ramUuid, faction)) {
            return null;
        }
        RamRecord existing = ramsByUuid.get(ramUuid);
        if (existing != null) {
            return existing.status == RamStatus.ACTIVE
                    ? snapshot(existing)
                    : null;
        }
        if (ramsByUuid.size() >= MAX_RAM_RECORDS) {
            logCapacityWarning(world.provider.dimensionId);
            return null;
        }
        long generation = allocateGeneration();
        RamRecord created = new RamRecord(
                ramUuid,
                generation,
                world.provider.dimensionId,
                faction.codeName(),
                RamStatus.ACTIVE,
                0,
                0
        );
        /* Coordinates are updated by touchRam immediately after creation. No
         * chunk lookup is permitted while registering durable ownership. */
        for (int slot = 0; slot < EntityBattleRam.CREW_SLOT_COUNT; ++slot) {
            created.slots[slot] = new SlotRecord(
                    slot,
                    SlotState.DEAD_RESPAWN_PENDING,
                    null,
                    world.getTotalWorldTime(),
                    0,
                    0
            );
        }
        ramsByUuid.put(ramUuid, created);
        markDirty();
        return snapshot(created);
    }

    /** Creates a conservative v1 record from the current ram entity NBT. */
    public synchronized RamSnapshot migrateLegacyRam(
            World world,
            UUID ramUuid,
            LOTRFaction faction,
            LegacySlot[] legacySlots,
            int ramChunkX,
            int ramChunkZ
    ) {
        if (!canWrite(world, ramUuid, faction)) {
            return null;
        }
        RamRecord existing = ramsByUuid.get(ramUuid);
        if (existing != null) {
            return snapshot(existing);
        }
        if (legacySlots == null
                || legacySlots.length != EntityBattleRam.CREW_SLOT_COUNT
                || ramsByUuid.size() >= MAX_RAM_RECORDS) {
            logCapacityWarning(world.provider.dimensionId);
            return null;
        }
        RamRecord record = new RamRecord(
                ramUuid,
                allocateGeneration(),
                world.provider.dimensionId,
                faction.codeName(),
                RamStatus.ACTIVE,
                ramChunkX,
                ramChunkZ
        );
        long now = world.getTotalWorldTime();
        for (int slot = 0; slot < EntityBattleRam.CREW_SLOT_COUNT; ++slot) {
            LegacySlot legacy = legacySlots[slot];
            SlotRecord imported;
            if (legacy != null && legacy.alive && legacy.crewUuid != null) {
                imported = new SlotRecord(
                        slot, SlotState.ALIVE_EXPECTED, legacy.crewUuid,
                        0L, 0, 0
                );
            } else if (legacy != null && !legacy.alive
                    && legacy.crewUuid == null && legacy.respawnAt > now) {
                imported = new SlotRecord(
                        slot, SlotState.DEAD_RESPAWN_PENDING, null,
                        legacy.respawnAt, 0, 0
                );
            } else {
                imported = new SlotRecord(
                        slot, SlotState.QUARANTINED, null, 0L, 0, 0
                );
                imported.reason = "AMBIGUOUS_LEGACY_SLOT";
            }
            record.slots[slot] = imported;
        }
        ramsByUuid.put(ramUuid, record);
        rebuildIndexesFor(record);
        markDirty();
        warnOnce(record, "Migrated legacy Battle Ram conservatively; ambiguous slots quarantined.");
        return snapshot(record);
    }

    public synchronized RamSnapshot getRam(UUID ramUuid) {
        RamRecord record = ramUuid == null ? null : ramsByUuid.get(ramUuid);
        return record == null ? null : snapshot(record);
    }

    public synchronized void touchRam(
            World world,
            UUID ramUuid,
            long generation,
            int chunkX,
            int chunkZ
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record != null
                && (record.lastRamChunkX != chunkX || record.lastRamChunkZ != chunkZ)) {
            record.lastRamChunkX = chunkX;
            record.lastRamChunkZ = chunkZ;
            markDirty();
        }
    }

    public synchronized boolean isCurrentExpected(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID crewUuid
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            return false;
        }
        SlotRecord value = record.slots[slot];
        return value.state == SlotState.ALIVE_EXPECTED
                && crewUuid != null
                && crewUuid.equals(value.expectedCrewUuid);
    }

    public synchronized CarrierResolution classifyCarrier(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID crewUuid,
            LOTRFaction actualFaction,
            Class<?> actualClass,
            boolean mounted
    ) {
        if (world == null || ramUuid == null || crewUuid == null || !isSlot(slot)) {
            return CarrierResolution.CORRUPT;
        }
        RamRecord record = ramsByUuid.get(ramUuid);
        if (record == null || record.dimension != world.provider.dimensionId) {
            return CarrierResolution.UNKNOWN_PARENT;
        }
        if (record.generation != generation) {
            return CarrierResolution.CORRUPT;
        }
        LOTRFaction expectedFaction = LOTRFaction.forName(record.factionCode);
        Class<?> expectedClass = BattleRamCrewTypes.getCrewClass(expectedFaction);
        if (expectedFaction == null || expectedClass == null
                || actualFaction != expectedFaction
                || actualClass != expectedClass || mounted) {
            quarantineSlot(record, slot, "INVALID_CARRIER_TYPE_OR_MOUNT");
            return CarrierResolution.QUARANTINED;
        }
        if (record.status == RamStatus.DISBANDED_TOMBSTONE
                || record.status == RamStatus.ABNORMALLY_REMOVED_TOMBSTONE) {
            return CarrierResolution.TOMBSTONED;
        }
        if (record.status != RamStatus.ACTIVE) {
            return CarrierResolution.QUARANTINED;
        }
        SlotRecord value = record.slots[slot];
        if (value.state == SlotState.ALIVE_EXPECTED
                && crewUuid.equals(value.expectedCrewUuid)) {
            return CarrierResolution.CURRENT;
        }
        if (value.state == SlotState.SPAWN_PREPARED
                && crewUuid.equals(value.expectedCrewUuid)) {
            value.state = SlotState.ALIVE_EXPECTED;
            markDirty();
            return CarrierResolution.CURRENT;
        }
        if ((value.state == SlotState.ALIVE_EXPECTED
                || value.state == SlotState.SPAWN_PREPARED)
                && value.expectedCrewUuid != null) {
            return CarrierResolution.STALE;
        }
        return value.state == SlotState.QUARANTINED
                ? CarrierResolution.QUARANTINED
                : CarrierResolution.CORRUPT;
    }

    public synchronized boolean markGenuineDeath(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID crewUuid
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            if (world != null) {
                SiegeRamDiagnostics.serverOnce(
                        world.provider.dimensionId,
                        "pending-reject:" + ramUuid + ":" + generation
                                + ":" + slot + ":NO_ACTIVE_RECORD_OR_SLOT",
                        "PENDING_REJECT",
                        fields(ramUuid, generation, slot, crewUuid)
                                + " reason=NO_ACTIVE_RECORD_OR_SLOT"
                );
            }
            return false;
        }
        SlotRecord value = record.slots[slot];
        if (value.state != SlotState.ALIVE_EXPECTED
                || !crewUuid.equals(value.expectedCrewUuid)) {
            SiegeRamDiagnostics.serverOnce(
                    world.provider.dimensionId,
                    "pending-reject:" + ramUuid + ":" + generation
                            + ":" + slot + ":" + value.state,
                    "PENDING_REJECT",
                    fields(ramUuid, generation, slot, crewUuid)
                            + " reason=" + (value.state
                            != SlotState.ALIVE_EXPECTED
                            ? "SLOT_NOT_ALIVE_EXPECTED"
                            : "EXPECTED_UUID_MISMATCH")
                            + " state=" + value.state
                            + " expected=" + value.expectedCrewUuid
            );
            return false;
        }
        UUID oldExpected = value.expectedCrewUuid;
        removeExpectedIndex(record, value);
        value.state = SlotState.DEAD_RESPAWN_PENDING;
        value.expectedCrewUuid = null;
        value.respawnAt = world.getTotalWorldTime()
                + MumakilConfig.getRamCarrierRespawnDelayTicks();
        value.hasLastCrewChunk = false;
        value.reason = "";
        markDirty();
        SiegeRamDiagnostics.server(
                "PENDING_SET",
                fields(ramUuid, generation, slot, crewUuid)
                        + " oldExpected=" + oldExpected
                        + " now=" + world.getTotalWorldTime()
                        + " respawnAt=" + value.respawnAt
        );
        return true;
    }

    public synchronized SlotSnapshot getSlot(
            UUID ramUuid,
            long generation,
            int slot
    ) {
        RamRecord record = ramsByUuid.get(ramUuid);
        if (record == null || record.generation != generation || !isSlot(slot)) {
            return null;
        }
        return snapshot(record.slots[slot]);
    }

    public synchronized boolean prepareSpawn(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID candidateUuid,
            int spawnChunkX,
            int spawnChunkZ
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot) || candidateUuid == null) {
            if (world != null) {
                SiegeRamDiagnostics.serverOnce(
                        world.provider.dimensionId,
                        "prepare-reject:" + ramUuid + ":" + generation
                                + ":" + slot + ":BAD_ACTIVE_OR_INPUT",
                        "SPAWN_PREPARE_REJECT",
                        fields(ramUuid, generation, slot, candidateUuid)
                                + " reason=BAD_ACTIVE_RECORD_SLOT_OR_CANDIDATE"
                );
            }
            return false;
        }
        SlotRecord value = record.slots[slot];
        if (value.state != SlotState.DEAD_RESPAWN_PENDING
                || world.getTotalWorldTime() < value.respawnAt) {
            String reason = value.state != SlotState.DEAD_RESPAWN_PENDING
                    ? "SLOT_NOT_PENDING" : "RESPAWN_NOT_DUE";
            SiegeRamDiagnostics.serverOnce(
                    world.provider.dimensionId,
                    "prepare-reject:" + ramUuid + ":" + generation
                            + ":" + slot + ":" + value.state + ":"
                            + value.respawnAt,
                    "SPAWN_PREPARE_REJECT",
                    fields(ramUuid, generation, slot, candidateUuid)
                            + " reason=" + reason + " state=" + value.state
                            + " now=" + world.getTotalWorldTime()
                            + " respawnAt=" + value.respawnAt
            );
            return false;
        }
        value.state = SlotState.SPAWN_PREPARED;
        value.expectedCrewUuid = candidateUuid;
        value.respawnAt = 0L;
        value.preparedChunkX = spawnChunkX;
        value.preparedChunkZ = spawnChunkZ;
        value.hasLastCrewChunk = true;
        value.lastCrewChunkX = spawnChunkX;
        value.lastCrewChunkZ = spawnChunkZ;
        addExpectedIndex(record, value);
        markDirty();
        SiegeRamDiagnostics.server(
                "SPAWN_PREPARED",
                fields(ramUuid, generation, slot, candidateUuid)
                        + " chunk=" + spawnChunkX + "," + spawnChunkZ
                        + " now=" + world.getTotalWorldTime()
        );
        return true;
    }

    public synchronized boolean markSpawned(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID candidateUuid
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            return false;
        }
        SlotRecord value = record.slots[slot];
        if (value.expectedCrewUuid == null
                || !value.expectedCrewUuid.equals(candidateUuid)
                || (value.state != SlotState.SPAWN_PREPARED
                && value.state != SlotState.ALIVE_EXPECTED)) {
            return false;
        }
        if (value.state != SlotState.ALIVE_EXPECTED) {
            value.state = SlotState.ALIVE_EXPECTED;
            markDirty();
        }
        return true;
    }

    public synchronized boolean markSpawnFailed(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID candidateUuid
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            return false;
        }
        SlotRecord value = record.slots[slot];
        if (value.state != SlotState.SPAWN_PREPARED
                || !candidateUuid.equals(value.expectedCrewUuid)) {
            return false;
        }
        removeExpectedIndex(record, value);
        value.state = SlotState.DEAD_RESPAWN_PENDING;
        value.expectedCrewUuid = null;
        value.respawnAt = world.getTotalWorldTime() + 20L;
        value.hasLastCrewChunk = false;
        markDirty();
        SiegeRamDiagnostics.server(
                "SPAWN_FAILED_RETRY",
                fields(ramUuid, generation, slot, candidateUuid)
                        + " retryAt=" + value.respawnAt
        );
        return true;
    }

    private static String fields(
            UUID ramUuid, long generation, int slot, UUID crewUuid
    ) {
        return "ram=" + ramUuid + " gen=" + generation + " slot=" + slot
                + " crew=" + crewUuid;
    }

    /** A prepared candidate may only be retried after its own spawn chunk is loaded. */
    public synchronized boolean canRetryPrepared(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID candidateUuid
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            return false;
        }
        SlotRecord value = record.slots[slot];
        return value.state == SlotState.SPAWN_PREPARED
                && candidateUuid != null
                && candidateUuid.equals(value.expectedCrewUuid)
                && world.getChunkProvider().chunkExists(
                        value.preparedChunkX, value.preparedChunkZ
                );
    }

    public synchronized boolean retireRam(
            World world,
            UUID ramUuid,
            long generation,
            RamStatus tombstoneStatus
    ) {
        if (tombstoneStatus != RamStatus.DISBANDED_TOMBSTONE
                && tombstoneStatus != RamStatus.ABNORMALLY_REMOVED_TOMBSTONE) {
            return false;
        }
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null) {
            return false;
        }
        for (SlotRecord value : record.slots) {
            removeExpectedIndex(record, value);
            value.state = SlotState.RETIRED;
            value.expectedCrewUuid = null;
            value.respawnAt = 0L;
            value.hasLastCrewChunk = false;
        }
        record.status = tombstoneStatus;
        markDirty();
        return true;
    }

    public synchronized void noteCarrierChunk(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            UUID crewUuid,
            int chunkX,
            int chunkZ
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null || !isSlot(slot)) {
            return;
        }
        SlotRecord value = record.slots[slot];
        if (value.expectedCrewUuid == null
                || !value.expectedCrewUuid.equals(crewUuid)) {
            return;
        }
        if (value.hasLastCrewChunk
                && value.lastCrewChunkX == chunkX
                && value.lastCrewChunkZ == chunkZ) {
            return;
        }
        removeChunkIndex(record, value);
        value.hasLastCrewChunk = true;
        value.lastCrewChunkX = chunkX;
        value.lastCrewChunkZ = chunkZ;
        addChunkIndex(record, value);
        markDirty();
    }

    public synchronized void enqueueChunk(int chunkX, int chunkZ) {
        Set<RamSlotKey> values = crewChunkIndex.get(chunkKey(chunkX, chunkZ));
        if (values == null) {
            return;
        }
        for (RamSlotKey key : values) {
            if (deferredKeys.size() >= MAX_DEFERRED_RECONCILIATION_KEYS) {
                warn("Battle Ram ownership deferred reconciliation queue is full; retaining durable evidence.");
                return;
            }
            deferredKeys.add(key);
        }
    }

    public synchronized void enqueue(RamSlotKey key) {
        if (key == null) {
            return;
        }
        if (deferredKeys.size() >= MAX_DEFERRED_RECONCILIATION_KEYS) {
            warn("Battle Ram ownership deferred reconciliation queue is full; retaining durable evidence.");
            return;
        }
        deferredKeys.add(key);
    }

    public synchronized List<RamSlotKey> pollDeferred(int budget) {
        if (budget <= 0 || deferredKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<RamSlotKey> result = new ArrayList<RamSlotKey>();
        java.util.Iterator<RamSlotKey> iterator = deferredKeys.iterator();
        while (iterator.hasNext() && result.size() < budget) {
            result.add(iterator.next());
            iterator.remove();
        }
        return result;
    }

    public synchronized void clearTransientQueue() {
        deferredKeys.clear();
    }

    public synchronized void quarantineCarrier(
            World world,
            UUID ramUuid,
            long generation,
            int slot,
            String reason
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record != null && isSlot(slot)) {
            quarantineSlot(record, slot, reason);
        }
    }

    public synchronized void quarantineRam(
            World world,
            UUID ramUuid,
            long generation,
            String reason
    ) {
        RamRecord record = getMutableActive(world, ramUuid, generation);
        if (record == null) {
            return;
        }
        record.status = RamStatus.QUARANTINED;
        for (SlotRecord value : record.slots) {
            removeExpectedIndex(record, value);
            value.state = SlotState.QUARANTINED;
            value.expectedCrewUuid = null;
            value.respawnAt = 0L;
            value.reason = boundReason(reason);
        }
        markDirty();
        warnOnce(record, "Quarantined Battle Ram ownership: " + boundReason(reason));
    }

    private void quarantineSlot(RamRecord record, int slot, String reason) {
        SlotRecord value = record.slots[slot];
        removeExpectedIndex(record, value);
        value.state = SlotState.QUARANTINED;
        value.expectedCrewUuid = null;
        value.respawnAt = 0L;
        value.reason = boundReason(reason);
        markDirty();
        warnOnce(record, "Quarantined Battle Ram slot " + slot + ": " + value.reason);
    }

    private boolean canWrite(World world, UUID ramUuid, LOTRFaction faction) {
        return world != null && !world.isRemote && ramUuid != null
                && faction != null && BattleRamCrewTypes.isSupported(faction)
                && !readOnlyDueToInvalidData;
    }

    private RamRecord getMutableActive(
            World world, UUID ramUuid, long generation
    ) {
        if (world == null || world.isRemote || ramUuid == null) {
            return null;
        }
        RamRecord record = ramsByUuid.get(ramUuid);
        return record != null && record.generation == generation
                && record.dimension == world.provider.dimensionId
                && record.status == RamStatus.ACTIVE ? record : null;
    }

    private long allocateGeneration() {
        if (nextRamGeneration <= 0L) {
            rejectLoadedData("ram generation counter overflowed");
            return 0L;
        }
        return nextRamGeneration++;
    }

    private void rebuildIndexesFor(RamRecord record) {
        for (SlotRecord slot : record.slots) {
            addExpectedIndex(record, slot);
            addChunkIndex(record, slot);
        }
    }

    private void addExpectedIndex(RamRecord record, SlotRecord slot) {
        if (slot.expectedCrewUuid != null) {
            RamSlotKey existing = expectedCrewIndex.put(
                    slot.expectedCrewUuid, new RamSlotKey(record.ramUuid, slot.slot)
            );
            if (existing != null) {
                rejectLoadedData("duplicate expected crew UUID");
            }
        }
        addChunkIndex(record, slot);
    }

    private void removeExpectedIndex(RamRecord record, SlotRecord slot) {
        if (slot.expectedCrewUuid != null) {
            expectedCrewIndex.remove(slot.expectedCrewUuid);
        }
        removeChunkIndex(record, slot);
    }

    private void addChunkIndex(RamRecord record, SlotRecord slot) {
        if (!slot.hasLastCrewChunk || slot.expectedCrewUuid == null) {
            return;
        }
        Long key = Long.valueOf(chunkKey(slot.lastCrewChunkX, slot.lastCrewChunkZ));
        Set<RamSlotKey> values = crewChunkIndex.get(key);
        if (values == null) {
            values = new HashSet<RamSlotKey>();
            crewChunkIndex.put(key, values);
        }
        values.add(new RamSlotKey(record.ramUuid, slot.slot));
    }

    private void removeChunkIndex(RamRecord record, SlotRecord slot) {
        if (!slot.hasLastCrewChunk) {
            return;
        }
        Long key = Long.valueOf(chunkKey(slot.lastCrewChunkX, slot.lastCrewChunkZ));
        Set<RamSlotKey> values = crewChunkIndex.get(key);
        if (values != null) {
            values.remove(new RamSlotKey(record.ramUuid, slot.slot));
            if (values.isEmpty()) {
                crewChunkIndex.remove(key);
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return ((long)x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static boolean isSlot(int slot) {
        return slot >= 0 && slot < EntityBattleRam.CREW_SLOT_COUNT;
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound nbt) {
        clearAll();
        if (!nbt.hasKey(NBT_FORMAT, TAG_INT)
                || nbt.getInteger(NBT_FORMAT) != FORMAT_VERSION
                || !nbt.hasKey(NBT_NEXT_GENERATION, TAG_LONG)
                || !nbt.hasKey(NBT_RAMS, TAG_LIST)) {
            rejectLoadedData("unsupported or malformed FormatVersion 1 data");
            return;
        }
        nextRamGeneration = nbt.getLong(NBT_NEXT_GENERATION);
        if (nextRamGeneration <= 0L) {
            rejectLoadedData("invalid ram generation counter");
            return;
        }
        NBTTagList list = (NBTTagList)nbt.getTag(NBT_RAMS);
        if ((list.tagCount() > 0 && list.func_150303_d() != TAG_COMPOUND)
                || list.tagCount() > MAX_RAM_RECORDS) {
            rejectLoadedData("ram record list exceeds defensive cap");
            return;
        }
        long highestGeneration = 0L;
        for (int i = 0; i < list.tagCount(); ++i) {
            RamRecord record = readRam(list.getCompoundTagAt(i));
            if (record == null || ramsByUuid.containsKey(record.ramUuid)) {
                rejectLoadedData("invalid or duplicate ram record");
                return;
            }
            ramsByUuid.put(record.ramUuid, record);
            highestGeneration = Math.max(highestGeneration, record.generation);
            rebuildIndexesFor(record);
            if (readOnlyDueToInvalidData) {
                return;
            }
        }
        if (nextRamGeneration <= highestGeneration) {
            rejectLoadedData("ram generation counter would reuse a generation");
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound nbt) {
        if (readOnlyDueToInvalidData) {
            throw new IllegalStateException("Refusing to overwrite invalid Battle Ram ownership evidence");
        }
        nbt.setInteger(NBT_FORMAT, FORMAT_VERSION);
        nbt.setLong(NBT_NEXT_GENERATION, nextRamGeneration);
        NBTTagList list = new NBTTagList();
        int written = 0;
        for (RamRecord record : ramsByUuid.values()) {
            if (++written > MAX_RAM_RECORDS) {
                throw new IllegalStateException("Battle Ram ownership output exceeds cap");
            }
            list.appendTag(writeRam(record));
        }
        nbt.setTag(NBT_RAMS, list);
    }

    private RamRecord readRam(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_RAM_UUID, TAG_STRING)
                || !nbt.hasKey(NBT_RAM_GENERATION, TAG_LONG)
                || !nbt.hasKey(NBT_DIMENSION, TAG_INT)
                || !nbt.hasKey(NBT_STATUS, TAG_STRING)
                || !nbt.hasKey(NBT_FACTION, TAG_STRING)
                || !nbt.hasKey(NBT_SLOTS, TAG_LIST)) {
            return null;
        }
        UUID uuid = parseUuid(nbt.getString(NBT_RAM_UUID));
        long generation = nbt.getLong(NBT_RAM_GENERATION);
        LOTRFaction faction = LOTRFaction.forName(nbt.getString(NBT_FACTION));
        RamStatus status = parseRamStatus(nbt.getString(NBT_STATUS));
        if (uuid == null || generation <= 0L || faction == null
                || !BattleRamCrewTypes.isSupported(faction) || status == null) {
            return null;
        }
        NBTTagList slots = (NBTTagList)nbt.getTag(NBT_SLOTS);
        if (slots.tagCount() != EntityBattleRam.CREW_SLOT_COUNT
                || slots.func_150303_d() != TAG_COMPOUND) {
            return null;
        }
        RamRecord result = new RamRecord(
                uuid, generation, nbt.getInteger(NBT_DIMENSION), faction.codeName(),
                status, nbt.getInteger(NBT_RAM_CHUNK_X),
                nbt.getInteger(NBT_RAM_CHUNK_Z)
        );
        boolean[] seen = new boolean[EntityBattleRam.CREW_SLOT_COUNT];
        for (int i = 0; i < slots.tagCount(); ++i) {
            SlotRecord slot = readSlot(slots.getCompoundTagAt(i));
            if (slot == null || !isSlot(slot.slot) || seen[slot.slot]) {
                return null;
            }
            seen[slot.slot] = true;
            result.slots[slot.slot] = slot;
        }
        for (boolean value : seen) {
            if (!value) {
                return null;
            }
        }
        return result;
    }

    private SlotRecord readSlot(NBTTagCompound nbt) {
        if (!nbt.hasKey(NBT_SLOT, TAG_INT)
                || !nbt.hasKey(NBT_SLOT_STATE, TAG_STRING)) {
            return null;
        }
        int slot = nbt.getInteger(NBT_SLOT);
        SlotState state = parseSlotState(nbt.getString(NBT_SLOT_STATE));
        UUID expected = nbt.hasKey(NBT_EXPECTED_UUID, TAG_STRING)
                ? parseUuid(nbt.getString(NBT_EXPECTED_UUID)) : null;
        if (!isSlot(slot) || state == null
                || ((state == SlotState.ALIVE_EXPECTED
                || state == SlotState.SPAWN_PREPARED) && expected == null)
                || ((state == SlotState.DEAD_RESPAWN_PENDING
                || state == SlotState.RETIRED || state == SlotState.QUARANTINED)
                && expected != null)) {
            return null;
        }
        SlotRecord result = new SlotRecord(
                slot, state, expected,
                Math.max(0L, nbt.getLong(NBT_RESPAWN_AT)),
                nbt.getInteger(NBT_PREPARED_CHUNK_X),
                nbt.getInteger(NBT_PREPARED_CHUNK_Z)
        );
        result.hasLastCrewChunk = nbt.getBoolean(NBT_HAS_LAST_CREW_CHUNK);
        result.lastCrewChunkX = nbt.getInteger(NBT_LAST_CREW_CHUNK_X);
        result.lastCrewChunkZ = nbt.getInteger(NBT_LAST_CREW_CHUNK_Z);
        result.reason = boundReason(nbt.getString(NBT_REASON));
        return result;
    }

    private NBTTagCompound writeRam(RamRecord record) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString(NBT_RAM_UUID, record.ramUuid.toString());
        nbt.setLong(NBT_RAM_GENERATION, record.generation);
        nbt.setInteger(NBT_DIMENSION, record.dimension);
        nbt.setString(NBT_STATUS, record.status.name());
        nbt.setString(NBT_FACTION, record.factionCode);
        nbt.setInteger(NBT_RAM_CHUNK_X, record.lastRamChunkX);
        nbt.setInteger(NBT_RAM_CHUNK_Z, record.lastRamChunkZ);
        NBTTagList slots = new NBTTagList();
        for (SlotRecord slot : record.slots) {
            slots.appendTag(writeSlot(slot));
        }
        nbt.setTag(NBT_SLOTS, slots);
        return nbt;
    }

    private NBTTagCompound writeSlot(SlotRecord slot) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger(NBT_SLOT, slot.slot);
        nbt.setString(NBT_SLOT_STATE, slot.state.name());
        if (slot.expectedCrewUuid != null) {
            nbt.setString(NBT_EXPECTED_UUID, slot.expectedCrewUuid.toString());
        }
        nbt.setLong(NBT_RESPAWN_AT, slot.respawnAt);
        nbt.setInteger(NBT_PREPARED_CHUNK_X, slot.preparedChunkX);
        nbt.setInteger(NBT_PREPARED_CHUNK_Z, slot.preparedChunkZ);
        nbt.setBoolean(NBT_HAS_LAST_CREW_CHUNK, slot.hasLastCrewChunk);
        nbt.setInteger(NBT_LAST_CREW_CHUNK_X, slot.lastCrewChunkX);
        nbt.setInteger(NBT_LAST_CREW_CHUNK_Z, slot.lastCrewChunkZ);
        if (slot.reason != null && !slot.reason.isEmpty()) {
            nbt.setString(NBT_REASON, boundReason(slot.reason));
        }
        return nbt;
    }

    private void clearAll() {
        ramsByUuid.clear();
        expectedCrewIndex.clear();
        crewChunkIndex.clear();
        deferredKeys.clear();
        readOnlyDueToInvalidData = false;
        capacityWarningLogged = false;
    }

    private void rejectLoadedData(String reason) {
        readOnlyDueToInvalidData = true;
        warn("Battle Ram crew ownership entered read-only quarantine: " + reason);
    }

    private void logCapacityWarning(int dimension) {
        if (!capacityWarningLogged) {
            capacityWarningLogged = true;
            warn("Battle Ram crew ownership capacity reached in dimension "
                    + dimension + "; refusing unsafe new ownership records and preserving tombstones.");
        }
    }

    private static void warn(String message) {
        FMLLog.warning("[LOTRMoreMobs] %s", message);
    }

    private static void warnOnce(RamRecord record, String message) {
        if (!record.warningLogged) {
            record.warningLogged = true;
            warn(message + " ram=" + record.ramUuid);
        }
    }

    private static String boundReason(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_REASON_LENGTH
                ? value : value.substring(0, MAX_REASON_LENGTH);
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.isEmpty() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static RamStatus parseRamStatus(String value) {
        try {
            return RamStatus.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static SlotState parseSlotState(String value) {
        try {
            return SlotState.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static RamSnapshot snapshot(RamRecord record) {
        SlotSnapshot[] slots = new SlotSnapshot[EntityBattleRam.CREW_SLOT_COUNT];
        for (int i = 0; i < slots.length; ++i) {
            slots[i] = snapshot(record.slots[i]);
        }
        return new RamSnapshot(record.ramUuid, record.generation, record.dimension,
                record.factionCode, record.status, record.lastRamChunkX,
                record.lastRamChunkZ, slots);
    }

    private static SlotSnapshot snapshot(SlotRecord slot) {
        return new SlotSnapshot(slot.slot, slot.state, slot.expectedCrewUuid,
                slot.respawnAt, slot.preparedChunkX, slot.preparedChunkZ,
                slot.hasLastCrewChunk, slot.lastCrewChunkX, slot.lastCrewChunkZ,
                slot.reason);
    }

    public enum RamStatus {
        ACTIVE, DISBANDED_TOMBSTONE, ABNORMALLY_REMOVED_TOMBSTONE, QUARANTINED
    }

    public enum SlotState {
        ALIVE_EXPECTED, DEAD_RESPAWN_PENDING, SPAWN_PREPARED, RETIRED, QUARANTINED
    }

    public enum CarrierResolution {
        CURRENT, STALE, TOMBSTONED, QUARANTINED, UNKNOWN_PARENT, CORRUPT
    }

    public static final class LegacySlot {
        public final boolean alive;
        public final UUID crewUuid;
        public final long respawnAt;

        public LegacySlot(boolean alive, UUID crewUuid, long respawnAt) {
            this.alive = alive;
            this.crewUuid = crewUuid;
            this.respawnAt = respawnAt;
        }
    }

    public static final class RamSlotKey {
        public final UUID ramUuid;
        public final int slot;

        public RamSlotKey(UUID ramUuid, int slot) {
            this.ramUuid = ramUuid;
            this.slot = slot;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof RamSlotKey)) {
                return false;
            }
            RamSlotKey other = (RamSlotKey)object;
            return slot == other.slot && ramUuid.equals(other.ramUuid);
        }

        @Override
        public int hashCode() {
            return ramUuid.hashCode() * 31 + slot;
        }
    }

    public static final class RamSnapshot {
        public final UUID ramUuid;
        public final long generation;
        public final int dimension;
        public final String factionCode;
        public final RamStatus status;
        public final int lastRamChunkX;
        public final int lastRamChunkZ;
        private final SlotSnapshot[] slots;

        private RamSnapshot(UUID ramUuid, long generation, int dimension,
                String factionCode, RamStatus status, int lastRamChunkX,
                int lastRamChunkZ, SlotSnapshot[] slots) {
            this.ramUuid = ramUuid;
            this.generation = generation;
            this.dimension = dimension;
            this.factionCode = factionCode;
            this.status = status;
            this.lastRamChunkX = lastRamChunkX;
            this.lastRamChunkZ = lastRamChunkZ;
            this.slots = slots;
        }

        public SlotSnapshot getSlot(int slot) {
            return isSlot(slot) ? slots[slot] : null;
        }
    }

    public static final class SlotSnapshot {
        public final int slot;
        public final SlotState state;
        public final UUID expectedCrewUuid;
        public final long respawnAt;
        public final int preparedChunkX;
        public final int preparedChunkZ;
        public final boolean hasLastCrewChunk;
        public final int lastCrewChunkX;
        public final int lastCrewChunkZ;
        public final String reason;

        private SlotSnapshot(int slot, SlotState state, UUID expectedCrewUuid,
                long respawnAt, int preparedChunkX, int preparedChunkZ,
                boolean hasLastCrewChunk, int lastCrewChunkX,
                int lastCrewChunkZ, String reason) {
            this.slot = slot;
            this.state = state;
            this.expectedCrewUuid = expectedCrewUuid;
            this.respawnAt = respawnAt;
            this.preparedChunkX = preparedChunkX;
            this.preparedChunkZ = preparedChunkZ;
            this.hasLastCrewChunk = hasLastCrewChunk;
            this.lastCrewChunkX = lastCrewChunkX;
            this.lastCrewChunkZ = lastCrewChunkZ;
            this.reason = reason;
        }
    }

    private static final class RamRecord {
        private final UUID ramUuid;
        private final long generation;
        private final int dimension;
        private final String factionCode;
        private RamStatus status;
        private int lastRamChunkX;
        private int lastRamChunkZ;
        private final SlotRecord[] slots =
                new SlotRecord[EntityBattleRam.CREW_SLOT_COUNT];
        private boolean warningLogged;

        private RamRecord(UUID ramUuid, long generation, int dimension,
                String factionCode, RamStatus status, int lastRamChunkX,
                int lastRamChunkZ) {
            this.ramUuid = ramUuid;
            this.generation = generation;
            this.dimension = dimension;
            this.factionCode = factionCode;
            this.status = status;
            this.lastRamChunkX = lastRamChunkX;
            this.lastRamChunkZ = lastRamChunkZ;
        }
    }

    private static final class SlotRecord {
        private final int slot;
        private SlotState state;
        private UUID expectedCrewUuid;
        private long respawnAt;
        private int preparedChunkX;
        private int preparedChunkZ;
        private boolean hasLastCrewChunk;
        private int lastCrewChunkX;
        private int lastCrewChunkZ;
        private String reason = "";

        private SlotRecord(int slot, SlotState state, UUID expectedCrewUuid,
                long respawnAt, int preparedChunkX, int preparedChunkZ) {
            this.slot = slot;
            this.state = state;
            this.expectedCrewUuid = expectedCrewUuid;
            this.respawnAt = respawnAt;
            this.preparedChunkX = preparedChunkX;
            this.preparedChunkZ = preparedChunkZ;
        }
    }
}
