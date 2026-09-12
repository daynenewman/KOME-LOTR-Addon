package com.enovak.lotrmoremobs.spawning;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.handler.MumakilConquestUnitRollEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilDriverControlEventHandler;
import com.enovak.lotrmoremobs.handler.MumakilHowdahArcherEventHandler;
import com.enovak.lotrmoremobs.util.MumakilServerPerformanceDiagnostics;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntitySouthronChampion;
import net.minecraft.entity.Entity;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * One server-side construction path for player-hired and autonomous Mumak war
 * formations. Autonomous construction is transactional: any failed member
 * spawn removes every member already created by that attempt.
 */
public final class MumakilWarFormationFactory {
    public static final String FORMATION_REPLACEMENT_MEMBER_KEY =
            "lotrmoremobs_mumakHomeFormationMember";
    /**
     * Save-key compatibility alias. The stored key predates conquest and
     * invasion replacement but now identifies members from all three origins.
     */
    @Deprecated
    public static final String HOME_FORMATION_MEMBER_KEY =
            FORMATION_REPLACEMENT_MEMBER_KEY;
    public static final int FORMATION_ARCHER_COUNT =
            LOTREntityMumakilHowdahArcher.getHowdahArcherSlotCount();

    private static final int SADDLE_SLOT = 0;
    private static final int HOWDAH_SLOT = 1;
    private static final int CLEARANCE_RADIUS = 4;
    private static final int CLEARANCE_HEIGHT = 16;
    private static final int GROUND_SAMPLE_RADIUS = 3;
    private static final int[][] REPLACEMENT_POSITION_SEARCH_OFFSETS =
            new int[][] {
                    {5, 0},
                    {-5, 0},
                    {0, 5},
                    {0, -5},
                    {5, 5},
                    {-5, 5},
                    {5, -5},
                    {-5, -5},
                    {9, 0},
                    {0, 9}
            };
    private static final int PLAYER_HIRE_MAX_SEARCH_RADIUS = 30;
    private static final int PLAYER_HIRE_VERTICAL_SEARCH = 30;
    private static final int PLAYER_HIRE_SEARCH_RING_STEP = 5;

    public enum FormationPlacementResult {
        VALID,
        UNLOADED_AREA,
        UNSAFE_GROUND_OR_LIQUID,
        SOLID_BLOCK_CLEARANCE,
        NEARBY_ENTITY_CLEARANCE
    }

    public static final class FormationPlacementSearchResult {
        private double x;
        private double y;
        private double z;
        private boolean found;
        private int positionsTested;
        private int unloadedRejected;
        private int unsafeGroundRejected;
        private int solidBlockRejected;
        private int nearbyEntityRejected;

        public boolean isFound() {
            return this.found;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        public int getPositionsTested() {
            return this.positionsTested;
        }

        public int getUnloadedRejected() {
            return this.unloadedRejected;
        }

        public int getUnsafeGroundRejected() {
            return this.unsafeGroundRejected;
        }

        public int getSolidBlockRejected() {
            return this.solidBlockRejected;
        }

        public int getNearbyEntityRejected() {
            return this.nearbyEntityRejected;
        }

        private void record(
                FormationPlacementResult result,
                double candidateX,
                double candidateY,
                double candidateZ
        ) {
            ++this.positionsTested;
            if (result == FormationPlacementResult.VALID) {
                this.found = true;
                this.x = candidateX;
                this.y = candidateY;
                this.z = candidateZ;
            } else if (result
                    == FormationPlacementResult.UNLOADED_AREA) {
                ++this.unloadedRejected;
            } else if (result
                    == FormationPlacementResult
                    .UNSAFE_GROUND_OR_LIQUID) {
                ++this.unsafeGroundRejected;
            } else if (result
                    == FormationPlacementResult
                    .SOLID_BLOCK_CLEARANCE) {
                ++this.solidBlockRejected;
            } else if (result
                    == FormationPlacementResult
                    .NEARBY_ENTITY_CLEARANCE) {
                ++this.nearbyEntityRejected;
            }
        }
    }

    private MumakilWarFormationFactory() {
    }

