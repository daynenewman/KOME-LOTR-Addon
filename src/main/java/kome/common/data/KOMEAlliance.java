package kome.common.data;

import lotr.common.fac.LOTRFaction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.text.Normalizer;

public class KOMEAlliance {
    public static final String CIVIL = "civil";
    public static final String MILITARY = "military";
    public static final String TRADE = "trade";
    public static final int STORAGE_SLOTS = 9;
    public static final int NONE = -1;
    public static final int PENDING = -2;
    public static final int DATA_SCHEMA_VERSION = 5;

    public String factionA = "";
    public String factionB = "";

    /**
     * Compatibility fields for the existing GUI and command code. Status is persisted separately;
     * NONE/PENDING values here are synchronized at every model boundary.
     */
    public int civilTier = NONE;
    public int militaryTier = NONE;
    public int tradeTier = NONE;
    public String lastUpdatedBy = "";
    public long updatedWorldTime;
    public long updatedRealTimeMillis;

    private KOMEAllianceTrackStatus civilStatus = KOMEAllianceTrackStatus.NONE;
    private KOMEAllianceTrackStatus militaryStatus = KOMEAllianceTrackStatus.NONE;
    private KOMEAllianceTrackStatus tradeStatus = KOMEAllianceTrackStatus.NONE;
    private String civilRequestedBy = "";
    private String civilPendingReceiver = "";
    private String tradeRequestedBy = "";
    private String tradePendingReceiver = "";
    private String militaryRequestedBy = "";
    private String militaryPendingReceiver = "";
    private KOMEAllianceFactionLedger ledgerA;
    private KOMEAllianceFactionLedger ledgerB;
    private String loadedSourceFaction = "";

    public KOMEAlliance(String firstFaction, String secondFaction) {
        setIdentity(firstFaction, secondFaction);
    }

    public String getPairKey() {
        return pairKey(factionA, factionB);
    }

    public boolean involves(String faction) {
        String key = normalizeFactionKey(faction);
        return key.length() > 0 && (key.equals(factionA) || key.equals(factionB));
    }

    public String getOtherFaction(String faction) {
        String key = normalizeFactionKey(faction);
        if (key.equals(factionA)) {
            return factionB;
        }
        if (key.equals(factionB)) {
            return factionA;
        }
        return "";
    }

    public String getLoadedSourceFaction() {
        return loadedSourceFaction;
    }

    public KOMEAllianceFactionLedger getFactionLedger(String faction) {
        String key = normalizeFactionKey(faction);
        if (ledgerA != null && key.equals(ledgerA.faction)) {
            return ledgerA;
        }
        if (ledgerB != null && key.equals(ledgerB.faction)) {
            return ledgerB;
        }
        return null;
    }

    public int getTier(String type) {
        syncStatusFromCompatibilityFields();
        String normalizedType = normalizeType(type);
        if (CIVIL.equals(normalizedType)) {
            return civilTier;
        }
        if (MILITARY.equals(normalizedType)) {
            return militaryTier;
        }
        if (TRADE.equals(normalizedType)) {
            return tradeTier;
        }
        return NONE;
    }

    public KOMEAllianceTrackStatus getStatus(String type) {
        syncStatusFromCompatibilityFields();
        String normalizedType = normalizeType(type);
        if (CIVIL.equals(normalizedType)) {
            return civilStatus;
        }
        if (MILITARY.equals(normalizedType)) {
            return militaryStatus;
        }
        if (TRADE.equals(normalizedType)) {
            return tradeStatus;
        }
        return KOMEAllianceTrackStatus.NONE;
    }

