package kome.common.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECompanyDiplomacyAuthorizationTest {
    @Test
    public void militaryPassageUsesCanonicalAlliesAndActiveWarVeto() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID gondorKing = crown(data, "gondor_test", "Gondor King");
        UUID rohanKing = crown(data, "rohan_test", "Rohan King");

        assertFalse(KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(data, "gondor_test", "rohan_test").allowed);

        increaseRelation(
            data, "gondor_test", "rohan_test",
            KOMEDiplomacyRelation.FRIENDS, gondorKing, rohanKing, 10L);

        assertFalse(KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(data, "gondor_test", "rohan_test").allowed);

        increaseRelation(
            data, "gondor_test", "rohan_test",
            KOMEDiplomacyRelation.ALLIES, gondorKing, rohanKing, 20L);

        assertTrue(KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(data, "gondor_test", "rohan_test").allowed);
        assertTrue(KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(data, "gondor_test", "gondor_test").allowed);

        assertNotNull(KOMEWarService.createWar(
            data, "gondor_test", "rohan_test", "Test War", "test", 30L));

        assertFalse(KOMECompanyDiplomacyAuthorization
            .canUseMilitaryPassage(data, "gondor_test", "rohan_test").allowed);
    }

    @Test
    public void delegationRequiresNativeKingButRecipientNeedNotBeKingOrAlly() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID nativeKing = crown(data, "native_test", "Native King");

        UUID neutralRecipient = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(neutralRecipient, "neutral_test");

        assertTrue(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", nativeKing, neutralRecipient).allowed);
        assertTrue(KOMECompanyDiplomacyAuthorization
            .canContinueDelegation(data, "native_test", neutralRecipient).allowed);

        UUID unpledgedRecipient = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(unpledgedRecipient, "");

        assertTrue(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", nativeKing, unpledgedRecipient).allowed);

        assertFalse(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", UUID.randomUUID(), neutralRecipient).allowed);
        assertFalse(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", nativeKing, nativeKing).allowed);
    }

    @Test
    public void activeOppositionRevokesOtherwiseValidDelegation() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID nativeKing = crown(data, "native_test", "Native King");
        UUID recipient = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(recipient, "recipient_test");

        assertTrue(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", nativeKing, recipient).allowed);

        assertNotNull(KOMEWarService.createWar(
            data, "native_test", "recipient_test", "Test War", "test", 40L));

        assertFalse(KOMECompanyDiplomacyAuthorization
            .canStartDelegation(data, "native_test", nativeKing, recipient).allowed);
        assertFalse(KOMECompanyDiplomacyAuthorization
            .canContinueDelegation(data, "native_test", recipient).allowed);
    }

    @Test
    public void worldDataPassageBoundaryUsesCanonicalDiplomacy() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID gondorKing = crown(data, "gondor_test", "Gondor King");
        UUID rohanKing = crown(data, "rohan_test", "Rohan King");

        assertFalse(data.canFactionUseMilitaryPassage("gondor_test", "rohan_test"));

        increaseRelation(
            data, "gondor_test", "rohan_test",
            KOMEDiplomacyRelation.FRIENDS, gondorKing, rohanKing, 10L);

        assertFalse(data.canFactionUseMilitaryPassage("gondor_test", "rohan_test"));

        increaseRelation(
            data, "gondor_test", "rohan_test",
            KOMEDiplomacyRelation.ALLIES, gondorKing, rohanKing, 20L);

        assertTrue(data.canFactionUseMilitaryPassage("gondor_test", "rohan_test"));

        assertNotNull(KOMEWarService.createWar(
            data, "gondor_test", "rohan_test", "Test War", "test", 30L));

        assertFalse(data.canFactionUseMilitaryPassage("gondor_test", "rohan_test"));
    }
    private static UUID crown(KOMEWorldData data, String faction, String name) {
        UUID king = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(king, faction);
        assertTrue(data.claimFactionKing(faction, faction, king, name));
        return king;
    }

    private static void increaseRelation(
            KOMEWorldData data,
            String from,
            String to,
            KOMEDiplomacyRelation target,
            UUID fromKing,
            UUID toKing,
            long now) {
        assertTrue(KOMEDiplomacyService
            .requestIncrease(data, from, to, target, fromKing, now).accepted);
        assertTrue(KOMEDiplomacyService
            .acceptPendingIncrease(data, to, from, toKing, now + 1L).accepted);
    }
}