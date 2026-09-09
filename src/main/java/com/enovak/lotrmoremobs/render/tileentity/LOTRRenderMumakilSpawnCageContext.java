package com.enovak.lotrmoremobs.render.tileentity;

import com.enovak.lotrmoremobs.render.MumakilRenderContext;
import lotr.client.render.tileentity.LOTRTileEntityMobSpawnerRenderer;
import net.minecraft.tileentity.TileEntity;

/**
 * Installs an explicit context only while LOTR's real mob-spawner/cage
 * renderer is invoking entity renderers. This covers placed cages and the
 * cage item/NEI preview without relying on FakeWorld or render coordinates.
 */
public final class LOTRRenderMumakilSpawnCageContext
        extends LOTRTileEntityMobSpawnerRenderer {
    @Override
    public void renderTileEntityAt(
            TileEntity tileEntity,
            double x,
            double y,
            double z,
            float partialTicks
    ) {
        MumakilRenderContext.Type previous =
                MumakilRenderContext.push(
                        MumakilRenderContext.Type.SPAWN_CAGE
                );
        try {
            super.renderTileEntityAt(
                    tileEntity,
                    x,
                    y,
                    z,
                    partialTicks
            );
        } finally {
            MumakilRenderContext.restore(previous);
        }
    }
}
