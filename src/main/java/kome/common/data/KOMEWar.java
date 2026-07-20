package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Persisted, server-authoritative two-coalition war record. */
public class KOMEWar {
    public static final String ACTIVE = "ACTIVE";
    public static final String ENDING = "ENDING";
    public static final String ENDED = "ENDED";
    public static final int MAX_HISTORY = 250;

    public String id = "";
    public String displayName = "";
    public String status = ACTIVE;
    public String sideOneName = "Side One";
    public String sideTwoName = "Side Two";
    public final Set<String> sideOneFactions = new LinkedHashSet<String>();
    public final Set<String> sideTwoFactions = new LinkedHashSet<String>();
    public long createdAtMillis;
    public long endingAtMillis;
    public long endedAtMillis;
    public long lastUpdatedAtMillis;
    public String endingReason = "";
    public boolean cancelled;
    public final List<TileCaptureEvent> tileCaptureHistory = new ArrayList<TileCaptureEvent>();
    public final List<AdministrativeEvent> administrativeHistory = new ArrayList<AdministrativeEvent>();
    public final List<MembershipRecord> membershipHistory = new ArrayList<MembershipRecord>();
    public final List<MilitarySupportEnrollment> militarySupportEnrollments = new ArrayList<MilitarySupportEnrollment>();
    public final List<StewardshipAuthorization> stewardshipAuthorizations = new ArrayList<StewardshipAuthorization>();

    public boolean isActive() {
        return ACTIVE.equals(status);
    }

    public boolean isEnding() {
        return ENDING.equals(status);
    }

    public boolean isEnded() {
        return ENDED.equals(status);
    }

