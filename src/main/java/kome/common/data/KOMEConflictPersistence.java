package kome.common.data;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Strict independently versioned conflict-domain codec. KOM-19 owns schema 2; later conflict
 * tickets must inspect the merged version rather than assuming their preferred version number.
 */
final class KOMEConflictPersistence {
    static final int DATA_SCHEMA_VERSION = 2;
    static final String SCHEMA_KEY = "ConflictDataSchemaVersion";
    static final String SEQUENCE_KEY = "NextConflictSequence";
    static final String RECORDS_KEY = "ConflictRecords";
    static final String JOIN_BATTLE_SEQUENCE_KEY = "NextJoinBattleReceiptSequence";
    static final String JOIN_BATTLE_RECEIPTS_KEY = "JoinBattleDeploymentReceipts";

    private KOMEConflictPersistence() { }

    /** Builds and validates a detached section before a destination root is mutated. */
    static NBTTagCompound write(KOMEConflictService source) {
        return write(source, new KOMEJoinBattleDeploymentRegistry());
    }

    static NBTTagCompound write(KOMEConflictService source,
            KOMEJoinBattleDeploymentRegistry joinBattleSource) {
        KOMEConflictService.PersistenceSnapshot snapshot = required(source, "Conflict service").persistenceSnapshot();
        KOMEConflictService validated = KOMEConflictService.restore(snapshot.records, snapshot.nextConflictSequence);
        KOMEConflictService.PersistenceSnapshot stable = validated.persistenceSnapshot();
        KOMEJoinBattleDeploymentRegistry.PersistenceSnapshot joinSnapshot =
            required(joinBattleSource, "Join Battle receipt registry").persistenceSnapshot();
        KOMEJoinBattleDeploymentRegistry validatedJoin = KOMEJoinBattleDeploymentRegistry.restore(
            joinSnapshot.receipts, joinSnapshot.nextSequence);
        KOMEJoinBattleDeploymentRegistry.PersistenceSnapshot stableJoin =
            validatedJoin.persistenceSnapshot();
        validateReceiptReferences(stable.records, stable.nextConflictSequence,
            stableJoin.receipts);
        NBTTagCompound section = new NBTTagCompound();
        section.setInteger(SCHEMA_KEY, DATA_SCHEMA_VERSION);
        section.setLong(SEQUENCE_KEY, stable.nextConflictSequence);
        NBTTagList records = new NBTTagList();
        List<KOMEConflictRecord> ordered = new ArrayList<KOMEConflictRecord>(stable.records.values());
        Collections.sort(ordered, new Comparator<KOMEConflictRecord>() {
            @Override public int compare(KOMEConflictRecord first, KOMEConflictRecord second) {
                return first.getTileId().compareTo(second.getTileId());
            }
        });
        for (KOMEConflictRecord record : ordered) records.appendTag(writeRecord(record));
        section.setTag(RECORDS_KEY, records);
        section.setLong(JOIN_BATTLE_SEQUENCE_KEY, stableJoin.nextSequence);
        NBTTagList joinReceipts = new NBTTagList();
        List<KOMEJoinBattleDeploymentReceipt> orderedReceipts =
            new ArrayList<KOMEJoinBattleDeploymentReceipt>(stableJoin.receipts.values());
        Collections.sort(orderedReceipts, new Comparator<KOMEJoinBattleDeploymentReceipt>() {
            @Override public int compare(KOMEJoinBattleDeploymentReceipt first,
                    KOMEJoinBattleDeploymentReceipt second) {
                return first.getReceiptId().compareTo(second.getReceiptId());
            }
        });
        for (KOMEJoinBattleDeploymentReceipt receipt : orderedReceipts)
            joinReceipts.appendTag(writeJoinBattleReceipt(receipt));
        section.setTag(JOIN_BATTLE_RECEIPTS_KEY, joinReceipts);
        return section;
    }

    static KOMEConflictService read(NBTTagCompound root) {
        return readSection(root).conflicts;
    }

