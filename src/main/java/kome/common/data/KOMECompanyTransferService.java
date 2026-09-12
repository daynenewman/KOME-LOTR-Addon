package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomic funding/metadata mutation for permanent whole-company ownership transfers. */
public final class KOMECompanyTransferService {
    public static final long OFFER_MILLIS = 5L * 60L * 1000L;

    private KOMECompanyTransferService() {
    }

    public static Result offer(KOMEWorldData data, KOMEArmyCompany company, UUID owner, UUID recipient,
            String recipientName, long nowMillis) {
        if (data == null || company == null || owner == null || !owner.equals(company.owner)) return Result.failure("Only the actual owner may offer this company.");
        if (company.temporaryController != null && owner.equals(company.temporaryController) && !owner.equals(company.owner)) return Result.failure("A temporary controller cannot transfer ownership.");
        if (company.isMoving()) return Result.failure("A company cannot transfer while moving or crossing a route boundary.");
        if (recipient == null || recipient.equals(owner)) return Result.failure("Choose a different same-faction recipient.");
        if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) return Result.failure("Wartime Stewardship is temporary authority, not transferable ownership.");
        String requiredFaction = KOMEAlliance.normalizeFactionKey(company.faction);
        String recipientFaction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(recipient));
        if (requiredFaction.length() == 0 || !requiredFaction.equals(recipientFaction)) {
            return Result.failure("Transfer rejected: recipient is not currently pledged to the company's required faction "
                + KOMEAlliance.displayFactionName(requiredFaction) + ".");
        }
        company.transferRecipient = recipient;
        company.transferRecipientName = recipientName == null ? "" : recipientName;
        company.transferOfferedBy = owner;
        company.transferOfferedAtMillis = nowMillis;
        company.transferExpiresAtMillis = nowMillis + OFFER_MILLIS;
        data.markDirty();
        return Result.success("Transfer offered to " + company.transferRecipientName + " for five minutes.");
    }

    public static Result accept(KOMEWorldData data, KOMEArmyCompany company, UUID recipient,
            String recipientName, long nowMillis) {
        if (data == null || company == null || recipient == null || !recipient.equals(company.transferRecipient)) return Result.failure("No transfer offer is pending for you.");
        if (nowMillis > company.transferExpiresAtMillis) {
            company.clearTransferOffer();
            data.markDirty();
            return Result.failure("The company transfer offer expired.");
        }
        if (company.isMoving()) return Result.failure("The company began moving; transfer cannot complete across a movement boundary.");
        String requiredFaction = KOMEAlliance.normalizeFactionKey(company.faction);
        String recipientFaction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(recipient));
        if (requiredFaction.length() == 0 || !requiredFaction.equals(recipientFaction)) {
            return Result.failure("Transfer rejected: recipient is not currently pledged to the company's required faction "
                + KOMEAlliance.displayFactionName(requiredFaction) + ".");
        }
        List<KOMEHiredUnitRecord> records = new ArrayList<KOMEHiredUnitRecord>();
        Map<String, Integer> allocationNeeds = new HashMap<String, Integer>();
        Map<String, Integer> formerAllocationUses = new HashMap<String, Integer>();
        Map<KOMEPopulationType, Integer> reserveNeeds = new HashMap<KOMEPopulationType, Integer>();
        Map<String, Integer> formerReserveUses = new HashMap<String, Integer>();
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null || !company.owner.equals(record.owner)) return Result.failure("Transfer rejected: a company unit record is missing or has a different owner.");
            if (record.isMoving()) return Result.failure("Transfer rejected: unit " + unitId + " is crossing a movement boundary.");
            if (KOMEHiredUnitRecord.SOURCE_OTHER_LEGACY.equals(record.sourceType)
                    || KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)) {
                return Result.failure("Transfer rejected: unit " + unitId + " has non-transferable or quarantined provenance " + record.sourceType + ".");
            }
            records.add(record);
            if (record.isFactionPopulationBankFunded()) {
                // Ownership changes without moving permanently-spent canonical population.
            } else if (record.isPlayerReserveFunded()) {
                add(reserveNeeds, record.type, record.cost);
                UUID formerSource = record.sourcePlayer == null ? company.owner : record.sourcePlayer;
                add(formerReserveUses, reserveKey(formerSource, record.type), record.cost);
            } else if (record.allocationPlayer != null || KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION.equals(record.sourceType)) {
                String key = allocationKey(record.allocationTileId, record.allocationFaction, record.type);
                add(allocationNeeds, key, record.cost);
                UUID formerAllocationPlayer = record.allocationPlayer == null ? company.owner : record.allocationPlayer;
                add(formerAllocationUses, allocationOwnerKey(record.allocationTileId, record.allocationFaction,
                    formerAllocationPlayer, record.type), record.cost);
            } else if (data.getFundingPool(record) == null) {
                return Result.failure("Transfer rejected: exact tile-pool source is missing for unit " + unitId + ".");
            }
        }
        for (Map.Entry<KOMEPopulationType, Integer> entry : reserveNeeds.entrySet()) {
            if (data.getPopulation(recipient).getAvailable(entry.getKey()) < entry.getValue().intValue()) {
                return Result.failure("Recipient lacks " + entry.getValue() + " available " + entry.getKey().key + " reserve population.");
            }
        }
        for (Map.Entry<String, Integer> entry : formerReserveUses.entrySet()) {
            ReserveKey key = reserveKey(entry.getKey());
            if (key.player == null || data.getPopulation(key.player).getUsed(key.type) < entry.getValue().intValue()) {
                return Result.failure("Transfer rejected: the former owner's exact " + key.type.key
                    + " reserve usage cannot be proven.");
            }
        }
        for (Map.Entry<String, Integer> entry : allocationNeeds.entrySet()) {
            AllocationKey key = allocationKey(entry.getKey());
            KOMEPlayerTilePopulationAllocation allocation = data.getAllocation(key.tile, key.faction, recipient);
            if (allocation == null || allocation.getAvailable(key.type) < entry.getValue().intValue()) {
                return Result.failure("Recipient lacks " + entry.getValue() + " available " + key.type.key
                    + " allocation at " + key.tile + " for " + KOMEAlliance.displayFactionName(key.faction) + ".");
            }
        }
        for (Map.Entry<String, Integer> entry : formerAllocationUses.entrySet()) {
            AllocationOwnerKey key = allocationOwnerKey(entry.getKey());
            KOMEPlayerTilePopulationAllocation allocation = key.player == null ? null
                : data.getAllocation(key.tile, key.faction, key.player);
            if (allocation == null || allocation.getUsed(key.type) < entry.getValue().intValue()) {
                return Result.failure("Transfer rejected: the former owner's exact " + key.type.key
                    + " allocation usage at " + key.tile + " cannot be proven.");
            }
        }

        // Debit every recipient source before releasing any former-owner source. Preconditions make this atomic.
        for (Map.Entry<KOMEPopulationType, Integer> entry : reserveNeeds.entrySet()) {
            data.getPopulation(recipient).tryUse(entry.getKey(), entry.getValue().intValue());
        }
        for (Map.Entry<String, Integer> entry : allocationNeeds.entrySet()) {
            AllocationKey key = allocationKey(entry.getKey());
            data.getAllocation(key.tile, key.faction, recipient).tryUse(key.type, entry.getValue().intValue());
        }

        UUID formerOwner = company.owner;
        for (KOMEHiredUnitRecord record : records) {
            if (record.isPlayerReserveFunded()) {
                UUID oldSource = record.sourcePlayer == null ? formerOwner : record.sourcePlayer;
                data.getPopulation(oldSource).release(record.type, record.cost);
                record.sourcePlayer = recipient;
            } else if (record.allocationPlayer != null || KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION.equals(record.sourceType)) {
                KOMEPlayerTilePopulationAllocation oldAllocation = data.getAllocation(record.allocationTileId,
                    record.allocationFaction, record.allocationPlayer == null ? formerOwner : record.allocationPlayer);
                if (oldAllocation != null) oldAllocation.release(record.type, record.cost);
                record.sourceType = KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION;
                record.allocationPlayer = recipient;
            }
            record.owner = recipient;
            record.controller = recipient;
            record.companyAssignedBy = recipient;
            record.companyAssignedByName = recipientName == null ? "" : recipientName;
            updateSnapshotOwner(record.stationedEntityData, recipient);
            updateSnapshotOwner(record.movingEntityData, recipient);
        }
        company.owner = recipient;
        company.ownerName = recipientName == null ? "" : recipientName;
        company.clearTemporaryController("Permanent company transfer completed");
        company.clearTransferOffer();
        company.updatedAtMillis = nowMillis;
        data.markDirty();
        return Result.success("Permanent transfer completed for all " + records.size() + " unit records. Company faction and population-source faction are unchanged.");
    }

    private static void updateSnapshotOwner(NBTTagCompound snapshot, UUID recipient) {
        if (snapshot == null || recipient == null || !snapshot.hasKey("HiredNPCInfo", 10)) return;
        NBTTagCompound info = snapshot.getCompoundTag("HiredNPCInfo");
        info.setString("HiringPlayerUUID", recipient.toString());
        info.removeTag("HiringPlayerName");
    }

    private static String allocationKey(String tile, String faction, KOMEPopulationType type) {
        return KOMEConquestTile.normalizeId(tile) + "|" + KOMEAlliance.normalizeFactionKey(faction) + "|" + type.key;
    }

    private static String allocationOwnerKey(String tile, String faction, UUID player, KOMEPopulationType type) {
        return KOMEConquestTile.normalizeId(tile) + "|" + KOMEAlliance.normalizeFactionKey(faction) + "|"
            + (player == null ? "" : player.toString()) + "|" + type.key;
    }

    private static AllocationOwnerKey allocationOwnerKey(String value) {
        String[] parts = value.split("\\|", -1);
        AllocationOwnerKey key = new AllocationOwnerKey();
        key.tile = parts.length > 0 ? parts[0] : "";
        key.faction = parts.length > 1 ? parts[1] : "";
        try { key.player = parts.length > 2 && parts[2].length() > 0 ? UUID.fromString(parts[2]) : null; }
        catch (IllegalArgumentException ignored) { key.player = null; }
        KOMEPopulationType type = parts.length > 3 ? KOMEPopulationType.forName(parts[3]) : null;
        key.type = type == null ? KOMEPopulationType.OFFENSIVE : type;
        return key;
    }

    private static String reserveKey(UUID player, KOMEPopulationType type) {
        return (player == null ? "" : player.toString()) + "|" + type.key;
    }

    private static ReserveKey reserveKey(String value) {
        String[] parts = value.split("\\|", -1);
        ReserveKey key = new ReserveKey();
        try { key.player = parts.length > 0 && parts[0].length() > 0 ? UUID.fromString(parts[0]) : null; }
        catch (IllegalArgumentException ignored) { key.player = null; }
        KOMEPopulationType type = parts.length > 1 ? KOMEPopulationType.forName(parts[1]) : null;
        key.type = type == null ? KOMEPopulationType.OFFENSIVE : type;
        return key;
    }

    private static AllocationKey allocationKey(String value) {
        String[] parts = value.split("\\|", -1);
        AllocationKey key = new AllocationKey();
        key.tile = parts.length > 0 ? parts[0] : "";
        key.faction = parts.length > 1 ? parts[1] : "";
        KOMEPopulationType type = parts.length > 2 ? KOMEPopulationType.forName(parts[2]) : null;
        key.type = type == null ? KOMEPopulationType.OFFENSIVE : type;
        return key;
    }

    private static <K> void add(Map<K, Integer> values, K key, int amount) {
        Integer old = values.get(key);
        values.put(key, Integer.valueOf((old == null ? 0 : old.intValue()) + Math.max(0, amount)));
    }

    private static class AllocationKey {
        String tile;
        String faction;
        KOMEPopulationType type;
    }

    private static class AllocationOwnerKey extends AllocationKey {
        UUID player;
    }

    private static class ReserveKey {
        UUID player;
        KOMEPopulationType type;
    }

    public static class Result {
        public final boolean success;
        public final String message;
        private Result(boolean success, String message) { this.success = success; this.message = message; }
        public static Result success(String message) { return new Result(true, message); }
        public static Result failure(String message) { return new Result(false, message); }
    }
}
