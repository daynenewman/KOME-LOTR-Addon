package kome.common.data;

import java.util.UUID;

/** A particular player needs a particular native NPC for active progression. */
public final class KOMEProgressionNpcRoleLease {
    public enum Role { SERFDOM_MASTER, LIEGE, COURIER_RECIPIENT, ESCORT_CHARGE, DEFENSE_PROTECTED }
    public final UUID npc, player;
    public final Role role;
    public final String token;

    public KOMEProgressionNpcRoleLease(UUID npc, UUID player, Role role, String token) {
        if (npc == null || player == null || role == null) throw new IllegalArgumentException("A role needs both identities");
        this.npc=npc; this.player=player; this.role=role; this.token=token==null?"":token;
    }
    @Override public int hashCode() { return npc.hashCode()*31+player.hashCode()*7+role.hashCode()*3+token.hashCode(); }
    @Override public boolean equals(Object value) {
        if (!(value instanceof KOMEProgressionNpcRoleLease)) return false;
        KOMEProgressionNpcRoleLease other=(KOMEProgressionNpcRoleLease)value;
        return npc.equals(other.npc)&&player.equals(other.player)&&role==other.role&&token.equals(other.token);
    }
}
