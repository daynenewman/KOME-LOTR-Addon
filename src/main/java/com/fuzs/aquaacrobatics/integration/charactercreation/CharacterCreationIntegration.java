package com.fuzs.aquaacrobatics.integration.charactercreation;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.fuzs.aquaacrobatics.AquaAcrobatics;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

/**
 * Optional bridge to LOTR Character Creation. This class deliberately uses
 * reflection so Aqua/KOME keeps no hard runtime dependency on that mod.
 */
public final class CharacterCreationIntegration {

    public static final float VANILLA_WIDTH = 0.6F;
    public static final float VANILLA_HEIGHT = 1.8F;
    public static final float VANILLA_EYE_HEIGHT = 1.62F;

    private static final BodyProfile VANILLA = new BodyProfile(
        false, "VANILLA", VANILLA_WIDTH, VANILLA_HEIGHT, VANILLA_EYE_HEIGHT, 1.0F, false);

    private static final Map<Object, BodyProfile> PROFILE_CACHE =
        Collections.synchronizedMap(new IdentityHashMap<Object, BodyProfile>());
    /**
     * Client appearance synchronization can briefly return no race while changing pose,
     * changing race, respawning, or crossing dimensions. Remember the last confirmed
     * profile for the live player object so Aqua never falls back to a vanilla-sized
     * camera/hitbox for a single tick.
     */
    private static final Map<EntityPlayer, BodyProfile> PLAYER_PROFILE_CACHE =
        Collections.synchronizedMap(new WeakHashMap<EntityPlayer, BodyProfile>());

    private static boolean commonInitialized;
    private static boolean commonAvailable;
    private static Method serverGetRace;
    private static Method bodyForRace;
    private static Method bodyGetWidth;
    private static Method bodyGetHeight;
    private static Method bodyHasTargetEyeHeight;
    private static Method bodyGetTargetEyeHeight;
    private static Method bodyGetRenderScale;
    private static Method bodyIsPrototypeSizeEnabled;

    private static boolean clientInitialized;
    private static boolean clientAvailable;
    private static Method clientCacheGetInstance;
    private static Method clientCacheGetPlayer;
    private static Method clientAppearanceGetRace;
    private static Method clientEyeCameraGetInstance;
    private static Method clientEyeCameraGetRenderCompensation;

    private CharacterCreationIntegration() {}

    public static BodyProfile getBodyProfile(EntityPlayer player) {
        Object race = getRace(player);
        if (race == null) {
            BodyProfile lastKnown = player == null ? null : PLAYER_PROFILE_CACHE.get(player);
            return lastKnown != null ? lastKnown : VANILLA;
        }

        BodyProfile cached = PROFILE_CACHE.get(race);
        if (cached != null) {
            if (player != null) PLAYER_PROFILE_CACHE.put(player, cached);
            return cached;
        }

        try {
            Object body = bodyForRace.invoke(null, race);
            boolean prototypeSize = ((Boolean) bodyIsPrototypeSizeEnabled.invoke(body)).booleanValue();
            float width = prototypeSize
                ? ((Number) bodyGetWidth.invoke(body)).floatValue()
                : VANILLA_WIDTH;
            float height = prototypeSize
                ? ((Number) bodyGetHeight.invoke(body)).floatValue()
                : VANILLA_HEIGHT;
            boolean hasTargetEye = prototypeSize
                && ((Boolean) bodyHasTargetEyeHeight.invoke(body)).booleanValue();
            float standingEye = hasTargetEye
                ? ((Number) bodyGetTargetEyeHeight.invoke(body)).floatValue()
                : VANILLA_EYE_HEIGHT;
            float renderScale = ((Number) bodyGetRenderScale.invoke(body)).floatValue();
            String raceName = race instanceof Enum ? ((Enum<?>) race).name() : String.valueOf(race);

            BodyProfile profile = new BodyProfile(
                true, raceName, width, height, standingEye, renderScale, hasTargetEye);
            PROFILE_CACHE.put(race, profile);
            if (player != null) PLAYER_PROFILE_CACHE.put(player, profile);
            return profile;
        } catch (Exception e) {
            disableCommon("Unable to read LOTR Character Creation body definition", e);
            BodyProfile lastKnown = player == null ? null : PLAYER_PROFILE_CACHE.get(player);
            return lastKnown != null ? lastKnown : VANILLA;
        }
    }

