package kome.common.data;

import lotr.common.fac.LOTRFaction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class KOMEAlliance {
    public static final String CIVIL = "civil";
    public static final String MILITARY = "military";
    public static final String TRADE = "trade";
    public static final int STORAGE_SLOTS = 9;
    public static final int NONE = -1;
    public static final int PENDING = -2;
    public static final int DATA_SCHEMA_VERSION = 7;

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
    private KOMEAllianceStageProgress stageA;
    private KOMEAllianceStageProgress stageB;
    private KOMEAllianceTrackStatus relationshipStatus = KOMEAllianceTrackStatus.NONE;
    private String requestedBy = "";
    private String pendingReceiver = "";
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

    public KOMEAllianceStageProgress getStageProgress(String faction) {
        String key = normalizeFactionKey(faction);
        if (stageA != null && key.equals(stageA.faction)) return stageA;
        if (stageB != null && key.equals(stageB.faction)) return stageB;
        return null;
    }

    public int getFactionStage(String faction) {
        KOMEAllianceStageProgress progress = getStageProgress(faction);
        return progress == null ? NONE : progress.stage;
    }

    public int getSharedRelationStage() {
        if (relationshipStatus != KOMEAllianceTrackStatus.ACTIVE) return NONE;
        return Math.min(Math.max(0, stageA == null ? 0 : stageA.stage),
            Math.max(0, stageB == null ? 0 : stageB.stage));
    }

    public KOMEAllianceTrackStatus getRelationshipStatus() {
        return relationshipStatus;
    }

    public boolean setFactionStage(String faction, int stage, String updatedBy, long worldTime, long nowMillis) {
        if (relationshipStatus != KOMEAllianceTrackStatus.ACTIVE) return false;
        KOMEAllianceStageProgress progress = getStageProgress(faction);
        if (progress == null) return false;
        int next = Math.max(0, Math.min(4, stage));
        if (next == progress.stage) return false;
        progress.setStage(next, nowMillis);
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = Math.max(0L, nowMillis);
        synchronizeLegacyViewFromStages();
        return true;
    }

    public boolean hasProduceMerchantSlot(String faction) {
        KOMEAllianceStageProgress progress = getStageProgress(faction);
        return progress != null && progress.produceMerchantSlotUnlocked;
    }

    public static String stageName(int stage) {
        switch (Math.max(0, Math.min(4, stage))) {
            case 1: return "Cooperation";
            case 2: return "Friends";
            case 3: return "Allies";
            case 4: return "Military Partnership";
            default: return "Formal Neutrality";
        }
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

    /**
     * Returns the tier unlocked by one participating faction. The relationship status remains
     * mutual, while benefits and the next progression target are directional.
     */
    public int getFactionTier(String faction, String type) {
        if (relationshipStatus == KOMEAllianceTrackStatus.PENDING) {
            return PENDING;
        }
        if (relationshipStatus != KOMEAllianceTrackStatus.ACTIVE) {
            return NONE;
        }
        return compatibilityTierForStage(getFactionStage(faction), normalizeType(type));
    }

    public void setFactionTier(String faction, String type, int tier, String updatedBy, long worldTime) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType) || getStageProgress(faction) == null
                || relationshipStatus != KOMEAllianceTrackStatus.ACTIVE) {
            return;
        }
        int targetStage = stageForCompatibilityTier(normalizedType, tier);
        if (targetStage > getFactionStage(faction)) {
            setFactionStage(faction, targetStage, updatedBy, worldTime, System.currentTimeMillis());
        }
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = System.currentTimeMillis();
    }

    public KOMEAllianceTrackStatus getStatus(String type) {
        return isValidType(type) ? relationshipStatus : KOMEAllianceTrackStatus.NONE;
    }

    public void setTier(String type, int tier, String updatedBy, long worldTime) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType)) {
            return;
        }
        KOMEAllianceTrackStatus status = KOMEAllianceTrackStatus.fromLegacyTier(tier);
        if (status == KOMEAllianceTrackStatus.NONE) {
            clearAllTracks(updatedBy, worldTime);
        } else if (status == KOMEAllianceTrackStatus.PENDING) {
            relationshipStatus = KOMEAllianceTrackStatus.PENDING;
            synchronizeLegacyViewFromStages();
        } else {
            relationshipStatus = KOMEAllianceTrackStatus.ACTIVE;
            int stage = stageForCompatibilityTier(normalizedType, tier);
            setFactionStage(factionA, stage, updatedBy, worldTime, System.currentTimeMillis());
            setFactionStage(factionB, stage, updatedBy, worldTime, System.currentTimeMillis());
        }
    }

    public void setTrack(String type, KOMEAllianceTrackStatus status, int tier, String updatedBy,
            long worldTime, long realTimeMillis) {
        String normalizedType = normalizeType(type);
        if (!isValidType(normalizedType)) {
            return;
        }
        KOMEAllianceTrackStatus safeStatus = status == null ? KOMEAllianceTrackStatus.NONE : status;
        relationshipStatus = safeStatus;
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
        setAllFactionTiers(normalizedType, safeStatus == KOMEAllianceTrackStatus.ACTIVE ? compatibilityTier : NONE);
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = Math.max(0L, realTimeMillis);
        if (safeStatus == KOMEAllianceTrackStatus.ACTIVE) {
            int stage = stageForCompatibilityTier(normalizedType, tier);
            if (stageA != null) stageA.setStage(stage, realTimeMillis);
            if (stageB != null) stageB.setStage(stage, realTimeMillis);
        } else if (safeStatus == KOMEAllianceTrackStatus.NONE) {
            if (stageA != null) stageA.resetFormalProgress();
            if (stageB != null) stageB.resetFormalProgress();
        }
        synchronizeLegacyViewFromStages();
    }

    public void requestTrack(String type, String updatedBy, long worldTime, boolean pending) {
        if (!isValidType(type)) return;
        relationshipStatus = pending ? KOMEAllianceTrackStatus.PENDING : KOMEAllianceTrackStatus.ACTIVE;
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = System.currentTimeMillis();
        synchronizeLegacyViewFromStages();
    }

    public void setPendingParties(String type, String requestedBy, String pendingReceiver) {
        if (relationshipStatus != KOMEAllianceTrackStatus.PENDING) return;
        this.requestedBy = normalizeFactionKey(requestedBy);
        this.pendingReceiver = normalizeFactionKey(pendingReceiver);
        synchronizeLegacyViewFromStages();
    }

    public String getPendingReceiver(String type) {
        return isValidType(type) ? pendingReceiver : "";
    }

    public String getRequestedBy(String type) {
        return isValidType(type) ? requestedBy : "";
    }

    public boolean acceptTrack(String type, String updatedBy, long worldTime) {
        if (!isValidType(type) || relationshipStatus != KOMEAllianceTrackStatus.PENDING) {
            return false;
        }
        relationshipStatus = KOMEAllianceTrackStatus.ACTIVE;
        requestedBy = "";
        pendingReceiver = "";
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = System.currentTimeMillis();
        synchronizeLegacyViewFromStages();
        return true;
    }

    public boolean breakTrack(String type, String updatedBy, long worldTime) {
        if (!isValidType(type) || relationshipStatus == KOMEAllianceTrackStatus.NONE) {
            return false;
        }
        clearAllTracks(updatedBy, worldTime);
        return true;
    }

    public boolean hasAnyAlliance() {
        return relationshipStatus != KOMEAllianceTrackStatus.NONE;
    }

    public boolean hasAnyAcceptedAlliance() {
        return relationshipStatus == KOMEAllianceTrackStatus.ACTIVE;
    }

    public boolean hasAccepted(String type) {
        return getStatus(type) == KOMEAllianceTrackStatus.ACTIVE;
    }

    public boolean hasRecoverableGoods() {
        return ledgerA != null && ledgerA.hasStoredOrClaimableGoods()
            || ledgerB != null && ledgerB.hasStoredOrClaimableGoods();
    }

    /**
     * Stage 2 produce-merchant entitlements deliberately survive a relationship break.  The
     * relationship record therefore remains persistent even when it has no active or pending
     * alliance and no recoverable ledger goods.
     */
    public boolean hasPersistentEntitlements() {
        return stageA != null && stageA.produceMerchantSlotUnlocked
            || stageB != null && stageB.produceMerchantSlotUnlocked;
    }

    public boolean hasPersistentData() {
        return hasAnyAlliance() || hasRecoverableGoods() || hasPersistentEntitlements();
    }

    public void clearAllTracks(String updatedBy, long worldTime) {
        relationshipStatus = KOMEAllianceTrackStatus.NONE;
        requestedBy = "";
        pendingReceiver = "";
        if (stageA != null) stageA.resetFormalProgress();
        if (stageB != null) stageB.resetFormalProgress();
        if (ledgerA != null) ledgerA.resetFormalProgress();
        if (ledgerB != null) ledgerB.resetFormalProgress();
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = Math.max(0L, worldTime);
        updatedRealTimeMillis = System.currentTimeMillis();
        synchronizeLegacyViewFromStages();
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
        refreshCompatibilityTier(CIVIL);
        refreshCompatibilityTier(TRADE);
        refreshCompatibilityTier(MILITARY);
        if (other.updatedRealTimeMillis > updatedRealTimeMillis
                || other.updatedRealTimeMillis == updatedRealTimeMillis && other.updatedWorldTime > updatedWorldTime) {
            lastUpdatedBy = other.lastUpdatedBy;
            updatedWorldTime = other.updatedWorldTime;
            updatedRealTimeMillis = other.updatedRealTimeMillis;
        }
        relationshipStatus = statusStrength(other.relationshipStatus) > statusStrength(relationshipStatus)
            ? other.relationshipStatus : relationshipStatus;
        if (other.stageA != null) mergeStage(other.stageA);
        if (other.stageB != null) mergeStage(other.stageB);
        if (pendingReceiver.length() == 0 && other.pendingReceiver.length() > 0) {
            requestedBy = other.requestedBy;
            pendingReceiver = other.pendingReceiver;
        }
        ensureHierarchy();
    }

    public void readFromNBT(NBTTagCompound nbt) {
        int schemaVersion = nbt.hasKey("SchemaVersion") ? nbt.getInteger("SchemaVersion") : 0;
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
        migrateFactionTiers(CIVIL, civilStatus, civilTier);
        migrateFactionTiers(TRADE, tradeStatus, tradeTier);
        migrateFactionTiers(MILITARY, militaryStatus, militaryTier);
        refreshCompatibilityTier(CIVIL);
        refreshCompatibilityTier(TRADE);
        refreshCompatibilityTier(MILITARY);
        NBTTagList stageProgress = nbt.getTagList("StageProgress", 10);
        if (schemaVersion >= 7 && stageProgress.tagCount() > 0) {
            for (int i = 0; i < stageProgress.tagCount(); i++) {
                KOMEAllianceStageProgress loaded = new KOMEAllianceStageProgress("");
                loaded.readFromNBT(stageProgress.getCompoundTagAt(i));
                KOMEAllianceStageProgress target = getStageProgress(loaded.faction);
                if (target != null) copyStage(target, loaded);
            }
            relationshipStatus = KOMEAllianceTrackStatus.fromKey(nbt.getString("RelationshipStatus"));
            requestedBy = normalizeFactionKey(nbt.getString("RequestedBy"));
            pendingReceiver = normalizeFactionKey(nbt.getString("PendingReceiver"));
        } else {
            relationshipStatus = strongestLegacyStatus();
            migrateLegacyStages();
            requestedBy = firstNonBlank(civilRequestedBy, tradeRequestedBy, militaryRequestedBy);
            pendingReceiver = firstNonBlank(civilPendingReceiver, tradePendingReceiver, militaryPendingReceiver);
        }
        synchronizeLegacyViewFromStages();
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
        nbt.setString("RelationshipStatus", relationshipStatus.key);
        nbt.setString("RequestedBy", requestedBy);
        nbt.setString("PendingReceiver", pendingReceiver);
        NBTTagList stageProgress = new NBTTagList();
        if (stageA != null) stageProgress.appendTag(stageA.writeToNBT());
        if (stageB != null) stageProgress.appendTag(stageB.writeToNBT());
        nbt.setTag("StageProgress", stageProgress);
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
        synchronizeLegacyViewFromStages();
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

    public static List<String> allFactionKeys() {
        List<String> result = new ArrayList<String>();
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()) {
                String key = normalizeFactionKey(faction.codeName());
                if (key.length() > 0 && !result.contains(key)) {
                    result.add(key);
                }
            }
        }
        return result;
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
        stageA = new KOMEAllianceStageProgress(factionA);
        stageB = new KOMEAllianceStageProgress(factionB);
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
        if (status == KOMEAllianceTrackStatus.ACTIVE) {
            initializeFactionTierIfMissing(type, compatibilityTier);
        } else {
            setAllFactionTiers(type, NONE);
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
        synchronizeLegacyViewFromStages();
    }

    private void migrateFactionTiers(String type, KOMEAllianceTrackStatus status, int legacyTier) {
        if (status != KOMEAllianceTrackStatus.ACTIVE) {
            setAllFactionTiers(type, NONE);
            return;
        }
        int migrated = Math.max(0, Math.min(maxTier(type), legacyTier));
        initializeFactionTierIfMissing(type, migrated);
    }

    private void initializeFactionTierIfMissing(String type, int tier) {
        if (ledgerA != null && !ledgerA.hasUnlockedTier(type)) {
            ledgerA.setUnlockedTier(type, tier);
        }
        if (ledgerB != null && !ledgerB.hasUnlockedTier(type)) {
            ledgerB.setUnlockedTier(type, tier);
        }
    }

    private void setAllFactionTiers(String type, int tier) {
        if (ledgerA != null) {
            ledgerA.setUnlockedTier(type, tier);
        }
        if (ledgerB != null) {
            ledgerB.setUnlockedTier(type, tier);
        }
    }

    private void refreshCompatibilityTier(String type) {
        KOMEAllianceTrackStatus status = getStatusWithoutSync(type);
        int tier;
        if (status == KOMEAllianceTrackStatus.PENDING) {
            tier = PENDING;
        } else if (status == KOMEAllianceTrackStatus.NONE) {
            tier = NONE;
        } else {
            int first = ledgerA == null ? 0 : Math.max(0, ledgerA.getUnlockedTier(type));
            int second = ledgerB == null ? 0 : Math.max(0, ledgerB.getUnlockedTier(type));
            tier = Math.min(first, second);
        }
        if (CIVIL.equals(type)) {
            civilTier = tier;
        } else if (TRADE.equals(type)) {
            tradeTier = tier;
        } else if (MILITARY.equals(type)) {
            militaryTier = tier;
        }
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

    private void synchronizeLegacyViewFromStages() {
        KOMEAllianceTrackStatus status = relationshipStatus == null ? KOMEAllianceTrackStatus.NONE : relationshipStatus;
        civilStatus = status;
        tradeStatus = status;
        militaryStatus = status;
        if (status == KOMEAllianceTrackStatus.PENDING) {
            civilTier = tradeTier = militaryTier = PENDING;
            civilRequestedBy = tradeRequestedBy = militaryRequestedBy = requestedBy;
            civilPendingReceiver = tradePendingReceiver = militaryPendingReceiver = pendingReceiver;
            setAllFactionTiers(CIVIL, NONE);
            setAllFactionTiers(TRADE, NONE);
            setAllFactionTiers(MILITARY, NONE);
            return;
        }
        if (status == KOMEAllianceTrackStatus.NONE) {
            civilTier = tradeTier = militaryTier = NONE;
            civilRequestedBy = tradeRequestedBy = militaryRequestedBy = "";
            civilPendingReceiver = tradePendingReceiver = militaryPendingReceiver = "";
            setAllFactionTiers(CIVIL, NONE);
            setAllFactionTiers(TRADE, NONE);
            setAllFactionTiers(MILITARY, NONE);
            return;
        }
        civilRequestedBy = tradeRequestedBy = militaryRequestedBy = "";
        civilPendingReceiver = tradePendingReceiver = militaryPendingReceiver = "";
        applyCompatibilityToLedger(stageA);
        applyCompatibilityToLedger(stageB);
        civilTier = Math.min(compatibilityTierForStage(stageA == null ? 0 : stageA.stage, CIVIL),
            compatibilityTierForStage(stageB == null ? 0 : stageB.stage, CIVIL));
        tradeTier = Math.min(compatibilityTierForStage(stageA == null ? 0 : stageA.stage, TRADE),
            compatibilityTierForStage(stageB == null ? 0 : stageB.stage, TRADE));
        militaryTier = Math.min(compatibilityTierForStage(stageA == null ? 0 : stageA.stage, MILITARY),
            compatibilityTierForStage(stageB == null ? 0 : stageB.stage, MILITARY));
    }

    private void applyCompatibilityToLedger(KOMEAllianceStageProgress stage) {
        if (stage == null) return;
        KOMEAllianceFactionLedger ledger = getFactionLedger(stage.faction);
        if (ledger == null) return;
        ledger.setUnlockedTier(CIVIL, compatibilityTierForStage(stage.stage, CIVIL));
        ledger.setUnlockedTier(TRADE, compatibilityTierForStage(stage.stage, TRADE));
        ledger.setUnlockedTier(MILITARY, compatibilityTierForStage(stage.stage, MILITARY));
    }

    private static int compatibilityTierForStage(int stage, String type) {
        int safeStage = Math.max(0, Math.min(4, stage));
        String normalized = normalizeType(type);
        if (CIVIL.equals(normalized)) return safeStage >= 1 ? 2 : 0;
        if (TRADE.equals(normalized)) return safeStage >= 2 ? 2 : 0;
        if (MILITARY.equals(normalized)) return safeStage >= 4 ? 3 : safeStage >= 3 ? 2 : 0;
        return NONE;
    }

    private static int stageForCompatibilityTier(String type, int tier) {
        String normalized = normalizeType(type);
        if (MILITARY.equals(normalized)) return tier >= 3 ? 4 : tier >= 2 ? 3 : 0;
        if (TRADE.equals(normalized)) return tier >= 2 ? 2 : 0;
        return CIVIL.equals(normalized) && tier >= 2 ? 1 : 0;
    }

    private KOMEAllianceTrackStatus strongestLegacyStatus() {
        KOMEAllianceTrackStatus result = civilStatus;
        if (statusStrength(tradeStatus) > statusStrength(result)) result = tradeStatus;
        if (statusStrength(militaryStatus) > statusStrength(result)) result = militaryStatus;
        return result;
    }

    private void migrateLegacyStages() {
        int stageForA = migratedStageForLedger(ledgerA);
        int stageForB = migratedStageForLedger(ledgerB);
        if (relationshipStatus == KOMEAllianceTrackStatus.ACTIVE) {
            stageA.setStage(stageForA, updatedRealTimeMillis);
            stageB.setStage(stageForB, updatedRealTimeMillis);
        }
        if (legacyTier(ledgerA, TRADE) >= 2) stageA.produceMerchantSlotUnlocked = true;
        if (legacyTier(ledgerB, TRADE) >= 2) stageB.produceMerchantSlotUnlocked = true;
    }

    private static int migratedStageForLedger(KOMEAllianceFactionLedger ledger) {
        int military = legacyTier(ledger, MILITARY);
        int trade = legacyTier(ledger, TRADE);
        int civil = legacyTier(ledger, CIVIL);
        if (military >= 3) return 4;
        if (military >= 2) return 3;
        if (trade >= 2) return 2;
        if (civil >= 2) return 1;
        return 0;
    }

    private static int legacyTier(KOMEAllianceFactionLedger ledger, String type) {
        return ledger == null ? NONE : Math.max(ledger.getUnlockedTier(type), ledger.getCompletedTier(type));
    }

    private void mergeStage(KOMEAllianceStageProgress other) {
        KOMEAllianceStageProgress target = getStageProgress(other.faction);
        if (target == null) return;
        if (other.stage > target.stage) target.setStage(other.stage, updatedRealTimeMillis);
        target.produceMerchantSlotUnlocked |= other.produceMerchantSlotUnlocked;
        for (int i = 1; i <= 4; i++) {
            target.claimedAtMillis[i] = earliestPositive(target.claimedAtMillis[i], other.claimedAtMillis[i]);
            target.fixedCompletedAtMillis[i] = earliestPositive(target.fixedCompletedAtMillis[i], other.fixedCompletedAtMillis[i]);
        }
        if (target.qualifyingDeploymentAtMillis <= 0L
                || other.qualifyingDeploymentAtMillis > 0L && other.qualifyingDeploymentAtMillis < target.qualifyingDeploymentAtMillis) {
            target.qualifyingWarId = other.qualifyingWarId;
            target.qualifyingCompanyId = other.qualifyingCompanyId;
            target.qualifyingDeploymentAtMillis = other.qualifyingDeploymentAtMillis;
        }
    }

    private static void copyStage(KOMEAllianceStageProgress target, KOMEAllianceStageProgress source) {
        target.stage = source.stage;
        target.produceMerchantSlotUnlocked = source.produceMerchantSlotUnlocked;
        target.qualifyingWarId = source.qualifyingWarId;
        target.qualifyingCompanyId = source.qualifyingCompanyId;
        target.qualifyingDeploymentAtMillis = source.qualifyingDeploymentAtMillis;
        for (int i = 1; i <= 4; i++) {
            target.claimedAtMillis[i] = source.claimedAtMillis[i];
            target.fixedCompletedAtMillis[i] = source.fixedCompletedAtMillis[i];
        }
    }

    private static long earliestPositive(long left, long right) {
        if (left <= 0L) return right;
        if (right <= 0L) return left;
        return Math.min(left, right);
    }

    private static String firstNonBlank(String first, String second, String third) {
        if (first != null && first.length() > 0) return first;
        if (second != null && second.length() > 0) return second;
        return third == null ? "" : third;
    }
}
