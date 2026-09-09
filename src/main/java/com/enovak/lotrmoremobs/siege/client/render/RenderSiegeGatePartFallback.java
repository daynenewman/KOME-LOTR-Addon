package com.enovak.lotrmoremobs.siege.client.render;

import com.enovak.lotrmoremobs.siege.gate.GateRegistry;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

/**
 * Draws a conservative ordinary-block fallback only while no loaded
 * controller TESR owns the GatePart. It does not animate or inspect chunks
 * beyond the already-loaded controller lookup.
 */
@SideOnly(Side.CLIENT)
public final class RenderSiegeGatePartFallback
        implements ISimpleBlockRenderingHandler {

    private final int renderId;

    public RenderSiegeGatePartFallback(int renderId) {
        this.renderId = renderId;
    }

    @Override
    public void renderInventoryBlock(
            Block block,
            int metadata,
            int modelId,
            RenderBlocks renderer
    ) {
        block.setBlockBoundsForItemRender();
        renderer.setRenderBoundsFromBlock(block);
        Tessellator tessellator = Tessellator.instance;

        GL11.glPushMatrix();
        try {
            GL11.glRotatef(90.0F, 0.0F, 1.0F, 0.0F);
            GL11.glTranslatef(-0.5F, -0.5F, -0.5F);

            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, -1.0F, 0.0F);
            renderer.renderFaceYNeg(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            0,
                            metadata
                    )
            );
            tessellator.draw();

            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, 1.0F, 0.0F);
            renderer.renderFaceYPos(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            1,
                            metadata
                    )
            );
            tessellator.draw();

            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, 0.0F, -1.0F);
            renderer.renderFaceZNeg(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            2,
                            metadata
                    )
            );
            tessellator.draw();

            tessellator.startDrawingQuads();
            tessellator.setNormal(0.0F, 0.0F, 1.0F);
            renderer.renderFaceZPos(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            3,
                            metadata
                    )
            );
            tessellator.draw();

            tessellator.startDrawingQuads();
            tessellator.setNormal(-1.0F, 0.0F, 0.0F);
            renderer.renderFaceXNeg(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            4,
                            metadata
                    )
            );
            tessellator.draw();

            tessellator.startDrawingQuads();
            tessellator.setNormal(1.0F, 0.0F, 0.0F);
            renderer.renderFaceXPos(
                    block,
                    0.0D,
                    0.0D,
                    0.0D,
                    renderer.getBlockIconFromSideAndMetadata(
                            block,
                            5,
                            metadata
                    )
            );
            tessellator.draw();
        } finally {
            GL11.glPopMatrix();
            renderer.setRenderBounds(
                    0.0D,
                    0.0D,
                    0.0D,
                    1.0D,
                    1.0D,
                    1.0D
            );
        }
    }

    @Override
    public boolean renderWorldBlock(
            IBlockAccess blockAccess,
            int x,
            int y,
            int z,
            Block block,
            int modelId,
            RenderBlocks renderer
    ) {
        if (!(blockAccess instanceof net.minecraft.world.World)
                || GateRegistry.getController(
                        (net.minecraft.world.World)blockAccess,
                        x,
                        y,
                        z
                ) != null) {
            return false;
        }
        renderer.setRenderBoundsFromBlock(block);
        return renderer.renderStandardBlock(block, x, y, z);
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return renderId;
    }
}
