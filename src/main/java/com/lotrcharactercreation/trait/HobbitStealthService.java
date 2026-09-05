package com.lotrcharactercreation.trait;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;

import lotr.common.entity.ai.LOTREntityAINearestAttackableTargetBasic;
import lotr.common.entity.npc.LOTREntityNPC;

public final class HobbitStealthService {

    private static final double ACQUISITION_RANGE_FACTOR = 0.15D;
    private static final double DEFAULT_TARGET_RANGE = 16.0D;
    private static final Map<LOTREntityNPC, UUID> ENGAGED_TARGETS = new WeakHashMap<LOTREntityNPC, UUID>();

    private HobbitStealthService() {}

    public static void handleTargetChange(LivingSetAttackTargetEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof LOTREntityNPC)) {
            return;
        }

        LOTREntityNPC npc = (LOTREntityNPC) event.entityLiving;
        if (!(event.target instanceof EntityPlayerMP)) {
            ENGAGED_TARGETS.remove(npc);
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.target;
        if (!RaceTraitService.hasHobbitAdvancedTrait(player)) {
            ENGAGED_TARGETS.remove(npc);
            return;
        }

        UUID playerId = player.getUniqueID();
        if (playerId.equals(ENGAGED_TARGETS.get(npc))) {
            return;
        }

        if (!isOrdinaryHostileAcquisition()) {
            ENGAGED_TARGETS.put(npc, playerId);
            return;
        }

        if (!player.isSneaking() || isAcquisitionExempt(npc, player)) {
            ENGAGED_TARGETS.put(npc, playerId);
            return;
        }

        double normalRange = getNormalAcquisitionRange(npc);
        double stealthRange = normalRange * ACQUISITION_RANGE_FACTOR;
        if (npc.getDistanceSqToEntity(player) <= stealthRange * stealthRange) {
            ENGAGED_TARGETS.put(npc, playerId);
            return;
        }

        npc.setAttackTarget(null);
        ENGAGED_TARGETS.remove(npc);
    }

    public static void updateNpc(LOTREntityNPC npc) {
        UUID engagedPlayerId = ENGAGED_TARGETS.get(npc);
        if (!(npc.getAttackTarget() instanceof EntityPlayerMP)) {
            if (engagedPlayerId != null) {
                ENGAGED_TARGETS.remove(npc);
            }
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) npc.getAttackTarget();
        if (!player.isEntityAlive() || player.worldObj != npc.worldObj
            || !RaceTraitService.hasHobbitAdvancedTrait(player)) {
            ENGAGED_TARGETS.remove(npc);
            return;
        }

        if (engagedPlayerId == null) {
            ENGAGED_TARGETS.put(npc, player.getUniqueID());
        } else if (!engagedPlayerId.equals(player.getUniqueID())) {
            ENGAGED_TARGETS.remove(npc);
        }
    }

    public static void removeNpc(LOTREntityNPC npc) {
        ENGAGED_TARGETS.remove(npc);
    }

    public static void removePlayer(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        Iterator<Map.Entry<LOTREntityNPC, UUID>> iterator = ENGAGED_TARGETS.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            if (playerId.equals(
                iterator.next()
                    .getValue())) {
                iterator.remove();
            }
        }
    }

    private static boolean isAcquisitionExempt(LOTREntityNPC npc, EntityPlayerMP player) {
        return npc.getAITarget() == player || npc.hiredNPCInfo.isActive || npc.isInvasionSpawned();
    }

    private static boolean isOrdinaryHostileAcquisition() {
        String acquisitionClassName = LOTREntityAINearestAttackableTargetBasic.class.getName();
        for (StackTraceElement frame : Thread.currentThread()
            .getStackTrace()) {
            if (acquisitionClassName.equals(frame.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private static double getNormalAcquisitionRange(LOTREntityNPC npc) {
        IAttributeInstance followRange = npc.getEntityAttribute(SharedMonsterAttributes.followRange);
        return followRange == null ? DEFAULT_TARGET_RANGE : followRange.getAttributeValue();
    }
}
