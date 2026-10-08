package kome.common.data;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Inventory is projected without consuming it or changing persisted deposits. */
public final class KOMEProgressionGoodsPresentation {
    private KOMEProgressionGoodsPresentation(){}
    public static String objective(KOMEPlayerProgression p,ItemStack[] inventory){
        KOMESerfKnightProgression state=p.getSerfKnightProgression();
        StringBuilder text=new StringBuilder();
        if("provisioning".equals(state.getActiveAssignmentKind())){
            KOMESerfProvisioningAssignment a=KOMESerfProvisioningAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());
            if(a==null)return "";text.append("Bring to your Master:");
            for(KOMESerfProvisioningAssignment.Requirement r:a.foods)provision(text,r,inventory);
            provision(text,a.drink,inventory);
        }else if("profession".equals(state.getActiveAssignmentKind())){
            KOMESerfProfessionAssignment a=KOMESerfProfessionAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.PROFESSION).getAssignmentData());
            if(a==null)return "";text.append("Bring to your Master:");
            for(KOMESerfProfessionAssignment.Requirement r:a.requirements){
                int have=r.delivered;if(inventory!=null)for(ItemStack s:inventory)if(KOMESerfProfessionService.matches(s,r))have+=s.stackSize;
                line(text,KOMEProgressionGoodsRules.name(r.requestedStack(),r.displayName),have,r.required);
            }
        }else{
            KOMEKnightCommission a=p.getKnightService().assignment();
            if(a!=null&&a.live()&&a.type==KOMEKnightCommission.Type.RELIEF){
                text.append("Bring relief to your people:");
                for(KOMEKnightCommission.Goods g:a.goods){
                    Item item=(Item)Item.itemRegistry.getObject(g.itemKey);if(item==null)continue;
                    ItemStack target=new ItemStack(item,1,g.damage);int have=g.delivered;
                    if(inventory!=null)for(ItemStack s:inventory)if(s!=null&&s.getItem()==item&&s.getItemDamage()==g.damage)have+=s.stackSize;
                    line(text,target.getDisplayName(),have,g.required);
                }
            }
        }
        return text.toString();
    }
    private static void provision(StringBuilder text,KOMESerfProvisioningAssignment.Requirement r,ItemStack[] inventory){
        int have=r.delivered;if(inventory!=null)for(ItemStack s:inventory)if(KOMESerfProvisioningService.matches(s,r))have+=s.stackSize;
        line(text,KOMEProgressionGoodsRules.name(r.requestedStack(),r.displayName),have,r.required);
    }
    private static void line(StringBuilder text,String name,int have,int required){text.append("\n").append(name).append(": ").append(Math.min(required,have)).append(" / ").append(required);}
    public static KOMEProgressionRankSummary enrich(KOMEProgressionRankSummary summary,KOMEPlayerProgression p,ItemStack[] inventory){
        String objective=objective(p,inventory);
        return objective.isEmpty()?summary:new KOMEProgressionRankSummary(summary.factionKey,summary.currentRank,summary.nextRank,summary.promotionTitle,summary.requirements,summary.activityHeading,summary.activityTitle,objective);
    }
}
