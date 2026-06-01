package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMETileTroopSummary {
    public String tileId = "";
    public int stationedPop;
    public int movingPop;
    public int incomingPop;

    public boolean hasAnyTroops() {
        return stationedPop > 0 || movingPop > 0 || incomingPop > 0;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Tile", KOMEConquestTile.normalizeId(tileId));
        nbt.setInteger("StationedPop", stationedPop);
        nbt.setInteger("MovingPop", movingPop);
        nbt.setInteger("IncomingPop", incomingPop);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("Tile"));
        stationedPop = nbt.getInteger("StationedPop");
        movingPop = nbt.getInteger("MovingPop");
        incomingPop = nbt.getInteger("IncomingPop");
    }
}
