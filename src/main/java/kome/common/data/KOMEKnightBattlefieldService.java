package kome.common.data;

import java.util.*;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEKnightCommission.*;

/** Personal evidence only. The canonical ordinary resolver still owns all military outcomes. */
public final class KOMEKnightBattlefieldService {
    private KOMEKnightBattlefieldService() {}
    static boolean compatible(KOMEKnightCommission a){return a!=null&&a.tier>0&&!a.adminForced&&a.stage==Stage.ACTIVE
        &&(a.type==Type.BORDER_INCURSION||a.type==Type.SETTLEMENT_DEFENSE||a.type==Type.FIELD_COMMAND);}
    interface Presence {EntityPlayerMP player(UUID owner);boolean inTile(EntityPlayerMP player,String tile);}
    static Presence presence=new Presence(){
        public EntityPlayerMP player(UUID owner){net.minecraft.server.MinecraftServer server=net.minecraft.server.MinecraftServer.getServer();if(server!=null&&server.getConfigurationManager()!=null)for(Object o:server.getConfigurationManager().playerEntityList)if(o instanceof EntityPlayerMP&&owner.equals(((EntityPlayerMP)o).getUniqueID()))return (EntityPlayerMP)o;return null;}
        public boolean inTile(EntityPlayerMP player,String tile){KOMETileResolution r=KOMETileWorldResolver.INSTANCE.resolveWorldPosition(player.dimension,player.posX,player.posZ);return r.status==KOMETileResolution.Status.RESOLVED&&tile.equals(r.tileId);}
    };
    static KOMEJoinBattleDeploymentReceipt deployment(KOMEWorldData data,UUID player,String conflict){
        for(KOMEJoinBattleDeploymentReceipt receipt:data.getJoinBattleDeploymentReceipts().records().values())if(player.equals(receipt.getPlayerId())&&conflict.equals(receipt.getConflictId())&&receipt.getState()==KOMEJoinBattleDeploymentReceipt.State.DEPLOYED)return receipt;return null;
    }
    static boolean participant(KOMEConflictRecord conflict,UUID owner,String faction){
        KOMEConflictRecord.PlayerParticipation p=conflict.getPlayers().get(owner);
        return p!=null&&p.status==KOMEConflictRecord.PlayerStatus.ACTIVE&&KOMEProgressionRelationshipLifecycle.sameFaction(p.factionAtRegistration,faction);
    }
    private static boolean assignedTile(KOMEKnightCommission a,String tile){KOMETileResolution r=KOMETileWorldResolver.INSTANCE.resolveWorldPosition(a.dimension,a.x,a.z);return r.status==KOMETileResolution.Status.RESOLVED&&tile.equals(r.tileId);}
    static boolean canonical(KOMEConflictRecord conflict){for(KOMEConflictRecord.Commitment c:conflict.getCommitments().values())if(c.validatedEvent!=null)return true;return false;}
    /** Called only for uncancelled positive player damage to an actual committed campaign enemy. */
    public static void damage(KOMEWorldData data,EntityPlayerMP player,LOTREntityNPC npc,float damage){
        KOMEPlayerProgression p=data.progressions.get(player.getUniqueID());KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();
        if(!compatible(a)||p.getKnightService().testing()||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||!player.isEntityAlive()||!Float.isFinite(damage)||damage<=0
                ||npc.worldObj!=player.worldObj||!npc.isEntityAlive()||player.getDistanceSqToEntity(npc)>96*96
                ||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege())||!KOMEProgressionFactionResolver.matches(a.faction,LOTRLevelData.getData(player).getPledgeFaction()))return;
        KOMEHiredUnitRecord unit=data.hiredUnits.get(npc.getUniqueID());
        if(unit==null||unit.farmhand||unit.populationReturned||!KOMEHiredUnitClassification.isCampaignUnit(unit)||unit.companyId.isEmpty())return;
        KOMEArmyCompany company=data.armyCompanies.get(unit.companyId);if(company==null||!company.units.contains(unit.entity)||!KOMEProgressionFactionResolver.matches(company.faction,npc.getFaction()))return;
        KOMEConflictRecord conflict=data.getConflictService().get(company.currentTile);
        if(conflict==null||!conflict.isActive()||!conflict.getCommitments().containsKey(company.id)||!participant(conflict,player.getUniqueID(),a.faction)||!presence.inTile(player,conflict.getTileId())||!assignedTile(a,conflict.getTileId())||!KOMEProgressionFactionResolver.matches(a.enemyFaction,npc.getFaction()))return;
        KOMETileResolution target=KOMETileWorldResolver.INSTANCE.resolveWorldPosition(npc.worldObj.provider.dimensionId,npc.posX,npc.posZ);
        if(target.status!=KOMETileResolution.Status.RESOLVED||!conflict.getTileId().equals(target.tileId))return;
        KOMEJoinBattleDeploymentReceipt receipt=deployment(data,player.getUniqueID(),conflict.getConflictId());
        if(receipt==null||!KOMEProgressionRelationshipLifecycle.sameFaction(receipt.getFactionId(),a.faction)
                ||!KOMEGovernanceService.militaryAction(data,player.getUniqueID(),a.faction).allowed)return;
        KOMEDiplomacyRelation relation=KOMEDiplomacyService.getRelation(data,a.faction,company.faction);
        if(relation!=KOMEDiplomacyRelation.ENEMIES&&relation!=KOMEDiplomacyRelation.MORTAL_ENEMIES)return;
        recordDamage(data,player.getUniqueID(),a,conflict,npc.getUniqueID(),Math.min(damage,npc.getHealth()),System.currentTimeMillis());
    }
    static boolean recordDamage(KOMEWorldData data,UUID owner,KOMEKnightCommission a,KOMEConflictRecord conflict,UUID target,float damage,long now){
        if(!compatible(a)||data.progressions.get(owner)==null||data.progressions.get(owner).getKnightService().assignment()!=a||a.acceptedMillis<=0||now<a.acceptedMillis||!conflict.isActive()||!canonical(conflict)||!Float.isFinite(damage)||damage<=0)return false;
        for(KOMEKnightCommission old:data.getProgression(owner).getKnightService().history())if(conflict.getConflictId().equals(old.battleEvidence.getString("CreditedConflict")))return false;
        NBTTagCompound evidence=a.battleEvidence;
        if(!evidence.getString("Conflict").isEmpty()&&!conflict.getConflictId().equals(evidence.getString("Conflict"))){
            KOMEConflictRecord old=data.getConflictService().get(evidence.getString("Tile"));if(old!=null&&old.isActive()&&old.getConflictId().equals(evidence.getString("Conflict")))return false;
            for(Object key:new ArrayList<Object>(evidence.func_150296_c()))evidence.removeTag((String)key);
        }
        evidence.setString("Conflict",conflict.getConflictId());evidence.setString("Tile",conflict.getTileId());evidence.setString("Owner",owner.toString());evidence.setString("Token",a.token);evidence.setString("Faction",a.faction);
        evidence.setLong("LastCombat",now);
        evidence.setBoolean("ValidatedBattle",true);
        NBTTagCompound targets=evidence.getCompoundTag("Targets");String id=target.toString();
        if(!targets.hasKey(id)&&targets.func_150296_c().size()>=32)return false;
        NBTTagCompound hit=targets.getCompoundTag(id);hit.setFloat("Damage",Math.min(100,hit.getFloat("Damage")+damage));targets.setTag(id,hit);evidence.setTag("Targets",targets);data.markDirty();return true;
    }
    public static void death(KOMEWorldData data,UUID target){for(KOMEPlayerProgression p:data.progressions.values()){
        KOMEKnightCommission a=p.getKnightService().assignment();if(!compatible(a))continue;
        NBTTagCompound targets=a.battleEvidence.getCompoundTag("Targets");if(targets.hasKey(target.toString(),10)){targets.getCompoundTag(target.toString()).setBoolean("Dead",true);data.markDirty();}
    }}
    static boolean enough(KOMEKnightCommission a){
        int dead=0;float damage=0;NBTTagCompound targets=a.battleEvidence.getCompoundTag("Targets");
        for(Object key:targets.func_150296_c()){NBTTagCompound hit=targets.getCompoundTag((String)key);if(hit.getBoolean("Dead")&&hit.getFloat("Damage")>=4)dead++;damage+=hit.getFloat("Damage");}
        return dead>=Math.max(2,a.tier)&&damage>=Math.max(16,8*a.tier);
    }
    static final class Present {
        final UUID owner;final String token,receipt;final EntityPlayerMP player;
        Present(UUID owner,KOMEKnightCommission a,KOMEJoinBattleDeploymentReceipt receipt,EntityPlayerMP player){this.owner=owner;token=a.token;this.receipt=receipt.getReceiptId();this.player=player;}
    }
    /** A transaction-local observation, taken before canonical egress moves participants home. */
    static List<Present> beforeClosure(KOMEWorldData data,KOMEOrdinaryBattleResolutionService.Plan plan){
        List<Present> result=new ArrayList<Present>();
        for(Map.Entry<UUID,KOMEPlayerProgression> row:data.progressions.entrySet()){
            KOMEPlayerProgression p=row.getValue();KOMEKnightCommission a=p.getKnightService().assignment();
            if(!compatible(a)||!plan.conflictId.equals(a.battleEvidence.getString("Conflict"))||!enough(a)||p.getKnightService().testing()||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT)continue;
            EntityPlayerMP owner=presence.player(row.getKey());KOMEJoinBattleDeploymentReceipt receipt=deployment(data,row.getKey(),plan.conflictId);
            if(owner!=null&&owner.isEntityAlive()&&presence.inTile(owner,plan.tileId)&&receipt!=null
                    &&KOMEProgressionFactionResolver.matches(a.faction,LOTRLevelData.getData(owner).getPledgeFaction())&&a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))result.add(new Present(row.getKey(),a,receipt,owner));
        }return result;
    }
    /** Only the successful canonical closure calls this. Admin ending/transfers and visits have no hook. */
    static void resolved(KOMEWorldData data,KOMEOrdinaryBattleResolutionService.Plan plan,KOMEConflictRecord ended,List<Present> present){
        if(ended==null||ended.isActive()||!plan.ready()||!ended.getConflictId().equals(plan.conflictId)||plan.winnerFactionId.isEmpty())return;
        for(Present observed:present){
            KOMEPlayerProgression p=data.progressions.get(observed.owner);KOMEKnightCommission a=p==null?null:p.getKnightService().assignment();if(!compatible(a)||!a.token.equals(observed.token))continue;
            if(!qualifies(observed.owner,a,ended,plan.winnerFactionId)||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT||p.getKnightService().testing())continue;
            EntityPlayerMP owner=observed.player;KOMEJoinBattleDeploymentReceipt receipt=data.getJoinBattleDeploymentReceipts().get(observed.receipt);
            if(owner==null||!owner.isEntityAlive()||receipt==null||!observed.owner.equals(receipt.getPlayerId())||!ended.getConflictId().equals(receipt.getConflictId())
                    ||!KOMEProgressionFactionResolver.matches(a.faction,LOTRLevelData.getData(owner).getPledgeFaction())||!a.liege.hasSameIdentity(p.getSerfKnightProgression().getLiege()))continue;
            a.battleEvidence.setString("CreditedConflict",ended.getConflictId());a.participated=true;a.threatResolved=true;a.stage=Stage.READY_TO_REPORT;
            data.markDirty();KOMEKnightCommissionService.cleanup(owner.worldObj,observed.owner,a);KOMEProgressionAutoCompleter.syncPlayer(owner,p);
        }
    }
    static boolean qualifies(UUID owner,KOMEKnightCommission a,KOMEConflictRecord ended,String winner){
        NBTTagCompound e=a.battleEvidence;return compatible(a)&&e.getBoolean("ValidatedBattle")&&!ended.isActive()&&ended.getEndedAtMillis()!=null&&ended.getEndedAtMillis()>=e.getLong("LastCombat")&&e.getLong("LastCombat")>=a.acceptedMillis&&participant(ended,owner,a.faction)&&enough(a)
            &&owner.toString().equals(e.getString("Owner"))&&a.token.equals(e.getString("Token"))&&a.faction.equals(e.getString("Faction"))
            &&ended.getConflictId().equals(e.getString("Conflict"))&&ended.getTileId().equals(e.getString("Tile"))&&e.getString("CreditedConflict").isEmpty()
            &&KOMEProgressionRelationshipLifecycle.sameFaction(winner,a.faction);
    }
}
