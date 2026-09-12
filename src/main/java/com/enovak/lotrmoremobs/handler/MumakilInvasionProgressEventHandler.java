package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import com.enovak.lotrmoremobs.spawning.MumakilInvasionFormationRegistry;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.UUID;
import lotr.common.LOTRMod;
import lotr.common.entity.LOTREntityInvasionSpawner;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Applies the additional invasion-life value carried by Mumak formation
 * members. Native NPC death code still owns its normal one-point decrement.
 */
public final class MumakilInvasionProgressEventHandler {
    private static final String WEIGHTED_CREDIT_APPLIED_KEY =
            "lotrmoremobs_mumakWeightedInvasionCreditApplied";
    private static final Field INVASION_REMAINING_FIELD =
            resolveInvasionRemainingField();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (event == null
                || event.entityLiving == null
                || event.entityLiving.worldObj == null
                || event.entityLiving.worldObj.isRemote) {
            return;
        }

        EntityLivingBase victim = event.entityLiving;
        NBTTagCompound data = victim.getEntityData();
        int memberWeight = data.getInteger(
                MumakilInvasionFormationRegistry
                        .INVASION_MEMBER_WEIGHT_KEY
        );
        if (memberWeight <= 0
                || data.getBoolean(WEIGHTED_CREDIT_APPLIED_KEY)) {
            return;
        }

        EntityPlayer player =
                LOTRMod.getDamagingPlayerIncludingUnits(event.source);
        if (player == null) {
            return;
        }

        UUID invasionId = getInvasionId(victim);
        if (invasionId == null) {
            return;
        }

        LOTREntityInvasionSpawner spawner =
                LOTREntityInvasionSpawner.locateInvasionNearby(
                        victim,
                        invasionId
                );
        if (spawner == null) {
            return;
        }

        /*
         * LOTREntityNPC.onDeath applies one point after Forge returns. For a
         * rewritten hired-formation kill, our credit bridge already applied
         * that same native point. A Mumak is not a LOTR NPC, so it needs its
         * full assigned value here.
         */
        int additionalPoints = victim instanceof LOTREntityNPC
                ? memberWeight - 1
                : memberWeight;
        if (additionalPoints <= 0) {
            data.setBoolean(WEIGHTED_CREDIT_APPLIED_KEY, true);
            return;
        }

        /*
         * Use the reflected remaining value when available so weighted kills
         * cannot overshoot the end of the invasion. Do not discard the kill
         * entirely if the private LOTR field cannot be resolved at runtime.
         */
        int remaining = getInvasionRemaining(spawner);

        boolean nativeNpcPointStillPending =
                victim instanceof LOTREntityNPC
                        && !data.getBoolean(
                        MumakilFormationCreditEventHandler
                                .INVASION_CREDIT_APPLIED_KEY
                );

        int creditedPoints = additionalPoints;

        if (remaining >= 0) {
            int availableAdditionalPoints = Math.max(
                    0,
                    remaining - (nativeNpcPointStillPending ? 1 : 0)
            );

            creditedPoints = Math.min(
                    additionalPoints,
                    availableAdditionalPoints
            );
        }

        data.setBoolean(
                WEIGHTED_CREDIT_APPLIED_KEY,
                true
        );

        for (int i = 0;
             i < creditedPoints && !spawner.isDead;
             ++i) {
            spawner.addPlayerKill(player);
        }
    }

    private static UUID getInvasionId(EntityLivingBase victim) {
        if (victim instanceof LOTREntityNPC) {
            LOTREntityNPC npc = (LOTREntityNPC)victim;
            return npc.isInvasionSpawned()
                    ? npc.getInvasionID()
                    : null;
        }
        if (victim instanceof LOTREntityMumakil) {
            return ((LOTREntityMumakil)victim)
                    .getMumakilInvasionId();
        }
        return null;
    }

    private static Field resolveInvasionRemainingField() {
        try {
            Field field = LOTREntityInvasionSpawner.class
                    .getDeclaredField("invasionRemaining");
            field.setAccessible(true);
            return field;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int getInvasionRemaining(
            LOTREntityInvasionSpawner spawner
    ) {
        if (INVASION_REMAINING_FIELD == null) {
            return -1;
        }
        try {
            return Math.max(
                    0,
                    INVASION_REMAINING_FIELD.getInt(spawner)
            );
        } catch (Exception ignored) {
            return -1;
        }
    }
}
