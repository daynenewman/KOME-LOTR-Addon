package kome.common.data;

import cpw.mods.fml.common.FMLLog;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;

import java.util.UUID;

/** Addon-owned destination gate. Current conquest ownership is resolved on every evaluation. */
public final class KOMEWaypointAccessService {
    private KOMEWaypointAccessService() {
    }

    public static Decision evaluate(KOMEWorldData data, UUID playerId, String playerFaction, boolean operator,
            LOTRAbstractWaypoint waypoint, boolean nativeEligible) {
        String normalizedPlayerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);

        if (waypoint == null) {
            return Decision.denied(
                "", "", normalizedPlayerFaction, nativeEligible,
                "No waypoint was selected.");
        }

        // Wanderers / unpledged players retain ordinary LOTR fast travel.
        if (normalizedPlayerFaction.length() == 0) {
            return Decision.allowed(
                "", "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "Unpledged players use native LOTR waypoint access.",
                State.DISABLED);
        }

        String tileId = resolveLinkedTileId(data, waypoint);

        if (tileId.length() == 0) {
            debugUnmapped(waypoint);
            return Decision.allowed(
                "", "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "This waypoint is not linked to a canonical KOME conquest tile; native LOTR eligibility applies.",
                State.UNMAPPED);
        }

        return evaluateResolvedTile(
            data,
            playerId,
            normalizedPlayerFaction,
            operator,
            tileId,
            nativeEligible);
    }

    private static String resolveLinkedTileId(KOMEWorldData data, LOTRAbstractWaypoint waypoint) {
        if (data == null || waypoint == null || !(waypoint instanceof LOTRWaypoint)) {
            return "";
        }

        String waypointKey = ((LOTRWaypoint) waypoint).getCodeName();
        if (waypointKey == null || waypointKey.length() == 0) {
            return "";
        }

        for (KOMETileWaypointLink link : data.tileWaypointLinksByTileId.values()) {
            if (link == null
                    || link.tileId == null
                    || link.lotrWaypointKey == null) {
                continue;
            }

            if (waypointKey.equals(link.lotrWaypointKey)) {
                return KOMEConquestTile.normalizeId(link.tileId);
            }
        }

        return "";
    }
    /** Pure resolved-tile policy boundary used by diagnostics and deterministic tests. */
    public static Decision evaluateResolvedTile(KOMEWorldData data, UUID playerId, String playerFaction, boolean bypass,
            String tileId, boolean nativeEligible) {
        String normalizedTileId = KOMEConquestTile.normalizeId(tileId);
        String normalizedPlayerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);

        if (data == null || normalizedTileId.length() == 0) {
            return Decision.allowed(normalizedTileId, "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "No canonical KOME destination tile is available; native LOTR eligibility applies.", State.UNMAPPED);
        }

        // Wanderers / unpledged players retain ordinary LOTR travel behavior.
        if (normalizedPlayerFaction.length() == 0) {
            return Decision.allowed(normalizedTileId, "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "Unpledged players use native LOTR waypoint access.", State.DISABLED);
        }

        KOMEConquestTile tile = data.conquestTiles.get(normalizedTileId);
        if (tile == null) {
            return Decision.allowed(normalizedTileId, "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "This destination has no canonical KOME tile record; native LOTR eligibility applies.", State.UNMAPPED);
        }

        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());

        if (bypass) {
            return Decision.allowed(normalizedTileId, owner, normalizedPlayerFaction, nativeEligible, nativeEligible,
                "Operator waypoint bypass.", State.BYPASS);
        }

        if (owner.length() == 0) {
            return Decision.allowed(normalizedTileId, "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "The destination is unclaimed; native LOTR eligibility applies.", State.UNCLAIMED);
        }

        if (owner.equals(normalizedPlayerFaction)) {
            return Decision.allowed(normalizedTileId, owner, normalizedPlayerFaction, nativeEligible, nativeEligible,
                "The destination is controlled by your faction.", State.OWN);
        }

        if (KOMEWarService.findActiveOpposition(
                data, normalizedPlayerFaction, owner) != null) {
            return Decision.denied(
                normalizedTileId,
                owner,
                normalizedPlayerFaction, nativeEligible,
                "Fast travel into " + KOMEAlliance.displayFactionName(owner)
                    + " territory is blocked because your factions are active enemies.");
        }
        if (KOMEDiplomacyService.relationAtLeast(
                data, normalizedPlayerFaction, owner, KOMEDiplomacyRelation.FRIENDS)) {
            return Decision.allowed(normalizedTileId, owner, normalizedPlayerFaction, nativeEligible, nativeEligible,
                "An accepted Friends or Allies relation permits travel into this territory.", State.ALLY);
        }

        return Decision.denied(
            normalizedTileId,
            owner,
            normalizedPlayerFaction, nativeEligible,
            "Fast travel into " + KOMEAlliance.displayFactionName(owner)
                + " territory requires an accepted Friends or Allies relation.");
    }
    public static Decision evaluatePlayer(EntityPlayer player, LOTRAbstractWaypoint waypoint, boolean nativeEligible) {
        if (player == null) {
            return Decision.denied("", "", "", nativeEligible, "Player is unavailable.");
        }
        KOMEWorldData data = KOMEReflection.isRemote(player.worldObj) ? KOMEClientData.INSTANCE : KOMEWorldData.get(player.worldObj);
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        String faction = pledge == null ? data.getPlayerFactionKey(KOMEReflection.getEntityUUID(player)) : pledge.codeName();
        boolean operator = KOMEReflection.isRemote(player.worldObj)
            ? KOMEClientData.INSTANCE.clientViewerIsAdmin : player.canCommandSenderUseCommand(2, "alliance");
        UUID playerId = KOMEReflection.getEntityUUID(player);
        return evaluate(data, playerId, faction, operator, waypoint, nativeEligible);
    }

    /** Native progression without the original waypoint-faction alignment gate. */
    public static boolean hasNativeProgression(EntityPlayer player, LOTRAbstractWaypoint waypoint) {
        if (player == null || waypoint == null) {
            return false;
        }
        if (!(waypoint instanceof LOTRWaypoint)) {
            return waypoint.hasPlayerUnlocked(player);
        }
        LOTRWaypoint standard = (LOTRWaypoint) waypoint;
        if (standard.isHidden()) {
            return false;
        }
        LOTRWaypoint.Region region = findWaypointRegion(standard);
        return region != null && LOTRLevelData.getData(player).isFTRegionUnlocked(region);
    }

    /** Entry point injected into unmodified LOTR bytecode by KOME's narrow transformer. */
    public static boolean allowFinalTravel(LOTRPlayerData playerData) {
        if (playerData == null || playerData.getTargetFTWaypoint() == null) {
            return true;
        }
        EntityPlayer player = findServerPlayer(playerData.getPlayerUUID());
        if (!(player instanceof EntityPlayerMP)) {
            return true;
        }
        if (!KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.FAST_TRAVEL)) {
            player.addChatMessage(new ChatComponentText("Fast travel denied: you have not unlocked Fast Travel yet."));
            playerData.setTargetFTWaypoint(null);
            return false;
        }
        Decision decision = evaluatePlayer(player, playerData.getTargetFTWaypoint(), true);
        if (!decision.finalAllowed) {
            player.addChatMessage(new ChatComponentText("Fast travel denied: " + decision.reason));
            playerData.setTargetFTWaypoint(null);
        }
        return decision.finalAllowed;
    }

    private static LOTRWaypoint.Region findWaypointRegion(LOTRWaypoint waypoint) {
        for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
            if (region.waypoints.contains(waypoint)) {
                return region;
            }
        }
        return null;
    }

    private static EntityPlayer findServerPlayer(UUID playerId) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null || playerId == null) {
            return null;
        }
        for (WorldServer world : server.worldServers) {
            if (world == null) {
                continue;
            }
            EntityPlayer player = world.func_152378_a(playerId);
            if (player != null) {
                return player;
            }
        }
        return null;
    }

    private static void debugUnmapped(LOTRAbstractWaypoint waypoint) {
        try {
            FMLLog.fine("[KOME] No canonical conquest tile mapping for waypoint %s at map %.2f, %.2f; using native LOTR behavior.",
                waypoint.getDisplayName(), waypoint.getX(), waypoint.getY());
        } catch (Throwable ignored) {
        }
    }

    public enum State {
        OWN, ALLY, UNCLAIMED, DENIED, BYPASS, DISABLED, UNMAPPED
    }

    public static final class Decision {
        public final String tileId;
        public final String tileOwner;
        public final String playerFaction;
        public final boolean nativeEligible;
        public final boolean territoryAllowed;
        public final boolean finalAllowed;
        public final String reason;
        public final State state;

        private Decision(String tileId, String tileOwner, String playerFaction, boolean nativeEligible,
                boolean territoryAllowed, boolean finalAllowed, String reason, State state) {
            this.tileId = tileId == null ? "" : tileId;
            this.tileOwner = KOMEAlliance.normalizeFactionKey(tileOwner);
            this.playerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);
            this.nativeEligible = nativeEligible;
            this.territoryAllowed = territoryAllowed;
            this.finalAllowed = finalAllowed;
            this.reason = reason == null ? "" : reason;
            this.state = state;
        }

        static Decision allowed(String tileId, String owner, String faction, boolean nativeEligibility,
                boolean result, String reason, State state) {
            return new Decision(tileId, owner, faction, nativeEligibility, true, result,
                result ? reason : "Native LOTR restrictions still deny this destination. " + reason, state);
        }

        static Decision denied(String tileId, String owner, String faction, boolean nativeEligibility, String reason) {
            return new Decision(tileId, owner, faction, nativeEligibility, false, false, reason, State.DENIED);
        }
    }
}
