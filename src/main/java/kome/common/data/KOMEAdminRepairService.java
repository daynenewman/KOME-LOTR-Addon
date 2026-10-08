package kome.common.data;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Server-thread, ephemeral, actor/world-bound plans. No save-time or automatic repair. */
public final class KOMEAdminRepairService {
    public static final int MAX_PLANS = 64;
    public static final long PLAN_LIFETIME_MILLIS = 120000L;
    private final Map<String, Plan> plans = new LinkedHashMap<String, Plan>();

    public UUID onlineRulerForPlan(KOMEWorldData data, String actor, String token) {
        Plan p = plans.get(token);
        return p != null && p.data == data && p.actor.equals(actor) ? p.ruler : null;
    }

    public Result preview(KOMEWorldData data, boolean authorized, String actor, String domain,
            String subject, String other, UUID onlineRuler, String onlineName, long now) {
        try {
            authority(data, authorized, actor);
            if (now < 0 || now > Long.MAX_VALUE - PLAN_LIFETIME_MILLIS)
                throw new IllegalArgumentException("Invalid preview time");
            if ("ruler".equals(domain) || "diplomacy".equals(domain)) subject = KOMEAdminDiagnostics.faction(subject);
            if ("diplomacy".equals(domain)) other = KOMEAdminDiagnostics.faction(other);
            if ("ownership".equals(domain)) subject = KOMEConquestTile.normalizeId(subject);
            String reason = repairReason(data, domain, subject, other, onlineRuler, onlineName);
            NBTTagCompound before = snapshot(data, domain, subject, other);
            expire(now);
            if (plans.size() >= MAX_PLANS) throw new IllegalStateException("Repair plan capacity reached; wait for expiration");
            String token = UUID.randomUUID().toString();
            plans.put(token, new Plan(data, actor, domain, subject, other, onlineRuler, onlineName,
                now, before));
            audit(data, actor, subject, "REPAIR_PREVIEW", reason, domain + ";plan=" + token, now);
            return new Result(true, reason + "; apply /kome repair apply " + token + " within 120 seconds", token);
        } catch (IllegalArgumentException | IllegalStateException denied) {
            audit(data, actor, subject, "REPAIR_DENIED", denied.getMessage(), domain, now);
            return new Result(false, denied.getMessage(), "");
        }
    }

    public Result apply(KOMEWorldData data, boolean authorized, String actor, String token,
            UUID onlineRuler, String onlineName, long now) {
        Plan plan = plans.get(token);
        try {
            authority(data, authorized, actor);
            if (plan == null) throw new IllegalArgumentException("Unknown or consumed plan; preview again");
            if (plan.data != data || !plan.actor.equals(actor))
                throw new IllegalArgumentException("Plan belongs to another actor or world; preview your own plan");
            // A rejected owner attempt consumes the plan; a different actor cannot invalidate it.
            plans.remove(token);
            if (now < plan.createdAt || now - plan.createdAt >= PLAN_LIFETIME_MILLIS)
                throw new IllegalArgumentException("Plan expired; preview again");
            if (!plan.before.equals(snapshot(data, plan.domain, plan.subject, plan.other)))
                throw new IllegalArgumentException("State changed since preview; no repair applied; preview again");
            if ("ruler".equals(plan.domain) && (!plan.ruler.equals(onlineRuler) || !plan.name.equals(onlineName)))
                throw new IllegalArgumentException("Online ruler identity/name changed or is unavailable; preview again");
            String reason = repairReason(data, plan.domain, plan.subject, plan.other, onlineRuler, onlineName);
            boolean changed;
            if ("ownership".equals(plan.domain)) changed = KOMEConquestClaimService.repairMissingOwnerAlias(
                data, data.conquestTiles.get(plan.subject));
            else if ("diplomacy".equals(plan.domain)) changed = KOMEDiplomacyService.repairObsoletePending(
                data, plan.subject, plan.other, actor, now);
            else if ("ruler".equals(plan.domain)) changed = KOMERulerService.repair(data,
                plan.subject, onlineRuler, onlineName).changed;
            else {
                data.publicWaypoints.repairPublicLink(data, UUID.fromString(plan.subject), actor, now);
                changed = true;
            }
            if (!changed) throw new IllegalStateException("Repair no longer applicable; no change made");
            audit(data, actor, plan.subject, "REPAIR_APPLY", reason,
                plan.domain + ";plan=" + token + ";before=" + describe(plan.domain, plan.before)
                    + ";after=" + describe(plan.domain, snapshot(data, plan.domain, plan.subject, plan.other)), now);
            return new Result(true, "Applied: " + reason, token);
        } catch (IllegalArgumentException | IllegalStateException denied) {
            audit(data, actor, plan == null ? token : plan.subject, "REPAIR_DENIED",
                denied.getMessage(), "plan=" + token, now);
            return new Result(false, denied.getMessage(), "");
        }
    }

