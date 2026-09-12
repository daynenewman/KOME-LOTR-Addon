package kome.common.network;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;

public class KOMECompanyGuiEntry {
    public String id = "";
    public String name = "";
    public String tile = "";
    public String tileDisplayName = "";
    public int unitCount;
    public int population;
    public int mountedPopulation;
    public int groundPopulation;
    public String status = "";
    public String movementOrderId = "";
    public String destinationTile = "";
    public long etaMillis;
    public boolean canMove;
    public String cannotMoveReason = "";
    public String faction = "";
    public String ownerName = "";
    public String controllerName = "";
    public String controllerAuthority = "";
    public String tendency = "";
    public String delegationAlliancePair = "";
    public String movementStatus = "";
    public String nativeFaction = "";
    public String authorizedWarIds = "";
    public String legalTargets = "";
    public String populationSource = "";
    public String withdrawalState = "";
    public String authorizationReason = "";
    public boolean canSetTendency;
    public boolean canReclaim;
    public boolean canChooseAccessResponse;
    public boolean canStay;
    public boolean canRetreat;
    public boolean canResume;
    public String accessLossReason = "";
    public String resumeBlockedReason = "";
    public String retreatTargetTile = "";
    public String currentTile = "";
    public String nextTile = "";
    public String intendedDestinationTile = "";
    public String retreatBlockedReason = "";
    public boolean canDisband;
    public int stewardshipUnallocated;
    public int stewardshipGlobalCap;
    public int stewardshipReserved;
    public int stewardshipAvailable;

    public void fromBytes(ByteBuf buf) {
        id = read(buf);
        name = read(buf);
        tile = read(buf);
        tileDisplayName = read(buf);
        unitCount = buf.readInt();
        population = buf.readInt();
        mountedPopulation = buf.readInt();
        groundPopulation = buf.readInt();
        status = read(buf);
        movementOrderId = read(buf);
        destinationTile = read(buf);
        etaMillis = buf.readLong();
        canMove = buf.readBoolean();
        cannotMoveReason = read(buf);
        faction = read(buf);
        ownerName = read(buf);
        controllerName = read(buf);
        controllerAuthority = read(buf);
        tendency = read(buf);
        delegationAlliancePair = read(buf);
        movementStatus = read(buf);
        nativeFaction = read(buf);
        authorizedWarIds = read(buf);
        legalTargets = read(buf);
        populationSource = read(buf);
        withdrawalState = read(buf);
        authorizationReason = read(buf);
        canSetTendency = buf.readBoolean();
        canReclaim = buf.readBoolean();
        canChooseAccessResponse = buf.readBoolean();
        canStay = buf.readBoolean();
        canRetreat = buf.readBoolean();
        canResume = buf.readBoolean();
        accessLossReason = read(buf);
        resumeBlockedReason = read(buf);
        retreatTargetTile = read(buf);
        currentTile = read(buf);
        nextTile = read(buf);
        intendedDestinationTile = read(buf);
        retreatBlockedReason = read(buf);
        canDisband = buf.readBoolean();
        stewardshipUnallocated = buf.readInt();
        stewardshipGlobalCap = buf.readInt();
        stewardshipReserved = buf.readInt();
        stewardshipAvailable = buf.readInt();
    }

    public void toBytes(ByteBuf buf) {
        write(buf, id);
        write(buf, name);
        write(buf, tile);
        write(buf, tileDisplayName);
        buf.writeInt(unitCount);
        buf.writeInt(population);
        buf.writeInt(mountedPopulation);
        buf.writeInt(groundPopulation);
        write(buf, status);
        write(buf, movementOrderId);
        write(buf, destinationTile);
        buf.writeLong(etaMillis);
        buf.writeBoolean(canMove);
        write(buf, cannotMoveReason);
        write(buf, faction);
        write(buf, ownerName);
        write(buf, controllerName);
        write(buf, controllerAuthority);
        write(buf, tendency);
        write(buf, delegationAlliancePair);
        write(buf, movementStatus);
        write(buf, nativeFaction);
        write(buf, authorizedWarIds);
        write(buf, legalTargets);
        write(buf, populationSource);
        write(buf, withdrawalState);
        write(buf, authorizationReason);
        buf.writeBoolean(canSetTendency);
        buf.writeBoolean(canReclaim);
        buf.writeBoolean(canChooseAccessResponse);
        buf.writeBoolean(canStay);
        buf.writeBoolean(canRetreat);
        buf.writeBoolean(canResume);
        write(buf, accessLossReason);
        write(buf, resumeBlockedReason);
        write(buf, retreatTargetTile);
        write(buf, currentTile);
        write(buf, nextTile);
        write(buf, intendedDestinationTile);
        write(buf, retreatBlockedReason);
        buf.writeBoolean(canDisband);
        buf.writeInt(stewardshipUnallocated);
        buf.writeInt(stewardshipGlobalCap);
        buf.writeInt(stewardshipReserved);
        buf.writeInt(stewardshipAvailable);
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
