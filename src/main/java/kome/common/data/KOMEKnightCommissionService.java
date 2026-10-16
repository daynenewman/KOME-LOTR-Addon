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

/** Server-only field commission lifecycle. Native LOTR owns combat and following. */
public final class KOMEKnightCommissionService {
    static boolean usesProperty(KOMEKnightCommission a){return a!=null&&(a.type==Type.STOLEN_GOODS||a.type==Type.RAID_CAMP);}
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
            &&(p.getKnightService().assignment()!=null||p.getKnightService().qualifyingTypes(npc.getFaction().codeName()).size()<3);
    }
    /** Preview persists once; reopening offers never changes type, goods, enemies or geography. */
    public static KOMEKnightCommission prepare(EntityPlayerMP player,LOTREntityNPC liege) {
        KOMEPlayerProgression existing=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID());
        KOMELordshipTrial ongoing=existing.getLordship().assignment();
        if(ongoing!=null&&ongoing.ordinaryService&&KOMECurrentLiege.valid(player,liege))return ongoing.objective;
        if(!eligible(player,liege))return null;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightServiceRecord state=p.getKnightService();
        if(state.assignment()!=null)return state.assignment();
        int tier=KOMEKnightFieldService.tier(state,liege.getFaction().codeName());
        List<Type> available=state.eligible(Arrays.asList(Type.values()),liege.getFaction().codeName());
        if(tier==1)available.removeAll(Arrays.asList(Type.NATIVE_INVASION,Type.RAID_CAMP,Type.WAR_PLANS,Type.FIELD_COMMAND,Type.PROTECT_LIEGE,Type.RESCUE_PATROL,Type.GO_FOR_HELP));
        List<Type> types=KOMEProgressionVariety.order(available,state.history(),liege.getFaction().codeName(),player.worldObj.rand,tier);
        for(Type type:types){
            if(type==Type.NATIVE_INVASION){
                KOMELordshipTrial invasion=KOMELordshipTrialService.generate(player,liege,KOMELordshipTrial.Scenario.NATIVE_INVASION);
                if(invasion!=null){KOMEKnightFieldService.profile(invasion.objective,tier);invasion.objective.complication=Complication.NONE;invasion.ordinaryService=true;if(p.getLordship().offer(invasion)){world.markDirty();return invasion.objective;}}
                continue;
            }
            KOMEKnightCommission a=generate(player,liege,type,tier);if(a!=null&&state.offer(a)){p.getSerfKnightProgression().variety.remember(a.liege,a.type.name(),a.destinationX,a.destinationZ);world.markDirty();return a;}}return null;
    }
    static KOMEKnightCommission generate(EntityPlayerMP player,LOTREntityNPC liege,Type type) {
        return generate(player,liege,type,0);
    }
    static KOMEKnightCommission generate(EntityPlayerMP player,LOTREntityNPC liege,Type type,int tier) {
        World world=player.worldObj;KOMEProgressionNpcRef ref=KOMEProgressionNpcRankService.referenceOf(liege);
        if(world.getWorldChunkManager()==null)return null;
        KOMEKnightCommission a=new KOMEKnightCommission(type,ref);
        if(tier>0)KOMEKnightFieldService.profile(a,tier);
        KOMEProgressionVariety variety=KOMEWorldData.get(world).getProgression(player.getUniqueID()).getSerfKnightProgression().variety;
        KOMEKnightCommissionLocations.Destination location=KOMEKnightCommissionLocations.choose(ref,world,type,a.token,variety,tier);if(location==null)return null;
        a.createdAt=world.getTotalWorldTime();a.x=location.x;a.z=location.z;a.destinationX=a.x;a.destinationZ=a.z;a.place=location.name;
        a.destinationProof=(NBTTagCompound)location.proof.copy();
        if(type==Type.BORDER_INCURSION)a.enemyFaction=location.proof.getString("BorderEnemy");
        if(KOMEKnightFieldService.hostileMission(type))a.enemyFaction=location.proof.getString("HostileFaction");
        BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt((int)a.x,(int)a.z);if(!(base instanceof LOTRBiome))return null;
        Set<String> candidates=new LinkedHashSet<String>();
        for(Class<? extends LOTREntityNPC> c:KOMECourierRecipientSpawner.eligibleClasses(world,(LOTRBiome)base,liege.getFaction()))candidates.add(c.getName());
        candidates.addAll(KOMEProgressionTrialSoldiers.spawnClasses());
        for(String candidate:candidates)if(civilian(construct(world,candidate),liege.getFaction())){a.civilianClass=candidate;break;}
        // A faction patrol can be the protected presence at a refuge; it remains independent.
        if(a.civilianClass.isEmpty()&&(type==Type.SETTLEMENT_DEFENSE||type==Type.RELIEF)){
            String defender=KOMEProgressionTrialSoldiers.resolve(world,liege.getFaction());if(defender!=null)a.civilianClass=defender;
        }
        if(type==Type.RESCUE_PATROL||type==Type.FIELD_COMMAND){String soldier=KOMEProgressionTrialSoldiers.resolve(world,liege.getFaction());if(soldier==null)return null;a.civilianClass=soldier;}
        if(type==Type.RESCUE_PATROL){KOMEKnightCommissionLocations.Destination refuge=KOMEProgressionDestinations.find(world,ref,64,3000,variety);if(refuge==null)return null;a.destinationX=refuge.x;a.destinationZ=refuge.z;a.destinationProof=refuge.proof;}
        if(type==Type.GO_FOR_HELP){a.helpCommander=KOMEKnightHelpService.contact(world,a);if(!a.helpCommander.isSet())return null;}
        if(type!=Type.BORDER_INCURSION&&type!=Type.STOLEN_GOODS&&!KOMEKnightFieldService.hostileMission(type)&&type!=Type.PROTECT_LIEGE&&a.civilianClass.isEmpty())return null;
        if((type!=Type.RELIEF||tier>0)&&!enemies(world,liege.getFaction(),a))return null;
        if(type==Type.RELIEF){a.goods.addAll(reliefGoods(a.faction,world.rand));if(a.goods.isEmpty())return null;}
        if(type==Type.STOLEN_GOODS||type==Type.RAID_CAMP){ItemStack lost=KOMEProgressionLostItems.choose(a.faction,world.rand);if(lost==null)return null;a.goods.add(new Goods(String.valueOf(Item.itemRegistry.getNameForObject(lost.getItem())),lost.getItemDamage(),1));}
        if(type==Type.RAID_CAMP||type==Type.WAR_PLANS){String officer=KOMEKnightEspionage.officer(world,KOMEProgressionFactionResolver.resolve(a.enemyFaction));if(officer==null)return null;a.civilianClass=officer;}
        if(type==Type.DANGEROUS_ESCORT){a.x=(ref.x+a.destinationX)/2;a.z=(ref.z+a.destinationZ)/2;if(!KOMEKnightCommissionLocations.usable(world,liege.getFaction(),a.x,a.z))return null;}
        a.y=KOMEKnightCommissionLocations.height(world,a.x,a.z);if(!Double.isFinite(a.y))return null;
        return a;
    }
    /** Validate actual native units as well as invasion metadata; try another force on invalid entries. */
    static boolean enemies(World world,LOTRFaction defender,KOMEKnightCommission a) {
        List<LOTRInvasions> choices=KOMEProgressionRegionalEnemies.choices(world,defender,a.x,a.z);
        if(choices.isEmpty())return false;Collections.rotate(choices,Math.floorMod(a.token.hashCode(),choices.size()));
        for(LOTRInvasions invasion:choices){if((a.type==Type.BORDER_INCURSION||KOMEKnightFieldService.hostileMission(a.type))&&!a.enemyFaction.isEmpty()&&!a.enemyFaction.equals(invasion.invasionFaction.codeName()))continue;List<LOTRInvasions.InvasionSpawnEntry> valid=new ArrayList<LOTRInvasions.InvasionSpawnEntry>();
            for(Object entry:invasion.invasionMobs){String name=((LOTRInvasions.InvasionSpawnEntry)entry).getEntityClass().getName();
                LOTREntityNPC sample=construct(world,name);
                if(sample!=null&&sample.getFaction()==invasion.invasionFaction&&sample.getFaction()!=defender
                        &&KOMESerfKnightDefenseService.hostile(defender,sample.getFaction())&&!sample.isChild()
                        &&sample.bossInfo==null&&!(sample instanceof LOTRNPCMount)&&sample.hiredNPCInfo!=null&&!sample.hiredNPCInfo.isActive
                        &&!heavySiegeUnit(name)&&!(a.tier==1&&name.toLowerCase(Locale.ROOT).matches(".*(elite|berserk).*"))&&((LOTRInvasions.InvasionSpawnEntry)entry).itemWeight>0)valid.add((LOTRInvasions.InvasionSpawnEntry)entry);
            }
            if(!valid.isEmpty()){a.enemyFaction=invasion.invasionFaction.codeName();Random mix=new Random(a.token.hashCode());
                int count=KOMEKnightFieldService.enemyCount(a,mix),heavy=0;
                for(int i=0;i<count;i++){
                    LOTRInvasions.InvasionSpawnEntry entry=(LOTRInvasions.InvasionSpawnEntry)net.minecraft.util.WeightedRandom.getRandomItem(mix,valid);
                    String name=entry.getEntityClass().getName();
                    if(name.toLowerCase(Locale.ROOT).contains("elite")||name.toLowerCase(Locale.ROOT).contains("berserk")){heavy++;if(heavy>(a.tier==2?1:2)){for(LOTRInvasions.InvasionSpawnEntry ordinary:valid)if(!ordinary.getEntityClass().getSimpleName().toLowerCase(Locale.ROOT).matches(".*(elite|berserk).*")){name=ordinary.getEntityClass().getName();break;}}count=Math.min(count,7);}
                    a.enemyClasses.add(name);
                }return true;}
        }return false;
    }
    private static boolean heavySiegeUnit(String name){String unit=name.toLowerCase(Locale.ROOT);return unit.contains("troll")||unit.contains("mumak")||unit.contains("bomber")||unit.contains("sapper")||unit.contains("siege")||unit.contains("entityent");}
    static int frontierScore(World world,LOTRFaction faction,double x,double z) {
        int score=0;for(int[] offset:new int[][]{{192,0},{-192,0},{0,192},{0,-192}}){int px=(int)x+offset[0],pz=(int)z+offset[1];BiomeGenBase b=world.getWorldChunkManager().getBiomeGenAt(px,pz);if(b instanceof LOTRBiome&&!KOMEKnightCommissionLocations.territory(world,faction,(LOTRBiome)b,px,pz))score++;}return score;
    }
    static boolean civilian(LOTREntityNPC npc,LOTRFaction faction) { return npc!=null&&npc.isCivilianNPC()&&npc.getFaction()==faction&&!npc.isChild()&&npc.bossInfo==null&&!npc.isTraderEscort&&!(npc instanceof LOTRNPCMount)&&!(npc instanceof LOTRUnitTradeable)&&npc.hiredNPCInfo!=null&&!npc.hiredNPCInfo.isActive; }
    static List<Goods> reliefGoods(String faction,Random random) {
        List<Goods> goods=new ArrayList<Goods>();Set<String> seen=new HashSet<String>();
        for(Item item:KOMELocalProvisionFoods.forFaction(faction))if(item!=null&&Item.itemRegistry.getNameForObject(item)!=null&&item.getItemStackLimit()>0){String key=String.valueOf(Item.itemRegistry.getNameForObject(item));if(!seen.add(key))continue;goods.add(new Goods(key,0,item.getItemStackLimit()<=1?6+random.nextInt(3):16+random.nextInt(17)));if(goods.size()==3)break;}return goods;
    }
    public static boolean acceptOrReport(EntityPlayerMP player,LOTREntityNPC liege) {
        if(KOMELordshipTrialService.eligible(player,liege))return KOMELordshipTrialService.acceptOrReport(player,liege);
        if(!eligible(player,liege)||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightServiceRecord state=p.getKnightService();KOMEKnightCommission a=state.assignment();if(a==null)return false;
        if(a.stage==Stage.READY_TO_REPORT&&!a.liege.hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(liege))&&!a.liege.hasSameIdentity(p.getSerfKnightProgression().getDeceasedLiege()))return false;
        if(a.type==Type.WAR_PLANS&&a.stage==Stage.ACTIVE&&a.dispatchStolen&&player.isSneaking()){
            boolean present=false;for(ItemStack item:player.inventory.mainInventory)if(KOMEKnightEspionage.matches(item,a,player.getUniqueID()))present=true;
            if(!present){a.failureReason="The dispatch was lost. Your earlier reported service remains honoured.";fail(world,player.worldObj,player.getUniqueID(),a);}
        }
        if(a.type==Type.WAR_PLANS&&a.stage==Stage.ACTIVE&&a.detected&&!a.dispatchStolen&&player.isSneaking()){a.failureReason="The enemy discovered you before the orders could be taken. Withdraw and seek fresh orders; earlier service remains honoured.";fail(world,player.worldObj,player.getUniqueID(),a);}
        if(a.activationPending){if(!state.discardTechnical(player.worldObj.getTotalWorldTime())){KOMEProgressionNpcSpeech.say(player,liege,"Preparations are still being checked. Return shortly for fresh orders.");return true;}cleanup(player.worldObj,player.getUniqueID(),a);KOMEProgressionNpcSpeech.say(player,liege,"The encounter could not be arranged safely. Your credited service is preserved. Ask me for fresh orders.");changed(world,player,p);return true;}
        if(a.stage==Stage.FAILED){cleanup(player.worldObj,player.getUniqueID(),a);state.clearFailed();KOMEProgressionNpcSpeech.commission(player,liege,a,"retry");changed(world,player,p);return true;}
        if(a.stage==Stage.READY_TO_REPORT){
            if(!a.adminForced&&(usesProperty(a)&&!consumeHeldProperty(player,a)||a.type==Type.WAR_PLANS&&!KOMEKnightEspionage.consume(player,a))){a.stage=Stage.ACTIVE;a.nextRecoveryTick=0;changed(world,player,p);KOMEProgressionNpcSpeech.say(player,liege,"Place the recovered property or sealed dispatch in your hand, and return it to me.");return true;}
            if(!a.adminForced&&(usesProperty(a)||a.type==Type.WAR_PLANS))KOMEProgressionExchangeSounds.handover(player,liege);
            player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();cleanup(player.worldObj,player.getUniqueID(),a);a.reportedAt=player.worldObj.getTotalWorldTime();
            if(state.testing()){state.discardTestAssignment();KOMEProgressionNpcSpeech.say(player,liege,"Your exercise is concluded. No earned service or reward has been recorded.");}
            else if(state.report(KOMEProgressionNpcRankService.referenceOf(liege),p.getSerfKnightProgression().getDeceasedLiege())){KOMEProgressionServiceRewards.commission(player,a);KOMEProgressionNpcSpeech.commission(player,liege,a,"reported");KOMEServicePromotionService.report(player,liege);}}
        else if(a.stage==Stage.OFFERED){if(!state.accept(p.getCanonicalRank(),p.getSerfKnightProgression().getLiege(),LOTRLevelData.getData(player).getPledgeFaction().codeName()))return false;a.acceptedAt=player.worldObj.getTotalWorldTime();a.acceptedMillis=System.currentTimeMillis();a.stage=Stage.ACTIVE;if(a.type==Type.DANGEROUS_ESCORT&&!spawnCharge(player,a,liege))technicalRetry(player,a);if(!KOMEKnightFieldService.startSoldiers(player,a))technicalRetry(player,a);KOMEProgressionNpcSpeech.commission(player,liege,a,a.stage==Stage.FAILED?"failed":"assigned");}
        else KOMEProgressionNpcSpeech.commission(player,liege,a,"progress");
        changed(world,player,p);return true;
    }
    static boolean spawnCharge(EntityPlayerMP player,KOMEKnightCommission a,LOTREntityNPC liege) { double[] site=safeSite(player.worldObj,(int)liege.posX,(int)liege.posZ);if(site==null)return false;LOTREntityNPC npc=spawn(player,a,a.civilianClass,Role.CHARGE,site);if(npc==null)return false;npc.hiredNPCInfo.isActive=true;npc.hiredNPCInfo.setHiringPlayer(player);npc.hiredNPCInfo.setTask(LOTRHiredNPCInfo.Task.WARRIOR);npc.hiredNPCInfo.ready();KOMEProgressionEscortFollowing.disable(npc);return true; }
    public static boolean directPropertyHandover(EntityPlayerMP player,LOTREntityNPC liege){
        if(!eligible(player,liege)||player.worldObj.isRemote||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEKnightCommission a=world.getProgression(player.getUniqueID()).getKnightService().assignment();
        if(a==null||a.type!=Type.STOLEN_GOODS||!hasProperty(new ItemStack[]{player.getCurrentEquippedItem()},a,player.getUniqueID()))return false;
        // The same held-property evidence as tickPlayer, without waiting for its next refresh.
        if(a.stage==Stage.ACTIVE&&a.threatResolved){a.stage=Stage.READY_TO_REPORT;world.markDirty();}
        return a.stage==Stage.READY_TO_REPORT&&acceptOrReport(player,liege);
    }
    /** No force loading: each generated actor is reserved before it joins the world. */
    static boolean activate(EntityPlayerMP player,KOMEKnightCommission a) { return activate(player,a,MARKER); }
    static boolean activate(EntityPlayerMP player,KOMEKnightCommission a,String markerKind) {
        if(a.encounterCreated||a.stage!=Stage.ACTIVE)return false;
        double[] site=safeSite(player.worldObj,(int)a.x,(int)a.z);if(site==null)return false;a.y=site[1];
        if(KOMEKnightFieldService.hostileMission(a.type)&&!KOMEProgressionEncounterSites.openGround(player.worldObj,site))return false;
        if(a.type==Type.PROTECT_LIEGE&&!KOMEKnightFieldService.liegeAvailable(player,a))return false;
        LOTREntityNPC beneficiary=null;if(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.RELIEF||a.type==Type.GO_FOR_HELP){beneficiary=spawn(player,a,a.civilianClass,Role.BENEFICIARY,site,markerKind);if(beneficiary==null)return false;}
        if(a.type==Type.GO_FOR_HELP){a.activationPending=false;a.encounterCreated=true;a.phase=Phase.INVESTIGATE;return true;}
        if(!KOMEKnightFieldService.activateProtected(player,a,site))return false;
        if(a.type==Type.RAID_CAMP||a.type==Type.WAR_PLANS){LOTREntityNPC officer=spawn(player,a,a.civilianClass,Role.ENEMY,site,markerKind);if(officer==null)return false;a.targetId=officer.getUniqueID().toString();officer.setSpecificLocationName("the field command post");officer.setHomeArea((int)a.x,(int)a.y,(int)a.z,12);if(a.type==Type.RAID_CAMP)a.carrierId=a.targetId;}
        if(a.type!=Type.RELIEF||a.tier>0)for(int i=0;i<a.enemyClasses.size();i++){double[] enemySite=KOMEProgressionEncounterSites.attacker(player.worldObj,a.x,a.z,a.type==Type.PROTECT_LIEGE?KOMEProgressionNpcRef.EMPTY:a.liege,a.token,i,a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.WAR_PLANS?24:48,a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.WAR_PLANS?40:64);if(enemySite==null)return false;LOTREntityNPC enemy=spawn(player,a,a.enemyClasses.get(i),Role.ENEMY,enemySite,markerKind);if(enemy==null)return false;
            Entity target=KOMEKnightFieldService.attackTarget(player,a);if(a.type==Type.WAR_PLANS){enemy.setHomeArea((int)a.x,(int)a.y,(int)a.z,24);}else if(target instanceof net.minecraft.entity.EntityLivingBase)enemy.setAttackTarget((net.minecraft.entity.EntityLivingBase)target,true);}
        if(a.type==Type.STOLEN_GOODS&&!a.actors.isEmpty())equipCarrier(player,a);a.phase=Phase.ENGAGE;a.activationPending=false;a.encounterCreated=true;return true;
    }
    private static LOTREntityNPC spawn(EntityPlayerMP player,KOMEKnightCommission a,String className,Role role,double[] site) { return spawn(player,a,className,role,site,MARKER); }
    static LOTREntityNPC spawn(EntityPlayerMP player,KOMEKnightCommission a,String className,Role role,double[] site,String markerKind) {
        LOTREntityNPC npc=construct(player.worldObj,className);if(npc==null)return null;LOTRFaction faction=KOMEProgressionFactionResolver.resolve(role==Role.ENEMY?a.enemyFaction:a.faction);if(npc.getFaction()!=faction)return null;
        npc.spawnRidingHorse=false;npc.setLocationAndAngles(site[0],site[1],site[2],0,0);
        try{npc.onSpawnWithEgg(null);npc.onArtificalSpawn();}catch(RuntimeException invalid){return null;}
        npc.setShouldTraderRespawn(false);
        if(npc.ridingEntity!=null||npc.isChild()||npc.bossInfo!=null||npc.isTraderEscort||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive)return null;
        if(role==Role.GUARD&&!KOMELordshipTrialService.suitableGuard(npc,faction))return null;
        if(role!=Role.ENEMY&&role!=Role.GUARD&&!npc.isCivilianNPC()
                &&!(role==Role.BENEFICIARY&&(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.RELIEF||a.type==Type.RESCUE_PATROL)&&KOMELordshipTrialService.suitableGuard(npc,faction)))return null;
        if(!player.worldObj.checkNoEntityCollision(npc.boundingBox)||!player.worldObj.getCollidingBoundingBoxes(npc,npc.boundingBox).isEmpty())return null;
        Actor actor=new Actor(npc.getUniqueID().toString(),npc.getClass().getName(),role,npc.posX,npc.posY,npc.posZ);a.actors.add(actor);
        actor.displayName=npc.getNPCName();
        KOMEProgressionEncounterMarker.mark(npc,markerKind,player.getUniqueID(),a.token);KOMEWorldData.get(player.worldObj).markDirty();
        if(a.adminForced)KOMEProgressionEncounterMarker.markTesting(npc);
        KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(player.worldObj),player.getUniqueID());
        if(!player.worldObj.spawnEntityInWorld(npc)){a.actors.remove(actor);KOMEProgressionNpcRoles.syncPlayer(KOMEWorldData.get(player.worldObj),player.getUniqueID());return null;}return npc;
    }
    static LOTREntityNPC construct(World world,String className) { return npcFactory.create(world,className); }
    /** Reconstitutes one missing slot. A dead slot is never eligible for recovery. */
    static boolean recoverEnemy(EntityPlayerMP player,KOMEKnightCommission a,Actor actor,String kind){
        if(a.tier>0)return false; // New encounters retry transactionally; never duplicate an unloaded UUID.
        if(actor.role!=Role.ENEMY||actor.dead||actor.recoveries>=3)return false;
        double[] site=safeSite(player.worldObj,(int)actor.x,(int)actor.z);if(site==null)return false;
        LOTREntityNPC npc=construct(player.worldObj,actor.className);
        if(npc==null||!KOMEProgressionFactionResolver.matches(a.enemyFaction,npc.getFaction()))return false;
        npc.onArtificalSpawn();npc.setLocationAndAngles(site[0],site[1],site[2],0,0);
        KOMEProgressionEncounterMarker.mark(npc,kind,player.getUniqueID(),a.token);
        String previousId=actor.id;actor.id=npc.getUniqueID().toString();if(a.carrierId.equals(previousId)){a.carrierId=actor.id;displayProperty(npc,property(a,player.getUniqueID()));}actor.x=site[0];actor.y=site[1];actor.z=site[2];actor.missingTicks=0;actor.recoveries++;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEProgressionNpcRoles.syncPlayer(data,player.getUniqueID());data.markDirty();
        return player.worldObj.spawnEntityInWorld(npc);
    }
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
        if(a.stage!=Stage.FAILED&&a.stage!=Stage.REPORTED&&(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||pledge==null||!KOMEProgressionFactionResolver.matches(a.faction,pledge)||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())&&!(a.stage==Stage.READY_TO_REPORT&&a.liege.hasSameIdentity(p.getSerfKnightProgression().getDeceasedLiege())))){fail(world,player.worldObj,player.getUniqueID(),a);return;}
        if(a.stage==Stage.ACCEPTED){
            if(a.type==Type.DANGEROUS_ESCORT&&a.protectedActor()==null){Entity liege=findLoaded(player.worldObj,a.liege.entityUuid);if(!(liege instanceof LOTREntityNPC)||player.getDistanceSqToEntity(liege)>64)return;if(!spawnCharge(player,a,(LOTREntityNPC)liege)){fail(world,player.worldObj,player.getUniqueID(),a);return;}}
            a.stage=Stage.ACTIVE;changed(world,player,p);
        }
        if(a.stage!=Stage.ACTIVE)return;
        if(a.tier>0&&(a.type==Type.DANGEROUS_ESCORT&&a.protectedActor()==null||a.type==Type.FIELD_COMMAND&&a.actors.isEmpty())){
            Entity issuer=findLoaded(player.worldObj,a.liege.entityUuid);if(!(issuer instanceof LOTREntityNPC)||player.getDistanceSqToEntity(issuer)>64)return;
            if(a.type==Type.DANGEROUS_ESCORT?!spawnCharge(player,a,(LOTREntityNPC)issuer):!KOMEKnightFieldService.startSoldiers(player,a)){technicalRetry(player,a);return;}
        }
        if(!validateDestination(player.worldObj,a)){technicalRetry(player,a);return;}
        if(a.type==Type.PROTECT_LIEGE&&!a.encounterCreated&&!KOMEKnightFieldService.liegeAvailable(player,a))return;
        if(!a.encounterCreated&&player.worldObj.getTotalWorldTime()>=a.activationRetryAt&&a.atSite(player.dimension,player.posX,player.posZ,a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.WAR_PLANS?40:72)&&loadedAround(player.worldObj,a.x,a.z)){if(!activate(player,a)){technicalRetry(player,a);return;}changed(world,player,p);}
        boolean missing=false;
        for(Actor actor:a.actors){if(actor.dead)continue;Entity entity=findLoaded(player.worldObj,actor.id);if(entity!=null){if(!entity.isEntityAlive()){continue;}actor.x=entity.posX;actor.y=entity.posY;actor.z=entity.posZ;actor.missingTicks=0;}else if(player.dimension==a.dimension&&player.getDistanceSq(actor.x,actor.y,actor.z)<=96*96&&loadedAround(player.worldObj,actor.x,actor.z)){if(actor.role==Role.ENEMY){actor.missingTicks+=20;if(actor.missingTicks>=(a.tier>0?1200:100)&&!recoverEnemy(player,a,actor,MARKER)){technicalRetry(player,a);return;}}else if(actor.role!=Role.GUARD)missing=true;}else actor.missingTicks=0;}
        // A minute of loaded evidence provides a recoverable failure, never a false kill.
        a.missingTicks=missing?a.missingTicks+20:0;if(a.missingTicks>=1200){technicalRetry(player,a);return;}
        boolean present=a.atSite(player.dimension,player.posX,player.posZ,96);
        if(a.type==Type.PROTECT_LIEGE&&a.encounterCreated&&!KOMEKnightFieldService.liegeSurvives(player,a)){
            if(present&&loadedAround(player.worldObj,a.x,a.z)){a.helpMissingTicks+=20;if(a.helpMissingTicks>=1200)technicalRetry(player,a);}return;
        }
        if(!KOMEKnightHelpService.tick(player,a)){technicalRetry(player,a);return;}
        if(present&&a.type!=Type.WAR_PLANS&&!KOMEKnightFieldService.complication(player,a)){technicalRetry(player,a);return;}
        resolveCombat(a,present);
        KOMEKnightFieldService.afterCombat(player,a);
        if(a.type==Type.DANGEROUS_ESCORT&&a.threatResolved&&!a.destinationProof.hasNoTags()){
            Actor charge=a.protectedActor();Entity entity=charge==null?null:findLoaded(player.worldObj,charge.id);
            if(entity instanceof LOTREntityNPC&&activeEscort(world,(LOTREntityNPC)entity)
                    &&escortArrived(a,entity.worldObj.provider.dimensionId,entity.posX,entity.posZ,player.getDistanceSqToEntity(entity))&&KOMEProgressionDestinations.arrived((LOTREntityNPC)entity,a.destinationProof)){a.stage=Stage.READY_TO_REPORT;KOMEProgressionNpcSpeech.say(player,(LOTREntityNPC)entity,KOMEProgressionDialogue.thanks((LOTREntityNPC)entity));}
        }
        if(usesProperty(a)&&a.threatResolved){if(hasProperty(player.inventory.mainInventory,a,player.getUniqueID())){if(a.type!=Type.RAID_CAMP||!a.atSite(player.dimension,player.posX,player.posZ,128))a.stage=Stage.READY_TO_REPORT;else a.phase=Phase.ESCAPE;}else if(present&&loaded(player.worldObj,a.x,a.z))recoverProperty(player,a);}
        if(a.type==Type.WAR_PLANS&&KOMEKnightEspionage.escaped(player,a))a.stage=Stage.READY_TO_REPORT;
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
    static boolean validateDestination(World world,KOMEKnightCommission a){
        if(KOMEKnightFieldService.hostileMission(a.type))return KOMEKnightCommissionLocations.hostileTerritory(world,KOMEProgressionFactionResolver.resolve(a.faction),KOMEProgressionFactionResolver.resolve(a.enemyFaction),a.x,a.z);
        if(a.type!=Type.DANGEROUS_ESCORT&&a.type!=Type.SETTLEMENT_DEFENSE&&a.type!=Type.RELIEF&&a.type!=Type.RESCUE_PATROL&&a.type!=Type.GO_FOR_HELP)return true;
        if(a.destinationProof.hasNoTags()){
            LOTRFaction faction=KOMEProgressionFactionResolver.resolve(a.faction);
            KOMEKnightCommissionLocations.Destination verified=KOMEProgressionDestinations.verify(world,faction,a.destinationX,a.destinationZ,"legacy:verified_shelter");
            if(verified==null)return !loadedAround(world,a.destinationX,a.destinationZ);
            a.destinationProof=verified.proof;a.place=verified.name;
        }
        return a.destinationProof.hasNoTags()?true:KOMEProgressionDestinations.valid(world,a.destinationProof);
    }
    static boolean resolveCombat(KOMEKnightCommission a,boolean ownerPresent) {
        if(a.type==Type.WAR_PLANS||a.stage!=Stage.ACTIVE||!a.allEnemiesDead()||!KOMEKnightFieldService.qualifiedCombat(a)||!ownerPresent)return false;
        if(a.tier>0&&a.complication!=Complication.NONE&&!a.complicationTriggered)return false;
        if(a.type==Type.SETTLEMENT_DEFENSE&&(a.protectedActor()==null||a.protectedActor().dead))return false;a.threatResolved=true;
        if(a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.BORDER_INCURSION||a.type==Type.PROTECT_LIEGE||a.type==Type.FIELD_COMMAND||a.type==Type.GO_FOR_HELP||a.type==Type.RELIEF&&a.goodsDelivered())a.stage=Stage.READY_TO_REPORT;return true;
    }
    static boolean escortArrived(KOMEKnightCommission a,int dimension,double x,double z,double ownerDistanceSq) { return a.threatResolved&&a.atDestination(dimension,x,z)&&ownerDistanceSq<=256; }
    private static Actor destinationHost(KOMEKnightCommission a) { for(Actor actor:a.actors)if(actor.role==Role.BENEFICIARY)return actor;return null; }
    public static boolean noteDamage(KOMEWorldData world,UUID owner,String target) {
        return noteDamage(world,owner,target,3F);
    }
    public static boolean noteDamage(KOMEWorldData world,UUID owner,String target,float damage) {
        if(!Float.isFinite(damage)||damage<=0)return false;
        KOMEPlayerProgression p=world.progressions.get(owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();if(a==null||a.stage!=Stage.ACTIVE)return false;
        for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead&&actor.id.equals(target)){a.participated=true;a.combatHits=Math.min(1000,a.combatHits+1);a.combatDamage=Math.min(10000,a.combatDamage+damage);world.markDirty();return true;}return false;
    }
    public static void npcDeath(KOMEWorldData world,World liveWorld,String id) {
        for(Map.Entry<UUID,KOMEPlayerProgression> row:world.progressions.entrySet()){
            KOMEKnightCommission a=row.getValue().getKnightService().assignment();
            if(a==null||!a.live())continue;
            if(a.type==Type.GO_FOR_HELP&&a.helpCommander.entityUuid.equals(id)&&a.phase==Phase.SEEK_HELP){a.failureReason="The commander was lost before help could be secured.";fail(world,liveWorld,row.getKey(),a);continue;}
            if(a.liege.entityUuid.equals(id)){if(a.stage!=Stage.READY_TO_REPORT)fail(world,liveWorld,row.getKey(),a);continue;}
            for(Actor actor:a.actors)if(a.stage==Stage.ACTIVE&&actor.id.equals(id)&&!actor.dead){
                if(actor.role==Role.ENEMY&&actor.id.equals(a.carrierId))dropCarrierProperty(world,liveWorld,row.getKey(),a,actor);actor.dead=true;
                if(actor.role==Role.ENEMY&&a.type==Type.WAR_PLANS&&actor.id.equals(a.targetId)&&!a.dispatchStolen){a.failureReason="The officer died before the orders could be stolen.";fail(world,liveWorld,row.getKey(),a);break;}
                if(actor.role==Role.GUARD){world.markDirty();break;}
                if(actor.role!=Role.ENEMY){if(a.type==Type.SETTLEMENT_DEFENSE&&!a.participated&&liveWorld!=null&&liveWorld.func_152378_a(row.getKey()) instanceof EntityPlayerMP){technicalRetry((EntityPlayerMP)liveWorld.func_152378_a(row.getKey()),a);return;}else fail(world,liveWorld,row.getKey(),a);}
                else if(a.allEnemiesDead()&&liveWorld!=null){
                    EntityPlayer owner=liveWorld.func_152378_a(row.getKey());Entity fallen=findLoaded(liveWorld,id);
                    double distance=owner==null?Double.POSITIVE_INFINITY:fallen==null?owner.getDistanceSq(actor.x,actor.y,actor.z):owner.getDistanceSqToEntity(fallen);
                    if(!a.participated&&a.type==Type.SETTLEMENT_DEFENSE&&owner instanceof EntityPlayerMP){technicalRetry((EntityPlayerMP)owner,a);return;}
                    if(a.tier>0&&a.type!=Type.WAR_PLANS&&!KOMEKnightFieldService.qualifiedCombat(a)){a.failureReason="The fighting ended before you had rendered substantial personal aid. Return to your Liege for another attempt.";fail(world,liveWorld,row.getKey(),a);break;}
                    if(!a.participated||owner==null||owner.dimension!=a.dimension||distance>96*96)fail(world,liveWorld,row.getKey(),a);
                    else if((a.type!=Type.PROTECT_LIEGE||owner instanceof EntityPlayerMP&&KOMEKnightFieldService.liegeSurvives((EntityPlayerMP)owner,a))&&resolveCombat(a,true)&&owner instanceof EntityPlayerMP)changed(world,(EntityPlayerMP)owner,row.getValue());
                }
                world.markDirty();
            }
        }
    }
        /** Roll back partial actors; an inability to prepare terrain is not a lost battle. */
    static void technicalRetry(EntityPlayerMP player,KOMEKnightCommission a) {
        cleanup(player.worldObj,player.getUniqueID(),a);a.actors.clear();a.encounterCreated=false;
        if(usesProperty(a)||a.type==Type.WAR_PLANS)a.revision++;
        a.participated=false;a.combatHits=0;a.combatDamage=0;a.threatResolved=false;a.carrierId="";a.targetId="";a.propertyDropped=false;a.dispatchStolen=false;a.detected=false;a.phase=Phase.INVESTIGATE;a.complicationTriggered=false;
        a.failureReason="Technical interruption: the same orders may be retried or reassigned without losing reported service.";
        a.activationPending=true;a.activationRetryAt=player.worldObj.getTotalWorldTime()+200;
        KOMEWorldData data=KOMEWorldData.get(player.worldObj);changed(data,player,data.getProgression(player.getUniqueID()));
    }
    static boolean propertyIdentity(ItemStack stack,KOMEKnightCommission a) {
        Goods g=a.goods.get(0);
        return stack.getItem()==STOLEN_PROPERTY&&stack.getItemDamage()==0
            ||g.itemKey.equals(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())))&&stack.getItemDamage()==g.damage;
    }
    static boolean taggedProperty(ItemStack stack){return stack!=null&&stack.hasTagCompound()&&stack.getTagCompound().hasKey(ITEM_TAG,10);}
    /** Native offhand preserves the main weapon and attack AI. Equipment is presentation only. */
    static void displayProperty(LOTREntityNPC npc,ItemStack stack) {
        if(stack!=null&&stack.getItem() instanceof net.minecraft.item.ItemArmor){
            int slot=4-((net.minecraft.item.ItemArmor)stack.getItem()).armorType;
            npc.setCurrentItemOrArmor(slot,stack);return;
        }
        if(npc.npcItemsInv!=null){npc.npcItemsInv.setIdleItem(stack);npc.npcItemsInv.setIdleItemMounted(stack);}
        npc.setCurrentItemOrArmor(0,stack);
    }
    static void equipCarrier(EntityPlayerMP player,KOMEKnightCommission a) {
        for(Actor actor:a.actors)if(actor.role==Role.ENEMY&&!actor.dead){
            Entity entity=findLoaded(player.worldObj,actor.id);
            if(entity instanceof LOTREntityNPC){a.carrierId=actor.id;displayProperty((LOTREntityNPC)entity,property(a,player.getUniqueID()));return;}
        }
    }
    static void dropCarrierProperty(KOMEWorldData data,World world,UUID owner,KOMEKnightCommission a,Actor actor){
        if(world==null||a.propertyDropped||!usesProperty(a))return;
        a.propertyDropped=true;data.markDirty();
        Entity entity=findLoaded(world,actor.id);if(entity instanceof LOTREntityNPC){LOTREntityNPC carrier=(LOTREntityNPC)entity;
            for(int slot=0;slot<=4;slot++)if(taggedProperty(carrier.getEquipmentInSlot(slot)))carrier.setCurrentItemOrArmor(slot,null);
            if(carrier.npcItemsInv!=null){carrier.npcItemsInv.setIdleItem(null);carrier.npcItemsInv.setIdleItemMounted(null);}
        }
        EntityItem dropped=new EntityItem(world,actor.x,actor.y+0.5,actor.z,property(a,owner));
        dropped.lifespan=Integer.MAX_VALUE;a.nextRecoveryTick=world.getTotalWorldTime()+1200;
        KOMEProgressionItemDrops.spawn(world,dropped);
    }
    static boolean loadedAround(World world,double x,double z) { for(int dx=-32;dx<=32;dx+=16)for(int dz=-32;dz<=32;dz+=16)if(!loaded(world,x+dx,z+dz))return false;return true; }
    public static boolean deliverRelief(EntityPlayerMP player,LOTREntityNPC npc) {
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);KOMEPlayerProgression p=world.getProgression(player.getUniqueID());KOMEKnightCommission a=p.getKnightService().assignment();if(a==null||a.type!=Type.RELIEF||a.stage!=Stage.ACTIVE||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||player.getDistanceSqToEntity(npc)>64)return false;
        Actor beneficiary=a.protectedActor();if(beneficiary==null||!beneficiary.id.equals(npc.getUniqueID().toString())||!KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction())||!a.atSite(player.dimension,npc.posX,npc.posZ,48))return false;
        int delivered=deliverGoods(a,player.inventory.mainInventory);if(delivered<=0)return false;if(delivered>0){KOMEProgressionExchangeSounds.handover(player,npc);player.inventory.markDirty();player.inventoryContainer.detectAndSendChanges();if(a.goodsDelivered()&&(a.tier==0||a.threatResolved))a.stage=Stage.READY_TO_REPORT;changed(world,player,p);}KOMEProgressionNpcSpeech.commission(player,npc,a,a.stage==Stage.READY_TO_REPORT?"relief_complete":"relief_partial");return true;
    }
    static int deliverGoods(KOMEKnightCommission a,ItemStack[] inventory) { int delivered=0;for(Goods good:a.goods)for(int i=0;i<inventory.length&&good.delivered<good.required;i++){ItemStack stack=inventory[i];if(stack==null||stack.getItemDamage()!=good.damage||!good.itemKey.equals(String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem()))))continue;int take=Math.min(stack.stackSize,good.required-good.delivered);good.delivered+=take;stack.stackSize-=take;delivered+=take;if(stack.stackSize<=0)inventory[i]=null;}return delivered; }
    static ItemStack property(KOMEKnightCommission a,UUID owner) {
        Goods goods=a.goods.get(0);ItemStack stack=KOMEProgressionLostItems.stack(a);if(stack==null)throw new IllegalStateException("Missing native stolen item");stack.stackSize=goods.required;NBTTagCompound root=new NBTTagCompound(),tag=new NBTTagCompound();tag.setString("Contents",goods.itemKey);tag.setInteger("Damage",goods.damage);tag.setString("Token",a.token);tag.setString("Owner",owner.toString());tag.setInteger("Revision",a.revision);root.setTag(ITEM_TAG,tag);stack.setTagCompound(root);return stack;
    }
    static boolean matchesProperty(ItemStack stack,KOMEKnightCommission a,UUID owner) { if(stack==null||a==null||owner==null||!usesProperty(a)||a.goods.size()!=1||stack.stackSize<=0||!propertyIdentity(stack,a)||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);return a.goods.get(0).itemKey.equals(tag.getString("Contents"))&&a.goods.get(0).damage==tag.getInteger("Damage")&&a.token.equals(tag.getString("Token"))&&owner.toString().equals(tag.getString("Owner"))&&a.revision==tag.getInteger("Revision"); }
    static boolean hasProperty(ItemStack[] inventory,KOMEKnightCommission a,UUID owner) { int count=0;for(ItemStack stack:inventory)if(matchesProperty(stack,a,owner))count+=stack.stackSize;return a.goods.size()==1&&count>=a.goods.get(0).required; }
    static boolean consumeProperty(ItemStack[] inventory,KOMEKnightCommission a,UUID owner) { if(!hasProperty(inventory,a,owner))return false;int remaining=a.goods.get(0).required;for(int i=0;i<inventory.length&&remaining>0;i++)if(matchesProperty(inventory[i],a,owner)){int take=Math.min(remaining,inventory[i].stackSize);inventory[i].stackSize-=take;remaining-=take;if(inventory[i].stackSize==0)inventory[i]=null;}a.goods.get(0).delivered=a.goods.get(0).required;return true; }
    static boolean consumeHeldProperty(EntityPlayerMP player,KOMEKnightCommission a){
        ItemStack held=player.getCurrentEquippedItem();
        if(!matchesProperty(held,a,player.getUniqueID())||held.stackSize<a.goods.get(0).required)return false;
        if(!consumeProperty(player.inventory.mainInventory,a,player.getUniqueID()))return false;
        return true;
    }
    private static void recoverProperty(EntityPlayerMP player,KOMEKnightCommission a) {
        int count=0;for(ItemStack stack:player.inventory.mainInventory)if(matchesProperty(stack,a,player.getUniqueID()))count+=stack.stackSize;
        for(Object entity:player.worldObj.loadedEntityList)if(entity instanceof EntityItem&&!((EntityItem)entity).isDead&&matchesProperty(((EntityItem)entity).getEntityItem(),a,player.getUniqueID()))count+=((EntityItem)entity).getEntityItem().stackSize;
        if(count>=a.goods.get(0).required)return;
        long now=player.worldObj.getTotalWorldTime();if(now<a.nextRecoveryTick)return;double[] site=safeSite(player.worldObj,(int)a.x,(int)a.z);if(site==null)return;
        purgeProperty(player.worldObj,player.getUniqueID(),a);
        if(a.retries>=3){fail(KOMEWorldData.get(player.worldObj),player.worldObj,player.getUniqueID(),a);return;}
        a.retries++;a.revision++;a.nextRecoveryTick=now+1200;KOMEWorldData.get(player.worldObj).markDirty();EntityItem item=new EntityItem(player.worldObj,site[0],site[1]+0.5,site[2],property(a,player.getUniqueID()));item.lifespan=Integer.MAX_VALUE;KOMEProgressionItemDrops.spawn(player.worldObj,item);
    }
    public static boolean canPickup(ItemStack stack,UUID owner) { return KOMEKnightEspionage.canPickup(stack,owner)&&(stack==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10)||owner!=null&&owner.toString().equals(stack.getTagCompound().getCompoundTag(ITEM_TAG).getString("Owner"))); }
    public static boolean reconcileItem(KOMEWorldData world,EntityItem entity) {
        if(KOMEKnightEspionage.reconcile(world,entity))return true;
        ItemStack stack=entity.getEntityItem();if(stack==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);
        try{UUID owner=UUID.fromString(tag.getString("Owner"));KOMEPlayerProgression p=world.progressions.get(owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();if(a!=null&&a.live()&&matchesProperty(stack,a,owner)){if(stack.getItem()==STOLEN_PROPERTY){ItemStack nativeItem=property(a,owner);nativeItem.stackSize=stack.stackSize;entity.setEntityItemStack(nativeItem);}entity.lifespan=Integer.MAX_VALUE;entity.age=0;return false;}}catch(IllegalArgumentException invalid){}entity.setDead();return true;
    }
    public static boolean activeEscort(KOMEWorldData world,LOTREntityNPC npc) {
        if(KOMEKnightFieldService.follower(world,npc))return true;
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);if(marker==null||!MARKER.equals(marker.kind)||npc.hiredNPCInfo==null||!npc.hiredNPCInfo.isActive||!marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))return false;KOMEPlayerProgression p=world.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();Actor actor=a==null?null:a.protectedActor();return p!=null&&p.getCanonicalRank()==KOMEProgressionRank.KNIGHT&&a!=null&&a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())&&a.type==Type.DANGEROUS_ESCORT&&a.stage==Stage.ACTIVE&&a.token.equals(marker.token)&&actor!=null&&!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&KOMEProgressionFactionResolver.matches(a.faction,npc.getFaction());
    }
    public static void reconcileNpc(KOMEWorldData world,LOTREntityNPC npc) {
        KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read(npc);if(marker==null||!MARKER.equals(marker.kind))return;KOMEPlayerProgression p=world.progressions.get(marker.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
        if(a!=null&&a.live()&&a.token.equals(marker.token))for(Actor actor:a.actors)if(!actor.dead&&actor.id.equals(npc.getUniqueID().toString())&&actor.className.equals(npc.getClass().getName())&&KOMEProgressionFactionResolver.matches(actor.role==Role.ENEMY?a.enemyFaction:a.faction,npc.getFaction()))return;
        if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive&&marker.owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID()))dismissFollower(npc);npc.setDead();
    }
    static void fail(KOMEWorldData world,World liveWorld,UUID owner,KOMEKnightCommission a) { a.stage=Stage.FAILED;a.cleanupPending=true;cleanup(liveWorld,owner,a);KOMEProgressionNpcRoles.syncPlayer(world,owner);world.markDirty(); }
    public static void cancel(World world,UUID owner,KOMEPlayerProgression p) { KOMEKnightCommission a=p.getKnightService().assignment();if(a==null)return;a.stage=Stage.FAILED;a.cleanupPending=true;cleanup(world,owner,a); }
    public static void playerDeath(EntityPlayer player){
        if(player==null||player.worldObj.isRemote)return;KOMEWorldData data=KOMEWorldData.get(player.worldObj);KOMEKnightCommission a=data.getProgression(player.getUniqueID()).getKnightService().assignment();
        if(a!=null&&a.tier>0&&a.stage==Stage.ACTIVE){a.failureReason="You fell in the field. Report to your Liege for a fresh attempt; earlier commissions remain honoured.";fail(data,player.worldObj,player.getUniqueID(),a);}
    }
    static void releaseFollower(World world,UUID owner,KOMEKnightCommission a) { for(Actor actor:a.actors){Entity entity=findLoaded(world,actor.id);if(actor.role!=Role.ENEMY&&entity instanceof LOTREntityNPC){LOTREntityNPC npc=(LOTREntityNPC)entity;if(npc.hiredNPCInfo!=null&&npc.hiredNPCInfo.isActive&&owner.equals(npc.hiredNPCInfo.getHiringPlayerUUID())){if(a.stage==Stage.READY_TO_REPORT)KOMEProgressionEscortFollowing.releaseDelivered(npc);else dismissFollower(npc);}}} }
    static void cleanup(World world,UUID owner,KOMEKnightCommission a) { if(world==null||a==null)return;releaseFollower(world,owner,a);for(Actor actor:a.actors){Entity entity=findLoaded(world,actor.id);if(entity instanceof LOTREntityNPC){KOMEProgressionEncounterMarker.Marker marker=KOMEProgressionEncounterMarker.read((LOTREntityNPC)entity);if(marker!=null&&MARKER.equals(marker.kind)&&owner.equals(marker.owner)&&a.token.equals(marker.token))entity.setDead();}}purgeProperty(world,owner,a);KOMEKnightEspionage.cleanup(world,owner,a); }
    private static boolean propertyBelongsTo(ItemStack stack,UUID owner,KOMEKnightCommission a) { if(stack==null||!stack.hasTagCompound()||!stack.getTagCompound().hasKey(ITEM_TAG,10))return false;NBTTagCompound tag=stack.getTagCompound().getCompoundTag(ITEM_TAG);return owner.toString().equals(tag.getString("Owner"))&&a.token.equals(tag.getString("Token")); }
    private static void purgeProperty(World world,UUID owner,KOMEKnightCommission a) {
        if(!usesProperty(a))return;
        for(Object entity:world.loadedEntityList)if(entity instanceof EntityItem&&propertyBelongsTo(((EntityItem)entity).getEntityItem(),owner,a))((EntityItem)entity).setDead();
        EntityPlayer player=world.func_152378_a(owner);if(player!=null&&player.inventory!=null){boolean changed=false;for(int i=0;i<player.inventory.mainInventory.length;i++)if(propertyBelongsTo(player.inventory.mainInventory[i],owner,a)){player.inventory.mainInventory[i]=null;changed=true;}if(changed){player.inventory.markDirty();if(player.inventoryContainer!=null)player.inventoryContainer.detectAndSendChanges();}}
    }
    static Entity findLoaded(World world,String id) { if(world==null)return null;for(Object entity:world.loadedEntityList)if(entity instanceof Entity&&id.equals(((Entity)entity).getUniqueID().toString()))return (Entity)entity;return null; }
    private static void changed(KOMEWorldData world,EntityPlayerMP player,KOMEPlayerProgression p) { KOMEProgressionNpcRoles.syncPlayer(world,player.getUniqueID());world.markDirty();KOMEProgressionAutoCompleter.syncPlayer(player,p); }
    private static void dismissFollower(LOTREntityNPC npc) { KOMEProgressionEscortFollowing.restore(npc);npc.hiredNPCInfo.dismissUnit(false); }
}
