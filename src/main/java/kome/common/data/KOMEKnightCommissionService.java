package kome.common.data;

import java.util.*;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.spawning.LOTRInvasions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import static kome.common.data.KOMEKnightCommission.*;

/** Server-only lifecycle for the five concrete commissions. Native LOTR owns combat and following. */
public final class KOMEKnightCommissionService {
    public static final Item STOLEN_PROPERTY=new kome.common.item.KOMEItemStolenProperty();
    static final String MARKER="knight_commission", ITEM_TAG="KOMEStolenProperty";
    interface NpcFactory { LOTREntityNPC create(World world,String className); }
    static NpcFactory npcFactory=new NpcFactory(){public LOTREntityNPC create(World world,String className){return nativeNpc(world,className);}};
    private KOMEKnightCommissionService() {}

    public static boolean eligible(EntityPlayer player,LOTREntityNPC npc) {
        if(!KOMECurrentLiege.valid(player,npc))return false;
        KOMEPlayerProgression p=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMEKnightCommission a=p.getKnightService().assignment();
        if(a!=null&&!KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction()))return false;
        return p.getLordship().assignment()==null&&p.getCanonicalRank()==KOMEProgressionRank.KNIGHT
            &&(p.getKnightService().assignment()!=null||p.getKnightService().qualifyingTypes(npc.getFaction().codeName()).size()<Type.values().length);
    }
    /** Preview persists once; reopening offers never changes type, goods, enemies or geography. */
    public static KOMEKnightCommission prepare(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!eligible(player,liege))return null;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightServiceRecord state=p.getKnightService();
        if(state.assignment()!=null)return state.assignment();
        List<Type> types=state.eligible(Arrays.asList(Type.values()),liege.getFaction().codeName());Collections.shuffle(types,player.worldObj.rand);
        for(Type type:types){KOMEKnightCommission a=generate(player,liege,type);if(a!=null&&state.offer(a)){world.markDirty();return a;}}return null;
    }
    static KOMEKnightCommission generate(EntityPlayerMP player,LOTREntityNPC liege,Type type) {
        World world=player.worldObj;KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(liege);
        if(world.getWorldChunkManager()==null)return null;
        KOMEKnightCommission a=new KOMEKnightCommission(type,ref);
        KOMEKnightCommissionLocations.Destination location=KOMEKnightCommissionLocations.choose(ref,world,type,a.token);if(location==null)return null;
        a.createdAt=world.getTotalWorldTime();a.x=location.x;a.z=location.z;a.destinationX=a.x;a.destinationZ=a.z;a.place=location.name;
        BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt((int)a.x,(int)a.z);if(!(base instanceof LOTRBiome))return null;
        Set<String> candidates=new LinkedHashSet<String>();
        for(Class<? extends LOTREntityNPC> c:KOMECourierRecipientSpawner.eligibleClasses(world,(LOTRBiome)base,liege.getFaction()))candidates.add(c.getName());
        candidates.addAll(KOMEProgressionTrialSoldiers.spawnClasses());
        for(String candidate:candidates)if(civilian(construct(world,candidate),liege.getFaction())){a.civilianClass=candidate;break;}
        // A faction patrol can be the protected presence at a refuge; it remains independent.
        if(a.civilianClass.isEmpty()&&(type==Type.SETTLEMENT_DEFENSE||type==Type.RELIEF)){
            String defender=KOMEProgressionTrialSoldiers.resolve(world,liege.getFaction());if(defender!=null)a.civilianClass=defender;
        }
        if(type!=Type.BORDER_INCURSION&&type!=Type.STOLEN_GOODS&&a.civilianClass.isEmpty())return null;
        if(type!=Type.RELIEF&&!enemies(world,liege.getFaction(),a))return null;
        if(type==Type.RELIEF){a.goods.addAll(reliefGoods(a.faction,world.rand));if(a.goods.isEmpty())return null;}
        if(type==Type.STOLEN_GOODS){if(lotr.common.LOTRMod.silver==null||Item.itemRegistry.getNameForObject(lotr.common.LOTRMod.silver)==null)return null;a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(lotr.common.LOTRMod.silver)),0,3));}
        if(type==Type.DANGEROUS_ESCORT){a.x=(ref.x+a.destinationX)/2;a.z=(ref.z+a.destinationZ)/2;if(!KOMEKnightCommissionLocations.usable(world,liege.getFaction(),a.x,a.z))return null;}
        a.y=KOMEKnightCommissionLocations.height(world,a.x,a.z);if(!Double.isFinite(a.y))return null;
        return a;
    }
    /** Validate actual native units as well as invasion metadata; try another force on invalid entries. */
    static boolean enemies(World world,LOTRFaction defender,KOMEKnightCommission a) {
        List<LOTRInvasions> choices=new ArrayList<LOTRInvasions>();
        for(LOTRInvasions invasion:LOTRInvasions.values())if(invasion.invasionFaction!=defender
                &&KOMESerfKnightDefenseService.hostile(defender,invasion.invasionFaction)
                &&invasion.invasionMobs!=null&&!invasion.invasionMobs.isEmpty())choices.add(invasion);
        if(choices.isEmpty())return false;Collections.rotate(choices,Math.floorMod(a.token.hashCode(),choices.size()));
        for(LOTRInvasions invasion:choices){List<String> valid=new ArrayList<String>();
            for(Object entry:invasion.invasionMobs){String name=((LOTRInvasions.InvasionSpawnEntry)entry).getEntityClass().getName();
                LOTREntityNPC sample=construct(world,name);
                if(sample!=null&&sample.getFaction()==invasion.invasionFaction&&sample.getFaction()!=defender
                        &&KOMESerfKnightDefenseService.hostile(defender,sample.getFaction())&&!sample.isChild()
                        &&sample.bossInfo==null&&!(sample instanceof LOTRNPCMount)&&sample.hiredNPCInfo!=null&&!sample.hiredNPCInfo.isActive)valid.add(name);
            }
            if(!valid.isEmpty()){a.enemyFaction=invasion.invasionFaction.codeName();for(int i=0;i<3;i++)a.enemyClasses.add(valid.get(i%valid.size()));return true;}
        }return false;
    }
    static int frontierScore(World world,LOTRFaction faction,double x,double z) {
        int score=0;for(int[] offset:new int[][]{{192,0},{-192,0},{0,192},{0,-192}}){int px=(int)x+offset[0],pz=(int)z+offset[1];BiomeGenBase b=world.getWorldChunkManager().getBiomeGenAt(px,pz);if(b instanceof LOTRBiome&&!KOMEKnightCommissionLocations.territory(world,faction,(LOTRBiome)b,px,pz))score++;}return score;
    }
    static boolean civilian(LOTREntityNPC npc,LOTRFaction faction) { return npc!=null&&npc.isCivilianNPC()&&npc.getFaction()==faction&&!npc.isChild()&&npc.bossInfo==null&&!npc.isTraderEscort&&!(npc instanceof LOTRNPCMount)&&!(npc instanceof LOTRUnitTradeable)&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive; }
    static List<Goods> reliefGoods(String faction,Random random) {
        List<Goods> goods=new ArrayList<Goods>();Set<String> seen=new HashSet<String>();
        for(Item item:KOMELocalProvisionFoods.forFaction(faction))if(item!=null&&Item.itemRegistry.getNameForObject(item)!=null&&item.getItemStackLimit()>0){String key=String.valueOf(Item.itemRegistry.getNameForObject(item));if(!seen.add(key))continue;goods.add(new Goods(key,0,item.getItemStackLimit()<=1?6+random.nextInt(3):16+random.nextInt(17)));if(goods.size()==3)break;}return goods;
    }
    public static boolean acceptOrReport(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!eligible(player,liege)||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightServiceRecord state=p.getKnightService();KOMEKnightCommission a=state.assignment();if(a==null)return false;
        if(a.stage==Stage.READY_TO_REPORT&&!a.liege.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(liege)))return false;
        if(a.stage==Stage.FAILED){cleanup(player.worldObj,player.getUniqueID(),a);state.clearFailed();KOMEProgressionNpcSpeech.commission(player,liege,a,"retry");changed(world,player,p);return true;}
        if(a.stage==Stage.READY_TO_REPORT){if(a.type==Type.STOLEN_GOODS&&!consumeProperty(player.inventory.mainInventory,a,player.getUniqueID())){a.stage=Stage.ACTIVE;a.nextRecoveryTick=0;changed(world,player,p);return true;}player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();cleanup(player.worldObj,player.getUniqueID(),a);a.reportedAt=player.worldObj.getTotalWorldTime();if(state.report(KOMEProgressionNpcRankService.referenceOf(liege)))KOMEProgressionNpcSpeech.commission(player,liege,a,"reported");}
        else if(a.stage==Stage.OFFERED){if(!state.accept(p.getCanonicalRank(),p.getSerfKnightProgression().getLiege(),LOTRLevelData.getData(player).getPledgeFaction().codeName()))return false;a.acceptedAt=player.worldObj.getTotalWorldTime();a.stage=Stage.ACTIVE;if(a.type==Type.DANGEROUS_ESCORT&&!spawnCharge(player,a,liege))fail(world,player.worldObj,player.getUniqueID(),a);KOMEProgressionNpcSpeech.commission(player,liege,a,a.stage==Stage.FAILED?"failed":"assigned");}
        else KOMEProgressionNpcSpeech.commission(player,liege,a,"progress");
        changed(world,player,p);return true;
    }
    static boolean spawnCharge(EntityPlayerMP player,KOMEKnightCommission a,LOTREntityNPC liege) { double[] site=safeSite(player.worldObj,(int)liege.posX,(int)liege.posZ);if(site==null)return false;LOTREntityNPC npc=spawn(player,a,a.civilianClass,Role.CHARGE,site);if(npc==null)return false;npc.hiredNPCInfo.isActive=true;npc.hiredNPCInfo.setHiringPlayer(player);npc.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR);npc.hiredNPCInfo.ready();return true; }
    /** No force loading: each generated actor is reserved before it joins the world. */
    static boolean activate(EntityPlayerMP player,KOMEKnightCommission a) { return activate(player,a,MARKER); }
    static boolean activate(EntityPlayerMP player,KOMEKnightCommission a,String markerKind) {
        if(a.encounterCreated||a.stage!=Stage.ACTIVE)return false;
        double[] site=safeSite(player.worldObj,(int)a.x,(int)a.z);if(site==null)return false;a.y=site[1];
        LOTREntityNPC beneficiary=null;if(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.RELIEF){beneficiary=spawn(player,a,a.civilianClass,Role.BENEFICIARY,site,markerKind);if(beneficiary==null)return false;}
        if(a.type!=Type.RELIEF)for(int i=0;i<a.enemyClasses.size();i++){double[] enemySite=safeSite(player.worldObj,(int)a.x+14+i*3,(int)a.z+14);if(enemySite==null)return false;LOTREntityNPC enemy=spawn(player,a,a.enemyClasses.get(i),Role.ENEMY,enemySite,markerKind);if(enemy==null)return false;if(beneficiary!=null)enemy.setAttackTarget(beneficiary,true);else enemy.setAttackTarget(player);}
        a.encounterCreated=true;return true;
    }
    private static LOTREntityNPC spawn(EntityPlayerMP player,KOMEKnightCommission a,String className,Role role,double[] site) { return spawn(player,a,className,role,site,MARKER); }
    static LOTREntityNPC spawn(EntityPlayerMP player,KOMEKnightCommission a,String className,Role role,double[] site,String markerKind) {
        LOTREntityNPC npc=construct(player.worldObj,className);if(npc==null)return null;LOTRFaction faction=KOMEProgressionFactionResolver.resolve(role==Role.ENEMY?a.enemyFaction:a.faction);if(npc.getFaction()!=faction)return null;
        npc.spawnRidingHorse=false;npc.setLocationAndAngles(site[0],site[1],site[2],0,0);
        try{npc.onSpawnWithEgg(null);npc.onArtificalSpawn();}catch(RuntimeException invalid){return null;}
        if(npc.ridingEntity!=null||npc.isChild()||npc.bossInfo!=null||npc.isTraderEscort||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive)return null;
        if(role==Role.GUARD&&!KOMELordshipTrialService.suitableGuard(npc,faction))return null;
        if(role!=Role.ENEMY&&role!=Role.GUARD&&!npc.isCivilianNPC()
                &&!(role==Role.BENEFICIARY&&(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.RELIEF)&&KOMELordshipTrialService.suitableGuard(npc,faction)))return null;
        npc.func_110163_bv();Actor actor=new Actor(npc.getUniqueID().toString(),npc.getClass().getName(),role,npc.posX,npc.posY,npc.posZ);a.actors.add(actor);
        KOMEProgressionEncounterMarker.mark(npc,markerKind,player.getUniqueID(),a.token);KOMEWorldData.get(player.worldObj).markDirty();
        if(!player.worldObj.spawnEntityInWorld(npc))return null;return npc;
    }
    static LOTREntityNPC construct(World world,String className) { return npcFactory.create(world,className); }
    private static LOTREntityNPC nativeNpc(World world,String className) { try{if(!className.startsWith("lotr.common.entity.npc."))return null;return (LOTREntityNPC)Class.forName(className).getConstructor(World.class).newInstance(world);}catch(Exception invalid){return null;} }
    static boolean loaded(World world,double x,double z) { return world.getChunkProvider()!=null&&world.getChunkProvider().chunkExists(((int)Math.floor(x))>>4,((int)Math.floor(z))>>4); }
    static double[] safeSite(World world,int x,int z) {
        for(int i=0;i<64;i++){int px=x+(i%8)-4,pz=z+(i/8)-4;if(!loaded(world,px,pz))continue;int y=world.getTopSolidOrLiquidBlock(px,pz);if(y<2||y>=world.getActualHeight()-2)continue;if(!world.getBlock(px,y-1,pz).getMaterial().isSolid()||world.getBlock(px,y-1,pz)==net.minecraft.init.Blocks.cactus||!world.isAirBlock(px,y,pz)||!world.isAirBlock(px,y+1,pz))continue;return new double[]{px+0.5,y,pz+0.5};}return null;
    }
    public static void tickPlayer(EntityPlayerMP player) {
        if(player==null||player.worldObj.isRemote)return;KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());
        LOTRFaction pledge=LOTRLevelData.getData(player).getPledgeFaction();
        if(reconcileAllegiance(player.worldObj,player.getUniqueID(),p,pledge==null?"":pledge.codeName()))return;
        KOMEKnightCommission a=p.getKnightService().assignment();if(a==null)return;
        if(a.stage!=Stage.FAILED&&a.stage!=Stage.REPORTED&&(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||pledge==null||!KOMEProgressionFactionResolver.matches(a.faction,pledge)||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))){fail(world,player.worldObj,player.getUniqueID(),a);return;}
        if(a.stage==Stage.ACCEPTED){
            if(a.type==Type.DANGEROUS_ESCORT&&a.protectedActor()==null){Entity liege=findLoaded(player.worldObj,a.liege.entityUuid);if(!(liege instanceof LOTREntityNPC)||player.getDistanceSqToEntity(liege)>64)return;if(!spawnCharge(player,a,(LOTREntityNPC)liege)){fail(world,player.worldObj,player.getUniqueID(),a);return;}}
            a.stage=Stage.ACTIVE;changed(world,player,p);
        }
        if(a.stage!=Stage.ACTIVE)return;
        if(a.type==Type.DANGEROUS_ESCORT&&destinationHost(a)==null&&a.atDestination(player.dimension,player.posX,player.posZ)&&loaded(player.worldObj,a.destinationX,a.destinationZ)){
            double[] haven=safeSite(player.worldObj,(int)a.destinationX,(int)a.destinationZ);
            if(haven==null||spawn(player,a,a.civilianClass,Role.BENEFICIARY,haven)==null){fail(world,player.worldObj,player.getUniqueID(),a);return;}
            KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();
        }
        if(!a.encounterCreated&&a.atSite(player.dimension,player.posX,player.posZ,72)&&loaded(player.worldObj,a.x,a.z)){if(!activate(player,a)){fail(world,player.worldObj,player.getUniqueID(),a);return;}changed(world,player,p);}
        boolean missing=false;
        for(Actor actor:a.actors){if(actor.dead)continue;Entity entity=findLoaded(player.worldObj,actor.id);if(entity!=null){if(!entity.isEntityAlive()){fail(world,player.worldObj,player.getUniqueID(),a);return;}actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;}else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96&&loadedAround(player.worldObj,actor.x,actor.z))missing=true;}
        // A minute of loaded evidence provides a recoverable failure, never a false kill.
        a.missingTicks=missing?a.missingTicks+20:0;if(a.missingTicks>=1200){fail(world,player.worldObj,player.getUniqueID(),a);return;}
        boolean present=a.atSite(player.dimension,player.posX,player.posZ,96);
        resolveCombat(a,present);
        if(a.type==Type.DANGEROUS_ESCORT&&a.threatResolved){Actor charge=a.protectedActor(),host=destinationHost(a);Entity entity=charge==null?null:findLoaded(player.worldObj,charge.id);Entity destination=host==null?null:findLoaded(player.worldObj,host.id);if(entity!=null&&destination!=null&&destination.isEntityAlive()&&escortArrived(a,entity.worldObj.provider.dimensionId,entity.posX,entity.posZ,player.getDistanceSqToEntity(entity)))a.stage=Stage.READY_TO_REPORT;}
        if(a.type==Type.STOLEN_GOODS&&a.threatResolved){if(hasProperty(player.inventory.mainInventory,a,player.getUniqueID()))a.stage=Stage.READY_TO_REPORT;else if(present&&loaded(player.worldObj,a.x,a.z))recoverProperty(player,a);}
        world.markDirty();if(a.stage==Stage.READY_TO_REPORT){releaseFollower(player.worldObj,player.getUniqueID(),a);changed(world,player,p);}
    }
    static boolean reconcileAllegiance(World world,UUID owner,KOMEPlayerProgression p,String faction) {
        KOMEKnightCommission a=p.getKnightService().assignment();if(a==null)return false;
        if(p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&KOMEProgressionRelationshipLifecycle.sameFaction(a.faction,faction))return false;
        // Retain field evidence, but an unreported old-faction charge never earns political credit.
        a.threatResolved=a.threatResolved||a.stage==Stage.READY_TO_REPORT;
        cleanup(world,owner,a);a.stage=Stage.FAILED;p.getKnightService().clearFailed();
        KOMEWorldData data=KOMEWorldData.get(world);KOMEProgressionNpcRoles.syncPlayer(data,owner);data.markDirty();return true;
    }
    static boolean resolveCombat(KOMEKnightCommission a,boolean ownerPresent) {
        if(a.stage!=Stage.ACTIVE||!a.allEnemiesDead()||!a.participated||!ownerPresent)return false;
        if(a.type==Type.SETTLEMENT_DEFENSE&&(a.protectedActor()==null||a.protectedActor().dead))return false;a.threatResolved=true;
        if(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.BORDER_INCURSION)a.stage=Stage.READY_TO_REPORT;return true;
    }
    static boolean escortArrived(KOMEKnightCommission a,int dimension,double x,double z,double ownerDistanceSq) { return a.threatResolved&&a.atDestination(dimension,x,z)&&ownerDistanceSq<=256; }
    private static Actor destinationHost(KOMEKnightCommission a) { for(Actor actor:a.actors)if(actor.role==Role.BENEFICIARY)return actor;return null; }
    public static boolean noteDamage(KOMEWorldData world,UUID owner,String target) {
        KOMEPlayerProgression p=world.progressions.get(owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();if(a==null||a.stage!=Stage.ACTIVE)return false;
        for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead&&actor.id.equals(target)){a.participated=true;world.markDirty();return true;}return false;
    }
    public static void npcDeath(KOMEWorldData world,World liveWorld,String id) {
        for(Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet()){
            KOMEKnightCommission a=row.getValue().getKnightService().assignment();
            if(a==null||!a.live())continue;
            if(a.liege.entityUuid.equals(id)){fail(world,liveWorld,row.getKey(),a);continue;}
            for(Actor actor:a.actors)if(a.stage==Stage.ACTIVE&&actor.id.equals(id)&&!actor.dead){
                actor.dead=true;
                if(actor.role!=Role.ENEMY)fail(world,liveWorld,row.getKey(),a);
                else if(a.allEnemiesDead()&&liveWorld!=null){
                    EntityPlayer owner=liveWorld.func_152378_a(row.getKey());Entity fallen=findLoaded(liveWorld,id);
                    double distance=owner==null?Double.POSITIVE_INFINITY:fallen==null?owner.getDistanceSq(actor.x,actor.y,actor.z):owner.getDistanceSqToEntity(fallen);
                    if(!a.participated||owner==null||owner.dimension!=a.dimension||distance>96*96)fail(world,liveWorld,row.getKey(),a);
                    else if(resolveCombat(a,true)&&owner instanceof EntityPlayerMP)changed(world,(EntityPlayerMP)owner,row.getValue());
                }
                world.markDirty();
            }
        }
    }
    static boolean loadedAround(World world,double x,double z) { for(int dx=-32;dx<=32;dx+=16)for(int dz=-32;dz<=32;dz+=16)if(!loaded(world,x+dx,z+dz))return false;return true; }
    public static boolean deliverRelief(EntityPlayerMP player,LOTREntityNPC npc) {
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightCommission a=p.getKnightService().assignment();if(a==null||a.type!=Type.RELIEF||a.stage!=Stage.ACTIVE||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||player.getDistanceSqToEntity(npc)>64)return false;
        Actor beneficiary=a.protectedActor();if(beneficiary==null||!beneficiary.id.equals(npc.getUniqueID().toString())||!KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction())||!a.atSite(player.dimension,npc.posX,npc.posZ,48))return false;
        int delivered=deliverGoods(a,player.inventory.mainInventory);if(delivered>0){player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();if(a.goodsDelivered())a.stage=Stage.READY_TO_REPORT;changed(world,player,p);}KOMEProgressionNpcSpeech.commission(player,npc,a,a.stage==Stage.READY_TO_REPORT?"relief_complete":"relief_partial");return true;
    }
    static int deliverGoods(KOMEKnightCommission a,ItemStack[] inventory) { int delivered=0;for(Goods good:a.goods)for(int i=0;i<inventory.length&&good.delivered<good.required;i++){ItemStack stack=inventory[i];if(stack==null||stack.getItemDamage()!=good.damage||!good.itemKey.equals(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()))))continue;int take=Math.min(stack.stackSize,good.required-good.delivered);good.delivered+=take;stack.stackSize-=take;delivered+=take;if(stack.stackSize<=0)inventory[i]=null;}return delivered; }
    static ItemStack property(KOMEKnightCommission a,UUID owner) {
        Goods goods=a.goods.get(0);ItemStack stack=new ItemStack(STOLEN_PROPERTY,goods.required);NBTTagCompound root=new NBTTagCompound(),tag=new NBTTagCompound();tag.setString("Contents",goods.itemKey);tag.setInteger("Damage",goods.damage);tag.setString("Token",a.token);tag.setString("Owner",owner.toString());tag.setInteger("Revision",a.revision);root.setTag(ITEM_TAG,tag);stack.setTagCompound(root);return stack;
    }
    static boolean matchesProperty(ItemStack stack,KOMEKnightCommission a,UUID owner) { if(stack==null||a==null||owner==null||a.type!=Type.STOLEN_GOODS||a.goods.size()!=1||stack.stackSize<=0||stack.getItem()!=STOLEN_PROPERTY||stack.getItemDamage()!=0||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);return a.goods.get(0).itemKey.equals(tag.getString("Contents"))&&a.goods.get(0).damage==tag.getInteger("Damage")&&a.token.equals(tag.getString("Token"))&&owner.toString().equals(tag.getString("Owner"))&&a.revision==tag.getInteger("Revision"); }
    static boolean hasProperty(ItemStack[] inventory,KOMEKnightCommission a,UUID owner) { int count=0;for(ItemStack stack:inventory)if(matchesProperty(stack,a,owner))count+=stack.stackSize;return a.goods.size()==1&&count>=a.goods.get(0).required; }
    static boolean consumeProperty(ItemStack[] inventory,KOMEKnightCommission a,UUID owner) { if(!hasProperty(inventory,a,owner))return false;int remaining=a.goods.get(0).required;for(int i=0;i<inventory.length&&remaining>0;i++)if(matchesProperty(inventory[i],a,owner)){int take=Math.min(remaining,inventory[i].stackSize);inventory[i].stackSize-=take;remaining-=take;if(inventory[i].stackSize==0)inventory[i]=null;}a.goods.get(0).delivered=a.goods.get(0).required;return true; }
    private static void recoverProperty(EntityPlayerMP player,KOMEKnightCommission a) {
        int count=0;for(ItemStack stack:player.inventory.mainInventory)if(matchesProperty(stack,a,player.getUniqueID()))count+=stack.stackSize;
        for(Object entity:player.worldObj.loadedEntityList)if(entity instanceof EntityItem&&!((EntityItem)entity).isDead&&matchesProperty(((EntityItem)entity).getEntityItem(),a,player.getUniqueID()))count+=((EntityItem)entity).getEntityItem().stackSize;
        if(count>=a.goods.get(0).required)return;
        long now=player.worldObj.getTotalWorldTime();if(now<a.nextRecoveryTick)return;double[] site=safeSite(player.worldObj,(int)a.x,(int)a.z);if(site==null)return;
        purgeProperty(player.worldObj,player.getUniqueID(),a);
        a.revision++;a.nextRecoveryTick=now+1200;KOMEWorldData.get(player.worldObj).markDirty();EntityItem item=new EntityItem(player.worldObj,site[0],site[1]+0.5,site[2],property(a,player.getUniqueID()));player.worldObj.spawnEntityInWorld(item);
    }
    public static boolean canPickup(ItemStack stack,UUID owner) { return stack==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10)||owner!=null&&owner.toString().equals(stack.getTagCompound().getCompoundTag(ITEM_TAG).getString("Owner")); }
    public static boolean reconcileItem(KOMEWorldData world,EntityItem entity) {
        ItemStack stack=entity.getEntityItem();if(stack==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        try{UUID owner=UUID.fromString(tag.getString("Owner"));KOMEPlayerProgression p=world.progressions.get(owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();if(a!=null&&a.live()&&matchesProperty(stack,a,owner))return false;}catch(IllegalArgumentException invalid){}entity.setDead();return true;
    }
    public static boolean activeEscort(KOMEWorldData world,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);if(marker==null||!MARKER.equals(marker.kind)||npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive||!marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return false;KOMEPlayerProgression p=world.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();Actor actor=a==null?null:a.protectedActor();return p!=null&&p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&a!=null&&a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())&&a.type==Type.DANGEROUS_ESCORT&&a.stage==Stage.ACTIVE&&a.token.equals(marker.token)&&actor!=null&&!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction());
    }
    public static void reconcileNpc(KOMEWorldData world,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);if(marker==null||!MARKER.equals(marker.kind))return;KOMEPlayerProgression p=world.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
        if(a!=null&&a.live()&&a.token.equals(marker.token))for(Actor actor:a.actors)if(!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&actor.className.equals(npc.getClass().getName())&&KOMEProgressionFactionResolver.matches(actor.role==Role.ENEMY?a.enemyFaction:a.faction,npc.getFaction()))return;
        if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive&&marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))npc.hiredNPCInfo.dismissUnit(false);npc.setDead();
    }
    static void fail(KOMEWorldData world,World liveWorld,UUID owner,KOMEKnightCommission a) { a.stage=Stage.FAILED;a.cleanupPending=true;cleanup(liveWorld,owner,a);KOMEProgressionNpcRoles.syncPlayer(world,owner);world.markDirty(); }
    public static void cancel(World world,UUID owner,KOMEPlayerProgression p) { KOMEKnightCommission a=p.getKnightService().assignment();if(a==null)return;a.stage=Stage.FAILED;a.cleanupPending=true;cleanup(world,owner,a); }
    static void releaseFollower(World world,UUID owner,KOMEKnightCommission a) { Actor charge=a.protectedActor();Entity entity=charge==null?null:findLoaded(world,charge.id);if(a.type==Type.DANGEROUS_ESCORT&&entity instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)entity;if(npc.hiredNPCInfo.isActive&&owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))npc.hiredNPCInfo.dismissUnit(false);} }
    static void cleanup(World world,UUID owner,KOMEKnightCommission a) { if(world==null||a==null)return;releaseFollower(world,owner,a);for(Actor actor:a.actors){Entity entity=findLoaded(world,actor.id);if(entity instanceof LOTREntityNPC){KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read((LOTREntityNPC)entity);if(marker!=null&&MARKER.equals(marker.kind)&&owner.equals(marker.owner)&&a.token.equals(marker.token))entity.setDead();}}purgeProperty(world,owner,a); }
    private static boolean propertyBelongsTo(ItemStack stack,UUID owner,KOMEKnightCommission a) { if(stack==null||stack.getItem()!=STOLEN_PROPERTY||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);return owner.toString().equals(tag.getString("Owner"))&&a.token.equals(tag.getString("Token")); }
    private static void purgeProperty(World world,UUID owner,KOMEKnightCommission a) {
        if(a.type!=Type.STOLEN_GOODS)return;
        for(Object entity:world.loadedEntityList)if(entity instanceof EntityItem&&propertyBelongsTo(((EntityItem)entity).getEntityItem(),owner,a))((EntityItem)entity).setDead();
        EntityPlayer player=world.func_152378_a(owner);if(player!=null&&player.inventory!=null){boolean changed=false;for(int i=0;i<player.inventory.mainInventory.length;i++)if(propertyBelongsTo(player.inventory.mainInventory[i],owner,a)){player.inventory.mainInventory[i]=null;changed=true;}if(changed){player.inventory.markDirty();if(player.inventoryContainer!=null)player.inventoryContainer.detectAndSendChanges();}}
    }
    static Entity findLoaded(World world,String id) { if(world==null)return null;for(Object entity:world.loadedEntityList)if(entity instanceof Entity&&id.equals(((Entity)entity).getUniqueID().toString()))return (Entity)entity;return null; }
    private static void changed(KOMEWorldData world,EntityPlayerMP player,KOMEPlayerProgression p) { KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p); }
}
