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
import net.minecraft.util.ChatComponentText;

import java.util.UUID;

/** Addon-owned destination gate. Current conquest ownership is resolved on every evaluation. */
public final class KOMEWaypointAccessService {
    private KOMEWaypointAccessService() {
    }

    public static Decision evaluate(KOMEWorldData data, UUID playerId, String playerFaction, boolean operator,
            LOTRAbstractWaypoint waypoint, boolean nativeEligible) {
        String normalizedPlayerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);
        if (waypoint == null) {
            return Decision.denied("", "", normalizedPlayerFaction, 0, nativeEligible, "No waypoint was selected.");
        }
        return Decision.allowed("", "", normalizedPlayerFaction, 0, nativeEligible, nativeEligible,
            "KOME adds no alliance or conquest-tier waypoint restriction; native LOTR eligibility applies.", State.DISABLED);
    }

    /** Pure resolved-tile policy boundary used by diagnostics and deterministic tests. */
    public static Decision evaluateResolvedTile(KOMEWorldData data, UUID playerId, String playerFaction, boolean bypass,
            String tileId, boolean nativeEligible) {
        String normalizedPlayerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);
        return Decision.allowed(KOMEConquestTile.normalizeId(tileId), "", normalizedPlayerFaction, 0,
            nativeEligible, nativeEligible,
            "KOME adds no alliance or conquest-tier waypoint restriction; native LOTR eligibility applies.", State.DISABLED);
    }

    public static Decision evaluatePlayer(EntityPlayer player, LOTRAbstractWaypoint waypoint, boolean nativeEligible) {
        if (player == null) {
            return Decision.denied("", "", "", 0, nativeEligible, "Player is unavailable.");
        }
        KOMEWorldData data = KOMEReflection.isRemote(player.worldObj) ? KOMEClientData.INSTANCE : KOMEWorldData.get(player.worldObj);
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        String faction = pledge == null ? data.getPlayerFactionKey(KOMEReflection.getEntityUUID(player)) : pledge.codeName();
        boolean operator = KOMEReflection.isRemote(player.worldObj)
            ? KOMEClientData.INSTANCE.clientViewerIsAdmin : player.canCommandSenderUseCommand(2, "alliance");
        UUID playerId = KOMEReflection.getEntityUUID(player);
        boolean clientBypass = KOMEReflection.isRemote(player.worldObj) && KOMEClientData.INSTANCE.clientWaypointBypass;
        return evaluate(data, playerId, faction, operator || clientBypass, waypoint, nativeEligible);
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
        return !standard.isHidden && LOTRLevelData.getData(player).isFTRegionUnlocked(standard.region);
    }

    /** Entry point injected into unmodified LOTR bytecode by KOME's narrow transformer. */
    public static boolean allowFinalTravel(LOTRPlayerData playerData) {
        if (playerData == null || playerData.getTargetFTWaypoint() == null) {
            return true;
        }
        EntityPlayer player = playerData.getPlayer();
        if (!(player instanceof EntityPlayerMP)) {
            return true;
        }
        Decision decision = evaluatePlayer(player, playerData.getTargetFTWaypoint(), true);
        if (!decision.finalAllowed) {
            player.addChatMessage(new ChatComponentText("Fast travel denied: " + decision.reason));
            playerData.setTargetFTWaypoint(null);
        }
        return decision.finalAllowed;
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
        public final int civilTier;
        public final boolean nativeEligible;
        public final boolean territoryAllowed;
        public final boolean finalAllowed;
        public final String reason;
        public final State state;

        private Decision(String tileId, String tileOwner, String playerFaction, int civilTier, boolean nativeEligible,
                boolean territoryAllowed, boolean finalAllowed, String reason, State state) {
            this.tileId = tileId == null ? "" : tileId;
            this.tileOwner = KOMEAlliance.normalizeFactionKey(tileOwner);
            this.playerFaction = KOMEAlliance.normalizeFactionKey(playerFaction);
            this.civilTier = Math.max(0, civilTier);
            this.nativeEligible = nativeEligible;
            this.territoryAllowed = territoryAllowed;
            this.finalAllowed = finalAllowed;
            this.reason = reason == null ? "" : reason;
            this.state = state;
        }

        static Decision allowed(String tileId, String owner, String faction, int civilTier, boolean nativeEligibility,
                boolean result, String reason, State state) {
            return new Decision(tileId, owner, faction, civilTier, nativeEligibility, true, result,
                result ? reason : "Native LOTR restrictions still deny this destination. " + reason, state);
        }

        static Decision denied(String tileId, String owner, String faction, int civilTier, boolean nativeEligibility, String reason) {
            return new Decision(tileId, owner, faction, civilTier, nativeEligibility, false, false, reason, State.DENIED);
        }
    }
}
