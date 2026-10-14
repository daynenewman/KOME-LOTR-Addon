package kome.client;

import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.world.World;

/** Optional AtomicStryker 1.7.10 API integration. No block writes or server lighting hooks.
 * Absent/incompatible engines leave illumination unavailable; particles remain cosmetic. */
final class KOMEElvenDynamicLights {
    static final int MAX_SOURCES=16, LIGHT_LEVEL=3;
    private Class<?> sourceType;
    private Method add,remove;
    private boolean checked;
    private World world;
    private final Map<EntityLivingBase,Source> sources=new IdentityHashMap<EntityLivingBase,Source>();
    boolean available(){
        if(!checked){checked=true;try{
            sourceType=Class.forName("atomicstryker.dynamiclights.client.IDynamicLightSource");
            Class<?> engine=Class.forName("atomicstryker.dynamiclights.client.DynamicLights");
            add=engine.getMethod("addLightSource",sourceType);remove=engine.getMethod("removeLightSource",sourceType);
        }catch(ReflectiveOperationException absent){add=remove=null;}catch(LinkageError incompatible){add=remove=null;}}
        return add!=null;
    }
    void update(World next,List<EntityLivingBase> eligible){
        if(next!=world){clear();world=next;}
        Set<EntityLivingBase> keep=Collections.newSetFromMap(new IdentityHashMap<EntityLivingBase,Boolean>());
        for(int i=0;i<Math.min(MAX_SOURCES,eligible.size());i++)keep.add(eligible.get(i));
        for(Iterator<Map.Entry<EntityLivingBase,Source>> it=sources.entrySet().iterator();it.hasNext();){
            Map.Entry<EntityLivingBase,Source> row=it.next();if(!keep.contains(row.getKey())||!row.getKey().isEntityAlive()){
                remove(row.getValue());it.remove();
            }
        }
        if(next==null||!available())return;
        for(EntityLivingBase elf:keep){
            Source source=sources.get(elf);
            if(source==null){
                source=new Source(elf,sourceType);
                try{add.invoke(null,source.proxy);sources.put(elf,source);}catch(ReflectiveOperationException failure){
                    source.anchor.setDead();clear();add=null;
                    cpw.mods.fml.common.FMLLog.warning("KOME: optional Elf dynamic lighting disabled after API failure: %s",failure.toString());return;
                }
            }
            source.anchor.setPosition(elf.posX,elf.boundingBox.minY+0.05D,elf.posZ);
        }
    }
    private void remove(Source source){
        try{if(remove!=null)remove.invoke(null,source.proxy);}catch(ReflectiveOperationException failure){
            cpw.mods.fml.common.FMLLog.warning("KOME: optional Elf dynamic-light removal failed: %s",failure.toString());
        }finally{source.anchor.setDead();}
    }
    void clear(){for(Source source:sources.values())remove(source);sources.clear();world=null;}
    private static final class Source implements InvocationHandler {
        final EntityBat anchor;final Object proxy;
        Source(EntityLivingBase elf,Class<?> type){
            // Unspawned, unsaved attachment fixes the source at the feet, including the local player.
            anchor=new EntityBat(elf.worldObj);anchor.setPosition(elf.posX,elf.boundingBox.minY+0.05D,elf.posZ);
            proxy=Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},this);
        }
        public Object invoke(Object instance,Method method,Object[] args){
            if(method.getName().equals("getAttachmentEntity"))return anchor;
            if(method.getName().equals("getLightLevel"))return LIGHT_LEVEL;
            if(method.getName().equals("equals"))return instance==args[0];
            if(method.getName().equals("hashCode"))return System.identityHashCode(instance);
            if(method.getName().equals("toString"))return "KOME Elf foot light";
            throw new UnsupportedOperationException(method.toString());
        }
    }
}
