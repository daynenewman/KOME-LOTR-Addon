package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

/**
 * Applies one position-only translation to a newly spawned player arrow when
 * the player's logical passenger position lags the Mumak's current pure seat
 * anchor. The player, Mumak, projectile motion, and all bow-assigned gameplay
 * state remain untouched.
 */
public final class MumakilPlayerArrowOriginEventHandler {
    private static final String SEAT_ERROR_TRANSLATED_KEY =
            "lotrmoremobs_mumakArrowSeatErrorTranslated";
    private static final String ADDON_REPOSITIONED_KEY =
            "lotrmoremobs_mumakArrowAddonRepositioned";
    private static final String TRANSLATION_X_KEY =
            "lotrmoremobs_mumakArrowTranslationX";
    private static final String TRANSLATION_Y_KEY =
            "lotrmoremobs_mumakArrowTranslationY";
    private static final String TRANSLATION_Z_KEY =
            "lotrmoremobs_mumakArrowTranslationZ";
    private static final String CAPTURED_KEY =
            "lotrmoremobs_mumakArrowOriginCaptured";
    private static final String BEFORE_X_KEY =
            "lotrmoremobs_mumakArrowBeforeX";
    private static final String BEFORE_Y_KEY =
            "lotrmoremobs_mumakArrowBeforeY";
    private static final String BEFORE_Z_KEY =
            "lotrmoremobs_mumakArrowBeforeZ";
    private static final String PLAYER_BEFORE_X_KEY =
            "lotrmoremobs_mumakArrowPlayerBeforeX";
    private static final String PLAYER_BEFORE_Y_KEY =
            "lotrmoremobs_mumakArrowPlayerBeforeY";
    private static final String PLAYER_BEFORE_Z_KEY =
            "lotrmoremobs_mumakArrowPlayerBeforeZ";
    private static final String PLAYER_EYE_BEFORE_Y_KEY =
            "lotrmoremobs_mumakArrowPlayerEyeBeforeY";
    private static final String POSITION_CHANGE_COUNT_KEY =
            "lotrmoremobs_mumakArrowPositionChangeCount";
    private static final double POSITION_EPSILON = 1.0E-7D;

    /**
     * Capture the bow-created coordinates before normal addon event handling.
     * Per-arrow entity data avoids a shared/current firing archer or slot.
     */
    @SubscribeEvent(
            priority = EventPriority.HIGHEST,
            receiveCanceled = true
    )
    public void captureMountedPlayerArrowOrigin(
            EntityJoinWorldEvent event
    ) {
        if (!LOTREntityMumakil.isPlayerSeatDiagnosticsEnabled()) {
            return;
        }

        EntityArrow arrow = getMountedPlayerArrow(event);
        if (arrow == null) {
            return;
        }

        NBTTagCompound data = arrow.getEntityData();
        if (data.getBoolean(CAPTURED_KEY)) {
            return;
        }

        data.setBoolean(CAPTURED_KEY, true);
        data.setDouble(BEFORE_X_KEY, arrow.posX);
        data.setDouble(BEFORE_Y_KEY, arrow.posY);
        data.setDouble(BEFORE_Z_KEY, arrow.posZ);
        EntityPlayer player =
                (EntityPlayer)arrow.shootingEntity;
        data.setDouble(PLAYER_BEFORE_X_KEY, player.posX);
        data.setDouble(PLAYER_BEFORE_Y_KEY, player.posY);
        data.setDouble(PLAYER_BEFORE_Z_KEY, player.posZ);
        data.setDouble(
                PLAYER_EYE_BEFORE_Y_KEY,
                player.posY + player.getEyeHeight()
        );
    }