    public static boolean initializeMount(
            LOTREntityMumakil mumakil,
            MumakilFormationOrigin origin
    ) {
        if (mumakil == null || origin == null || origin == MumakilFormationOrigin.NONE) {
            return false;
        }

        mumakil.setFormationOrigin(origin);
        mumakil.setBelongsToNPC(true);
        mumakil.setHiredWarMumakil(true);
        mumakil.setMountable(true);
        mumakil.setGrowingAge(0);
        mumakil.saddleMountForWorldGen();

        boolean saddleEquipped = setInventoryStack(
                mumakil,
                SADDLE_SLOT,
                new ItemStack(Items.saddle)
        );
        boolean howdahEquipped = setInventoryStack(
                mumakil,
                HOWDAH_SLOT,
                new ItemStack(Main.mumakilHowdah)
        );
        mumakil.setMumakilHowdahEquipped(howdahEquipped);

        if (mumakil.worldObj != null && !mumakil.worldObj.isRemote) {
            MumakilHowdahArcherEventHandler.markHiredHowdahArcherCarrier(mumakil);
        }

        return saddleEquipped && howdahEquipped;
    }

    public static boolean canNaturalFormationSpawnAt(
            World world,
            Entity excludedEntity,
            double x,
            double y,
            double z
    ) {
        return validateNaturalFormationSpawnAt(
                world,
                excludedEntity,
                x,
                y,
                z
        ) == FormationPlacementResult.VALID;
    }

    public static FormationPlacementResult
    validateNaturalFormationSpawnAt(
            World world,
            Entity excludedEntity,
            double x,
            double y,
            double z
    ) {
        return validateFormationSpawnAt(
                world,
                excludedEntity,
                x,
                y,
                z,
                false
        );
    }

    private static FormationPlacementResult validateFormationSpawnAt(
            World world,
            Entity excludedEntity,
            double x,
            double y,
            double z,
            boolean ignoreEntityClearance
    ) {
        if (world == null || world.isRemote) {
            return FormationPlacementResult.UNLOADED_AREA;
        }

        long blockStart =
                MumakilServerPerformanceDiagnostics.startTimer(world);
        int baseX = MathHelper.floor_double(x);
        int baseY = MathHelper.floor_double(y);
        int baseZ = MathHelper.floor_double(z);
        if (baseY < 1 || baseY + CLEARANCE_HEIGHT >= world.getActualHeight()) {
            MumakilServerPerformanceDiagnostics.recordBlockClearance(
                    world,
                    System.nanoTime() - blockStart
            );
            return FormationPlacementResult
                    .UNSAFE_GROUND_OR_LIQUID;
        }

        int[][] groundOffsets = new int[][] {
                {0, 0},
                {-GROUND_SAMPLE_RADIUS, 0},
                {GROUND_SAMPLE_RADIUS, 0},
                {0, -GROUND_SAMPLE_RADIUS},
                {0, GROUND_SAMPLE_RADIUS}
        };
        for (int i = 0; i < groundOffsets.length; ++i) {
            int groundX = baseX + groundOffsets[i][0];
            int groundZ = baseZ + groundOffsets[i][1];
            if (!world.blockExists(groundX, baseY, groundZ)) {
                MumakilServerPerformanceDiagnostics
                        .recordBlockClearance(
                                world,
                                System.nanoTime() - blockStart
                        );
                return FormationPlacementResult.UNLOADED_AREA;
            }
            if (!World.doesBlockHaveSolidTopSurface(
                    world,
                    groundX,
                    baseY - 1,
                    groundZ
            )) {
                MumakilServerPerformanceDiagnostics
                        .recordBlockClearance(
                                world,
                                System.nanoTime() - blockStart
                        );
                return FormationPlacementResult
                        .UNSAFE_GROUND_OR_LIQUID;
            }
        }

        /*
         * Validate the actual adult collision volume rather than the former
         * 11x11x29 solid-block sweep. The old sweep was almost twice the
         * Mumak's height and rejected harmless terrain well outside its body.
         */
        if (!world.blockExists(
                baseX - CLEARANCE_RADIUS,
                baseY + CLEARANCE_HEIGHT,
                baseZ - CLEARANCE_RADIUS
        ) || !world.blockExists(
                baseX + CLEARANCE_RADIUS,
                baseY + CLEARANCE_HEIGHT,
                baseZ + CLEARANCE_RADIUS
        )) {
            MumakilServerPerformanceDiagnostics.recordBlockClearance(
                    world,
                    System.nanoTime() - blockStart
            );
            return FormationPlacementResult.UNLOADED_AREA;
        }

        AxisAlignedBB mumakSpace = AxisAlignedBB.getBoundingBox(
                x - 3.5D,
                y,
                z - 3.5D,
                x + 3.5D,
                y + 15.5D,
                z + 3.5D
        );
        if (world.isAnyLiquid(mumakSpace)) {
            MumakilServerPerformanceDiagnostics.recordBlockClearance(
                    world,
                    System.nanoTime() - blockStart
            );
            return FormationPlacementResult
                    .UNSAFE_GROUND_OR_LIQUID;
        }
        if (!world.func_147461_a(mumakSpace).isEmpty()) {
            MumakilServerPerformanceDiagnostics.recordBlockClearance(
                    world,
                    System.nanoTime() - blockStart
            );
            return FormationPlacementResult
                    .SOLID_BLOCK_CLEARANCE;
        }
        MumakilServerPerformanceDiagnostics.recordBlockClearance(
                world,
                System.nanoTime() - blockStart
        );

        /*
         * A creative formation egg is an explicit player placement action.
         * Existing entities may be pushed normally after the transaction, but
         * every terrain, liquid, world-height, chunk, and solid-block check
         * above remains mandatory. Natural/replacement callers never request
         * this policy and retain the strict entity-clearance scan below.
         */
        if (ignoreEntityClearance) {
            return FormationPlacementResult.VALID;
        }

        long entityStart =
                MumakilServerPerformanceDiagnostics.startTimer(world);
        boolean entitiesClear =
                world.getEntitiesWithinAABBExcludingEntity(
                excludedEntity,
                mumakSpace
        ).isEmpty();
        MumakilServerPerformanceDiagnostics.recordEntityClearance(
                world,
                System.nanoTime() - entityStart
        );
        return entitiesClear
                ? FormationPlacementResult.VALID
                : FormationPlacementResult
                .NEARBY_ENTITY_CLEARANCE;
    }