    private static void authority(KOMEWorldData data, boolean authorized, String actor) {
        if (!authorized) throw new IllegalArgumentException("Operator permission level 2 is required");
        KOMEPublicWaypoint.actor(actor);
        if (data == null || data instanceof KOMEClientData)
            throw new IllegalArgumentException("Canonical server world authority is required");
        data.ensureWritable();
    }

    private static String repairReason(KOMEWorldData data, String domain, String subject,
            String other, UUID ruler, String name) {
        if ("ownership".equals(domain)) {
            KOMEConquestTile tile = data.conquestTiles.get(subject);
            if (tile == null || !subject.equals(tile.id)) throw new IllegalArgumentException("Unknown or inconsistent tile identity");
            if (!KOMEAlliance.allFactionKeys().contains(tile.currentRulingFaction)
                    || tile.ownerFaction == null || !tile.ownerFaction.isEmpty())
                throw new IllegalArgumentException("No unambiguous missing ownership alias; valid, legacy-only or conflicting ownership is preserved");
            return "Restore empty ownership alias to current owner " + tile.currentRulingFaction;
        }
        if ("diplomacy".equals(domain)) {
            if (!KOMEAllianceAuthority.hasAuthoritativePair(subject, other))
                throw new IllegalArgumentException("Two distinct authoritative LOTR factions are required");
            KOMEDiplomacyRecord r = data.canonicalDiplomacyRecords.get(KOMEDiplomacyRecord.pairKey(subject, other));
            KOMEDiplomacyRelation current = KOMEDiplomacyService.getRelation(data, subject, other);
            if (r == null || r.pendingTarget == null || r.pendingTarget.rank() > current.rank())
                throw new IllegalArgumentException("No obsolete pending request; valid consent and LOTR relation are preserved");
            if (r.relation == null || r.requestingFaction == null || r.receivingFaction == null
                    || r.requestingFaction.equals(r.receivingFaction)
                    || !(r.requestingFaction.equals(subject) || r.requestingFaction.equals(other))
                    || !(r.receivingFaction.equals(subject) || r.receivingFaction.equals(other)))
                throw new IllegalArgumentException("Malformed workflow consent; manual review required");
            if (!r.key().equals(KOMEDiplomacyRecord.pairKey(subject, other)))
                throw new IllegalArgumentException("Workflow pair identity is inconsistent; manual review required");
            return "Discard obsolete pending " + r.pendingTarget.key + "; keep LOTR relation " + current.key;
        }
        if ("ruler".equals(domain)) {
            if (ruler == null || !ruler.equals(KOMERulerService.getRuler(data, subject)))
                throw new IllegalArgumentException("Matching online ruler UUID is required; no assignment inferred");
            KOMEPublicWaypoint.text(name, 64, false);
            if (name.equals(KOMERulerService.getRulerName(data, subject)))
                throw new IllegalArgumentException("Ruler cached name is already valid");
            return "Refresh ruler cached name " + cachedName(KOMERulerService.getRulerName(data, subject))
                + " -> " + name + "; preserve UUID and office";
        }
        if ("waypoint".equals(domain)) {
            KOMEPublicWaypoint r = data.publicWaypoints.get(UUID.fromString(subject));
            if (r == null || r.source != KOMEPublicWaypoint.Source.PUBLIC)
                throw new IllegalArgumentException("Existing public waypoint required; native, migrated and quarantined records are preserved");
            String resolved = KOMEPublicWaypointRegistry.resolveTile(data, r.dimension, r.x, r.z);
            KOMEPublicWaypoint occupant = data.publicWaypoints.forTile(resolved);
            if (resolved.equals(r.tileId) || occupant != null && !occupant.id.equals(r.id))
                throw new IllegalArgumentException("Link is valid or resolved tile is occupied; no safe repair");
            return "Relink public waypoint " + r.tileId + " -> " + resolved + "; preserve coordinates, level and identity";
        }
        throw new IllegalArgumentException("No safe repair for this domain; population rates are derived and capital changes require validated relocation");
    }

