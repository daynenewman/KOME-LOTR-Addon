package com.lotrcharactercreation.client.render;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.gui.LOTRGuiMap;

@SideOnly(Side.CLIENT)
public final class LOTRMapReflectionAccessor {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final LOTRMapReflectionAccessor INSTANCE = new LOTRMapReflectionAccessor();

    private Field playerLocationsField;
    private Field playerLocationProfileField;
    private Field playerLocationPosXField;
    private Field playerLocationPosZField;
    private Field mapXMinField;
    private Field mapXMaxField;
    private Field mapYMinField;
    private Field mapYMaxField;
    private Field hasOverlayField;
    private Field loadingConquestGridField;
    private Method transformCoordsMethod;
    private Method isMiddleEarthMethod;
    private volatile boolean available;
    private boolean failureLogged;

    private LOTRMapReflectionAccessor() {
        try {
            Class<?> mapClass = LOTRGuiMap.class;
            Class<?> playerLocationClass = Class.forName("lotr.client.gui.LOTRGuiMap$PlayerLocationInfo");
            playerLocationsField = accessibleField(mapClass, "playerLocations");
            playerLocationProfileField = accessibleField(playerLocationClass, "profile");
            playerLocationPosXField = accessibleField(playerLocationClass, "posX");
            playerLocationPosZField = accessibleField(playerLocationClass, "posZ");
            mapXMinField = accessibleField(mapClass, "mapXMin");
            mapXMaxField = accessibleField(mapClass, "mapXMax");
            mapYMinField = accessibleField(mapClass, "mapYMin");
            mapYMaxField = accessibleField(mapClass, "mapYMax");
            hasOverlayField = accessibleField(mapClass, "hasOverlay");
            loadingConquestGridField = accessibleField(mapClass, "loadingConquestGrid");
            transformCoordsMethod = accessibleMethod(mapClass, "transformCoords", double.class, double.class);
            isMiddleEarthMethod = accessibleMethod(mapClass, "isMiddleEarth");
            available = true;
        } catch (Exception exception) {
            disable("initialize LOTRGuiMap reflection", exception);
        }
    }

    public static LOTRMapReflectionAccessor getInstance() {
        return INSTANCE;
    }

    public boolean isAvailable() {
        return available;
    }

    public MapSnapshot capture(LOTRGuiMap mapGui) {
        if (!available || mapGui == null) {
            return null;
        }

        try {
            if (hasOverlayField.getBoolean(mapGui) || loadingConquestGridField.getBoolean(mapGui)) {
                return null;
            }

            Object rawLocations = playerLocationsField.get(null);
            if (!(rawLocations instanceof Map)) {
                throw new IllegalStateException("LOTRGuiMap.playerLocations is not a Map");
            }

            Map<UUID, PlayerLocation> locations = new HashMap<UUID, PlayerLocation>();
            for (Object rawEntry : ((Map<?, ?>) rawLocations).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) rawEntry;
                Object rawLocation = entry.getValue();
                Object rawProfile = playerLocationProfileField.get(rawLocation);
                if (!(entry.getKey() instanceof UUID) || !(rawProfile instanceof GameProfile)) {
                    continue;
                }

                locations.put(
                    (UUID) entry.getKey(),
                    new PlayerLocation(
                        (GameProfile) rawProfile,
                        playerLocationPosXField.getDouble(rawLocation),
                        playerLocationPosZField.getDouble(rawLocation)));
            }

            boolean middleEarth = ((Boolean) isMiddleEarthMethod.invoke(mapGui)).booleanValue();
            return new MapSnapshot(
                Collections.unmodifiableMap(locations),
                middleEarth,
                mapXMinField.getInt(null),
                mapXMaxField.getInt(null),
                mapYMinField.getInt(null),
                mapYMaxField.getInt(null));
        } catch (Exception exception) {
            disable("read LOTRGuiMap state", unwrap(exception));
            return null;
        }
    }

    public ScreenPosition transformPlayerPosition(LOTRGuiMap mapGui, MapSnapshot snapshot, double worldX,
        double worldZ) {
        if (!available || mapGui == null || snapshot == null) {
            return null;
        }

        try {
            float[] transformed = (float[]) transformCoordsMethod
                .invoke(mapGui, Double.valueOf(worldX), Double.valueOf(worldZ));
            if (transformed == null || transformed.length < 2) {
                throw new IllegalStateException("LOTRGuiMap.transformCoords returned invalid coordinates");
            }

            int iconBorder = 5;
            double playerX = Math.round(transformed[0]);
            double playerY = Math.round(transformed[1]);
            playerX = Math.max(snapshot.mapXMin + iconBorder, playerX);
            playerX = Math.min(snapshot.mapXMax - iconBorder - 1, playerX);
            playerY = Math.max(snapshot.mapYMin + iconBorder, playerY);
            playerY = Math.min(snapshot.mapYMax - iconBorder - 1, playerY);
            return new ScreenPosition(playerX, playerY);
        } catch (Exception exception) {
            disable("transform LOTRGuiMap player coordinates", unwrap(exception));
            return null;
        }
    }

    private static Field accessibleField(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method accessibleMethod(Class<?> owner, String name, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        Method method = owner.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Throwable unwrap(Exception exception) {
        return exception instanceof InvocationTargetException && exception.getCause() != null ? exception.getCause()
            : exception;
    }

    private synchronized void disable(String action, Throwable cause) {
        available = false;
        if (failureLogged) {
            return;
        }

        failureLogged = true;
        LOGGER.warn(
            "Disabling LOTR map appearance overlay: could not " + action
                + " ("
                + cause.getClass()
                    .getSimpleName()
                + ": "
                + cause.getMessage()
                + ")");
    }

    public static final class MapSnapshot {

        private final Map<UUID, PlayerLocation> playerLocations;
        private final boolean middleEarth;
        private final int mapXMin;
        private final int mapXMax;
        private final int mapYMin;
        private final int mapYMax;

        private MapSnapshot(Map<UUID, PlayerLocation> playerLocations, boolean middleEarth, int mapXMin, int mapXMax,
            int mapYMin, int mapYMax) {
            this.playerLocations = playerLocations;
            this.middleEarth = middleEarth;
            this.mapXMin = mapXMin;
            this.mapXMax = mapXMax;
            this.mapYMin = mapYMin;
            this.mapYMax = mapYMax;
        }

        public Map<UUID, PlayerLocation> getPlayerLocations() {
            return playerLocations;
        }

        public boolean isMiddleEarth() {
            return middleEarth;
        }
    }

    public static final class PlayerLocation {

        private final GameProfile profile;
        private final double posX;
        private final double posZ;

        private PlayerLocation(GameProfile profile, double posX, double posZ) {
            this.profile = profile;
            this.posX = posX;
            this.posZ = posZ;
        }

        public GameProfile getProfile() {
            return profile;
        }

        public double getPosX() {
            return posX;
        }

        public double getPosZ() {
            return posZ;
        }
    }

    public static final class ScreenPosition {

        private final double x;
        private final double y;

        private ScreenPosition(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }
    }
}
