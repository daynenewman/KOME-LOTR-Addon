package kome.verification;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEMusterNativeRoster;
import kome.common.data.KOMEMusterRoster;
import lotr.common.LOTRDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/** Test-only mod: real Forge registration/factories, no player ownership and no deployment. */
@Mod(modid="kom11verification",name="KOM-11 disposable native verification",version="1",dependencies="required-after:kome")
public final class MusterNativeVerification {
    @Mod.EventHandler public void verify(FMLServerStartedEvent event) throws Exception {
        StringBuilder evidence=new StringBuilder("faction\tkey\tentity\tmount\tcost\tweight\n");
        try {
            int dimension=LOTRDimension.MIDDLE_EARTH.dimensionID;
            if(DimensionManager.getWorld(dimension)==null)DimensionManager.initDimension(dimension);
            World world=DimensionManager.getWorld(dimension);
            int before=world.loadedEntityList.size(), units=0;
            for(String faction:KOMEAlliance.allFactionKeys()) {
                List<KOMEMusterRoster.Unit> roster=new KOMEMusterNativeRoster(world).resolve(faction);
                if(roster.isEmpty())throw new IllegalStateException("Empty native roster: "+faction);
                for(KOMEMusterRoster.Unit unit:roster) {
                    if(!faction.equals(unit.faction)||"rohan".equals(faction)&&!unit.mounted())
                        throw new IllegalStateException("Invalid native roster: "+unit.key);
                    evidence.append(faction).append('\t').append(unit.key).append('\t').append(unit.entityId)
                        .append('\t').append(unit.mountId).append('\t').append(unit.cost).append('\t').append(unit.weight).append('\n');
                    units++;
                }
            }
            if(world.loadedEntityList.size()!=before)throw new IllegalStateException("Native roster probe spawned an entity.");
            evidence.append("PASS factions=24 units=").append(units).append("; no entities spawned\n");
            Files.write(Paths.get("native-rosters.tsv"),evidence.toString().getBytes(StandardCharsets.UTF_8));
        } catch(Throwable failure) {
            evidence.append("FAIL ").append(failure.toString()).append('\n');
            Files.write(Paths.get("native-rosters.tsv"),evidence.toString().getBytes(StandardCharsets.UTF_8));
            throw new RuntimeException("KOM-11 native verification failed",failure);
        } finally {MinecraftServer.getServer().initiateShutdown();}
    }
}
