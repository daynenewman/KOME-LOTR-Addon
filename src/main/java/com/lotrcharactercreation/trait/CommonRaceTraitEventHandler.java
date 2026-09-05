package com.lotrcharactercreation.trait;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.block.BlockCrops;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.FoodStats;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerUseItemEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.LOTRConfig;
import lotr.common.LOTRMod;
import lotr.common.block.LOTRBlockMug;
import lotr.common.enchant.LOTREnchantment;
import lotr.common.enchant.LOTREnchantmentHelper;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.item.LOTRItemBottlePoison;
import lotr.common.item.LOTRItemMug;
import lotr.common.tileentity.LOTRTileEntityMug;

public final class CommonRaceTraitEventHandler {

    private static final float BANE_DAMAGE_BONUS = 4.0F;
    private static final int ROTTEN_FLESH_HUNGER_DURATION_TICKS = 600;
    private static final int MAGGOTY_BREAD_HUNGER_DURATION_TICKS = 400;
    private final Map<UUID, HungerToleranceSnapshot> pendingHungerTolerance = new HashMap<UUID, HungerToleranceSnapshot>();
    private final Map<UUID, DwarfFoodConsumptionContext> pendingDwarfFeastFood = new HashMap<UUID, DwarfFoodConsumptionContext>();
    private final Map<UUID, DraughtDamageContext> armedOrcDraughtDamage = new HashMap<UUID, DraughtDamageContext>();

    @SubscribeEvent
    public void entityConstructing(EntityEvent.EntityConstructing event) {
        if (event.entity instanceof EntityPlayerMP) {
            event.entity.registerExtendedProperties(
                "lotrcharactercreationRacialHealthLoad",
                new RacialHealthLoadProperty((EntityPlayerMP) event.entity));
        }
    }