    static Loaded readSection(NBTTagCompound root) {
        require(root, SCHEMA_KEY, 3);
        int schema = root.getInteger(SCHEMA_KEY);
        if (schema != 1 && schema != DATA_SCHEMA_VERSION)
            throw new IllegalArgumentException("Unsupported " + SCHEMA_KEY + ": " + schema);
        if (schema == 1 && (root.hasKey(JOIN_BATTLE_SEQUENCE_KEY)
                || root.hasKey(JOIN_BATTLE_RECEIPTS_KEY)))
            throw new IllegalArgumentException("ConflictData v1 cannot contain Join Battle receipt authority.");
        require(root, SEQUENCE_KEY, 4);
        require(root, RECORDS_KEY, 9);
        long nextSequence = root.getLong(SEQUENCE_KEY);
        Map<String, KOMEConflictRecord> records = new LinkedHashMap<String, KOMEConflictRecord>();
        Set<String> conflictIds = new LinkedHashSet<String>();
        NBTTagList rows = compoundList(root, RECORDS_KEY);
        for (int i = 0; i < rows.tagCount(); i++) {
            KOMEConflictRecord record;
            try {
                record = readRecord(rows.getCompoundTagAt(i));
            } catch (RuntimeException invalid) {
                throw new IllegalArgumentException("Invalid ConflictRecords[" + i + "]: " + invalid.getMessage(), invalid);
            }
            if (records.put(record.getTileId(), record) != null)
                throw new IllegalArgumentException("Duplicate conflict tile authority: " + record.getTileId());
            if (!conflictIds.add(record.getConflictId()))
                throw new IllegalArgumentException("Duplicate conflict identity: " + record.getConflictId());
        }
        KOMEConflictService conflicts = KOMEConflictService.restore(records, nextSequence);
        KOMEJoinBattleDeploymentRegistry joinBattle;
        if (schema == 1) {
            joinBattle = new KOMEJoinBattleDeploymentRegistry();
        } else {
            require(root, JOIN_BATTLE_SEQUENCE_KEY, 4);
            require(root, JOIN_BATTLE_RECEIPTS_KEY, 9);
            long nextJoinSequence = root.getLong(JOIN_BATTLE_SEQUENCE_KEY);
            Map<String, KOMEJoinBattleDeploymentReceipt> receipts =
                new LinkedHashMap<String, KOMEJoinBattleDeploymentReceipt>();
            NBTTagList receiptRows = compoundList(root, JOIN_BATTLE_RECEIPTS_KEY);
            for (int i = 0; i < receiptRows.tagCount(); i++) {
                KOMEJoinBattleDeploymentReceipt receipt;
                try {
                    receipt = readJoinBattleReceipt(receiptRows.getCompoundTagAt(i));
                } catch (RuntimeException invalid) {
                    throw new IllegalArgumentException("Invalid " + JOIN_BATTLE_RECEIPTS_KEY
                        + "[" + i + "]: " + invalid.getMessage(), invalid);
                }
                if (receipts.put(receipt.getReceiptId(), receipt) != null)
                    throw new IllegalArgumentException("Duplicate Join Battle receipt identity: "
                        + receipt.getReceiptId());
            }
            joinBattle = KOMEJoinBattleDeploymentRegistry.restore(receipts, nextJoinSequence);
        }
        validateReceiptReferences(records, nextSequence, joinBattle.records());
        return new Loaded(conflicts, joinBattle, schema == 1);
    }

    static final class Loaded {
        final KOMEConflictService conflicts;
        final KOMEJoinBattleDeploymentRegistry joinBattleReceipts;
        final boolean migratedFromV1;

        Loaded(KOMEConflictService conflicts,
                KOMEJoinBattleDeploymentRegistry joinBattleReceipts,
                boolean migratedFromV1) {
            this.conflicts = required(conflicts, "Conflict service");
            this.joinBattleReceipts = required(joinBattleReceipts,
                "Join Battle receipt registry");
            this.migratedFromV1 = migratedFromV1;
        }

        static Loaded empty() {
            return new Loaded(new KOMEConflictService(),
                new KOMEJoinBattleDeploymentRegistry(), false);
        }
    }

    private static void validateReceiptReferences(Map<String, KOMEConflictRecord> conflicts,
            long nextConflictSequence,
            Map<String, KOMEJoinBattleDeploymentReceipt> receipts) {
        Map<String, KOMEConflictRecord> byId = new LinkedHashMap<String, KOMEConflictRecord>();
        for (KOMEConflictRecord record : conflicts.values()) byId.put(record.getConflictId(), record);
        for (KOMEJoinBattleDeploymentReceipt receipt : receipts.values()) {
            long sequence = KOMEConflictIdAllocator.sequenceOf(receipt.getConflictId());
            if (sequence >= nextConflictSequence)
                throw new IllegalArgumentException("Join Battle receipt references an unissued conflict identity: "
                    + receipt.getReceiptId());
            KOMEConflictRecord current = byId.get(receipt.getConflictId());
            if (current != null && !current.getTileId().equals(receipt.getTileId()))
                throw new IllegalArgumentException("Join Battle receipt tile disagrees with matching current conflict: "
                    + receipt.getReceiptId());
            // A missing old ConflictRecord is valid recovery input: latest-per-tile replacement
            // must never discard a deployed or pending-egress physical obligation.
        }
    }

