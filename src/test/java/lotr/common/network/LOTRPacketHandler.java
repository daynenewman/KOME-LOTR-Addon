package lotr.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraft.entity.Entity;

/** Test-only native transport seam: plain JUnit cannot bootstrap Forge's LaunchClassLoader.
 * Native hired-info helpers, codecs and message handlers remain the real v36.15 classes.
 */
public final class LOTRPacketHandler {
    public static SimpleNetworkWrapper networkWrapper;
    public static NetworkRegistry.TargetPoint nearEntity(Entity entity,double range){
        return new NetworkRegistry.TargetPoint(entity.dimension,entity.posX,entity.posY,entity.posZ,range);
    }
}
