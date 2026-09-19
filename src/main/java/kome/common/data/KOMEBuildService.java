package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.math.BigInteger;

/**
 * Authoritative Build mutation service. GUIs and admin commands both use this boundary.
 */
public final class KOMEBuildService {
    private KOMEBuildService() {
    }

    /** Canonical registration boundary. The registering manager's exact time is approved immediately. */
    public static KOMEPlayerBuild create(KOMEWorldData data, String name, String tileId, int dimension,
            double x, double y, double z, UUID builder, String builderName, String builderFaction,
            String populationFaction, KOMEBuildType type, long centiHours, long nowMillis) {
        if (type == null) throw new IllegalArgumentException("Build type is required.");
        KOMEBuildTime.requireNonnegative(centiHours);
        requireWritable(data);
        Decision coordinates = validateCoordinates(tileId, dimension, x, y, z);
        if (!coordinates.allowed) throw new IllegalArgumentException(coordinates.reason);
        Decision placement = canPlace(data, builder, builderFaction, tileId, populationFaction);
        if (!placement.allowed) throw new IllegalArgumentException(placement.reason);
        KOMEPlayerBuild build = new KOMEPlayerBuild();

        build.displayName = KOMEPlayerBuild.sanitizeName(name);
        build.tileId = KOMEConquestTile.normalizeId(tileId);
        build.dimension = dimension;
        build.x = x;
        build.y = y;
        build.z = z;
        build.builderUuid = builder;
        build.builderName = safe(builderName);
        build.managerUuid = builder;
        build.managerName = safe(builderName);
        build.originalBuilderFaction = KOMEAlliance.normalizeFactionKey(builderFaction);
        build.populationFaction = KOMEAlliance.normalizeFactionKey(populationFaction);
        build.type = type;
        build.createdAtMillis = Math.max(0L, nowMillis);
        build.updatedAtMillis = Math.max(0L, nowMillis);
        build.markerLabel = build.displayName;
        KOMEBuildContribution initial = newSubmission(data, build, builder, builderName, builderFaction, centiHours, nowMillis);
        boolean managerSubmission = isManager(build, builder);
        if (managerSubmission) KOMEBuildTime.add(build.approvedCentiHours(), centiHours);
        build.id = data.nextBuildId();
        build.contributions.add(initial);
        data.builds.put(build.id, build);
        audit(data, build, "REGISTER", builder, builderName, "Build registered", "", nowMillis);
        auditContribution(data, build, initial, "SUBMIT", builder, builderName, "Initial submission", 0L, nowMillis);
        if (managerSubmission) {
            autoApproveManagerSubmission(data, build, initial, builder, builderName, nowMillis);
        }
        data.markDirty();
        return build;
    }

    public static Decision canPlace(KOMEWorldData data, String playerFaction, String tileId,
            String populationFaction) {
        return canPlace(data, null, playerFaction, tileId, populationFaction);
    }

    public static Decision canPlace(KOMEWorldData data, UUID builder, String playerFaction, String tileId,
            String populationFaction) {
        String player = KOMEAlliance.normalizeFactionKey(playerFaction);
        String owner = KOMEAlliance.normalizeFactionKey(populationFaction);
        KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (data == null || tile == null || tile.projectRulingFaction().isEmpty()) return Decision.deny("The selected conquest tile is not claimed.");
        String controller = KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        KOMEForeignConstructionService.Decision construction = KOMEForeignConstructionService.canConstruct(data, tile.id, builder, player);
        if (!construction.allowed) return Decision.deny(construction.reason);
        if (owner.length() == 0) return Decision.deny("Choose a population-owning faction.");
        if (owner.equals(player)) return Decision.allow();
        if (!isFriendlyOrAllied(data, player, owner) || !isFriendlyOrAllied(data, controller, owner)) {
            return Decision.deny("A foreign population owner must be Friendly or Allied with both your faction and the tile controller.");
        }
        return Decision.allow();
    }

