package kome.common.network;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

/** Server-thread, connection-scoped consent to administrative map intelligence. */
public final class KOMEAdminMapVision {
    private static final Map<EntityPlayerMP, NetHandlerPlayServer> ENABLED =
        new IdentityHashMap<EntityPlayerMP, NetHandlerPlayServer>();

    private KOMEAdminMapVision() { }

    public static boolean enabled(EntityPlayerMP player) {
        if (player == null) return false;
        NetHandlerPlayServer connection = ENABLED.get(player);
        if (connection == null) return false;
        if (connection != player.playerNetServerHandler || !player.canCommandSenderUseCommand(2, "troops")) {
            ENABLED.put(player, null); // Preserve one pending filtered publication for the tick hook.
            return false;
        }
        return true;
    }

    /** Returns false only when an unauthorized player attempted to enable vision. */
    public static boolean setEnabled(EntityPlayerMP player, boolean enable) {
        if (player == null) return false;
        if (!enable) { ENABLED.remove(player); return true; }
        if (!player.canCommandSenderUseCommand(2, "troops") || player.playerNetServerHandler == null)
            return false;
        ENABLED.put(player, player.playerNetServerHandler);
        return true;
    }

    /** Detects OP loss without scanning companies or publishing on unchanged ticks. */
    public static boolean revokeIfUnauthorized(EntityPlayerMP player) {
        if (player == null || !ENABLED.containsKey(player)) return false;
        if (ENABLED.get(player) == null) { ENABLED.remove(player); return true; }
        if (player.playerNetServerHandler == ENABLED.get(player)
                && player.canCommandSenderUseCommand(2, "troops")) return false;
        ENABLED.remove(player);
        return true;
    }

    public static void forget(EntityPlayerMP player) { ENABLED.remove(player); }
    public static void clear() { ENABLED.clear(); }
}
