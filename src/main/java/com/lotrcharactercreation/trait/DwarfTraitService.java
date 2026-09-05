package com.lotrcharactercreation.trait;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;

import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

public final class DwarfTraitService {

    public static final float MAX_STAMINA = 100.0F;
    public static final int MAX_FEAST = 20;
    public static final float STAMINA_RESTART_THRESHOLD = 20.0F;
    public static final float BASE_STAMINA_DRAIN_PER_TICK = 1.0F / 6.0F;
    public static final int FEAST_DIGESTION_INTERVAL_TICKS = 600;

    private static final UUID SPRINT_SPEED_MODIFIER_ID = UUID.fromString("8d991ca3-6e50-4d2a-a84b-1fd51d82356c");
    private static final String SPRINT_SPEED_MODIFIER_NAME = "LOTR Character Creation Dwarf stamina sprint";
    private static final double EMPTY_FEAST_SPRINT_SPEED_BONUS = 0.20D;
    private static final int SPRINT_SPEED_MODIFIER_OPERATION = 2;
    private static final int STAMINA_SYNC_INTERVAL_TICKS = 4;
    private static final Map<UUID, DwarfServerState> PLAYER_STATES = new HashMap<UUID, DwarfServerState>();

    private DwarfTraitService() {}

    public static boolean isActiveDwarf(EntityPlayerMP player) {
        return RaceTraitService.getActiveRace(player) == PlayerRace.DWARF;
    }

    public static void refresh(EntityPlayerMP player) {
        if (!isActiveDwarf(player)) {
            PLAYER_STATES.remove(player.getUniqueID());
            setSprintSpeedModifier(player, false);
            ModNetwork.sendDwarfTraitState(player, false, 0.0F, 0, false);
            return;
        }

        ensureInitialized(player);
        DwarfServerState state = getOrCreateState(player);
        float stamina = PlayerRaceData.getDwarfStamina(player);
        int feast = PlayerRaceData.getDwarfFeast(player);
        boolean exhausted = PlayerRaceData.isDwarfStaminaExhausted(player);
        boolean creative = player.capabilities.isCreativeMode;
        boolean sprintBonusActive = player.isSprinting() && (creative || !exhausted && stamina > 0.0F);
        setSprintSpeedModifier(player, sprintBonusActive, feast);
        synchronize(player, state, true);
    }

    public static void updatePlayer(EntityPlayerMP player) {
        if (!isActiveDwarf(player)) {
            boolean hadState = PLAYER_STATES.remove(player.getUniqueID()) != null;
            boolean hadModifier = hasSprintSpeedModifier(player);
            setSprintSpeedModifier(player, false);
            if (hadState || hadModifier) {
                ModNetwork.sendDwarfTraitState(player, false, 0.0F, 0, false);
            }
            return;
        }

        ensureInitialized(player);
        DwarfServerState state = PLAYER_STATES.get(player.getUniqueID());
        boolean forceSync = false;
        if (state == null) {
            state = getOrCreateState(player);
            forceSync = true;
        }

        if (!player.isEntityAlive()) {
            setSprintSpeedModifier(player, false);
            synchronize(player, state, forceSync);
            return;
        }

        processFeastDigestion(player, state);
        float stamina = PlayerRaceData.getDwarfStamina(player);
        int feast = PlayerRaceData.getDwarfFeast(player);
        boolean exhausted = PlayerRaceData.isDwarfStaminaExhausted(player);
        if (exhausted && stamina >= STAMINA_RESTART_THRESHOLD) {
            exhausted = false;
            PlayerRaceData.setDwarfStaminaExhausted(player, false);
        } else if (!exhausted && stamina <= 0.0F) {
            exhausted = true;
            PlayerRaceData.setDwarfStaminaExhausted(player, true);
        }

        if (player.capabilities.isCreativeMode) {
            setSprintSpeedModifier(player, player.isSprinting(), feast);
            synchronize(player, state, forceSync);
            return;
        }

        if (exhausted && stamina < STAMINA_RESTART_THRESHOLD && player.isSprinting()) {
            player.setSprinting(false);
        }

        float feastFactor = getFeastFactor(feast);
        if (player.isSprinting() && !exhausted && stamina > 0.0F) {
            setSprintSpeedModifier(player, true, feast);
            stamina = Math.max(0.0F, stamina - BASE_STAMINA_DRAIN_PER_TICK * feastFactor);
            PlayerRaceData.setDwarfStamina(player, stamina);
            if (stamina <= 0.0F) {
                PlayerRaceData.setDwarfStaminaExhausted(player, true);
                player.setSprinting(false);
                setSprintSpeedModifier(player, false);
            }
        } else {
            setSprintSpeedModifier(player, false);
            if (stamina < MAX_STAMINA) {
                stamina = Math.min(MAX_STAMINA, stamina + getStaminaRecoveryPerTick(feast));
                PlayerRaceData.setDwarfStamina(player, stamina);
                if (PlayerRaceData.isDwarfStaminaExhausted(player) && stamina >= STAMINA_RESTART_THRESHOLD) {
                    PlayerRaceData.setDwarfStaminaExhausted(player, false);
                }
            }
        }

        synchronize(player, state, forceSync);
    }

