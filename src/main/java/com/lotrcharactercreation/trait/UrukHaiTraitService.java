package com.lotrcharactercreation.trait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.oredict.OreDictionary;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;

public final class UrukHaiTraitService {

    public static final float MAX_RAGE = 100.0F;

    private static final float RAGE_PER_DAMAGE = 5.0F;
    private static final int RAGE_DECAY_DELAY_TICKS = 100;
    private static final float RAGE_DECAY_PER_TICK = 0.5F;
    private static final float HEAVY_BLOW_RAGE_COST = 50.0F;
    private static final float HEAVY_BLOW_DAMAGE_BONUS = 1.0F;
    private static final float HEAVY_BLOW_STRENGTH = 0.25F;
    private static final float CHOPPING_SPEED_MULTIPLIER = 1.25F;
    private static final int RAGE_SYNC_INTERVAL_TICKS = 4;
    private static final int LOG_WOOD_ORE_ID = OreDictionary.getOreID("logWood");
    private static final List<PendingHeavyBlow> PENDING_HEAVY_BLOWS = new ArrayList<PendingHeavyBlow>();
    private static final Map<UUID, UrukRageState> PLAYER_RAGE = new HashMap<UUID, UrukRageState>();

    private UrukHaiTraitService() {}

    public static void handleLivingHurt(LivingHurtEvent event) {
        tryHeavyBlow(event);
        gainRageFromDamage(event);
    }

    public static void refresh(EntityPlayerMP player) {
        if (!isActiveUrukHai(player)) {
            PLAYER_RAGE.remove(player.getUniqueID());
            ModNetwork.sendUrukRageState(player, false, 0.0F);
            return;
        }

        synchronize(player, getOrCreateState(player), true);
    }

    public static void prepareForLogin(EntityPlayerMP player) {
        clearTransientState(player);
    }

    public static void updatePlayer(EntityPlayerMP player) {
        if (!isActiveUrukHai(player)) {
            if (PLAYER_RAGE.remove(player.getUniqueID()) != null) {
                ModNetwork.sendUrukRageState(player, false, 0.0F);
            }
            return;
        }

        UrukRageState state = PLAYER_RAGE.get(player.getUniqueID());
        boolean forceSync = false;
        if (state == null) {
            state = getOrCreateState(player);
            forceSync = true;
        }

        if (state.decayDelayTicks > 0) {
            --state.decayDelayTicks;
        } else if (state.rage > 0.0F) {
            state.rage = Math.max(0.0F, state.rage - RAGE_DECAY_PER_TICK);
        }
        synchronize(player, state, forceSync);
    }

    public static void handleDeath(EntityLivingBase entity) {
        removeEntity(entity);
        if (!(entity instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) entity;
        if (!isActiveUrukHai(player)) {
            PLAYER_RAGE.remove(player.getUniqueID());
            return;
        }

        UrukRageState state = getOrCreateState(player);
        state.rage = 0.0F;
        state.decayDelayTicks = 0;
        synchronize(player, state, true);
    }

    public static void clearTransientState(EntityPlayerMP player) {
        removeEntity(player);
        PLAYER_RAGE.remove(player.getUniqueID());
    }

    private static void tryHeavyBlow(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote || event.ammount <= 0.0F) {
            return;
        }

        Entity attackerEntity = event.source.getEntity();
        if (!(attackerEntity instanceof EntityPlayerMP) || event.source.getSourceOfDamage() != attackerEntity
            || !isDirectMeleeDamage(event.source)) {
            return;
        }

        EntityPlayerMP attacker = (EntityPlayerMP) attackerEntity;
        EntityLivingBase target = event.entityLiving;
        UrukRageState state = PLAYER_RAGE.get(attacker.getUniqueID());
        if (!attacker.isEntityAlive() || !target.isEntityAlive()
            || RaceTraitService.getActiveRace(attacker) != PlayerRace.URUK_HAI
            || state == null
            || attacker.getRNG()
                .nextFloat() >= getHeavyBlowChance(state.rage)) {
            return;
        }

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

        event.ammount += HEAVY_BLOW_DAMAGE_BONUS;
        PENDING_HEAVY_BLOWS.add(new PendingHeavyBlow(attacker, target, directionX, directionZ));
        state.rage = Math.max(0.0F, state.rage - HEAVY_BLOW_RAGE_COST);
        synchronize(attacker, state, true);
    }

    private static void gainRageFromDamage(LivingHurtEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || event.entityLiving.worldObj.isRemote
            || event.ammount <= 0.0F) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        if (!isActiveUrukHai(player)) {
            return;
        }