    public static List<String> selectablePopulationOwners(KOMEWorldData data, String playerFaction, String tileId) {
        List<String> result = new ArrayList<String>();
        String player = KOMEAlliance.normalizeFactionKey(playerFaction);
        KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (player.length() == 0 || tile == null || tile.projectRulingFaction().isEmpty()) return result;
        if (!canPlace(data, player, tile.id, player).allowed) return result;
        result.add(player);
        String controller = KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        for (String candidate : KOMEAlliance.allFactionKeys()) {
            String owner = KOMEAlliance.normalizeFactionKey(candidate);
            if (owner.length() == 0 || result.contains(owner)) continue;
            if (isFriendlyOrAllied(data, player, owner) && isFriendlyOrAllied(data, controller, owner)) {
                result.add(owner);
            }
        }
        Collections.sort(result);
        if (result.remove(player)) result.add(0, player);
        return result;
    }

    /** Canonical contribution boundary: an existing Build supplies its only type. */
    public static KOMEBuildContribution addSubmission(KOMEWorldData data, KOMEPlayerBuild build,
            UUID contributor, String contributorName, String contributorFaction, long centiHours,
            boolean contributorIsManager, long nowMillis) {
        requireActive(data, build);
        boolean managerSubmission = contributorIsManager && isManager(build, contributor);
        long before = build.approvedCentiHours();
        if (managerSubmission) KOMEBuildTime.add(before, centiHours);
        KOMEBuildContribution contribution = newSubmission(data, build, contributor, contributorName,
                contributorFaction, centiHours, nowMillis);
        build.contributions.add(contribution);
        auditContribution(data, build, contribution, "SUBMIT", contributor, contributorName,
                "Contribution submitted", before, nowMillis);
        if (managerSubmission) {
            autoApproveManagerSubmission(data, build, contribution, contributor, contributorName, nowMillis);
        }
        return contribution;
    }

    private static void autoApproveManagerSubmission(KOMEWorldData data, KOMEPlayerBuild build,
            KOMEBuildContribution contribution, UUID manager, String managerName, long nowMillis) {
        Decision approved = review(data, build, contribution, contribution.centiHours,
                KOMEBuildContribution.APPROVED, manager, managerName, "APPROVE",
                "Manager contribution approved immediately", nowMillis);
        if (!approved.allowed) {
            throw new IllegalStateException("Prevalidated manager contribution could not be approved: " + approved.reason);
        }
    }

    private static KOMEBuildContribution newSubmission(KOMEWorldData data, KOMEPlayerBuild build,
            UUID contributor, String name, String faction, long centiHours, long nowMillis) {
        KOMEBuildTime.requireNonnegative(centiHours);
        build.validateContributions();
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = data.nextBuildContributionId(build);
        contribution.contributorUuid = contributor;
        contribution.contributorName = safe(name);
        contribution.contributorFaction = KOMEAlliance.normalizeFactionKey(faction);
        contribution.centiHours = centiHours;
        contribution.submittedAtMillis = Math.max(0L, nowMillis);
        return contribution;
    }

    public static Decision decideSubmission(KOMEWorldData data, KOMEPlayerBuild build, String contributionId,
            UUID manager, String managerName, boolean approve, String reason, long nowMillis) {
        return decideSubmission(data, build, contributionId, manager, managerName, false, approve, reason, nowMillis);
    }

    public static Decision decideSubmission(KOMEWorldData data, KOMEPlayerBuild build, String contributionId,
            UUID manager, String managerName, boolean admin, boolean approve, String reason, long nowMillis) {
        Decision permission = canReview(data, build, manager, admin);
        if (!permission.allowed) return permission;
        KOMEBuildContribution contribution = build.getContribution(contributionId);
        if (contribution == null) return Decision.deny("Unknown Build contribution.");
        String target = approve ? KOMEBuildContribution.APPROVED : KOMEBuildContribution.REJECTED;
        if (target.equals(contribution.status)) return Decision.allow();
        if (!contribution.isPending() && !(contribution.isApproved() && !approve))
            return Decision.deny("A rejected or removed contribution cannot be re-approved; submit a new contribution.");
        return review(data, build, contribution, contribution.centiHours, target,
                manager, managerName, approve ? "APPROVE" : "REJECT", reason, nowMillis);
    }