    public static FormationPlacementSearchResult
    findReplacementFormationPlacement(
            World world,
            LOTREntityNPC triggeringNpc
    ) {
        FormationPlacementSearchResult search =
                new FormationPlacementSearchResult();
        if (world == null
                || world.isRemote
                || triggeringNpc == null
                || triggeringNpc.worldObj != world) {
            search.record(
                    FormationPlacementResult.UNLOADED_AREA,
                    0.0D,
                    0.0D,
                    0.0D
            );
            return search;
        }

        long searchStart =
                MumakilServerPerformanceDiagnostics.startTimer(world);
        try {
            FormationPlacementResult exact =
                    validateNaturalFormationSpawnAt(
                            world,
                            triggeringNpc,
                            triggeringNpc.posX,
                            triggeringNpc.posY,
                            triggeringNpc.posZ
                    );
            search.record(
                    exact,
                    triggeringNpc.posX,
                    triggeringNpc.posY,
                    triggeringNpc.posZ
            );
            if (search.isFound()) {
                return search;
            }

            int baseX = MathHelper.floor_double(triggeringNpc.posX);
            int baseZ = MathHelper.floor_double(triggeringNpc.posZ);
            int seed = triggeringNpc.getPersistentID().hashCode()
                    & Integer.MAX_VALUE;
            for (int attempt = 0;
                 attempt < REPLACEMENT_POSITION_SEARCH_OFFSETS.length;
                 ++attempt) {
                int index = (attempt + seed)
                        % REPLACEMENT_POSITION_SEARCH_OFFSETS.length;
                int candidateBlockX = baseX
                        + REPLACEMENT_POSITION_SEARCH_OFFSETS[index][0];
                int candidateBlockZ = baseZ
                        + REPLACEMENT_POSITION_SEARCH_OFFSETS[index][1];
                if (!world.blockExists(
                        candidateBlockX,
                        MathHelper.floor_double(triggeringNpc.posY),
                        candidateBlockZ
                )) {
                    search.record(
                            FormationPlacementResult.UNLOADED_AREA,
                            candidateBlockX + 0.5D,
                            triggeringNpc.posY,
                            candidateBlockZ + 0.5D
                    );
                    continue;
                }

                double candidateX = candidateBlockX + 0.5D;
                double candidateZ = candidateBlockZ + 0.5D;
                double candidateY = world.getTopSolidOrLiquidBlock(
                        candidateBlockX,
                        candidateBlockZ
                );
                FormationPlacementResult result =
                        validateNaturalFormationSpawnAt(
                                world,
                                triggeringNpc,
                                candidateX,
                                candidateY,
                                candidateZ
                        );
                search.record(
                        result,
                        candidateX,
                        candidateY,
                        candidateZ
                );
                if (search.isFound()) {
                    return search;
                }
            }
            return search;
        } finally {
            MumakilServerPerformanceDiagnostics
                    .recordNearbyPositionSearch(
                            world,
                            System.nanoTime() - searchStart
                    );
        }
    }

