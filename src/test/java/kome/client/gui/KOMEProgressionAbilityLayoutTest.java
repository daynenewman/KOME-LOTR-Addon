package kome.client.gui;
import java.util.*;
import org.junit.Test;
import kome.common.data.KOMEProgressionAbilityPolicy.*;
import kome.common.data.KOMEProgressionPermissionRegistry.Ability;
import static org.junit.Assert.*;
public class KOMEProgressionAbilityLayoutTest {
    @Test public void currentAndFutureGroupsAccountForHeadersAndRows() {
        List<Outcome> rows=Arrays.asList(new Outcome(Ability.BATTLE_SERVICE.id,true,Availability.CONTEXT_REQUIRED,"Native company checks apply.",""),
            new Outcome(Ability.CAMPAIGN_RECRUITMENT.id,false,Availability.LOCKED,"Requires Lord standing.",""));
        assertEquals(80,KOMEProgressionRankLayout.abilityHeight(rows));
        assertEquals(0,KOMEProgressionRankLayout.abilityHeight(Collections.<Outcome>emptyList()));
    }
    @Test public void princeInheritedRowsNeedOnlyOneHeader() {
        List<Outcome> rows=new ArrayList<>();
        for(Ability a:Ability.values())rows.add(new Outcome(a.id,true,Availability.CONTEXT_REQUIRED,"Action checks apply.",""));
        assertEquals(20+20*Ability.values().length,KOMEProgressionRankLayout.abilityHeight(rows));
    }
}
