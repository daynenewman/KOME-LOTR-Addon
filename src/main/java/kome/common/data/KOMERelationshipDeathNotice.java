package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

/** One-shot, persisted notice for the death of a player's progression relationship NPC. */
public final class KOMERelationshipDeathNotice {
    public enum Role {
        MASTER("master"),
        LIEGE("liege"),
        KING("king");

        public final String key;
        Role(String key) { this.key = key; }

        public static Role forKey(String key) {
            if (key != null) for (Role role : values()) if (role.key.equals(key)) return role;
            return null;
        }
    }

    public final String eventId;
    public final Role role;
    public final String npcName;
    public final String deathSuffix;
    public final long occurredAtMillis;

    public KOMERelationshipDeathNotice(String eventId, Role role, String npcName,
            String deathSuffix, long occurredAtMillis) {
        if (eventId == null || eventId.trim().length() == 0 || role == null)
            throw new IllegalArgumentException("Relationship death notices require an event id and role.");
        this.eventId = eventId.trim();
        this.role = role;
        this.npcName = clean(npcName, "Unknown");
        this.deathSuffix = clean(deathSuffix, "died");
        this.occurredAtMillis = Math.max(0L, occurredAtMillis);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Event", eventId);
        tag.setString("Role", role.key);
        tag.setString("NPC", npcName);
        tag.setString("Death", deathSuffix);
        tag.setLong("OccurredAt", occurredAtMillis);
        return tag;
    }

    public static KOMERelationshipDeathNotice readFromNBT(NBTTagCompound tag) {
        if (tag == null) return null;
        Role role = Role.forKey(tag.getString("Role"));
        String eventId = tag.getString("Event");
        if (role == null || eventId == null || eventId.trim().length() == 0) return null;
        try {
            return new KOMERelationshipDeathNotice(eventId, role, tag.getString("NPC"),
                tag.getString("Death"), tag.getLong("OccurredAt"));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static String clean(String value, String fallback) {
        String cleaned = value == null ? "" : value.trim();
        return cleaned.length() == 0 ? fallback : cleaned;
    }
}
