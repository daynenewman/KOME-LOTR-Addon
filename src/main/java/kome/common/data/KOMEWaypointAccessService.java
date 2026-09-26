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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

        String waypointFaction = resolveWaypointFaction(waypoint);

        // Wanderers / unpledged players retain ordinary LOTR fast travel.
        if (normalizedPlayerFaction.length() == 0) {
            return Decision.allowed(
                "", "", normalizedPlayerFaction, nativeEligible, nativeEligible,
                "Unpledged players use native LOTR waypoint access.",
                State.DISABLED).withWaypointFaction(waypointFaction);
        }

        TileLinkResolution tileLink = resolveLinkedTile(data, waypoint);
        if (tileLink.ambiguous) {
            return Decision.denied(
                "", "", normalizedPlayerFaction, nativeEligible,
                "Fast travel is blocked because this waypoint has multiple canonical conquest-tile links: "
                    + tileLink.candidates + ".")
                .withWaypointFaction(waypointFaction);
        }
        String tileId = tileLink.tileId;
        if (tileId.length() == 0) {
            debugUnmapped(waypoint);
        }

        Decision territorialDecision = evaluateResolvedTile(
            data,
            playerId,
            normalizedPlayerFaction,
            operator,
            tileId,
            nativeEligible).withWaypointFaction(waypointFaction);

        // A valid current controller is the sole diplomatic owner. The native
        // waypoint faction is historical/default metadata in conquered land.
        if (territorialDecision.tileOwner.length() > 0) {
            return territorialDecision;
        }

        return evaluateNativeFallback(
            data, normalizedPlayerFaction, waypointFaction, nativeEligible,
            territorialDecision);
    }

    private static TileLinkResolution resolveLinkedTile(
            KOMEWorldData data, LOTRAbstractWaypoint waypoint) {
        if (data == null || waypoint == null || !(waypoint instanceof LOTRWaypoint)) {
            return TileLinkResolution.none();
        }

        String waypointKey = ((LOTRWaypoint) waypoint).getCodeName();
        if (waypointKey == null || waypointKey.length() == 0) {
            return TileLinkResolution.none();
        }

        List<String> matchingTiles = new ArrayList<String>();
        for (KOMETileWaypointLink link : data.tileWaypointLinksByTileId.values()) {
            if (link == null
                    || link.tileId == null
                    || link.lotrWaypointKey == null) {
                continue;
            }

            if (waypointKey.equals(link.lotrWaypointKey)) {
                String tileId = KOMEConquestTile.normalizeId(link.tileId);
                if (tileId.length() > 0 && !matchingTiles.contains(tileId)) {
                    matchingTiles.add(tileId);
                }
            }
        }

        if (matchingTiles.isEmpty()) {
            return TileLinkResolution.none();
        }
        Collections.sort(matchingTiles);
        if (matchingTiles.size() == 1) {
            return TileLinkResolution.unique(matchingTiles.get(0));
        }
        StringBuilder candidates = new StringBuilder();
        for (String tileId : matchingTiles) {
            if (candidates.length() > 0) {
                candidates.append(", ");
            }
            candidates.append(tileId);
        }
        return TileLinkResolution.ambiguous(candidates.toString());
    }

    private static Decision evaluateNativeFallback(KOMEWorldData data,
            String playerFaction, String waypointFaction, boolean nativeEligible,
            Decision territorialDecision) {
        if (waypointFaction.length() == 0) {
            return territorialDecision;
        }
        if (waypointFaction.equals(playerFaction)) {
            return Decision.allowed(
                territorialDecision.tileId, "", playerFaction,
                nativeEligible, nativeEligible,
                "No current canonical tile owner exists; the native waypoint faction is the diplomatic fallback and matches your faction.",
                State.NATIVE_FALLBACK)
                .withWaypointFaction(waypointFaction);
        }
        if (KOMEWarService.findActiveOpposition(
                data, playerFaction, waypointFaction) != null) {
            return Decision.denied(
                territorialDecision.tileId, "", playerFaction, nativeEligible,
                "No current canonical tile owner exists; native "
                    + KOMEAlliance.displayFactionName(waypointFaction)
                    + " ownership is the fallback and an active war blocks travel.")
                .withWaypointFaction(waypointFaction);
        }
        KOMEDiplomacyRelation relation = KOMEDiplomacyService.getRelation(
            data, playerFaction, waypointFaction);
        if (relation.rank() < KOMEDiplomacyRelation.NEUTRAL.rank()) {
            return Decision.denied(
                territorialDecision.tileId, "", playerFaction, nativeEligible,
                "No current canonical tile owner exists; native "
                    + KOMEAlliance.displayFactionName(waypointFaction)
                    + " ownership is the fallback and the current LOTR relation is "
                    + relation.displayName + ".")
                .withWaypointFaction(waypointFaction);
        }
        return Decision.allowed(
            territorialDecision.tileId, "", playerFaction,
            nativeEligible, nativeEligible,
            "No current canonical tile owner exists; native "
                + KOMEAlliance.displayFactionName(waypointFaction)
                + " ownership is the diplomatic fallback and the current LOTR relation is "
                + relation.displayName + ".",
            State.NATIVE_FALLBACK)
            .withWaypointFaction(waypointFaction);
    }

    static String resolveWaypointFaction(LOTRAbstractWaypoint waypoint) {
        if (!(waypoint instanceof LOTRWaypoint)) {
            return "";
        }
        LOTRFaction faction = ((LOTRWaypoint) waypoint).faction;
        return faction != null && faction.isPlayableAlignmentFaction()
            ? KOMEAlliance.normalizeFactionKey(faction.codeName()) : "";
    }

    /** Pure resolved-tile policy boundary used by diagnostics and deterministic tests. */
    public static Decision evaluateResolvedTile(KOMEWorldData data, UUID playerId, String playerFaction, boolean operator,
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

        // Diplomatic and active-war restrictions are server rules, not command
        // permissions. Operators must pass the same destination policy as players.
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
                data, normalizedPlayerFaction, owner, KOMEDiplomacyRelation.NEUTRAL)) {
            return Decision.allowed(normalizedTileId, owner, normalizedPlayerFaction, nativeEligible, nativeEligible,
                "A Neutral-or-better LOTR relation permits travel into this territory.",
                State.DIPLOMATIC);
        }

        return Decision.denied(
            normalizedTileId,
            owner,
            normalizedPlayerFaction, nativeEligible,
            "Fast travel into " + KOMEAlliance.displayFactionName(owner)
                + " territory requires a Neutral-or-better LOTR relation.");
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

    /** Immediate server gate injected into LOTR's native fast-travel request. */
    public static boolean allowNativeRequest(
            EntityPlayerMP player, LOTRAbstractWaypoint target) {
        if (player == null || target == null) {
            System.err.println("[KOME FT] native-request denied: missing player or waypoint");
            return false;
        }
        if (!KOMEProgressionPermissions.has(
                player, KOMEProgressionPermissions.FAST_TRAVEL)) {
            player.addChatMessage(new ChatComponentText(
                "Fast travel denied: you have not unlocked Fast Travel yet."));
            System.out.println("[KOME FT] native-request player="
                + player.getCommandSenderName() + " waypoint=" + waypointIdentity(target)
                + " allowed=false reason=progression");
            return false;
        }
        Decision decision = evaluatePlayer(
            player, target, hasNativeProgression(player, target));
        logDecision("native-request", player, target, decision);
        if (!decision.finalAllowed) {
            player.addChatMessage(new ChatComponentText(
                "Fast travel denied: " + decision.reason));
        }
        return decision.finalAllowed;
    }

    /** Entry point injected into unmodified LOTR bytecode by KOME's narrow transformer. */
    public static boolean allowFinalTravel(LOTRPlayerData playerData) {
        if (playerData == null) {
            return false;
        }
        LOTRAbstractWaypoint target = playerData.getTargetFTWaypoint();
        if (target == null) {
            return true;
        }
        EntityPlayer player = findServerPlayer(playerData.getPlayerUUID());
        if (!(player instanceof EntityPlayerMP)) {
            System.err.println("[KOME FT] final-guard player="
                + playerData.getPlayerUUID() + " waypoint=" + waypointIdentity(target)
                + " allowed=false reason=server-player-unavailable");
            playerData.setTargetFTWaypoint(null);
            return false;
        }
        if (!KOMEProgressionPermissions.has(player, KOMEProgressionPermissions.FAST_TRAVEL)) {
            player.addChatMessage(new ChatComponentText("Fast travel denied: you have not unlocked Fast Travel yet."));
            playerData.setTargetFTWaypoint(null);
            return false;
        }
        boolean nativeEligible = hasNativeProgression(player, target);
        Decision decision = evaluatePlayer(player, target, nativeEligible);
        logDecision("final-guard", player, target, decision);
        if (!decision.finalAllowed) {
            player.addChatMessage(new ChatComponentText("Fast travel denied: " + decision.reason));
            playerData.setTargetFTWaypoint(null);
        }
        return decision.finalAllowed;
    }

    private static void logDecision(String milestone, EntityPlayer player,
            LOTRAbstractWaypoint target, Decision decision) {
        System.out.println("[KOME FT] " + milestone
            + " player=" + (player == null ? "<missing>" : player.getCommandSenderName())
            + " waypoint=" + waypointIdentity(target)
            + " diplomaticOwner="
            + (decision == null || decision.diplomaticOwner.length() == 0
                ? "<none>" : decision.diplomaticOwner)
            + " state=" + (decision == null ? "<none>" : decision.state)
            + " allowed=" + (decision != null && decision.finalAllowed)
            + " reason=" + (decision == null ? "no decision" : decision.reason));
    }

    private static String waypointIdentity(LOTRAbstractWaypoint waypoint) {
        if (waypoint instanceof LOTRWaypoint) {
            return ((LOTRWaypoint) waypoint).getCodeName();
        }
        return waypoint == null ? "<missing>" : waypoint.getDisplayName();
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
        OWN, DIPLOMATIC, NATIVE_FALLBACK, UNCLAIMED, DENIED, DISABLED, UNMAPPED
    }

    public static final class Decision {
        public final String tileId;
        public final String tileOwner;
        public final String waypointFaction;
        public final String diplomaticOwner;
        public final String playerFaction;
        public final boolean nativeEligible;
        public final boolean territoryAllowed;
        public final boolean finalAllowed;
        public final String reason;
        public final State state;

        private Decision(String tileId, String tileOwner, String waypointFaction,
                String playerFaction, boolean nativeEligible,
                boolean territoryAllowed, boolean finalAllowed, String reason, State state) {
            this.tileId = tileId == null ? "" : tileId;
            this.tileOwner = KOMEAlliance.normalizeFactionKey(tileOwner);
            this.waypointFaction = KOMEAlliance.normalizeFactionKey(waypointFaction);
            this.diplomaticOwner = this.tileOwner.length() > 0
                ? this.tileOwner : this.waypointFaction;
            this.playerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);
            this.nativeEligible = nativeEligible;
            this.territoryAllowed = territoryAllowed;
            this.finalAllowed = finalAllowed;
            this.reason = reason == null ? "" : reason;
            this.state = state;
        }

        static Decision allowed(String tileId, String owner, String faction, boolean nativeEligibility,
                boolean result, String reason, State state) {
            return new Decision(tileId, owner, "", faction, nativeEligibility, true, result,
                result ? reason : "Native LOTR restrictions still deny this destination. " + reason, state);
        }

        static Decision denied(String tileId, String owner, String faction, boolean nativeEligibility, String reason) {
            return new Decision(tileId, owner, "", faction, nativeEligibility, false, false,
                reason, State.DENIED);
        }

        Decision withWaypointFaction(String faction) {
            return new Decision(tileId, tileOwner, faction, playerFaction, nativeEligible,
                territoryAllowed, finalAllowed, reason, state);
        }
    }

    private static final class TileLinkResolution {
        private final String tileId;
        private final boolean ambiguous;
        private final String candidates;

        private TileLinkResolution(
                String tileId, boolean ambiguous, String candidates) {
            this.tileId = KOMEConquestTile.normalizeId(tileId);
            this.ambiguous = ambiguous;
            this.candidates = candidates == null ? "" : candidates;
        }

        private static TileLinkResolution none() {
            return new TileLinkResolution("", false, "");
        }

        private static TileLinkResolution unique(String tileId) {
            return new TileLinkResolution(tileId, false, "");
        }

        private static TileLinkResolution ambiguous(String candidates) {
            return new TileLinkResolution("", true, candidates);
        }
    }
}