    public void setTier(String type, int tier, String updatedBy, long worldTime) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType)) {
            return;
        }
        KOMEAllianceTrackStatus status = KOMEAllianceTrackStatus.fromLegacyTier(tier);
        setTrack(normalizedType, status, tier, updatedBy, worldTime, System.currentTimeMillis());
        ensureHierarchy();
    }

    public void setTrack(String type, KOMEAllianceTrackStatus status, int tier, String updatedBy,
            long worldTime, long realTimeMillis) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType)) {
            return;
        }
        KOMEAllianceTrackStatus safeStatus = status == null ? KOMEAllianceTrackStatus.NONE : status;
        int compatibilityTier = compatibilityTier(normalizedType, safeStatus, tier);
        if (CIVIL.equals(normalizedType)) {
            civilStatus = safeStatus;
            civilTier = compatibilityTier;
        } else if (MILITARY.equals(normalizedType)) {
            militaryStatus = safeStatus;
            militaryTier = compatibilityTier;
        } else {
            tradeStatus = safeStatus;
            tradeTier = compatibilityTier;
        }
        if (safeStatus != KOMEAllianceTrackStatus.PENDING) {
            clearPendingParties(normalizedType);
        }
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = Math.max(0L, realTimeMillis);
    }

    public void requestTrack(String type, String updatedBy, long worldTime, boolean pending) {
        String normalizedType = normalizeType(type);
        KOMEAllianceTrackStatus status = pending ? KOMEAllianceTrackStatus.PENDING : KOMEAllianceTrackStatus.ACTIVE;
        if (MILITARY.equals(normalizedType)) {
            establishMinimum(CIVIL, status, updatedBy, worldTime);
            establishMinimum(TRADE, status, updatedBy, worldTime);
            setTrack(MILITARY, status, 0, updatedBy, worldTime, System.currentTimeMillis());
        } else if (TRADE.equals(normalizedType)) {
            establishMinimum(CIVIL, status, updatedBy, worldTime);
            setTrack(TRADE, status, 0, updatedBy, worldTime, System.currentTimeMillis());
        } else if (CIVIL.equals(normalizedType)) {
            setTrack(CIVIL, status, 0, updatedBy, worldTime, System.currentTimeMillis());
        }
        ensureHierarchy();
    }

    public void setPendingParties(String type, String requestedBy, String pendingReceiver) {
        String normalizedType = normalizeType(type);
        String requester = normalizeFactionKey(requestedBy);
        String receiver = normalizeFactionKey(pendingReceiver);
        if (MILITARY.equals(normalizedType)) {
            setPendingPartiesIfPending(CIVIL, requester, receiver);
            setPendingPartiesIfPending(TRADE, requester, receiver);
            setPendingPartiesIfPending(MILITARY, requester, receiver);
        } else if (TRADE.equals(normalizedType)) {
            setPendingPartiesIfPending(CIVIL, requester, receiver);
            setPendingPartiesIfPending(TRADE, requester, receiver);
        } else {
            setPendingPartiesIfPending(CIVIL, requester, receiver);
        }
    }

    public String getPendingReceiver(String type) {
        String normalizedType = normalizeType(type);
        if (CIVIL.equals(normalizedType)) {
            return civilPendingReceiver;
        }
        if (TRADE.equals(normalizedType)) {
            return tradePendingReceiver;
        }
        if (MILITARY.equals(normalizedType)) {
            return militaryPendingReceiver;
        }
        return "";
    }

    public String getRequestedBy(String type) {
        String normalizedType = normalizeType(type);
        if (CIVIL.equals(normalizedType)) {
            return civilRequestedBy;
        }
        if (TRADE.equals(normalizedType)) {
            return tradeRequestedBy;
        }
        if (MILITARY.equals(normalizedType)) {
            return militaryRequestedBy;
        }
        return "";
    }

    public boolean acceptTrack(String type, String updatedBy, long worldTime) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType) || getStatus(normalizedType) != KOMEAllianceTrackStatus.PENDING) {
            return false;
        }
        if (MILITARY.equals(normalizedType)) {
            activateIfPending(CIVIL, updatedBy, worldTime);
            activateIfPending(TRADE, updatedBy, worldTime);
            activateIfPending(MILITARY, updatedBy, worldTime);
        } else if (TRADE.equals(normalizedType)) {
            activateIfPending(CIVIL, updatedBy, worldTime);
            activateIfPending(TRADE, updatedBy, worldTime);
        } else {
            activateIfPending(CIVIL, updatedBy, worldTime);
        }
        ensureHierarchy();
        return true;
    }

    public boolean breakTrack(String type, String updatedBy, long worldTime) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType) || getStatus(normalizedType) == KOMEAllianceTrackStatus.NONE) {
            return false;
        }
        if (CIVIL.equals(normalizedType)) {
            setTrack(CIVIL, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
            setTrack(TRADE, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
            setTrack(MILITARY, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
        } else if (TRADE.equals(normalizedType)) {
            setTrack(TRADE, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
            setTrack(MILITARY, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
        } else {
            setTrack(MILITARY, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
        }
        return true;
    }

    public boolean hasAnyAlliance() {
        return getStatus(CIVIL) != KOMEAllianceTrackStatus.NONE
            || getStatus(TRADE) != KOMEAllianceTrackStatus.NONE
            || getStatus(MILITARY) != KOMEAllianceTrackStatus.NONE;
    }

    public boolean hasAnyAcceptedAlliance() {
        return getStatus(CIVIL) == KOMEAllianceTrackStatus.ACTIVE
            || getStatus(TRADE) == KOMEAllianceTrackStatus.ACTIVE
            || getStatus(MILITARY) == KOMEAllianceTrackStatus.ACTIVE;
    }

    public boolean hasAccepted(String type) {
        return getStatus(type) == KOMEAllianceTrackStatus.ACTIVE;
    }

    public boolean hasRecoverableGoods() {
        return ledgerA != null && ledgerA.hasStoredOrClaimableGoods()
            || ledgerB != null && ledgerB.hasStoredOrClaimableGoods();
    }

    public void clearAllTracks(String updatedBy, long worldTime) {
        setTrack(CIVIL, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
        setTrack(TRADE, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
        setTrack(MILITARY, KOMEAllianceTrackStatus.NONE, NONE, updatedBy, worldTime, System.currentTimeMillis());
    }

    public String getAssignment(String id) {
        return ledgerA == null ? "" : ledgerA.getAssignment(id);
    }

    public String getAssignment(String faction, String id) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? "" : ledger.getAssignment(id);
    }

    public void setAssignment(String id, String value) {
        if (ledgerA != null) {
            ledgerA.setAssignment(id, value);
        }
    }

    public void setAssignment(String faction, String id, String value) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.setAssignment(id, value);
        }
    }

    public int getDelivered(String id) {
        return ledgerA == null ? 0 : ledgerA.getDelivered(id);
    }

    public int getDelivered(String faction, String id) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? 0 : ledger.getDelivered(id);
    }

    public void addDelivered(String id, int amount) {
        if (ledgerA != null) {
            ledgerA.addDelivered(id, amount);
        }
    }

    public void addDelivered(String faction, String id, int amount) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.addDelivered(id, amount);
        }
    }

    public void setDelivered(String id, int amount) {
        if (ledgerA != null) {
            ledgerA.setDelivered(id, amount);
        }
    }

    public void setDelivered(String faction, String id, int amount) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.setDelivered(id, amount);
        }
    }

    public ItemStack getClaimSample(String id) {
        return ledgerA == null ? null : ledgerA.getClaimSample(id);
    }

    public ItemStack getClaimSample(String faction, String id) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? null : ledger.getClaimSample(id);
    }

    public int getClaimAmount(String id) {
        return ledgerA == null ? 0 : ledgerA.getClaimAmount(id);
    }

    public int getClaimAmount(String faction, String id) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? 0 : ledger.getClaimAmount(id);
    }

    public void addClaimGoods(String id, ItemStack sample, int amount) {
        if (ledgerA != null) {
            ledgerA.addClaimGoods(id, sample, amount);
        }
    }

    public void addClaimGoods(String faction, String id, ItemStack sample, int amount) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.addClaimGoods(id, sample, amount);
        }
    }

    public void clearClaimGoods(String id) {
        if (ledgerA != null) {
            ledgerA.clearClaimGoods(id);
        }
    }

    public void clearClaimGoods(String faction, String id) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.clearClaimGoods(id);
        }
    }

    public ItemStack getStorage(int slot) {
        return ledgerA == null ? null : ledgerA.getStorage(slot);
    }

    public ItemStack getStorage(String faction, int slot) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? null : ledger.getStorage(slot);
    }

    public void setStorage(int slot, ItemStack stack) {
        if (ledgerA != null) {
            ledgerA.setStorage(slot, stack);
        }
    }

    public void setStorage(String faction, int slot, ItemStack stack) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        if (ledger != null) {
            ledger.setStorage(slot, stack);
        }
    }

    public ItemStack decrStorage(int slot, int count) {
        return ledgerA == null ? null : ledgerA.decrStorage(slot, count);
    }

    public ItemStack decrStorage(String faction, int slot, int count) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(faction);
        return ledger == null ? null : ledger.decrStorage(slot, count);
    }

    public void mergeFrom(KOMEAlliance other, boolean combineStoredGoods) {
        if (other == null || !getPairKey().equals(other.getPairKey())) {
            return;
        }
        mergeTrack(CIVIL, other);
        mergeTrack(TRADE, other);
        mergeTrack(MILITARY, other);
        KOMEAllianceFactionLedger otherA = other.getFactionLedger(factionA);
        KOMEAllianceFactionLedger otherB = other.getFactionLedger(factionB);
        if (ledgerA != null && otherA != null) {
            ledgerA.mergeFrom(otherA, combineStoredGoods);
        }
        if (ledgerB != null && otherB != null) {
            ledgerB.mergeFrom(otherB, combineStoredGoods);
        }
        if (other.updatedRealTimeMillis > updatedRealTimeMillis
                || other.updatedRealTimeMillis == updatedRealTimeMillis && other.updatedWorldTime > updatedWorldTime) {
            lastUpdatedBy = other.lastUpdatedBy;
            updatedWorldTime = other.updatedWorldTime;
            updatedRealTimeMillis = other.updatedRealTimeMillis;
        }
        ensureHierarchy();
    }

    public void readFromNBT(NBTTagCompound nbt) {
        String sourceFaction = normalizeFactionKey(nbt.getString("FactionA"));
        String targetFaction = normalizeFactionKey(nbt.getString("FactionB"));
        loadedSourceFaction = sourceFaction;
        setIdentity(sourceFaction, targetFaction);
        int savedCivilTier = nbt.hasKey("CivilTier") ? nbt.getInteger("CivilTier") : NONE;
        int savedTradeTier = nbt.hasKey("TradeTier") ? nbt.getInteger("TradeTier") : NONE;
        int savedMilitaryTier = nbt.hasKey("MilitaryTier") ? nbt.getInteger("MilitaryTier") : NONE;
        civilStatus = nbt.hasKey("CivilStatus") ? KOMEAllianceTrackStatus.fromKey(nbt.getString("CivilStatus"))
            : KOMEAllianceTrackStatus.fromLegacyTier(savedCivilTier);
        tradeStatus = nbt.hasKey("TradeStatus") ? KOMEAllianceTrackStatus.fromKey(nbt.getString("TradeStatus"))
            : KOMEAllianceTrackStatus.fromLegacyTier(savedTradeTier);
        militaryStatus = nbt.hasKey("MilitaryStatus") ? KOMEAllianceTrackStatus.fromKey(nbt.getString("MilitaryStatus"))
            : KOMEAllianceTrackStatus.fromLegacyTier(savedMilitaryTier);
        civilTier = compatibilityTier(CIVIL, civilStatus, savedCivilTier);
        tradeTier = compatibilityTier(TRADE, tradeStatus, savedTradeTier);
        militaryTier = compatibilityTier(MILITARY, militaryStatus, savedMilitaryTier);
        civilRequestedBy = normalizeFactionKey(nbt.getString("CivilRequestedBy"));
        civilPendingReceiver = normalizeFactionKey(nbt.getString("CivilPendingReceiver"));
        tradeRequestedBy = normalizeFactionKey(nbt.getString("TradeRequestedBy"));
        tradePendingReceiver = normalizeFactionKey(nbt.getString("TradePendingReceiver"));
        militaryRequestedBy = normalizeFactionKey(nbt.getString("MilitaryRequestedBy"));
        militaryPendingReceiver = normalizeFactionKey(nbt.getString("MilitaryPendingReceiver"));
        if (civilStatus == KOMEAllianceTrackStatus.PENDING && civilPendingReceiver.length() == 0) {
            civilRequestedBy = sourceFaction;
            civilPendingReceiver = targetFaction;
        }
        if (tradeStatus == KOMEAllianceTrackStatus.PENDING && tradePendingReceiver.length() == 0) {
            tradeRequestedBy = sourceFaction;
            tradePendingReceiver = targetFaction;
        }
        if (militaryStatus == KOMEAllianceTrackStatus.PENDING && militaryPendingReceiver.length() == 0) {
            militaryRequestedBy = sourceFaction;
            militaryPendingReceiver = targetFaction;
        }
        lastUpdatedBy = nbt.getString("LastUpdatedBy");
        updatedWorldTime = Math.max(0L, nbt.getLong("UpdatedWorldTime"));
        updatedRealTimeMillis = Math.max(0L, nbt.getLong("UpdatedRealTimeMillis"));

        NBTTagList factionLedgers = nbt.getTagList("FactionLedgers", 10);
        if (factionLedgers.tagCount() > 0) {
            for (int i = 0; i < factionLedgers.tagCount(); i++) {
                KOMEAllianceFactionLedger loaded = new KOMEAllianceFactionLedger("");
                loaded.readFromNBT(factionLedgers.getCompoundTagAt(i));
                KOMEAllianceFactionLedger target = getFactionLedger(loaded.faction);
                if (target != null) {
                    target.mergeFrom(loaded, true);
                }
            }
        } else {
            readLegacyLedger(nbt, sourceFaction);
            KOMEAllianceFactionLedger sourceLedger = getFactionLedger(sourceFaction);
            if (sourceLedger != null) {
                if (civilStatus == KOMEAllianceTrackStatus.ACTIVE) {
                    sourceLedger.setCompletedTier(CIVIL, civilTier);
                }
                if (tradeStatus == KOMEAllianceTrackStatus.ACTIVE) {
                    sourceLedger.setCompletedTier(TRADE, tradeTier);
                }
                if (militaryStatus == KOMEAllianceTrackStatus.ACTIVE) {
                    sourceLedger.setCompletedTier(MILITARY, militaryTier);
                }
            }
        }
        ensureHierarchy();
    }

    public NBTTagCompound writeToNBT() {
        syncStatusFromCompatibilityFields();
        ensureHierarchy();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("SchemaVersion", DATA_SCHEMA_VERSION);
        nbt.setString("FactionA", factionA);
        nbt.setString("FactionB", factionB);
        nbt.setString("CivilStatus", civilStatus.key);
        nbt.setString("TradeStatus", tradeStatus.key);
        nbt.setString("MilitaryStatus", militaryStatus.key);
        nbt.setInteger("CivilTier", civilTier);
        nbt.setInteger("TradeTier", tradeTier);
        nbt.setInteger("MilitaryTier", militaryTier);
        nbt.setString("CivilRequestedBy", civilRequestedBy);
        nbt.setString("CivilPendingReceiver", civilPendingReceiver);
        nbt.setString("TradeRequestedBy", tradeRequestedBy);
        nbt.setString("TradePendingReceiver", tradePendingReceiver);
        nbt.setString("MilitaryRequestedBy", militaryRequestedBy);
        nbt.setString("MilitaryPendingReceiver", militaryPendingReceiver);
        nbt.setString("LastUpdatedBy", lastUpdatedBy == null ? "" : lastUpdatedBy);
        nbt.setLong("UpdatedWorldTime", Math.max(0L, updatedWorldTime));
        nbt.setLong("UpdatedRealTimeMillis", Math.max(0L, updatedRealTimeMillis));
        NBTTagList ledgers = new NBTTagList();
        if (ledgerA != null) {
            ledgers.appendTag(ledgerA.writeToNBT());
        }
        if (ledgerB != null) {
            ledgers.appendTag(ledgerB.writeToNBT());
        }
        nbt.setTag("FactionLedgers", ledgers);
        return nbt;
    }

    public void ensureHierarchy() {
        syncStatusFromCompatibilityFields();
        if (militaryStatus == KOMEAllianceTrackStatus.ACTIVE) {
            activateMinimum(CIVIL);
            activateMinimum(TRADE);
        } else if (militaryStatus == KOMEAllianceTrackStatus.PENDING) {
            pendingMinimum(CIVIL);
            pendingMinimum(TRADE);
        }
        if (tradeStatus == KOMEAllianceTrackStatus.ACTIVE) {
            activateMinimum(CIVIL);
        } else if (tradeStatus == KOMEAllianceTrackStatus.PENDING) {
            pendingMinimum(CIVIL);
        }
    }

    public static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase();
        if ("civ".equals(value)) {
            return CIVIL;
        }
        if ("mil".equals(value)) {
            return MILITARY;
        }
        return value;
    }

    public static boolean isValidType(String type) {
        String value = normalizeType(type);
        return CIVIL.equals(value) || MILITARY.equals(value) || TRADE.equals(value);
    }

    public static int maxTier(String type) {
        String value = normalizeType(type);
        if (MILITARY.equals(value)) {
            return 3;
        }
        if (CIVIL.equals(value) || TRADE.equals(value)) {
            return 2;
        }
        return NONE;
    }

    public static String pairKey(String firstFaction, String secondFaction) {
        String first = normalizeFactionKey(firstFaction);
        String second = normalizeFactionKey(secondFaction);
        if (first.compareTo(second) <= 0) {
            return first + "|" + second;
        }
        return second + "|" + first;
    }

    public static String directionKey(String senderFaction, String receiverFaction) {
        return normalizeFactionKey(senderFaction) + ">" + normalizeFactionKey(receiverFaction);
    }

    public static String normalizeFactionKey(String value) {
        String normalized = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String key = normalized.toLowerCase().replaceAll("[^a-z0-9]", "");
        if ("".equals(key) || "none".equals(key) || "neutral".equals(key) || "neutralzone".equals(key)
                || "unclaimed".equals(key) || "unaligned".equals(key)) {
            return "";
        }
        if ("hobbits".equals(key)) {
            return "hobbit";
        }
        if ("breeland".equals(key)) {
            return "bree";
        }
        if ("rangernorth".equals(key) || "rangersnorth".equals(key) || "rangerofthenorth".equals(key)
                || "rangersofthenorth".equals(key) || "dunedainnorth".equals(key)
                || "dunedainofthenorth".equals(key) || "northerndunedain".equals(key)) {
            return "dunedain";
        }
        if ("highelf".equals(key) || "highelves".equals(key) || "highelven".equals(key)
                || "lindon".equals(key) || "rivendell".equals(key) || "imladris".equals(key)) {
            return "highelves";
        }
        if ("nearharad".equals(key) || "harad".equals(key) || "haradwaith".equals(key)
                || "southron".equals(key) || "southrons".equals(key)) {
            return "harad";
        }
        if ("woodelf".equals(key) || "woodelves".equals(key) || "woodlandrealm".equals(key)
                || "mirkwoodelves".equals(key)) {
            return "woodelf";
        }
        return key;
    }

    public static LOTRFaction findLotrFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()
                && (normalizeFactionKey(faction.codeName()).equals(normalized)
                || normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
    }

    public static String displayFactionName(String key) {
        String normalized = normalizeFactionKey(key);
        if ("dunedain".equals(normalized)) {
            return "Dunedain";
        }
        if ("highelves".equals(normalized)) {
            return "High Elves";
        }
        if ("harad".equals(normalized)) {
            return "Harad";
        }
        LOTRFaction faction = findLotrFaction(key);
        if (faction != null) {
            return faction.factionName();
        }
        if (key == null || key.length() == 0) {
            return "No faction";
        }
        String value = key.replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private void setIdentity(String firstFaction, String secondFaction) {
        String first = normalizeFactionKey(firstFaction);
        String second = normalizeFactionKey(secondFaction);
        if (first.compareTo(second) <= 0) {
            factionA = first;
            factionB = second;
        } else {
            factionA = second;
            factionB = first;
        }
        ledgerA = new KOMEAllianceFactionLedger(factionA);
        ledgerB = new KOMEAllianceFactionLedger(factionB);
    }

    private void activateIfPending(String type, String updatedBy, long worldTime) {
        if (getStatus(type) == KOMEAllianceTrackStatus.PENDING) {
            setTrack(type, KOMEAllianceTrackStatus.ACTIVE, 0, updatedBy, worldTime, System.currentTimeMillis());
        }
    }

    private void establishMinimum(String type, KOMEAllianceTrackStatus status, String updatedBy, long worldTime) {
        if (getStatus(type) == KOMEAllianceTrackStatus.NONE) {
            setTrack(type, status, 0, updatedBy, worldTime, System.currentTimeMillis());
        }
    }

    private void activateMinimum(String type) {
        if (getStatusWithoutSync(type) != KOMEAllianceTrackStatus.ACTIVE) {
            assignTrack(type, KOMEAllianceTrackStatus.ACTIVE, 0);
        }
    }

    private void pendingMinimum(String type) {
        if (getStatusWithoutSync(type) == KOMEAllianceTrackStatus.NONE) {
            assignTrack(type, KOMEAllianceTrackStatus.PENDING, PENDING);
        }
    }

    private void mergeTrack(String type, KOMEAlliance other) {
        KOMEAllianceTrackStatus currentStatus = getStatus(type);
        KOMEAllianceTrackStatus otherStatus = other.getStatus(type);
        if (statusStrength(otherStatus) > statusStrength(currentStatus)) {
            assignTrack(type, otherStatus, other.getTier(type));
            copyPendingParties(type, other);
        } else if (currentStatus == KOMEAllianceTrackStatus.ACTIVE && otherStatus == KOMEAllianceTrackStatus.ACTIVE) {
            assignTrack(type, currentStatus, Math.max(getTier(type), other.getTier(type)));
        } else if (currentStatus == KOMEAllianceTrackStatus.PENDING && otherStatus == KOMEAllianceTrackStatus.PENDING
                && other.getPendingReceiver(type).compareTo(getPendingReceiver(type)) < 0) {
            copyPendingParties(type, other);
        }
    }

    private void assignTrack(String type, KOMEAllianceTrackStatus status, int tier) {
        int compatibilityTier = compatibilityTier(type, status, tier);
        if (CIVIL.equals(type)) {
            civilStatus = status;
            civilTier = compatibilityTier;
        } else if (TRADE.equals(type)) {
            tradeStatus = status;
            tradeTier = compatibilityTier;
        } else if (MILITARY.equals(type)) {
            militaryStatus = status;
            militaryTier = compatibilityTier;
        }
        if (status != KOMEAllianceTrackStatus.PENDING) {
            clearPendingParties(type);
        }
    }

    private void setPendingPartiesIfPending(String type, String requester, String receiver) {
        if (getStatus(type) != KOMEAllianceTrackStatus.PENDING) {
            return;
        }
        if (CIVIL.equals(type)) {
            civilRequestedBy = requester;
            civilPendingReceiver = receiver;
        } else if (TRADE.equals(type)) {
            tradeRequestedBy = requester;
            tradePendingReceiver = receiver;
        } else if (MILITARY.equals(type)) {
            militaryRequestedBy = requester;
            militaryPendingReceiver = receiver;
        }
    }

    private void clearPendingParties(String type) {
        if (CIVIL.equals(type)) {
            civilRequestedBy = "";
            civilPendingReceiver = "";
        } else if (TRADE.equals(type)) {
            tradeRequestedBy = "";
            tradePendingReceiver = "";
        } else if (MILITARY.equals(type)) {
            militaryRequestedBy = "";
            militaryPendingReceiver = "";
        }
    }

    private void copyPendingParties(String type, KOMEAlliance other) {
        setPendingPartiesIfPending(type, other.getRequestedBy(type), other.getPendingReceiver(type));
    }

    private KOMEAllianceTrackStatus getStatusWithoutSync(String type) {
        if (CIVIL.equals(type)) {
            return civilStatus;
        }
        if (TRADE.equals(type)) {
            return tradeStatus;
        }
        if (MILITARY.equals(type)) {
            return militaryStatus;
        }
        return KOMEAllianceTrackStatus.NONE;
    }

    private void syncStatusFromCompatibilityFields() {
        civilStatus = KOMEAllianceTrackStatus.fromLegacyTier(civilTier);
        tradeStatus = KOMEAllianceTrackStatus.fromLegacyTier(tradeTier);
        militaryStatus = KOMEAllianceTrackStatus.fromLegacyTier(militaryTier);
        civilTier = compatibilityTier(CIVIL, civilStatus, civilTier);
        tradeTier = compatibilityTier(TRADE, tradeStatus, tradeTier);
        militaryTier = compatibilityTier(MILITARY, militaryStatus, militaryTier);
    }

    private void readLegacyLedger(NBTTagCompound nbt, String contributingFaction) {
        KOMEAllianceFactionLedger ledger = getFactionLedger(contributingFaction);
        if (ledger == null) {
            return;
        }
        NBTTagList assignmentList = nbt.getTagList("Assignments", 10);
        for (int i = 0; i < assignmentList.tagCount(); i++) {
            NBTTagCompound entry = assignmentList.getCompoundTagAt(i);
            ledger.setAssignment(entry.getString("ID"), entry.getString("Value"));
        }
        NBTTagList deliveredList = nbt.getTagList("Delivered", 10);
        for (int i = 0; i < deliveredList.tagCount(); i++) {
            NBTTagCompound entry = deliveredList.getCompoundTagAt(i);
            ledger.setDelivered(entry.getString("ID"), Math.max(0, entry.getInteger("Amount")));
        }
        NBTTagList storageList = nbt.getTagList("Storage", 10);
        for (int i = 0; i < storageList.tagCount(); i++) {
            NBTTagCompound entry = storageList.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (slot >= 0 && slot < STORAGE_SLOTS) {
                ledger.setStorage(slot, stack);
            } else {
                ledger.addRecoveryStack(stack);
            }
        }
        NBTTagList claimList = nbt.getTagList("ClaimGoods", 10);
        for (int i = 0; i < claimList.tagCount(); i++) {
            NBTTagCompound entry = claimList.getCompoundTagAt(i);
            ItemStack sample = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("Stack"));
            int amount = Math.max(0, entry.getInteger("Amount"));
            if (sample != null && amount > 0) {
                ledger.addClaimGoods(entry.getString("ID"), sample, amount);
            }
        }
    }

    private static int compatibilityTier(String type, KOMEAllianceTrackStatus status, int tier) {
        if (status == KOMEAllianceTrackStatus.PENDING) {
            return PENDING;
        }
        if (status == KOMEAllianceTrackStatus.NONE) {
            return NONE;
        }
        return Math.max(0, Math.min(maxTier(type), tier));
    }

    private static int statusStrength(KOMEAllianceTrackStatus status) {
        if (status == KOMEAllianceTrackStatus.ACTIVE) {
            return 2;
        }
        if (status == KOMEAllianceTrackStatus.PENDING) {
            return 1;
        }
        return 0;
    }
}
