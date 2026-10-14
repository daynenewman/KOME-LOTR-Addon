package kome.common.data;
import java.util.UUID;
import org.junit.Test;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import static org.junit.Assert.*;
public class KOMEProgressionMilitaryAuthorityTest {
    @Test public void lordCannotStartNativeHostileConquestOutsideDeclaredWar() {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.ENEMY);
        try {
            KOMEWorldData data=new KOMEWorldData("military");UUID actor=UUID.randomUUID();
            KOMECanonicalRankService.setCanonicalRank(data,actor,KOMEProgressionRank.LORD);
            data.lastKnownPlayerFactions.put(actor,"gondor");
            KOMEConquestTile tile=new KOMEConquestTile("T100");tile.claim("rohan",0L);data.conquestTiles.put(tile.id,tile);
            KOMEArmyCompany company=new KOMEArmyCompany();company.owner=actor;company.faction="gondor";
            String denial=KOMECompanyCommandAuthority.newMilitaryOperationDenial(data,company,actor,tile.id,false);
            assertTrue(denial.contains("recognized faction ruler"));assertTrue(data.wars.isEmpty());
            assertEquals("rohan",tile.projectRulingFaction());
            assertEquals(KOMEDiplomacyRelation.ENEMIES,KOMEDiplomacyService.getRelation(data,"gondor","rohan"));
            KOMERulerService.assignRuler(data,"gondor",actor,"Ruler");
            assertEquals("",KOMECompanyCommandAuthority.newMilitaryOperationDenial(data,company,actor,tile.id,false));
        } finally { LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.NEUTRAL); }
    }
    @Test public void staffAndAlreadyDeclaredWarOrdersRemainAuthorized() {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.ENEMY);
        try {
            KOMEWorldData data=new KOMEWorldData("military");UUID actor=UUID.randomUUID();
            KOMEConquestTile tile=new KOMEConquestTile("T100");tile.claim("rohan",0L);data.conquestTiles.put(tile.id,tile);
            KOMEArmyCompany company=new KOMEArmyCompany();company.owner=actor;company.faction="gondor";
            assertEquals("",KOMECompanyCommandAuthority.newMilitaryOperationDenial(data,company,actor,tile.id,true));
            KOMEWarService.createWar(data,"gondor","rohan","Declared","Ruler",1L);
            assertNotNull(KOMEWarService.findActiveOpposition(data,"gondor","rohan"));
            assertEquals("",KOMECompanyCommandAuthority.newMilitaryOperationDenial(data,company,actor,tile.id,false));
        } finally { LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.NEUTRAL); }
    }
    @Test public void normalFriendlyMovementDoesNotCreatePoliticalAuthorityOrWar() {
        KOMEWorldData data=new KOMEWorldData("military");UUID actor=UUID.randomUUID();
        KOMEConquestTile tile=new KOMEConquestTile("T100");tile.claim("gondor",0L);data.conquestTiles.put(tile.id,tile);
        KOMEArmyCompany company=new KOMEArmyCompany();company.owner=actor;company.faction="gondor";
        assertEquals("",KOMECompanyCommandAuthority.newMilitaryOperationDenial(data,company,actor,tile.id,false));
        assertTrue(data.wars.isEmpty());assertFalse(KOMERulerAuthorization.canActAsRuler(data,"gondor",actor));
    }
}
