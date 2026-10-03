package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** UI-agnostic transaction boundary for creating a newly hired CAMPAIGN combat unit. */
public final class KOMECampaignRecruitmentService {
    private static final Set<String> IN_FLIGHT = Collections.synchronizedSet(new HashSet<String>());
    private static final Map<String, Long> RECENT_REQUESTS = new HashMap<String, Long>();
    private static final Object DUPLICATE_LOCK = new Object();
    private static final long DUPLICATE_WINDOW_MILLIS = 750L;

    private KOMECampaignRecruitmentService() { }

    public enum SourceKind {
        NATIVE_TRADER,
        FUTURE_RECRUITMENT_SOURCE
    }

    public enum Code {
        SUCCESS,
        INVALID_REQUEST,
        DUPLICATE_REQUEST,
        INVALID_SOURCE,
        INVALID_UNIT,
        FARMHAND_UNSUPPORTED,
        LOCATION_NOT_ALLOWED,
        INSUFFICIENT_POPULATION,
        INSUFFICIENT_COINS,
        DEPLOYMENT_FAILED,
        NATIVE_HIRE_FAILED,
        COMPANY_ADMISSION_FAILED
    }

    /** Stable intent only: no client-supplied price, population cost, or trusted tile. */
    public static final class Request {
        public final UUID playerId;
        public final String playerName;
        public final SourceKind sourceKind;
        public final String sourceReference;
        public final String unitKey;
        public final KOMEHiredUnitClass requestedClass;

        public Request(UUID playerId, String playerName, SourceKind sourceKind,
                String sourceReference, String unitKey) {
            this(playerId, playerName, sourceKind, sourceReference, unitKey,
                KOMEHiredUnitClass.CAMPAIGN);
        }

        public Request(UUID playerId, String playerName, SourceKind sourceKind,
                String sourceReference, String unitKey, KOMEHiredUnitClass requestedClass) {
            this.playerId = playerId;
            this.playerName = clean(playerName);
            this.sourceKind = sourceKind;
            this.sourceReference = clean(sourceReference);
            this.unitKey = clean(unitKey);
            this.requestedClass = requestedClass;
        }

        String transactionKey() {
            return String.valueOf(playerId) + "|" + sourceKind + "|"
                + sourceReference + "|" + unitKey;
        }
    }

    /** Adapter implemented by the authoritative recruitment source, never by a GUI. */
    public interface RecruitmentSource {
        PreparedRecruitment prepare(Request request) throws RecruitmentFailure;
    }

    /** Source-owned native effects. The core transaction controls when each phase runs. */
    public interface RecruitmentEffect {
        void prepareDeployment(KOMEWorldData data, String payingFaction,
            String strategicTile) throws Exception;
        void performNativeHire() throws Exception;
        void applyCampaignState(KOMEHiredUnitRecord record) throws Exception;
        void spawn() throws Exception;
        void rollback();
    }

    public static final class PreparedRecruitment {
        public final KOMEWorldData data;
        public final KOMEHiredUnitRecord record;
        public final String payingFaction;
        public final int nativeCoinCost;
        public final RecruitmentEffect effect;

        public PreparedRecruitment(KOMEWorldData data, KOMEHiredUnitRecord record,
                String payingFaction, int nativeCoinCost,
                RecruitmentEffect effect) {
            this.data = data;
            this.record = record;
            this.payingFaction = KOMEAlliance.normalizeFactionKey(payingFaction);
            this.nativeCoinCost = nativeCoinCost;
            this.effect = effect;
        }
    }

    public static final class RecruitmentFailure extends Exception {
        public final Code code;

        public RecruitmentFailure(Code code, String message) {
            super(message);
            this.code = code == null ? Code.INVALID_REQUEST : code;
        }
    }

