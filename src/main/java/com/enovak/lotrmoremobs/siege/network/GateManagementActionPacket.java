package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

import net.minecraft.entity.player.EntityPlayerMP;

public class GateManagementActionPacket
        implements IMessage {

    public static final int BEGIN_REPAIR = 0;
    public static final int SET_NAME = 1;
    public static final int SET_FACTION = 2;
    public static final int SET_ALIGNMENT = 3;

    /*
     * Legacy IDs 4-6 belonged to:
     *
     * editor / operator / whitelist toggles.
     *
     * They intentionally remain unused so an old or hand-crafted packet
     * cannot accidentally become a different action.
     */
    public static final int CLAIM_OWNERLESS = 7;
    public static final int SET_MAX_HEALTH = 8;

    /*
     * New unified player-access system.
     *
     * value:
     *     0 = Access
     *     1 = Manager (legacy internal Editor role)
     */
    public static final int SET_PLAYER_ACCESS_LEVEL = 9;

    public static final int REMOVE_PLAYER_ACCESS = 10;
    public static final int SET_CONTROLLER_APPEARANCE = 11;

    /*
     * Separates the gate's LOTR faction identity from automatic alignment
     * access. value: 0 = OFF, 1 = ON.
     */
    public static final int SET_FACTION_ACCESS = 12;

    /* value: 0 = Automatic, 1 = Locked Closed, 2 = Held Open. */
    public static final int SET_GATE_CONTROL_MODE = 13;

    public static final int ACCESS_LEVEL_ACCESS = 0;
    public static final int ACCESS_LEVEL_EDITOR = 1;

    private static final int MAX_PACKET_TEXT_BYTES = 256;
    private static final int MAX_FACTION_TEXT_LENGTH = 64;
    private static final int MAX_PLAYER_TEXT_LENGTH = 64;
    private static final int MAX_BLOCK_REGISTRY_NAME_LENGTH = 256;

    private int action;
    private int dimensionId;

    private int x;
    private int y;
    private int z;

    private int value;

    private String text =
            "";

    private boolean payloadValid =
            true;

    public GateManagementActionPacket() {
    }

    public GateManagementActionPacket(
            int action,
            int dimensionId,
            int x,
            int y,
            int z
    ) {
        this(
                action,
                dimensionId,
                x,
                y,
                z,
                0,
                ""
        );
    }

    public GateManagementActionPacket(
            int action,
            int dimensionId,
            int x,
            int y,
            int z,
            int value,
            String text
    ) {
        this.action =
                action;

        this.dimensionId =
                dimensionId;

        this.x = x;
        this.y = y;
        this.z = z;

        this.value =
                value;

        this.text =
                text == null
                        ? ""
                        : text;
    }

    @Override
    public void fromBytes(
            ByteBuf buffer
    ) {
        action =
                buffer.readByte();

        dimensionId =
                buffer.readInt();

        x =
                buffer.readInt();

        y =
                buffer.readInt();

        z =
                buffer.readInt();

        value =
                buffer.readInt();

        int textBytes =
                ByteBufUtils.readVarInt(
                        buffer,
                        2
                );

        if (textBytes < 0
                || textBytes
                > MAX_PACKET_TEXT_BYTES
                || textBytes
                > buffer.readableBytes()) {

            payloadValid =
                    false;

            buffer.skipBytes(
                    buffer.readableBytes()
            );

            text =
                    "";

            return;
        }

        text =
                buffer.toString(
                        buffer.readerIndex(),
                        textBytes,
                        CharsetUtil.UTF_8
                );

        buffer.readerIndex(
                buffer.readerIndex()
                        + textBytes
        );
    }

    @Override
    public void toBytes(
            ByteBuf buffer
    ) {
        buffer.writeByte(
                action
        );

        buffer.writeInt(
                dimensionId
        );

        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);

        buffer.writeInt(
                value
        );

        ByteBufUtils.writeUTF8String(
                buffer,
                text
        );
    }

    public static boolean isKnownAction(
            int action
    ) {
        return action
                == BEGIN_REPAIR
                || action
                == SET_NAME
                || action
                == SET_FACTION
                || action
                == SET_ALIGNMENT
                || action
                == CLAIM_OWNERLESS
                || action
                == SET_MAX_HEALTH
                || action
                == SET_PLAYER_ACCESS_LEVEL
                || action
                == REMOVE_PLAYER_ACCESS
                || action
                == SET_CONTROLLER_APPEARANCE
                || action
                == SET_FACTION_ACCESS
                || action
                == SET_GATE_CONTROL_MODE;
    }

    public static boolean isCoalescibleUpdate(
            int action
    ) {
        return action
                == SET_NAME
                || action
                == SET_FACTION
                || action
                == SET_ALIGNMENT
                || action
                == SET_MAX_HEALTH
                || action
                == SET_CONTROLLER_APPEARANCE
                || action
                == SET_FACTION_ACCESS
                || action
                == SET_GATE_CONTROL_MODE;
    }

    private boolean hasValidShape() {
        if (!payloadValid
                || !isKnownAction(
                action
        )
                || !SiegeRequestLimiter
                .isSaneBlockPosition(
                        x,
                        y,
                        z
                )) {

            return false;
        }

        if (action
                == SET_PLAYER_ACCESS_LEVEL
                && value
                != ACCESS_LEVEL_ACCESS
                && value
                != ACCESS_LEVEL_EDITOR) {

            return false;
        }

        if (action
                == SET_FACTION_ACCESS
                && value != 0
                && value != 1) {

            return false;
        }

        if (action
                == SET_GATE_CONTROL_MODE
                && (value < 0 || value > 2)) {

            return false;
        }

        /*
         * Controller appearance metadata must be a normal
         * Minecraft block metadata value.
         */
        if (action
                == SET_CONTROLLER_APPEARANCE
                && (value < 0
                || value > 15)) {

            return false;
        }

        return isValidRequestText(
                action,
                text
        );
    }

    public static boolean isValidRequestText(
            int action,
            String text
    ) {
        if (text == null
                || !isKnownAction(
                action
        )) {

            return false;
        }

        int maximumLength;

        if (action
                == SET_NAME) {

            maximumLength =
                    TileEntitySiegeGate
                            .MAX_GATE_NAME_LENGTH;

        } else if (action
                == SET_FACTION) {

            maximumLength =
                    MAX_FACTION_TEXT_LENGTH;

        } else if (action
                == SET_PLAYER_ACCESS_LEVEL
                || action
                == REMOVE_PLAYER_ACCESS) {

            maximumLength =
                    MAX_PLAYER_TEXT_LENGTH;

        } else if (action
                == SET_CONTROLLER_APPEARANCE) {

            maximumLength =
                    MAX_BLOCK_REGISTRY_NAME_LENGTH;

        } else {
            maximumLength =
                    0;
        }

        return text.length()
                <= maximumLength;
    }

    public static class Handler
            implements IMessageHandler<
            GateManagementActionPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateManagementActionPacket message,
                MessageContext context
        ) {
            EntityPlayerMP player =
                    context
                            .getServerHandler()
                            .playerEntity;

            if (player != null
                    && message
                    .hasValidShape()) {

                GateManagementManager
                        .queueAction(
                                player,
                                message.action,
                                message.dimensionId,
                                message.x,
                                message.y,
                                message.z,
                                message.value,
                                message.text
                        );
            }

            return null;
        }
    }
}