    public static boolean hasFeastCapacity(EntityPlayerMP player) {
        if (!isActiveDwarf(player)) {
            return false;
        }
        ensureInitialized(player);
        return PlayerRaceData.getDwarfFeast(player) < MAX_FEAST;
    }

    public static void addFeast(EntityPlayerMP player, int amount) {
        if (amount <= 0 || !isActiveDwarf(player)) {
            return;
        }

        ensureInitialized(player);
        int oldFeast = PlayerRaceData.getDwarfFeast(player);
        int newFeast = MathHelper.clamp_int(oldFeast + amount, 0, MAX_FEAST);
        if (newFeast != oldFeast) {
            PlayerRaceData.setDwarfFeast(player, newFeast);
            RaceTraitService.refreshKnockbackResistance(player);
            refreshSprintSpeedModifier(player, newFeast);
            synchronize(player, getOrCreateState(player), true);
        }
    }

    public static float getFeastFactor(int feast) {
        return 1.0F + MathHelper.clamp_int(feast, 0, MAX_FEAST) / (float) MAX_FEAST;
    }

    public static double getFeastKnockbackResistance(int feast) {
        return MathHelper.clamp_int(feast, 0, MAX_FEAST) / (double) MAX_FEAST;
    }

    public static double getSprintSpeedBonus(int feast) {
        double feastFraction = MathHelper.clamp_int(feast, 0, MAX_FEAST) / (double) MAX_FEAST;
        return EMPTY_FEAST_SPRINT_SPEED_BONUS * (1.0D - feastFraction);
    }

    public static float getStaminaRecoveryPerTick(int feast) {
        float recoverySeconds = 30.0F - MathHelper.clamp_int(feast, 0, MAX_FEAST);
        return MAX_STAMINA / (recoverySeconds * 20.0F);
    }

    public static void handleClone(EntityPlayerMP player, boolean wasDeath) {
        clearTransientState(player);
        if (wasDeath && isActiveDwarf(player)) {
            ensureInitialized(player);
            PlayerRaceData.setDwarfStamina(player, MAX_STAMINA);
            PlayerRaceData.setDwarfStaminaExhausted(player, false);
        }
    }

    public static void clearTransientState(EntityPlayerMP player) {
        PLAYER_STATES.remove(player.getUniqueID());
        setSprintSpeedModifier(player, false);
    }

    private static void ensureInitialized(EntityPlayerMP player) {
        if (!PlayerRaceData.hasDwarfResourceData(player)) {
            PlayerRaceData.setDwarfStamina(player, MAX_STAMINA);
            PlayerRaceData.setDwarfFeast(player, 0);
            PlayerRaceData.setDwarfStaminaExhausted(player, false);
            return;
        }

        float storedStamina = PlayerRaceData.getDwarfStamina(player);
        float stamina = MathHelper.clamp_float(storedStamina, 0.0F, MAX_STAMINA);
        int storedFeast = PlayerRaceData.getDwarfFeast(player);
        int feast = MathHelper.clamp_int(storedFeast, 0, MAX_FEAST);
        if (stamina != storedStamina) {
            PlayerRaceData.setDwarfStamina(player, stamina);
        }
        if (feast != storedFeast) {
            PlayerRaceData.setDwarfFeast(player, feast);
        }
    }