    public static Result recruit(Request request, RecruitmentSource source) {
        Result invalid = validateIntent(request, source);
        if (invalid != null) return invalid;
        String transactionKey = request.transactionKey();
        if (!acquire(transactionKey, System.currentTimeMillis())) {
            return Result.failure(Code.DUPLICATE_REQUEST,
                "A campaign recruitment for this unit selection is already in progress.");
        }
        try {
            PreparedRecruitment prepared = source.prepare(request);
            return execute(request, prepared);
        } catch (RecruitmentFailure failure) {
            return Result.failure(failure.code, cleanFailure(failure));
        } catch (RuntimeException failure) {
            return Result.failure(Code.NATIVE_HIRE_FAILED, cleanFailure(failure));
        } finally {
            IN_FLIGHT.remove(transactionKey);
        }
    }

    private static boolean acquire(String key, long now) {
        synchronized (DUPLICATE_LOCK) {
            Long recent = RECENT_REQUESTS.get(key);
            if (IN_FLIGHT.contains(key)
                    || recent != null && now - recent.longValue() < DUPLICATE_WINDOW_MILLIS) {
                return false;
            }
            if (RECENT_REQUESTS.size() > 4096) {
                java.util.Iterator<Map.Entry<String, Long>> entries =
                    RECENT_REQUESTS.entrySet().iterator();
                while (entries.hasNext()) {
                    if (now - entries.next().getValue().longValue()
                            >= DUPLICATE_WINDOW_MILLIS) entries.remove();
                }
            }
            RECENT_REQUESTS.put(key, Long.valueOf(now));
            IN_FLIGHT.add(key);
            return true;
        }
    }

    static void clearDuplicateGuardForTests() {
        synchronized (DUPLICATE_LOCK) {
            IN_FLIGHT.clear();
            RECENT_REQUESTS.clear();
        }
    }

    private static Result validateIntent(Request request, RecruitmentSource source) {
        if (request == null || source == null || request.playerId == null
                || request.sourceKind == null || request.sourceReference.length() == 0
                || request.unitKey.length() == 0) {
            return Result.failure(Code.INVALID_REQUEST,
                "A player, recruitment source, and unit selection are required.");
        }
        if (request.requestedClass != KOMEHiredUnitClass.CAMPAIGN) {
            return Result.failure(Code.INVALID_REQUEST,
                "This service accepts only explicit CAMPAIGN recruitment intent.");
        }
        return null;
    }

