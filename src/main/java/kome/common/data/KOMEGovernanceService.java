package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** War-scoped permissions. Never calls pledge release, transfers assets, heals or refunds. */
public final class KOMEGovernanceService {
    private KOMEGovernanceService() { }

    public static final class Decision {
        public final boolean allowed;
        public final String faction, reason;
        private Decision(boolean allowed, String faction, String reason) {
            this.allowed = allowed; this.faction = faction; this.reason = reason;
        }
        static Decision allow(String faction) { return new Decision(true, faction, ""); }
        static Decision deny(String reason) { return new Decision(false, "", reason); }
    }

    public static KOMEPlayerGovernance record(KOMEWorldData data, UUID player, String warId) {
        return data == null ? null : data.playerGovernance.get(KOMEPlayerGovernance.key(player, data.warSeason.seasonId, warId));
    }

    public static List<KOMEPlayerGovernance> records(KOMEWorldData data, UUID player) {
        List<KOMEPlayerGovernance> result = new ArrayList<KOMEPlayerGovernance>();
        if (data != null) for (String key : new TreeSet<String>(data.playerGovernance.keySet())) {
            KOMEPlayerGovernance record = data.playerGovernance.get(key);
            if (record.player.equals(player)) result.add(record);
        }
        return Collections.unmodifiableList(result);
    }

    /** Capture known membership at defeat or before a real native pledge transition can erase it. */
    static void retainDefeat(KOMEWorldData data, UUID player, String origin, long now) {
        if (player == null || !data.warSeason.isFactionDefeated(origin)) return;
        for (KOMEWar war : data.wars.values()) {
            if (war.isEnded() || war.sideOf(origin) == 0 || record(data, player, war.id) != null) continue;
            publish(data, new KOMEPlayerGovernance(player, data.warSeason.seasonId, war.id, origin, "",
                KOMEPlayerGovernance.State.SUBMITTED, Math.max(0L, now), 1,
                "Defeat retains civilian status; choose a valid allied exile host to participate"), "SERVER");
        }
    }

    static void retainKnownDefeatedPlayers(KOMEWorldData data, String origin, long now) {
        java.util.Set<UUID> players = new java.util.HashSet<UUID>(data.lastKnownPlayerFactions.keySet());
        players.addAll(data.progressions.keySet());
        UUID ruler = data.getFactionKingId(origin);
        if (ruler != null) players.add(ruler);
        for (UUID player : players) if (origin.equals(data.getPlayerFactionKey(player))) retainDefeat(data, player, origin, now);
    }

    /** Self-service transition: actor is obtained from the server sender, never a target supplied by a client. */
    public static Decision choose(KOMEWorldData data, UUID actor, String warId,
            KOMEPlayerGovernance.State state, String host, long now) {
        if (data == null || actor == null || state == null || now < 0) return Decision.deny("A player and valid governance choice are required.");
        synchronized (data) {
            data.ensureWritable();
            KOMEWar war = data.wars.get(warId);
            if (war == null || !war.isActive() || !data.warSeason.isPopulationPayoutEnabled())
                return Decision.deny("Choose an active war during War or Finale.");
            KOMEPlayerGovernance prior = record(data, actor, warId);
            String origin = prior == null ? data.getPlayerFactionKey(actor) : prior.origin;
            if (war.sideOf(origin) == 0 || !data.warSeason.isFactionDefeated(origin))
                return Decision.deny("Your originating faction has no canonical defeat in this war/season.");
            String hostKey = state == KOMEPlayerGovernance.State.EXILED ? KOMEAlliance.normalizeFactionKey(host) : "";
            if (state == KOMEPlayerGovernance.State.EXILED) {
                String invalid = hostReason(data, war, origin, hostKey);
                if (!invalid.isEmpty()) return Decision.deny(invalid);
            }
            if (prior != null && now < prior.changedAt) return Decision.deny("Governance clock moved backwards; retry after " + prior.changedAt + ".");
            if (prior != null && prior.state == state && prior.host.equals(hostKey))
                return Decision.allow(state == KOMEPlayerGovernance.State.EXILED ? hostKey : origin);
            if (prior != null && prior.revision == Long.MAX_VALUE) return Decision.deny("Governance revision exhausted; administrator inspection required.");
            publish(data, new KOMEPlayerGovernance(actor, data.warSeason.seasonId, warId, origin, hostKey,
                state, now, prior == null ? 1 : prior.revision + 1, "Player selected " + state), actor.toString());
            return Decision.allow(state == KOMEPlayerGovernance.State.EXILED ? hostKey : origin);
        }
    }

    private static String hostReason(KOMEWorldData data, KOMEWar war, String origin, String host) {
        if (host.isEmpty() || host.equals(origin) || !KOMEAlliance.allFactionKeys().contains(host))
            return "Exile requires a different recognized allied host faction.";
        if (!KOMEDiplomacyService.areAllies(data, origin, host)) return "Exile host must have the canonical ALLY relation with " + origin + ".";
        if (data.warSeason.isFactionDefeated(host)) return "The proposed host is defeated in this season.";
        return "";
    }

