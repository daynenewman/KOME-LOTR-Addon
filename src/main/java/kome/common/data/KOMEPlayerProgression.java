package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.item.ItemStack;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class KOMEPlayerProgression {
    public static final int OFFERING_SLOTS = 54;
    private final Set<String> completed = new HashSet<>();
    private final Map<String, String> assignments = new HashMap<>();
    private final Map<String, Integer> quotaDelivered = new HashMap<>();
    private final ItemStack[] offerings = new ItemStack[OFFERING_SLOTS];
    private String pledgedLordID = "";
    private String pledgedLordName = "";
    private String pledgedLordFaction = "";

    public boolean isCompleted(KOMEProgressionAchievement achievement) {
        return achievement != null && (achievement.defaultUnlocked || completed.contains(achievement.id));
    }

    public boolean grant(String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        return achievement != null && completed.add(achievement.id);
    }

    public boolean revoke(String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        return achievement != null && completed.remove(achievement.id);
    }

    public void reset() {
        completed.clear();
        assignments.clear();
        quotaDelivered.clear();
        clearOfferings();
        pledgedLordID = "";
        pledgedLordName = "";
        pledgedLordFaction = "";
    }

    public String getAssignment(String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        return achievement == null ? null : assignments.get(achievement.id);
    }

    public boolean setAssignment(String id, String assignment) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        if (achievement == null || assignment == null || assignment.trim().isEmpty()) {
            return false;
        }
        String previous = assignments.put(achievement.id, assignment);
        return previous == null || !previous.equals(assignment);
    }

    public Map<String, String> getAssignments() {
        return assignments;
    }

    public int getQuotaDelivered(String id) {
        Integer delivered = quotaDelivered.get(id);
        return delivered == null ? 0 : delivered.intValue();
    }

    public void addQuotaDelivered(String id, int amount) {
        if (id != null && amount > 0) {
            quotaDelivered.put(id, Integer.valueOf(getQuotaDelivered(id) + amount));
        }
    }

    public void setPledgedLord(String id, String name, String faction) {
        pledgedLordID = clean(id);
        pledgedLordName = clean(name);
        pledgedLordFaction = clean(faction);
    }

    public boolean hasPledgedLord() {
        return pledgedLordName != null && !pledgedLordName.trim().isEmpty();
    }

    public String getPledgedLordID() {
        return pledgedLordID;
    }

    public String getPledgedLordFaction() {
        return pledgedLordFaction;
    }

    public String getPledgedLordDisplay() {
        if (!hasPledgedLord()) {
            return "None";
        }
        return pledgedLordFaction == null || pledgedLordFaction.trim().isEmpty() ? pledgedLordName : pledgedLordName + " of " + pledgedLordFaction;
    }

    public ItemStack getOffering(int slot) {
        return slot >= 0 && slot < offerings.length ? offerings[slot] : null;
    }

    public void setOffering(int slot, ItemStack stack) {
        if (slot >= 0 && slot < offerings.length) {
            offerings[slot] = stack;
        }
    }

    public ItemStack decrOffering(int slot, int count) {
        ItemStack stack = getOffering(slot);
        if (stack == null) {
            return null;
        }
        if (stack.stackSize <= count) {
            offerings[slot] = null;
            return stack;
        }
        ItemStack split = stack.splitStack(count);
        if (stack.stackSize <= 0) {
            offerings[slot] = null;
        }
        return split;
    }

    public int getOfferingSlots() {
        return offerings.length;
    }

    public void clearOfferings() {
        for (int i = 0; i < offerings.length; i++) {
            offerings[i] = null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public int getCompletedCount(String group) {
        int count = 0;
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
            if ((group == null || achievement.group.equalsIgnoreCase(group)) && isCompleted(achievement)) {
                count++;
            }
        }
        return count;
    }

    public int getTotalCount(String group) {
        int count = 0;
        for (KOMEProgressionAchievement achievement : KOMEProgressionAchievement.ALL) {
            if (group == null || achievement.group.equalsIgnoreCase(group)) {
                count++;
            }
        }
        return count;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        completed.clear();
        assignments.clear();
        quotaDelivered.clear();
        NBTTagList list = nbt.getTagList("Completed", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            String id = list.getStringTagAt(i);
            if (KOMEProgressionAchievement.forID(id) != null) {
                completed.add(id);
            }
        }
        NBTTagList assignmentList = nbt.getTagList("Assignments", 10);
        for (int i = 0; i < assignmentList.tagCount(); i++) {
            NBTTagCompound entry = assignmentList.getCompoundTagAt(i);
            String id = entry.getString("ID");
            if (KOMEProgressionAchievement.forID(id) != null) {
                assignments.put(id, entry.getString("Text"));
            }
        }
        NBTTagList deliveredList = nbt.getTagList("QuotaDelivered", 10);
        for (int i = 0; i < deliveredList.tagCount(); i++) {
            NBTTagCompound entry = deliveredList.getCompoundTagAt(i);
            String id = entry.getString("ID");
            if (KOMEProgressionAchievement.forID(id) != null) {
                quotaDelivered.put(id, Integer.valueOf(entry.getInteger("Amount")));
            }
        }
        pledgedLordID = nbt.getString("PledgedLordID");
        pledgedLordName = nbt.getString("PledgedLordName");
        pledgedLordFaction = nbt.getString("PledgedLordFaction");
        clearOfferings();
        NBTTagList offeringList = nbt.getTagList("Offerings", 10);
        for (int i = 0; i < offeringList.tagCount(); i++) {
            NBTTagCompound entry = offeringList.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot >= 0 && slot < offerings.length) {
                offerings[slot] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (String id : completed) {
            list.appendTag(new NBTTagString(id));
        }
        nbt.setTag("Completed", list);
        NBTTagList assignmentList = new NBTTagList();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            NBTTagCompound assignment = new NBTTagCompound();
            assignment.setString("ID", entry.getKey());
            assignment.setString("Text", entry.getValue());
            assignmentList.appendTag(assignment);
        }
        nbt.setTag("Assignments", assignmentList);
        NBTTagList deliveredList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : quotaDelivered.entrySet()) {
            NBTTagCompound delivered = new NBTTagCompound();
            delivered.setString("ID", entry.getKey());
            delivered.setInteger("Amount", entry.getValue().intValue());
            deliveredList.appendTag(delivered);
        }
        nbt.setTag("QuotaDelivered", deliveredList);
        nbt.setString("PledgedLordID", pledgedLordID);
        nbt.setString("PledgedLordName", pledgedLordName);
        nbt.setString("PledgedLordFaction", pledgedLordFaction);
        NBTTagList offeringList = new NBTTagList();
        for (int i = 0; i < offerings.length; i++) {
            if (offerings[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("Slot", (byte) i);
                offerings[i].writeToNBT(entry);
                offeringList.appendTag(entry);
            }
        }
        nbt.setTag("Offerings", offeringList);
        return nbt;
    }
}
