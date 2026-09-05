package com.lotrcharactercreation.trait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.lotrcharactercreation.race.PlayerRace;

public final class ElfLightnessService {

    private static final float EXTRA_SHOVE_CHANCE = 0.25F;
    private static final float EXTRA_SHOVE_STRENGTH = 0.25F;
    private static final double JUMP_VERTICAL_BONUS = 0.05D;
    private static final List<PendingElfShove> PENDING_SHOVES = new ArrayList<PendingElfShove>();

    private ElfLightnessService() {}

    public static void applyJumpBonus(LivingJumpEvent event) {
        if (event.entityLiving instanceof EntityPlayer && event.entityLiving.isEntityAlive()
            && RaceTraitService.hasElfJumpTrait((EntityPlayer) event.entityLiving)) {
            event.entityLiving.motionY += JUMP_VERTICAL_BONUS;
        }
    }

    public static void queueShoveIfEligible(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || event.entityLiving.worldObj.isRemote
            || event.ammount <= 0.0F) {
            return;
        }

        EntityPlayerMP target = (EntityPlayerMP) event.entityLiving;
        if (!target.isEntityAlive() || RaceTraitService.getActiveRace(target) != PlayerRace.ELF) {
            return;
        }

        Entity attackerEntity = event.source.getEntity();
        if (!(attackerEntity instanceof EntityLivingBase) || event.source.getSourceOfDamage() != attackerEntity
            || !isDirectMeleeDamage(event.source)) {
            return;
        }

        if (target.getRNG()
            .nextFloat() >= EXTRA_SHOVE_CHANCE) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) attackerEntity;
        double directionX = target.posX - attacker.posX;
        double directionZ = target.posZ - attacker.posZ;
        double directionLength = MathHelper.sqrt_double(directionX * directionX + directionZ * directionZ);
        if (directionLength < 1.0E-4D) {
            float attackerYaw = attacker.rotationYaw * (float) Math.PI / 180.0F;
            directionX = -MathHelper.sin(attackerYaw);
            directionZ = MathHelper.cos(attackerYaw);
        } else {
            directionX /= directionLength;
            directionZ /= directionLength;
        }

        PENDING_SHOVES.add(new PendingElfShove(target, attacker, directionX, directionZ));
    }

    public static void processPendingShoves() {
        if (PENDING_SHOVES.isEmpty()) {
            return;
        }

        List<PendingElfShove> pending = new ArrayList<PendingElfShove>(PENDING_SHOVES);
        PENDING_SHOVES.clear();
        for (PendingElfShove shove : pending) {
            shove.applyIfValid();
        }
    }

    public static void removeEntity(Entity entity) {
        if (entity == null || PENDING_SHOVES.isEmpty()) {
            return;
        }

        Iterator<PendingElfShove> iterator = PENDING_SHOVES.iterator();
        while (iterator.hasNext()) {
            PendingElfShove shove = iterator.next();
            if (shove.target == entity || shove.attacker == entity) {
                iterator.remove();
            }
        }
    }

    private static boolean isDirectMeleeDamage(DamageSource source) {
        String damageType = source.getDamageType();
        return ("player".equals(damageType) || "mob".equals(damageType)) && !source.isProjectile()
            && !source.isExplosion()
            && !source.isMagicDamage()
            && !source.isFireDamage();
    }

    private static final class PendingElfShove {

        private final EntityPlayerMP target;
        private final EntityLivingBase attacker;
        private final double directionX;
        private final double directionZ;

        private PendingElfShove(EntityPlayerMP target, EntityLivingBase attacker, double directionX,
            double directionZ) {
            this.target = target;
            this.attacker = attacker;
            this.directionX = directionX;
            this.directionZ = directionZ;
        }

        private void applyIfValid() {
            if (!target.isEntityAlive() || !attacker.isEntityAlive()
                || target.worldObj != attacker.worldObj
                || target.worldObj.getEntityByID(attacker.getEntityId()) != attacker
                || RaceTraitService.getActiveRace(target) != PlayerRace.ELF) {
                return;
            }

            target.addVelocity(directionX * EXTRA_SHOVE_STRENGTH, 0.0D, directionZ * EXTRA_SHOVE_STRENGTH);
            target.velocityChanged = true;
        }
    }
}