    /**
     * Finds a clear location for a player-hired Mumak formation within
     * 30 blocks horizontally and vertically of the hiring NPC.
     *
     * The hiring player remains excluded from the entity-collision test because
     * native LOTR initially creates the hired mount/driver at the player's
     * position before this validated placement is applied.
     */
    public static FormationPlacementSearchResult
    findPlayerHiredFormationPlacement(
            World world,
            EntityPlayer player,
            LOTREntityNPC hiringNpc,
            LOTREntityNPC driver,
            LOTREntityMumakil mumakil,
            float placementYaw
    ) {
        FormationPlacementSearchResult search =
                new FormationPlacementSearchResult();

        if (world == null
                || world.isRemote
                || player == null
                || player.worldObj != world
                || hiringNpc == null
                || hiringNpc.worldObj != world
                || hiringNpc.isDead
                || !hiringNpc.isEntityAlive()
                || driver == null
                || mumakil == null) {
            search.record(
                    FormationPlacementResult.UNLOADED_AREA,
                    0.0D,
                    0.0D,
                    0.0D
            );
            return search;
        }

        /*
         * The hiring NPC, rather than the player, is the center of the
         * permitted Mumak placement area.
         */
        int baseX = MathHelper.floor_double(hiringNpc.posX);
        int baseY = MathHelper.floor_double(hiringNpc.posY);
        int baseZ = MathHelper.floor_double(hiringNpc.posZ);

        double fallbackX = 0.0D;
        double fallbackY = 0.0D;
        double fallbackZ = 0.0D;
        boolean hasNonExposedFallback = false;

        /*
         * Search nearest rings first.
         *
         * Seven rings:
         * 0, 5, 10, 15, 20, 25, 30 blocks.
         *
         * This retains 49 horizontal candidates total:
         * one center candidate plus eight candidates on each outer ring.
         */
        for (int radius = 0;
             radius <= PLAYER_HIRE_MAX_SEARCH_RADIUS;
             radius += PLAYER_HIRE_SEARCH_RING_STEP) {

            int angularCandidates = radius == 0 ? 1 : 8;

            for (int angle = 0; angle < angularCandidates; ++angle) {
                double angleRadians = radius == 0
                        ? 0.0D
                        : angle * (Math.PI / 4.0D);

                int candidateX = baseX + MathHelper.floor_double(
                        radius * Math.cos(angleRadians) + 0.5D
                );

                int candidateZ = baseZ + MathHelper.floor_double(
                        radius * Math.sin(angleRadians) + 0.5D
                );

                /*
                 * Start at the highest useful location in this column, but do
                 * not allow placement more than 30 blocks above the hiring NPC.
                 *
                 * We then search downward as far as 30 blocks below the NPC.
                 * This allows hiring from hills, bridges, treehouses, cliffs,
                 * valleys, etc. without making the search player-centered.
                 */
                int surfaceY = world.getTopSolidOrLiquidBlock(
                        candidateX,
                        candidateZ
                );

                int highestY = Math.min(
                        surfaceY,
                        baseY + PLAYER_HIRE_VERTICAL_SEARCH
                );

                int lowestY = Math.max(
                        1,
                        baseY - PLAYER_HIRE_VERTICAL_SEARCH
                );

                if (highestY < lowestY) {
                    continue;
                }

                for (int candidateY = highestY;
                     candidateY >= lowestY;
                     --candidateY) {

                    double x = candidateX + 0.5D;
                    double y = candidateY;
                    double z = candidateZ + 0.5D;

                    /*
                     * Keep the existing Mumak-sized validator unchanged.
                     *
                     * It already requires:
                     * - solid supporting ground;
                     * - full adult Mumak block clearance;
                     * - sufficient headroom;
                     * - no liquid;
                     * - loaded terrain;
                     * - entity clearance.
                     */
                    FormationPlacementResult result =
                            validatePlayerHiredCandidate(
                                    world,
                                    player,
                                    driver,
                                    mumakil,
                                    x,
                                    y,
                                    z,
                                    placementYaw
                            );

                    search.record(result, x, y, z);

                    if (result != FormationPlacementResult.VALID) {
                        continue;
                    }

                    /*
                     * Continue preferring ordinary exposed terrain.
                     */
                    if (world.canBlockSeeTheSky(
                            candidateX,
                            candidateY,
                            candidateZ
                    )) {
                        return search;
                    }

                    /*
                     * Preserve the existing validated non-exposed fallback.
                     */
                    if (!hasNonExposedFallback) {
                        fallbackX = x;
                        fallbackY = y;
                        fallbackZ = z;
                        hasNonExposedFallback = true;
                    }
                }
            }
        }

        if (hasNonExposedFallback) {
            search.record(
                    FormationPlacementResult.VALID,
                    fallbackX,
                    fallbackY,
                    fallbackZ
            );
        }

        return search;
    }

