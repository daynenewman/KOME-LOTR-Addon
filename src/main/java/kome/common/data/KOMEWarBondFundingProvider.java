package kome.common.data;
/** Future economy adapter. Population is intentionally absent from this contract. */
public interface KOMEWarBondFundingProvider {
    Result debit(String faction, int amount, String reference);
    Result credit(String faction, int amount, String reference);
    final class Result { public final boolean success; public final String reason; private Result(boolean ok,String why){success=ok;reason=why;} public static Result ok(){return new Result(true,"");} public static Result deny(String reason){return new Result(false,reason);} }
}