    public int sideOf(String faction) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (sideOneFactions.contains(key)) return 1;
        if (sideTwoFactions.contains(key)) return 2;
        return 0;
    }

    public boolean opposes(String first, String second) {
        int a = sideOf(first);
        int b = sideOf(second);
        return a > 0 && b > 0 && a != b;
    }

    public boolean sameSide(String first, String second) {
        int a = sideOf(first);
        return a > 0 && a == sideOf(second);
    }

    public Set<String> getSide(int side) {
        return side == 1 ? sideOneFactions : sideTwoFactions;
    }

    public Set<String> getOpposingFactions(String faction) {
        int side = sideOf(faction);
        return side == 1 ? new LinkedHashSet<String>(sideTwoFactions)
            : side == 2 ? new LinkedHashSet<String>(sideOneFactions) : new LinkedHashSet<String>();
    }

    public boolean addFaction(int side, String faction) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (key.length() == 0 || side != 1 && side != 2 || sideOf(key) != 0) return false;
        return getSide(side).add(key);
    }

    public void recordMembership(String faction, int side, String source, String nativeFaction,
            String actor, long timestamp) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);
        String sourceKey = safe(source).trim().toUpperCase(java.util.Locale.ROOT);
        for (MembershipRecord record : membershipHistory) {
            if (record.active && record.side == side && record.faction.equals(key)
                    && record.source.equals(sourceKey) && record.nativeFaction.equals(nativeKey)) return;
        }
        MembershipRecord record = new MembershipRecord();
        record.faction = key;
        record.side = side;
        record.source = sourceKey.length() == 0 ? "MANUAL" : sourceKey;
        record.nativeFaction = nativeKey;
        record.actor = safe(actor);
        record.addedAtMillis = Math.max(0L, timestamp);
        record.active = true;
        membershipHistory.add(record);
        trim(membershipHistory);
    }

    public void endMembership(String faction, String reason, long timestamp) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        for (MembershipRecord record : membershipHistory) {
            if (record.active && record.faction.equals(key)) {
                record.active = false;
                record.endedAtMillis = Math.max(0L, timestamp);
                record.endReason = safe(reason);
            }
        }
    }

    public MilitarySupportEnrollment supportEnrollment(String nativeFaction, String supportingFaction, boolean create) {
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);
        String supportingKey = KOMEAlliance.normalizeFactionKey(supportingFaction);
        for (MilitarySupportEnrollment enrollment : militarySupportEnrollments) {
            if (enrollment.nativeFaction.equals(nativeKey) && enrollment.supportingFaction.equals(supportingKey)) return enrollment;
        }
        if (!create) return null;
        MilitarySupportEnrollment enrollment = new MilitarySupportEnrollment();
        enrollment.nativeFaction = nativeKey;
        enrollment.supportingFaction = supportingKey;
        militarySupportEnrollments.add(enrollment);
        return enrollment;
    }

    public boolean removeFaction(String faction) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        return sideOneFactions.remove(key) || sideTwoFactions.remove(key);
    }

    public boolean moveFaction(String faction, int side) {
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (key.length() == 0 || side != 1 && side != 2) return false;
        int old = sideOf(key);
        if (old == 0 || old == side) return false;
        getSide(old).remove(key);
        getSide(side).add(key);
        return true;
    }

    public void addTileCapture(String tileId, String formerOwner, String newOwner, UUID claimant,
            String claimantName, long timestamp, String claimMethod) {
        TileCaptureEvent event = new TileCaptureEvent();
        event.tileId = KOMEConquestTile.normalizeId(tileId);
        event.formerOwner = KOMEAlliance.normalizeFactionKey(formerOwner);
        event.newOwner = KOMEAlliance.normalizeFactionKey(newOwner);
        event.claimant = claimant;
        event.claimantName = safe(claimantName);
        event.timestamp = Math.max(0L, timestamp);
        event.claimMethod = safe(claimMethod);
        tileCaptureHistory.add(event);
        trim(tileCaptureHistory);
        lastUpdatedAtMillis = Math.max(lastUpdatedAtMillis, event.timestamp);
    }

    public void addAdministrativeEvent(String actor, String action, String detail, long timestamp) {
        AdministrativeEvent event = new AdministrativeEvent();
        event.actor = safe(actor);
        event.action = safe(action);
        event.detail = safe(detail);
        event.timestamp = Math.max(0L, timestamp);
        administrativeHistory.add(event);
        trim(administrativeHistory);
        lastUpdatedAtMillis = Math.max(lastUpdatedAtMillis, event.timestamp);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", safe(id));
        nbt.setString("DisplayName", safe(displayName));
        nbt.setString("Status", normalizedStatus(status));
        nbt.setString("SideOneName", safeName(sideOneName, "Side One"));
        nbt.setString("SideTwoName", safeName(sideTwoName, "Side Two"));
        nbt.setTag("SideOneFactions", writeStrings(sideOneFactions));
        nbt.setTag("SideTwoFactions", writeStrings(sideTwoFactions));
        nbt.setLong("CreatedAtMillis", createdAtMillis);
        nbt.setLong("EndingAtMillis", endingAtMillis);
        nbt.setLong("EndedAtMillis", endedAtMillis);
        nbt.setLong("LastUpdatedAtMillis", lastUpdatedAtMillis);
        nbt.setString("EndingReason", safe(endingReason));
        nbt.setBoolean("Cancelled", cancelled);
        NBTTagList captures = new NBTTagList();
        for (TileCaptureEvent event : tileCaptureHistory) captures.appendTag(event.writeToNBT());
        nbt.setTag("TileCaptureHistory", captures);
        NBTTagList admin = new NBTTagList();
        for (AdministrativeEvent event : administrativeHistory) admin.appendTag(event.writeToNBT());
        nbt.setTag("AdministrativeHistory", admin);
        NBTTagList memberships = new NBTTagList();
        for (MembershipRecord membership : membershipHistory) memberships.appendTag(membership.writeToNBT());
        nbt.setTag("MembershipHistory", memberships);
        NBTTagList support = new NBTTagList();
        for (MilitarySupportEnrollment enrollment : militarySupportEnrollments) support.appendTag(enrollment.writeToNBT());
        nbt.setTag("MilitarySupportEnrollments", support);
        NBTTagList authorizations = new NBTTagList();
        for (StewardshipAuthorization authorization : stewardshipAuthorizations) {
            authorizations.appendTag(authorization.writeToNBT());
        }
        nbt.setTag("StewardshipAuthorizations", authorizations);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = nbt.getString("Id");
        displayName = nbt.getString("DisplayName");
        status = normalizedStatus(nbt.getString("Status"));
        sideOneName = safeName(nbt.getString("SideOneName"), "Side One");
        sideTwoName = safeName(nbt.getString("SideTwoName"), "Side Two");
        sideOneFactions.clear();
        sideTwoFactions.clear();
        readStrings(nbt.getTagList("SideOneFactions", 10), sideOneFactions);
        readStrings(nbt.getTagList("SideTwoFactions", 10), sideTwoFactions);
        // Deterministically reject a corrupt duplicate rather than allowing a faction on both sides.
        sideTwoFactions.removeAll(sideOneFactions);
        createdAtMillis = Math.max(0L, nbt.getLong("CreatedAtMillis"));
        endingAtMillis = Math.max(0L, nbt.getLong("EndingAtMillis"));
        endedAtMillis = Math.max(0L, nbt.getLong("EndedAtMillis"));
        lastUpdatedAtMillis = Math.max(createdAtMillis, nbt.getLong("LastUpdatedAtMillis"));
        endingReason = nbt.getString("EndingReason");
        cancelled = nbt.getBoolean("Cancelled");
        tileCaptureHistory.clear();
        NBTTagList captures = nbt.getTagList("TileCaptureHistory", 10);
        for (int i = 0; i < captures.tagCount(); i++) {
            TileCaptureEvent event = new TileCaptureEvent();
            event.readFromNBT(captures.getCompoundTagAt(i));
            tileCaptureHistory.add(event);
        }
        trim(tileCaptureHistory);
        administrativeHistory.clear();
        NBTTagList admin = nbt.getTagList("AdministrativeHistory", 10);
        for (int i = 0; i < admin.tagCount(); i++) {
            AdministrativeEvent event = new AdministrativeEvent();
            event.readFromNBT(admin.getCompoundTagAt(i));
            administrativeHistory.add(event);
        }
        trim(administrativeHistory);
        membershipHistory.clear();
        NBTTagList memberships = nbt.getTagList("MembershipHistory", 10);
        for (int i = 0; i < memberships.tagCount(); i++) {
            MembershipRecord membership = new MembershipRecord();
            membership.readFromNBT(memberships.getCompoundTagAt(i));
            if (membership.faction.length() > 0 && membership.side >= 1 && membership.side <= 2) membershipHistory.add(membership);
        }
        if (membershipHistory.isEmpty()) {
            for (String faction : sideOneFactions) recordMembership(faction, 1, "LEGACY", "", "migration", createdAtMillis);
            for (String faction : sideTwoFactions) recordMembership(faction, 2, "LEGACY", "", "migration", createdAtMillis);
        }
        militarySupportEnrollments.clear();
        NBTTagList support = nbt.getTagList("MilitarySupportEnrollments", 10);
        for (int i = 0; i < support.tagCount(); i++) {
            MilitarySupportEnrollment enrollment = new MilitarySupportEnrollment();
            enrollment.readFromNBT(support.getCompoundTagAt(i));
            if (enrollment.nativeFaction.length() > 0 && enrollment.supportingFaction.length() > 0) militarySupportEnrollments.add(enrollment);
        }
        stewardshipAuthorizations.clear();
        NBTTagList authorizations = nbt.getTagList("StewardshipAuthorizations", 10);
        for (int i = 0; i < authorizations.tagCount(); i++) {
            StewardshipAuthorization authorization = new StewardshipAuthorization();
            authorization.readFromNBT(authorizations.getCompoundTagAt(i));
            if (authorization.companyId.length() > 0) stewardshipAuthorizations.add(authorization);
        }
    }

    private static NBTTagList writeStrings(Set<String> values) {
        NBTTagList result = new NBTTagList();
        for (String value : values) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Value", KOMEAlliance.normalizeFactionKey(value));
            result.appendTag(entry);
        }
        return result;
    }

    private static void readStrings(NBTTagList list, Set<String> destination) {
        for (int i = 0; i < list.tagCount(); i++) {
            String value = KOMEAlliance.normalizeFactionKey(list.getCompoundTagAt(i).getString("Value"));
            if (value.length() > 0) destination.add(value);
        }
    }

    private static <T> void trim(List<T> values) {
        while (values.size() > MAX_HISTORY) values.remove(0);
    }

    private static String normalizedStatus(String value) {
        return ENDING.equals(value) ? ENDING : ENDED.equals(value) ? ENDED : ACTIVE;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeName(String value, String fallback) {
        String result = safe(value).trim();
        return result.length() == 0 ? fallback : result;
    }

    private static UUID parseUuid(String value) {
        try { return value == null || value.length() == 0 ? null : UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static class TileCaptureEvent {
        public String tileId = "";
        public String formerOwner = "";
        public String newOwner = "";
        public UUID claimant;
        public String claimantName = "";
        public long timestamp;
        public String claimMethod = "";

        public NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("TileId", safe(tileId));
            nbt.setString("FormerOwner", safe(formerOwner));
            nbt.setString("NewOwner", safe(newOwner));
            nbt.setString("Claimant", claimant == null ? "" : claimant.toString());
            nbt.setString("ClaimantName", safe(claimantName));
            nbt.setLong("Timestamp", timestamp);
            nbt.setString("ClaimMethod", safe(claimMethod));
            return nbt;
        }

        public void readFromNBT(NBTTagCompound nbt) {
            tileId = KOMEConquestTile.normalizeId(nbt.getString("TileId"));
            formerOwner = KOMEAlliance.normalizeFactionKey(nbt.getString("FormerOwner"));
            newOwner = KOMEAlliance.normalizeFactionKey(nbt.getString("NewOwner"));
            claimant = parseUuid(nbt.getString("Claimant"));
            claimantName = nbt.getString("ClaimantName");
            timestamp = Math.max(0L, nbt.getLong("Timestamp"));
            claimMethod = nbt.getString("ClaimMethod");
        }
    }

    public static class AdministrativeEvent {
        public String actor = "";
        public String action = "";
        public String detail = "";
        public long timestamp;

        public NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("Actor", safe(actor));
            nbt.setString("Action", safe(action));
            nbt.setString("Detail", safe(detail));
            nbt.setLong("Timestamp", timestamp);
            return nbt;
        }

        public void readFromNBT(NBTTagCompound nbt) {
            actor = nbt.getString("Actor");
            action = nbt.getString("Action");
            detail = nbt.getString("Detail");
            timestamp = Math.max(0L, nbt.getLong("Timestamp"));
        }
    }

    public static class StewardshipAuthorization {
        public String companyId = "";
        public String nativeFaction = "";
        public String controllerFaction = "";
        public UUID controller;
        public String controllerName = "";
        public int reservation;
        public String state = "ACTIVE";
        public String dormantReason = "";
        public final List<String> revocationHistory = new ArrayList<String>();
        public long createdAtMillis;
        public long updatedAtMillis;

        public NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("CompanyId", safe(companyId));
            nbt.setString("NativeFaction", KOMEAlliance.normalizeFactionKey(nativeFaction));
            nbt.setString("ControllerFaction", KOMEAlliance.normalizeFactionKey(controllerFaction));
            nbt.setString("Controller", controller == null ? "" : controller.toString());
            nbt.setString("ControllerName", safe(controllerName));
            nbt.setInteger("Reservation", Math.max(0, reservation));
            nbt.setString("State", safe(state));
            nbt.setString("DormantReason", safe(dormantReason));
            NBTTagList revocations = new NBTTagList();
            for (String value : revocationHistory) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Value", safe(value));
                revocations.appendTag(entry);
            }
            nbt.setTag("RevocationHistory", revocations);
            nbt.setLong("CreatedAtMillis", createdAtMillis);
            nbt.setLong("UpdatedAtMillis", updatedAtMillis);
            return nbt;
        }

        public void readFromNBT(NBTTagCompound nbt) {
            companyId = nbt.getString("CompanyId");
            nativeFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("NativeFaction"));
            controllerFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("ControllerFaction"));
            controller = parseUuid(nbt.getString("Controller"));
            controllerName = nbt.getString("ControllerName");
            reservation = Math.max(0, nbt.getInteger("Reservation"));
            state = nbt.getString("State");
            dormantReason = nbt.getString("DormantReason");
            revocationHistory.clear();
            NBTTagList revocations = nbt.getTagList("RevocationHistory", 10);
            for (int i = 0; i < revocations.tagCount(); i++) revocationHistory.add(revocations.getCompoundTagAt(i).getString("Value"));
            createdAtMillis = Math.max(0L, nbt.getLong("CreatedAtMillis"));
            updatedAtMillis = Math.max(0L, nbt.getLong("UpdatedAtMillis"));
        }
    }

    public static class MembershipRecord {
        public String faction = "";
        public int side;
        public String source = "MANUAL";
        public String nativeFaction = "";
        public String actor = "";
        public long addedAtMillis;
        public boolean active = true;
        public long endedAtMillis;
        public String endReason = "";

        NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("Faction", faction);
            nbt.setInteger("Side", side);
            nbt.setString("Source", source);
            nbt.setString("NativeFaction", nativeFaction);
            nbt.setString("Actor", actor);
            nbt.setLong("AddedAtMillis", addedAtMillis);
            nbt.setBoolean("Active", active);
            nbt.setLong("EndedAtMillis", endedAtMillis);
            nbt.setString("EndReason", endReason);
            return nbt;
        }

        void readFromNBT(NBTTagCompound nbt) {
            faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
            side = nbt.getInteger("Side");
            source = nbt.getString("Source");
            nativeFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("NativeFaction"));
            actor = nbt.getString("Actor");
            addedAtMillis = Math.max(0L, nbt.getLong("AddedAtMillis"));
            active = !nbt.hasKey("Active") || nbt.getBoolean("Active");
            endedAtMillis = Math.max(0L, nbt.getLong("EndedAtMillis"));
            endReason = nbt.getString("EndReason");
        }
    }

    public static class MilitarySupportEnrollment {
        public String nativeFaction = "";
        public String supportingFaction = "";
        public int side;
        public String state = "DORMANT";
        public UUID authorizedKing;
        public String authorizedKingName = "";
        public String reason = "";
        public long enrolledAtMillis;
        public long updatedAtMillis;

        NBTTagCompound writeToNBT() {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setString("NativeFaction", nativeFaction);
            nbt.setString("SupportingFaction", supportingFaction);
            nbt.setInteger("Side", side);
            nbt.setString("State", state);
            nbt.setString("AuthorizedKing", authorizedKing == null ? "" : authorizedKing.toString());
            nbt.setString("AuthorizedKingName", authorizedKingName);
            nbt.setString("Reason", reason);
            nbt.setLong("EnrolledAtMillis", enrolledAtMillis);
            nbt.setLong("UpdatedAtMillis", updatedAtMillis);
            return nbt;
        }

        void readFromNBT(NBTTagCompound nbt) {
            nativeFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("NativeFaction"));
            supportingFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("SupportingFaction"));
            side = nbt.getInteger("Side");
            state = nbt.getString("State");
            authorizedKing = parseUuid(nbt.getString("AuthorizedKing"));
            authorizedKingName = nbt.getString("AuthorizedKingName");
            reason = nbt.getString("Reason");
            enrolledAtMillis = Math.max(0L, nbt.getLong("EnrolledAtMillis"));
            updatedAtMillis = Math.max(0L, nbt.getLong("UpdatedAtMillis"));
        }
    }
}
