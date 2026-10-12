package kome.common.data;

import java.lang.reflect.Field;
import lotr.common.entity.npc.*;
import net.minecraft.item.*;

/** Native unmodified pool prices, capped below the cheapest advertised acquisition price.
 * A quarter of the cheapest per-unit pool price leaves room for native barter discounts.
 * No service bonus: collection/farming profit is legitimate, buy-and-deliver arbitrage is not.
 * Unknown items: 10 milli-silver per unit; ordinary ingots: 100; never sell-price speculation.
 */
public final class KOMEProgressionGoodsValue {
    interface Matcher {boolean matches(LOTRTradeEntry entry,ItemStack stack);}
    static Matcher matcher=(entry,stack)->entry.matches(stack);
    public static final int MAX_PAYMENT=128;
    private KOMEProgressionGoodsValue() {}
    public static int unitMilli(ItemStack stack){
        if(stack==null||stack.getItem()==null)return 0;
        double cheapest=Double.POSITIVE_INFINITY;
        for(Field field:LOTRTradeEntries.class.getFields())if(field.getType()==LOTRTradeEntries.class){
            try{LOTRTradeEntries pool=(LOTRTradeEntries)field.get(null);if(pool==null||pool.tradeEntries==null)continue;
                // Container/enchantment entries construct dynamic contents and are not unit goods.
                for(LOTRTradeEntry entry:pool.tradeEntries){if(entry==null||entry.getClass()!=LOTRTradeEntry.class||entry.getCost()<0)continue;ItemStack offered=entry.createTradeItem();
                    if(offered!=null&&offered.getItem()!=null&&offered.stackSize>0&&matcher.matches(entry,stack))cheapest=Math.min(cheapest,entry.getCost()/(double)offered.stackSize);
                }
            }catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
        }
        return quote(cheapest,fallback(stack));
    }
    static int quote(double cheapest,int fallback){return Double.isFinite(cheapest)?Math.max(0,Math.min(1000,(int)Math.floor(cheapest*250))):fallback;}
    static int fallback(ItemStack stack){String key=String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()));return key.contains("ingot")||key.equals("lotr:copper")||key.equals("lotr:tin")?100:10;}
    static int payment(long milli){return (int)Math.min(MAX_PAYMENT,Math.max(0,milli)/1000);}
    public static int delivered(KOMESerfKnightProgression.Duty duty,KOMESerfKnightDutyType type){
        long value=0;
        if(type==KOMESerfKnightDutyType.PROVISIONING){KOMESerfProvisioningAssignment a=KOMESerfProvisioningAssignment.readFromNBT(duty.getAssignmentData());if(a==null)return 0;for(KOMESerfProvisioningAssignment.Requirement r:a.foods)value+=(long)Math.min(r.required,r.delivered)*r.valueMilli;value+=(long)Math.min(a.drink.required,a.drink.delivered)*a.drink.valueMilli;}
        else if(type==KOMESerfKnightDutyType.PROFESSION){KOMESerfProfessionAssignment a=KOMESerfProfessionAssignment.readFromNBT(duty.getAssignmentData());if(a==null)return 0;for(KOMESerfProfessionAssignment.Requirement r:a.requirements)value+=(long)Math.min(r.required,r.delivered)*r.valueMilli;}
        return payment(value);
    }
}
