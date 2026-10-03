package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.spawning.*;
import net.minecraft.world.World;

/** Small native-catalog resolver for temporary progression troops, never a hiring permission. */
public final class KOMEProgressionTrialSoldiers {
    private KOMEProgressionTrialSoldiers() {}
    public static String resolve(World world,LOTRFaction faction) {
        if(world==null||faction==null)return null;
        List<LOTRUnitTradeEntry> trades=new ArrayList<LOTRUnitTradeEntry>();
        try {
            for(Field field:LOTRUnitTradeEntries.class.getFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==LOTRUnitTradeEntries.class){
                LOTRUnitTradeEntries catalog=(LOTRUnitTradeEntries)field.get(null);
                if(catalog!=null&&catalog.tradeEntries!=null)for(LOTRUnitTradeEntry entry:catalog.tradeEntries)
                    if(entry!=null&&entry.entityClass!=null&&entry.mountClass==null&&entry.task==LOTRHiredNPCInfo.Task.WARRIOR)trades.add(entry);
            }
        } catch(IllegalAccessException invalid) { throw new IllegalStateException("Native unit catalogs are inaccessible",invalid); }
        Collections.sort(trades,new Comparator<LOTRUnitTradeEntry>(){public int compare(LOTRUnitTradeEntry a,LOTRUnitTradeEntry b){
            int standing=Float.compare(a.alignmentRequired,b.alignmentRequired);return standing!=0?standing:a.entityClass.getName().compareTo(b.entityClass.getName());
        }});
        Set<String> tested=new HashSet<String>();
        for(LOTRUnitTradeEntry entry:trades){String name=entry.entityClass.getName();
            if(tested.add(name)&&KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(world,name),faction))return name;
        }
        // Some factions expose normal defenders through native biome spawn lists instead.
        for(String name:spawnClasses())if(tested.add(name)&&KOMELordshipTrialService.suitableGuard(KOMEKnightCommissionService.construct(world,name),faction))return name;
        return null;
    }
    static Set<String> spawnClasses() {
        Set<String> names=new TreeSet<String>();
        try {for(Field field:LOTRSpawnList.class.getFields())if(Modifier.isStatic(field.getModifiers())&&field.getType()==LOTRSpawnList.class){
            LOTRSpawnList list=(LOTRSpawnList)field.get(null);
            if(list!=null)for(LOTRSpawnEntry entry:list.getReadOnlyList())if(entry.entityClass!=null)names.add(entry.entityClass.getName());
        }}catch(IllegalAccessException invalid){throw new IllegalStateException("Native spawn catalogs are inaccessible",invalid);}
        return names;
    }
}
