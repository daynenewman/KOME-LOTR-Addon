package kome.common.data;

import java.util.UUID;

/**
 * Canonical diplomacy authorization boundary for strategic company passage
 * and voluntary company delegation.
 */
public final class KOMECompanyDiplomacyAuthorization {
    private KOMECompanyDiplomacyAuthorization() {
    }

    public static Decision canUseMilitaryPassage(
            KOMEWorldData data, String movingFaction, String tileOwnerFaction) {
        String moving = KOMEAlliance.normalizeFactionKey(movingFaction);
        String owner = KOMEAlliance.normalizeFactionKey(tileOwnerFaction);

        if (data == null || moving.length() == 0 || owner.length() == 0) {
            return Decision.deny("Military passage requires two valid factions.");
        }
        if (moving.equals(owner)) {
            return Decision.allow();
        }
        if (isOpenlyHostile(data, moving, owner)) {
            return Decision.deny("Active war blocks military passage.");
        }
        if (!KOMEDiplomacyService.areAllies(data, moving, owner)) {
            return Decision.deny("Military passage requires canonical Allies.");
        }
        return Decision.allow();
    }

    public static Decision canStartDelegation(
            KOMEWorldData data, String nativeFaction, UUID delegatingKing, UUID recipient) {
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);

        if (data == null || nativeKey.length() == 0 || delegatingKing == null || recipient == null) {
            return Decision.deny("Delegation requires a native faction, King, and recipient.");
        }
        if (delegatingKing.equals(recipient)) {
            return Decision.deny("Delegation requires another player.");
        }
        if (!KOMERulerAuthorization.canActAsRuler(data, nativeKey, delegatingKing)
                || !nativeKey.equals(KOMEAlliance.normalizeFactionKey(
                    data.getPlayerFactionKey(delegatingKing)))) {
            return Decision.deny("Only the recognized, pledged native King may delegate company control.");
        }

        return recipientDecision(data, nativeKey, recipient);
    }

    public static Decision canContinueDelegation(
            KOMEWorldData data, String nativeFaction, UUID recipient) {
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);

        if (data == null || nativeKey.length() == 0 || recipient == null) {
            return Decision.deny("Delegation requires a native faction and recipient.");
        }

        return recipientDecision(data, nativeKey, recipient);
    }

    public static boolean isOpenlyHostile(
            KOMEWorldData data, String firstFaction, String secondFaction) {
        String first = KOMEAlliance.normalizeFactionKey(firstFaction);
        String second = KOMEAlliance.normalizeFactionKey(secondFaction);

        return data != null
            && first.length() > 0
            && second.length() > 0
            && !first.equals(second)
            && KOMEWarService.findActiveOpposition(data, first, second) != null;
    }

    private static Decision recipientDecision(
            KOMEWorldData data, String nativeFaction, UUID recipient) {
        String recipientFaction =
            KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(recipient));

        if (recipientFaction.length() == 0 || nativeFaction.equals(recipientFaction)) {
            return Decision.allow();
        }
        if (isOpenlyHostile(data, nativeFaction, recipientFaction)) {
            return Decision.deny("The recipient belongs to a faction openly hostile to the native faction.");
        }
        return Decision.allow();
    }

    public static final class Decision {
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
    }
}
