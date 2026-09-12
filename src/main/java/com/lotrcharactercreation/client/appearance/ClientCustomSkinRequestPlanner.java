package com.lotrcharactercreation.client.appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.lotrcharactercreation.network.CustomSkinRequestIdentity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pure cache-comparison planner for active external manifest definitions. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinRequestPlanner {

    private ClientCustomSkinRequestPlanner() {}

    public static List<CustomSkinRequestIdentity> findMissing(List<ClientExternalSkinDefinition> definitions,
        ContentAvailability availability) {
        if (definitions == null || availability == null) {
            throw new IllegalArgumentException("custom skin request planning inputs cannot be null");
        }
        List<CustomSkinRequestIdentity> missing = new ArrayList<CustomSkinRequestIdentity>();
        for (ClientExternalSkinDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("custom skin request definition cannot be null");
            }
            if (!availability.isAvailable(definition)) {
                missing.add(new CustomSkinRequestIdentity(definition.getPresetId(), definition.getSha256()));
            }
        }
        return Collections.unmodifiableList(missing);
    }

    public interface ContentAvailability {

        boolean isAvailable(ClientExternalSkinDefinition definition);
    }
}
