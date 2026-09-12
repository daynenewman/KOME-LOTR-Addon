package kome.common.data;

import kome.common.config.KOMEConfigRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Pure war-registry rules shared by conquest, commands, movement and records. */
public final class KOMEWarService {
    public static final long CLAIM_CONFIRMATION_MILLIS = 30_000L;
    private static volatile KOMEWarBondFundingProvider bondFundingProvider;
    public static void setBondFundingProvider(KOMEWarBondFundingProvider provider){bondFundingProvider=provider;}
    public static AuthorizationDecision postParticipationBond(KOMEWar war,String faction,String role,long now){int amount=KOMEConfigRegistry.season().isWarBondsEnabled()?KOMEConfigRegistry.season().getParticipationWarBond():0;if(amount<=0)return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());for(KOMEWar.BondEscrow e:war.bondEscrows)if(e.faction.equals(KOMEAlliance.normalizeFactionKey(faction))&&e.role.equals(role))return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());if(bondFundingProvider==null)return AuthorizationDecision.deny("War bonds require a funding provider; none is installed.");KOMEWarBondFundingProvider.Result r=bondFundingProvider.debit(faction,amount,"war-participation");if(!r.success)return AuthorizationDecision.deny("War bond funding failed: "+r.reason);KOMEWar.BondEscrow e=new KOMEWar.BondEscrow();e.faction=KOMEAlliance.normalizeFactionKey(faction);e.role=role;e.amount=amount;e.postedAtMillis=now;war.bondEscrows.add(e);return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());}
    public static AuthorizationDecision resolveBond(KOMEWar war,KOMEWar.BondEscrow e,String action,String destination,long now){if(e==null||!"HELD".equals(e.status))return AuthorizationDecision.deny("Escrow is already resolved.");if("FORFEIT".equals(action)){e.status="FORFEITED";e.resolvedAtMillis=now;return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());}if(bondFundingProvider==null)return AuthorizationDecision.deny("No funding provider is installed for escrow resolution.");String target="REFUND".equals(action)?e.faction:destination;if(target==null||target.length()==0)return AuthorizationDecision.deny("A payout destination is required.");KOMEWarBondFundingProvider.Result r=bondFundingProvider.credit(target,e.amount,"war-escrow-"+action);if(!r.success)return AuthorizationDecision.deny(r.reason);e.status=action;e.resolvedAtMillis=now;return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());}

    private KOMEWarService() {
    }

    public static KOMEWar createWar(KOMEWorldData data, String first, String second, String name,
            String actor, long now) {
        return createWarResult(data,first,second,name,actor,now).war;
    }
    public static CreationResult createWarResult(KOMEWorldData data,String first,String second,String name,String actor,long now) {
        KOMEWar existing=findActiveOpposition(data,first,second); if(existing!=null)return CreationResult.ok(existing);
        int bond=KOMEConfigRegistry.season().isWarBondsEnabled()?KOMEConfigRegistry.season().getAttackerWarBond():0;
        if(bond>0 && bondFundingProvider==null) return CreationResult.deny("War bonds require a funding provider; none is installed.");
        if(bond>0){KOMEWarBondFundingProvider.Result paid=bondFundingProvider.debit(first,bond,"war-declaration");if(!paid.success)return CreationResult.deny("War bond funding failed: "+paid.reason);}
        KOMEWar war = createWarInternal(data, first, second, name, actor, now, true);
        if(war!=null && bond>0){KOMEWar.BondEscrow e=new KOMEWar.BondEscrow();e.faction=KOMEAlliance.normalizeFactionKey(first);e.role="ATTACKER_DECLARATION";e.amount=bond;e.postedAtMillis=Math.max(0L,now);war.bondEscrows.add(e);}
        if (war != null) recordFirstLegalConflict(data, now);
        return war==null?CreationResult.deny("Could not create the war record."):CreationResult.ok(war);
    }
    public static final class CreationResult { public final KOMEWar war; public final String reason; private CreationResult(KOMEWar w,String r){war=w;reason=r;} static CreationResult ok(KOMEWar w){return new CreationResult(w,"");} static CreationResult deny(String r){return new CreationResult(null,r);} }
    public static AuthorizationDecision declarationBondDecision(String attacker){int bond=KOMEConfigRegistry.season().isWarBondsEnabled()?KOMEConfigRegistry.season().getAttackerWarBond():0;if(bond<=0)return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());if(bondFundingProvider==null)return AuthorizationDecision.deny("War bonds require a funding provider; none is installed.");return AuthorizationDecision.allow(Collections.<KOMEWar>emptyList());}

    /** Canonical integration point for any already-validated conflict source. */
    public static KOMEWarSeasonState.TransitionResult recordFirstLegalConflict(KOMEWorldData data, long now) {
        if (data == null) return KOMEWarSeasonState.TransitionResult.denied("Missing world data.");
        long duration = KOMEConfigRegistry.season().getMinimumWarSeasonLengthDays().isPresent()
            ? KOMEConfigRegistry.season().getMinimumWarSeasonLengthDays().getAsInt() * 86400000L : -1L;
        KOMEWarSeasonState.TransitionResult result = data.warSeason.recordLegalConflict(now, duration);
        if (result.allowed) data.markDirty();
        return result;
    }

    private static KOMEWar createWarInternal(KOMEWorldData data, String first, String second, String name,
            String actor, long now, boolean revalidateMovement) {
        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);
        if (a.length() == 0 || b.length() == 0 || a.equals(b)) return null;
        KOMEWar existing = findActiveOpposition(data, a, b);
        if (existing != null) return existing;
        KOMEWar war = new KOMEWar();
        war.id = nextWarId(data);
        war.displayName = name == null ? "" : name.trim();
        war.sideOneFactions.add(a);
        war.sideTwoFactions.add(b);
        war.initiatingFaction = a;
        war.defendingFaction = b;
        war.createdAtMillis = Math.max(0L, now);
        war.lastUpdatedAtMillis = war.createdAtMillis;
        war.lastActivePressureAtMillis = war.createdAtMillis;
        war.addAdministrativeEvent(actor, "CREATE", a + " opposed to " + b, now);
        war.recordMembership(a, 1, "MANUAL", "", actor, now);
        war.recordMembership(b, 2, "MANUAL", "", actor, now);
        data.wars.put(war.id, war);
        reconcileAutomaticMilitarySupport(data, now, "War created");
        if (revalidateMovement) {
            KOMEMovementAccessService.revalidateAll(data, now);
            KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, now);
            data.markDirty();
        }
        return war;
    }

    public static KOMEWar recordHostileCapture(KOMEWorldData data, String tileId, String formerOwner,
            String newOwner, UUID claimant, String claimantName, long now, String claimMethod) {
        String former = KOMEAlliance.normalizeFactionKey(formerOwner);
        String next = KOMEAlliance.normalizeFactionKey(newOwner);
        if (former.length() == 0 || next.length() == 0 || former.equals(next)) return null;
        KOMEWar war = findActiveOpposition(data, former, next);
        if (war == null) {
            war = createWarInternal(data, next, former, "", claimantName, now, false);
            if (war != null) {
                war.endMembership(next, "Capture-created war provenance correction", now);
                war.endMembership(former, "Capture-created war provenance correction", now);
                war.recordMembership(next, 1, "CAPTURE", "", claimantName, now);
                war.recordMembership(former, 2, "CAPTURE", "", claimantName, now);
            }
        }
        if (war != null) {
            recordFirstLegalConflict(data, now);
            recordHostilePressure(data, war, now, "HOSTILE_CAPTURE");
            war.addTileCapture(tileId, former, next, claimant, claimantName, now, claimMethod);
            reconcileAutomaticMilitarySupport(data, now, "Capture updated active war");
            KOMEMovementAccessService.revalidateAll(data, now);
            KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, now);
            data.markDirty();
        }
        return war;
    }

    /** Hook for conflict, encirclement, siege, relief, and future systems; timestamps are caller supplied. */
    public static boolean recordHostilePressure(KOMEWorldData data, KOMEWar war, long now, String source) {
        if (data == null || war == null || !war.isActive()) return false;
        long timestamp = Math.max(war.lastActivePressureAtMillis, Math.max(0L, now));
        if (timestamp == war.lastActivePressureAtMillis) return false;
        war.lastActivePressureAtMillis = timestamp;
        war.lastUpdatedAtMillis = Math.max(war.lastUpdatedAtMillis, timestamp);
        war.addAdministrativeEvent("system", "ACTIVE_PRESSURE", source == null ? "" : source, timestamp);
        data.markDirty(); return true;
    }

    public static boolean isInactivityEligible(KOMEWar war, long now, long thresholdMillis) {
        return war != null && war.isActive() && thresholdMillis >= 0L && Math.max(0L, now) - war.lastActivePressureAtMillis >= thresholdMillis;
    }
    public static boolean isInactivityEligible(KOMEWar war, long now) {
        return KOMEConfigRegistry.season().getWarInactivityDurationMillis().isPresent()
            && isInactivityEligible(war, now, KOMEConfigRegistry.season().getWarInactivityDurationMillis().getAsInt());
    }

    public static KOMEWar findActiveOpposition(KOMEWorldData data, String first, String second) {
        if (data == null) return null;
        List<KOMEWar> sorted = sortedWars(data);
        for (KOMEWar war : sorted) if (war.isActive() && war.opposes(first, second)) return war;
        return null;
    }

    public static List<KOMEWar> findActiveSameSide(KOMEWorldData data, String first, String second) {
        List<KOMEWar> result = new ArrayList<KOMEWar>();
        if (data == null) return result;
        for (KOMEWar war : sortedWars(data)) if (war.isActive() && war.sameSide(first, second)) result.add(war);
        return result;
    }

    public static List<KOMEWar> authorizedSameSideWars(KOMEWorldData data, String nativeFaction,
            String controllerFaction) {
        List<KOMEWar> result = new ArrayList<KOMEWar>();
        if (data == null || data.hasFactionKing(nativeFaction)) return result;
        if (findActiveOpposition(data, nativeFaction, controllerFaction) != null) return result;
        // KOM-31: stewardship is a wartime defensive authority, not an alliance-progression reward.
        // Friends is the minimum canonical diplomacy relationship; the supporting faction must
        // also already be an explicit member of the native faction's active war side.
        if (!KOMEDiplomacyService.relationAtLeast(data, nativeFaction, controllerFaction,
                KOMEDiplomacyRelation.FRIENDS)) {
            return result;
        }
        for (KOMEWar war : sortedWars(data)) {
            if (war.isActive() && war.sameSide(nativeFaction, controllerFaction)) result.add(war);
        }
        return result;
    }

    public static AuthorizationDecision supportingKingDecision(KOMEWorldData data, String nativeFaction,
            String supportingFaction, UUID actor) {
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);
        String supportingKey = KOMEAlliance.normalizeFactionKey(supportingFaction);
        if (data == null || nativeKey.length() == 0 || supportingKey.length() == 0 || actor == null)
            return AuthorizationDecision.deny("Missing stewardship identity.");
        if (data.hasFactionKing(nativeKey))
            return AuthorizationDecision.deny("Wartime Stewardship is dormant because the native faction has a king.");
        if (!KOMERulerAuthorization.canActAsRuler(data, supportingKey, actor))
            return AuthorizationDecision.deny("Only the currently recognized supporting king may use Wartime Stewardship.");
        if (!supportingKey.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor))))
            return AuthorizationDecision.deny("The recognized supporting king is not actually pledged to the supporting faction.");
        if (findActiveOpposition(data, nativeKey, supportingKey) != null)
            return AuthorizationDecision.deny("The supporting faction is directly opposed to the native faction in an active war.");
        List<KOMEWar> wars = authorizedSameSideWars(data, nativeKey, supportingKey);
        if (wars.isEmpty())
            return AuthorizationDecision.deny("Stewardship requires Friends-or-better diplomacy and an active war with both factions on the same side.");
        return AuthorizationDecision.allow(wars);
    }

    /**
     * Revalidates the historical support records without manufacturing war membership.
     * A supporting faction must now join a war through its canonical war-membership path;
     * Stage 4 progression never grants or auto-enrols stewardship authority.
     */
    public static boolean reconcileAutomaticMilitarySupport(KOMEWorldData data, long now, String reason) {
        if (data == null) return false;
        boolean changed = false;
        for (KOMEWar war : sortedWars(data)) {
                for (KOMEWar.MilitarySupportEnrollment enrollment : war.militarySupportEnrollments) {
                    int nativeSide = war.sideOf(enrollment.nativeFaction);
                    int supportingSide = war.sideOf(enrollment.supportingFaction);
                    if (!war.isActive()) {
                        changed |= updateEnrollment(enrollment, "DORMANT", null, "",
                            "The authorizing war is no longer ACTIVE", nativeSide, now);
                    } else if ("OPERATOR_REMOVED".equals(enrollment.state) && supportingSide == 0) {
                        changed |= updateEnrollment(enrollment, "OPERATOR_REMOVED", null, "",
                            "Automatic support membership was explicitly removed by an operator", nativeSide, now);
                    } else if (nativeSide == 0) {
                        changed |= updateEnrollment(enrollment, "DORMANT", null, "",
                            "The native faction is no longer a member of this war", 0, now);
                    } else if (supportingSide > 0 && supportingSide != nativeSide) {
                        changed |= updateEnrollment(enrollment, "CONTRADICTION", null, "",
                            "Supporting faction is already on the opposing side; operator resolution is required", nativeSide, now);
                    } else if (data.hasFactionKing(enrollment.nativeFaction)) {
                        changed |= updateEnrollment(enrollment, "DORMANT", null, "",
                            "The native faction has a recognized king; kingless Wartime Stewardship is dormant", nativeSide, now);
                    } else if (!KOMEDiplomacyService.relationAtLeast(data, enrollment.nativeFaction,
                            enrollment.supportingFaction, KOMEDiplomacyRelation.FRIENDS)) {
                        changed |= updateEnrollment(enrollment, "DORMANT", null, "",
                            "Friends-or-better canonical diplomacy is not active", nativeSide, now);
                    }
                }
        }
        KOMEWartimeStewardshipService.revalidateAll(data, now, reason);
        if (changed) data.markDirty();
        return changed;
    }

    private static boolean updateEnrollment(KOMEWar.MilitarySupportEnrollment enrollment, String state,
            UUID king, String kingName, String reason, int side, long now) {
        String safeState = state == null ? "DORMANT" : state;
        String safeName = kingName == null ? "" : kingName;
        String safeReason = reason == null ? "" : reason;
        boolean changed = enrollment.side != side || !safeState.equals(enrollment.state)
            || (enrollment.authorizedKing == null ? king != null : !enrollment.authorizedKing.equals(king))
            || !safeName.equals(enrollment.authorizedKingName) || !safeReason.equals(enrollment.reason);
        enrollment.side = side;
        enrollment.state = safeState;
        enrollment.authorizedKing = king;
        enrollment.authorizedKingName = safeName;
        enrollment.reason = safeReason;
        if (enrollment.enrolledAtMillis <= 0L) enrollment.enrolledAtMillis = Math.max(0L, now);
        if (changed || enrollment.updatedAtMillis <= 0L) enrollment.updatedAtMillis = Math.max(0L, now);
        return changed;
    }

    public static Set<String> authorizedOpponents(KOMEWorldData data, String nativeFaction,
            String controllerFaction) {
        Set<String> result = new HashSet<String>();
        for (KOMEWar war : authorizedSameSideWars(data, nativeFaction, controllerFaction)) {
            result.addAll(war.getOpposingFactions(nativeFaction));
        }
        return result;
    }

    public static String authorizationWarIds(KOMEWorldData data, String nativeFaction, String controllerFaction) {
        StringBuilder result = new StringBuilder();
        for (KOMEWar war : authorizedSameSideWars(data, nativeFaction, controllerFaction)) {
            if (result.length() > 0) result.append(',');
            result.append(war.id);
        }
        return result.toString();
    }

    public static String allianceFingerprint(KOMEWorldData data, String first, String second) {
        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);
        StringBuilder result = new StringBuilder();

        KOMEDiplomacyRelation relation =
            KOMEDiplomacyService.getRelation(data, a, b);

        result.append("diplomacy=").append(relation.key);

        KOMEDiplomacyRecord record = null;
        if (data != null
                && a.length() > 0
                && b.length() > 0
                && !a.equals(b)) {
            record = KOMEDiplomacyService.records(data).get(
                KOMEDiplomacyRecord.pairKey(a, b));
        }

        if (record != null && record.pendingTarget != null) {
            result.append("|pending=")
                .append(record.pendingTarget.key)
                .append(':')
                .append(record.requestingFaction)
                .append(':')
                .append(record.receivingFaction);
        }

        result.append("|same=");
        for (KOMEWar war : findActiveSameSide(data, a, b)) {
            result.append(war.id).append(',');
        }

        result.append("|activeMembership=");
        for (KOMEWar war : sortedWars(data)) {
            if (war.isActive()
                    && (war.sideOf(a) > 0 || war.sideOf(b) > 0)) {
                result.append(war.id)
                    .append(':')
                    .append(war.sideOf(a))
                    .append(':')
                    .append(war.sideOf(b))
                    .append(',');
            }
        }

        return result.toString();
    }

    public static boolean requiresHostileConfirmation(
            KOMEWorldData data,
            String first,
            String second) {
        return KOMEDiplomacyService.relationAtLeast(
                data,
                first,
                second,
                KOMEDiplomacyRelation.FRIENDS)
            || !findActiveSameSide(data, first, second).isEmpty();
    }
    public static List<String> contradictoryMemberships(KOMEWorldData data, String faction) {
        List<String> result = new ArrayList<String>();
        if (data == null) return result;
        String key = KOMEAlliance.normalizeFactionKey(faction);
        List<KOMEWar> active = new ArrayList<KOMEWar>();
        for (KOMEWar war : sortedWars(data)) if (war.isActive() && war.sideOf(key) > 0) active.add(war);
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                for (String other : active.get(i).getOpposingFactions(key)) {
                    if (active.get(j).sameSide(key, other)) {
                        result.add(active.get(i).id + " opposes " + other + " but " + active.get(j).id + " places it on the same side");
                    }
                }
            }
        }
        return result;
    }

    public static List<KOMEWar> sortedWars(KOMEWorldData data) {
        List<KOMEWar> result = new ArrayList<KOMEWar>(data.wars.values());
        Collections.sort(result, new java.util.Comparator<KOMEWar>() {
            public int compare(KOMEWar a, KOMEWar b) { return a.id.compareTo(b.id); }
        });
        return result;
    }

    private static String nextWarId(KOMEWorldData data) {
        int value = Math.max(1, data.nextWarSequence);
        String id;
        do { id = String.format("war-%05d", Integer.valueOf(value++)); }
        while (data.wars.containsKey(id));
        data.nextWarSequence = value;
        return id;
    }

    public static final class AuthorizationDecision {
        public final boolean allowed;
        public final String reason;
        public final List<KOMEWar> wars;

        private AuthorizationDecision(boolean allowed, String reason, List<KOMEWar> wars) {
            this.allowed = allowed;
            this.reason = reason;
            this.wars = wars;
        }

        static AuthorizationDecision allow(List<KOMEWar> wars) {
            return new AuthorizationDecision(true, "", new ArrayList<KOMEWar>(wars));
        }

        static AuthorizationDecision deny(String reason) {
            return new AuthorizationDecision(false, reason, new ArrayList<KOMEWar>());
        }
    }
}
