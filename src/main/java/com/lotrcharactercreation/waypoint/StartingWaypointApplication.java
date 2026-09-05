package com.lotrcharactercreation.waypoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.RaceTraitService;

import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRControlZone;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;

public final class StartingWaypointApplication {

    private static final Logger LOGGER = LogManager.getLogger("LOTR Character Creation");
    private static final Set<LOTRFaction> CONTROL_ZONE_FALLBACK_FACTIONS = Collections
        .unmodifiableSet(EnumSet.of(LOTRFaction.RANGER_NORTH, LOTRFaction.MORWAITH));
    private static final Set<LOTRWaypoint> DURINS_FOLK_ADDITIONAL_WAYPOINTS = Collections
        .unmodifiableSet(EnumSet.of(LOTRWaypoint.EAST_PEAK, LOTRWaypoint.WEST_PEAK));

    private StartingWaypointApplication() {}

    public static Result tryApply(EntityPlayerMP player) {
        if (PlayerRaceData.isCharacterCreationComplete(player) || !PlayerRaceData.isRaceSelectionComplete(player)
            || !PlayerRaceData.isFactionSelectionComplete(player)
            || !PlayerRaceData.isStartingFactionApplied(player)) {
            return null;
        }

        if (PlayerRaceData.isStartingWaypointApplied(player)) {
            completeCharacterCreation(player);
            return null;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        StartingFaction startingFaction = PlayerRaceData.getStartingFaction(player);
        if (!startingFaction.isAllowedFor(race)) {
            return Result.failure(startingFaction, "The saved starting faction is not valid for the selected race.");
        }

        if (startingFaction == StartingFaction.WANDERER) {
            PlayerRaceData.setStartingWaypointApplied(player, true);
            completeCharacterCreation(player);
            return Result.success(startingFaction, null);
        }

        LOTRFaction lotrFaction = startingFaction.getLotrFaction();
        if (lotrFaction == null) {
            return Result.failure(startingFaction, "The saved starting faction has no LOTR faction mapping.");
        }

        LOTRWaypoint waypoint = resolveOrChooseWaypoint(player, lotrFaction);
        if (waypoint == null) {
            String storedCodeName = PlayerRaceData.getStartingWaypointCodeName(player);
            if (storedCodeName == null) {
                return Result.failure(
                    startingFaction,
                    "No eligible built-in LOTR waypoint belongs to " + startingFaction.getDisplayName() + ".");
            }

            return Result.failure(
                startingFaction,
                "The saved starting waypoint '" + storedCodeName
                    + "' is no longer valid for "
                    + startingFaction.getDisplayName()
                    + ".");
        }

        if (!teleportToWaypoint(player, waypoint)) {
            return Result.failure(
                startingFaction,
                "Could not reach " + waypoint.getDisplayName() + "; the same waypoint will be retried next login.");
        }

        PlayerRaceData.setStartingWaypointApplied(player, true);
        completeCharacterCreation(player);
        return Result.success(startingFaction, waypoint);
    }

    private static LOTRWaypoint resolveOrChooseWaypoint(EntityPlayerMP player, LOTRFaction lotrFaction) {
        List<LOTRWaypoint> eligibleWaypoints = findDirectFactionWaypoints(lotrFaction);
        if (eligibleWaypoints.isEmpty() && CONTROL_ZONE_FALLBACK_FACTIONS.contains(lotrFaction)) {
            eligibleWaypoints = findControlZoneFallbackWaypoints(lotrFaction);
        }

        String storedCodeName = PlayerRaceData.getStartingWaypointCodeName(player);
        if (storedCodeName != null) {
            LOTRWaypoint storedWaypoint = LOTRWaypoint.waypointForName(storedCodeName);
            return eligibleWaypoints.contains(storedWaypoint) ? storedWaypoint : null;
        }

        if (eligibleWaypoints.isEmpty()) {
            return null;
        }

        LOTRWaypoint chosenWaypoint = eligibleWaypoints.get(
            player.getRNG()
                .nextInt(eligibleWaypoints.size()));
        PlayerRaceData.setStartingWaypointCodeName(player, chosenWaypoint.getCodeName());
        return chosenWaypoint;
    }

    private static List<LOTRWaypoint> findDirectFactionWaypoints(LOTRFaction lotrFaction) {
        List<LOTRWaypoint> waypoints = new ArrayList<>();
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (isDirectFactionWaypoint(waypoint, lotrFaction) || isAdditionalFactionWaypoint(waypoint, lotrFaction)) {
                waypoints.add(waypoint);
            }
        }
        return waypoints;
    }

