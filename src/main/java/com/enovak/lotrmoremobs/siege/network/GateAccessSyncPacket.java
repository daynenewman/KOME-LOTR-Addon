package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import com.enovak.lotrmoremobs.siege.gate.GateControlMode;
import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;

import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

public class GateAccessSyncPacket
        implements IMessage {

    private int dimensionId;

    private int x;
    private int y;
    private int z;

    private String gateName =
            TileEntitySiegeGate.DEFAULT_GATE_NAME;

    private UUID ownerUuid;

    private String factionName =
            "";

    private int requiredAlignment =
            TileEntitySiegeGate
                    .DEFAULT_REQUIRED_ALIGNMENT;

    private boolean factionAccessEnabled =
            true;

    private int gateControlMode =
            GateControlMode.AUTOMATIC.getNetworkId();

    private List<UUID> editors =
            new ArrayList<UUID>();

    private List<UUID> operators =
            new ArrayList<UUID>();

    private List<UUID> whitelist =
            new ArrayList<UUID>();

    private Map<UUID, String> accessNames =
            new LinkedHashMap<UUID, String>();

    private UUID reservedRamUuid;

    public GateAccessSyncPacket() {
    }

    public GateAccessSyncPacket(
            TileEntitySiegeGate gate
    ) {
        dimensionId =
                gate.getWorldObj()
                        .provider.dimensionId;

        x = gate.xCoord;
        y = gate.yCoord;
        z = gate.zCoord;

        gateName =
                gate.getGateName();

        ownerUuid =
                gate.getOwnerUuid();

        factionName =
                gate.getGateFaction()
                        == null
                        ? ""
                        : gate.getGateFaction()
                        .codeName();

        requiredAlignment =
                gate.getRequiredAlignment();

        factionAccessEnabled =
                gate.isFactionAccessEnabled();

        gateControlMode =
                gate.getGateControlMode()
                        .getNetworkId();

        editors.addAll(
                gate.getEditorUuids()
        );

        operators.addAll(
                gate.getOperatorUuids()
        );

        whitelist.addAll(
                gate.getAccessWhitelistUuids()
        );

        Set<UUID> allAccessEntries =
                new LinkedHashSet<UUID>();

        /*
         * Include the owner in the display-name snapshot so the Player Access
         * screen can pin the owner as its first, read-only row.
         */
        if (ownerUuid != null) {
            allAccessEntries.add(ownerUuid);
        }

        allAccessEntries.addAll(
                editors
        );

        allAccessEntries.addAll(
                operators
        );

        allAccessEntries.addAll(
                whitelist
        );

        for (UUID uuid
                : allAccessEntries) {

            if (uuid == null) {
                continue;
            }

            accessNames.put(
                    uuid,
                    resolveAccessName(
                            uuid
                    )
            );
        }

        reservedRamUuid =
                gate.getReservedRamUuid();
    }

    @Override
    public void fromBytes(
            ByteBuf buffer
    ) {
        dimensionId =
                buffer.readInt();

        x =
                buffer.readInt();

        y =
                buffer.readInt();

        z =
                buffer.readInt();

        gateName =
                ByteBufUtils
                        .readUTF8String(
                                buffer
                        );

        ownerUuid =
                readNullableUuid(
                        buffer
                );

        factionName =
                ByteBufUtils
                        .readUTF8String(
                                buffer
                        );

        requiredAlignment =
                buffer.readInt();

        factionAccessEnabled =
                buffer.readBoolean();

        gateControlMode =
                buffer.readByte();

        editors =
                readUuidList(
                        buffer
                );

        operators =
                readUuidList(
                        buffer
                );

        whitelist =
                readUuidList(
                        buffer
                );

        accessNames =
                readAccessNames(
                        buffer
                );

        reservedRamUuid =
                readNullableUuid(
                        buffer
                );
    }

    @Override
    public void toBytes(
            ByteBuf buffer
    ) {
        buffer.writeInt(
                dimensionId
        );

        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);

        ByteBufUtils.writeUTF8String(
                buffer,
                gateName
        );

        writeNullableUuid(
                buffer,
                ownerUuid
        );

        ByteBufUtils.writeUTF8String(
                buffer,
                factionName
        );

        buffer.writeInt(
                requiredAlignment
        );

        buffer.writeBoolean(
                factionAccessEnabled
        );

        buffer.writeByte(
                gateControlMode
        );

        writeUuidList(
                buffer,
                editors
        );

        writeUuidList(
                buffer,
                operators
        );

        writeUuidList(
                buffer,
                whitelist
        );

        writeAccessNames(
                buffer,
                accessNames
        );

        writeNullableUuid(
                buffer,
                reservedRamUuid
        );
    }

    public int getDimensionId() {
        return dimensionId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getGateName() {
        return gateName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getFactionName() {
        return factionName;
    }

    public int getRequiredAlignment() {
        return requiredAlignment;
    }

    public boolean isFactionAccessEnabled() {
        return factionAccessEnabled;
    }

    public int getGateControlMode() {
        return gateControlMode;
    }

    public List<UUID> getEditors() {
        return editors;
    }

    public List<UUID> getOperators() {
        return operators;
    }

    public List<UUID> getWhitelist() {
        return whitelist;
    }

    public Map<UUID, String> getAccessNames() {
        return new LinkedHashMap<UUID, String>(
                accessNames
        );
    }

    public UUID getReservedRamUuid() {
        return reservedRamUuid;
    }

    private static String resolveAccessName(
            UUID uuid
    ) {
        if (uuid == null) {
            return "";
        }

        MinecraftServer server =
                MinecraftServer
                        .getServer();

        if (server != null) {
            try {
                GameProfile profile =
                        server
                                .func_152358_ax()
                                .func_152652_a(
                                        uuid
                                );

                if (profile != null
                        && profile.getName()
                        != null
                        && !profile
                        .getName()
                        .isEmpty()) {

                    return profile
                            .getName();
                }

            } catch (RuntimeException ignored) {
            }
        }

        return uuid.toString();
    }

    private static UUID readNullableUuid(
            ByteBuf buffer
    ) {
        return buffer.readBoolean()
                ? new UUID(
                buffer.readLong(),
                buffer.readLong()
        )
                : null;
    }

    private static void writeNullableUuid(
            ByteBuf buffer,
            UUID uuid
    ) {
        buffer.writeBoolean(
                uuid != null
        );

        if (uuid != null) {
            buffer.writeLong(
                    uuid.getMostSignificantBits()
            );

            buffer.writeLong(
                    uuid.getLeastSignificantBits()
            );
        }
    }

    private static List<UUID> readUuidList(
            ByteBuf buffer
    ) {
        int count =
                buffer.readUnsignedShort();

        if (count
                > TileEntitySiegeGate
                .MAX_ACCESS_ENTRIES) {

            throw new IllegalArgumentException(
                    "Too many gate access entries"
            );
        }

        List<UUID> values =
                new ArrayList<UUID>(
                        count
                );

        for (int i = 0;
             i < count;
             ++i) {

            values.add(
                    new UUID(
                            buffer.readLong(),
                            buffer.readLong()
                    )
            );
        }

        return values;
    }

    private static void writeUuidList(
            ByteBuf buffer,
            Collection<UUID> values
    ) {
        List<UUID> validValues =
                new ArrayList<UUID>();

        for (UUID uuid
                : values) {

            if (uuid != null
                    && validValues.size()
                    < TileEntitySiegeGate
                    .MAX_ACCESS_ENTRIES) {

                validValues.add(
                        uuid
                );
            }
        }

        buffer.writeShort(
                validValues.size()
        );

        for (UUID uuid
                : validValues) {

            buffer.writeLong(
                    uuid.getMostSignificantBits()
            );

            buffer.writeLong(
                    uuid.getLeastSignificantBits()
            );
        }
    }

    private static Map<UUID, String> readAccessNames(
            ByteBuf buffer
    ) {
        int count =
                buffer.readUnsignedShort();

        if (count
                > TileEntitySiegeGate
                .MAX_ACCESS_ENTRIES + 1) {

            throw new IllegalArgumentException(
                    "Too many access names"
            );
        }

        Map<UUID, String> values =
                new LinkedHashMap<UUID, String>();

        for (int i = 0;
             i < count;
             ++i) {

            UUID uuid =
                    new UUID(
                            buffer.readLong(),
                            buffer.readLong()
                    );

            String name =
                    ByteBufUtils
                            .readUTF8String(
                                    buffer
                            );

            if (name == null
                    || name.isEmpty()) {

                name =
                        uuid.toString();
            }

            values.put(
                    uuid,
                    name
            );
        }

        return values;
    }

    private static void writeAccessNames(
            ByteBuf buffer,
            Map<UUID, String> values
    ) {
        List<Map.Entry<UUID, String>> valid =
                new ArrayList<Map.Entry<UUID, String>>();

        for (Map.Entry<UUID, String> entry
                : values.entrySet()) {

            if (entry.getKey() != null
                    && valid.size()
                    < TileEntitySiegeGate
                    .MAX_ACCESS_ENTRIES + 1) {

                valid.add(
                        entry
                );
            }
        }

        buffer.writeShort(
                valid.size()
        );

        for (Map.Entry<UUID, String> entry
                : valid) {

            UUID uuid =
                    entry.getKey();

            buffer.writeLong(
                    uuid.getMostSignificantBits()
            );

            buffer.writeLong(
                    uuid.getLeastSignificantBits()
            );

            String name =
                    entry.getValue();

            ByteBufUtils.writeUTF8String(
                    buffer,
                    name == null
                            || name.isEmpty()
                            ? uuid.toString()
                            : name
            );
        }
    }

    public static class Handler
            implements IMessageHandler<
            GateAccessSyncPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateAccessSyncPacket message,
                MessageContext context
        ) {
            Main.proxy
                    .handleGateAccessSync(
                            message
                    );

            return null;
        }
    }
}