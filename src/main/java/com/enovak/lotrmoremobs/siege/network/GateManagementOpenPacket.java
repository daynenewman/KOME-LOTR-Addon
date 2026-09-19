package com.enovak.lotrmoremobs.siege.network;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GateManagementOpenPacket implements IMessage {

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private boolean canManage;
    private boolean canManagePlayerAccess;
    private boolean canAdminister;
    private KOMEGateManagementSnapshot komeSnapshot;

    public GateManagementOpenPacket() {
    }

    public GateManagementOpenPacket(
            int dimensionId,
            int x,
            int y,
            int z,
            boolean canManage,
            boolean canManagePlayerAccess,
            boolean canAdminister
    ) {
        this(dimensionId, x, y, z, canManage, canManagePlayerAccess, canAdminister,
            new KOMEGateManagementSnapshot(false, false, false, "", "", "",
                "Unavailable", "Unavailable", 0, 0, 1,
                Collections.<KOMEGateManagementSnapshot.BuildOption>emptyList(),
                Collections.<KOMEGateManagementSnapshot.RelinkOption>emptyList()));
    }

    public GateManagementOpenPacket(int dimensionId, int x, int y, int z,
            boolean canManage, boolean canManagePlayerAccess, boolean canAdminister,
            KOMEGateManagementSnapshot komeSnapshot) {
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.canManage = canManage;
        this.canManagePlayerAccess = canManagePlayerAccess;
        this.canAdminister = canAdminister;
        this.komeSnapshot = komeSnapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        dimensionId = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        canManage = buffer.readBoolean();
        canManagePlayerAccess = buffer.readBoolean();
        canAdminister = buffer.readBoolean();
        boolean linked = buffer.readBoolean();
        boolean relinkRequired = buffer.readBoolean();
        boolean needsConfirmation = buffer.readBoolean();
        String buildId = ByteBufUtils.readUTF8String(buffer);
        String buildName = ByteBufUtils.readUTF8String(buffer);
        String recordId = ByteBufUtils.readUTF8String(buffer);
        String gateSize = ByteBufUtils.readUTF8String(buffer);
        String projectedHp = ByteBufUtils.readUTF8String(buffer);
        int suggestedWidth = buffer.readInt();
        int suggestedHeight = buffer.readInt();
        int serverDefault = buffer.readInt();
        int buildCount = buffer.readUnsignedShort();
        if (buildCount > KOMEGateManagementSnapshot.MAX_OPTIONS) {
            throw new IllegalArgumentException("Too many KOME Build options in Gate Management packet.");
        }
        List<KOMEGateManagementSnapshot.BuildOption> builds =
            new ArrayList<KOMEGateManagementSnapshot.BuildOption>();
        for (int i = 0; i < buildCount; i++) {
            builds.add(new KOMEGateManagementSnapshot.BuildOption(
                ByteBufUtils.readUTF8String(buffer), ByteBufUtils.readUTF8String(buffer)));
        }
        int relinkCount = buffer.readUnsignedShort();
        if (relinkCount > KOMEGateManagementSnapshot.MAX_OPTIONS) {
            throw new IllegalArgumentException("Too many KOME Relink options in Gate Management packet.");
        }
        List<KOMEGateManagementSnapshot.RelinkOption> relinks =
            new ArrayList<KOMEGateManagementSnapshot.RelinkOption>();
        for (int i = 0; i < relinkCount; i++) {
            relinks.add(new KOMEGateManagementSnapshot.RelinkOption(
                ByteBufUtils.readUTF8String(buffer), ByteBufUtils.readUTF8String(buffer),
                ByteBufUtils.readUTF8String(buffer)));
        }
        komeSnapshot = new KOMEGateManagementSnapshot(linked, relinkRequired,
            needsConfirmation, buildId, buildName, recordId, gateSize, projectedHp,
            suggestedWidth, suggestedHeight, serverDefault, builds, relinks);
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(dimensionId);
        buffer.writeInt(x);
        buffer.writeInt(y);
        buffer.writeInt(z);
        buffer.writeBoolean(canManage);
        buffer.writeBoolean(canManagePlayerAccess);
        buffer.writeBoolean(canAdminister);
        KOMEGateManagementSnapshot snapshot = komeSnapshot;
        if (snapshot == null) {
            snapshot = new KOMEGateManagementSnapshot(false, false, false, "", "", "",
                "Unavailable", "Unavailable", 0, 0, 1,
                Collections.<KOMEGateManagementSnapshot.BuildOption>emptyList(),
                Collections.<KOMEGateManagementSnapshot.RelinkOption>emptyList());
        }
        buffer.writeBoolean(snapshot.isLinked());
        buffer.writeBoolean(snapshot.isRelinkRequired());
        buffer.writeBoolean(snapshot.needsDimensionConfirmation());
        ByteBufUtils.writeUTF8String(buffer, snapshot.getBuildId());
        ByteBufUtils.writeUTF8String(buffer, snapshot.getBuildName());
        ByteBufUtils.writeUTF8String(buffer, snapshot.getRecordId());
        ByteBufUtils.writeUTF8String(buffer, snapshot.getGateSizeLabel());
        ByteBufUtils.writeUTF8String(buffer, snapshot.getProjectedMaxHpLabel());
        buffer.writeInt(snapshot.getSuggestedWidth());
        buffer.writeInt(snapshot.getSuggestedHeight());
        buffer.writeInt(snapshot.getServerDefaultMaxHp());
        buffer.writeShort(snapshot.getEligibleBuilds().size());
        for (KOMEGateManagementSnapshot.BuildOption option : snapshot.getEligibleBuilds()) {
            ByteBufUtils.writeUTF8String(buffer, option.getBuildId());
            ByteBufUtils.writeUTF8String(buffer, option.getLabel());
        }
        buffer.writeShort(snapshot.getRelinkOptions().size());
        for (KOMEGateManagementSnapshot.RelinkOption option : snapshot.getRelinkOptions()) {
            ByteBufUtils.writeUTF8String(buffer, option.getBuildId());
            ByteBufUtils.writeUTF8String(buffer, option.getRecordId());
            ByteBufUtils.writeUTF8String(buffer, option.getLabel());
        }
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

    public boolean canManage() {
        return canManage;
    }

    public boolean canManagePlayerAccess() {
        return canManagePlayerAccess;
    }

    public boolean canAdminister() {
        return canAdminister;
    }

    public KOMEGateManagementSnapshot getKomeSnapshot() { return komeSnapshot; }

    public static class Handler implements IMessageHandler<
            GateManagementOpenPacket,
            IMessage> {

        @Override
        public IMessage onMessage(
                GateManagementOpenPacket message,
                MessageContext context
        ) {
            Main.proxy.handleGateManagementOpen(message);
            return null;
        }
    }
}
