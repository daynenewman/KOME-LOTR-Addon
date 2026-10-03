package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side relationship-death chat delivery, including persistent offline delivery. */
public final class KOMERelationshipDeathNotificationService {
    private KOMERelationshipDeathNotificationService() { }

    public static String deathSuffix(DamageSource source, LOTREntityNPC npc) {
        if (source == null || npc == null) return "died";
        try {
            String full = source.func_151519_b(npc).getUnformattedText();
            String[] names = new String[] { npc.getNPCName(), npc.getCommandSenderName() };
            for (String name : names) {
                if (name == null || name.length() == 0 || full == null) continue;
                if (full.equals(name)) return "died";
                String prefix = name + " ";
                if (full.startsWith(prefix)) {
                    String suffix = full.substring(prefix.length()).trim();
                    if (suffix.length() > 0) return suffix;
                }
            }
        } catch (RuntimeException ignored) { }
        Entity attacker = source.getEntity();
        if (attacker != null) {
            String name = attacker.getCommandSenderName();
            if (name != null && name.trim().length() > 0) return "was slain by " + name.trim();
        }
        String type = source.getDamageType();
        return type == null || type.trim().length() == 0 ? "died" : fallbackDamageText(type);
    }

    public static void queue(KOMEWorldData data, UUID playerId, KOMEPlayerProgression progression,
            KOMERelationshipDeathNotice notice) {
        if (data == null || playerId == null || progression == null || notice == null) return;
        if (progression.queueRelationshipDeathNotice(notice)) data.markDirty();
    }

    public static boolean deliverIfOnline(KOMEWorldData data, UUID playerId) {
        EntityPlayerMP player = findOnlinePlayer(playerId);
        return player != null && deliverPending(player, data, false);
    }

    public static boolean deliverPending(EntityPlayerMP player, KOMEWorldData data, boolean returningPlayer) {
        if (player == null || data == null) return false;
        KOMEPlayerProgression progression = data.progressions.get(player.getUniqueID());
        if (progression == null || progression.pendingRelationshipDeathNoticeCount() == 0) return false;
        List<KOMERelationshipDeathNotice> notices = progression.drainRelationshipDeathNotices();
        for (KOMERelationshipDeathNotice notice : notices)
            player.addChatMessage(new ChatComponentText(ownerMessage(notice, returningPlayer)));
        data.markDirty();
        return !notices.isEmpty();
    }

    public static void notifyKiller(EntityPlayer killer, String npcName,
            Map<KOMERelationshipDeathNotice.Role, List<String>> affectedNames,
            Map<KOMERelationshipDeathNotice.Role, Boolean> killedOwn) {
        if (killer == null || affectedNames == null || affectedNames.isEmpty()) return;
        String name = npcName == null || npcName.trim().length() == 0 ? "the NPC" : npcName.trim();
        for (KOMERelationshipDeathNotice.Role role : KOMERelationshipDeathNotice.Role.values()) {
            List<String> names = affectedNames.get(role);
            boolean own = killedOwn != null && Boolean.TRUE.equals(killedOwn.get(role));
            if ((names == null || names.isEmpty()) && !own) continue;
            killer.addChatMessage(new ChatComponentText(killerMessage(name, role, names, own)));
        }
    }

    static String ownerMessage(KOMERelationshipDeathNotice notice, boolean returningPlayer) {
        StringBuilder message = new StringBuilder();
        message.append(EnumChatFormatting.GRAY);
        if (returningPlayer) {
            message.append("While you were away, your ");
        } else {
            message.append("Your ");
        }
        message.append(notice.role.key).append(", ")
            .append(EnumChatFormatting.GOLD).append(notice.npcName)
            .append(EnumChatFormatting.GRAY).append(", ").append(notice.deathSuffix);
        if (!endsWithPunctuation(notice.deathSuffix)) message.append('.');
        return message.toString();
    }

