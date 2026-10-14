package atomicstryker.dynamiclights.client;

/** Test-only adapter contract; no lighting engine is bundled with the addon. */
public interface IDynamicLightSource {
    net.minecraft.entity.EntityLivingBase getAttachmentEntity();
    int getLightLevel();
}
