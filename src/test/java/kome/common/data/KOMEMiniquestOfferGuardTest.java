package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityRohirrimWarrior;
import lotr.common.entity.npc.LOTREntityQuestInfo;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMiniquestOfferGuardTest {
    @Test public void relationshipSuppressionIsPlayerSpecific() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        TestNpc npc=KOMEAccessFixture.allocate(TestNpc.class);npc.id=UUID.randomUUID();npc.setUniqueID(npc.id);npc.worldObj=fixture.world;
        LOTREntityQuestInfo info=KOMEAccessFixture.allocate(LOTREntityQuestInfo.class);
        java.lang.reflect.Field owner=LOTREntityQuestInfo.class.getDeclaredField("theNPC");owner.setAccessible(true);owner.set(info,npc);
        assertTrue("fresh players retain LOTR's ordinary offer path without an advancement",KOMEMiniquestOfferGuard.allowOffer(info,fixture.player));
        KOMEPlayerProgression serving=fixture.data.getProgression(fixture.player.id);serving.setCanonicalRank(KOMEProgressionRank.SERF);
        serving.getSerfKnightProgression().setSerfdomMaster(new KOMEProgressionNpcRef(npc.id.toString(),"Aldor","rohan",0,0,64,0));
        KOMEAccessFixture.Player other=KOMEAccessFixture.allocate(KOMEAccessFixture.Player.class);other.id=UUID.randomUUID();other.worldObj=fixture.world;
        assertTrue(KOMEMiniquestOfferGuard.isRelationshipNpc(fixture.player,npc));
        assertFalse(KOMEMiniquestOfferGuard.isRelationshipNpc(other,npc));
        assertFalse("only the serving player suppresses new quests from this NPC",KOMEMiniquestOfferGuard.allowOffer(info,fixture.player));
        assertTrue("other players retain the native offer path",KOMEMiniquestOfferGuard.allowOffer(info,other));
    }
    public static final class TestNpc extends LOTREntityRohirrimWarrior {UUID id;private TestNpc(){super(null);}@Override public UUID getUniqueID(){return id;}@Override public LOTRFaction getFaction(){return LOTRFaction.ROHAN;}}
}