    @SubscribeEvent
    public void playerCloned(PlayerEvent.Clone event) {
        if (event.entityPlayer instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
            ElfGrappleService.handleClone(event.original, player);
            ElfLightnessService.removeEntity(event.original);
            ElfLightnessService.removeEntity(player);
            UrukHaiTraitService.removeEntity(event.original);
            UrukHaiTraitService.removeEntity(player);
            UrukHaiTraitService.clearTransientState(player);
            clearTransientConsumptionState(player);
            HobbitStealthService.removePlayer(player);
            OrcEnvironmentService.clearTransientState(player);
            DwarfTraitService.handleClone(player, event.wasDeath);
            RaceTraitService.refreshDerivedAttributes(player);
            if (event.wasDeath) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    @SubscribeEvent
    public void playerLivingUpdate(LivingUpdateEvent event) {
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }

        if (event.entityLiving instanceof LOTREntityNPC) {
            HobbitStealthService.updateNpc((LOTREntityNPC) event.entityLiving);
            return;
        }
        if (!(event.entityLiving instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        if (RaceTraitService.getActiveRace(player) == PlayerRace.ELF && player.isPotionActive(Potion.poison)) {
            player.removePotionEffect(Potion.poison.id);
        }
        clearInactiveDwarfFoodContext(player);
        HobbitThrowableService.maintainServerUse(player);
        HobbitTraitService.applyAdditionalUnderwaterAirLoss(player);
        clearExpiredDraughtDamageContext(player);
        OrcEnvironmentService.updatePlayer(player);
        DwarfTraitService.updatePlayer(player);
        UrukHaiTraitService.updatePlayer(player);
    }

    @SubscribeEvent
    public void playerJumped(LivingJumpEvent event) {
        ElfLightnessService.applyJumpBonus(event);
    }

    @SubscribeEvent
    public void entityInteracted(EntityInteractEvent event) {
        if (event.entityPlayer instanceof EntityPlayerMP && !event.entityPlayer.worldObj.isRemote
            && ElfGrappleService.tryStart((EntityPlayerMP) event.entityPlayer, event.target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void entityJoinedWorld(EntityJoinWorldEvent event) {
        ElfGrappleService.handleEntityJoinedWorld(event.entity);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerInteracted(PlayerInteractEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.world.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        if (event.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR && event.useItem != Event.Result.DENY
            && HobbitThrowableService.beginServerUse(player)) {
            event.setCanceled(true);
        }
        tryStartDwarfFeastEating(player, event);
        armedOrcDraughtDamage.remove(player.getUniqueID());
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.useBlock == Event.Result.DENY
            || !isFoodTolerantRace(player)) {
            return;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        boolean usesBlock = !player.isSneaking() || heldItem == null
            || heldItem.getItem()
                .doesSneakBypassUse(event.world, event.x, event.y, event.z, player);
        if (!usesBlock || !(event.world.getBlock(event.x, event.y, event.z) instanceof LOTRBlockMug)) {
            return;
        }

        TileEntity tileEntity = event.world.getTileEntity(event.x, event.y, event.z);
        if (!(tileEntity instanceof LOTRTileEntityMug)) {
            return;
        }

        LOTRTileEntityMug mug = (LOTRTileEntityMug) tileEntity;
        if (mug.isEmpty() || LOTRItemMug.isItemEmptyDrink(heldItem)
            || heldItem != null && heldItem.getItem() instanceof LOTRItemBottlePoison && mug.canPoisonMug()) {
            return;
        }

        ItemStack mugItem = mug.getMugItem();
        ItemStack equivalentDrink = LOTRItemMug.getEquivalentDrink(mugItem);
        if (mugItem != null && equivalentDrink != null
            && equivalentDrink.getItem() == LOTRMod.mugOrcDraught
            && ((LOTRItemMug) equivalentDrink.getItem()).canPlayerDrink(player)) {
            armOrcDraughtDamage(player, mugItem);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void playerStartedUsingItem(PlayerUseItemEvent.Start event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        UUID playerId = player.getUniqueID();
        pendingHungerTolerance.remove(playerId);
        pendingDwarfFeastFood.remove(playerId);
        armedOrcDraughtDamage.remove(playerId);
        HobbitThrowableService.handleItemUseStarted(player, event.item);
        if (event.isCanceled()) {
            return;
        }
        if (event.item != null && isFoodTolerantRace(player) && isToleratedHungerFood(event.item.getItem())) {
            pendingHungerTolerance.put(playerId, HungerToleranceSnapshot.capture(player, event.item.getItem()));
        }
        if (event.item != null && event.item.getItem() instanceof ItemFood && DwarfTraitService.isActiveDwarf(player)) {
            pendingDwarfFeastFood.put(playerId, DwarfFoodConsumptionContext.capture(player, event.item));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void playerUsingItem(PlayerUseItemEvent.Tick event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        UUID playerId = player.getUniqueID();
        if (event.isCanceled()) {
            pendingDwarfFeastFood.remove(playerId);
            return;
        }
        DwarfFoodConsumptionContext dwarfFoodContext = pendingDwarfFeastFood.get(playerId);
        if (dwarfFoodContext != null && !dwarfFoodContext.matches(event.item)) {
            pendingDwarfFeastFood.remove(playerId);
        }
        if (event.item != null && event.item.getItem() == LOTRMod.mugOrcDraught
            && event.duration <= 1
            && isFoodTolerantRace(player)) {
            armOrcDraughtDamage(player, event.item);
        } else {
            armedOrcDraughtDamage.remove(playerId);
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void playerStoppedUsingItem(PlayerUseItemEvent.Stop event) {
        if (event.entityPlayer instanceof EntityPlayerMP && !event.entityPlayer.worldObj.isRemote) {
            EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
            pendingDwarfFeastFood.remove(player.getUniqueID());
            if (event.isCanceled()) {
                return;
            }
            HobbitThrowableService.releaseServerUse(player, event.item);
            clearTransientConsumptionState(player);
        }
    }

    @SubscribeEvent
    public void playerFinishedUsingItem(PlayerUseItemEvent.Finish event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer.worldObj.isRemote) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        HobbitThrowableService.clearServerUse(player);
        applyDwarfFeastOverflow(player, event.item);
        restorePreexistingHunger(player, event.item);
        armedOrcDraughtDamage.remove(player.getUniqueID());
        if (event.item == null || !(event.item.getItem() instanceof ItemFood)) {
            return;
        }
        if (RaceTraitService.getActiveRace(player) != PlayerRace.ELF) {
            return;
        }

        ItemFood food = (ItemFood) event.item.getItem();
        float normalSaturation = food.func_150905_g(event.item) * food.func_150906_h(event.item) * 2.0F;
        FoodStats foodStats = player.getFoodStats();
        NBTTagCompound foodStatsData = new NBTTagCompound();
        foodStats.writeNBT(foodStatsData);
        foodStatsData.setFloat(
            "foodSaturationLevel",
            Math.min(foodStats.getSaturationLevel() + normalSaturation, (float) foodStats.getFoodLevel()));
        foodStats.readNBT(foodStatsData);
    }

    @SubscribeEvent
    public void playerBreakSpeed(PlayerEvent.BreakSpeed event) {
        ItemStack heldItem = event.entityPlayer.getCurrentEquippedItem();
        if (RaceTraitService.hasDwarfMiningTrait(event.entityPlayer) && heldItem != null
            && heldItem.getItem()
                .getDigSpeed(heldItem, event.block, event.metadata) > 1.0F
            && ForgeHooks.canToolHarvestBlock(event.block, event.metadata, heldItem)) {
            event.newSpeed *= 1.25F;
        }
        UrukHaiTraitService.applyChoppingBonus(event);
        if (RaceTraitService.hasOrcEnvironmentTrait(event.entityPlayer)
            && OrcEnvironmentService.isDaylightExposed(event.entityPlayer)) {
            event.newSpeed *= 0.70F;
        }
    }

    @SubscribeEvent
    public void blockHarvestDrops(BlockEvent.HarvestDropsEvent event) {
        if (event.world.isRemote || event.isSilkTouching
            || !(event.block instanceof BlockCrops)
            || event.blockMetadata != 7
            || !(event.harvester instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.harvester;
        if (RaceTraitService.getActiveRace(player) != PlayerRace.HOBBIT) {
            return;
        }

        Item cropItem = event.block.getItemDropped(event.blockMetadata, player.getRNG(), 0);
        ItemStack normalCropDrop = findDropForItem(event, cropItem);
        if (normalCropDrop != null && player.getRNG()
            .nextFloat() < 0.50F) {
            ItemStack extraCrop = normalCropDrop.copy();
            extraCrop.stackSize = 1;
            event.drops.add(extraCrop);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void playerAttacked(LivingAttackEvent event) {
        if (!(event.entityLiving instanceof EntityPlayerMP) || event.entityLiving.worldObj.isRemote
            || event.source != DamageSource.magic) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.entityLiving;
        DraughtDamageContext context = armedOrcDraughtDamage.get(player.getUniqueID());
        if (context != null && context.armedTick == player.ticksExisted
            && Math.abs(event.ammount - context.expectedDamage) < 0.0001F) {
            armedOrcDraughtDamage.remove(player.getUniqueID());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void elfGrappleRetaliation(LivingAttackEvent event) {
        ElfGrappleService.cancelTargetRetaliation(event);
    }

    @SubscribeEvent
    public void playerHurt(LivingHurtEvent event) {
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }

        HobbitThrowableService.applyChargedProjectileDamage(event);

        if (event.entityLiving instanceof EntityPlayerMP) {
            EntityPlayerMP target = (EntityPlayerMP) event.entityLiving;
            PlayerRace targetRace = RaceTraitService.getActiveRace(target);
            if (targetRace != null) {
                applyPlayableBaneDamage(event, targetRace);
                if (targetRace == PlayerRace.HOBBIT && event.source == DamageSource.fall) {
                    event.ammount *= 0.50F;
                } else if (targetRace == PlayerRace.URUK_HAI && event.source.isProjectile()) {
                    event.ammount *= 0.75F;
                }
            }
        }

        applyOrcDaylightMeleePenalty(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void elfHurt(LivingHurtEvent event) {
        ElfLightnessService.queueShoveIfEligible(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void elfGrappleDamage(LivingHurtEvent event) {
        ElfGrappleService.applyGrappleDamageBonus(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void urukHaiDealtMeleeDamage(LivingHurtEvent event) {
        UrukHaiTraitService.handleLivingHurt(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void livingDeath(LivingDeathEvent event) {
        ElfGrappleService.handleDeath(event.entityLiving);
        ElfLightnessService.removeEntity(event.entityLiving);
        UrukHaiTraitService.handleDeath(event.entityLiving);
        if (event.entityLiving instanceof LOTREntityNPC) {
            HobbitStealthService.removeNpc((LOTREntityNPC) event.entityLiving);
        } else if (event.entityLiving instanceof EntityPlayerMP) {
            HobbitStealthService.removePlayer((EntityPlayerMP) event.entityLiving);
        }
        if (event.entityLiving.worldObj.isRemote) {
            return;
        }

        Entity immediateSource = event.source.getSourceOfDamage();
        if (!(immediateSource instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) immediateSource;
        if (RaceTraitService.getActiveRace(player) == PlayerRace.MAN && player.getCurrentEquippedItem() != null
            && player.getRNG()
                .nextFloat() < 0.25F) {
            LOTREnchantmentHelper.onKillEntity(player, event.entityLiving, event.source);
        }
    }

    public void clearTransientConsumptionState(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        pendingHungerTolerance.remove(playerId);
        pendingDwarfFeastFood.remove(playerId);
        armedOrcDraughtDamage.remove(playerId);
        HobbitThrowableService.clearServerUse(player);
    }

    @SubscribeEvent
    public void livingTargetChanged(LivingSetAttackTargetEvent event) {
        HobbitStealthService.handleTargetChange(event);
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        ElfGrappleService.handleWorldUnload(event.world);
    }

    private static final class RacialHealthLoadProperty implements IExtendedEntityProperties {

        private final EntityPlayerMP player;

        private RacialHealthLoadProperty(EntityPlayerMP player) {
            this.player = player;
        }

        @Override
        public void saveNBTData(NBTTagCompound compound) {}

        @Override
        public void loadNBTData(NBTTagCompound compound) {
            RaceTraitService.prepareMaxHealthForLoad(player);
        }

        @Override
        public void init(Entity entity, World world) {}
    }

    private static void tryStartDwarfFeastEating(EntityPlayerMP player, PlayerInteractEvent event) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR || event.useItem == Event.Result.DENY
            || LOTRConfig.canAlwaysEat
            || player.capabilities.isCreativeMode
            || player.ridingEntity != null
            || player.isUsingItem()
            || player.getFoodStats()
                .getFoodLevel() < 20
            || !DwarfTraitService.hasFeastCapacity(player)) {
            return;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (heldItem != null && heldItem.getItem() instanceof ItemFood && !player.canEat(false)) {
            player.setItemInUse(heldItem, heldItem.getMaxItemUseDuration());
        }
    }

    private void applyDwarfFeastOverflow(EntityPlayerMP player, ItemStack consumedItem) {
        DwarfFoodConsumptionContext context = pendingDwarfFeastFood.remove(player.getUniqueID());
        if (context == null || !context.matches(consumedItem) || !DwarfTraitService.isActiveDwarf(player)) {
            return;
        }

        ItemFood food = (ItemFood) consumedItem.getItem();
        int foodValue = Math.max(0, food.func_150905_g(consumedItem));
        int normalCapacity = Math.max(0, 20 - context.preFoodLevel);
        int overflow = Math.max(0, foodValue - normalCapacity);
        DwarfTraitService.addFeast(player, overflow);
    }

    private void clearInactiveDwarfFoodContext(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        DwarfFoodConsumptionContext context = pendingDwarfFeastFood.get(playerId);
        if (context != null && !player.isUsingItem()) {
            pendingDwarfFeastFood.remove(playerId);
        }
    }

    private void armOrcDraughtDamage(EntityPlayerMP player, ItemStack draught) {
        armedOrcDraughtDamage.put(
            player.getUniqueID(),
            new DraughtDamageContext(2.0F * LOTRItemMug.getStrength(draught), player.ticksExisted));
    }

    private void clearExpiredDraughtDamageContext(EntityPlayerMP player) {
        DraughtDamageContext context = armedOrcDraughtDamage.get(player.getUniqueID());
        if (context != null && context.armedTick != player.ticksExisted) {
            armedOrcDraughtDamage.remove(player.getUniqueID());
        }
    }

    private void restorePreexistingHunger(EntityPlayerMP player, ItemStack consumedItem) {
        HungerToleranceSnapshot snapshot = pendingHungerTolerance.remove(player.getUniqueID());
        if (snapshot == null || consumedItem == null
            || consumedItem.getItem() != snapshot.consumedItem
            || !isFoodTolerantRace(player)) {
            return;
        }

        PotionEffect currentHunger = player.getActivePotionEffect(Potion.hunger);
        int foodHungerDuration = getToleratedHungerDuration(snapshot.consumedItem);
        if (currentHunger == null || currentHunger.getAmplifier() != 0
            || currentHunger.getDuration() != foodHungerDuration) {
            return;
        }

        player.removePotionEffect(Potion.hunger.id);
        if (snapshot.preexistingHunger != null) {
            int elapsedTicks = Math.max(0, player.ticksExisted - snapshot.startTick);
            int remainingDuration = Math.max(0, snapshot.preexistingHunger.getDuration() - elapsedTicks);
            if (remainingDuration > 0) {
                PotionEffect restoredHunger = new PotionEffect(
                    Potion.hunger.id,
                    remainingDuration,
                    snapshot.preexistingHunger.getAmplifier(),
                    snapshot.preexistingHunger.getIsAmbient());
                restoredHunger.setCurativeItems(copyItemStacks(snapshot.preexistingHunger.getCurativeItems()));
                player.addPotionEffect(restoredHunger);
            }
        }
    }

    private static boolean isFoodTolerantRace(EntityPlayerMP player) {
        PlayerRace race = RaceTraitService.getActiveRace(player);
        return race == PlayerRace.ORC || race == PlayerRace.URUK_HAI;
    }

    private static boolean isToleratedHungerFood(Item item) {
        return item == Items.rotten_flesh || item == LOTRMod.maggotyBread;
    }

    private static int getToleratedHungerDuration(Item item) {
        if (item == Items.rotten_flesh) {
            return ROTTEN_FLESH_HUNGER_DURATION_TICKS;
        }
        if (item == LOTRMod.maggotyBread) {
            return MAGGOTY_BREAD_HUNGER_DURATION_TICKS;
        }
        return 0;
    }

    private static List<ItemStack> copyItemStacks(List<ItemStack> itemStacks) {
        List<ItemStack> copies = new ArrayList<ItemStack>(itemStacks.size());
        for (ItemStack itemStack : itemStacks) {
            copies.add(itemStack.copy());
        }
        return copies;
    }

    private static void applyOrcDaylightMeleePenalty(LivingHurtEvent event) {
        if (!isDirectPlayerMelee(event.source)) {
            return;
        }

        EntityPlayerMP attacker = (EntityPlayerMP) event.source.getEntity();
        if (RaceTraitService.getActiveRace(attacker) == PlayerRace.ORC
            && OrcEnvironmentService.isDaylightExposed(attacker)) {
            event.ammount = Math.max(0.0F, event.ammount - 1.0F);
        }
    }

    private static boolean isDirectPlayerMelee(DamageSource source) {
        Entity sourceEntity = source.getEntity();
        return "player".equals(source.getDamageType()) && sourceEntity instanceof EntityPlayerMP
            && source.getSourceOfDamage() == sourceEntity;
    }

    private static void applyPlayableBaneDamage(LivingHurtEvent event, PlayerRace targetRace) {
        LOTREnchantment bane = getBaneForRace(targetRace);
        if (bane == null || !isDirectPlayerMelee(event.source)) {
            return;
        }

        Entity attackerEntity = event.source.getEntity();
        ItemStack heldItem = ((EntityPlayerMP) attackerEntity).getCurrentEquippedItem();
        if (heldItem != null && LOTREnchantmentHelper.hasEnchant(heldItem, bane)) {
            event.ammount += BANE_DAMAGE_BONUS;
        }
    }

    private static LOTREnchantment getBaneForRace(PlayerRace race) {
        if (race == PlayerRace.ELF) {
            return LOTREnchantment.baneElf;
        }
        if (race == PlayerRace.DWARF) {
            return LOTREnchantment.baneDwarf;
        }
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return LOTREnchantment.baneOrc;
        }
        return null;
    }

    private static ItemStack findDropForItem(BlockEvent.HarvestDropsEvent event, Item item) {
        if (item == null) {
            return null;
        }
        for (ItemStack drop : event.drops) {
            if (drop != null && drop.getItem() == item && drop.stackSize > 0) {
                return drop;
            }
        }
        return null;
    }

    private static final class HungerToleranceSnapshot {

        private final Item consumedItem;
        private final PotionEffect preexistingHunger;
        private final int startTick;

        private HungerToleranceSnapshot(Item consumedItem, PotionEffect preexistingHunger, int startTick) {
            this.consumedItem = consumedItem;
            this.preexistingHunger = preexistingHunger;
            this.startTick = startTick;
        }

        private static HungerToleranceSnapshot capture(EntityPlayerMP player, Item consumedItem) {
            PotionEffect hunger = player.getActivePotionEffect(Potion.hunger);
            PotionEffect hungerCopy = null;
            if (hunger != null) {
                hungerCopy = new PotionEffect(
                    hunger.getPotionID(),
                    hunger.getDuration(),
                    hunger.getAmplifier(),
                    hunger.getIsAmbient());
                hungerCopy.setCurativeItems(copyItemStacks(hunger.getCurativeItems()));
            }
            return new HungerToleranceSnapshot(consumedItem, hungerCopy, player.ticksExisted);
        }
    }

    private static final class DraughtDamageContext {

        private final float expectedDamage;
        private final int armedTick;

        private DraughtDamageContext(float expectedDamage, int armedTick) {
            this.expectedDamage = expectedDamage;
            this.armedTick = armedTick;
        }
    }

    private static final class DwarfFoodConsumptionContext {

        private final Item consumedItem;
        private final int itemDamage;
        private final int preFoodLevel;

        private DwarfFoodConsumptionContext(Item consumedItem, int itemDamage, int preFoodLevel) {
            this.consumedItem = consumedItem;
            this.itemDamage = itemDamage;
            this.preFoodLevel = preFoodLevel;
        }

        private static DwarfFoodConsumptionContext capture(EntityPlayerMP player, ItemStack itemStack) {
            return new DwarfFoodConsumptionContext(
                itemStack.getItem(),
                itemStack.getItemDamage(),
                player.getFoodStats()
                    .getFoodLevel());
        }

        private boolean matches(ItemStack itemStack) {
            return itemStack != null && itemStack.getItem() == consumedItem && itemStack.getItemDamage() == itemDamage;
        }
    }
}
