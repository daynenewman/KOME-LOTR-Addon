package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;

/** Historical service and recommendation provenance, saved inside the player's progression. */
public final class KOMEServiceRecognition {
    public static final int VERSION = 1;
    public static final class Record {
        public final UUID recipient;
        public final String id, faction, accomplishments;
        public final KOMEProgressionNpcRef issuer;
        public final KOMEProgressionRank from, conferred;
        public final long worldTime;
        public boolean redeemed, invalidated;
        public KOMEProgressionNpcRef priorSuperior=KOMEProgressionNpcRef.EMPTY;
        Record(UUID recipient, String id, KOMEProgressionNpcRef issuer, KOMEProgressionRank from,
                KOMEProgressionRank conferred, String accomplishments, long worldTime) {
            this.recipient=recipient;this.id=id;this.issuer=issuer;this.faction=issuer.factionKey;
            this.from=from;this.conferred=conferred;this.accomplishments=accomplishments;this.worldTime=worldTime;
        }
        public boolean usable(UUID owner, KOMEProgressionRank rank, String pledge) {
            return !redeemed&&!invalidated&&recipient.equals(owner)&&rank==conferred&&KOMEProgressionFactionResolver.matches(faction,KOMEProgressionFactionResolver.resolve(pledge))
                &&issuer.isSet()&&!accomplishments.isEmpty();
        }
    }
    private final List<Record> records=new ArrayList<Record>();
    private NBTTagCompound retiredStanding;
    public int migrationVersion;
    public List<Record> records(){return Collections.unmodifiableList(records);}
    public Record recommendation(UUID owner,KOMEProgressionRank rank,String faction){
        for(Record r:records)if(r.usable(owner,rank,faction))return r;return null;
    }
    Record earn(UUID owner,KOMEProgressionNpcRef issuer,KOMEProgressionRank from,
            KOMEProgressionRank to,String evidence,long time) {
        for(Record r:records)if(r.from==from)return r;
        Record r=new Record(owner,UUID.randomUUID().toString(),issuer,from,to,evidence,time);records.add(r);return r;
    }
    void retireStanding(NBTTagCompound evidence){if(retiredStanding==null)retiredStanding=(NBTTagCompound)evidence.copy();}
    public void allegianceChanged(String faction){for(Record r:records)if(!KOMEProgressionFactionResolver.matches(r.faction,KOMEProgressionFactionResolver.resolve(faction)))r.invalidated=true;}
    public NBTTagCompound write(){
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",VERSION);n.setInteger("Migration",migrationVersion);
        if(retiredStanding!=null)n.setTag("RetiredStanding",retiredStanding.copy());
        NBTTagList list=new NBTTagList();for(Record r:records){NBTTagCompound e=new NBTTagCompound();
            e.setString("Recipient",r.recipient.toString());e.setString("ID",r.id);e.setTag("Issuer",r.issuer.writeToNBT());
            e.setString("From",r.from.key);e.setString("Conferred",r.conferred.key);e.setString("Service",r.accomplishments);
            e.setLong("WorldTime",r.worldTime);e.setBoolean("Redeemed",r.redeemed);e.setBoolean("Invalidated",r.invalidated);list.appendTag(e);
            e.setTag("PriorSuperior",r.priorSuperior.writeToNBT());
        }n.setTag("Records",list);return n;
    }
    public void read(NBTTagCompound n){
        records.clear();retiredStanding=null;migrationVersion=0;if(n==null)return;
        migrationVersion=Math.max(0,n.getInteger("Migration"));
        if(n.hasKey("RetiredStanding",10))retiredStanding=(NBTTagCompound)n.getCompoundTag("RetiredStanding").copy();
        NBTTagList list=n.getTagList("Records",10);for(int i=0;i<Math.min(2,list.tagCount());i++)try{
            NBTTagCompound e=list.getCompoundTagAt(i);KOMEProgressionRank from=KOMEProgressionRank.forKey(e.getString("From")),to=KOMEProgressionRank.forKey(e.getString("Conferred"));
            KOMEProgressionNpcRef issuer=KOMEProgressionNpcRef.readFromNBT(e.getCompoundTag("Issuer"));
            if(!issuer.isSet()||from==null||to==null||to.order!=from.order+1||from.order<1||from.order>2)continue;
            UUID id=UUID.fromString(e.getString("ID"));Record r=new Record(UUID.fromString(e.getString("Recipient")),id.toString(),issuer,from,to,e.getString("Service"),e.getLong("WorldTime"));
            r.redeemed=e.getBoolean("Redeemed");r.invalidated=e.getBoolean("Invalidated");records.add(r);
            r.priorSuperior=KOMEProgressionNpcRef.readFromNBT(e.getCompoundTag("PriorSuperior"));
        }catch(IllegalArgumentException ignored){}
    }
}