    private static FormationPlacementResult validatePlayerHiredCandidate(
            World world,
            Entity excludedEntity,
            LOTREntityNPC driver,
            LOTREntityMumakil mumakil,
            double x,
            double y,
            double z,
            float placementYaw
    ) {
        FormationPlacementResult body = validateNaturalFormationSpawnAt(
                world,
                excludedEntity,
                x,
                y,
                z
        );
        if (body != FormationPlacementResult.VALID) {
            return body;
        }

        mumakil.setLocationAndAngles(x, y, z, placementYaw, 0.0F);
        mumakil.rotationYawHead = placementYaw;
        mumakil.renderYawOffset = placementYaw;
        driver.mountEntity(mumakil);
        mumakil.positionRiderAtMumakilAnchor(driver);

        AxisAlignedBB riderSpace = driver.boundingBox;
        if (riderSpace == null
                || !world.func_147461_a(riderSpace).isEmpty()
                || world.isAnyLiquid(riderSpace)) {
            return FormationPlacementResult.SOLID_BLOCK_CLEARANCE;
        }
        return FormationPlacementResult.VALID;
    }

    /**
     * All replacement origins consume one ordinary NPC already admitted by
     * LOTR. The driver replaces that NPC for normal cap purposes; the fixed
     * attached archers are cap-exempt only while their attachment validates.
     *
     * LOTR invasion spawners may deliberately flag members persistent. That
     * flag is invasion lifecycle state, so invasion admission validates the
     * active matching spawner rather than rejecting persistence.
     */
    public static boolean hasReplacementFormationSpawnCapacity(
            World world,
            LOTREntityNPC replacedUnit,
            MumakilFormationOrigin origin
    ) {
        if (world == null
                || world.isRemote
                || replacedUnit == null
                || replacedUnit.worldObj != world
                || replacedUnit.isDead
                || !replacedUnit.isEntityAlive()
                || !world.loadedEntityList.contains(replacedUnit)) {
            return false;
        }
        if (origin == MumakilFormationOrigin.INVASION_NEAR_HARAD) {
            return replacedUnit.isInvasionSpawned()
                    && replacedUnit.getInvasionID() != null;
        }
        return (origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD
                || origin
                == MumakilFormationOrigin.CONQUEST_NEAR_HARAD)
                && !replacedUnit.isNPCPersistent
                && replacedUnit.getSpawnCountValue() > 0;
    }

