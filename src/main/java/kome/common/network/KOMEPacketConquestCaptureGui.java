package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import java.util.ArrayList;
import java.util.List;

public class KOMEPacketConquestCaptureGui implements IMessage {
    public kome.common.data.KOMEPopulationProjection population = new kome.common.data.KOMEPopulationProjection(
            "", 0L, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO, false, 0L);
    public String tileId;
    public String ownerFaction;
    public String pendingFromFaction;
    public String pendingToFaction;
    public String viewerFaction;
    public int offensivePop;
    public int defensivePop;
    public int mountedPop;
    public int groundPop;
    public int incomingPop;
    public int outgoingPop;
    public long incomingEtaMillis;
    public boolean canClaim;
    public boolean canTransfer;
    public boolean canAcceptTransfer;
    public boolean canCancelTransfer;
    public boolean canMoveTroops;
    public boolean canInspectWaypoint;
    public String claimantName = "";
    public boolean ownerHasKing;
    public int myOffensivePop;
    public int myDefensivePop;
    public int myMountedPop;
    public int myGroundPop;
    public String activeRecruitmentTile = "";
    public boolean canSetRecruitmentTile;
    public String lotrWaypointKey = "";
    public String lotrWaypointDisplayName = "";
    public String lotrWaypointRegion = "";
    public int waypointLevel;
    public String currentRulingFaction = "";
    public String defaultRulingFaction = "";
    public String mapRegion = "";
    public final List<String> capitalFactions = new ArrayList<String>();
    public boolean claimConfirmationArmed;
    public String claimWarning = "";
    public String claimWarDestination = "";
    public final List<BuildView> builds = new ArrayList<BuildView>();
    public final List<String> selectablePopulationOwners = new ArrayList<String>();
    public int viewerDimension;
    public double viewerX;
    public double viewerY;
    public double viewerZ;
    public String focusBuildId = "";