    /** Exact individual review amount. Staff privilege is unchanged from administrative sethours. */
    public static Decision adjustSubmission(KOMEWorldData data, KOMEPlayerBuild build, String contributionId,
            UUID actor, String actorName, boolean admin, String hours, String reason, long nowMillis) {
        Decision permission = canReview(data, build, actor, admin);
        if (!permission.allowed) return permission;
        if (!admin) return Decision.deny("Only administrators may adjust Build hours.");
        KOMEBuildContribution contribution = build.getContribution(contributionId);
        if (contribution == null || (!contribution.isPending() && !contribution.isApproved()))
            return Decision.deny("Only pending or approved contributions may be adjusted.");
        final long centiHours;
        try { centiHours = KOMEBuildTime.parseHours(hours); }
        catch (IllegalArgumentException invalid) { return Decision.deny(invalid.getMessage()); }
        if (contribution.isApproved() && contribution.centiHours == centiHours) return Decision.allow();
        return review(data, build, contribution, centiHours, KOMEBuildContribution.APPROVED,
                actor, actorName, "ADJUST", reason, nowMillis);
    }

    private static Decision review(KOMEWorldData data, KOMEPlayerBuild build, KOMEBuildContribution contribution,
            long hours, String target, UUID actor, String name, String action, String reason, long nowMillis) {
        long before = build.approvedCentiHours();
        long priorHours = contribution.centiHours;
        try {
            long without = before - (contribution.isApproved() ? priorHours : 0L);
            if (KOMEBuildContribution.APPROVED.equals(target)) KOMEBuildTime.add(without, hours);
            else KOMEBuildTime.requireNonnegative(hours);
        } catch (IllegalArgumentException invalid) { return Decision.deny(invalid.getMessage()); }
        contribution.centiHours = hours;
        contribution.status = target;
        contribution.decidedAtMillis = Math.max(0L, nowMillis);
        contribution.decidedByUuid = actor;
        contribution.decidedByName = safe(name);
        contribution.decisionReason = safe(reason).isEmpty() ? "Build contribution " + action.toLowerCase(java.util.Locale.ROOT) : safe(reason);
        audit(data, build, action, actor, name, contribution.decisionReason,
                "contribution=" + contribution.id + ";status=" + target
                + ";priorHours=" + KOMEBuildTime.formatHours(priorHours)
                + ";hours=" + KOMEBuildTime.formatHours(hours)
                + ";approvedCentiHoursDelta=" + (build.approvedCentiHours() - before), nowMillis);
        return Decision.allow();
    }

    public static Decision removeApprovedContribution(KOMEWorldData data, KOMEPlayerBuild build,
            String contributionId, UUID manager, String managerName, String reason, long nowMillis) {
        return removeApprovedContribution(data, build, contributionId, manager, managerName, false, reason, nowMillis);
    }

    public static Decision removeApprovedContribution(KOMEWorldData data, KOMEPlayerBuild build,
            String contributionId, UUID manager, String managerName, boolean admin, String reason, long nowMillis) {
        Decision permission = canReview(data, build, manager, admin);
        if (!permission.allowed) return permission;
        KOMEBuildContribution contribution = build.getContribution(contributionId);
        if (contribution != null && contribution.isRemoved()) return Decision.allow();
        if (contribution == null || !contribution.isApproved()) return Decision.deny("That contribution is not active and approved.");
        return review(data, build, contribution, contribution.centiHours, KOMEBuildContribution.REMOVED,
                manager, managerName, "APPROVE_REMOVE", reason, nowMillis);
    }

    /** Existing total repair semantics, preserving the replaced records and their amounts. */
    public static Decision setApprovedHours(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorName, boolean admin, String hours, long nowMillis) {
        Decision permission = canReview(data, build, actor, admin);
        if (!permission.allowed) return permission;
        if (!admin) return Decision.deny("Only administrators may adjust Build hours.");
        final long amount;
        try { amount = KOMEBuildTime.parseHours(hours); }
        catch (IllegalArgumentException invalid) { return Decision.deny(invalid.getMessage()); }
        long before = build.approvedCentiHours();
        if (before == amount) return Decision.allow();
        KOMEBuildContribution repair = newSubmission(data, build, actor, actorName,
                build.originalBuilderFaction, amount, nowMillis);
        repair.status = KOMEBuildContribution.APPROVED;
        repair.decidedAtMillis = Math.max(0L, nowMillis);
        repair.decidedByUuid = actor;
        repair.decidedByName = safe(actorName);
        repair.decisionReason = "Administrative sethours repair";
        for (KOMEBuildContribution contribution : build.contributions) {
            if (!contribution.isApproved()) continue;
            contribution.status = KOMEBuildContribution.REMOVED;
            contribution.decidedAtMillis = repair.decidedAtMillis;
            contribution.decidedByUuid = actor;
            contribution.decidedByName = repair.decidedByName;
            contribution.decisionReason = "Superseded by " + repair.id;
        }
        build.contributions.add(repair);
        auditContribution(data, build, repair, "ADJUST_TOTAL", actor, actorName, repair.decisionReason, before, nowMillis);
        return Decision.allow();
    }

