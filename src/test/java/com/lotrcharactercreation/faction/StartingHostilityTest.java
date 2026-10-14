package com.lotrcharactercreation.faction;

import java.util.*;
import lotr.common.LOTRPlayerData;
import lotr.common.fac.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StartingHostilityTest {
    static class Data extends LOTRPlayerData {
        final Map<LOTRFaction,Float> values=new EnumMap<LOTRFaction,Float>(LOTRFaction.class);
        final Map<LOTRFaction,Integer> writes=new EnumMap<LOTRFaction,Integer>(LOTRFaction.class);
        Data(){super(UUID.randomUUID());}
        @Override public float getAlignment(LOTRFaction f){return values.containsKey(f)?values.get(f):0F;}
        @Override public void setAlignment(LOTRFaction f,float value){values.put(f,value);writes.put(f,writes.containsKey(f)?writes.get(f)+1:1);}
    }
    @Test public void nativeEnemyAndMortalEnemyRelationsApplyOneAdditivePenalty(){
        for(LOTRFaction selected:new LOTRFaction[]{LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFaction.MORDOR,LOTRFaction.HIGH_ELF}){
            Data data=new Data();data.values.put(selected,150F);
            Set<LOTRFaction> expected=new HashSet<LOTRFaction>(selected.getOthersOfRelation(LOTRFactionRelations.Relation.ENEMY));expected.addAll(selected.getOthersOfRelation(LOTRFactionRelations.Relation.MORTAL_ENEMY));expected.remove(selected);
            for(LOTRFaction f:expected)data.values.put(f,7F);
            StartingFactionApplication.applyStartingHostility(data,selected);assertEquals(expected,data.writes.keySet());
            for(LOTRFaction f:expected){assertEquals(6F,data.getAlignment(f),0);assertEquals(Integer.valueOf(1),data.writes.get(f));}
            assertEquals(150F,data.getAlignment(selected),0);
        }
    }
    @Test public void nativeRelationOverridesDriveEnumeration(){
        LOTRFaction a=LOTRFaction.GONDOR,b=LOTRFaction.ROHAN;LOTRFactionRelations.Relation before=LOTRFactionRelations.getRelations(a,b);
        try{LOTRFactionRelations.overrideRelations(a,b,LOTRFactionRelations.Relation.ENEMY);assertTrue(StartingFactionApplication.hostileFactions(a).contains(b));
            LOTRFactionRelations.overrideRelations(a,b,LOTRFactionRelations.Relation.MORTAL_ENEMY);assertEquals(1,Collections.frequency(new ArrayList<LOTRFaction>(StartingFactionApplication.hostileFactions(a)),b));
            LOTRFactionRelations.overrideRelations(a,b,LOTRFactionRelations.Relation.ALLY);assertFalse(StartingFactionApplication.hostileFactions(a).contains(b));
            LOTRFactionRelations.overrideRelations(a,b,LOTRFactionRelations.Relation.NEUTRAL);assertFalse(StartingFactionApplication.hostileFactions(a).contains(b));
        }finally{LOTRFactionRelations.overrideRelations(a,b,before);}
    }
    @Test public void completedCharactersCannotReceiveHostilityAgain()throws Exception{
        kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();com.lotrcharactercreation.race.PlayerRaceData.setCharacterCreationComplete(f.player,true);
        assertNull(StartingFactionApplication.tryApply(f.player,true,""));assertTrue(StartingFactionApplication.hostileFactions(null).isEmpty());
    }
    @Test public void savedStartingFactionMarkerBlocksDuplicateBeforeWaypointCompletion()throws Exception{
        kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();com.lotrcharactercreation.race.PlayerRaceData.setStartingFactionApplied(f.player,true);
        assertFalse(com.lotrcharactercreation.race.PlayerRaceData.isCharacterCreationComplete(f.player));assertNull(StartingFactionApplication.tryApply(f.player,true,""));
    }
}
