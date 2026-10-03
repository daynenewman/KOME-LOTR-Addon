package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Concrete Knight charge, independent of legacy achievements and promotion gates. */
public final class KOMEKnightCommission {
    public enum Type { SETTLEMENT_DEFENSE, STOLEN_GOODS, BORDER_INCURSION, DANGEROUS_ESCORT, RELIEF }
    public enum Stage { OFFERED, ACCEPTED, ACTIVE, READY_TO_REPORT, REPORTED, FAILED }
    public enum Role { BENEFICIARY, CHARGE, ENEMY, GUARD }
    public final Type type;
    public final String token, faction;
    public final KOMEProgressionNpcRef liege;
    public Stage stage = Stage.OFFERED;
    public int dimension, revision, retries, missingTicks;
    public double x, y, z, destinationX, destinationZ;
    public String place = "", enemyFaction = "", civilianClass = "";
    public long createdAt, acceptedAt, reportedAt, nextRecoveryTick;
    public boolean participated, encounterCreated, threatResolved, cleanupPending;
    public final List<Actor> actors = new ArrayList<Actor>();
    public final List<Goods> goods = new ArrayList<Goods>();
    public final List<String> enemyClasses = new ArrayList<String>();

    public KOMEKnightCommission(Type type, KOMEProgressionNpcRef liege) {
        this(type, liege, UUID.randomUUID().toString());
    }
    private KOMEKnightCommission(Type type, KOMEProgressionNpcRef liege, String token) {
        if(type == null || liege == null || !liege.isSet()) throw new IllegalArgumentException("A commission needs its Liege");
        UUID.fromString(token);
        this.type=type; this.liege=liege; this.token=token; faction=liege.factionKey; dimension=liege.dimension;
    }
    public boolean live() { return stage==Stage.ACCEPTED || stage==Stage.ACTIVE || stage==Stage.READY_TO_REPORT; }
    public boolean atSite(int dim,double px,double pz,double radius) { return dim==dimension && square(px-x)+square(pz-z)<=radius*radius; }
    public boolean atDestination(int dim,double px,double pz) { return dim==dimension && square(px-destinationX)+square(pz-destinationZ)<=24*24; }
    private static double square(double d) { return d*d; }
    public boolean allEnemiesDead() { boolean found=false; for(Actor actor:actors)if(actor.role==Role.ENEMY){found=true;if(!actor.dead)return false;}return found; }
    public boolean goodsDelivered() { if(goods.isEmpty())return false;for(Goods g:goods)if(g.delivered<g.required)return false;return true; }
    public Actor protectedActor() { for(Actor actor:actors)if(actor.role!=Role.ENEMY&&actor.role!=Role.GUARD)return actor;return null; }