    /**
     * The sole ordinary-NPC replacement transaction used by home, conquest,
     * and invasion candidates.
     */
    public static boolean createReplacementFormationFromUnit(
            LOTREntityNPC replacedUnit,
            FormationPlacementSearchResult placement,
            MumakilFormationOrigin origin,
            UUID invasionId
    ) {
        if (replacedUnit == null
                || replacedUnit.worldObj == null
                || replacedUnit.worldObj.isRemote
                || replacedUnit.isDead
                || !replacedUnit.isEntityAlive()
                || origin == null
                || replacedUnit
                instanceof LOTREntityMumakilHowdahArcher
                || replacedUnit.getEntityData().getBoolean(
                FORMATION_REPLACEMENT_MEMBER_KEY
        )
                || !hasReplacementFormationSpawnCapacity(
                replacedUnit.worldObj,
                replacedUnit,
                origin
        )
                || placement == null
                || !placement.isFound()
                || !canNaturalFormationSpawnAt(
                replacedUnit.worldObj,
                replacedUnit,
                placement.getX(),
                placement.getY(),
                placement.getZ()
        )) {
            return false;
        }

        UUID resolvedInvasionId = null;
        List invasionBonusFactions = null;
        if (origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD) {
            if (replacedUnit.isInvasionSpawned()
                    || replacedUnit.getInvasionID() != null
                    || !MumakilWarFormationSpawnRegistry
                    .isNativeNearHaradHomeMilitaryCandidate(
                            replacedUnit
                    )) {
                return false;
            }
        } else if (origin
                == MumakilFormationOrigin.CONQUEST_NEAR_HARAD) {
            if (replacedUnit.isInvasionSpawned()
                    || replacedUnit.getInvasionID() != null
                    || !MumakilConquestUnitRollEventHandler
                    .isCapturedConquestCandidate(replacedUnit)
                    || MumakilWarFormationSpawnRegistry
                    .isNativeNearHaradHomeTerritory(replacedUnit)
                    || !MumakilWarFormationSpawnRegistry
                    .isNearHaradConquestMilitaryCandidate(
                            replacedUnit
                    )
                    || MumakilWarFormationSpawnRegistry
                    .getDirectNearHaradConquest(
                            replacedUnit.worldObj,
                            MathHelper.floor_double(replacedUnit.posX),
                            MathHelper.floor_double(replacedUnit.posZ)
                    )
                    < MumakilConfig
                    .conquestFormationMinimumConquest) {
                return false;
            }
        } else if (origin
                == MumakilFormationOrigin.INVASION_NEAR_HARAD) {
            if (invasionId == null
                    || !replacedUnit.isInvasionSpawned()
                    || !invasionId.equals(
                    replacedUnit.getInvasionID()
            )
                    || replacedUnit.getEntityData().getInteger(
                    MumakilInvasionFormationRegistry
                            .INVASION_MEMBER_WEIGHT_KEY
            ) > 0) {
                return false;
            }
            LOTREntityInvasionSpawner spawner =
                    LOTREntityInvasionSpawner.locateInvasionNearby(
                            replacedUnit,
                            invasionId
                    );
            if (spawner == null
                    || spawner.isDead
                    || spawner.worldObj != replacedUnit.worldObj
                    || !invasionId.equals(spawner.getInvasionID())
                    || !MumakilInvasionFormationRegistry
                    .isEligibleInvasion(spawner.getInvasionType())) {
                return false;
            }
            resolvedInvasionId = invasionId;
            invasionBonusFactions = replacedUnit.killBonusFactions;
        } else {
            return false;
        }

        return createAutonomousFormation(
                replacedUnit.worldObj,
                origin,
                resolvedInvasionId,
                invasionBonusFactions,
                placement.getX(),
                placement.getY(),
                placement.getZ(),
                replacedUnit.rotationYaw,
                replacedUnit,
                true,
                false,
                null
        );
    }

