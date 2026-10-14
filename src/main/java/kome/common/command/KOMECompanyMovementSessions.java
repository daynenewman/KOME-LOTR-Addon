package kome.common.command;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

/** Ephemeral server-owned confirmation identity. Never sent by or accepted from a client. */
public final class KOMECompanyMovementSessions {
    static final int MAX_CONNECTIONS = 1024;
    private static final Map<NetHandlerPlayServer, UUID> sessions = new WeakHashMap<NetHandlerPlayServer, UUID>();
    private KOMECompanyMovementSessions() { }

    public static synchronized UUID current(EntityPlayerMP actor) {
        NetHandlerPlayServer connection = actor == null ? null : actor.playerNetServerHandler;
        if (connection == null || connection.playerEntity != actor)
            throw new KOMECompanyMovementService.MovementDenied("INVALID_ACTOR", "A current server connection is required.");
        UUID identity = sessions.get(connection);
        if (identity == null) {
            if (sessions.size() >= MAX_CONNECTIONS)
                throw new KOMECompanyMovementService.MovementDenied("SESSION_LIMIT", "Movement confirmation capacity is unavailable. Try again later.");
            identity = UUID.randomUUID();
            sessions.put(connection, identity);
        }
        return identity;
    }

    public static synchronized void forget(NetHandlerPlayServer connection) { sessions.remove(connection); }
    public static synchronized void clear() { sessions.clear(); }
    static synchronized int size() { return sessions.size(); }
}
