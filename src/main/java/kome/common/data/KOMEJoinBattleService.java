package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

import static kome.common.data.KOMEConflictContracts.ExpectedConflict;
import static kome.common.data.KOMEConflictRecord.FactionParticipation;
import static kome.common.data.KOMEConflictRecord.PlayerParticipation;
import static kome.common.data.KOMEConflictRecord.PlayerStatus;

/**
 * Server-authoritative, read-only Join Battle eligibility for KOM-19.
 *
 * <p>This service does not register participants, move players, inspect physical proximity,
 * create deployment receipts, or infer diplomatic sides. A current coherent CAMPAIGN company
 * committed to the exact active ConflictRecord is the eligibility authority.</p>
 */
public final class KOMEJoinBattleService {
    public static final KOMEJoinBattleService INSTANCE = new KOMEJoinBattleService();

    public enum Reason {
        ALLOWED,
        INVALID_REQUEST,
        INVALID_TILE,
        NO_ACTIVE_CONFLICT,
        STALE_CONFLICT,
        UNPLEDGED,
        FACTION_NOT_PARTICIPATING,
        NO_ELIGIBLE_COMPANY,
        WITHDRAWN,
        COMPANY_NOT_COMMITTED,
        WRONG_FACTION_COMPANY,
        COMPANY_INCOHERENT,
        COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS
    }

    /** Immutable company row suitable for a later server-produced Join Battle UI. */
    public static final class EligibleCompany {
        public final String companyId;
        public final String displayName;
        public final String factionId;
        public final int authoritativeMemberCount;
        public final int campaignCombatMemberCount;
        public final KOMECompanyCoherenceService.Status coherenceStatus;

        private EligibleCompany(KOMEArmyCompany company, int campaignCombatMemberCount,
                KOMECompanyCoherenceService.Status coherenceStatus) {
            this.companyId = clean(company.id);
            this.displayName = KOMEHiredUnitRecord.normalizeCompanyName(company.name);
            this.factionId = faction(company.faction);
            this.authoritativeMemberCount = company.units.size();
            this.campaignCombatMemberCount = campaignCombatMemberCount;
            this.coherenceStatus = coherenceStatus;
        }
    }

    /** Immutable current-state projection. No physical coordinates or mutable authorities leak. */
    public static final class Projection {
        public final String tileId;
        public final String conflictId;
        public final long conflictRevision;
        public final KOMEConflictRecord.State conflictState;
        public final String playerFactionId;
        public final Reason reason;
        public final List<EligibleCompany> eligibleCompanies;

        private Projection(String tileId, KOMEConflictRecord record, String playerFactionId,
                Reason reason, List<EligibleCompany> eligibleCompanies) {
            this.tileId = tileId;
            this.conflictId = record == null ? "" : record.getConflictId();
            this.conflictRevision = record == null ? 0L : record.getRevision();
            this.conflictState = record == null ? null : record.getState();
            this.playerFactionId = playerFactionId;
            this.reason = reason;
            this.eligibleCompanies = Collections.unmodifiableList(
                new ArrayList<EligibleCompany>(eligibleCompanies));
        }

        public boolean isAllowed() {
            return reason == Reason.ALLOWED;
        }
    }

    /** Exact selected-company revalidation result for the future action packet. */
    public static final class SelectionResult {
        public final Reason reason;
        public final Projection current;
        public final EligibleCompany selectedCompany;

        private SelectionResult(Reason reason, Projection current,
                EligibleCompany selectedCompany) {
            this.reason = reason;
            this.current = current;
            this.selectedCompany = selectedCompany;
        }

        public boolean isAllowed() {
            return reason == Reason.ALLOWED && selectedCompany != null;
        }
    }

    private KOMEJoinBattleService() { }

    /** Resolves the current LOTR pledge on the server for every evaluation. */
    public Projection evaluate(KOMEWorldData data, EntityPlayerMP player, String tileId) {
        if (player == null)
            return denied(data, null, "", tileId, null, false, Reason.INVALID_REQUEST);
        return evaluateResolved(data, KOMEReflection.getEntityUUID(player),
            currentPledgeFaction(player), tileId, null, false);
    }