    /**
     * Creates one persistent, autonomous Near Harad formation from the
     * dedicated creative spawn egg. The egg owner is used only as the
     * collision-validation exclusion; it is never recorded as an owner.
     */
    public static boolean createSpawnEggFormation(
            World world,
            Entity placementActor,
            double x,
            double y,
            double z,
            float yaw,
            String customName
    ) {
        return createAutonomousFormation(
                world,
                MumakilFormationOrigin.CREATIVE_SPAWN_EGG,
                null,
                null,
                x,
                y,
                z,
                yaw,
                placementActor,
                false,
                true,
                customName
        );
    }

    private static boolean createAutonomousFormation(
            World world,
            MumakilFormationOrigin origin,
            UUID invasionId,
            List invasionBonusFactions,
            double x,
            double y,
            double z,
            float yaw,
            Entity validationExclusion,
            boolean placementPrevalidated,
            boolean persistentFormation,
            String customName
    ) {
        if (world == null
                || world.isRemote
                || origin == null
                || origin == MumakilFormationOrigin.NONE) {
            return false;
        }

        boolean ordinaryUnitReplacement =
                (origin == MumakilFormationOrigin.NATURAL_NEAR_HARAD
                        || origin
                        == MumakilFormationOrigin.CONQUEST_NEAR_HARAD
                        || origin
                        == MumakilFormationOrigin.INVASION_NEAR_HARAD)
                        && validationExclusion
                        instanceof LOTREntityNPC;

        if (!placementPrevalidated) {
            FormationPlacementResult placementResult =
                    validateFormationSpawnAt(
                            world,
                            validationExclusion,
                            x,
                            y,
                            z,
                            origin
                                    == MumakilFormationOrigin
                                    .CREATIVE_SPAWN_EGG
                    );
            if (placementResult != FormationPlacementResult.VALID) {
                return false;
            }
        }

        List<Entity> createdMembers = new ArrayList<Entity>();
        try {
        LOTREntityMumakil mumakil = new LOTREntityMumakil(world);
        mumakil.setLocationAndAngles(
                x,
                y,
                z,
                yaw,
                0.0F
        );
        mumakil.rotationYawHead = yaw;
        mumakil.renderYawOffset = yaw;
        mumakil.bypassNaturalSpawnSpacing();
        mumakil.onSpawnWithEgg(null);
        if (!initializeMount(mumakil, origin)) {
            removeCreatedMembers(createdMembers);
            return false;
        }
        if (customName != null && !customName.isEmpty()) {
            mumakil.setCustomNameTag(customName);
        }
        if (persistentFormation) {
            mumakil.func_110163_bv();
        }
        if (invasionId != null) {
            mumakil.setMumakilInvasionId(invasionId);
            mumakil.getEntityData().setInteger(
                    MumakilInvasionFormationRegistry
                            .INVASION_MEMBER_WEIGHT_KEY,
                    MumakilConfig.INVASION_MUMAK_BUDGET_VALUE
            );
        }
        if (ordinaryUnitReplacement) {
            mumakil.getEntityData().setBoolean(
                    FORMATION_REPLACEMENT_MEMBER_KEY,
                    true
            );
        }
        if (!world.spawnEntityInWorld(mumakil)) {
            removeCreatedMembers(createdMembers);
            return false;
        }
        createdMembers.add(mumakil);

        LOTREntitySouthronChampion driver =
                new LOTREntitySouthronChampion(world);
        driver.onSpawnWithEgg(null);
        if (ordinaryUnitReplacement) {
            driver.getEntityData().setBoolean(
                    FORMATION_REPLACEMENT_MEMBER_KEY,
                    true
            );
            MumakilFormationReplacementService
                    .markReplacementEvaluated(driver);
        }
        if (invasionId != null) {
            driver.setInvasionID(invasionId);
            driver.getEntityData().setInteger(
                    MumakilInvasionFormationRegistry
                            .INVASION_MEMBER_WEIGHT_KEY,
                    MumakilConfig.INVASION_DRIVER_BUDGET_VALUE
            );
            if (invasionBonusFactions != null) {
                driver.killBonusFactions.addAll(
                        invasionBonusFactions
                );
            }
        }
        driver.isNPCPersistent = persistentFormation;
        driver.setShouldTraderRespawn(false);
        /*
         * Keep an attached driver from independently distance-despawning.
         * Natural formations retain their nonpersistent LOTR cap state; spawn-
         * egg formations retain the explicit persistence requested above.
         */
        driver.func_110163_bv();
        mumakil.positionRiderAtMumakilAnchor(driver);
        driver.rotationYaw = mumakil.renderYawOffset;
        driver.prevRotationYaw = driver.rotationYaw;
        driver.renderYawOffset = driver.rotationYaw;
        driver.prevRenderYawOffset = driver.rotationYaw;

        if (!world.spawnEntityInWorld(driver)) {
            removeCreatedMembers(createdMembers);
            return false;
        }
        createdMembers.add(driver);
        driver.mountEntity(mumakil);
        mumakil.updateRiderPosition();
        if (!MumakilDriverControlEventHandler
                .activateValidatedDriver(mumakil, driver)) {
            removeCreatedMembers(createdMembers);
            return false;
        }

        for (int slot = 0;
             slot < FORMATION_ARCHER_COUNT;
             ++slot) {
            LOTREntityMumakilHowdahArcher archer =
                    MumakilHowdahArcherEventHandler
                            .spawnAttachedHowdahArcher(
                                    mumakil,
                                    slot,
                                    persistentFormation,
                                    invasionId,
                                    invasionBonusFactions
            );
            if (archer == null) {
                removeCreatedMembers(createdMembers);
                return false;
            }
            if (ordinaryUnitReplacement) {
                archer.getEntityData().setBoolean(
                        FORMATION_REPLACEMENT_MEMBER_KEY,
                        true
                );
                MumakilFormationReplacementService
                        .markReplacementEvaluated(archer);
            }
            if (invasionId != null) {
                archer.getEntityData().setInteger(
                        MumakilInvasionFormationRegistry
                                .INVASION_MEMBER_WEIGHT_KEY,
                        MumakilConfig.INVASION_ARCHER_BUDGET_VALUE
                );
            }
            createdMembers.add(archer);
        }

        MumakilHowdahArcherEventHandler.markHowdahArcherSetComplete(mumakil);
        if (persistentFormation) {
            mumakil.playLivingSound();
        }
        return true;
        } catch (RuntimeException e) {
            removeCreatedMembers(createdMembers);
            return false;
        }
    }

