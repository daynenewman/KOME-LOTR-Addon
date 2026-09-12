package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.network.RamControlActionPacket;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import com.enovak.lotrmoremobs.siege.ram.RamControlManager;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.MouseEvent;

/**
 * Keeps the Battle Ram directly controllable even though its physical entity
 * is intentionally transparent to projectile/entity collision ray traces.
 */
public final class RamDirectInteractionClientHandler {

    private static final double INTERACTION_REACH = 5.0D;
    private static final double TRACE_EXPANSION = 0.25D;

    @SubscribeEvent
    public void onMouseInput(MouseEvent event) {
        if (event == null
                || event.button != 1
                || !event.buttonstate
                || ClientRamTargetState.isActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.thePlayer;
        if (player == null
                || minecraft.theWorld == null
                || minecraft.currentScreen != null) {
            return;
        }

        /*
         * Preserve ordinary interactions with visible entities such as the
         * ram carriers. Their server-side handler redirects sneak-interaction
         * to the parent ram and suppresses LOTR NPC quest interaction.
         */
        if (minecraft.objectMouseOver != null
                && minecraft.objectMouseOver.entityHit != null) {
            return;
        }

        EntityBattleRam ram = findLookedAtRam(player);
        if (ram == null) {
            return;
        }

        event.setCanceled(true);
        Main.network.sendToServer(new RamControlActionPacket(
                player.isSneaking()
                        ? RamControlManager.OPEN_CONTROL
                        : RamControlManager.TOGGLE_PAUSE,
                player.dimension,
                ram.getEntityId()
        ));
    }

    private EntityBattleRam findLookedAtRam(EntityPlayer player) {
        Vec3 start = Vec3.createVectorHelper(
                player.posX,
                player.posY + player.getEyeHeight(),
                player.posZ
        );
        Vec3 look = player.getLook(1.0F);
        Vec3 end = start.addVector(
                look.xCoord * INTERACTION_REACH,
                look.yCoord * INTERACTION_REACH,
                look.zCoord * INTERACTION_REACH
        );

        AxisAlignedBB search = player.boundingBox
                .addCoord(
                        look.xCoord * INTERACTION_REACH,
                        look.yCoord * INTERACTION_REACH,
                        look.zCoord * INTERACTION_REACH
                )
                .expand(1.0D, 1.0D, 1.0D);

        List rams = player.worldObj.getEntitiesWithinAABB(
                EntityBattleRam.class,
                search
        );

        EntityBattleRam closestRam = null;
        double closestDistance = INTERACTION_REACH + 1.0D;

        for (Object object : rams) {
            EntityBattleRam candidate = (EntityBattleRam)object;
            if (candidate == null || candidate.isDead) {
                continue;
            }

            AxisAlignedBB bounds = candidate.boundingBox.expand(
                    TRACE_EXPANSION,
                    TRACE_EXPANSION,
                    TRACE_EXPANSION
            );

            double distance;
            if (bounds.isVecInside(start)) {
                distance = 0.0D;
            } else {
                MovingObjectPosition hit = bounds.calculateIntercept(
                        start,
                        end
                );
                if (hit == null || hit.hitVec == null) {
                    continue;
                }
                distance = start.distanceTo(hit.hitVec);
            }

            if (distance < closestDistance) {
                closestDistance = distance;
                closestRam = candidate;
            }
        }

        return closestRam;
    }
}