    private static void processFeastDigestion(EntityPlayerMP player, DwarfServerState state) {
        int feast = PlayerRaceData.getDwarfFeast(player);
        if (feast <= 0) {
            state.feastDigestionTicks = 0;
            return;
        }

        ++state.feastDigestionTicks;
        if (state.feastDigestionTicks >= FEAST_DIGESTION_INTERVAL_TICKS) {
            PlayerRaceData.setDwarfFeast(player, feast - 1);
            RaceTraitService.refreshKnockbackResistance(player);
            state.feastDigestionTicks = 0;
        }
    }

    private static DwarfServerState getOrCreateState(EntityPlayerMP player) {
        DwarfServerState state = PLAYER_STATES.get(player.getUniqueID());
        if (state == null) {
            state = new DwarfServerState();
            PLAYER_STATES.put(player.getUniqueID(), state);
        }
        return state;
    }

    private static void synchronize(EntityPlayerMP player, DwarfServerState state, boolean force) {
        float stamina = PlayerRaceData.getDwarfStamina(player);
        int feast = PlayerRaceData.getDwarfFeast(player);
        boolean exhausted = PlayerRaceData.isDwarfStaminaExhausted(player);
        boolean staminaChanged = Float.isNaN(state.lastSentStamina)
            || Math.abs(stamina - state.lastSentStamina) > 0.0001F;
        boolean staminaCadenceElapsed = player.ticksExisted - state.lastSyncTick >= STAMINA_SYNC_INTERVAL_TICKS;
        if (!force && feast == state.lastSentFeast
            && exhausted == state.lastSentExhausted
            && (!staminaChanged || !staminaCadenceElapsed)) {
            return;
        }

        ModNetwork.sendDwarfTraitState(player, true, stamina, feast, exhausted);
        state.lastSentStamina = stamina;
        state.lastSentFeast = feast;
        state.lastSentExhausted = exhausted;
        state.lastSyncTick = player.ticksExisted;
    }

    private static boolean hasSprintSpeedModifier(EntityPlayerMP player) {
        return player.getEntityAttribute(SharedMonsterAttributes.movementSpeed)
            .getModifier(SPRINT_SPEED_MODIFIER_ID) != null;
    }

    private static void refreshSprintSpeedModifier(EntityPlayerMP player, int feast) {
        float stamina = PlayerRaceData.getDwarfStamina(player);
        boolean exhausted = PlayerRaceData.isDwarfStaminaExhausted(player);
        boolean active = player.isSprinting() && (player.capabilities.isCreativeMode || !exhausted && stamina > 0.0F);
        setSprintSpeedModifier(player, active, feast);
    }

    private static void setSprintSpeedModifier(EntityPlayerMP player, boolean active) {
        setSprintSpeedModifier(player, active, 0);
    }

    private static void setSprintSpeedModifier(EntityPlayerMP player, boolean active, int feast) {
        IAttributeInstance movementSpeed = player.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        AttributeModifier oldModifier = movementSpeed.getModifier(SPRINT_SPEED_MODIFIER_ID);
        double desiredAmount = getSprintSpeedBonus(feast);
        boolean shouldApply = active && desiredAmount > 0.000001D;
        if (shouldApply && (oldModifier == null || oldModifier.getOperation() != SPRINT_SPEED_MODIFIER_OPERATION
            || Math.abs(oldModifier.getAmount() - desiredAmount) > 0.000001D)) {
            if (oldModifier != null) {
                movementSpeed.removeModifier(oldModifier);
            }
            movementSpeed.applyModifier(
                new AttributeModifier(
                    SPRINT_SPEED_MODIFIER_ID,
                    SPRINT_SPEED_MODIFIER_NAME,
                    desiredAmount,
                    SPRINT_SPEED_MODIFIER_OPERATION).setSaved(false));
        } else if (!shouldApply && oldModifier != null) {
            movementSpeed.removeModifier(oldModifier);
        }
    }

    private static final class DwarfServerState {

        private int feastDigestionTicks;
        private float lastSentStamina = Float.NaN;
        private int lastSentFeast = -1;
        private boolean lastSentExhausted;
        private int lastSyncTick;
    }
}