    private static Decision canReview(KOMEWorldData data, KOMEPlayerBuild build, UUID actor, boolean admin) {
        try { requireActive(data, build); }
        catch (IllegalArgumentException invalid) { return Decision.deny(invalid.getMessage()); }
        if (!admin && !isManager(build, actor)) return Decision.deny("Only the current Build manager may review submissions.");
        return Decision.allow();
    }

    private static void requireWritable(KOMEWorldData data) {
        if (data == null || data.isWriteBlocked()) throw new IllegalArgumentException("Build world data is unavailable or write-blocked.");
    }

    private static void requireActive(KOMEWorldData data, KOMEPlayerBuild build) {
        requireWritable(data);
        if (build == null || !build.active || build.type == null) throw new IllegalArgumentException("The Build is not active.");
        build.validateContributions();
    }

    private static void auditContribution(KOMEWorldData data, KOMEPlayerBuild build, KOMEBuildContribution contribution,
            String action, UUID actor, String name, String reason, long before, long nowMillis) {
        audit(data, build, action, actor, name, reason, "contribution=" + contribution.id
                + ";status=" + contribution.status + ";hours=" + KOMEBuildTime.formatHours(contribution.centiHours)
                + ";approvedCentiHoursDelta=" + (build.approvedCentiHours() - before), nowMillis);
    }

    private static void audit(KOMEWorldData data, KOMEPlayerBuild build, String action, UUID actor,
            String actorName, String reason, String details, long nowMillis) {
        String auditReason = safe(reason);
        if (auditReason.length() > 500) auditReason = auditReason.substring(0, 500);
        String values = details + ";approvedHours=" + KOMEBuildTime.formatHours(build.approvedCentiHours())
                + ";type=" + build.type.key + ";populationFaction=" + build.populationFaction + ";tile=" + build.tileId;
        build.appendAudit(Math.max(0L, nowMillis) + "|" + action + "|" + (actor == null ? "" : actor.toString())
                + "|" + safe(actorName) + "|" + auditReason + "|" + values);
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        KOMEAuditService.record(data, nowMillis, "BUILD", action, actor == null ? safe(actorName) : actor.toString(),
                build.id, auditReason.isEmpty() ? action : auditReason, values);
        data.markDirty();
    }

    public static Decision deleteBuild(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorName, boolean admin, String reason, long nowMillis) {
        Decision allowed = canDeleteBuild(data, build, actor, admin);
        if (!allowed.allowed) return allowed;
        String safeReason = safe(reason);
        softDelete(data, build, actor, actorName, safeReason.length() == 0 ? "Deleted by manager" : safeReason, nowMillis);
        return Decision.allow();
    }

    public static Decision canDeleteBuild(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            boolean admin) {
        if (build == null || !build.active) return Decision.deny("The Build is already inactive.");
        if (!admin && !isManager(build, actor)) return Decision.deny("Only the current manager or an administrator may delete this Build.");
        return Decision.allow();
    }

    public static Decision destroyEnemyBuild(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorName, String actorFaction, boolean admin, long nowMillis) {
        Decision allowed = canDestroyEnemyBuild(data, build, actor, actorFaction, admin);
        if (!allowed.allowed) return allowed;
        softDelete(data, build, actor, actorName, "Destroyed by enemy homeland controller", nowMillis);
        return Decision.allow();
    }

