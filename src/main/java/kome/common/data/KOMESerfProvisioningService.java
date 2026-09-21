package kome.common.data;

import lotr.common.item.LOTRItemMug;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Exact-item server-side provisioning delivery; never trusts client counts. */
public final class KOMESerfProvisioningService {
    private KOMESerfProvisioningService(){}
    static final class StackView {final int itemId,damage,count;final String vessel;StackView(int itemId,int damage,int count,String vessel){this.itemId=itemId;this.damage=damage;this.count=count;this.vessel=vessel==null?"":vessel;}}
    public static int deliver(KOMESerfProvisioningAssignment assignment,InventoryPlayer inventory){
        if(assignment==null||inventory==null)return 0;StackView[] views=new StackView[inventory.mainInventory.length];for(int i=0;i<views.length;i++)views[i]=view(inventory.mainInventory[i]);int[] plan=calculateTakeAmounts(assignment,views);
        int changed=0;for(int slot=0;slot<plan.length;slot++){if(plan[slot]<=0)continue;ItemStack stack=inventory.mainInventory[slot];if(stack==null||stack.stackSize<plan[slot])return 0;KOMESerfProvisioningAssignment.Requirement requirement=matching(views[slot],assignment);if(requirement==null)return 0;}
        for(int slot=0;slot<plan.length;slot++){int amount=plan[slot];if(amount<=0)continue;ItemStack stack=inventory.mainInventory[slot];KOMESerfProvisioningAssignment.Requirement requirement=matching(views[slot],assignment);stack.stackSize-=amount;requirement.delivered+=amount;changed+=amount;if(stack.stackSize<=0)inventory.mainInventory[slot]=null;}
        if(changed>0)inventory.markDirty();return changed;
    }
    static int[] calculateTakeAmounts(KOMESerfProvisioningAssignment assignment,StackView[] stacks){int[] take=new int[stacks.length];for(KOMESerfProvisioningAssignment.Requirement r:assignment.foods)plan(r,stacks,take);plan(assignment.drink,stacks,take);return take;}
    private static void plan(KOMESerfProvisioningAssignment.Requirement r,StackView[] stacks,int[] take){int remaining=r.required-r.delivered;for(int i=0;i<stacks.length&&remaining>0;i++){StackView stack=stacks[i];if(!matchesIdentity(stack,r))continue;int amount=Math.min(stack.count-take[i],remaining);if(amount>0){take[i]+=amount;remaining-=amount;}}}
    private static KOMESerfProvisioningAssignment.Requirement matching(StackView stack,KOMESerfProvisioningAssignment assignment){for(KOMESerfProvisioningAssignment.Requirement r:assignment.foods)if(matchesIdentity(stack,r))return r;return matchesIdentity(stack,assignment.drink)?assignment.drink:null;}
    static boolean matchesIdentity(StackView stack,KOMESerfProvisioningAssignment.Requirement r){return stack!=null&&r!=null&&stack.itemId==r.itemId&&stack.damage==r.damage&&(!r.isDrink()||r.vessel.equals(stack.vessel));}
    private static StackView view(ItemStack stack){if(stack==null||stack.getItem()==null)return null;String vessel="";if(stack.getItem() instanceof LOTRItemMug)try{vessel=LOTRItemMug.getVessel(stack).name();}catch(Exception ignored){}return new StackView(Item.getIdFromItem(stack.getItem()),stack.getItemDamage(),stack.stackSize,vessel);}
    public static boolean matches(ItemStack stack,KOMESerfProvisioningAssignment.Requirement r){return matchesIdentity(view(stack),r);}
}
