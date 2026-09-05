package com.lotrcharactercreation.trait;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.biome.BiomeGenBase;

import com.lotrcharactercreation.race.PlayerRace;

import lotr.common.world.biome.LOTRBiome;

public final class OrcEnvironmentService {

    private static final int NAUSEA_MIN_INTERVAL_TICKS = 1200;
    private static final int NAUSEA_MAX_INTERVAL_TICKS = 2400;
    private static final int NAUSEA_DURATION_TICKS = 200;
    private static final int NIGHT_VISION_DURATION_TICKS = 300;
    private static final int NIGHT_VISION_REFRESH_THRESHOLD_TICKS = 220;
    private static final int EXTRA_NATURAL_REGEN_INTERVAL_TICKS = 320;
    private static final Map<UUID, OrcEnvironmentState> PLAYER_STATES = new HashMap<UUID, OrcEnvironmentState>();

    private OrcEnvironmentService() {}

    public static boolean isDaylightExposed(EntityPlayer player) {
        if (player == null || player.worldObj == null) {
            return false;
        }

        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.boundingBox.minY);
        int z = MathHelper.floor_double(player.posZ);
        BiomeGenBase biome = player.worldObj.getBiomeGenForCoords(x, z);
        boolean exposed = player.worldObj.isDaytime() && player.worldObj.canBlockSeeTheSky(x, y, z);
        if (biome instanceof LOTRBiome && ((LOTRBiome) biome).canSpawnHostilesInDay()) {
            exposed = false;
        }
        return exposed;
    }

    public static void onTraitsRefreshed(EntityPlayerMP player, PlayerRace activeRace) {
        if (activeRace != PlayerRace.ORC) {
            removePlayerState(player, true);
            return;
        }

        OrcEnvironmentState state = PLAYER_STATES.get(player.getUniqueID());
        if (state == null) {
            state = new OrcEnvironmentState();
            PLAYER_STATES.put(player.getUniqueID(), state);
        }

        boolean exposed = isDaylightExposed(player);
        if (!state.initialized || state.daylightExposed != exposed) {
            state.initialized = true;
            state.daylightExposed = exposed;
            state.nauseaTicksRemaining = exposed ? nextNauseaInterval(player) : 0;
        }
        updateNightVision(player, state);
    }

    public static void updatePlayer(EntityPlayerMP player) {
        PlayerRace activeRace = RaceTraitService.getActiveRace(player);
        OrcEnvironmentState state = PLAYER_STATES.get(player.getUniqueID());
        if (activeRace != PlayerRace.ORC) {
            if (state != null || RaceTraitService.hasOrcEnvironmentSpeedModifier(player)) {
                RaceTraitService.refreshDerivedAttributes(player);
            }
            return;
        }

        boolean exposed = isDaylightExposed(player);
        if (state == null || !state.initialized || state.daylightExposed != exposed) {
            RaceTraitService.refreshDerivedAttributes(player);
            state = PLAYER_STATES.get(player.getUniqueID());
        }
        if (state == null) {
            return;
        }

        if (state.daylightExposed) {
            updateNausea(player, state);
        }
        updateNightVision(player, state);
        updateNaturalRegeneration(player, state);
    }

    public static void clearTransientState(EntityPlayerMP player) {
        removePlayerState(player, true);
    }

    private static void updateNausea(EntityPlayerMP player, OrcEnvironmentState state) {
        if (state.nauseaTicksRemaining > 0) {
            --state.nauseaTicksRemaining;
        }
        if (state.nauseaTicksRemaining > 0) {
            return;
        }

        PotionEffect currentNausea = player.getActivePotionEffect(Potion.confusion);
        if (currentNausea == null
            || currentNausea.getAmplifier() == 0 && currentNausea.getDuration() < NAUSEA_DURATION_TICKS) {
            player.addPotionEffect(new PotionEffect(Potion.confusion.id, NAUSEA_DURATION_TICKS, 0));
        }
        state.nauseaTicksRemaining = nextNauseaInterval(player);
    }

    private static int nextNauseaInterval(EntityPlayerMP player) {
        return NAUSEA_MIN_INTERVAL_TICKS + player.getRNG()
            .nextInt(NAUSEA_MAX_INTERVAL_TICKS - NAUSEA_MIN_INTERVAL_TICKS + 1);
    }

    private static void updateNightVision(EntityPlayerMP player, OrcEnvironmentState state) {
        PotionEffect nightVision = player.getActivePotionEffect(Potion.nightVision);
        if (state.ownsNightVision && !isClearlyRacialNightVision(nightVision)) {
            state.ownsNightVision = false;
        }

        if (state.daylightExposed) {
            if (state.ownsNightVision) {
                player.removePotionEffect(Potion.nightVision.id);
                state.ownsNightVision = false;
            }
            return;
        }

        if (nightVision == null) {
            player.addPotionEffect(new PotionEffect(Potion.nightVision.id, NIGHT_VISION_DURATION_TICKS, 0, true));
            state.ownsNightVision = true;
        } else if (state.ownsNightVision && nightVision.getDuration() <= NIGHT_VISION_REFRESH_THRESHOLD_TICKS) {
            player.addPotionEffect(new PotionEffect(Potion.nightVision.id, NIGHT_VISION_DURATION_TICKS, 0, true));
        }
    }

    private static boolean isClearlyRacialNightVision(PotionEffect effect) {
        return effect != null && effect.getAmplifier() == 0
            && effect.getIsAmbient()
            && effect.getDuration() <= NIGHT_VISION_DURATION_TICKS;
    }

    private static void updateNaturalRegeneration(EntityPlayerMP player, OrcEnvironmentState state) {
        boolean canRegenerate = player.isEntityAlive() && player.getFoodStats()
            .getFoodLevel() >= 18
            && player.shouldHeal()
            && player.worldObj.getGameRules()
                .getGameRuleBooleanValue("naturalRegeneration");
        if (!canRegenerate) {
            state.naturalRegenTicks = 0;
            return;
        }

        ++state.naturalRegenTicks;
        if (state.naturalRegenTicks >= EXTRA_NATURAL_REGEN_INTERVAL_TICKS) {
            player.heal(1.0F);
            player.addExhaustion(3.0F);
            state.naturalRegenTicks = 0;
        }
    }

    private static void removePlayerState(EntityPlayerMP player, boolean removeOwnedNightVision) {
        OrcEnvironmentState state = PLAYER_STATES.remove(player.getUniqueID());
        if (state != null && removeOwnedNightVision && state.ownsNightVision) {
            PotionEffect nightVision = player.getActivePotionEffect(Potion.nightVision);
            if (isClearlyRacialNightVision(nightVision)) {
                player.removePotionEffect(Potion.nightVision.id);
            }
        }
    }

    private static final class OrcEnvironmentState {

        private boolean initialized;
        private boolean daylightExposed;
        private boolean ownsNightVision;
        private int nauseaTicksRemaining;
        private int naturalRegenTicks;
    }
}
