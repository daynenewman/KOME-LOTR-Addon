package com.lotrcharactercreation.client.appearance;

import java.util.HashMap;
import java.util.Map;

import com.lotrcharactercreation.network.CustomSkinSyncProtocol;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pure per-content-identity whole-file retry accounting. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinRetryState {

    private final Map<ClientCustomSkinIdentity, Integer> failureCounts =
        new HashMap<ClientCustomSkinIdentity, Integer>();

    public int recordFailure(ClientCustomSkinIdentity identity) {
        if (identity == null) {
            throw new IllegalArgumentException("custom skin retry identity cannot be null");
        }
        int count = failureCounts.containsKey(identity) ? failureCounts.get(identity).intValue() + 1 : 1;
        failureCounts.put(identity, Integer.valueOf(count));
        return count;
    }

    public boolean shouldRetry(int failureCount) {
        return failureCount > 0 && failureCount <= CustomSkinSyncProtocol.MAX_FILE_RETRIES;
    }

    public void succeeded(ClientCustomSkinIdentity identity) {
        failureCounts.remove(identity);
    }

    public int getFailureCount(ClientCustomSkinIdentity identity) {
        Integer count = failureCounts.get(identity);
        return count == null ? 0 : count.intValue();
    }

    public void clear() {
        failureCounts.clear();
    }
}