    public static final class Actor {
        public final String id, className;
        public final Role role;
        public double x,y,z;
        public boolean dead;
        public Actor(String id,String className,Role role,double x,double y,double z) { UUID.fromString(id);this.id=id;this.className=className;this.role=role;this.x=x;this.y=y;this.z=z; }
        NBTTagCompound write() { NBTTagCompound n=new NBTTagCompound();n.setString("Id",id);n.setString("Class",className);n.setString("Role",role.name());n.setDouble("X",x);n.setDouble("Y",y);n.setDouble("Z",z);n.setBoolean("Dead",dead);return n; }
    }
    public static final class Goods {
        public final String itemKey;
        public final int damage, required;
        public int delivered;
        public Goods(String key,int damage,int required) { if(key==null||key.isEmpty()||required<1||required>64)throw new IllegalArgumentException("Invalid provisions");itemKey=key;this.damage=damage;this.required=required; }
        NBTTagCompound write() { NBTTagCompound n=new NBTTagCompound();n.setString("Item",itemKey);n.setInteger("Damage",damage);n.setInteger("Required",required);n.setInteger("Delivered",delivered);return n; }
    }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",1);n.setString("Type",type.name());n.setString("Token",token);n.setTag("Liege",liege.writeToNBT());n.setString("Stage",stage.name());
        n.setString("ServedFaction",faction);n.setInteger("Dimension",dimension);n.setInteger("Revision",revision);n.setInteger("Retries",retries);n.setInteger("MissingTicks",missingTicks);
        n.setDouble("X",x);n.setDouble("Y",y);n.setDouble("Z",z);n.setDouble("DestinationX",destinationX);n.setDouble("DestinationZ",destinationZ);
        n.setString("Place",place);n.setString("EnemyFaction",enemyFaction);n.setString("CivilianClass",civilianClass);
        n.setLong("Created",createdAt);n.setLong("Accepted",acceptedAt);n.setLong("Reported",reportedAt);n.setLong("NextRecovery",nextRecoveryTick);
        n.setBoolean("Participated",participated);n.setBoolean("EncounterCreated",encounterCreated);n.setBoolean("ThreatResolved",threatResolved);n.setBoolean("CleanupPending",cleanupPending);
        NBTTagList a=new NBTTagList();for(Actor actor:actors)a.appendTag(actor.write());n.setTag("Actors",a);
        NBTTagList g=new NBTTagList();for(Goods good:goods)g.appendTag(good.write());n.setTag("Goods",g);
        NBTTagList e=new NBTTagList();for(String type:enemyClasses){NBTTagCompound t=new NBTTagCompound();t.setString("Class",type);e.appendTag(t);}n.setTag("EnemyClasses",e);return n;
    }
    public static KOMEKnightCommission readFromNBT(NBTTagCompound n) {
        if(n==null || !n.hasKey("Token"))return null;
        try {
            KOMEKnightCommission a=new KOMEKnightCommission(Type.valueOf(n.getString("Type")),KOMEProgressionNpcRef.readFromNBT(n.getCompoundTag("Liege")),n.getString("Token"));
            if(!n.getString("ServedFaction").isEmpty()&&!KOMEProgressionRelationshipLifecycle.sameFaction(n.getString("ServedFaction"),a.faction))return null;
            a.stage=Stage.valueOf(n.getString("Stage"));a.dimension=n.getInteger("Dimension");a.revision=Math.max(0,n.getInteger("Revision"));a.retries=Math.max(0,n.getInteger("Retries"));a.missingTicks=Math.max(0,n.getInteger("MissingTicks"));
            a.x=n.getDouble("X");a.y=n.getDouble("Y");a.z=n.getDouble("Z");a.destinationX=n.getDouble("DestinationX");a.destinationZ=n.getDouble("DestinationZ");
            if(!KOMEKnightCommissionLocations.coordinate(a.x)||!Double.isFinite(a.y)||a.y<0||a.y>256||!KOMEKnightCommissionLocations.coordinate(a.z)||!KOMEKnightCommissionLocations.coordinate(a.destinationX)||!KOMEKnightCommissionLocations.coordinate(a.destinationZ)||a.dimension!=a.liege.dimension)return null;
            a.place=n.getString("Place");a.enemyFaction=n.getString("EnemyFaction");a.civilianClass=n.getString("CivilianClass");a.createdAt=n.getLong("Created");a.acceptedAt=n.getLong("Accepted");a.reportedAt=n.getLong("Reported");a.nextRecoveryTick=n.getLong("NextRecovery");
            a.participated=n.getBoolean("Participated");a.encounterCreated=n.getBoolean("EncounterCreated");a.threatResolved=n.getBoolean("ThreatResolved");a.cleanupPending=n.getBoolean("CleanupPending");
            NBTTagList actors=n.getTagList("Actors",10);if(actors.tagCount()>16)return null;
            Set<String> ids=new HashSet<String>();for(int i=0;i<actors.tagCount();i++){NBTTagCompound t=actors.getCompoundTagAt(i);Actor actor=new Actor(t.getString("Id"),t.getString("Class"),Role.valueOf(t.getString("Role")),t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));if(!ids.add(actor.id)||!KOMEKnightCommissionLocations.coordinate(actor.x)||!KOMEKnightCommissionLocations.coordinate(actor.z)||!Double.isFinite(actor.y))return null;actor.dead=t.getBoolean("Dead");a.actors.add(actor);}
            NBTTagList goods=n.getTagList("Goods",10);if(goods.tagCount()>8)return null;for(int i=0;i<goods.tagCount();i++){NBTTagCompound t=goods.getCompoundTagAt(i);Goods g=new Goods(t.getString("Item"),t.getInteger("Damage"),t.getInteger("Required"));g.delivered=Math.max(0,Math.min(g.required,t.getInteger("Delivered")));a.goods.add(g);}
            NBTTagList types=n.getTagList("EnemyClasses",10);if(types.tagCount()>8)return null;for(int i=0;i<types.tagCount();i++)a.enemyClasses.add(types.getCompoundTagAt(i).getString("Class"));return a;
        } catch(IllegalArgumentException invalid) { return null; }
    }
}