    public static boolean hasCharacterCreationRace(EntityPlayer player) {
        return getBodyProfile(player).fromCharacterCreation;
    }

    public static EntitySize getStandingSize(EntityPlayer player) {
        BodyProfile profile = getBodyProfile(player);
        return EntitySize.flexible(profile.width, profile.height);
    }

    public static EntitySize getStandingSize(EntityPlayer player, boolean fixed) {
        BodyProfile profile = getBodyProfile(player);
        return new EntitySize(profile.width, profile.height, fixed);
    }

    public static float getStandingEyeHeight(EntityPlayer player) {
        return getBodyProfile(player).standingEyeHeight;
    }

    public static float getRenderScale(EntityPlayer player) {
        return getBodyProfile(player).renderScale;
    }

    public static float getHeightScale(EntityPlayer player) {
        return getBodyProfile(player).height / VANILLA_HEIGHT;
    }

    public static boolean hasPrototypeEyeHeight(EntityPlayer player) {
        return getBodyProfile(player).hasPrototypeEyeHeight;
    }

    /**
     * Character Creation computes the local racial eye height as:
     * defaultEyeHeight + racialStandingEyeHeight - 1.62.
     * Return the inverse-composed default value so its final result is the
     * pose eye height Aqua actually wants.
     */
    public static float composeDefaultEyeHeight(EntityPlayer player, float desiredPoseEyeHeight) {
        BodyProfile profile = getBodyProfile(player);
        return profile.hasPrototypeEyeHeight
            ? desiredPoseEyeHeight + VANILLA_EYE_HEIGHT - profile.standingEyeHeight
            : desiredPoseEyeHeight;
    }

    /**
     * Character Creation temporarily rewrites yOffset during rendering for races
     * with prototype eye heights. Aqua must keep the normal persistent yOffset for
     * those players: lowering it for the swimming pose both stacks with the external
     * render correction and can produce an out-of-range 1.7.10 movement stance.
     */
    public static boolean shouldPreserveVanillaYOffset(EntityPlayer player) {
        return getBodyProfile(player).hasPrototypeEyeHeight;
    }

    /**
     * Returns Character Creation's temporary local-player render yOffset delta.
     * This is zero outside its render-time adjustment window and for players that
     * do not use a prototype racial eye height.
     */
    public static double getLocalRenderYOffsetCompensation(EntityPlayer player) {
        BodyProfile profile = getBodyProfile(player);
        if (!profile.hasPrototypeEyeHeight || player == null || player.worldObj == null || !player.worldObj.isRemote) {
            return 0.0D;
        }
        if (!ensureClient()) return 0.0D;

        try {
            Object service = clientEyeCameraGetInstance.invoke(null);
            return ((Number) clientEyeCameraGetRenderCompensation.invoke(service, player)).doubleValue();
        } catch (Exception e) {
            disableClient("Unable to read LOTR Character Creation render camera compensation", e);
            return 0.0D;
        }
    }

    public static double getExcessLocalRenderYOffsetCompensation(EntityPlayer player) {
        BodyProfile profile = getBodyProfile(player);
        double actual = getLocalRenderYOffsetCompensation(player);
        if (Math.abs(actual) < 1.0E-6D) return 0.0D;

        // While swimming/crawling Aqua intentionally restores the native
        // 1.7.10 yOffset of 0.28. Character Creation's larger render-time
        // compensation is then necessary to keep its racial model attached
        // to the same physical feet as the canonical Aqua hitbox. Do not
        // subtract that pose component here.
        if (player instanceof IPlayerResizeable
            && ((IPlayerResizeable) player).getPose() == Pose.SWIMMING) {
            return 0.0D;
        }

        double standingCompensation = VANILLA_EYE_HEIGHT - profile.standingEyeHeight;
        return actual - standingCompensation;
    }

    private static Object getRace(EntityPlayer player) {
        if (player == null || !ensureCommon()) return null;
        try {
            if (player.worldObj != null && player.worldObj.isRemote) {
                if (!ensureClient()) return null;
                Object cache = clientCacheGetInstance.invoke(null);
                Object appearance = clientCacheGetPlayer.invoke(cache, player);
                return appearance == null ? null : clientAppearanceGetRace.invoke(appearance);
            }
            if (player instanceof EntityPlayerMP) {
                return serverGetRace.invoke(null, (EntityPlayerMP) player);
            }
        } catch (Exception e) {
            if (player.worldObj != null && player.worldObj.isRemote) {
                disableClient("Unable to read LOTR Character Creation client race", e);
            } else {
                disableCommon("Unable to read LOTR Character Creation server race", e);
            }
        }
        return null;
    }