    private static NBTTagCompound writeJoinBattleReceipt(
            KOMEJoinBattleDeploymentReceipt value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("ReceiptId", value.getReceiptId());
        tag.setString("ActionToken", value.getActionToken());
        tag.setString("PlayerId", value.getPlayerId().toString());
        tag.setString("ConflictId", value.getConflictId());
        tag.setString("TileId", value.getTileId());
        tag.setLong("AcceptedConflictRevision", value.getAcceptedConflictRevision());
        tag.setString("FactionId", value.getFactionId());
        tag.setString("SelectedCompanyId", value.getSelectedCompanyId());
        tag.setLong("CreatedAtMillis", value.getCreatedAtMillis());
        tag.setString("State", value.getState().name());
        tag.setLong("UpdatedAtMillis", value.getUpdatedAtMillis());
        putOptionalLong(tag, "DeployedAtMillis", value.getDeployedAtMillis());
        putOptionalLong(tag, "EgressRequestedAtMillis", value.getEgressRequestedAtMillis());
        if (!value.getEgressReason().isEmpty()) tag.setString("EgressReason", value.getEgressReason());
        putOptionalLong(tag, "ClosedAtMillis", value.getClosedAtMillis());
        if (value.getClosureOutcome() != null)
            tag.setString("ClosureOutcome", value.getClosureOutcome().name());
        tag.setTag("ReturnAnchor", writePose(value.getReturnAnchor()));
        if (value.getDeploymentDestination() != null)
            tag.setTag("DeploymentDestination", writePose(value.getDeploymentDestination()));
        tag.setString("ParticipationRecovery", value.getParticipationRecovery().name());
        tag.setBoolean("EnteredMounted", value.isEnteredMounted());
        if (value.isEnteredMounted()) {
            tag.setString("MountUuid", value.getMountUuid().toString());
            tag.setString("MountEntityType", value.getMountEntityType());
            tag.setString("MountProfile", value.getMountProfile().name());
            tag.setTag("MountSourceAnchor", writePose(value.getMountSourceAnchor()));
            tag.setString("MountTransferPhase", value.getMountTransferPhase().name());
            NBTTagCompound mountNbt = value.getTemporaryMountNbt();
            if (mountNbt != null) tag.setTag("TemporaryMountNbt", mountNbt.copy());
            if (value.getMountDisposition() != null)
                tag.setString("MountDisposition", value.getMountDisposition().name());
        }
        return tag;
    }

    private static KOMEJoinBattleDeploymentReceipt readJoinBattleReceipt(NBTTagCompound tag) {
        String rawReceipt = string(tag, "ReceiptId");
        String rawToken = string(tag, "ActionToken");
        String rawConflict = string(tag, "ConflictId");
        String rawTile = string(tag, "TileId");
        String rawFaction = string(tag, "FactionId");
        String rawCompany = string(tag, "SelectedCompanyId");
        KOMEJoinBattleDeploymentReceipt.Builder builder =
            KOMEJoinBattleDeploymentReceipt.builder()
                .receiptId(rawReceipt)
                .actionToken(rawToken)
                .playerId(uuid(string(tag, "PlayerId"), "Join Battle player"))
                .conflictId(rawConflict)
                .tileId(rawTile)
                .acceptedConflictRevision(longValue(tag, "AcceptedConflictRevision"))
                .factionId(rawFaction)
                .selectedCompanyId(rawCompany)
                .createdAtMillis(longValue(tag, "CreatedAtMillis"))
                .state(enumValue(KOMEJoinBattleDeploymentReceipt.State.class,
                    string(tag, "State"), "Join Battle receipt state"))
                .updatedAtMillis(longValue(tag, "UpdatedAtMillis"))
                .deployedAtMillis(optionalLong(tag, "DeployedAtMillis"))
                .egressRequestedAtMillis(optionalLong(tag, "EgressRequestedAtMillis"))
                .egressReason(optionalString(tag, "EgressReason"))
                .closedAtMillis(optionalLong(tag, "ClosedAtMillis"))
                .closureOutcome(optionalEnum(tag, "ClosureOutcome",
                    KOMEJoinBattleDeploymentReceipt.ClosureOutcome.class,
                    "Join Battle closure outcome"))
                .returnAnchor(readPose(compound(tag, "ReturnAnchor")))
                .deploymentDestination(tag.hasKey("DeploymentDestination")
                    ? readPose(compound(tag, "DeploymentDestination")) : null)
                .participationRecovery(enumValue(
                    KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.class,
                    string(tag, "ParticipationRecovery"), "participation recovery state"));
        require(tag, "EnteredMounted", 1);
        boolean mounted = tag.getBoolean("EnteredMounted");
        String rawMountEntityType = null;
        builder.enteredMounted(mounted);
        if (mounted) {
            rawMountEntityType = string(tag, "MountEntityType");
            builder.mountUuid(uuid(string(tag, "MountUuid"), "mount"))
                .mountEntityType(rawMountEntityType)
                .mountProfile(enumValue(KOMEJoinBattleDeploymentReceipt.MountProfile.class,
                    string(tag, "MountProfile"), "mount profile"))
                .mountSourceAnchor(readPose(compound(tag, "MountSourceAnchor")))
                .mountTransferPhase(enumValue(
                    KOMEJoinBattleDeploymentReceipt.MountTransferPhase.class,
                    string(tag, "MountTransferPhase"), "mount transfer phase"))
                .temporaryMountNbt(tag.hasKey("TemporaryMountNbt")
                    ? compound(tag, "TemporaryMountNbt") : null)
                .mountDisposition(optionalEnum(tag, "MountDisposition",
                    KOMEJoinBattleDeploymentReceipt.MountDisposition.class,
                    "mount disposition"));
        } else {
            for (String forbidden : new String[] {"MountUuid", "MountEntityType", "MountProfile",
                    "MountSourceAnchor", "MountTransferPhase", "TemporaryMountNbt", "MountDisposition"})
                if (tag.hasKey(forbidden))
                    throw new IllegalArgumentException("Unmounted receipt contains " + forbidden + ".");
        }
        KOMEJoinBattleDeploymentReceipt receipt = builder.build();
        if (!rawReceipt.equals(receipt.getReceiptId())
                || !rawToken.equals(receipt.getActionToken())
                || !rawConflict.equals(receipt.getConflictId())
                || !rawTile.equals(receipt.getTileId())
                || !rawFaction.equals(receipt.getFactionId())
                || !rawCompany.equals(receipt.getSelectedCompanyId())
                || mounted && !rawMountEntityType.equals(receipt.getMountEntityType()))
            throw new IllegalArgumentException("Join Battle receipt contains noncanonical identity text.");
        return receipt;
    }