    private static NBTTagCompound snapshot(KOMEWorldData data, String domain, String subject, String other) {
        NBTTagCompound n = new NBTTagCompound();
        if ("ownership".equals(domain)) {
            KOMEConquestTile t = data.conquestTiles.get(subject);
            if (t == null) throw new IllegalArgumentException("Tile removed since preview");
            n.setTag("Tile", t.projectToNBT());
            n.setString("RawCurrent", String.valueOf(t.currentRulingFaction));
            n.setString("RawAlias", String.valueOf(t.ownerFaction));
        } else if ("diplomacy".equals(domain)) {
            KOMEDiplomacyRecord r = data.canonicalDiplomacyRecords.get(KOMEDiplomacyRecord.pairKey(subject, other));
            if (r == null) throw new IllegalArgumentException("Workflow removed since preview");
            if (r.relation == null) throw new IllegalArgumentException("Workflow relation is malformed; manual review required");
            n.setTag("Workflow", r.writeToNBT());
            n.setString("Effective", KOMEDiplomacyService.getRelation(data, subject, other).key);
        } else if ("ruler".equals(domain)) {
            n.setString("UUID", String.valueOf(KOMERulerService.getRuler(data, subject)));
            n.setString("Name", KOMERulerService.getRulerName(data, subject));
        } else if ("waypoint".equals(domain)) {
            KOMEPublicWaypoint r = data.publicWaypoints.get(UUID.fromString(subject));
            if (r == null) throw new IllegalArgumentException("Waypoint removed since preview");
            n.setTag("Waypoint", r.writeToNBT());
            n.setLong("Revision", data.publicWaypoints.revision());
            n.setString("Resolved", KOMEPublicWaypointRegistry.resolveTile(data, r.dimension, r.x, r.z));
        }
        return n;
    }

    private static String describe(String domain, NBTTagCompound n) {
        if ("ownership".equals(domain)) return "current=" + n.getString("RawCurrent") + ",alias=" + n.getString("RawAlias");
        if ("ruler".equals(domain)) return n.getString("UUID") + ",name=" + cachedName(n.getString("Name"));
        if ("diplomacy".equals(domain)) return "effective=" + n.getString("Effective")
            + ",pending=" + n.getCompoundTag("Workflow").getString("PendingTarget");
        NBTTagCompound w = n.getCompoundTag("Waypoint");
        return "tile=" + w.getString("Tile") + ",revision=" + n.getLong("Revision")
            + ",position=" + w.getInteger("Dimension") + ":" + w.getInteger("X")
            + "," + w.getInteger("Y") + "," + w.getInteger("Z") + ",level=" + w.getInteger("Level");
    }

    private static String cachedName(String value) {
        // Corrupted saved names must not hide the confirmation token or the after-value.
        return value.length() <= 64 ? value : value.substring(0, 61) + "...";
    }

    private void expire(long now) {
        Iterator<Plan> it = plans.values().iterator();
        while (it.hasNext()) {
            Plan p = it.next();
            if (now < p.createdAt || now - p.createdAt >= PLAN_LIFETIME_MILLIS) it.remove();
        }
    }

    private static void audit(KOMEWorldData data, String actor, String subject, String action,
            String reason, String details, long now) {
        if (data != null && !(data instanceof KOMEClientData) && !data.isWriteBlocked())
            KOMEAuditService.record(data, now, "ADMIN", action, KOMEAdminDiagnostics.line(actor),
                KOMEAdminDiagnostics.line(subject), KOMEAdminDiagnostics.line(reason), KOMEAdminDiagnostics.line(details));
    }

    private static final class Plan {
        final KOMEWorldData data;
        final String actor, domain, subject, other, name;
        final UUID ruler;
        final long createdAt;
        final NBTTagCompound before;
        Plan(KOMEWorldData data, String actor, String domain, String subject, String other,
                UUID ruler, String name, long now, NBTTagCompound before) {
            this.data = data; this.actor = actor; this.domain = domain; this.subject = subject;
            this.other = other; this.ruler = ruler; this.name = name; this.createdAt = now;
            this.before = (NBTTagCompound) before.copy();
        }
    }

    public static final class Result {
        public final boolean allowed;
        public final String reason, token;
        private Result(boolean allowed, String reason, String token) {
            this.allowed = allowed; this.reason = KOMEAdminDiagnostics.line(reason); this.token = token;
        }
    }
}