    public static Decision canDestroyEnemyBuild(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorFaction, boolean admin) {
        if (data == null || build == null || !build.active) return Decision.deny("The Build is not active.");
        KOMEConquestTile tile = data.conquestTiles.get(build.tileId);
        String controller = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        String defaultOwner = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction);
        String acting = KOMEAlliance.normalizeFactionKey(actorFaction);
        if (!admin && (!acting.equals(controller) || !KOMERulerAuthorization.canActAsRuler(data, controller, actor))) {
            return Decision.deny("Only the current controller's king or an administrator may destroy an enemy Build.");
        }
        if (!controller.equals(defaultOwner)) return Decision.deny("Enemy destruction is allowed only in the controller's original homeland.");
        if (!isHostile(data, controller, build.populationFaction)) {
            return Decision.deny("Friendly, Allied, and Neutral Builds cannot be destroyed.");
        }
        return Decision.allow();
    }

    public static boolean reconcileManager(KOMEWorldData data, KOMEPlayerBuild build) {
        if (data == null || build == null || !build.active) return false;
        requireActive(data, build);
        if (build.managerUuid != null
                && build.populationFaction.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(build.managerUuid)))) {
            return false;
        }
        UUID king = data.getFactionKingId(build.populationFaction);
        String kingName = data.getFactionKingName(build.populationFaction);
        if (king == null) {
            if (build.managerUuid == null && build.managerName.length() == 0) return false;
            build.managerUuid = null;
            build.managerName = "";
        } else {
            if (king.equals(build.managerUuid) && safe(kingName).equals(build.managerName)) return false;
            build.managerUuid = king;
            build.managerName = safe(kingName);
        }
        audit(data, build, "MANAGER_RECONCILE", king, kingName, "Manager reconciled with current faction ruler", "", System.currentTimeMillis());
        return true;
    }

    public static BigInteger approvedCentiHoursForPartner(KOMEWorldData data, String contributorFaction,
            String populationOwnerFaction) {
        BigInteger result = BigInteger.ZERO;
        String contributor = KOMEAlliance.normalizeFactionKey(contributorFaction);
        String owner = KOMEAlliance.normalizeFactionKey(populationOwnerFaction);
        if (data == null || contributor.length() == 0 || owner.length() == 0) return BigInteger.ZERO;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build == null || !build.active || !owner.equals(KOMEAlliance.normalizeFactionKey(build.populationFaction))) continue;
            for (KOMEBuildContribution contribution : build.contributions) {
                if (contribution != null && contribution.isApproved()
                        && contributor.equals(KOMEAlliance.normalizeFactionKey(contribution.contributorFaction))) {
                    result = result.add(BigInteger.valueOf(contribution.totalCentiHours()));
                }
            }
        }
        return result;
    }

    public static List<KOMEPlayerBuild> buildsInTile(KOMEWorldData data, String tileId, boolean includeDeleted) {
        List<KOMEPlayerBuild> result = new ArrayList<KOMEPlayerBuild>();
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (data == null) return result;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build != null && tile.equals(build.tileId) && (includeDeleted || build.active)) result.add(build);
        }
        Collections.sort(result, new Comparator<KOMEPlayerBuild>() {
            @Override
            public int compare(KOMEPlayerBuild left, KOMEPlayerBuild right) {
                int byName = KOMEPlayerBuild.sanitizeName(left.displayName).compareToIgnoreCase(KOMEPlayerBuild.sanitizeName(right.displayName));
                return byName != 0 ? byName : safe(left.id).compareTo(safe(right.id));
            }
        });
        return result;
    }

    /** Authoritative KOM-7 input: active NORMAL Builds and their exact rate inputs. */
    public static List<KOMEPlayerBuild> activeNormalBuilds(KOMEWorldData data) {
        return activeBuildsOfType(data, KOMEBuildType.NORMAL);
    }

    /** Authoritative downstream siege input: active DEFENSIVE Builds and approved defensive hours. */
    public static List<KOMEPlayerBuild> activeDefensiveBuilds(KOMEWorldData data) {
        return activeBuildsOfType(data, KOMEBuildType.DEFENSIVE);
    }

    private static List<KOMEPlayerBuild> activeBuildsOfType(KOMEWorldData data, KOMEBuildType type) {
        List<KOMEPlayerBuild> result = new ArrayList<KOMEPlayerBuild>();
        if (data == null || type == null) return result;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build != null && build.active && build.type == type) { build.validateContributions(); result.add(build); }
        }
        Collections.sort(result, new Comparator<KOMEPlayerBuild>() {
            @Override public int compare(KOMEPlayerBuild left, KOMEPlayerBuild right) {
                return safe(left.id).compareTo(safe(right.id));
            }
        });
        return result;
    }

    public static boolean isManager(KOMEPlayerBuild build, UUID actor) {
        return build != null && build.active && actor != null && actor.equals(build.managerUuid);
    }

    public static Decision rename(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            boolean admin, String name, long nowMillis) {
        Decision permission = canReview(data, build, actor, admin);
        if (!permission.allowed) return permission;
        build.displayName = KOMEPlayerBuild.sanitizeName(name);
        build.markerLabel = build.displayName;
        audit(data, build, "RENAME", actor, "", "Build renamed", "name=" + build.displayName, nowMillis);
        return Decision.allow();
    }

    public static Decision reassignManager(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorName, boolean admin, UUID manager, String managerName, long nowMillis) {
        if (!admin) return Decision.deny("Only administrators may reassign Build records.");
        // Existing administrative metadata repair also permits inactive records.
        try {
            requireWritable(data);
            if (build == null) return Decision.deny("Unknown Build record.");
            build.validateContributions();
        } catch (IllegalArgumentException invalid) { return Decision.deny(invalid.getMessage()); }
        if (manager == null) return Decision.deny("A new manager is required.");
        if (manager.equals(build.managerUuid) && safe(managerName).equals(build.managerName)) return Decision.allow();
        build.managerUuid = manager;
        build.managerName = safe(managerName);
        audit(data, build, "MANAGER_REASSIGN", actor, actorName, "Administrative manager reassignment",
                "manager=" + manager + ";managerName=" + build.managerName, nowMillis);
        return Decision.allow();
    }

    public static KOMETileResolution tileAtWorldCoordinates(int dimension, double worldX, double worldZ) {
        return KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, worldX, worldZ);
    }

    public static Decision validateCoordinates(String expectedTile, int dimension, double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            return Decision.deny("Build coordinates must be finite.");
        }
        KOMETileResolution resolved = tileAtWorldCoordinates(dimension, x, z);
        if (resolved.status != KOMETileResolution.Status.RESOLVED) {
            return Decision.deny("Build coordinate rejected: " + resolved);
        }
        return KOMEConquestTile.normalizeId(expectedTile).equals(resolved.tileId)
            ? Decision.allow() : Decision.deny("The selected coordinates are not inside the confirmed conquest tile.");
    }

    public static boolean isFriendlyOrAllied(KOMEWorldData data, String first, String second) {
        String a = KOMEAlliance.normalizeFactionKey(first);
        String b = KOMEAlliance.normalizeFactionKey(second);
        if (a.length() == 0 || b.length() == 0) return false;
        if (a.equals(b)) return true;

        return data != null && KOMEDiplomacyService.relationAtLeast(
            data, a, b, KOMEDiplomacyRelation.FRIENDS);
    }

    public static boolean isHostile(KOMEWorldData data, String first, String second) {
        if (data != null && KOMEWarService.findActiveOpposition(data, first, second) != null) {
            return true;
        }

        LOTRFactionRelations.Relation relation =
            KOMEAllianceAuthority.getCurrentRelation(first, second);

        return relation == LOTRFactionRelations.Relation.ENEMY
            || relation == LOTRFactionRelations.Relation.MORTAL_ENEMY;
    }

    private static void softDelete(KOMEWorldData data, KOMEPlayerBuild build, UUID actor,
            String actorName, String reason, long nowMillis) {
        requireActive(data, build);
        long removedHours = build.approvedCentiHours();
        for (KOMEBuildContribution contribution : build.contributions) {
            if (contribution == null || contribution.isRemoved()
                    || KOMEBuildContribution.REJECTED.equals(contribution.status)) continue;
            contribution.status = contribution.isPending()
                ? KOMEBuildContribution.REJECTED : KOMEBuildContribution.REMOVED;
            contribution.decidedAtMillis = Math.max(0L, nowMillis);
            contribution.decidedByUuid = actor;
            contribution.decidedByName = safe(actorName);
            contribution.decisionReason = safe(reason);
        }
        build.active = false;
        build.markerVisible = false;
        build.deletedAtMillis = Math.max(0L, nowMillis);
        build.deletedByUuid = actor;
        build.deletedByName = safe(actorName);
        build.deletionReason = safe(reason);
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        audit(data, build, "DELETE", actor, actorName, reason, "approvedCentiHoursRemoved=" + removedHours, nowMillis);
    }


    public static final class Decision {
        public final boolean allowed;
        public final String reason;

        private Decision(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = safe(reason);
        }

        public static Decision allow() {
            return new Decision(true, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
