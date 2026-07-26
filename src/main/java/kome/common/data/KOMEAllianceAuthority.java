package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public class KOMEAllianceAuthority {
    public static final long FOURTEEN_DAYS_MILLIS = 14L * 24L * 60L * 60L * 1000L;

    private final KOMEWorldData data;

    public KOMEAllianceAuthority(KOMEWorldData data) {
        this.data = data;
    }

    public Decision canRequestAlliance(EntityPlayerMP actor, String type, String fromFaction, String toFaction) {
        String normalizedType = KOMEAlliance.normalizeType(type);
        String from = KOMEAlliance.normalizeFactionKey(fromFaction);
        String to = KOMEAlliance.normalizeFactionKey(toFaction);
        if (actor == null || data == null) {
            return Decision.deny("A server player is required.");
        }
        String actorFaction = getPlayerFaction(actor);
        boolean actorIsFromKing = data.isFactionKing(from, KOMEReflection.getEntityUUID(actor));
        return decideRequestAlliance(normalizedType, from, to, actorFaction, actorIsFromKing,
            data.hasFactionKing(from), data.hasFactionKing(to), getDefaultRelation(from, to));
    }

    static Decision decideRequestAlliance(String type, String fromFaction, String toFaction, String actorFaction,
            boolean actorIsFromKing, boolean fromHasKing, boolean toHasKing, LOTRFactionRelations.Relation relation) {
        String from = KOMEAlliance.normalizeFactionKey(fromFaction);
        String to = KOMEAlliance.normalizeFactionKey(toFaction);
        String actorSide = KOMEAlliance.normalizeFactionKey(actorFaction);
        if (from.length() == 0 || to.length() == 0 || from.equals(to)) {
            return Decision.deny("Choose two different playable factions.");
        }
        if (!from.equals(actorSide)) {
            return Decision.deny("You may negotiate only for your pledged faction.");
        }
        if (!fromHasKing || !actorIsFromKing) {
            return Decision.deny("Only your faction king may send alliance requests.");
        }
        if (toHasKing) {
            // Two sovereign kings may negotiate across hostile lore defaults. Acceptance remains receiver-only.
            return Decision.allow(false);
        }
        int automaticStage = automaticStageForKinglessRelation(relation);
        if (automaticStage < 0) {
            return Decision.deny("The original LOTR relation is " + relationName(relation)
                + "; Enemy and Mortal Enemy kingless factions cannot auto-accept.");
        }
        return Decision.allow(true, automaticStage);
    }

    public Decision canAcceptAlliance(EntityPlayerMP actor, KOMEAlliance alliance, String type, String receivingFaction) {
        String receiving = KOMEAlliance.normalizeFactionKey(receivingFaction);
        if (actor == null || alliance == null || !alliance.involves(receiving)) {
            return Decision.deny("No matching mutual alliance request exists.");
        }
        return decideAcceptAlliance(alliance.getStatus(type) == KOMEAllianceTrackStatus.PENDING,
            alliance.getPendingReceiver(type), receiving, isFactionKing(actor, receiving));
    }

    static Decision decideAcceptAlliance(boolean pending, String pendingReceiver, String attemptedReceiver,
            boolean actorIsAttemptedReceiverKing) {
        String expected = KOMEAlliance.normalizeFactionKey(pendingReceiver);
        String attempted = KOMEAlliance.normalizeFactionKey(attemptedReceiver);
        if (!pending) {
            return Decision.deny("That agreement is not pending.");
        }
        if (expected.length() > 0 && !expected.equals(attempted)) {
            return Decision.deny("Only " + KOMEAlliance.displayFactionName(expected) + " may accept this request.");
        }
        return actorIsAttemptedReceiverKing ? Decision.allow(false)
            : Decision.deny("Only the receiving faction king may accept.");
    }

    public Decision canBreakAlliance(EntityPlayerMP actor, KOMEAlliance alliance) {
        if (actor == null || alliance == null) {
            return Decision.deny("No matching mutual alliance exists.");
        }
        if (isAdmin(actor)) {
            return Decision.allow(false);
        }
        UUID actorId = KOMEReflection.getEntityUUID(actor);
        if (data.isFactionKing(alliance.factionA, actorId) || data.isFactionKing(alliance.factionB, actorId)) {
            return Decision.allow(false);
        }
        return Decision.deny("Only a participating faction king or an administrator may break this alliance.");
    }

    public Decision canViewAlliance(EntityPlayerMP actor, KOMEAlliance alliance) {
        if (actor == null || alliance == null) {
            return Decision.deny("No alliance is available.");
        }
        if (isAdmin(actor) || alliance.involves(getPlayerFaction(actor))) {
            return Decision.allow(false);
        }
        return Decision.deny("Alliance details are private to participating factions.");
    }

    public Decision canUseAllianceLedger(EntityPlayerMP actor, KOMEAlliance alliance, boolean mutate) {
        Decision view = canViewAlliance(actor, alliance);
        if (!view.allowed) {
            return view;
        }
        if (!mutate || isAdmin(actor)) {
            return Decision.allow(false);
        }
        String faction = getPlayerFaction(actor);
        return alliance.involves(faction) ? Decision.allow(false)
            : Decision.deny("Only pledged members of a participating faction may contribute.");
    }

    public int getEffectiveTier(String firstFaction, String secondFaction, String type, long nowMillis) {
        KOMEAlliance alliance = data == null ? null : data.getAlliance(firstFaction, secondFaction, false);
        return getEffectiveTier(alliance, firstFaction, type, nowMillis);
    }

    /**
     * Legacy non-directional query. It deliberately returns the lower side so callers cannot gain
     * a benefit that only the other faction unlocked.
     */
    public int getEffectiveTier(KOMEAlliance alliance, String type, long nowMillis) {
        if (alliance == null) {
            return KOMEAlliance.NONE;
        }
        return Math.min(getEffectiveTier(alliance, alliance.factionA, type, nowMillis),
            getEffectiveTier(alliance, alliance.factionB, type, nowMillis));
    }

    public int getEffectiveTier(KOMEAlliance alliance, String actingFaction, String type, long nowMillis) {
        if (alliance == null || alliance.getStatus(type) != KOMEAllianceTrackStatus.ACTIVE) {
            return KOMEAlliance.NONE;
        }
        return alliance.getFactionTier(actingFaction, type);
    }

    public int getEffectiveStage(String actingFaction, String partnerFaction) {
        KOMEAlliance alliance = data == null ? null : data.getAlliance(actingFaction, partnerFaction, false);
        return alliance == null || alliance.getRelationshipStatus() != KOMEAllianceTrackStatus.ACTIVE
            ? KOMEAlliance.NONE : alliance.getFactionStage(actingFaction);
    }

    public boolean isTierProvisional(KOMEAlliance alliance, String type, long nowMillis) {
        return false;
    }

    public boolean canFactionUseAlliedWaypoint(String travelerFaction, String waypointFaction) {
        return !isDirectlyHostile(travelerFaction, waypointFaction);
    }

    public boolean canFactionHireAlliedFarmhand(String hiringFaction, String unitFaction) {
        return !isDirectlyHostile(hiringFaction, unitFaction)
            && getEffectiveStage(hiringFaction, unitFaction) >= 1;
    }

    public boolean canFactionHireAlliedMilitaryUnit(String hiringFaction, String unitFaction) {
        return data != null && !data.hasFactionKing(unitFaction) && !isDirectlyHostile(hiringFaction, unitFaction)
            && getEffectiveStage(hiringFaction, unitFaction) >= 4
            && !KOMEWarService.authorizedSameSideWars(data, unitFaction, hiringFaction).isEmpty();
    }

    public boolean canFactionUseMilitaryPassage(String movingFaction, String tileOwnerFaction) {
        String moving = KOMEAlliance.normalizeFactionKey(movingFaction);
        String owner = KOMEAlliance.normalizeFactionKey(tileOwnerFaction);
        return moving.length() > 0 && (moving.equals(owner)
            || !isDirectlyHostile(moving, owner)
                && getEffectiveStage(moving, owner) >= 3);
    }

    public boolean canTemporarilyCommand(String companyFaction, String controllerFaction) {
        if (isDirectlyHostile(companyFaction, controllerFaction)
                || getEffectiveStage(controllerFaction, companyFaction) < 4) {
            return false;
        }
        return data != null && (data.hasFactionKing(companyFaction)
            || !KOMEWarService.authorizedSameSideWars(data, companyFaction, controllerFaction).isEmpty());
    }

    public boolean canVoluntarilyDelegate(String nativeFaction, String controllerFaction) {
        return data != null && data.hasFactionKing(nativeFaction) && canTemporarilyCommand(nativeFaction, controllerFaction);
    }

    public Decision canVoluntarilyDelegate(String nativeFaction, UUID nativeKing, String controllerFaction,
            UUID supportingKing) {
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);
        String supportingKey = KOMEAlliance.normalizeFactionKey(controllerFaction);
        if (data == null || nativeKing == null || supportingKing == null)
            return Decision.deny("Both recognized kings are required.");
        if (!data.isFactionKing(nativeKey, nativeKing)
                || !nativeKey.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(nativeKing))))
            return Decision.deny("Only the recognized, pledged native king may delegate a personally owned company.");
        if (!data.isFactionKing(supportingKey, supportingKing)
                || !supportingKey.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(supportingKing))))
            return Decision.deny("The recipient must be the recognized, pledged king of the supporting faction.");
        if (isDirectlyHostile(nativeKey, supportingKey))
            return Decision.deny("Direct active opposition overrides Military T3 delegation.");
        return canVoluntarilyDelegate(nativeKey, supportingKey) ? Decision.allow(false)
            : Decision.deny("The receiving faction needs directional Stage 4 Military Partnership.");
    }

    public Decision canControlTemporaryCompany(KOMEArmyCompany company, UUID actor) {
        if (data == null || company == null || actor == null || !actor.equals(company.temporaryController))
            return Decision.deny("The player is not the recorded temporary controller.");
        if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            KOMEWarService.AuthorizationDecision decision = KOMEWartimeStewardshipService.controllerDecision(data, company, actor);
            return decision.allowed ? Decision.allow(false) : Decision.deny(decision.reason);
        }
        if (!KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority))
            return Decision.deny("This company is not under Military T3 temporary control.");
        String nativeFaction = KOMEWartimeStewardshipService.nativeFaction(company);
        String supportingFaction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor));
        if (company.owner == null || company.delegatedBy == null || !company.owner.equals(company.delegatedBy))
            return Decision.deny("Voluntary delegation is limited to a native king's personally owned company.");
        return canVoluntarilyDelegate(nativeFaction, company.delegatedBy, supportingFaction, actor);
    }

    public boolean canWartimeSteward(String nativeFaction, String controllerFaction) {
        return data != null && !data.hasFactionKing(nativeFaction)
            && !KOMEWarService.authorizedSameSideWars(data, nativeFaction, controllerFaction).isEmpty();
    }

    private boolean isDirectlyHostile(String first, String second) {
        return data != null && KOMEWarService.findActiveOpposition(data, first, second) != null;
    }

    public String getPlayerFaction(EntityPlayerMP player) {
        if (player == null || data == null) {
            return "";
        }
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    public boolean isFactionKing(EntityPlayerMP player, String faction) {
        return player != null && data != null
            && data.isFactionKing(faction, KOMEReflection.getEntityUUID(player));
    }

    public boolean isAdmin(EntityPlayerMP player) {
        return player != null && player.canCommandSenderUseCommand(2, "alliance");
    }

    public static LOTRFactionRelations.Relation getDefaultRelation(String firstFaction, String secondFaction) {
        LOTRFaction first = KOMEAlliance.findLotrFaction(firstFaction);
        LOTRFaction second = KOMEAlliance.findLotrFaction(secondFaction);
        if (first == null || second == null || first == second) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        try {
            java.lang.reflect.Method method = LOTRFactionRelations.class.getDeclaredMethod("getFromDefaultMap", LOTRFactionRelations.FactionPair.class);
            method.setAccessible(true);
            Object value = method.invoke(null, new LOTRFactionRelations.FactionPair(first, second));
            if (value instanceof LOTRFactionRelations.Relation) {
                return (LOTRFactionRelations.Relation) value;
            }
        } catch (Throwable ignored) {
        }
        return LOTRFactionRelations.getRelations(first, second);
    }

    public static LOTRFactionRelations.Relation getCurrentRelation(String firstFaction, String secondFaction) {
        LOTRFaction first = KOMEAlliance.findLotrFaction(firstFaction);
        LOTRFaction second = KOMEAlliance.findLotrFaction(secondFaction);
        if (first == null || second == null || first == second) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        return LOTRFactionRelations.getRelations(first, second);
    }

    public static boolean relationAllows(String type, LOTRFactionRelations.Relation relation) {
        String normalizedType = KOMEAlliance.normalizeType(type);
        if (KOMEAlliance.MILITARY.equals(normalizedType)) {
            return relation == LOTRFactionRelations.Relation.ALLY;
        }
        if (KOMEAlliance.TRADE.equals(normalizedType)) {
            return relation == LOTRFactionRelations.Relation.ALLY || relation == LOTRFactionRelations.Relation.FRIEND;
        }
        return KOMEAlliance.CIVIL.equals(normalizedType) && (relation == LOTRFactionRelations.Relation.ALLY
            || relation == LOTRFactionRelations.Relation.FRIEND || relation == LOTRFactionRelations.Relation.NEUTRAL);
    }

    /** Neutral -> Stage 1, Friend -> Stage 2, Ally -> Stage 3, hostile -> reject. */
    public static int automaticStageForKinglessRelation(LOTRFactionRelations.Relation relation) {
        if (relation == LOTRFactionRelations.Relation.ALLY) return 3;
        if (relation == LOTRFactionRelations.Relation.FRIEND) return 2;
        if (relation == LOTRFactionRelations.Relation.NEUTRAL) return 1;
        return -1;
    }

    public static String relationName(LOTRFactionRelations.Relation relation) {
        if (relation == LOTRFactionRelations.Relation.MORTAL_ENEMY) {
            return "Mortal Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.ENEMY) {
            return "Enemy";
        }
        if (relation == LOTRFactionRelations.Relation.FRIEND) {
            return "Friend";
        }
        if (relation == LOTRFactionRelations.Relation.ALLY) {
            return "Ally";
        }
        return "Neutral";
    }

    private static int provisionalTier(KOMEAllianceFactionLedger ledger, String type) {
        String normalizedType = KOMEAlliance.normalizeType(type);
        if (KOMEAlliance.CIVIL.equals(normalizedType)) {
            return ledger.provisionalCivilTier;
        }
        if (KOMEAlliance.TRADE.equals(normalizedType)) {
            return ledger.provisionalTradeTier;
        }
        if (KOMEAlliance.MILITARY.equals(normalizedType)) {
            return ledger.provisionalMilitaryTier;
        }
        return KOMEAlliance.NONE;
    }

    public static class Decision {
        public final boolean allowed;
        public final boolean automaticAcceptance;
        public final int automaticStage;
        public final String reason;

        private Decision(boolean allowed, boolean automaticAcceptance, int automaticStage, String reason) {
            this.allowed = allowed;
            this.automaticAcceptance = automaticAcceptance;
            this.automaticStage = Math.max(0, Math.min(3, automaticStage));
            this.reason = reason == null ? "" : reason;
        }

        public static Decision allow(boolean automaticAcceptance) {
            return new Decision(true, automaticAcceptance, 0, "");
        }

        public static Decision allow(boolean automaticAcceptance, int automaticStage) {
            return new Decision(true, automaticAcceptance, automaticStage, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, false, 0, reason);
        }
    }
}
