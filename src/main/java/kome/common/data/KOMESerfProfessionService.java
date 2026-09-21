package kome.common.data;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Exact-item, exact-metadata server-side Profession delivery. */
public final class KOMESerfProfessionService {
    private KOMESerfProfessionService(){}
    static final class StackView {final int itemId,damage,count;StackView(int itemId,int damage,int count){this.itemId=itemId;this.damage=damage;this.count=count;}}
    public static int deliver(KOMESerfProfessionAssignment assignment,InventoryPlayer inventory){
        if(assignment==null||inventory==null)return 0;StackView[] views=new StackView[inventory.mainInventory.length];for(int i=0;i<views.length;i++)views[i]=view(inventory.mainInventory[i]);int[] plan=calculateTakeAmounts(assignment,views);
        for(int slot=0;slot<plan.length;slot++){if(plan[slot]<=0)continue;ItemStack stack=inventory.mainInventory[slot];if(stack==null||stack.stackSize<plan[slot]||matching(views[slot],assignment)==null)return 0;}
        int changed=0;for(int slot=0;slot<plan.length;slot++){int amount=plan[slot];if(amount<=0)continue;ItemStack stack=inventory.mainInventory[slot];KOMESerfProfessionAssignment.Requirement requirement=matching(views[slot],assignment);stack.stackSize-=amount;requirement.delivered+=amount;changed+=amount;if(stack.stackSize<=0)inventory.mainInventory[slot]=null;}
        if(changed>0)inventory.markDirty();return changed;
    }
    static int[] calculateTakeAmounts(KOMESerfProfessionAssignment assignment,StackView[] stacks){int[] take=new int[stacks.length];for(KOMESerfProfessionAssignment.Requirement requirement:assignment.requirements)plan(requirement,stacks,take);return take;}
    private static void plan(KOMESerfProfessionAssignment.Requirement requirement,StackView[] stacks,int[] take){int remaining=requirement.required-requirement.delivered;for(int i=0;i<stacks.length&&remaining>0;i++){StackView stack=stacks[i];if(!matchesIdentity(stack,requirement))continue;int amount=Math.min(stack.count-take[i],remaining);if(amount>0){take[i]+=amount;remaining-=amount;}}}
    private static KOMESerfProfessionAssignment.Requirement matching(StackView stack,KOMESerfProfessionAssignment assignment){for(KOMESerfProfessionAssignment.Requirement requirement:assignment.requirements)if(matchesIdentity(stack,requirement))return requirement;return null;}
    static boolean matchesIdentity(StackView stack,KOMESerfProfessionAssignment.Requirement requirement){return stack!=null&&requirement!=null&&KOMESerfProfessionAssignment.EXACT_METADATA.equals(requirement.metadataPolicy)&&stack.itemId==requirement.itemId&&stack.damage==requirement.damage;}
    private static StackView view(ItemStack stack){return stack==null||stack.getItem()==null?null:new StackView(Item.getIdFromItem(stack.getItem()),stack.getItemDamage(),stack.stackSize);}
    public static boolean matches(ItemStack stack,KOMESerfProfessionAssignment.Requirement requirement){return matchesIdentity(view(stack),requirement);}
    public static boolean completeIfReady(KOMEPlayerProgression progression,KOMESerfProfessionAssignment assignment){if(progression==null||assignment==null||!assignment.complete())return false;KOMESerfKnightProgression.Duty duty=progression.getSerfKnightProgression().getDuty(KOMESerfKnightDutyType.PROFESSION);if(duty==null||duty.isCompleted())return false;return KOMESerfKnightService.completeDuty(progression,KOMESerfKnightDutyType.PROFESSION).success&&duty.isCompleted();}
}