    /**
     * Revalidates one server-projected company against an exact Conflict ID/revision.
     * The future client supplies only the company ID and conflict expectation.
     */
    public SelectionResult validateSelectedCompany(KOMEWorldData data, EntityPlayerMP player,
            String tileId, ExpectedConflict expected, String selectedCompanyId) {
        if (player == null)
            return selection(Reason.INVALID_REQUEST,
                denied(data, null, "", tileId, expected, true, Reason.INVALID_REQUEST), null);
        return validateSelectedResolved(data, KOMEReflection.getEntityUUID(player),
            currentPledgeFaction(player), tileId, expected, selectedCompanyId);
    }

    /** Package-local deterministic seam; callers outside this package must use live player pledge. */
    Projection evaluateResolved(KOMEWorldData data, UUID playerId, String authoritativeFaction,
            String tileId, ExpectedConflict expected, boolean requireExpectedConflict) {
        Base base = base(data, playerId, authoritativeFaction, tileId,
            expected, requireExpectedConflict);
        if (base.reason != Reason.ALLOWED)
            return projection(base, base.reason, Collections.<EligibleCompany>emptyList());

        List<EligibleCompany> eligible = new ArrayList<EligibleCompany>();
        for (String companyId : base.record.getCommitments().keySet()) {
            CompanyAssessment assessment = assessCompany(data, base.record,
                base.playerFaction, companyId);
            if (assessment.reason == Reason.ALLOWED) eligible.add(assessment.company);
        }
        Collections.sort(eligible, new Comparator<EligibleCompany>() {
            @Override public int compare(EligibleCompany first, EligibleCompany second) {
                return first.companyId.compareTo(second.companyId);
            }
        });
        return projection(base, eligible.isEmpty()
            ? Reason.NO_ELIGIBLE_COMPANY : Reason.ALLOWED, eligible);
    }

    /** Package-local deterministic seam used by focused service tests. */
    SelectionResult validateSelectedResolved(KOMEWorldData data, UUID playerId,
            String authoritativeFaction, String tileId, ExpectedConflict expected,
            String selectedCompanyId) {
        Base base = base(data, playerId, authoritativeFaction, tileId, expected, true);
        if (base.reason != Reason.ALLOWED)
            return selection(base.reason,
                projection(base, base.reason, Collections.<EligibleCompany>emptyList()), null);

        String companyId = clean(selectedCompanyId);
        if (companyId.length() == 0
                || !base.record.getCommitments().containsKey(companyId)) {
            Projection current = evaluateResolved(data, playerId, base.playerFaction,
                base.tileId, expected, true);
            return selection(Reason.COMPANY_NOT_COMMITTED, current, null);
        }
        CompanyAssessment assessment = assessCompany(data, base.record,
            base.playerFaction, companyId);
        Projection current = evaluateResolved(data, playerId, base.playerFaction,
            base.tileId, expected, true);
        return selection(assessment.reason, current, assessment.company);
    }

    private Base base(KOMEWorldData data, UUID playerId, String authoritativeFaction,
            String tileId, ExpectedConflict expected, boolean requireExpectedConflict) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (data == null || playerId == null)
            return new Base(data, playerId, tile, "", null, Reason.INVALID_REQUEST);
        if (!KOMEConquestTile.isCanonicalTileId(tile))
            return new Base(data, playerId, tile, "", null, Reason.INVALID_TILE);

        KOMEConflictRecord record = data.getConflictService().get(tile);
        if (record == null || !record.isActive())
            return new Base(data, playerId, tile, "", record, Reason.NO_ACTIVE_CONFLICT);
        if (requireExpectedConflict && !matches(record, expected))
            return new Base(data, playerId, tile, "", record, Reason.STALE_CONFLICT);

        String playerFaction = faction(authoritativeFaction);
        LOTRFaction resolvedFaction = KOMEAlliance.findLotrFaction(playerFaction);
        if (playerFaction.length() == 0 || resolvedFaction == null
                || !resolvedFaction.isPlayableAlignmentFaction())
            return new Base(data, playerId, tile, "", record, Reason.UNPLEDGED);

        FactionParticipation participation = record.getFactionParticipation()
            .get(playerFaction);
        if (participation == null || !participation.isActive())
            return new Base(data, playerId, tile, playerFaction, record,
                Reason.FACTION_NOT_PARTICIPATING);

