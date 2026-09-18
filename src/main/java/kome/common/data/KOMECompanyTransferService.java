package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;
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
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null || !company.owner.equals(record.owner)) return Result.failure("Transfer rejected: a company unit record is missing or has a different owner.");
            if (record.isMoving()) return Result.failure("Transfer rejected: unit " + unitId + " is crossing a movement boundary.");
            if (!record.isFactionPopulationBankFunded()) {
                return Result.failure("Transfer rejected: unit " + unitId + " does not use canonical faction-bank provenance.");
            }
            records.add(record);
            // Ownership changes without moving permanently-spent canonical population.
        }
        UUID formerOwner = company.owner;
        if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                && company.temporaryController != null) {
            data.recordCompanyDelegationAudit(
                nowMillis, "REVOKED_TRANSFER", company,
                recipient, recipientName,
                company.temporaryController, company.temporaryControllerName,
                "Permanent company transfer completed");
        }
        for (KOMEHiredUnitRecord record : records) {
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

    public static class Result {
        public final boolean success;
        public final String message;
        private Result(boolean success, String message) { this.success = success; this.message = message; }
        public static Result success(String message) { return new Result(true, message); }
        public static Result failure(String message) { return new Result(false, message); }
    }
}