        UrukRageState state = getOrCreateState(player);
        state.decayDelayTicks = RAGE_DECAY_DELAY_TICKS;
        float newRage = Math.min(MAX_RAGE, state.rage + event.ammount * RAGE_PER_DAMAGE);
        if (newRage != state.rage) {
            state.rage = newRage;
            synchronize(player, state, true);
        }
    }

    public static void processPendingHeavyBlows() {
        if (PENDING_HEAVY_BLOWS.isEmpty()) {
            return;
        }

        List<PendingHeavyBlow> pending = new ArrayList<PendingHeavyBlow>(PENDING_HEAVY_BLOWS);
        PENDING_HEAVY_BLOWS.clear();
        for (PendingHeavyBlow heavyBlow : pending) {
            heavyBlow.applyIfValid();
        }
    }

    public static void removeEntity(Entity entity) {
        if (entity == null || PENDING_HEAVY_BLOWS.isEmpty()) {
            return;
        }

        Iterator<PendingHeavyBlow> iterator = PENDING_HEAVY_BLOWS.iterator();
        while (iterator.hasNext()) {
            PendingHeavyBlow heavyBlow = iterator.next();
            if (heavyBlow.attacker == entity || heavyBlow.target == entity) {
                iterator.remove();
            }
        }
    }

    public static void applyChoppingBonus(PlayerEvent.BreakSpeed event) {
        ItemStack heldItem = event.entityPlayer.getCurrentEquippedItem();
        if (!RaceTraitService.hasUrukHaiChoppingTrait(event.entityPlayer) || heldItem == null
            || !isLog(event.block, event.metadata)
            || !heldItem.getItem()
                .getToolClasses(heldItem)
                .contains("axe")
            || heldItem.getItem()
                .getDigSpeed(heldItem, event.block, event.metadata) <= 1.0F) {
            return;
        }

        event.newSpeed *= CHOPPING_SPEED_MULTIPLIER;
    }

    private static float getHeavyBlowChance(float rage) {
        return MathHelper.clamp_float(rage / MAX_RAGE, 0.0F, 1.0F);
    }

    private static boolean isActiveUrukHai(EntityPlayerMP player) {
        return RaceTraitService.getActiveRace(player) == PlayerRace.URUK_HAI;
    }

    private static UrukRageState getOrCreateState(EntityPlayerMP player) {
        UrukRageState state = PLAYER_RAGE.get(player.getUniqueID());
        if (state == null) {
            state = new UrukRageState();
            PLAYER_RAGE.put(player.getUniqueID(), state);
        }
        return state;
    }

    private static void synchronize(EntityPlayerMP player, UrukRageState state, boolean force) {
        boolean rageChanged = Float.isNaN(state.lastSentRage) || Math.abs(state.rage - state.lastSentRage) > 0.0001F;
        boolean cadenceElapsed = player.ticksExisted - state.lastSyncTick >= RAGE_SYNC_INTERVAL_TICKS;
        if (!force && (!rageChanged || !cadenceElapsed)) {
            return;
        }

        ModNetwork.sendUrukRageState(player, true, state.rage);
        state.lastSentRage = state.rage;
        state.lastSyncTick = player.ticksExisted;
    }

    private static boolean isLog(Block block, int metadata) {
        if (block instanceof BlockLog) {
            return true;
        }

        Item blockItem = Item.getItemFromBlock(block);
        if (blockItem == null) {
            return false;
        }
        for (int oreId : OreDictionary.getOreIDs(new ItemStack(blockItem, 1, metadata))) {
            if (oreId == LOG_WOOD_ORE_ID) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectMeleeDamage(DamageSource source) {
        String damageType = source.getDamageType();
        return "player".equals(damageType) && !source.isProjectile()
            && !source.isExplosion()
            && !source.isMagicDamage()
            && !source.isFireDamage();
    }

    private static final class PendingHeavyBlow {

        private final EntityPlayerMP attacker;
        private final EntityLivingBase target;
        private final double directionX;
        private final double directionZ;

        private PendingHeavyBlow(EntityPlayerMP attacker, EntityLivingBase target, double directionX,
            double directionZ) {
            this.attacker = attacker;
            this.target = target;
            this.directionX = directionX;
            this.directionZ = directionZ;
        }

        private void applyIfValid() {
            if (!attacker.isEntityAlive() || !target.isEntityAlive()
                || attacker.worldObj != target.worldObj
                || attacker.worldObj.getEntityByID(attacker.getEntityId()) != attacker
                || RaceTraitService.getActiveRace(attacker) != PlayerRace.URUK_HAI) {
                return;
            }

            target.addVelocity(directionX * HEAVY_BLOW_STRENGTH, 0.0D, directionZ * HEAVY_BLOW_STRENGTH);
            target.velocityChanged = true;
        }
    }

    private static final class UrukRageState {

        private float rage;
        private int decayDelayTicks;
        private float lastSentRage = Float.NaN;
        private int lastSyncTick;
    }
}
