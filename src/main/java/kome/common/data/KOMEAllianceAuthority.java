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

    public Decision canViewAlliance(EntityPlayerMP actor, KOMEAlliance alliance) {
        if (actor == null || alliance == null) {
            return Decision.deny("No alliance is available.");
        }
        if (isAdmin(actor) || alliance.involves(getPlayerFaction(actor))) {
            return Decision.allow();
        }
        return Decision.deny("Alliance details are private to participating factions.");
    }

    public Decision canUseAllianceLedger(EntityPlayerMP actor, KOMEAlliance alliance, boolean mutate) {
        Decision view = canViewAlliance(actor, alliance);
        if (!view.allowed) {
            return view;
        }
        if (!mutate || isAdmin(actor)) {
            return Decision.allow();
        }
        String faction = getPlayerFaction(actor);
        return alliance.involves(faction) ? Decision.allow()
            : Decision.deny("Only pledged members of a participating faction may contribute.");
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
        // KOME no longer adds an alliance or war gate to LOTR waypoint travel.
        // Native LOTR waypoint ownership and fast-travel checks remain authoritative.
        return true;
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
            return Decision.deny("Direct active opposition overrides Stage 4 delegation.");
        return canVoluntarilyDelegate(nativeKey, supportingKey) ? Decision.allow()
            : Decision.deny("The receiving faction needs directional Stage 4 Military Partnership.");
    }

    public Decision canControlTemporaryCompany(KOMEArmyCompany company, UUID actor) {
        if (data == null || company == null || actor == null || !actor.equals(company.temporaryController))
            return Decision.deny("The player is not the recorded temporary controller.");
        if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            KOMEWarService.AuthorizationDecision decision = KOMEWartimeStewardshipService.controllerDecision(data, company, actor);
            return decision.allowed ? Decision.allow() : Decision.deny(decision.reason);
        }
        if (!KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority))
            return Decision.deny("This company is not under Stage 4 temporary control.");
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

    public static LOTRFactionRelations.Relation getCurrentRelation(String firstFaction, String secondFaction) {
        LOTRFaction first = KOMEAlliance.findLotrFaction(firstFaction);
        LOTRFaction second = KOMEAlliance.findLotrFaction(secondFaction);
        if (first == null || second == null || first == second) {
            return LOTRFactionRelations.Relation.NEUTRAL;
        }
        return LOTRFactionRelations.getRelations(first, second);
    }

    public static class Decision {
        public final boolean allowed;
        public final String reason;

        private Decision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason == null ? "" : reason;
        }

        public static Decision allow() {
            return new Decision(true, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }}
