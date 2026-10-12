package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable per-player/per-war decision. Native pledge and progression are deliberately absent. */
public final class KOMEPlayerGovernance {
    public enum State { SUBMITTED, EXILED }
    public final UUID player;
    public final long season, changedAt, revision;
    public final String warId, origin, host, reason;
    public final State state;

    KOMEPlayerGovernance(UUID player, long season, String warId, String origin, String host,
            State state, long changedAt, long revision, String reason) {
        if (player == null || season < 1 || warId == null || warId.isEmpty() || warId.length() > 128
                || !KOMEAlliance.allFactionKeys().contains(origin) || state == null
                || changedAt < 0 || revision < 1 || reason == null || reason.isEmpty() || reason.length() > 512
                || host == null || (state == State.SUBMITTED && !host.isEmpty())
                || (state == State.EXILED && (origin.equals(host) || !KOMEAlliance.allFactionKeys().contains(host))))
            throw new IllegalArgumentException("Invalid player governance record");
        this.player = player; this.season = season; this.warId = warId; this.origin = origin;
        this.host = host; this.state = state; this.changedAt = changedAt; this.revision = revision; this.reason = reason;
    }

    public String key() { return key(player, season, warId); }
    static String key(UUID player, long season, String warId) { return player + ":" + season + ":" + warId; }

    NBTTagCompound write() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Player", player.toString()); nbt.setLong("Season", season);
        nbt.setString("War", warId); nbt.setString("Origin", origin); nbt.setString("Host", host);
        nbt.setString("State", state.name()); nbt.setLong("ChangedAt", changedAt);
        nbt.setLong("Revision", revision); nbt.setString("Reason", reason);
        return nbt;
    }

    static KOMEPlayerGovernance read(NBTTagCompound nbt) {
        for (String key : new String[] {"Player", "War", "Origin", "Host", "State", "Reason"})
            if (!nbt.hasKey(key, 8)) throw new IllegalArgumentException("Missing governance " + key);
        for (String key : new String[] {"Season", "ChangedAt", "Revision"})
            if (!nbt.hasKey(key, 4)) throw new IllegalArgumentException("Missing governance " + key);
        return new KOMEPlayerGovernance(UUID.fromString(nbt.getString("Player")), nbt.getLong("Season"),
            nbt.getString("War"), nbt.getString("Origin"), nbt.getString("Host"),
            State.valueOf(nbt.getString("State")), nbt.getLong("ChangedAt"), nbt.getLong("Revision"), nbt.getString("Reason"));
    }
}
