package com.lotrcharactercreation.faction;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.client.LOTRAlignmentTicker;
import org.junit.Test;
import static org.junit.Assert.*;

public class CharacterAlignmentViewTest {
    static class ViewData extends LOTRPlayerData {
        LOTRFaction viewing,regional;
        ViewData(){super(UUID.randomUUID());}
        @Override public void setViewingFaction(LOTRFaction value){viewing=value;}
        @Override public void setRegionLastViewedFaction(lotr.common.LOTRDimension.DimensionRegion region,LOTRFaction value){assertSame(value.factionRegion,region);regional=value;}
    }
    @Test public void selectedFactionIsUsedForInitialAndRegionalView() {
        for(LOTRFaction faction:new LOTRFaction[]{LOTRFaction.ROHAN,LOTRFaction.GONDOR,LOTRFaction.MORDOR}){
            ViewData data=new ViewData();StartingFactionApplication.applyInitialAlignmentView(data,faction);
            assertSame(faction,data.viewing);assertSame(faction,data.regional);
        }
    }
    @Test public void nativeInitializedTickerShowsLiveGainLossAndRelog()throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.pledge(LOTRFaction.ROHAN);
        LOTRPlayerData data=LOTRLevelData.getData(f.player);
        Field alignments=LOTRPlayerData.class.getDeclaredField("alignments");alignments.setAccessible(true);
        Map map=(Map)alignments.get(data);LOTRAlignmentTicker ticker=LOTRAlignmentTicker.forFaction(LOTRFaction.ROHAN);
        map.put(LOTRFaction.ROHAN,150F);ticker.update(f.player,true);assertEquals(150,ticker.getInterpolatedAlignment(1),0);
        map.put(LOTRFaction.ROHAN,175F);ticker.update(f.player,false);for(int i=0;i<20;i++)ticker.update(f.player,false);
        assertEquals(175,ticker.getInterpolatedAlignment(1),0);assertTrue(ticker.numericalTick>0);
        map.put(LOTRFaction.ROHAN,125F);ticker.update(f.player,false);for(int i=0;i<20;i++)ticker.update(f.player,false);
        assertEquals(125,ticker.getInterpolatedAlignment(1),0);
        ticker.update(f.player,true);assertEquals(125,ticker.getInterpolatedAlignment(1),0);
    }
    @Test public void creationDefaultIsOnlyInsideOneTimeApplicationGuard()throws Exception {
        String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/com/lotrcharactercreation/faction/StartingFactionApplication.java")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(source.indexOf("isStartingFactionApplied")<source.indexOf("applyInitialAlignmentView(lotrData,selectedPledge)"));
        assertTrue(source.indexOf("applyInitialAlignmentView(lotrData,selectedPledge)")<source.lastIndexOf("setStartingFactionApplied(player, true)"));
    }
    @Test public void nativeSaveReloadPreservesDefaultAndLaterManualFactionChoice()throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        lotr.common.LOTRCommonProxy previous=lotr.common.LOTRMod.proxy;
        lotr.common.LOTRMod.proxy=new lotr.common.LOTRCommonProxy(){
            @Override public boolean isClient(){return true;}
            @Override public net.minecraft.world.World getClientWorld(){return fixture.world;}
        };
        try {
        LOTRPlayerData data=new LOTRPlayerData(UUID.randomUUID());
        StartingFactionApplication.applyInitialAlignmentView(data,LOTRFaction.ROHAN);
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();data.save(saved);
        LOTRPlayerData reload=new LOTRPlayerData(UUID.randomUUID());reload.load(saved);
        assertSame(LOTRFaction.ROHAN,reload.getViewingFaction());
        assertSame(LOTRFaction.ROHAN,reload.getRegionLastViewedFaction(LOTRFaction.ROHAN.factionRegion));
        reload.setViewingFaction(LOTRFaction.GONDOR);reload.setRegionLastViewedFaction(LOTRFaction.GONDOR.factionRegion,LOTRFaction.GONDOR);
        reload.save(saved);data.load(saved);assertSame(LOTRFaction.GONDOR,data.getViewingFaction());
        assertSame(LOTRFaction.GONDOR,data.getRegionLastViewedFaction(LOTRFaction.GONDOR.factionRegion));
        } finally {lotr.common.LOTRMod.proxy=previous;}
    }
}
