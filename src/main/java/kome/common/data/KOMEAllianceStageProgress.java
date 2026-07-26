package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Directional side of a canonical mutual alliance record.
 * Stages and benefits belong to this faction only; the base LOTR relation is shared.
 */
public class KOMEAllianceStageProgress {
    public String faction = "";
    public int stage;
    public boolean produceMerchantSlotUnlocked;
    public final long[] claimedAtMillis = new long[5];
    public final long[] fixedCompletedAtMillis = new long[5];
    public String qualifyingWarId = "";
    public String qualifyingCompanyId = "";
    public long qualifyingDeploymentAtMillis;

    public KOMEAllianceStageProgress(String faction) {
        this.faction = KOMEAlliance.normalizeFactionKey(faction);
    }

    public void setStage(int value, long nowMillis) {
        int next = Math.max(0, Math.min(4, value));
        if (next > stage) {
            for (int i = stage + 1; i <= next; i++) {
                if (claimedAtMillis[i] <= 0L) claimedAtMillis[i] = Math.max(0L, nowMillis);
            }
        }
        stage = next;
        if (stage >= 2) produceMerchantSlotUnlocked = true;
    }

    public void resetFormalProgress() {
        stage = 0;
        for (int i = 1; i < claimedAtMillis.length; i++) claimedAtMillis[i] = 0L;
        for (int i = 1; i < fixedCompletedAtMillis.length; i++) fixedCompletedAtMillis[i] = 0L;
        qualifyingWarId = "";
        qualifyingCompanyId = "";
        qualifyingDeploymentAtMillis = 0L;
        // produceMerchantSlotUnlocked deliberately persists after a break.
    }

    public void markFixedComplete(int targetStage, long nowMillis) {
        if (targetStage >= 1 && targetStage <= 4 && fixedCompletedAtMillis[targetStage] <= 0L) {
            fixedCompletedAtMillis[targetStage] = Math.max(0L, nowMillis);
        }
    }

    public boolean isFixedComplete(int targetStage) {
        return targetStage >= 1 && targetStage <= 4 && fixedCompletedAtMillis[targetStage] > 0L;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
        nbt.setInteger("Stage", Math.max(0, Math.min(4, stage)));
        nbt.setBoolean("ProduceMerchantSlotUnlocked", produceMerchantSlotUnlocked);
        nbt.setString("QualifyingWarId", safe(qualifyingWarId));
        nbt.setString("QualifyingCompanyId", safe(qualifyingCompanyId));
        nbt.setLong("QualifyingDeploymentAtMillis", Math.max(0L, qualifyingDeploymentAtMillis));
        NBTTagList claims = new NBTTagList();
        for (int i = 1; i <= 4; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Stage", i);
            entry.setLong("ClaimedAtMillis", Math.max(0L, claimedAtMillis[i]));
            entry.setLong("FixedCompletedAtMillis", Math.max(0L, fixedCompletedAtMillis[i]));
            claims.appendTag(entry);
        }
        nbt.setTag("StageClaims", claims);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
        stage = Math.max(0, Math.min(4, nbt.getInteger("Stage")));
        produceMerchantSlotUnlocked = nbt.getBoolean("ProduceMerchantSlotUnlocked") || stage >= 2;
        qualifyingWarId = safe(nbt.getString("QualifyingWarId"));
        qualifyingCompanyId = safe(nbt.getString("QualifyingCompanyId"));
        qualifyingDeploymentAtMillis = Math.max(0L, nbt.getLong("QualifyingDeploymentAtMillis"));
        NBTTagList claims = nbt.getTagList("StageClaims", 10);
        for (int i = 0; i < claims.tagCount(); i++) {
            NBTTagCompound entry = claims.getCompoundTagAt(i);
            int target = entry.getInteger("Stage");
            if (target >= 1 && target <= 4) {
                claimedAtMillis[target] = Math.max(0L, entry.getLong("ClaimedAtMillis"));
                fixedCompletedAtMillis[target] = Math.max(0L, entry.getLong("FixedCompletedAtMillis"));
            }
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
