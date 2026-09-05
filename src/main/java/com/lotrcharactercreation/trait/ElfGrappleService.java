package com.lotrcharactercreation.trait;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

import cpw.mods.fml.relauncher.ReflectionHelper;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityBalrog;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityNPCRideable;
import lotr.common.entity.npc.LOTREntitySauron;
import lotr.common.entity.npc.LOTRNPCMount;
import lotr.common.item.LOTRWeaponStats;

public final class ElfGrappleService {

    public static final int MIN_DURATION_TICKS = 80;
    public static final int MAX_DURATION_TICKS = 100;
    public static final int COOLDOWN_TICKS = 600;
    public static final float TIMEOUT_DAMAGE = 8.0F;
    public static final float ATTACK_DAMAGE_MULTIPLIER = 1.25F;
    private static final float MAXIMUM_TARGET_HEALTH_FRACTION = 0.25F;
    private static final Map<UUID, GrappleSession> ACTIVE_GRAPPLES = new HashMap<UUID, GrappleSession>();
    private static final Field ARROW_KNOCKBACK_STRENGTH = ReflectionHelper
        .findField(EntityArrow.class, "knockbackStrength", "field_70256_ap");

    private ElfGrappleService() {}

    public static boolean tryStart(EntityPlayerMP player, Entity interactedEntity) {
        if (!(interactedEntity instanceof LOTREntityNPC)
            || !isEligibleAtStart(player, (LOTREntityNPC) interactedEntity)) {
            return false;
        }

        LOTREntityNPC target = (LOTREntityNPC) interactedEntity;
        player.mountEntity(target);
        if (player.ridingEntity != target || target.riddenByEntity != player) {
            return false;
        }

        int duration = MIN_DURATION_TICKS + player.getRNG()
            .nextInt(MAX_DURATION_TICKS - MIN_DURATION_TICKS + 1);
        GrappleSession session = new GrappleSession(player, target, duration);
        ACTIVE_GRAPPLES.put(player.getUniqueID(), session);
        synchronizeToOwnerAndTrackers(session);
        return true;
    }

