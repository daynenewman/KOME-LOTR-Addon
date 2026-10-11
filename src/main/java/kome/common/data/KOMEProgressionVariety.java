package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;

/** Eight recent destinations per player, plus existing commission history for type weighting. */
public final class KOMEProgressionVariety {
    private final List<NBTTagCompound> destinations=new ArrayList<NBTTagCompound>();
    public void remember(KOMEProgressionNpcRef origin,String service,double x,double z){
        NBTTagCompound n=new NBTTagCompound();n.setString("Faction",origin.factionKey);n.setString("Liege",origin.entityUuid);n.setString("Service",service);n.setInteger("Dimension",origin.dimension);n.setDouble("X",x);n.setDouble("Z",z);destinations.add(n);while(destinations.size()>8)destinations.remove(0);
    }
    public int penalty(KOMEProgressionNpcRef origin,double x,double z){int penalty=0;for(int i=0;i<destinations.size();i++){NBTTagCompound n=destinations.get(i);double dx=x-n.getDouble("X"),dz=z-n.getDouble("Z");if(n.getInteger("Dimension")==origin.dimension&&KOMEProgressionRelationshipLifecycle.sameFaction(n.getString("Faction"),origin.factionKey)&&dx*dx+dz*dz<=128*128)penalty+=i+1;}return penalty;}
    public NBTTagList write(){NBTTagList list=new NBTTagList();for(NBTTagCompound n:destinations)list.appendTag(n.copy());return list;}
    public void read(NBTTagList list){destinations.clear();for(int i=Math.max(0,list.tagCount()-8);i<list.tagCount();i++){NBTTagCompound n=list.getCompoundTagAt(i);if(KOMEKnightCommissionLocations.coordinate(n.getDouble("X"))&&KOMEKnightCommissionLocations.coordinate(n.getDouble("Z")))destinations.add((NBTTagCompound)n.copy());}}
    public void clear(){destinations.clear();}
    static List<KOMEKnightCommission.Type> order(List<KOMEKnightCommission.Type> available,List<KOMEKnightCommission> history,String faction,Random random){
        List<KOMEKnightCommission.Type> remaining=new ArrayList<KOMEKnightCommission.Type>(available),ordered=new ArrayList<KOMEKnightCommission.Type>();
        while(!remaining.isEmpty()){
            int total=0;for(KOMEKnightCommission.Type type:remaining)total+=weight(type,history,faction);
            int roll=random.nextInt(total);for(KOMEKnightCommission.Type type:remaining){roll-=weight(type,history,faction);if(roll<0){ordered.add(type);remaining.remove(type);break;}}
        }return ordered;
    }
    private static int weight(KOMEKnightCommission.Type type,List<KOMEKnightCommission> history,String faction){for(int i=history.size()-1;i>=Math.max(0,history.size()-3);i--){KOMEKnightCommission a=history.get(i);if(a.type==type&&KOMEProgressionRelationshipLifecycle.sameFaction(a.faction,faction))return 1;}return 4;}
}
