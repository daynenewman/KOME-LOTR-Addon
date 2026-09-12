package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.network.RamTargetModePacket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public final class ClientRamTargetState {

    private static int dimensionId;
    private static int ramEntityId;
    private static UUID ramUuid;
    private static LOTRFaction ramFaction;
    private static boolean active;
    private static final List<RamTargetModePacket.TargetEntry> targetQueue =
            new ArrayList<RamTargetModePacket.TargetEntry>();

    private ClientRamTargetState() {
    }

    public static void apply(RamTargetModePacket packet) {
        boolean wasActive =
                active;

        if (packet.isQueueRefreshOnly()) {
            if (!active
                    || dimensionId != packet.getDimensionId()
                    || ramEntityId != packet.getRamEntityId()) {
                return;
            }

            ramUuid = readUuid(packet.getRamUuid());
            ramFaction = packet.getRamFaction() == null
                    || packet.getRamFaction().isEmpty()
                    ? null
                    : LOTRFaction.forName(packet.getRamFaction());
            targetQueue.clear();
            targetQueue.addAll(packet.getTargetQueue());
            return;
        }

        dimensionId = packet.getDimensionId();
        ramEntityId = packet.getRamEntityId();
        ramUuid = readUuid(packet.getRamUuid());
        ramFaction = packet.getRamFaction() == null
                || packet.getRamFaction().isEmpty()
                ? null
                : LOTRFaction.forName(packet.getRamFaction());
        active = packet.isActive();

        targetQueue.clear();
        if (active) {
            targetQueue.addAll(packet.getTargetQueue());
        }

        if (active && !wasActive) {
            Minecraft minecraft =
                    Minecraft.getMinecraft();

            if (minecraft.thePlayer != null) {
                ChatComponentText message =
                        new ChatComponentText(
                                "Press ESC to exit ram targeting mode"
                        );

                message.getChatStyle()
                        .setColor(
                                EnumChatFormatting.GRAY
                        );

                minecraft.thePlayer.addChatMessage(
                        message
                );
            }
        }
    }

    public static void clear() {
        active = false;
        targetQueue.clear();
    }

    public static boolean isActive() {
        return active;
    }

    public static int getDimensionId() {
        return dimensionId;
    }

    public static int getRamEntityId() {
        return ramEntityId;
    }

    public static UUID getRamUuid() {
        return ramUuid;
    }

    public static LOTRFaction getRamFaction() {
        return ramFaction;
    }

    public static int getQueueIndex(
            int dimension,
            int controllerX,
            int controllerY,
            int controllerZ
    ) {
        for (int i = 0; i < targetQueue.size(); ++i) {
            RamTargetModePacket.TargetEntry entry = targetQueue.get(i);
            if (entry.getDimensionId() == dimension
                    && entry.getControllerX() == controllerX
                    && entry.getControllerY() == controllerY
                    && entry.getControllerZ() == controllerZ) {
                return i;
            }
        }
        return -1;
    }

    public static List<RamTargetModePacket.TargetEntry> getTargetQueue() {
        return Collections.unmodifiableList(targetQueue);
    }

    private static UUID readUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
