package com.lotrcharactercreation.faction;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.config.ModConfiguration;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;

public final class StartingFactionApplication {

    private StartingFactionApplication() {}

    public static StartingFaction tryApply(EntityPlayerMP player, boolean replacementConfirmed,
        String expectedExistingPledgeCode) {
        if (PlayerRaceData.isCharacterCreationComplete(player) || PlayerRaceData.isStartingFactionApplied(player)
            || !PlayerRaceData.isRaceSelectionComplete(player)
            || !PlayerRaceData.isFactionSelectionComplete(player)) {
            return null;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        StartingFaction startingFaction = PlayerRaceData.getStartingFaction(player);
        if (!startingFaction.isAllowedFor(race)) {
            return null;
        }

        if (!ModConfiguration.isAutomaticStartingAllegianceEnabled()) {
            PlayerRaceData.setStartingFactionApplied(player, true);
            return startingFaction;
        }

        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        LOTRFaction existingPledge = lotrData.getPledgeFaction();
        LOTRFaction selectedPledge = startingFaction.getLotrFaction();
        if (!isFinalizationAuthorized(
            true,
            PlayerRaceData.isCharacterCreationComplete(player),
            pledgeCode(existingPledge),
            pledgeCode(selectedPledge),
            replacementConfirmed,
            expectedExistingPledgeCode)) {
            return null;
        }

        if (startingFaction == StartingFaction.WANDERER) {
            if (existingPledge != null) {
                lotrData.revokePledgeFaction(player, true);
                if (lotrData.getPledgeFaction() != null) {
                    return null;
                }
            }

            PlayerRaceData.setStartingFactionApplied(player, true);
            return startingFaction;
        }

        if (selectedPledge == null) {
            return null;
        }

        float pledgeAlignment = selectedPledge.getPledgeAlignment();

        if (lotrData.isPledgedTo(selectedPledge)) {
            grantRequiredAlignment(lotrData, selectedPledge, pledgeAlignment);
        } else if (existingPledge != null) {
            replaceExistingPledge(player, lotrData, selectedPledge, pledgeAlignment);
        } else {
            if (!lotrData.canMakeNewPledge() || !lotrData.getFactionsPreventingPledgeTo(selectedPledge)
                    .isEmpty()) {
                return null;
            }

            grantRequiredAlignment(lotrData, selectedPledge, pledgeAlignment);
            if (!lotrData.canPledgeTo(selectedPledge)) {
                return null;
            }

            lotrData.setPledgeFaction(selectedPledge);
        }

        if (!lotrData.isPledgedTo(selectedPledge)) {
            return null;
        }

        PlayerRaceData.setStartingFactionApplied(player, true);
        return startingFaction;
    }

    public static boolean isReplacementRequired(boolean automaticStartingAllegiance, String existingPledgeCode,
        String selectedPledgeCode) {
        String existing = normalizePledgeCode(existingPledgeCode);
        String selected = normalizePledgeCode(selectedPledgeCode);
        return automaticStartingAllegiance && !existing.isEmpty() && !existing.equals(selected);
    }

    public static boolean isFinalizationAuthorized(boolean automaticStartingAllegiance,
        boolean characterCreationComplete, String actualExistingPledgeCode, String selectedPledgeCode,
        boolean replacementConfirmed, String expectedExistingPledgeCode) {
        if (characterCreationComplete) {
            return false;
        }
        if (!automaticStartingAllegiance) {
            return true;
        }

        String actual = normalizePledgeCode(actualExistingPledgeCode);
        String expected = normalizePledgeCode(expectedExistingPledgeCode);
        if (!actual.equals(expected)) {
            return false;
        }
        return !isReplacementRequired(true, actual, selectedPledgeCode) || replacementConfirmed;
    }

    public static String pledgeCode(LOTRFaction faction) {
        return faction == null ? "" : faction.codeName();
    }

    private static void replaceExistingPledge(EntityPlayerMP player, LOTRPlayerData lotrData,
        LOTRFaction selectedPledge, float pledgeAlignment) {
        lotrData.revokePledgeFaction(player, true);
        if (lotrData.getPledgeFaction() != null) {
            return;
        }

        grantRequiredAlignment(lotrData, selectedPledge, pledgeAlignment);
        List<LOTRFaction> blockingFactions = lotrData.getFactionsPreventingPledgeTo(selectedPledge);
        for (LOTRFaction blockingFaction : blockingFactions) {
            if (lotrData.getAlignment(blockingFaction) > 0.0F) {
                lotrData.setAlignment(blockingFaction, 0.0F);
            }
        }

        if (lotrData.canPledgeTo(selectedPledge)) {
            // Character Creation alone may bypass canMakeNewPledge after normal revocation.
            lotrData.setPledgeFaction(selectedPledge);
        }
    }

    private static void grantRequiredAlignment(LOTRPlayerData lotrData, LOTRFaction lotrFaction,
        float pledgeAlignment) {
        if (lotrData.getAlignment(lotrFaction) < pledgeAlignment) {
            lotrData.setAlignment(lotrFaction, pledgeAlignment);
        }
    }

    private static String normalizePledgeCode(String pledgeCode) {
        return pledgeCode == null ? "" : pledgeCode;
    }
}