    /** Read-only permission, including before the defeated player makes a choice or reconnects. */
    public static Decision participation(KOMEWorldData data, UUID player, KOMEWar war, String actingFaction) {
        String acting = KOMEAlliance.normalizeFactionKey(actingFaction);
        if (data == null || player == null || war == null) return Decision.deny("War participation authority is unavailable.");
        if (war.isEnded()) return Decision.allow(acting);
        KOMEPlayerGovernance record = record(data, player, war.id);
        String origin = record == null ? data.getPlayerFactionKey(player) : record.origin;
        if (record == null) {
            if (war.sideOf(origin) != 0 && data.warSeason.isFactionDefeated(origin))
                return Decision.deny("Faction " + origin + " is defeated in " + war.id + "; use /governance submit or exile before participating.");
            return Decision.allow(acting);
        }
        if (record.state == KOMEPlayerGovernance.State.SUBMITTED)
            return Decision.deny("Submitted players cannot participate directly or indirectly in " + war.id + ". Civilian activities remain available.");
        String invalid = hostReason(data, war, record.origin, record.host);
        if (!invalid.isEmpty()) return Decision.deny("Exile host is no longer valid: " + invalid + " Participation is suspended.");
        if (!war.sameSide(record.origin, record.host))
            return Decision.deny("Host " + record.host + " has not joined the originating side of " + war.id + "; existing war authorization is required before participation.");
        if (!acting.equals(record.host)) return Decision.deny("Exile participation in " + war.id + " must be under host " + record.host + ". Native assets remain preserved.");
        return Decision.allow(record.host);
    }

    /** Military actions for a participating faction include supply, recruitment and company control. */
    public static Decision militaryAction(KOMEWorldData data, UUID player, String faction) {
        if (data == null || player == null) return Decision.deny("Player military authority is unavailable.");
        for (KOMEWar war : data.wars.values()) if (!war.isEnded() && war.sideOf(faction) != 0) {
            Decision decision = participation(data, player, war, faction);
            if (!decision.allowed) return decision;
        }
        return Decision.allow(KOMEAlliance.normalizeFactionKey(faction));
    }

    /** Used only by war actions; native identity APIs remain unchanged. Conflicting host affiliations fail closed. */
    public static Decision effectiveFaction(KOMEWorldData data, UUID player, String opposingFaction) {
        if (data == null || player == null) return Decision.deny("Player war identity is unavailable.");
        String faction = data.getPlayerFactionKey(player), host = "";
        for (KOMEPlayerGovernance record : records(data, player)) {
            KOMEWar war = data.wars.get(record.warId);
            if (record.season != data.warSeason.seasonId || war == null || war.isEnded()
                    || war.sideOf(opposingFaction) == 0) continue;
            Decision permission = participation(data, player, war, record.host);
            if (!permission.allowed) return permission;
            if (!host.isEmpty() && !host.equals(permission.faction)) return Decision.deny("Several wars require different exile hosts for this action.");
            host = permission.faction;
        }
        if (!host.isEmpty()) faction = host;
        Decision permission = militaryAction(data, player, faction);
        return permission.allowed ? Decision.allow(faction) : permission;
    }

    /** Live invalidation is permanent until the player makes another valid choice. No destructive cleanup. */
    public static int reconcile(KOMEWorldData data, long now) {
        if (data == null) return 0;
        synchronized (data) {
            data.ensureWritable();
            int changed = 0;
            for (KOMEPlayerGovernance record : new ArrayList<KOMEPlayerGovernance>(data.playerGovernance.values())) {
                KOMEWar war = data.wars.get(record.warId);
                if (record.season != data.warSeason.seasonId || record.state != KOMEPlayerGovernance.State.EXILED
                        || war == null || war.isEnded()) continue;
                String reason = hostReason(data, war, record.origin, record.host);
                if (reason.isEmpty() || now < record.changedAt || record.revision == Long.MAX_VALUE) continue;
                publish(data, new KOMEPlayerGovernance(record.player, record.season, record.warId, record.origin,
                    "", KOMEPlayerGovernance.State.SUBMITTED, now, record.revision + 1, reason), "SERVER");
                changed++;
            }
            return changed;
        }
    }

    private static void publish(KOMEWorldData data, KOMEPlayerGovernance next, String actor) {
        KOMEPlayerGovernance previous = data.playerGovernance.get(next.key());
        List<KOMEAuditEntry> audit = new ArrayList<KOMEAuditEntry>(data.centralAudit);
        boolean dirty = data.isDirty();
        try {
            data.playerGovernance.put(next.key(), next);
            KOMEAuditService.record(data, next.changedAt, "GOVERNANCE", next.state.name(), actor, next.key(), next.reason,
                "origin=" + next.origin + ";host=" + next.host + ";revision=" + next.revision);
        } catch (RuntimeException failure) {
            if (previous == null) data.playerGovernance.remove(next.key()); else data.playerGovernance.put(next.key(), previous);
            data.centralAudit.clear(); data.centralAudit.addAll(audit); data.setDirty(dirty); throw failure;
        }
    }

    static void write(KOMEWorldData data, NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (String key : new TreeSet<String>(data.playerGovernance.keySet())) list.appendTag(data.playerGovernance.get(key).write());
        nbt.setInteger("GovernanceSchema", 1); nbt.setTag("PlayerGovernance", list);
    }

    static void read(KOMEWorldData data, NBTTagCompound nbt, boolean required) {
        data.playerGovernance.clear();
        if (!required && !nbt.hasKey("GovernanceSchema") && !nbt.hasKey("PlayerGovernance")) return;
        if (!nbt.hasKey("GovernanceSchema", 3) || nbt.getInteger("GovernanceSchema") != 1 || !nbt.hasKey("PlayerGovernance", 9))
            throw new IllegalArgumentException("Missing or unsupported governance authority");
        NBTTagList list = (NBTTagList) nbt.getTag("PlayerGovernance");
        if (list.tagCount() > 0 && list.func_150303_d() != 10) throw new IllegalArgumentException("Invalid governance list");
        for (int i = 0; i < list.tagCount(); i++) {
            KOMEPlayerGovernance record = KOMEPlayerGovernance.read(list.getCompoundTagAt(i));
            if (record.season > data.warSeason.seasonId || data.playerGovernance.put(record.key(), record) != null)
                throw new IllegalArgumentException("Duplicate or future governance record");
        }
    }
}