    private static List<LOTRWaypoint> findControlZoneFallbackWaypoints(LOTRFaction lotrFaction) {
        List<LOTRWaypoint> waypoints = new ArrayList<>();
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (!waypoint.isHidden() && waypoint.faction == LOTRFaction.UNALIGNED
                && isControlZoneCenter(waypoint, lotrFaction)
                && !isSharedControlZoneCenter(waypoint, lotrFaction)) {
                waypoints.add(waypoint);
            }
        }
        return waypoints;
    }

    private static boolean isDirectFactionWaypoint(LOTRWaypoint waypoint, LOTRFaction lotrFaction) {
        return waypoint != null && waypoint.faction == lotrFaction && !waypoint.isHidden();
    }

    private static boolean isAdditionalFactionWaypoint(LOTRWaypoint waypoint, LOTRFaction lotrFaction) {
        return lotrFaction == LOTRFaction.DURINS_FOLK && waypoint != null
            && !waypoint.isHidden()
            && DURINS_FOLK_ADDITIONAL_WAYPOINTS.contains(waypoint);
    }

    private static boolean isControlZoneCenter(LOTRWaypoint waypoint, LOTRFaction lotrFaction) {
        for (LOTRControlZone controlZone : lotrFaction.getControlZones()) {
            if (waypoint.getX() == controlZone.mapX && waypoint.getY() == controlZone.mapY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSharedControlZoneCenter(LOTRWaypoint waypoint, LOTRFaction lotrFaction) {
        for (LOTRFaction otherFaction : LOTRFaction.getPlayableAlignmentFactions()) {
            if (otherFaction != lotrFaction && isControlZoneCenter(waypoint, otherFaction)) {
                return true;
            }
        }
        return false;
    }

    private static boolean teleportToWaypoint(EntityPlayerMP player, LOTRWaypoint waypoint) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return false;
        }

        try {
            int dimensionId = LOTRDimension.MIDDLE_EARTH.dimensionID;
            WorldServer targetWorld = server.worldServerForDimension(dimensionId);
            if (targetWorld == null) {
                return false;
            }

            int x = waypoint.getXCoord();
            int z = waypoint.getZCoord();
            targetWorld.theChunkProviderServer.loadChunk(x >> 4, z >> 4);
            int y = waypoint.getYCoord(targetWorld, x, z);
            if (y <= 0) {
                return false;
            }

            player.mountEntity(null);
            if (player.dimension == dimensionId) {
                player.setPositionAndUpdate(x + 0.5D, y, z + 0.5D);
                resetMotion(player);
            } else {
                server.getConfigurationManager()
                    .transferPlayerToDimension(player, dimensionId, new DirectWaypointTeleporter(targetWorld, x, y, z));
            }

            double deltaX = player.posX - (x + 0.5D);
            double deltaY = player.posY - y;
            double deltaZ = player.posZ - (z + 0.5D);
            return player.dimension == dimensionId && player.worldObj == targetWorld
                && deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ <= 1.0D;
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Failed to apply starting waypoint {} for {}",
                waypoint.getCodeName(),
                player.getCommandSenderName(),
                exception);
            return false;
        }
    }

    private static void resetMotion(Entity entity) {
        entity.motionX = 0.0D;
        entity.motionY = 0.0D;
        entity.motionZ = 0.0D;
        entity.fallDistance = 0.0F;
    }

    private static void completeCharacterCreation(EntityPlayerMP player) {
        if (CharacterCreationFlowService.isReadyForFinalization(player)
            && PlayerRaceData.isRaceSelectionComplete(player)
            && PlayerRaceData.isFactionSelectionComplete(player)
            && PlayerRaceData.isStartingFactionApplied(player)
            && PlayerRaceData.isStartingWaypointApplied(player)) {
            PlayerRaceData.setCharacterCreationComplete(player, true);
            RaceTraitService.refreshDerivedAttributes(player);
        }
    }

    public static final class Result {

        private final boolean successful;
        private final StartingFaction startingFaction;
        private final LOTRWaypoint waypoint;
        private final String failureMessage;

        private Result(boolean successful, StartingFaction startingFaction, LOTRWaypoint waypoint,
            String failureMessage) {
            this.successful = successful;
            this.startingFaction = startingFaction;
            this.waypoint = waypoint;
            this.failureMessage = failureMessage;
        }

        public static Result success(StartingFaction startingFaction, LOTRWaypoint waypoint) {
            return new Result(true, startingFaction, waypoint, null);
        }

        public static Result failure(StartingFaction startingFaction, String failureMessage) {
            return new Result(false, startingFaction, null, failureMessage);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public StartingFaction getStartingFaction() {
            return startingFaction;
        }

        public LOTRWaypoint getWaypoint() {
            return waypoint;
        }

        public String getFailureMessage() {
            return failureMessage;
        }
    }

    private static final class DirectWaypointTeleporter extends Teleporter {

        private final double x;
        private final double y;
        private final double z;

        private DirectWaypointTeleporter(WorldServer targetWorld, int x, int y, int z) {
            super(targetWorld);
            this.x = x + 0.5D;
            this.y = y;
            this.z = z + 0.5D;
        }

        @Override
        public void placeInPortal(Entity entity, double previousX, double previousY, double previousZ,
            float previousYaw) {
            entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
            resetMotion(entity);
        }
    }
}