    public static void handleAttackRequest(EntityPlayerMP player) {
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (!isActiveSessionValid(session, player) || RaceTraitService.getActiveRace(player) != PlayerRace.ELF) {
            return;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (!ElfGrappleWeaponPolicy.isDagger(heldItem) || session.daggerAttackCooldownTicks > 0) {
            return;
        }

        LOTREntityNPC target = session.target;
        if (target.riddenByEntity != player) {
            return;
        }

        Entity capturedRider = target.riddenByEntity;
        session.daggerAttackCooldownTicks = Math.max(1, LOTRWeaponStats.getAttackTimePlayer(heldItem));
        target.riddenByEntity = null;
        try {
            player.attackTargetEntityWithCurrentItem(target);
        } finally {
            GrappleSession current = ACTIVE_GRAPPLES.get(player.getUniqueID());
            if (capturedRider == player && target.isEntityAlive()
                && current == session
                && current.target == target
                && player.ridingEntity == target
                && target.riddenByEntity == null) {
                target.riddenByEntity = player;
            }
        }
    }

    public static void applyGrappleDamageBonus(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.source.getEntity() instanceof EntityPlayerMP)
            || event.source.getSourceOfDamage() != event.source.getEntity()
            || !"player".equals(event.source.getDamageType())) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.source.getEntity();
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (RaceTraitService.getActiveRace(player) == PlayerRace.ELF && session != null
            && session.target == event.entityLiving
            && ElfGrappleWeaponPolicy.isDagger(player.getCurrentEquippedItem())) {
            event.ammount *= ATTACK_DAMAGE_MULTIPLIER;
        }
    }

    public static void handleEntityJoinedWorld(Entity entity) {
        if (!(entity instanceof EntityArrow) || entity.worldObj.isRemote) {
            return;
        }

        EntityArrow arrow = (EntityArrow) entity;
        if (!(arrow.shootingEntity instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) arrow.shootingEntity;
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (!isActiveSessionValid(session, player)) {
            return;
        }

        ItemStack heldItem = player.inventory.getCurrentItem();
        if (!ElfGrappleWeaponPolicy.isBow(heldItem)) {
            return;
        }
        applyGrappleArrowImpact(arrow, session);
    }

    public static void cancelTargetRetaliation(LivingAttackEvent event) {
        if (event.entityLiving.worldObj.isRemote || !(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (session != null && event.source.getEntity() == session.target) {
            event.setCanceled(true);
        }
    }

    public static void processServerTick() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        List<?> onlinePlayers = new ArrayList<Object>(server.getConfigurationManager().playerEntityList);
        for (Object value : onlinePlayers) {
            if (value instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP) value;
                processPendingLogoutCleanup(player);
                decrementCooldown(player);
            }
        }

        for (GrappleSession session : new ArrayList<GrappleSession>(ACTIVE_GRAPPLES.values())) {
            if (ACTIVE_GRAPPLES.get(session.playerId) != session) {
                continue;
            }
            if (!isConnected(server, session.player)) {
                endSafely(session);
                continue;
            }
            if (!session.target.isEntityAlive()) {
                endSafely(session);
                continue;
            }
            if (!isSessionIdentityValid(session, session.player)
                || RaceTraitService.getActiveRace(session.player) != PlayerRace.ELF) {
                endSafely(session);
                continue;
            }
            if (!hasSymmetricRiderPair(session)) {
                endSafely(session);
                continue;
            }
            if (!session.justStarted && session.player.isSneaking()) {
                endSafely(session);
                continue;
            }

            ElfGrappleAnchor.apply(session.player, session.target);
            if (session.daggerAttackCooldownTicks > 0) {
                --session.daggerAttackCooldownTicks;
            }
            if (session.justStarted) {
                session.justStarted = false;
            } else if (--session.remainingTicks <= 0) {
                endForTimeout(session);
            }
        }
    }

    public static void handleDeath(EntityLivingBase deadEntity) {
        if (deadEntity.worldObj.isRemote) {
            return;
        }

        for (GrappleSession session : new ArrayList<GrappleSession>(ACTIVE_GRAPPLES.values())) {
            if (session.player == deadEntity || session.target == deadEntity) {
                endSafely(session);
            }
        }
    }

    public static void handleLogout(EntityPlayerMP player) {
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (session != null) {
            PlayerRaceData.setElfGrappleCleanupPending(player, true);
            PlayerRaceData.setElfGrappleCooldownTicks(player, COOLDOWN_TICKS);
            ACTIVE_GRAPPLES.remove(player.getUniqueID());
            dismountIfStillRelated(session);
            synchronizeToOwnerAndTrackers(player);
        }
    }

    public static void handleDimensionChange(EntityPlayerMP player) {
        GrappleSession session = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (session != null) {
            endSafely(session);
        }
    }

    public static void handleClone(EntityPlayer original, EntityPlayerMP clone) {
        GrappleSession session = ACTIVE_GRAPPLES.get(original.getUniqueID());
        if (session != null) {
            endSafely(session);
            PlayerRaceData.setElfGrappleCooldownTicks(clone, COOLDOWN_TICKS);
        }
    }

    public static void handleWorldUnload(World world) {
        if (world.isRemote) {
            return;
        }
        for (GrappleSession session : new ArrayList<GrappleSession>(ACTIVE_GRAPPLES.values())) {
            if (session.player.worldObj == world || session.target.worldObj == world) {
                endSafely(session);
            }
        }
    }

    public static void synchronizeOwner(EntityPlayerMP player) {
        synchronize(player, player);
    }

    public static void synchronizeTo(EntityPlayerMP subject, EntityPlayerMP recipient) {
        synchronize(subject, recipient);
    }

    private static boolean isEligibleAtStart(EntityPlayerMP player, LOTREntityNPC target) {
        if (player == null || player.worldObj.isRemote
            || RaceTraitService.getActiveRace(player) != PlayerRace.ELF
            || !ElfGrappleWeaponPolicy.canStartGrapple(player.getCurrentEquippedItem())
            || ACTIVE_GRAPPLES.containsKey(player.getUniqueID())
            || PlayerRaceData.getElfGrappleCooldownTicks(player) != 0
            || player.ridingEntity != null
            || !target.isEntityAlive()
            || target.worldObj != player.worldObj
            || target.getHealth() > target.getMaxHealth() * MAXIMUM_TARGET_HEALTH_FRACTION
            || target.ridingEntity != null
            || target.riddenByEntity != null
            || isExcludedTarget(target)) {
            return false;
        }

        if (target.hiredNPCInfo.isActive && player.getUniqueID()
            .equals(target.hiredNPCInfo.getHiringPlayerUUID())) {
            return false;
        }
        return LOTRLevelData.getData(player)
            .getAlignment(LOTRMod.getNPCFaction(target)) < 0.0F && LOTRMod.canPlayerAttackEntity(player, target, false);
    }

    private static boolean isExcludedTarget(LOTREntityNPC target) {
        return target instanceof IBossDisplayData || target instanceof LOTREntitySauron
            || target instanceof LOTREntityBalrog
            || target instanceof LOTRNPCMount
            || target instanceof LOTREntityNPCRideable;
    }

    private static boolean isActiveSessionValid(GrappleSession session, EntityPlayerMP player) {
        return isSessionIdentityValid(session, player) && hasSymmetricRiderPair(session);
    }

    private static boolean isSessionIdentityValid(GrappleSession session, EntityPlayerMP player) {
        return session != null && session.player == player
            && session.playerId.equals(player.getUniqueID())
            && session.target.isEntityAlive()
            && session.target.worldObj == player.worldObj
            && player.worldObj.getEntityByID(session.targetEntityId) == session.target
            && session.targetId.equals(session.target.getUniqueID());
    }

    private static boolean hasSymmetricRiderPair(GrappleSession session) {
        return session.player.ridingEntity == session.target && session.target.riddenByEntity == session.player;
    }

    private static void decrementCooldown(EntityPlayerMP player) {
        int cooldown = PlayerRaceData.getElfGrappleCooldownTicks(player);
        if (cooldown <= 0) {
            return;
        }
        int updatedCooldown = cooldown - 1;
        PlayerRaceData.setElfGrappleCooldownTicks(player, updatedCooldown);
        if (updatedCooldown == 0) {
            synchronizeOwner(player);
        }
    }

    private static void processPendingLogoutCleanup(EntityPlayerMP player) {
        if (!PlayerRaceData.isElfGrappleCleanupPending(player)) {
            return;
        }

        GrappleSession staleSession = ACTIVE_GRAPPLES.get(player.getUniqueID());
        if (staleSession != null) {
            ACTIVE_GRAPPLES.remove(player.getUniqueID());
            dismountIfStillRelated(staleSession);
        }

        Entity restoredMount = player.ridingEntity;
        if (restoredMount != null) {
            player.mountEntity(null);
            if (restoredMount.riddenByEntity == player) {
                restoredMount.riddenByEntity = null;
            }
        }

        for (Entity entity : new ArrayList<Entity>(player.worldObj.loadedEntityList)) {
            if (entity.riddenByEntity == player) {
                entity.riddenByEntity = null;
            }
        }

        if (player.ridingEntity == null && !hasReverseRidingReference(player)) {
            PlayerRaceData.setElfGrappleCleanupPending(player, false);
            synchronizeOwner(player);
        }
    }

    private static boolean hasReverseRidingReference(EntityPlayerMP player) {
        for (Entity entity : new ArrayList<Entity>(player.worldObj.loadedEntityList)) {
            if (entity.riddenByEntity == player) {
                return true;
            }
        }
        return false;
    }

    private static void endSafely(GrappleSession session) {
        if (!removeSession(session)) {
            return;
        }
        dismountIfStillRelated(session);
        PlayerRaceData.setElfGrappleCooldownTicks(session.player, COOLDOWN_TICKS);
        synchronizeToOwnerAndTrackers(session.player);
    }

    private static void endForTimeout(GrappleSession session) {
        if (!removeSession(session)) {
            return;
        }
        dismountIfStillRelated(session);
        if (session.player.isEntityAlive() && session.target.isEntityAlive()
            && session.player.worldObj == session.target.worldObj) {
            DamageSource timeoutDamage = new EntityDamageSource("mob", session.target).setDamageBypassesArmor();
            session.player.attackEntityFrom(timeoutDamage, TIMEOUT_DAMAGE);
        }
        PlayerRaceData.setElfGrappleCooldownTicks(session.player, COOLDOWN_TICKS);
        synchronizeToOwnerAndTrackers(session.player);
    }

    private static boolean removeSession(GrappleSession session) {
        if (ACTIVE_GRAPPLES.get(session.playerId) != session || ACTIVE_GRAPPLES.remove(session.playerId) != session) {
            return false;
        }
        return true;
    }

    private static void applyGrappleArrowImpact(EntityArrow arrow, GrappleSession session) {
        LOTREntityNPC target = session.target;
        MovingObjectPosition targetHit = findAimedTargetHit(arrow, target);
        if (targetHit == null) {
            return;
        }

        Vec3 start = Vec3.createVectorHelper(arrow.posX, arrow.posY, arrow.posZ);
        MovingObjectPosition blockHit = arrow.worldObj.func_147447_a(start, targetHit.hitVec, false, true, false);
        if (blockHit != null && start.distanceTo(blockHit.hitVec) < start.distanceTo(targetHit.hitVec)) {
            return;
        }

        double horizontalSpeed = MathHelper.sqrt_double(arrow.motionX * arrow.motionX + arrow.motionZ * arrow.motionZ);
        int damage = MathHelper.ceiling_double_int(horizontalSpeed * arrow.getDamage());
        if (arrow.getIsCritical()) {
            damage += session.player.getRNG()
                .nextInt(damage / 2 + 2);
        }
        if (arrow.isBurning()) {
            target.setFire(5);
        }

        Entity capturedRider = target.riddenByEntity;
        boolean damaged;
        target.riddenByEntity = null;
        try {
            damaged = target.attackEntityFrom(DamageSource.causeArrowDamage(arrow, session.player), damage);
        } finally {
            GrappleSession current = ACTIVE_GRAPPLES.get(session.playerId);
            if (capturedRider == session.player && target.isEntityAlive()
                && current == session
                && session.player.ridingEntity == target
                && target.riddenByEntity == null) {
                target.riddenByEntity = session.player;
            }
        }

        if (!damaged) {
            return;
        }
        target.setArrowCountInEntity(target.getArrowCountInEntity() + 1);
        int knockbackStrength = getArrowKnockbackStrength(arrow);
        if (knockbackStrength > 0 && horizontalSpeed > 0.0D) {
            target.addVelocity(
                arrow.motionX * knockbackStrength * 0.6D / horizontalSpeed,
                0.1D,
                arrow.motionZ * knockbackStrength * 0.6D / horizontalSpeed);
        }
        EnchantmentHelper.func_151384_a(target, session.player);
        EnchantmentHelper.func_151385_b(session.player, target);
        arrow.playSound(
            "random.bowhit",
            1.0F,
            1.2F / (session.player.getRNG()
                .nextFloat() * 0.2F + 0.9F));
        arrow.setDead();
    }

    private static MovingObjectPosition findAimedTargetHit(EntityArrow arrow, LOTREntityNPC target) {
        double targetCenterY = (target.boundingBox.minY + target.boundingBox.maxY) * 0.5D;
        double directionDotProduct = (target.posX - arrow.posX) * arrow.motionX
            + (targetCenterY - arrow.posY) * arrow.motionY
            + (target.posZ - arrow.posZ) * arrow.motionZ;
        if (directionDotProduct <= 0.0D) {
            return null;
        }

        double speed = MathHelper
            .sqrt_double(arrow.motionX * arrow.motionX + arrow.motionY * arrow.motionY + arrow.motionZ * arrow.motionZ);
        if (speed <= 0.0D) {
            return null;
        }

        Vec3 start = Vec3.createVectorHelper(arrow.posX, arrow.posY, arrow.posZ);
        double rayLength = Math.max(4.0D, arrow.getDistanceToEntity(target) + target.width + 0.5D);
        Vec3 end = Vec3.createVectorHelper(
            arrow.posX + arrow.motionX / speed * rayLength,
            arrow.posY + arrow.motionY / speed * rayLength,
            arrow.posZ + arrow.motionZ / speed * rayLength);
        AxisAlignedBB targetBox = target.boundingBox.expand(0.3D, 0.3D, 0.3D);
        MovingObjectPosition targetHit = targetBox.calculateIntercept(start, end);
        if (targetHit != null) {
            return targetHit;
        }
        return targetBox.isVecInside(start) ? new MovingObjectPosition(target, start) : null;
    }

    private static int getArrowKnockbackStrength(EntityArrow arrow) {
        try {
            return ARROW_KNOCKBACK_STRENGTH.getInt(arrow);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to read grapple-arrow knockback strength", exception);
        }
    }

    private static void dismountIfStillRelated(GrappleSession session) {
        if (session.player.ridingEntity == session.target && session.target.riddenByEntity == session.player) {
            session.player.mountEntity(null);
        }
        if (session.player.ridingEntity == session.target) {
            session.player.ridingEntity = null;
        }
        if (session.target.riddenByEntity == session.player) {
            session.target.riddenByEntity = null;
        }
    }

    private static void synchronizeToOwnerAndTrackers(GrappleSession session) {
        synchronizeToOwnerAndTrackers(session.player);
    }

    private static void synchronizeToOwnerAndTrackers(EntityPlayerMP subject) {
        MinecraftServer server = MinecraftServer.getServer();
        if (isConnected(server, subject)) {
            synchronize(subject, subject);
        }
        if (!(subject.worldObj instanceof WorldServer)) {
            return;
        }
        for (EntityPlayer trackingPlayer : ((WorldServer) subject.worldObj).getEntityTracker()
            .getTrackingPlayers(subject)) {
            if (trackingPlayer instanceof EntityPlayerMP && trackingPlayer != subject) {
                synchronize(subject, (EntityPlayerMP) trackingPlayer);
            }
        }
    }

    private static void synchronize(EntityPlayerMP subject, EntityPlayerMP recipient) {
        GrappleSession session = ACTIVE_GRAPPLES.get(subject.getUniqueID());
        boolean active = session != null && isActiveSessionValid(session, subject);
        int targetEntityId = active ? session.targetEntityId : -1;
        boolean ready = !active && PlayerRaceData.getElfGrappleCooldownTicks(subject) == 0;
        ModNetwork.sendElfGrappleState(subject, recipient, targetEntityId, active, ready);
    }

    private static boolean isConnected(MinecraftServer server, EntityPlayerMP player) {
        return server != null && server.getConfigurationManager().playerEntityList.contains(player);
    }

    private static final class GrappleSession {

        private final EntityPlayerMP player;
        private final UUID playerId;
        private final LOTREntityNPC target;
        private final UUID targetId;
        private final int targetEntityId;
        private int remainingTicks;
        private int daggerAttackCooldownTicks;
        private boolean justStarted = true;

        private GrappleSession(EntityPlayerMP player, LOTREntityNPC target, int remainingTicks) {
            this.player = player;
            playerId = player.getUniqueID();
            this.target = target;
            targetId = target.getUniqueID();
            targetEntityId = target.getEntityId();
            this.remainingTicks = remainingTicks;
        }
    }

}
