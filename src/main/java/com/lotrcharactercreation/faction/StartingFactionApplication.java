package com.lotrcharactercreation.faction;

import net.minecraft.entity.player.EntityPlayerMP;

import com.lotrcharactercreation.config.ModConfiguration;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;

public final class StartingFactionApplication {

    private StartingFactionApplication() {}

    public static StartingFaction tryApply(EntityPlayerMP player) {
        if (PlayerRaceData.isStartingFactionApplied(player) || !PlayerRaceData.isRaceSelectionComplete(player)
            || !PlayerRaceData.isFactionSelectionComplete(player)) {
            return null;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        StartingFaction startingFaction = PlayerRaceData.getStartingFaction(player);
        if (!startingFaction.isAllowedFor(race)) {
            return null;
        }

        if (startingFaction == StartingFaction.WANDERER) {
            PlayerRaceData.setStartingFactionApplied(player, true);
            return startingFaction;
        }

        if (!ModConfiguration.isAutomaticStartingAllegianceEnabled()) {
            PlayerRaceData.setStartingFactionApplied(player, true);
            return startingFaction;
        }

        LOTRFaction lotrFaction = startingFaction.getLotrFaction();
        if (lotrFaction == null) {
            return null;
        }

        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        float pledgeAlignment = lotrFaction.getPledgeAlignment();

        if (lotrData.isPledgedTo(lotrFaction)) {
            grantRequiredAlignment(lotrData, lotrFaction, pledgeAlignment);
        } else {
            if (lotrData.getPledgeFaction() != null || !lotrData.canMakeNewPledge()
                || !lotrData.getFactionsPreventingPledgeTo(lotrFaction)
                    .isEmpty()) {
                return null;
            }

            grantRequiredAlignment(lotrData, lotrFaction, pledgeAlignment);
            if (!lotrData.canPledgeTo(lotrFaction)) {
                return null;
            }

            lotrData.setPledgeFaction(lotrFaction);
        }

        if (!lotrData.isPledgedTo(lotrFaction)) {
            return null;
        }

        PlayerRaceData.setStartingFactionApplied(player, true);
        return startingFaction;
    }

    private static void grantRequiredAlignment(LOTRPlayerData lotrData, LOTRFaction lotrFaction,
        float pledgeAlignment) {
        if (lotrData.getAlignment(lotrFaction) < pledgeAlignment) {
            lotrData.setAlignment(lotrFaction, pledgeAlignment);
        }
    }
}
