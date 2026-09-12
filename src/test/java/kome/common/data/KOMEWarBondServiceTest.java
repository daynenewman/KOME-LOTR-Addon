package kome.common.data;
import org.junit.*; import static org.junit.Assert.*;
/** Focused provider-boundary regression tests; population is deliberately absent. */
public class KOMEWarBondServiceTest {
 static final class Fake implements KOMEWarBondFundingProvider {int debits,credits; int amount; String reason="insufficient funds"; boolean ok; public Result debit(String f,int a,String r){debits++;amount=a;return ok?Result.ok():Result.deny(reason);} public Result credit(String f,int a,String r){credits++;return ok?Result.ok():Result.deny(reason);} }
 @After public void clear(){KOMEWarService.setBondFundingProvider(null);}
 @Test public void disabledDefaultCreatesNoEscrow(){Fake f=new Fake();f.ok=true;KOMEWarService.setBondFundingProvider(f);KOMEWar w=KOMEWarService.createWar(new KOMEWorldData("x"),"gondor","mordor","","t",1L);assertNotNull(w);assertEquals(0,f.debits);assertTrue(w.bondEscrows.isEmpty());}
 @Test public void explicitEscrowResolutionIsIdempotent(){KOMEWar w=new KOMEWar();KOMEWar.BondEscrow e=new KOMEWar.BondEscrow();e.faction="gondor";e.amount=4;w.bondEscrows.add(e);Fake f=new Fake();f.ok=true;KOMEWarService.setBondFundingProvider(f);assertTrue(KOMEWarService.resolveBond(w,e,"REFUND","",2L).allowed);assertEquals(1,f.credits);assertFalse(KOMEWarService.resolveBond(w,e,"REFUND","",3L).allowed);assertEquals(1,f.credits);}
}