    private static synchronized boolean ensureCommon() {
        if (commonInitialized) return commonAvailable;
        commonInitialized = true;
        try {
            ClassLoader loader = CharacterCreationIntegration.class.getClassLoader();
            Class<?> raceClass = loader.loadClass("com.lotrcharactercreation.race.PlayerRace");
            Class<?> raceDataClass = loader.loadClass("com.lotrcharactercreation.race.PlayerRaceData");
            Class<?> bodyClass = loader.loadClass("com.lotrcharactercreation.body.RaceBodyDefinition");

            serverGetRace = raceDataClass.getMethod("getRace", EntityPlayerMP.class);
            bodyForRace = bodyClass.getMethod("forRace", raceClass);
            bodyGetWidth = bodyClass.getMethod("getWidth");
            bodyGetHeight = bodyClass.getMethod("getHeight");
            bodyHasTargetEyeHeight = bodyClass.getMethod("hasTargetEyeHeight");
            bodyGetTargetEyeHeight = bodyClass.getMethod("getTargetEyeHeight");
            bodyGetRenderScale = bodyClass.getMethod("getRenderScale");
            bodyIsPrototypeSizeEnabled = bodyClass.getMethod("isPrototypeSizeEnabled");
            commonAvailable = true;
        } catch (ClassNotFoundException e) {
            commonAvailable = false;
        } catch (Exception e) {
            disableCommon("LOTR Character Creation compatibility API could not be initialized", e);
        }
        return commonAvailable;
    }

    private static synchronized boolean ensureClient() {
        if (clientInitialized) return clientAvailable;
        clientInitialized = true;
        if (!ensureCommon()) return false;
        try {
            ClassLoader loader = CharacterCreationIntegration.class.getClassLoader();
            Class<?> cacheClass = loader.loadClass(
                "com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache");
            Class<?> appearanceClass = loader.loadClass(
                "com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache$SynchronizedPlayerAppearance");
            Class<?> cameraServiceClass = loader.loadClass(
                "com.lotrcharactercreation.client.body.ClientPlayerEyeCameraService");

            clientCacheGetInstance = cacheClass.getMethod("getInstance");
            clientCacheGetPlayer = cacheClass.getMethod("get", EntityPlayer.class);
            clientAppearanceGetRace = appearanceClass.getMethod("getRace");
            clientEyeCameraGetInstance = cameraServiceClass.getMethod("getInstance");
            clientEyeCameraGetRenderCompensation = cameraServiceClass.getMethod(
                "getLocalPlayerRenderYOffsetCompensation", EntityPlayer.class);
            clientAvailable = true;
        } catch (Exception e) {
            disableClient("LOTR Character Creation client compatibility API could not be initialized", e);
        }
        return clientAvailable;
    }

    private static void disableCommon(String message, Exception e) {
        commonAvailable = false;
        AquaAcrobatics.LOGGER.warn(message + "; falling back to vanilla Aqua player dimensions", e);
    }

    private static void disableClient(String message, Exception e) {
        clientAvailable = false;
        AquaAcrobatics.LOGGER.warn(message + "; falling back to normal Aqua client presentation", e);
    }

    public static final class BodyProfile {
        public final boolean fromCharacterCreation;
        public final String raceName;
        public final float width;
        public final float height;
        public final float standingEyeHeight;
        public final float renderScale;
        public final boolean hasPrototypeEyeHeight;

        private BodyProfile(boolean fromCharacterCreation, String raceName, float width, float height,
            float standingEyeHeight, float renderScale, boolean hasPrototypeEyeHeight) {
            this.fromCharacterCreation = fromCharacterCreation;
            this.raceName = raceName;
            this.width = width;
            this.height = height;
            this.standingEyeHeight = standingEyeHeight;
            this.renderScale = renderScale;
            this.hasPrototypeEyeHeight = hasPrototypeEyeHeight;
        }
    }
}