        PlayerParticipation player = record.getPlayers().get(playerId);
        if (player != null && player.status == PlayerStatus.WITHDRAWN)
            return new Base(data, playerId, tile, playerFaction, record, Reason.WITHDRAWN);
        return new Base(data, playerId, tile, playerFaction, record, Reason.ALLOWED);
    }

    private CompanyAssessment assessCompany(KOMEWorldData data, KOMEConflictRecord conflict,
            String playerFaction, String companyId) {
        if (!conflict.getCommitments().containsKey(companyId))
            return CompanyAssessment.denied(Reason.COMPANY_NOT_COMMITTED);
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) return CompanyAssessment.denied(Reason.COMPANY_INCOHERENT);
        if (!playerFaction.equals(faction(company.faction)))
            return CompanyAssessment.denied(Reason.WRONG_FACTION_COMPANY);
        if (!conflict.getTileId().equals(KOMEConquestTile.normalizeId(company.currentTile)))
            return CompanyAssessment.denied(Reason.COMPANY_INCOHERENT);

        int campaignCombatMembers = 0;
        boolean terminalMember = false;
        for (UUID memberId : company.units) {
            KOMEHiredUnitRecord member = data.hiredUnits.get(memberId);
            if (member != null && member.populationReturned) terminalMember = true;
            if (isCampaignCombatMember(company, member)) campaignCombatMembers++;
        }
        if (campaignCombatMembers == 0)
            return CompanyAssessment.denied(
                Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS);
        if (terminalMember) return CompanyAssessment.denied(Reason.COMPANY_INCOHERENT);

        KOMECompanyCoherenceService.Assessment coherence =
            KOMECompanyCoherenceService.INSTANCE.assess(data, company);
        if (coherence.status == KOMECompanyCoherenceService.Status.INCOHERENT)
            return CompanyAssessment.denied(Reason.COMPANY_INCOHERENT);
        return CompanyAssessment.allowed(new EligibleCompany(company,
            campaignCombatMembers, coherence.status));
    }

    private static boolean isCampaignCombatMember(KOMEArmyCompany company,
            KOMEHiredUnitRecord member) {
        return member != null && member.entity != null && !member.populationReturned
            && KOMEHiredUnitClassification.isCampaignUnit(member)
            && !member.farmhand && member.type == KOMEPopulationType.OFFENSIVE
            && clean(company.id).equals(clean(member.companyId));
    }

    private static String currentPledgeFaction(EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null || !pledge.isPlayableAlignmentFaction()
            ? "" : faction(pledge.codeName());
    }

    private static boolean matches(KOMEConflictRecord record, ExpectedConflict expected) {
        return expected != null && !expected.isAbsent()
            && record.getConflictId().equals(expected.conflictId)
            && record.getRevision() == expected.revision;
    }

    private Projection denied(KOMEWorldData data, UUID playerId, String faction,
            String tileId, ExpectedConflict expected, boolean requireExpected, Reason fallback) {
        if (data == null || playerId == null)
            return new Projection(KOMEConquestTile.normalizeId(tileId), null,
                faction(faction), fallback, Collections.<EligibleCompany>emptyList());
        Projection evaluated = evaluateResolved(data, playerId, faction, tileId,
            expected, requireExpected);
        return evaluated.reason == Reason.ALLOWED
            ? new Projection(evaluated.tileId, data.getConflictService().get(evaluated.tileId),
                evaluated.playerFactionId, fallback, Collections.<EligibleCompany>emptyList())
            : evaluated;
    }

    private static Projection projection(Base base, Reason reason,
            List<EligibleCompany> companies) {
        return new Projection(base.tileId, base.record, base.playerFaction, reason, companies);
    }

    private static SelectionResult selection(Reason reason, Projection current,
            EligibleCompany selected) {
        return new SelectionResult(reason, current, selected);
    }

    private static String faction(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Base {
        final KOMEWorldData data;
        final UUID playerId;
        final String tileId;
        final String playerFaction;
        final KOMEConflictRecord record;
        final Reason reason;

        Base(KOMEWorldData data, UUID playerId, String tileId, String playerFaction,
                KOMEConflictRecord record, Reason reason) {
            this.data = data;
            this.playerId = playerId;
            this.tileId = tileId;
            this.playerFaction = playerFaction;
            this.record = record;
            this.reason = reason;
        }
    }

    private static final class CompanyAssessment {
        final Reason reason;
        final EligibleCompany company;

        private CompanyAssessment(Reason reason, EligibleCompany company) {
            this.reason = reason;
            this.company = company;
        }

        static CompanyAssessment denied(Reason reason) {
            return new CompanyAssessment(reason, null);
        }

        static CompanyAssessment allowed(EligibleCompany company) {
            return new CompanyAssessment(Reason.ALLOWED, company);
        }
    }
}
