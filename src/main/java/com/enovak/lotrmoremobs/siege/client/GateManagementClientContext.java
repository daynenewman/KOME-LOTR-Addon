package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.client.gui.GuiGateManagement;
import com.enovak.lotrmoremobs.siege.network.GateManagementOpenPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;

public final class GateManagementClientContext {

    private static int dimensionId;

    private static int controllerX;
    private static int controllerY;
    private static int controllerZ;

    private static boolean canManage;
    private static boolean canManagePlayerAccess;
    private static boolean canAdminister;

    private static boolean active;

    private static final Map<UUID, String> accessNames =
            new HashMap<UUID, String>();

    private static boolean hasAccessNameSnapshot;

    private static int accessNameDimension;
    private static int accessNameX;
    private static int accessNameY;
    private static int accessNameZ;

    private static long accessGeneration;

    private GateManagementClientContext() {
    }

    public static void open(
            GateManagementOpenPacket packet
    ) {
        Minecraft minecraft =
                Minecraft.getMinecraft();

        if (minecraft.theWorld == null
                || minecraft.theWorld.provider.dimensionId
                != packet.getDimensionId()) {

            return;
        }

        boolean resumeEdit =
                GateEditClientContext.matchesManagement(
                        packet.getDimensionId(),
                        packet.getX(),
                        packet.getY(),
                        packet.getZ()
                );

        boolean matchingAccessSnapshot =
                hasAccessNameSnapshot
                        && accessNameDimension
                        == packet.getDimensionId()
                        && accessNameX
                        == packet.getX()
                        && accessNameY
                        == packet.getY()
                        && accessNameZ
                        == packet.getZ();

        dimensionId =
                packet.getDimensionId();

        controllerX =
                packet.getX();

        controllerY =
                packet.getY();

        controllerZ =
                packet.getZ();

        canManage =
                packet.canManage();

        canManagePlayerAccess =
                packet.canManagePlayerAccess();

        canAdminister =
                packet.canAdminister();

        if (!matchingAccessSnapshot) {
            accessNames.clear();

            hasAccessNameSnapshot =
                    false;
        }

        if (!resumeEdit) {
            GateEditClientContext.clear();
        }

        GateFinalizedInspectionClientContext.clear();

        active =
                true;

        minecraft.displayGuiScreen(
                new GuiGateManagement(
                        resumeEdit
                )
        );
    }

    public static void updateAccessNames(
            int packetDimension,
            int x,
            int y,
            int z,
            Map<UUID, String> names
    ) {
        /*
         * Ignore access snapshots belonging to another gate while this
         * management screen is active.
         */
        if (active
                && (packetDimension
                != dimensionId
                || x != controllerX
                || y != controllerY
                || z != controllerZ)) {

            return;
        }

        accessNameDimension =
                packetDimension;

        accessNameX =
                x;

        accessNameY =
                y;

        accessNameZ =
                z;

        accessNames.clear();

        if (names != null) {
            accessNames.putAll(
                    names
            );
        }

        hasAccessNameSnapshot =
                true;

        ++accessGeneration;
    }

    public static String getAccessDisplayName(
            UUID uuid
    ) {
        if (uuid == null) {
            return "";
        }

        String value =
                accessNames.get(
                        uuid
                );

        return value == null
                || value.isEmpty()
                ? uuid.toString()
                : value;
    }

    public static long getAccessGeneration() {
        return accessGeneration;
    }

    public static void clear() {
        active =
                false;

        accessNames.clear();

        hasAccessNameSnapshot =
                false;

        ++accessGeneration;
    }

    public static boolean isActive() {
        return active;
    }

    public static int getDimensionId() {
        return dimensionId;
    }

    public static int getControllerX() {
        return controllerX;
    }

    public static int getControllerY() {
        return controllerY;
    }

    public static int getControllerZ() {
        return controllerZ;
    }

    public static boolean canManage() {
        return canManage;
    }

    public static boolean canManagePlayerAccess() {
        return canManagePlayerAccess;
    }

    public static boolean canAdminister() {
        return canAdminister;
    }
}