    public static void removeNaturalFormationMembers(
            LOTREntityMumakil mumakil,
            Entity capturedDriver
    ) {
        if (mumakil == null
                || mumakil.worldObj == null
                || mumakil.worldObj.isRemote
                || !mumakil.isNaturalNearHaradFormation()
                && mumakil.getFormationOrigin()
                != MumakilFormationOrigin.INVASION_NEAR_HARAD) {
            return;
        }

        Entity driver = capturedDriver != null
                ? capturedDriver
                : mumakil.riddenByEntity;
        if (driver != null && !driver.isDead) {
            driver.mountEntity(null);
            driver.setDead();
        }

        String mountUuid = mumakil.getPersistentID().toString();
        List loaded = new ArrayList(mumakil.worldObj.loadedEntityList);
        for (int i = 0; i < loaded.size(); ++i) {
            Object object = loaded.get(i);
            if (!(object instanceof LOTREntityMumakilHowdahArcher)) {
                continue;
            }

            LOTREntityMumakilHowdahArcher archer =
                    (LOTREntityMumakilHowdahArcher)object;
            if (archer.getHowdahMountEntityId() == mumakil.getEntityId()
                    || mountUuid.equals(archer.getHowdahMountUuid())) {
                archer.setDead();
            }
        }
    }

    private static boolean setInventoryStack(
            LOTREntityMumakil mumakil,
            int slot,
            ItemStack stack
    ) {
        IInventory inventory = mumakil.getMumakilMountInventory();
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return false;
        }

        inventory.setInventorySlotContents(slot, stack);
        inventory.markDirty();
        return true;
    }

    private static void removeCreatedMembers(List<Entity> createdMembers) {
        for (int i = createdMembers.size() - 1; i >= 0; --i) {
            Entity entity = createdMembers.get(i);
            if (entity != null && !entity.isDead) {
                if (entity.ridingEntity != null) {
                    entity.mountEntity(null);
                }
                entity.setDead();
            }
        }
    }

}