    private static Result execute(Request request, PreparedRecruitment prepared) {
        Result invalid = validatePrepared(request, prepared);
        if (invalid != null) return invalid;
        KOMERecruitmentLocationService.Selection selection =
            KOMERecruitmentLocationService.resolveSelectedOrDefault(prepared.data,
                request.playerId, prepared.payingFaction);
        String strategicTile = KOMEConquestTile.normalizeId(selection.tileId);
        if (strategicTile.length() == 0 || selection.decision == null
                || !selection.decision.legal) {
            safeRollback(prepared.effect);
            return Result.failure(Code.LOCATION_NOT_ALLOWED,
                "No legal KOME campaign recruitment tile could be resolved: "
                    + (selection.decision == null ? "unavailable selection"
                        : selection.decision.reason));
        }
        try {
            prepared.effect.prepareDeployment(prepared.data,
                prepared.payingFaction, strategicTile);
        } catch (Exception failure) {
            safeRollback(prepared.effect);
            return Result.failure(Code.DEPLOYMENT_FAILED,
                "Campaign recruitment tile " + strategicTile
                    + " could not produce a safe deployment: "
                    + cleanFailure(failure));
        }
        KOMEPopulationService.CombatHireDebit population =
            KOMEPopulationService.beginCombatHireDebit(prepared.data,
                prepared.payingFaction, prepared.record.populationSpent);
        if (population == null) {
            safeRollback(prepared.effect);
            return Result.failure(Code.INSUFFICIENT_POPULATION,
                "Not enough available " + KOMEAlliance.displayFactionName(prepared.payingFaction)
                    + " population. Required: " + prepared.record.populationSpent + ".");
        }
        Map<String, NBTTagCompound> companies = snapshotCompanies(prepared.data);
        List<KOMEAuditEntry> audit = new ArrayList<KOMEAuditEntry>(prepared.data.centralAudit);
        String priorCurrentTile = prepared.record.currentTile;
        String priorSourceTile = prepared.record.sourceTileId;
        String priorCompanyId = prepared.record.companyId;
        String priorCompanyName = prepared.record.companyName;
        long priorCompanyAssignedAt = prepared.record.companyAssignedAtMillis;
        UUID priorCompanyAssignedBy = prepared.record.companyAssignedBy;
        String priorCompanyAssignedByName = prepared.record.companyAssignedByName;
        NBTTagCompound priorStationedEntityData = prepared.record.stationedEntityData == null
            ? null : (NBTTagCompound) prepared.record.stationedEntityData.copy();
        boolean nativeStarted = false;
        try {
            nativeStarted = true;
            prepared.effect.performNativeHire();
            KOMEHiredUnitClassification.assignForCampaignWorkflow(prepared.record);
            prepared.record.currentTile = strategicTile;
            if (KOMEConquestTile.normalizeId(prepared.record.sourceTileId).length() == 0) {
                prepared.record.sourceTileId = strategicTile;
            }
            prepared.data.hiredUnits.put(prepared.record.entity, prepared.record);
            KOMEArmyCompany company = prepared.data.assignUnitToCampaignCompanyAtTile(
                prepared.record, request.playerName, strategicTile);
            if (company == null || !company.units.contains(prepared.record.entity)
                    || !company.id.equals(prepared.record.companyId)) {
                throw new TransactionFailure(Code.COMPANY_ADMISSION_FAILED,
                    "Campaign company admission could not be completed.");
            }
            prepared.effect.applyCampaignState(prepared.record);
            prepared.effect.spawn();
            KOMEAuditService.record(prepared.data, System.currentTimeMillis(), "UNIT",
                "CAMPAIGN_HIRE", request.playerId.toString(), prepared.record.entity.toString(),
                "Campaign combat unit hired",
                "source=" + request.sourceKind + ";unit=" + request.unitKey
                    + ";tile=" + strategicTile + ";company=" + company.id
                    + ";coinCost=" + prepared.nativeCoinCost
                    + ";populationCost=" + prepared.record.populationSpent);
            prepared.data.markDirty();
            prepared.data.syncConquestTiles();
            population.commit();
            return Result.success(prepared, company, strategicTile);
        } catch (Exception failure) {
            prepared.data.hiredUnits.remove(prepared.record.entity);
            restoreCompanies(prepared.data, companies);
            prepared.data.centralAudit.clear();
            prepared.data.centralAudit.addAll(audit);
            prepared.record.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
            prepared.record.currentTile = priorCurrentTile;
            prepared.record.sourceTileId = priorSourceTile;
            prepared.record.companyId = priorCompanyId;
            prepared.record.companyName = priorCompanyName;
            prepared.record.companyAssignedAtMillis = priorCompanyAssignedAt;
            prepared.record.companyAssignedBy = priorCompanyAssignedBy;
            prepared.record.companyAssignedByName = priorCompanyAssignedByName;
            prepared.record.stationedEntityData = priorStationedEntityData;
            if (nativeStarted) safeRollback(prepared.effect);
            population.rollback();
            prepared.data.markDirty();
            Code code = failure instanceof TransactionFailure
                ? ((TransactionFailure) failure).code : Code.NATIVE_HIRE_FAILED;
            return Result.failure(code,
                "Campaign recruitment failed and was rolled back: " + cleanFailure(failure));
        }
    }