    static String killerMessage(String npcName, KOMERelationshipDeathNotice.Role role,
            List<String> affectedNames, boolean own) {
        StringBuilder message = new StringBuilder();
        message.append(EnumChatFormatting.GRAY).append("You have slain ");
        if (own) {
            message.append("your own ").append(role.key).append(", ")
                .append(EnumChatFormatting.RED).append(npcName).append(EnumChatFormatting.GRAY);
            if (affectedNames != null && !affectedNames.isEmpty()) {
                message.append(", also ").append(role.key).append(" of ")
                    .append(formatAudience(affectedNames));
            }
        } else {
            message.append(EnumChatFormatting.RED).append(npcName)
                .append(EnumChatFormatting.GRAY).append(", ").append(role.key).append(" of ")
                .append(formatAudience(affectedNames));
        }
        message.append('.');
        return message.toString();
    }

    public static String eventId(UUID deadNpc, long worldTime) {
        return (deadNpc == null ? "unknown" : deadNpc.toString()) + "@" + Math.max(0L, worldTime);
    }

    public static String playerFaction(KOMEWorldData data, UUID playerId, KOMEPlayerProgression progression) {
        if (data == null || playerId == null) return "";
        String faction = KOMEAlliance.normalizeFactionKey(data.lastKnownPlayerFactions.get(playerId));
        if (faction.length() > 0) return faction;
        if (progression == null) return "";
        KOMESerfKnightProgression state = progression.getSerfKnightProgression();
        if (state.hasLiege()) {
            faction = KOMEAlliance.normalizeFactionKey(state.getLiege().factionKey);
            if (faction.length() > 0) return faction;
        }
        if (state.getSerfdomMaster().isSet()) {
            faction = KOMEAlliance.normalizeFactionKey(state.getSerfdomMaster().factionKey);
            if (faction.length() > 0) return faction;
        }
        return KOMEAlliance.normalizeFactionKey(progression.getPledgedLordFaction());
    }

    public static String rememberedPlayerName(KOMEWorldData data, UUID playerId) {
        if (data == null || playerId == null) return "unknown player";
        String name = data.playerNames.get(playerId);
        if (name != null && name.trim().length() > 0) return name.trim();
        EntityPlayerMP online = findOnlinePlayer(playerId);
        if (online != null) return online.getCommandSenderName();
        String raw = playerId.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    private static EntityPlayerMP findOnlinePlayer(UUID playerId) {
        if (playerId == null) return null;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) return null;
        for (Object value : server.getConfigurationManager().playerEntityList) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                if (playerId.equals(player.getUniqueID())) return player;
            }
        }
        return null;
    }

    private static String formatAudience(List<String> values) {
        List<String> names = new ArrayList<String>();
        if (values != null) for (String value : values)
            if (value != null && value.trim().length() > 0 && !names.contains(value.trim())) names.add(value.trim());
        if (names.isEmpty()) return "an unknown player";
        if (names.size() > 3) return names.size() + " players";
        if (names.size() == 1) return names.get(0);
        if (names.size() == 2) return names.get(0) + " and " + names.get(1);
        return names.get(0) + ", " + names.get(1) + ", and " + names.get(2);
    }

    private static String fallbackDamageText(String type) {
        if ("fall".equals(type)) return "fell to their death";
        if ("drown".equals(type)) return "drowned";
        if ("starve".equals(type)) return "starved to death";
        if ("inFire".equals(type) || "onFire".equals(type) || "lava".equals(type)) return "burned to death";
        if ("inWall".equals(type)) return "suffocated";
        if ("outOfWorld".equals(type)) return "fell into the void";
        if ("cactus".equals(type)) return "was killed by a cactus";
        if (type.startsWith("explosion")) return "was killed by an explosion";
        if ("magic".equals(type) || "indirectMagic".equals(type)) return "was killed by magic";
        if ("wither".equals(type)) return "withered away";
        return "died (" + type + ")";
    }

    private static boolean endsWithPunctuation(String value) {
        if (value == null || value.length() == 0) return false;
        char last = value.charAt(value.length() - 1);
        return last == '.' || last == '!' || last == '?';
    }
}