    /**
     * Translate only the individual arrow at the end of join-world handling.
     * The persistent marker makes this idempotent if another mod reposts the
     * same entity event or the arrow later rejoins a world.
     */
    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true
    )
    public void translateAndLogMountedPlayerArrowOrigin(
            EntityJoinWorldEvent event
    ) {
        EntityArrow arrow = getMountedPlayerArrow(event);
        if (arrow == null || event.isCanceled()) {
            return;
        }

        Entity shooter = arrow.shootingEntity;
        EntityPlayer player = (EntityPlayer)shooter;
        Entity ridingEntity = player.ridingEntity;
        LOTREntityMumakil parentMumak =
                (LOTREntityMumakil)ridingEntity;
        if (parentMumak.riddenByEntity != player) {
            return;
        }

        Vec3 seatAnchor = parentMumak.calculatePlayerSeatPosition(
                player
        );
        if (seatAnchor == null) {
            return;
        }

        NBTTagCompound data = arrow.getEntityData();
        if (!data.getBoolean(SEAT_ERROR_TRANSLATED_KEY)) {
            double deltaX = seatAnchor.xCoord - player.posX;
            double deltaY = seatAnchor.yCoord - player.posY;
            double deltaZ = seatAnchor.zCoord - player.posZ;
            boolean repositioned = positionChanged(
                    0.0D,
                    0.0D,
                    0.0D,
                    deltaX,
                    deltaY,
                    deltaZ
            );

            data.setBoolean(SEAT_ERROR_TRANSLATED_KEY, true);
            data.setBoolean(ADDON_REPOSITIONED_KEY, repositioned);
            data.setDouble(TRANSLATION_X_KEY, deltaX);
            data.setDouble(TRANSLATION_Y_KEY, deltaY);
            data.setDouble(TRANSLATION_Z_KEY, deltaZ);

            if (repositioned) {
                translateArrowPosition(
                        arrow,
                        deltaX,
                        deltaY,
                        deltaZ
                );
                data.setInteger(POSITION_CHANGE_COUNT_KEY, 1);
            }
        }

        if (!LOTREntityMumakil.isPlayerSeatDiagnosticsEnabled()
                || !data.getBoolean(CAPTURED_KEY)) {
            return;
        }

        double beforeX = data.getDouble(BEFORE_X_KEY);
        double beforeY = data.getDouble(BEFORE_Y_KEY);
        double beforeZ = data.getDouble(BEFORE_Z_KEY);
        double playerBeforeX =
                data.getDouble(PLAYER_BEFORE_X_KEY);
        double playerBeforeY =
                data.getDouble(PLAYER_BEFORE_Y_KEY);
        double playerBeforeZ =
                data.getDouble(PLAYER_BEFORE_Z_KEY);
        double playerEyeBeforeY =
                data.getDouble(PLAYER_EYE_BEFORE_Y_KEY);
        boolean anyJoinHandlerRepositioned = positionChanged(
                beforeX,
                beforeY,
                beforeZ,
                arrow.posX,
                arrow.posY,
                arrow.posZ
        );
        int positionChangeCount = data.getInteger(
                POSITION_CHANGE_COUNT_KEY
        );
        boolean addonRepositioned = data.getBoolean(
                ADDON_REPOSITIONED_KEY
        );
        double translationX = data.getDouble(TRANSLATION_X_KEY);
        double translationY = data.getDouble(TRANSLATION_Y_KEY);
        double translationZ = data.getDouble(TRANSLATION_Z_KEY);
        ArcherSlotSelection selection =
                selectValidatedArcherSlot(arrow);

        System.out.println(
                "[LOTRMoreMobs][MumakPlayerSeat]"
                        + " reason=ARROW_SPAWN"
                        + " worldTick="
                        + event.world.getTotalWorldTime()
                        + " logicalSide=SERVER"
                        + " playerEntityId=" + player.getEntityId()
                        + " mumakEntityId="
                        + parentMumak.getEntityId()
                        + " playerRidingEntityClass="
                        + ridingEntity.getClass().getName()
                        + " playerRidingEntityId="
                        + ridingEntity.getEntityId()
                        + " mumakRiddenByEntityIsPlayer="
                        + (parentMumak.riddenByEntity == player)
                        + " mumakPos=" + formatPosition(
                        parentMumak.posX,
                        parentMumak.posY,
                        parentMumak.posZ
                )
                        + " mumakPrevPos=" + formatPosition(
                        parentMumak.prevPosX,
                        parentMumak.prevPosY,
                        parentMumak.prevPosZ
                )
                        + " mumakRotationYaw="
                        + parentMumak.rotationYaw
                        + " mumakRenderYawOffset="
                        + parentMumak.renderYawOffset
                        + " playerViewYaw=" + player.rotationYaw
                        + " arrowEntityId=" + arrow.getEntityId()
                        + " shootingEntityClass="
                        + shooter.getClass().getName()
                        + " shootingEntityId="
                        + shooter.getEntityId()
                        + " arrowShootingEntityClass="
                        + shooter.getClass().getName()
                        + " arrowShootingEntityId="
                        + shooter.getEntityId()
                        + " shootingEntityPosBeforeAddonHandling="
                        + formatPosition(
                        playerBeforeX,
                        playerBeforeY,
                        playerBeforeZ
                )
                        + " shootingEntityPosAfterAddonHandling="
                        + formatPosition(
                        player.posX,
                        player.posY,
                        player.posZ
                )
                        + " playerPrevPos="
                        + formatPosition(
                        player.prevPosX,
                        player.prevPosY,
                        player.prevPosZ
                )
                        + " playerEyePosBeforeAddonHandling="
                        + formatPosition(
                        playerBeforeX,
                        playerEyeBeforeY,
                        playerBeforeZ
                )
                        + " playerEyePosAfterAddonHandling="
                        + formatPosition(
                        player.posX,
                        player.posY + player.getEyeHeight(),
                        player.posZ
                )
                        + " arrowPosBeforeAddonHandling="
                        + formatPosition(beforeX, beforeY, beforeZ)
                        + " arrowInitialPos="
                        + formatPosition(beforeX, beforeY, beforeZ)
                        + " arrowPosAfterAddonHandling="
                        + formatPosition(
                        arrow.posX,
                        arrow.posY,
                        arrow.posZ
                )
                        + " calculatedSeatAnchor="
                        + formatPosition(
                        seatAnchor.xCoord,
                        seatAnchor.yCoord,
                        seatAnchor.zCoord
                )
                        + " playerMinusSeatAnchor="
                        + formatPosition(
                        player.posX - seatAnchor.xCoord,
                        player.posY - seatAnchor.yCoord,
                        player.posZ - seatAnchor.zCoord
                )
                        + " arrowMinusSeatAnchor="
                        + formatPosition(
                        beforeX - seatAnchor.xCoord,
                        beforeY - seatAnchor.yCoord,
                        beforeZ - seatAnchor.zCoord
                )
                        + " arrowAfterMinusSeatAnchor="
                        + formatPosition(
                        arrow.posX - seatAnchor.xCoord,
                        arrow.posY - seatAnchor.yCoord,
                        arrow.posZ - seatAnchor.zCoord
                )
                        + " arrowMinusPlayerEye="
                        + formatPosition(
                        beforeX - playerBeforeX,
                        beforeY - playerEyeBeforeY,
                        beforeZ - playerBeforeZ
                )
                        + " seatErrorTranslation="
                        + formatPosition(
                        translationX,
                        translationY,
                        translationZ
                )
                        + " cachedSeatOrOriginCoordinates=NONE"
                        + " archerSlotSelected="
                        + selection.selected
                        + " selectedSlot=" + selection.slot
                        + " validatedArcherParentMumakId="
                        + selection.parentMumakId
                        + " parentMumakEntityId="
                        + parentMumak.getEntityId()
                        + " playerRidingEntityId="
                        + ridingEntity.getEntityId()
                        + " addonRepositionedArrow="
                        + addonRepositioned
                        + " anyJoinHandlerRepositionedArrow="
                        + anyJoinHandlerRepositioned
                        + " arrowChanged="
                        + anyJoinHandlerRepositioned
                        + " arrowPositionChangeCount="
                        + positionChangeCount
                        + " addonPositionCorrectionPath="
                        + (addonRepositioned
                        ? "ONE_TIME_ARROW_SEAT_ERROR_TRANSLATION"
                        : "ZERO_SEAT_ERROR_NO_POSITION_WRITE")
                        + " eventCanceled=" + event.isCanceled()
        );
    }

    private static void translateArrowPosition(
            EntityArrow arrow,
            double deltaX,
            double deltaY,
            double deltaZ
    ) {
        double motionX = arrow.motionX;
        double motionY = arrow.motionY;
        double motionZ = arrow.motionZ;

        arrow.setPosition(
                arrow.posX + deltaX,
                arrow.posY + deltaY,
                arrow.posZ + deltaZ
        );
        arrow.prevPosX += deltaX;
        arrow.prevPosY += deltaY;
        arrow.prevPosZ += deltaZ;
        arrow.lastTickPosX += deltaX;
        arrow.lastTickPosY += deltaY;
        arrow.lastTickPosZ += deltaZ;

        arrow.motionX = motionX;
        arrow.motionY = motionY;
        arrow.motionZ = motionZ;
    }

    private static EntityArrow getMountedPlayerArrow(
            EntityJoinWorldEvent event
    ) {
        if (event == null
                || event.world == null
                || event.world.isRemote
                || !(event.entity instanceof EntityArrow)) {
            return null;
        }

        EntityArrow arrow = (EntityArrow)event.entity;
        Entity shooter = arrow.shootingEntity;
        if (!(shooter instanceof EntityPlayer)) {
            return null;
        }

        EntityPlayer player = (EntityPlayer)shooter;
        return player.ridingEntity instanceof LOTREntityMumakil
                ? arrow
                : null;
    }

    /**
     * The only legal route to a Southron slot selection starts with the exact
     * arrow shooter class and then resolves the fully validated attachment.
     * EntityPlayer shooters fail the first condition and always return NONE.
     */
    private static ArcherSlotSelection selectValidatedArcherSlot(
            EntityArrow arrow
    ) {
        if (arrow == null
                || !(arrow.shootingEntity
                instanceof LOTREntityMumakilHowdahArcher)) {
            return ArcherSlotSelection.NONE;
        }

        LOTREntityMumakilHowdahArcher archer =
                (LOTREntityMumakilHowdahArcher)
                        arrow.shootingEntity;
        LOTREntityMumakil parentMumak =
                MumakilHowdahArcherEventHandler
                        .getFullyValidatedAttachedArcherParent(
                                archer
                        );
        if (parentMumak == null) {
            return ArcherSlotSelection.NONE;
        }

        return new ArcherSlotSelection(
                true,
                archer.getHowdahSlot(),
                parentMumak.getEntityId()
        );
    }

    private static boolean positionChanged(
            double beforeX,
            double beforeY,
            double beforeZ,
            double afterX,
            double afterY,
            double afterZ
    ) {
        return Math.abs(beforeX - afterX) > POSITION_EPSILON
                || Math.abs(beforeY - afterY) > POSITION_EPSILON
                || Math.abs(beforeZ - afterZ) > POSITION_EPSILON;
    }

    private static String formatPosition(
            double x,
            double y,
            double z
    ) {
        return "(" + x + "," + y + "," + z + ")";
    }

    private static final class ArcherSlotSelection {
        private static final ArcherSlotSelection NONE =
                new ArcherSlotSelection(false, -1, -1);

        private final boolean selected;
        private final int slot;
        private final int parentMumakId;

        private ArcherSlotSelection(
                boolean selected,
                int slot,
                int parentMumakId
        ) {
            this.selected = selected;
            this.slot = slot;
            this.parentMumakId = parentMumakId;
        }
    }
}