    private static NBTTagCompound writePose(KOMEJoinBattleDeploymentReceipt.Pose pose) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("Dimension", pose.dimensionId);
        tag.setDouble("X", pose.x); tag.setDouble("Y", pose.y); tag.setDouble("Z", pose.z);
        tag.setFloat("Yaw", pose.yaw); tag.setFloat("Pitch", pose.pitch);
        return tag;
    }

    private static KOMEJoinBattleDeploymentReceipt.Pose readPose(NBTTagCompound tag) {
        require(tag, "Dimension", 3); require(tag, "X", 6); require(tag, "Y", 6);
        require(tag, "Z", 6); require(tag, "Yaw", 5); require(tag, "Pitch", 5);
        return new KOMEJoinBattleDeploymentReceipt.Pose(tag.getInteger("Dimension"),
            tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"),
            tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }

    private static String optionalString(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) return "";
        require(tag, key, 8);
        return tag.getString(key);
    }

    private static <E extends Enum<E>> E optionalEnum(NBTTagCompound tag, String key,
            Class<E> type, String label) {
        return tag.hasKey(key) ? enumValue(type, string(tag, key), label) : null;
    }

    private static NBTTagCompound writeRecord(KOMEConflictRecord record) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("TileId", record.getTileId());
        tag.setString("ConflictId", record.getConflictId());
        tag.setString("State", record.getState().name());
        tag.setLong("Revision", record.getRevision());
        tag.setLong("CreatedAtMillis", record.getCreatedAtMillis());
        putOptionalLong(tag, "EndedAtMillis", record.getEndedAtMillis());

        NBTTagList commitments = new NBTTagList();
        for (String id : sorted(record.getCommitments().keySet())) {
            Commitment value = record.getCommitments().get(id);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("DetachmentId", value.detachmentId);
            row.setString("Origin", value.origin.name());
            row.setLong("AcceptedAtMillis", value.acceptedAtMillis);
            row.setString("MovementOrderId", value.movementOrderId);
            if (value.validatedEvent != null)
                row.setTag("ValidatedEvent", writeValidatedEvent(value.validatedEvent));
            commitments.appendTag(row);
        }
        tag.setTag("Commitments", commitments);

        NBTTagList factions = new NBTTagList();
        for (String id : sorted(record.getFactionParticipation().keySet())) {
            FactionParticipation value = record.getFactionParticipation().get(id);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("FactionId", value.factionId);
            row.setLong("ContinuitySequence", value.continuitySequence);
            row.setLong("StartedAtMillis", value.startedAtMillis);
            putOptionalLong(row, "EndedAtMillis", value.endedAtMillis);
            factions.appendTag(row);
        }
        tag.setTag("FactionParticipation", factions);

        NBTTagList players = new NBTTagList();
        List<PlayerParticipation> orderedPlayers = new ArrayList<PlayerParticipation>(record.getPlayers().values());
        Collections.sort(orderedPlayers, new Comparator<PlayerParticipation>() {
            @Override public int compare(PlayerParticipation first, PlayerParticipation second) {
                return first.playerId.toString().compareTo(second.playerId.toString());
            }
        });
        for (PlayerParticipation value : orderedPlayers) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("PlayerId", value.playerId.toString());
            row.setString("FactionId", value.factionAtRegistration);
            row.setLong("StartedAtMillis", value.startedAtMillis);
            row.setString("Status", value.status.name());
            putOptionalLong(row, "WithdrawnAtMillis", value.withdrawnAtMillis);
            players.appendTag(row);
        }
        tag.setTag("Players", players);

        NBTTagList garrisons = new NBTTagList();
        for (String id : sorted(record.getOriginalGarrison().keySet())) {
            GarrisonCohort cohort = record.getOriginalGarrison().get(id);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("DetachmentId", cohort.detachmentId);
            NBTTagList members = new NBTTagList();
            List<UUID> memberIds = new ArrayList<UUID>(cohort.members.keySet());
            Collections.sort(memberIds, new Comparator<UUID>() {
                @Override public int compare(UUID first, UUID second) { return first.toString().compareTo(second.toString()); }
            });
            for (UUID memberId : memberIds) {
                NBTTagCompound member = new NBTTagCompound();
                member.setString("MemberId", memberId.toString());
                member.setString("State", cohort.members.get(memberId).name());
                members.appendTag(member);
            }
            row.setTag("Members", members);
            garrisons.appendTag(row);
        }
        tag.setTag("OriginalGarrison", garrisons);

        tag.setTag("ComplexCatalog", writeCatalog(record.getComplexCatalog()));
        NBTTagList complexes = new NBTTagList();
        for (String id : sorted(record.getComplexes().keySet()))
            complexes.appendTag(writeComplex(record.getComplexes().get(id)));
        tag.setTag("Complexes", complexes);
        tag.setTag("ResponseTimer", writeTimer(record.getResponseTimer()));
        tag.setTag("CaptureTimer", writeTimer(record.getCaptureTimer()));
        tag.setTag("CombatEpisode", writeEpisode(record.getCombatEpisode()));
        if (record.getEncirclement() != null) tag.setTag("Encirclement", writeEncirclement(record.getEncirclement()));

        NBTTagList diagnostics = new NBTTagList();
        for (String key : sorted(record.getDiagnostics().keySet())) {
            ReferenceDiagnostic value = record.getDiagnostics().get(key);
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Kind", value.kind.name());
            row.setString("ReferenceId", value.referenceId);
            row.setString("Status", value.status.name());
            row.setString("Reason", value.reason);
            diagnostics.appendTag(row);
        }
        tag.setTag("Diagnostics", diagnostics);
        tag.setTag("LastTransition", writeTransition(record.getLastTransition()));
        return tag;
    }

    private static KOMEConflictRecord readRecord(NBTTagCompound tag) {
        String rawTile = string(tag, "TileId");
        String canonicalTile = tile(rawTile);
        if (!rawTile.equals(canonicalTile)) throw new IllegalArgumentException("Noncanonical conflict tile ID.");
        String conflictId = canonicalConflictId(string(tag, "ConflictId"));
        State state = enumValue(State.class, string(tag, "State"), "conflict state");
        long revision = longValue(tag, "Revision");
        long createdAt = longValue(tag, "CreatedAtMillis");
        Long endedAt = optionalLong(tag, "EndedAtMillis");
        Draft draft = new Draft(conflictId, canonicalTile, state, createdAt);
        draft.revision = revision;
        draft.endedAtMillis = endedAt;

        NBTTagList commitments = compoundList(tag, "Commitments");
        for (int i = 0; i < commitments.tagCount(); i++) {
            NBTTagCompound row = commitments.getCompoundTagAt(i);
            ValidatedCommitmentEvent validatedEvent = row.hasKey("ValidatedEvent")
                ? readValidatedEvent(compound(row, "ValidatedEvent")) : null;
            Commitment value = new Commitment(canonicalCompanyId(string(row, "DetachmentId")),
                enumValue(EntryOrigin.class, string(row, "Origin"), "commitment origin"),
                longValue(row, "AcceptedAtMillis"), canonicalOptionalId(string(row, "MovementOrderId")),
                validatedEvent);
            duplicate(draft.commitments.put(value.detachmentId, value), "commitment", value.detachmentId);
        }

        NBTTagList factions = compoundList(tag, "FactionParticipation");
        for (int i = 0; i < factions.tagCount(); i++) {
            NBTTagCompound row = factions.getCompoundTagAt(i);
            FactionParticipation value = new FactionParticipation(canonicalFaction(string(row, "FactionId")),
                longValue(row, "ContinuitySequence"), longValue(row, "StartedAtMillis"),
                optionalLong(row, "EndedAtMillis"));
            duplicate(draft.factionParticipation.put(value.factionId, value), "faction participation", value.factionId);
        }

        NBTTagList players = compoundList(tag, "Players");
        for (int i = 0; i < players.tagCount(); i++) {
            NBTTagCompound row = players.getCompoundTagAt(i);
            UUID playerId = uuid(string(row, "PlayerId"), "player");
            PlayerParticipation value = new PlayerParticipation(playerId, canonicalFaction(string(row, "FactionId")),
                longValue(row, "StartedAtMillis"), enumValue(PlayerStatus.class, string(row, "Status"), "player status"),
                optionalLong(row, "WithdrawnAtMillis"));
            duplicate(draft.players.put(playerId, value), "player participation", playerId.toString());
        }

        NBTTagList garrisons = compoundList(tag, "OriginalGarrison");
        for (int i = 0; i < garrisons.tagCount(); i++) {
            NBTTagCompound row = garrisons.getCompoundTagAt(i);
            String detachmentId = canonicalCompanyId(string(row, "DetachmentId"));
            Map<UUID, GarrisonMemberState> members = new LinkedHashMap<UUID, GarrisonMemberState>();
            NBTTagList memberRows = compoundList(row, "Members");
            for (int j = 0; j < memberRows.tagCount(); j++) {
                NBTTagCompound member = memberRows.getCompoundTagAt(j);
                UUID memberId = uuid(string(member, "MemberId"), "garrison member");
                GarrisonMemberState memberState = enumValue(GarrisonMemberState.class,
                    string(member, "State"), "garrison member state");
                duplicate(members.put(memberId, memberState), "garrison member", memberId.toString());
            }
            GarrisonCohort cohort = new GarrisonCohort(detachmentId, members);
            duplicate(draft.originalGarrison.put(cohort.detachmentId, cohort), "garrison cohort", cohort.detachmentId);
        }

        draft.complexCatalog = readCatalog(compound(tag, "ComplexCatalog"));
        NBTTagList complexes = compoundList(tag, "Complexes");
        for (int i = 0; i < complexes.tagCount(); i++) {
            ComplexSubstate value = readComplex(complexes.getCompoundTagAt(i));
            duplicate(draft.complexes.put(value.complexId, value), "Siege Complex", value.complexId);
        }
        draft.responseTimer = readTimer(compound(tag, "ResponseTimer"));
        draft.captureTimer = readTimer(compound(tag, "CaptureTimer"));
        draft.combatEpisode = readEpisode(compound(tag, "CombatEpisode"));
        if (tag.hasKey("Encirclement")) {
            require(tag, "Encirclement", 10);
            draft.encirclement = readEncirclement(tag.getCompoundTag("Encirclement"));
        } else {
            draft.encirclement = null;
        }

        NBTTagList diagnostics = compoundList(tag, "Diagnostics");
        for (int i = 0; i < diagnostics.tagCount(); i++) {
            NBTTagCompound row = diagnostics.getCompoundTagAt(i);
            ReferenceDiagnostic value = new ReferenceDiagnostic(
                enumValue(ReferenceKind.class, string(row, "Kind"), "reference kind"),
                canonicalId(string(row, "ReferenceId")),
                enumValue(ReferenceStatus.class, string(row, "Status"), "reference status"),
                canonicalText(string(row, "Reason"), "diagnostic reason", 512));
            duplicate(draft.diagnostics.put(value.key(), value), "reference diagnostic", value.key());
        }
        draft.lastTransition = readTransition(compound(tag, "LastTransition"));
        return new KOMEConflictRecord(draft);
    }

    private static NBTTagCompound writeCatalog(ComplexCatalog value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Availability", value.availability.name());
        tag.setTag("RequiredComplexIds", idRows(value.requiredComplexIds));
        return tag;
    }

    private static NBTTagCompound writeValidatedEvent(ValidatedCommitmentEvent value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("DetachmentFactionId", value.detachmentFactionId);
        tag.setString("AuthorityKind", value.authorityKind.name());
        tag.setString("AuthorityFactionId", value.authorityFactionId);
        tag.setBoolean("CreatedConflict", value.createdConflict);
        tag.setBoolean("DefensiveContext", value.defensiveContext);
        NBTTagList factions = new NBTTagList();
        for (String detachmentId : sorted(value.originalGarrisonFactions.keySet())) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("DetachmentId", detachmentId);
            row.setString("FactionId", value.originalGarrisonFactions.get(detachmentId));
            factions.appendTag(row);
        }
        tag.setTag("OriginalGarrisonFactions", factions);
        return tag;
    }

    private static ValidatedCommitmentEvent readValidatedEvent(NBTTagCompound tag) {
        String detachmentFaction = canonicalFaction(string(tag, "DetachmentFactionId"));
        ConflictAuthorityKind authorityKind = enumValue(ConflictAuthorityKind.class,
            string(tag, "AuthorityKind"), "conflict authority kind");
        String authorityFaction = canonicalFaction(string(tag, "AuthorityFactionId"));
        boolean createdConflict = booleanValue(tag, "CreatedConflict");
        boolean defensiveContext = booleanValue(tag, "DefensiveContext");
        Map<String, String> factions = new LinkedHashMap<String, String>();
        NBTTagList rows = compoundList(tag, "OriginalGarrisonFactions");
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String detachmentId = canonicalCompanyId(string(row, "DetachmentId"));
            String factionId = canonicalFaction(string(row, "FactionId"));
            duplicate(factions.put(detachmentId, factionId),
                "validated-event garrison faction", detachmentId);
        }
        return new ValidatedCommitmentEvent(detachmentFaction, authorityKind,
            authorityFaction, createdConflict, defensiveContext, factions);
    }

    private static ComplexCatalog readCatalog(NBTTagCompound tag) {
        return new ComplexCatalog(enumValue(CatalogAvailability.class, string(tag, "Availability"), "catalog availability"),
            readIds(tag, "RequiredComplexIds"));
    }

    private static NBTTagCompound writeComplex(ComplexSubstate value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("ComplexId", value.complexId);
        tag.setString("State", value.state.name());
        tag.setString("ActiveAssaultId", value.activeAssaultId);
        tag.setTag("SecuredSegmentIds", idRows(value.progress.securedSegmentIds));
        if (value.lead != null) {
            NBTTagCompound lead = new NBTTagCompound();
            lead.setString("FactionId", value.lead.factionId);
            if (value.lead.actorId != null) lead.setString("ActorId", value.lead.actorId.toString());
            tag.setTag("Lead", lead);
        }
        return tag;
    }

    private static ComplexSubstate readComplex(NBTTagCompound tag) {
        LeadAuthority lead = null;
        if (tag.hasKey("Lead")) {
            NBTTagCompound value = compound(tag, "Lead");
            UUID actor = value.hasKey("ActorId") ? uuid(string(value, "ActorId"), "lead actor") : null;
            lead = new LeadAuthority(canonicalFaction(string(value, "FactionId")), actor);
        }
        return new ComplexSubstate(canonicalId(string(tag, "ComplexId")),
            enumValue(ComplexState.class, string(tag, "State"), "complex state"),
            canonicalOptionalId(string(tag, "ActiveAssaultId")),
            new ProgressCheckpoint(readIds(tag, "SecuredSegmentIds")), lead);
    }

    private static NBTTagCompound writeTimer(TimerSlot value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Status", value.status.name());
        tag.setLong("DurationSnapshotMillis", value.durationSnapshotMillis);
        tag.setLong("ElapsedMillis", value.elapsedMillis);
        putOptionalLong(tag, "CheckpointAtMillis", value.checkpointAtMillis);
        tag.setString("EpisodeId", value.episodeId);
        return tag;
    }

    private static TimerSlot readTimer(NBTTagCompound tag) {
        return new TimerSlot(enumValue(TimerStatus.class, string(tag, "Status"), "timer status"),
            longValue(tag, "DurationSnapshotMillis"), longValue(tag, "ElapsedMillis"),
            optionalLong(tag, "CheckpointAtMillis"), canonicalOptionalId(string(tag, "EpisodeId")));
    }

    private static NBTTagCompound writeEpisode(CombatEpisode value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("Sequence", value.sequence);
        tag.setString("EpisodeId", value.episodeId);
        tag.setString("State", value.state.name());
        putOptionalLong(tag, "StartedAtMillis", value.startedAtMillis);
        putOptionalLong(tag, "EndedAtMillis", value.endedAtMillis);
        return tag;
    }

    private static CombatEpisode readEpisode(NBTTagCompound tag) {
        return new CombatEpisode(longValue(tag, "Sequence"), canonicalOptionalId(string(tag, "EpisodeId")),
            enumValue(EpisodeState.class, string(tag, "State"), "episode state"),
            optionalLong(tag, "StartedAtMillis"), optionalLong(tag, "EndedAtMillis"));
    }

    private static NBTTagCompound writeEncirclement(EncirclementLifecycle value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("StartedAtMillis", value.startedAtMillis);
        tag.setLong("LiveElapsedMillis", value.liveElapsedMillis);
        tag.setLong("CheckpointAtMillis", value.checkpointAtMillis);
        putOptionalLong(tag, "EndedAtMillis", value.endedAtMillis);
        return tag;
    }

    private static EncirclementLifecycle readEncirclement(NBTTagCompound tag) {
        return new EncirclementLifecycle(longValue(tag, "StartedAtMillis"), longValue(tag, "LiveElapsedMillis"),
            longValue(tag, "CheckpointAtMillis"), optionalLong(tag, "EndedAtMillis"));
    }

    private static NBTTagCompound writeTransition(LastTransition value) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Operation", value.operation.name());
        if (value.fromState != null) tag.setString("FromState", value.fromState.name());
        tag.setString("ToState", value.toState.name());
        tag.setLong("Revision", value.revision);
        tag.setLong("TimestampMillis", value.timestampMillis);
        tag.setString("Actor", value.actor);
        tag.setString("Reason", value.reason);
        tag.setString("Subject", value.subject);
        return tag;
    }

    private static LastTransition readTransition(NBTTagCompound tag) {
        Operation operation = enumValue(Operation.class, string(tag, "Operation"), "transition operation");
        State from = tag.hasKey("FromState")
            ? enumValue(State.class, string(tag, "FromState"), "transition origin") : null;
        State to = enumValue(State.class, string(tag, "ToState"), "transition destination");
        long revision = longValue(tag, "Revision");
        long timestamp = longValue(tag, "TimestampMillis");
        Context context = new Context(timestamp,
            canonicalText(string(tag, "Actor"), "transition actor", 128),
            canonicalText(string(tag, "Reason"), "transition reason", 512));
        return new LastTransition(operation, from, to, revision, context,
            canonicalOptional(string(tag, "Subject")));
    }

    private static NBTTagList idRows(Set<String> values) {
        NBTTagList rows = new NBTTagList();
        for (String id : sorted(values)) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Id", id);
            rows.appendTag(row);
        }
        return rows;
    }

    private static Set<String> readIds(NBTTagCompound tag, String key) {
        Set<String> values = new LinkedHashSet<String>();
        NBTTagList rows = compoundList(tag, key);
        for (int i = 0; i < rows.tagCount(); i++) {
            String value = canonicalId(string(rows.getCompoundTagAt(i), "Id"));
            if (!values.add(value)) throw new IllegalArgumentException("Duplicate ID in " + key + ": " + value);
        }
        return values;
    }

    private static List<String> sorted(Set<String> values) {
        List<String> result = new ArrayList<String>(values);
        Collections.sort(result);
        return result;
    }

    private static NBTTagCompound compound(NBTTagCompound parent, String key) {
        require(parent, key, 10);
        return parent.getCompoundTag(key);
    }

    private static NBTTagList compoundList(NBTTagCompound parent, String key) {
        require(parent, key, 9);
        NBTTagList list = (NBTTagList) parent.getTag(key);
        int elementType = list.func_150303_d();
        if (elementType != 10 && (elementType != 0 || list.tagCount() > 0))
            throw new IllegalArgumentException(key + " must contain compound records.");
        NBTTagList copy = (NBTTagList) list.copy();
        for (int i = copy.tagCount() - 1; i >= 0; i--) {
            NBTBase actual = copy.removeTag(i);
            if (!(actual instanceof NBTTagCompound))
                throw new IllegalArgumentException(key + " contains a non-compound record.");
        }
        return list;
    }

    private static String string(NBTTagCompound tag, String key) {
        require(tag, key, 8);
        return tag.getString(key);
    }

    private static long longValue(NBTTagCompound tag, String key) {
        require(tag, key, 4);
        return tag.getLong(key);
    }

    private static boolean booleanValue(NBTTagCompound tag, String key) {
        require(tag, key, 1);
        return tag.getBoolean(key);
    }

    private static Long optionalLong(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) return null;
        require(tag, key, 4);
        return Long.valueOf(tag.getLong(key));
    }

    private static void putOptionalLong(NBTTagCompound tag, String key, Long value) {
        if (value != null) tag.setLong(key, value.longValue());
    }

    private static void require(NBTTagCompound tag, String key, int type) {
        if (tag == null || !tag.hasKey(key, type))
            throw new IllegalArgumentException("Missing or wrong NBT type for " + key + ".");
    }

    private static UUID uuid(String value, String label) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException("Noncanonical UUID.");
            return parsed;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Malformed " + label + " UUID.", invalid);
        }
    }

    private static String canonicalConflictId(String value) {
        String canonical = KOMEConflictIdAllocator.requireIdentity(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical conflict ID.");
        return canonical;
    }

    private static String canonicalCompanyId(String value) {
        String canonical = companyId(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical detachment ID.");
        return canonical;
    }

    private static String canonicalFaction(String value) {
        String canonical = faction(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical faction ID.");
        return canonical;
    }

    private static String canonicalId(String value) {
        String canonical = id(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical reference ID.");
        return canonical;
    }

    private static String canonicalOptionalId(String value) {
        String canonical = optionalId(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical optional reference ID.");
        return canonical;
    }

    private static String canonicalText(String value, String label, int maximum) {
        String canonical = text(value, label, maximum);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical " + label + ".");
        return canonical;
    }

    private static String canonicalOptional(String value) {
        String canonical = optional(value);
        if (!canonical.equals(value)) throw new IllegalArgumentException("Noncanonical optional text.");
        return canonical;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Unknown " + label + ": " + value, invalid); }
    }

    private static void duplicate(Object previous, String kind, String identity) {
        if (previous != null) throw new IllegalArgumentException("Duplicate " + kind + ": " + identity);
    }
}
