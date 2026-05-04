package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMETerritory {
    public String waypoint;
    public String faction = "";
    public String ruler = "";
    public String displayName = "";

    public KOMETerritory(String waypoint) {
        this.waypoint = waypoint;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        waypoint = nbt.getString("Waypoint");
        faction = nbt.getString("Faction");
        ruler = nbt.getString("Ruler");
        displayName = nbt.getString("DisplayName");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Waypoint", waypoint);
        nbt.setString("Faction", faction);
        nbt.setString("Ruler", ruler);
        nbt.setString("DisplayName", displayName);
        return nbt;
    }
}
