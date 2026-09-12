package com.fuzs.aquaacrobatics.client.entity;

import static net.minecraft.entity.SharedMonsterAttributes.movementSpeed;

import java.util.Objects;
import java.lang.reflect.Field;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.potion.Potion;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovementInput;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.AquaMovementLogic;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.util.BlockPos;
import com.fuzs.aquaacrobatics.util.MovementInputStorage;
import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;
import com.fuzs.aquaacrobatics.util.math.AxisAlignedBBSpliterator;

public final class AquaClientPlayerMovementPolicy {

    private AquaClientPlayerMovementPolicy() {}

    public static void captureLivingUpdateHead(EntityPlayerSP player, MovementInputStorage storage) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return;

        int sprintTimer = readTimer(player, "sprintToggleTimer", "field_71156_d");
        int flyTimer = readTimer(player, "flyToggleTimer", "field_71101_bC");

        SprintToggleTimerSnapshot snapshot = snapshotSprintToggleTimer(
            sprintTimer,
            player.movementInput.sneak,
            player.isUsingItem(),
            player.isRiding());

        writeTimer(
            player,
            snapshot.getSprintToggleTimer(),
            "sprintToggleTimer",
            "field_71156_d");
        storage.sprintToggleTimer = snapshot.getMovementSprintToggleTimer();
        copyMovementInput(storage, player.movementInput);
        storeMovementSnapshot(storage, player.isSprinting(), player.capabilities.isFlying);
        storage.isStartingToFly = isStartingToFly(
            player.capabilities.allowFlying,
            EFRIntegration.isSpectator(player),
            player.capabilities.isFlying,
            player.movementInput.jump,
            Minecraft.getMinecraft().gameSettings.keyBindJump.getIsKeyPressed(),
            flyTimer,
            ((IPlayerResizeable) player).isSwimming());
    }

    private static int readTimer(EntityPlayerSP player, String... names) {
        try {
            Field field = timerField(player, names);
            field.setAccessible(true);
            return field.getInt(player);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing EntityPlayerSP timer field", e);
        }
    }

    private static void writeTimer(EntityPlayerSP player, int value, String... names) {
        try {
            Field field = timerField(player, names);
            field.setAccessible(true);
            field.setInt(player, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Missing EntityPlayerSP timer field", e);
        }
    }

    private static Field timerField(EntityPlayerSP player, String... names) throws NoSuchFieldException {
        for (Class<?> type = player.getClass(); type != null; type = type.getSuperclass()) {
            for (String name : names) {
                try {
                    return type.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {}
            }
        }
        throw new NoSuchFieldException(names[0]);
    }

    public static void handleWaterSneaking(EntityPlayerSP player) {

        if (!PlayerMovementMode.useModernPlayerMovement(player)) return;
        if (player.isInWater() && player.movementInput.sneak && !player.capabilities.isFlying) {
            player.motionY -= 0.03999999910593033 * player.getEntityAttribute(movementSpeed).getAttributeValue();
        }
    }

    public static void applyLivingUpdateTail(EntityPlayerSP player, MovementInputStorage storage) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return;

        boolean betterSprintingLoaded = cpw.mods.fml.common.Loader.isModLoaded("bettersprinting");
        LivingUpdateTailDecision decision = getLivingUpdateTailDecision(player, storage,
            betterSprintingLoaded, player.isInWater());
        if (decision.shouldSuppressSprint()) {
            player.setSprinting(false);
        } else {
            if (decision.shouldRestoreSprint()) player.setSprinting(storage.isSprinting);
            if (decision.shouldRunSprintPolicy()) {
                boolean saturated = decision.isSaturated();
                SprintStartDecision start = startSprinting(player, storage, saturated,
                    Minecraft.getMinecraft().gameSettings.keyBindSprint.getIsKeyPressed());
                if (start.shouldSetSprintToggleTimer()) {
                    writeTimer(
                        player,
                        start.getSprintToggleTimer(),
                        "sprintToggleTimer",
                        "field_71156_d");
                }
                if (start.shouldStartSprinting()) player.setSprinting(true);
                stopSprinting(player, storage, saturated);
            }
        }
        handleWaterSneaking(player);
        slowDownSneakFlying(player);
    }

    public static boolean isActuallySneaking(EntityPlayerSP player) {

        return player.isSneaking();
    }

    public static boolean isForcedDown(EntityPlayerSP player) {

        if (!PlayerMovementMode.useModernPlayerMovement(player)) return false;
        IPlayerResizeable resizeable = (IPlayerResizeable) player;
        return resizeable.isResizingAllowed() && !player.capabilities.isFlying
            && (resizeable.getPose() == Pose.CROUCHING || resizeable.isVisuallySwimming());
    }

    public static boolean isUsingSwimmingAnimation(EntityPlayerSP player) {

        if (!PlayerMovementMode.useModernPlayerMovement(player)) return false;
        return isUsingSwimmingAnimation(player, player.movementInput.moveForward, player.movementInput.moveStrafe);
    }

    public static boolean isUsingSwimmingAnimation(EntityPlayerSP player, float moveForward, float moveStrafe) {

        if (!PlayerMovementMode.useModernPlayerMovement(player)) return false;
        if (canSwim(player)) {
            return isMovingForward(moveForward, moveStrafe);
        }

        if (ConfigHandler.MovementConfig.sidewaysSprinting) {
            return moveForward >= 0.8F || Math.abs(moveStrafe) > 0.8F;
        }

        return moveForward >= 0.8F;
    }

    public static boolean canSwim(EntityPlayerSP player) {

        return PlayerMovementMode.useModernPlayerMovement(player)
            && ((IPlayerResizeable) player).getEyesInWaterPlayer();
    }

    public static boolean isMovingForward(float moveForward, float moveStrafe) {

        if (moveForward > 1.0E-5F) {
            return true;
        } else if (ConfigHandler.MovementConfig.sidewaysSwimming) {
            return Math.abs(moveStrafe) > 1.0E-5F;
        }

        return false;
    }

    public static boolean canPerformElytraTakeoff(EntityPlayerSP player, MovementInputStorage movementStorage) {

        return PlayerMovementMode.useModernPlayerMovement(player)
            && ConfigHandler.MovementConfig.easyElytraTakeoff && player.movementInput.jump
            && !movementStorage.isStartingToFly
            && !movementStorage.jump
            && player.motionY >= 0.0
            && !player.capabilities.isFlying
            && !player.isRiding()
            && !player.isOnLadder();
    }

    public static boolean handleExactPlayerBlockCollision(EntityPlayerSP player, double x, double z) {

        if (!PlayerMovementMode.useModernPlayerMovement(player)) return false;
        if (ConfigHandler.playerBlockCollisions != ConfigHandler.PlayerBlockCollisions.EXACT) {
            return false;
        }

        if (!player.noClip) {
            setPlayerOffsetMotion(player, x, z);
        }

        return true;
    }

    public static int roundPlayerBlockCollisionOffset(float value) {

        if (!PlayerMovementMode.useModernPlayerMovementClient()) return Math.round(value);
        if (ConfigHandler.playerBlockCollisions == ConfigHandler.PlayerBlockCollisions.APPROXIMATE) {
            value -= 0.65;
        }

        return Math.round(value);
    }

    public static void stopSprinting(EntityPlayerSP player, MovementInputStorage movementStorage,
        boolean isSaturated) {

        if (player.isSprinting()) {
            boolean isNotMoving = !isMovingForward(player.movementInput.moveForward, player.movementInput.moveStrafe)
                || !isSaturated;
            boolean hasCollided = isNotMoving || player.isInWater() && !canSwim(player) && !movementStorage.isFlying;
            if (((IPlayerResizeable) player).isSwimming()) {
                if (!player.movementInput.sneak && isNotMoving || !player.isInWater()) {
                    player.setSprinting(false);
                }
            } else if (hasCollided) {
                player.setSprinting(false);
            }
        }
    }

    public static SprintStartDecision startSprinting(EntityPlayerSP player, MovementInputStorage movementStorage,
        boolean isSaturated, boolean sprintKeyPressed) {

        boolean shouldSetSprintToggleTimer = false;
        int sprintToggleTimer = 0;
        boolean shouldStartSprinting = false;
        boolean wasSneaking = movementStorage.sneak;
        boolean wasSwimming = isUsingSwimmingAnimation(
            player,
            movementStorage.moveForward,
            movementStorage.moveStrafe);
        boolean isSprintingEnvironment = player.onGround || canSwim(player) || movementStorage.isFlying;
        if (isSprintingEnvironment && !wasSneaking
            && !wasSwimming
            && isUsingSwimmingAnimation(player)
            && !player.isSprinting()
            && isSaturated
            && !player.isPotionActive(Potion.blindness)) {
            if (movementStorage.sprintToggleTimer <= 0 && !sprintKeyPressed) {
                shouldSetSprintToggleTimer = true;
                sprintToggleTimer = ConfigHandler.MovementConfig.noDoubleTapSprinting ? 0 : 7;
            } else {
                shouldStartSprinting = true;
            }
        }

        if (!(player.isSprinting() || shouldStartSprinting) && (!player.isInWater() || canSwim(player))
            && isUsingSwimmingAnimation(player)
            && isSaturated
            && !player.isPotionActive(Potion.blindness)
            && sprintKeyPressed) {
            shouldStartSprinting = true;
        }

        return new SprintStartDecision(shouldSetSprintToggleTimer, sprintToggleTimer, shouldStartSprinting);
    }

    public static SprintToggleTimerSnapshot snapshotSprintToggleTimer(int sprintToggleTimer, boolean isSneaking,
        boolean isUsingItem, boolean isRiding) {

        if (isSneaking) {
            sprintToggleTimer = 0;
        }

        int movementSprintToggleTimer = sprintToggleTimer;
        if (movementSprintToggleTimer > 0) {
            --movementSprintToggleTimer;
        }

        if (isUsingItem && !isRiding) {
            movementSprintToggleTimer = 0;
        }

        return new SprintToggleTimerSnapshot(sprintToggleTimer, movementSprintToggleTimer);
    }

    public static void copyMovementInput(MovementInputStorage movementStorage, MovementInput movementInput) {

        movementStorage.copyFrom(movementInput);
    }

    public static void storeMovementSnapshot(MovementInputStorage movementStorage, boolean isSprinting,
        boolean isFlying) {

        movementStorage.isSprinting = isSprinting;
        movementStorage.isFlying = isFlying;
    }

    public static boolean isStartingToFly(boolean allowFlying, boolean isSpectator, boolean isFlying,
        boolean isJumping, boolean isJumpKeyPressed, int flyToggleTimer, boolean isSwimming) {

        if (allowFlying) {
            if (isSpectator) {
                return !isFlying;
            } else if (!isJumping && isJumpKeyPressed) {
                return flyToggleTimer != 0 && !isSwimming;
            }
        }

        return false;
    }

    public static LivingUpdateTailDecision getLivingUpdateTailDecision(EntityPlayerSP player,
        MovementInputStorage movementStorage, boolean betterSprintingLoaded, boolean inWater) {

        if (isForcedLandCrawling(player)) {
            return LivingUpdateTailDecision.FORCED_LAND_CRAWLING;
        }

        boolean shouldRestoreSprint = !betterSprintingLoaded ? player.isSprinting() != movementStorage.isSprinting
            : inWater;
        boolean shouldRunSprintPolicy = !betterSprintingLoaded || inWater;
        boolean isSaturated = shouldRunSprintPolicy
            && ((float) player.getFoodStats().getFoodLevel() > 6.0F || player.capabilities.allowFlying);
        return new LivingUpdateTailDecision(false, shouldRestoreSprint, shouldRunSprintPolicy, isSaturated);
    }

    public static void slowDownSneakFlying(EntityPlayerSP player) {

        if (player.capabilities.isFlying && player.movementInput.sneak) {
            player.movementInput.moveStrafe = (float) ((double) player.movementInput.moveStrafe * 0.3);
            player.movementInput.moveForward = (float) ((double) player.movementInput.moveForward * 0.3);
        }
    }

    public static void applyForcedLandCrawlMovement(EntityPlayerSP player) {

        if (isForcedLandCrawling(player)) {
            AquaMovementLogic.applyForcedLandCrawlMovement(player, player.movementInput);
        }
    }

    public static void suppressForcedLandCrawlSprint(EntityPlayerSP player) {

        if (isForcedLandCrawling(player)) {
            player.setSprinting(false);
        }
    }

    private static boolean isForcedLandCrawling(EntityPlayerSP player) {

        IPlayerResizeable resizeable = (IPlayerResizeable) player;
        return AquaMovementLogic.isForcedLandCrawling(
            player,
            resizeable,
            player.movementInput,
            ((IPlayerSPSwimming) player).isForcedDown());
    }

    private static void setPlayerOffsetMotion(EntityPlayerSP player, double x, double z) {

        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);

        if (shouldBlockPushPlayer(player, blockX, blockZ)) {
            double d0 = x - blockX;
            double d1 = z - blockZ;
            ForgeDirection direction = null;
            double closest = Double.MAX_VALUE;

            ForgeDirection[] xzPlane = new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.EAST,
                ForgeDirection.NORTH, ForgeDirection.SOUTH };

            for (ForgeDirection dir : xzPlane) {
                boolean isX = dir.offsetX != 0;
                double d3 = isX ? d0 : d1;
                double d4 = (dir.offsetX + dir.offsetZ > 0) ? (1.0 - d3) : d3;

                int offsetX = blockX + dir.offsetX;
                int offsetZ = blockZ + dir.offsetZ;

                if (d4 < closest && !shouldBlockPushPlayer(player, offsetX, offsetZ)) {
                    closest = d4;
                    direction = dir;
                }
            }

            if (direction != null) {
                if (direction.offsetX != 0) {
                    player.motionX = 0.1 * direction.offsetX;
                } else {
                    player.motionZ = 0.1 * direction.offsetZ;
                }
            }
        }
    }

    private static boolean shouldBlockPushPlayer(EntityPlayerSP player, int x, int z) {

        double minY = player.boundingBox.minY;
        double maxY = player.boundingBox.maxY;
        AxisAlignedBB aabb = AxisAlignedBB.getBoundingBox(x, minY, z, x + 1.0, maxY, z + 1.0);
        return !isAxisAlignedBBNotClear(player.worldObj, player, aabb.expand(-1.0E-7, -1.0E-7, -1.0E-7));
    }

    private static boolean shouldBlockPushPlayer(EntityPlayerSP player, BlockPos pos) {

        double minY = player.boundingBox.minY;
        double maxY = player.boundingBox.maxY;
        AxisAlignedBB aabb = AxisAlignedBB
            .getBoundingBox(pos.getX(), minY, pos.getZ(), pos.getX() + 1.0, maxY, pos.getZ() + 1.0);
        return !isAxisAlignedBBNotClear(player.worldObj, player, aabb.expand(-1.0E-7, -1.0E-7, -1.0E-7));
    }

    private static boolean isAxisAlignedBBNotClear(World world, @Nullable Entity entity, AxisAlignedBB aabb) {

        return createAxisAlignedBBStream(world, entity, aabb).allMatch(Objects::isNull);
    }

    private static Stream<AxisAlignedBB> createAxisAlignedBBStream(World world, @Nullable Entity entity,
        AxisAlignedBB aabb) {

        return StreamSupport.stream(new AxisAlignedBBSpliterator(world, entity, aabb), false);
    }

    public static final class SprintStartDecision {

        private final boolean shouldSetSprintToggleTimer;
        private final int sprintToggleTimer;
        private final boolean shouldStartSprinting;

        private SprintStartDecision(boolean shouldSetSprintToggleTimer, int sprintToggleTimer,
            boolean shouldStartSprinting) {

            this.shouldSetSprintToggleTimer = shouldSetSprintToggleTimer;
            this.sprintToggleTimer = sprintToggleTimer;
            this.shouldStartSprinting = shouldStartSprinting;
        }

        public boolean shouldSetSprintToggleTimer() {

            return this.shouldSetSprintToggleTimer;
        }

        public int getSprintToggleTimer() {

            return this.sprintToggleTimer;
        }

        public boolean shouldStartSprinting() {

            return this.shouldStartSprinting;
        }
    }

    public static final class LivingUpdateTailDecision {

        private static final LivingUpdateTailDecision FORCED_LAND_CRAWLING = new LivingUpdateTailDecision(
            true,
            false,
            false,
            false);

        private final boolean shouldSuppressSprint;
        private final boolean shouldRestoreSprint;
        private final boolean shouldRunSprintPolicy;
        private final boolean isSaturated;

        private LivingUpdateTailDecision(boolean shouldSuppressSprint, boolean shouldRestoreSprint,
            boolean shouldRunSprintPolicy, boolean isSaturated) {

            this.shouldSuppressSprint = shouldSuppressSprint;
            this.shouldRestoreSprint = shouldRestoreSprint;
            this.shouldRunSprintPolicy = shouldRunSprintPolicy;
            this.isSaturated = isSaturated;
        }

        public boolean shouldSuppressSprint() {

            return this.shouldSuppressSprint;
        }

        public boolean shouldRestoreSprint() {

            return this.shouldRestoreSprint;
        }

        public boolean shouldRunSprintPolicy() {

            return this.shouldRunSprintPolicy;
        }

        public boolean isSaturated() {

            return this.isSaturated;
        }
    }

    public static final class SprintToggleTimerSnapshot {

        private final int sprintToggleTimer;
        private final int movementSprintToggleTimer;

        private SprintToggleTimerSnapshot(int sprintToggleTimer, int movementSprintToggleTimer) {

            this.sprintToggleTimer = sprintToggleTimer;
            this.movementSprintToggleTimer = movementSprintToggleTimer;
        }

        public int getSprintToggleTimer() {

            return this.sprintToggleTimer;
        }

        public int getMovementSprintToggleTimer() {

            return this.movementSprintToggleTimer;
        }
    }
}
