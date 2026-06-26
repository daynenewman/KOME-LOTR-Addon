package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMETileTroopSummary {
    public String tileId = "";
    public String ownerFaction = "";
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public int farmhandTotal;
    public int farmhandUsed;
    public int stationedPop;
    public int stationedOffensivePop;
    public int stationedMountedPop;
    public int stationedDefensivePop;
    public int movingPop;
    public int incomingPop;
    public int outgoingMovementCount;
    public int incomingMovementCount;

    public boolean hasAnyTroops() {
        return stationedPop > 0 || movingPop > 0 || incomingPop > 0;
    }

    public boolean hasAnyPopulation() {
        return hasAnyTroops() || offensiveTotal > 0 || offensiveUsed > 0 || defensiveTotal > 0 || defensiveUsed > 0 || farmhandTotal > 0 || farmhandUsed > 0;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Tile", KOMEConquestTile.normalizeId(tileId));
        nbt.setString("OwnerFaction", ownerFaction == null ? "" : ownerFaction);
        nbt.setInteger("OffensiveTotal", offensiveTotal);
        nbt.setInteger("OffensiveUsed", offensiveUsed);
        nbt.setInteger("DefensiveTotal", defensiveTotal);
        nbt.setInteger("DefensiveUsed", defensiveUsed);
        nbt.setInteger("FarmhandTotal", farmhandTotal);
        nbt.setInteger("FarmhandUsed", farmhandUsed);
        nbt.setInteger("StationedPop", stationedPop);
        nbt.setInteger("StationedOffensivePop", stationedOffensivePop);
        nbt.setInteger("StationedMountedPop", stationedMountedPop);
        nbt.setInteger("StationedDefensivePop", stationedDefensivePop);
        nbt.setInteger("MovingPop", movingPop);
        nbt.setInteger("IncomingPop", incomingPop);
        nbt.setInteger("OutgoingMovementCount", outgoingMovementCount);
        nbt.setInteger("IncomingMovementCount", incomingMovementCount);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("Tile"));
        ownerFaction = nbt.getString("OwnerFaction");
        offensiveTotal = nbt.getInteger("OffensiveTotal");
        offensiveUsed = nbt.getInteger("OffensiveUsed");
        defensiveTotal = nbt.getInteger("DefensiveTotal");
        defensiveUsed = nbt.getInteger("DefensiveUsed");
        farmhandTotal = nbt.getInteger("FarmhandTotal");
        farmhandUsed = nbt.getInteger("FarmhandUsed");
        stationedPop = nbt.getInteger("StationedPop");
        stationedOffensivePop = nbt.getInteger("StationedOffensivePop");
        stationedMountedPop = nbt.getInteger("StationedMountedPop");
        stationedDefensivePop = nbt.getInteger("StationedDefensivePop");
        movingPop = nbt.getInteger("MovingPop");
        incomingPop = nbt.getInteger("IncomingPop");
        outgoingMovementCount = nbt.getInteger("OutgoingMovementCount");
        incomingMovementCount = nbt.getInteger("IncomingMovementCount");
    }
}