    private static Result validatePrepared(Request request, PreparedRecruitment prepared) {
        if (prepared == null || prepared.data == null || prepared.record == null
                || prepared.effect == null || prepared.record.entity == null
                || prepared.record.owner == null || !request.playerId.equals(prepared.record.owner)) {
            if (prepared != null) safeRollback(prepared.effect);
            return Result.failure(Code.INVALID_SOURCE,
                "The recruitment source did not resolve a complete authoritative candidate.");
        }
        if (prepared.record.farmhand) {
            safeRollback(prepared.effect);
            return Result.failure(Code.FARMHAND_UNSUPPORTED,
                "Farmhands cannot be hired for campaign service.");
        }
        if (prepared.record.type != KOMEPopulationType.OFFENSIVE
                || prepared.record.populationSpent <= 0
                || prepared.payingFaction.length() == 0
                || prepared.nativeCoinCost < 0
                || prepared.data.hiredUnits.containsKey(prepared.record.entity)) {
            safeRollback(prepared.effect);
            return Result.failure(Code.INVALID_UNIT,
                "The recruitment source resolved an invalid or duplicate combat unit.");
        }
        if (KOMEHiredUnitClassification.isCampaignUnit(prepared.record)) {
            safeRollback(prepared.effect);
            return Result.failure(Code.INVALID_UNIT,
                "Campaign classification must be assigned by the recruitment transaction.");
        }
        return null;
    }

    private static Map<String, NBTTagCompound> snapshotCompanies(KOMEWorldData data) {
        Map<String, NBTTagCompound> result = new LinkedHashMap<String, NBTTagCompound>();
        for (Map.Entry<String, KOMEArmyCompany> entry : data.armyCompanies.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey(), entry.getValue().writeToNBT());
            }
        }
        return result;
    }

    private static void restoreCompanies(KOMEWorldData data,
            Map<String, NBTTagCompound> snapshots) {
        List<String> added = new ArrayList<String>();
        for (String id : data.armyCompanies.keySet()) {
            if (!snapshots.containsKey(id)) added.add(id);
        }
        for (String id : added) data.armyCompanies.remove(id);
        for (Map.Entry<String, NBTTagCompound> entry : snapshots.entrySet()) {
            KOMEArmyCompany company = data.armyCompanies.get(entry.getKey());
            if (company == null) company = new KOMEArmyCompany();
            company.readFromNBT(entry.getValue());
            data.armyCompanies.put(entry.getKey(), company);
        }
    }

    private static void safeRollback(RecruitmentEffect effect) {
        if (effect == null) return;
        try { effect.rollback(); }
        catch (RuntimeException ignored) { }
    }

    private static String cleanFailure(Throwable failure) {
        String value = failure == null ? "unknown failure" : failure.getMessage();
        return clean(value == null || value.length() == 0
            ? failure.getClass().getSimpleName() : value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('|', ' ').replace('\n', ' ')
            .replace('\r', ' ').trim();
    }

    private static final class TransactionFailure extends Exception {
        final Code code;
        TransactionFailure(Code code, String reason) { super(reason); this.code = code; }
    }

    public static final class Result {
        public final boolean success;
        public final Code code;
        public final String reason;
        public final UUID unitId;
        public final String companyId;
        public final String strategicTile;
        public final int nativeCoinCost;
        public final int populationCost;

        private Result(boolean success, Code code, String reason, UUID unitId,
                String companyId, String strategicTile, int coinCost, int populationCost) {
            this.success = success;
            this.code = code;
            this.reason = clean(reason);
            this.unitId = unitId;
            this.companyId = clean(companyId);
            this.strategicTile = KOMEConquestTile.normalizeId(strategicTile);
            this.nativeCoinCost = coinCost;
            this.populationCost = populationCost;
        }

        static Result success(PreparedRecruitment prepared, KOMEArmyCompany company,
                String strategicTile) {
            return new Result(true, Code.SUCCESS, "Campaign unit hired.",
                prepared.record.entity, company.id, strategicTile,
                prepared.nativeCoinCost, prepared.record.populationSpent);
        }

        public static Result failure(Code code, String reason) {
            return new Result(false, code, reason, null, "", "", 0, 0);
        }
    }
}