    public KOMEPacketConquestCaptureGui() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        population = KOMEPopulationWire.readProjection(buf);
        tileId = KOMEPopulationWire.readText(buf);
        ownerFaction = KOMEPopulationWire.readText(buf);
        pendingFromFaction = KOMEPopulationWire.readText(buf);
        pendingToFaction = KOMEPopulationWire.readText(buf);
        viewerFaction = KOMEPopulationWire.readText(buf);
        offensivePop = buf.readInt();
        defensivePop = buf.readInt();
        mountedPop = buf.readInt();
        groundPop = buf.readInt();
        incomingPop = buf.readInt();
        outgoingPop = buf.readInt();
        incomingEtaMillis = buf.readLong();
        canClaim = buf.readBoolean();
        canTransfer = buf.readBoolean();
        canAcceptTransfer = buf.readBoolean();
        canCancelTransfer = buf.readBoolean();
        canMoveTroops = buf.readBoolean();
        canInspectWaypoint = buf.readBoolean();
        claimantName = KOMEPopulationWire.readText(buf);
        ownerHasKing = buf.readBoolean();
        myOffensivePop = buf.readInt();
        myDefensivePop = buf.readInt();
        myMountedPop = buf.readInt();
        myGroundPop = buf.readInt();
        activeRecruitmentTile = KOMEPopulationWire.readText(buf);
        canSetRecruitmentTile = buf.readBoolean();
        lotrWaypointKey = KOMEPopulationWire.readText(buf);
        lotrWaypointDisplayName = KOMEPopulationWire.readText(buf);
        lotrWaypointRegion = KOMEPopulationWire.readText(buf);
        waypointLevel = buf.readInt();
        currentRulingFaction = KOMEPopulationWire.readText(buf);
        defaultRulingFaction = KOMEPopulationWire.readText(buf);
        mapRegion = KOMEPopulationWire.readText(buf);
        capitalFactions.clear();
        int capitalCount = KOMEPopulationWire.count(buf.readInt());
        for (int i = 0; i < capitalCount; i++)
            capitalFactions.add(KOMEPopulationWire.readText(buf));
        claimConfirmationArmed = buf.readBoolean();
        claimWarning = KOMEPopulationWire.readText(buf);
        claimWarDestination = KOMEPopulationWire.readText(buf);
        builds.clear();
        int buildCount = KOMEPopulationWire.count(buf.readInt());
        for (int i = 0; i < buildCount; i++) {
            BuildView view = new BuildView();
            view.read(buf);
            builds.add(view);
        }
        selectablePopulationOwners.clear();
        int ownerCount = KOMEPopulationWire.count(buf.readInt());
        for (int i = 0; i < ownerCount; i++) selectablePopulationOwners.add(KOMEPopulationWire.readText(buf));
        viewerDimension = buf.readInt();
        viewerX = buf.readDouble();
        viewerY = buf.readDouble();
        viewerZ = buf.readDouble();
        focusBuildId = KOMEPopulationWire.readText(buf);
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override
    public void toBytes(ByteBuf output) {
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            KOMEPopulationWire.writeProjection(buf, population);
            KOMEPopulationWire.writeText(buf, tileId);
            KOMEPopulationWire.writeText(buf, ownerFaction);
            KOMEPopulationWire.writeText(buf, pendingFromFaction);
            KOMEPopulationWire.writeText(buf, pendingToFaction);
            KOMEPopulationWire.writeText(buf, viewerFaction);
            buf.writeInt(offensivePop);
            buf.writeInt(defensivePop);
            buf.writeInt(mountedPop);
            buf.writeInt(groundPop);
            buf.writeInt(incomingPop);
            buf.writeInt(outgoingPop);
            buf.writeLong(incomingEtaMillis);
            buf.writeBoolean(canClaim);
            buf.writeBoolean(canTransfer);
            buf.writeBoolean(canAcceptTransfer);
            buf.writeBoolean(canCancelTransfer);
            buf.writeBoolean(canMoveTroops);
            buf.writeBoolean(canInspectWaypoint);
            KOMEPopulationWire.writeText(buf, claimantName);
            buf.writeBoolean(ownerHasKing);
            buf.writeInt(myOffensivePop);
            buf.writeInt(myDefensivePop);
            buf.writeInt(myMountedPop);
            buf.writeInt(myGroundPop);
            KOMEPopulationWire.writeText(buf, activeRecruitmentTile);
            buf.writeBoolean(canSetRecruitmentTile);
            KOMEPopulationWire.writeText(buf, lotrWaypointKey);
            KOMEPopulationWire.writeText(buf, lotrWaypointDisplayName);
            KOMEPopulationWire.writeText(buf, lotrWaypointRegion);
            buf.writeInt(waypointLevel);
            KOMEPopulationWire.writeText(buf, currentRulingFaction);
            KOMEPopulationWire.writeText(buf, defaultRulingFaction);
            KOMEPopulationWire.writeText(buf, mapRegion);
            buf.writeInt(KOMEPopulationWire.count(capitalFactions.size()));
            for (String faction : capitalFactions)
                KOMEPopulationWire.writeText(buf, faction == null ? "" : faction);
            buf.writeBoolean(claimConfirmationArmed);
            KOMEPopulationWire.writeText(buf, claimWarning == null ? "" : claimWarning);
            KOMEPopulationWire.writeText(buf, claimWarDestination == null ? "" : claimWarDestination);
            buf.writeInt(KOMEPopulationWire.count(builds.size()));
            for (BuildView view : builds) view.write(buf);
            buf.writeInt(KOMEPopulationWire.count(selectablePopulationOwners.size()));
            for (String owner : selectablePopulationOwners) KOMEPopulationWire.writeText(buf, owner == null ? "" : owner);
            buf.writeInt(viewerDimension);
            buf.writeDouble(viewerX);
            buf.writeDouble(viewerY);
            buf.writeDouble(viewerZ);
            KOMEPopulationWire.writeText(buf, focusBuildId == null ? "" : focusBuildId);
        });
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestCaptureGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestCaptureGui message, MessageContext ctx) {
            final KOMEPacketConquestCaptureGui snapshot = KOMEPopulationWire.copyForPublication(message, KOMEPacketConquestCaptureGui::new);
            KOMEAddon.proxy.enqueueClientTask(() -> KOMEAddon.proxy.displayConquestCaptureGui(snapshot));
            return null;
        }
    }

    public static class BuildView {
        public String id = "";
        public String name = "";
        public String populationFaction = "";
        public String builder = "";
        public String manager = "";
        public int dimension;
        public double x;
        public double y;
        public double z;
        public String buildType = "";
        public long approvedCentiHours;
        public int pendingCount;
        public String status = "";
        public boolean canManage;
        public boolean canDestroy;
        public String destroyMode = "";
        public String destroyReason = "";
        public final List<ContributionView> contributions = new ArrayList<ContributionView>();

        void read(ByteBuf buf) {
            id = KOMEPopulationWire.readText(buf);
            name = KOMEPopulationWire.readText(buf);
            populationFaction = KOMEPopulationWire.readText(buf);
            builder = KOMEPopulationWire.readText(buf);
            manager = KOMEPopulationWire.readText(buf);
            dimension = buf.readInt();
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
            buildType = kome.common.data.KOMEBuildType.forKey(KOMEPopulationWire.readText(buf)).key;
            approvedCentiHours = KOMEPopulationWire.nonnegative(buf.readLong());
            pendingCount = (int) KOMEPopulationWire.nonnegative(buf.readInt());
            status = KOMEPopulationWire.readText(buf);
            canManage = buf.readBoolean();
            canDestroy = buf.readBoolean();
            destroyMode = KOMEPopulationWire.readText(buf);
            destroyReason = KOMEPopulationWire.readText(buf);
            contributions.clear();
            int count = KOMEPopulationWire.count(buf.readInt());
            for (int i = 0; i < count; i++) {
                ContributionView contribution = new ContributionView();
                contribution.read(buf);
                contributions.add(contribution);
            }
        }

        void write(ByteBuf buf) {
            KOMEPopulationWire.writeText(buf, safe(id));
            KOMEPopulationWire.writeText(buf, safe(name));
            KOMEPopulationWire.writeText(buf, safe(populationFaction));
            KOMEPopulationWire.writeText(buf, safe(builder));
            KOMEPopulationWire.writeText(buf, safe(manager));
            buf.writeInt(dimension);
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            KOMEPopulationWire.writeText(buf, kome.common.data.KOMEBuildType.forKey(buildType).key);
            buf.writeLong(KOMEPopulationWire.nonnegative(approvedCentiHours));
            buf.writeInt((int) KOMEPopulationWire.nonnegative(pendingCount));
            KOMEPopulationWire.writeText(buf, safe(status));
            buf.writeBoolean(canManage);
            buf.writeBoolean(canDestroy);
            KOMEPopulationWire.writeText(buf, safe(destroyMode));
            KOMEPopulationWire.writeText(buf, safe(destroyReason));
            buf.writeInt(KOMEPopulationWire.count(contributions.size()));
            for (ContributionView contribution : contributions) contribution.write(buf);
        }
    }

    public static class ContributionView {
        public String id = "";
        public String player = "";
        public String faction = "";
        public long centiHours;
        public String status = kome.common.data.KOMEBuildContribution.PENDING;

        void read(ByteBuf buf) {
            id = KOMEPopulationWire.readText(buf);
            player = KOMEPopulationWire.readText(buf);
            faction = KOMEPopulationWire.readText(buf);
            centiHours = KOMEPopulationWire.nonnegative(buf.readLong());
            status = kome.common.data.KOMEBuildContribution.normalizeStatus(KOMEPopulationWire.readText(buf));
        }

        void write(ByteBuf buf) {
            KOMEPopulationWire.writeText(buf, safe(id));
            KOMEPopulationWire.writeText(buf, safe(player));
            KOMEPopulationWire.writeText(buf, safe(faction));
            buf.writeLong(KOMEPopulationWire.nonnegative(centiHours));
            KOMEPopulationWire.writeText(buf, kome.common.data.KOMEBuildContribution.normalizeStatus(status));
        }
    }



    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
