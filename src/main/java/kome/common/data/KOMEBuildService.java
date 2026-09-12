package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import lotr.common.world.genlayer.LOTRGenLayerWorld;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Authoritative Build mutation service. GUIs and admin commands both use this boundary.
 */
public final class KOMEBuildService {
    private KOMEBuildService() {
    }

    /** Canonical Slice-1 creation boundary. Legacy callers remain at the packet edge. */
    public static KOMEPlayerBuild create(KOMEWorldData data, String name, String tileId, int dimension,
            double x, double y, double z, UUID builder, String builderName, String builderFaction,
            String populationFaction, KOMEBuildType type, int halfHours, long nowMillis) {
        if (type == null) throw new IllegalArgumentException("Build type is required.");
        if (halfHours <= 0) throw new IllegalArgumentException("Submit at least one half-hour.");
        Decision placement = canPlace(data, builder, builderFaction, tileId, populationFaction);
        if (!placement.allowed) throw new IllegalArgumentException(placement.reason);
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = data.nextBuildId();
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
        data.builds.put(build.id, build);
        addSubmission(data, build, builder, builderName, builderFaction, halfHours, true, nowMillis);
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
        if (data == null || tile == null || !tile.isClaimed()) return Decision.deny("The selected conquest tile is not claimed.");
        String controller = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
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
        if (player.length() == 0 || tile == null || !tile.isClaimed()) return result;
        if (!canPlace(data, player, tile.id, player).allowed) return result;
        result.add(player);
        String controller = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
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
            UUID contributor, String contributorName, String contributorFaction, int halfHours,
            boolean contributorIsManager, long nowMillis) {
        if (data == null || build == null || !build.active || build.type == null) {
            throw new IllegalArgumentException("The Build is not active.");
        }
        if (halfHours <= 0) throw new IllegalArgumentException("Submit at least one half-hour.");
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = data.nextBuildContributionId(build);
        contribution.contributorUuid = contributor;
        contribution.contributorName = safe(contributorName);
        contribution.contributorFaction = KOMEAlliance.normalizeFactionKey(contributorFaction);
        contribution.halfHours = halfHours;
        contribution.submittedAtMillis = Math.max(0L, nowMillis);
        if (contributorIsManager && contributor != null && contributor.equals(build.managerUuid)) {
            contribution.status = KOMEBuildContribution.APPROVED;
            contribution.decidedAtMillis = Math.max(0L, nowMillis);
            contribution.decidedByUuid = contributor;
            contribution.decidedByName = safe(contributorName);
            contribution.decisionReason = "Manager contribution approved immediately";
        }
        build.contributions.add(contribution);
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        data.markDirty();
        return contribution;
    }

    public static Decision decideSubmission(KOMEWorldData data, KOMEPlayerBuild build, String contributionId,
            UUID manager, String managerName, boolean approve, String reason, long nowMillis) {
        return decideSubmission(data, build, contributionId, manager, managerName, false, approve, reason, nowMillis);
    }

    public static Decision decideSubmission(KOMEWorldData data, KOMEPlayerBuild build, String contributionId,
            UUID manager, String managerName, boolean admin, boolean approve, String reason, long nowMillis) {
        if (!admin && !isManager(build, manager)) return Decision.deny("Only the current Build manager may review submissions.");
        KOMEBuildContribution contribution = build == null ? null : build.getContribution(contributionId);
        if (contribution == null || !contribution.isPending()) return Decision.deny("That contribution is not pending.");
        contribution.status = approve ? KOMEBuildContribution.APPROVED : KOMEBuildContribution.REJECTED;
        contribution.decidedAtMillis = Math.max(0L, nowMillis);
        contribution.decidedByUuid = manager;
        contribution.decidedByName = safe(managerName);
        contribution.decisionReason = safe(reason);
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        data.markDirty();
        return Decision.allow();
    }

    public static Decision removeApprovedContribution(KOMEWorldData data, KOMEPlayerBuild build,
            String contributionId, UUID manager, String managerName, String reason, long nowMillis) {
        return removeApprovedContribution(data, build, contributionId, manager, managerName, false, reason, nowMillis);
    }

    public static Decision removeApprovedContribution(KOMEWorldData data, KOMEPlayerBuild build,
            String contributionId, UUID manager, String managerName, boolean admin, String reason, long nowMillis) {
        if (!admin && !isManager(build, manager)) return Decision.deny("Only the current Build manager may remove approved hours.");
        KOMEBuildContribution contribution = build == null ? null : build.getContribution(contributionId);
        if (contribution == null || !contribution.isApproved()) return Decision.deny("That contribution is not active and approved.");
        contribution.status = KOMEBuildContribution.REMOVED;
        contribution.decidedAtMillis = Math.max(0L, nowMillis);
        contribution.decidedByUuid = manager;
        contribution.decidedByName = safe(managerName);
        contribution.decisionReason = safe(reason);
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        data.markDirty();
        return Decision.allow();
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
        String controller = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
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
        build.updatedAtMillis = System.currentTimeMillis();
        data.markDirty();
        return true;
    }

    public static int approvedHalfHoursForPartner(KOMEWorldData data, String contributorFaction,
            String populationOwnerFaction) {
        int result = 0;
        String contributor = KOMEAlliance.normalizeFactionKey(contributorFaction);
        String owner = KOMEAlliance.normalizeFactionKey(populationOwnerFaction);
        if (data == null || contributor.length() == 0 || owner.length() == 0) return 0;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build == null || !build.active || !owner.equals(KOMEAlliance.normalizeFactionKey(build.populationFaction))) continue;
            for (KOMEBuildContribution contribution : build.contributions) {
                if (contribution != null && contribution.isApproved()
                        && contributor.equals(KOMEAlliance.normalizeFactionKey(contribution.contributorFaction))) {
                    result += contribution.totalHalfHours();
                }
            }
        }
        return Math.max(0, result);
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
            if (build != null && build.active && build.type == type) result.add(build);
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
        if (build == null || !build.active) return Decision.deny("The Build is not active.");
        if (!admin && !isManager(build, actor)) return Decision.deny("Only the current manager may rename this Build.");
        build.displayName = KOMEPlayerBuild.sanitizeName(name);
        build.markerLabel = build.displayName;
        build.updatedAtMillis = Math.max(build.updatedAtMillis, nowMillis);
        data.markDirty();
        return Decision.allow();
    }

    public static String tileAtWorldCoordinates(double worldX, double worldZ) {
        double mapX = worldX / LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originX;
        double mapZ = worldZ / LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originZ;
        return KOMEConquestTile.normalizeId(KOMEConquestTileDefaults.getTileIdAtMapPosition(mapX, mapZ));
    }

    public static Decision validateCoordinates(String expectedTile, double x, double y, double z) {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)
                || Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z)) {
            return Decision.deny("Build coordinates must be finite.");
        }
        String actualTile = tileAtWorldCoordinates(x, z);
        return KOMEConquestTile.normalizeId(expectedTile).equals(actualTile)
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
        data.markDirty();
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
