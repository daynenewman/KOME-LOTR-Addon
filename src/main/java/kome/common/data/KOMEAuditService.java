package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Single bounded, persisted audit stream for cross-domain inspection. */
public final class KOMEAuditService {
    public static final int MAX_ENTRIES = 500;
    private KOMEAuditService() { }

    public static KOMEAuditEntry record(KOMEWorldData data, long timestamp, String domain, String action,
            String actor, String subject, String reason, String details) {
        if (data == null) return null;
        KOMEAuditEntry entry = new KOMEAuditEntry(timestamp, domain, action, actor, subject, reason, details);
        if (entry.action.length() == 0 || entry.reason.length() == 0) return null;
        data.centralAudit.add(entry); while (data.centralAudit.size() > MAX_ENTRIES) data.centralAudit.remove(0);
        data.markDirty(); return entry;
    }
    public static List<KOMEAuditEntry> entries(KOMEWorldData data) {
        return data == null ? Collections.<KOMEAuditEntry>emptyList()
            : Collections.unmodifiableList(new ArrayList<KOMEAuditEntry>(data.centralAudit));
    }
    public static List<String> summary(KOMEWorldData data) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        if (data != null) for (KOMEAuditEntry entry : data.centralAudit) {
            String key = entry.domain + "/" + entry.action;
            counts.put(key, Integer.valueOf(counts.containsKey(key) ? counts.get(key).intValue() + 1 : 1));
        }
        List<String> result = new ArrayList<String>(); for (Map.Entry<String, Integer> item : counts.entrySet())
            result.add(item.getKey() + "=" + item.getValue());
        return result;
    }
    public static void writeToNBT(KOMEWorldData data, NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList(); if (data != null) for (KOMEAuditEntry entry : data.centralAudit) list.appendTag(entry.writeToNBT());
        nbt.setTag("CentralAudit", list);
    }
    public static void readFromNBT(KOMEWorldData data, NBTTagCompound nbt) {
        if (data == null) return; data.centralAudit.clear(); NBTTagList list = nbt.getTagList("CentralAudit", 10);
        for (int i = 0; i < list.tagCount() && data.centralAudit.size() < MAX_ENTRIES; i++) data.centralAudit.add(KOMEAuditEntry.readFromNBT(list.getCompoundTagAt(i)));
    }
}
