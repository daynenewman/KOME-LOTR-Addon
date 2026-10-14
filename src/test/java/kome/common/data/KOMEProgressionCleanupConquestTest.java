package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.KOMETestServerSession;
import kome.common.network.*;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionCleanupConquestTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void everyRelationshipAndUnclaimedCaseFailsWithoutChangingWarOwnershipOrTimers() {
        for (KOMEDiplomacyRelation relation : KOMEDiplomacyRelation.values()) {
            LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR, LOTRFaction.ROHAN, relation.toLotrRelation());
            try {
                KOMEWorldData data = new KOMEWorldData("claims"); UUID actor = UUID.randomUUID();
                data.getProgression(actor).setCanonicalRank(KOMEProgressionRank.LORD);
                data.getProgression(actor).grant("baseline.take_waypoints");
                KOMERulerService.assignRuler(data,"gondor",actor,"King");
                for (String owner : new String[]{"rohan", "gondor", ""}) {
                    KOMEConquestTile tile = new KOMEConquestTile("T100");
                    if (!owner.isEmpty()) tile.claim(owner, 1L);
                    data.conquestTiles.put(tile.id,tile); net.minecraft.nbt.NBTTagCompound before=tile.writeToNBT();
                    assertFalse(KOMEConquestClaimService.claim(data,tile,"gondor",actor,"King",20L,30L).success);
                    assertEquals(before,tile.writeToNBT()); assertTrue(data.wars.isEmpty());
                    assertNull(data.getConflictService().get(tile.id));
                    assertEquals(relation,KOMEDiplomacyService.getRelation(data,"gondor","rohan"));
                }
            } finally { LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.NEUTRAL); }
        }
    }

    @Test public void forgedQueuedLordClaimNeverCreatesWarOrMutatesTerritoryEvenWithProgressionDisabled() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture(); SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        KOMEPacketHandler.network=f.network;
        try (KOMETestServerSession session=new KOMETestServerSession(f)) {
            f.pledge(LOTRFaction.GONDOR); f.data.setProgressionEnabled(false);
            f.data.getProgression(f.player.id).setCanonicalRank(KOMEProgressionRank.LORD);
            f.data.getProgression(f.player.id).grant("baseline.take_waypoints");
            KOMEConquestTile tile=new KOMEConquestTile("T100"); tile.claim("rohan",0L); f.data.conquestTiles.put(tile.id,tile);
            KOMEPacketConquestClaim intent=new KOMEPacketConquestClaim(tile.id);
            KOMEPacketHandler.ServerThreadHandler<KOMEPacketConquestClaim> handler=
                new KOMEPacketHandler.ServerThreadHandler<KOMEPacketConquestClaim>(new KOMEPacketConquestClaim.Handler()) {};
            handler.onMessage(intent,f.context); handler.onMessage(intent,f.context);
            assertEquals("rohan",tile.projectRulingFaction());
            int drained=0; for(int i=0;i<10 && drained<2;i++) drained+=KOMEPacketHandler.runPendingServerTasks();
            assertEquals(2,drained);
            assertEquals("rohan",tile.projectRulingFaction()); assertTrue(f.data.wars.isEmpty());
            assertTrue(f.player.messages.stream().anyMatch(s->s.contains("cannot certify")));
            for(Object message:f.network.messages) if(message instanceof KOMEPacketConquestCaptureGui)
                assertFalse(((KOMEPacketConquestCaptureGui)message).canClaim);
        } finally {KOMEPacketHandler.network=previous; KOMEPacketHandler.clearPendingServerTasks();}
    }

    @Test public void rulerOfferAndAcceptanceStillTransferWithoutRankOrWarShortcut() throws Exception {
        KOMEAccessFixture first=new KOMEAccessFixture(), second=new KOMEAccessFixture();
        SimpleNetworkWrapper previous=KOMEPacketHandler.network; KOMEPacketHandler.network=first.network;
        try {
            first.pledge(LOTRFaction.ROHAN);second.pledge(LOTRFaction.GONDOR);
            second.player.worldObj=first.world; first.world.playerEntities.add(second.player);
            KOMEWorldData data=first.data; KOMEConquestTile tile=new KOMEConquestTile("T100"); tile.claim("rohan",0L);
            data.conquestTiles.put(tile.id,tile);
            KOMERulerService.assignRuler(data,"rohan",first.player.id,"First");
            KOMERulerService.assignRuler(data,"gondor",second.player.id,"Second");
            new KOMEPacketConquestTransfer.Handler().onMessage(new KOMEPacketConquestTransfer(tile.id,LOTRFaction.GONDOR.codeName()),first.context);
            assertTrue(first.player.messages.toString(),tile.hasPendingTransfer()); assertEquals("rohan",tile.projectRulingFaction());
            new KOMEPacketConquestTransfer.Handler().onMessage(new KOMEPacketConquestTransfer(tile.id,"gondor",KOMEPacketConquestTransfer.ACCEPT),second.context);
            assertEquals("gondor",tile.projectRulingFaction()); assertTrue(data.wars.isEmpty());
        } finally {KOMEPacketHandler.network=previous;}
    }
}
