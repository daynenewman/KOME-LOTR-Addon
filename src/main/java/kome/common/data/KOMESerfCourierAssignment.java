package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;
import lotr.common.world.biome.LOTRBiome;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.nbt.NBTTagCompound;

/** Persistent destination-first authority for the Courier duty. */
public final class KOMESerfCourierAssignment {
    public static final int VERSION=6,MIN_COURIER_DISTANCE=500,MAX_COURIER_DISTANCE=5000;
    public static final String RULE_SAME_FACTION_ADULT_NPC="same_faction_adult_npc";
    public enum Kind { MESSAGE("message"),PARCEL("parcel");public final String key;Kind(String k){key=k;}static Kind of(String k){for(Kind v:values())if(v.key.equals(k))return v;return null;} }
    public enum Stage { OUTBOUND("outbound"),DELIVERED("delivered");public final String key;Stage(String k){key=k;}static Stage of(String k){if("undeliverable".equals(k))return OUTBOUND;for(Stage v:values())if(v.key.equals(k))return v;return null;} }

    public final String masterFactionKey,token;
    public final int dimension,minimumDistance;
    public final double x,y,z;
    public String destinationKey,destinationName,destinationFactionKey;
    public double destinationX,destinationZ;
    public Kind kind=Kind.MESSAGE;
    public String cargo="";
    public Stage stage;
    public KOMEProgressionNpcRef recipient;
    public int storyVariant,recipientDeaths;
    public long nextRecipientWorldTime;

    private KOMESerfCourierAssignment(String faction,String token,int dimension,double x,double y,double z,
            int minimumDistance,Stage stage,KOMEProgressionNpcRef recipient,String destinationKey,
            String destinationName,String destinationFactionKey,double destinationX,double destinationZ,
            int storyVariant){
        masterFactionKey=safe(faction);this.token=safe(token);this.dimension=dimension;this.x=x;this.y=y;this.z=z;
        this.minimumDistance=minimumDistance;this.stage=stage;this.recipient=recipient==null?KOMEProgressionNpcRef.EMPTY:recipient;
        setDestination(destinationKey,destinationName,destinationFactionKey,destinationX,destinationZ);
        this.storyVariant=storyVariant;
    }

    public static KOMESerfCourierAssignment create(KOMEProgressionNpcRef master){LOTRWaypoint w=chooseDestination(master);return w==null?null:create(master,w);}
    public static KOMESerfCourierAssignment create(KOMEProgressionNpcRef master,World world){
        if(master==null||!master.isSet()||world==null||world.provider.dimensionId!=master.dimension)return null;
        String token=UUID.randomUUID().toString();GeographicDestination destination=chooseGeographic(master,world,token);
        if(destination==null)return null;
        return new KOMESerfCourierAssignment(master.factionKey,token,master.dimension,master.x,master.y,master.z,
            MIN_COURIER_DISTANCE,Stage.OUTBOUND,KOMEProgressionNpcRef.EMPTY,destination.key,destination.name,
            master.factionKey,destination.x,destination.z,Math.floorMod(token.hashCode(),5));
    }
    static KOMESerfCourierAssignment create(KOMEProgressionNpcRef master,LOTRWaypoint w){
        if(master==null||!master.isSet()||w==null)return null;String token=UUID.randomUUID().toString();
        String faction=w.faction==null?master.factionKey:w.faction.codeName();
        return new KOMESerfCourierAssignment(master.factionKey,token,master.dimension,master.x,master.y,master.z,
            MIN_COURIER_DISTANCE,Stage.OUTBOUND,KOMEProgressionNpcRef.EMPTY,w.getCodeName(),w.getDisplayName(),faction,
            w.getXCoord(),w.getZCoord(),Math.floorMod(token.hashCode(),5));
    }
    static LOTRWaypoint chooseDestination(KOMEProgressionNpcRef master){return master==null||!master.isSet()?null:
        chooseDestination(master.factionKey,master.dimension,master.x,master.z,master.entityUuid,null,0);}

    private static GeographicDestination chooseGeographic(KOMEProgressionNpcRef master,World world,String seed){
        LOTRFaction faction=faction(master.factionKey);if(faction==null||master.dimension!=LOTRDimension.MIDDLE_EARTH.dimensionID)return null;
        java.util.Random random=new java.util.Random((seed==null?0:seed.hashCode())*31L
            +Double.doubleToLongBits(master.x)+Double.doubleToLongBits(master.z));
        for(int attempt=0;attempt<192;attempt++){
            double distance=MIN_COURIER_DISTANCE+random.nextDouble()*(MAX_COURIER_DISTANCE-MIN_COURIER_DISTANCE);
            double angle=random.nextDouble()*Math.PI*2D;
            int cx=((int)Math.round(master.x+Math.cos(angle)*distance))&~15;
            int cz=((int)Math.round(master.z+Math.sin(angle)*distance))&~15;
            cx+=8;cz+=8;String key=geographicKey(cx,cz);
            double actualX=cx-master.x,actualZ=cz-master.z,actualDistance=Math.sqrt(actualX*actualX+actualZ*actualZ);
            if(actualDistance<MIN_COURIER_DISTANCE||actualDistance>MAX_COURIER_DISTANCE)continue;
            BiomeGenBase base=world.getWorldChunkManager().getBiomeGenAt(cx,cz);
            if(!(base instanceof LOTRBiome))continue;LOTRBiome biome=(LOTRBiome)base;
            if(!isTerritoryCandidate(world,faction,biome,cx,cz))continue;
            return new GeographicDestination(key,biome.getBiomeDisplayName(),cx,cz);
        }
        return null;
    }

    static boolean isTerritoryCandidate(World world,LOTRFaction faction,LOTRBiome biome,int x,int z){
        if(world==null||faction==null||biome==null||biome==LOTRBiome.utumno||biome.isWateryBiome()
                ||biome.heightBaseParameter>1.25F||biome.heightBaseParameter<-0.5F)return false;
        boolean controlled=faction.inDefinedControlZone(world,x,64,z);
        boolean nativeSpawns=biome.npcSpawnList!=null&&biome.npcSpawnList.isFactionPresent(world,faction);
        return controlled||nativeSpawns;
    }
    static double distanceFromOrigin(KOMESerfCourierAssignment a){double dx=a.destinationX-a.x,dz=a.destinationZ-a.z;return Math.sqrt(dx*dx+dz*dz);}
    private static String geographicKey(int x,int z){return "territory:"+Integer.toHexString(x)+":"+Integer.toHexString(z);}
    private static final class GeographicDestination{final String key,name;final double x,z;GeographicDestination(String k,String n,double x,double z){key=k;name=n==null||n.length()==0?"Faction lands":n;this.x=x;this.z=z;}}

    private static LOTRWaypoint chooseDestination(String factionKey,int dimension,double x,double z,String seed,String excluded,int salt){
        if(dimension!=LOTRDimension.MIDDLE_EARTH.dimensionID)return null;LOTRFaction master=faction(factionKey);if(master==null)return null;
        List<LOTRWaypoint> own=candidates(master,x,z,excluded,true),allied=allied(master,x,z,excluded,true);
        List<LOTRWaypoint> choices=own.isEmpty()?allied:own;
        if(choices.isEmpty()){own=candidates(master,x,z,excluded,false);allied=allied(master,x,z,excluded,false);choices=own.isEmpty()?allied:own;}
        return choices.isEmpty()?null:choices.get(Math.floorMod((seed==null?0:seed.hashCode())+salt,choices.size()));
    }
    private static List<LOTRWaypoint> candidates(LOTRFaction wanted,double x,double z,String excluded,boolean settlements){
        List<LOTRWaypoint> result=new ArrayList<LOTRWaypoint>();for(LOTRWaypoint w:LOTRWaypoint.values())if(w.faction==wanted&&eligibleDestination(w,x,z,excluded,settlements))result.add(w);return result;
    }
    private static List<LOTRWaypoint> allied(LOTRFaction master,double x,double z,String excluded,boolean settlements){
        List<LOTRWaypoint> result=new ArrayList<LOTRWaypoint>();for(LOTRWaypoint w:LOTRWaypoint.values())if(w.faction!=null&&w.faction!=master&&!master.isBadRelation(w.faction)&&!w.faction.isBadRelation(master)&&eligibleDestination(w,x,z,excluded,settlements))result.add(w);return result;
    }
    private static boolean eligibleDestination(LOTRWaypoint w,double x,double z,String excluded,boolean settlements){return !w.isHidden()&&!w.getCodeName().equals(excluded)&&farEnough(w,x,z)&&(!settlements||looksPopulated(w));}

    /** LOTR exposes no settlement-kind field; place-name forms avoid roads, fords, mountains and regions. */
    static boolean looksPopulated(LOTRWaypoint w){
        String n=w.name();String[] forms={"CITY","HALLS","TOWN","BURG","HOLD","PORT","FORT","KEEP","DEEP","LANDING","INN","MINAS","MITHLOND","EDORAS","ALDBURG","GRIMSLADE","PELARGIR","RIVENDELL","HOBBITON","BREE","ISENGARD","EREBOR","DALE","BELEGOST","NOGROD","FORNOST","ANNUMINAS","CARN_DUM","DOL_GULDUR","CARAS_GALADHON","DOL_AMROTH","BARAD_DUR","MORANNON","UMBAR","HARAD","KHAND","RHUN"};
        for(String form:forms)if(n.contains(form))return true;return false;
    }
    private void setDestination(String key,String name,String faction,double dx,double dz){destinationKey=safe(key);destinationName=safe(name);destinationFactionKey=safe(faction);destinationX=dx;destinationZ=dz;}
    private static String safe(String s){return s==null?"":s;}
    private static boolean farEnough(LOTRWaypoint w,double x,double z){double dx=w.getXCoord()-x,dz=w.getZCoord()-z;return dx*dx+dz*dz>=MIN_COURIER_DISTANCE*(double)MIN_COURIER_DISTANCE;}
    private static LOTRFaction faction(String key){if(key!=null)for(LOTRFaction f:LOTRFaction.values())if(key.equalsIgnoreCase(f.codeName()))return f;return null;}
    private static LOTRWaypoint waypoint(String key){if(key!=null)for(LOTRWaypoint w:LOTRWaypoint.values())if(key.equals(w.getCodeName()))return w;return null;}
    public boolean valid(){return masterFactionKey.length()>0&&token.length()>0&&stage!=null&&minimumDistance>0&&destinationKey.length()>0&&destinationName.length()>0&&destinationFactionKey.length()>0&&storyVariant>=0&&storyVariant<5;}
    public boolean atDestination(int dim,double px,double pz,double radius){double dx=px-destinationX,dz=pz-destinationZ;return dim==dimension&&dx*dx+dz*dz<=radius*radius;}

    public NBTTagCompound writeToNBT(){NBTTagCompound t=new NBTTagCompound();t.setInteger("Version",VERSION);t.setString("Kind",kind.key);t.setString("Cargo",cargo);t.setString("Stage",stage.key);t.setString("MasterFactionKey",masterFactionKey);t.setString("RecipientRuleKey",RULE_SAME_FACTION_ADULT_NPC);t.setTag("RecipientNpc",recipient.writeToNBT());t.setInteger("MasterDimension",dimension);t.setDouble("MasterX",x);t.setDouble("MasterY",y);t.setDouble("MasterZ",z);t.setInteger("MinimumTravelDistance",minimumDistance);t.setString("PackageId",token);t.setString("DestinationKey",destinationKey);t.setString("DestinationName",destinationName);t.setString("DestinationFactionKey",destinationFactionKey);t.setDouble("DestinationX",destinationX);t.setDouble("DestinationZ",destinationZ);t.setInteger("StoryVariant",storyVariant);t.setInteger("RecipientDeaths",recipientDeaths);t.setLong("NextRecipientWorldTime",nextRecipientWorldTime);return t;}
    public static KOMESerfCourierAssignment readFromNBT(NBTTagCompound t){
        if(t==null||!RULE_SAME_FACTION_ADULT_NPC.equals(t.getString("RecipientRuleKey")))return null;int version=t.getInteger("Version");if(version<1||version>VERSION)return null;
        String key=t.getString("DestinationKey"),name=t.getString("DestinationName"),df=t.getString("DestinationFactionKey");double dx=t.getDouble("DestinationX"),dz=t.getDouble("DestinationZ");LOTRWaypoint w=waypoint(key);
        if(version==1){w=chooseDestination(t.getString("MasterFactionKey"),t.getInteger("MasterDimension"),t.getDouble("MasterX"),t.getDouble("MasterZ"),t.getString("PackageId"),null,0);if(w==null)return null;key=w.getCodeName();name=w.getDisplayName();dx=w.getXCoord();dz=w.getZCoord();}
        if(df.length()==0&&w!=null&&w.faction!=null)df=w.faction.codeName();int variant=version>=3?t.getInteger("StoryVariant"):Math.floorMod(t.getString("PackageId").hashCode(),5);
        KOMESerfCourierAssignment a=new KOMESerfCourierAssignment(t.getString("MasterFactionKey"),t.getString("PackageId"),t.getInteger("MasterDimension"),t.getDouble("MasterX"),t.getDouble("MasterY"),t.getDouble("MasterZ"),Math.max(MIN_COURIER_DISTANCE,t.getInteger("MinimumTravelDistance")),Stage.of(t.getString("Stage")),KOMEProgressionNpcRef.readFromNBT(t.getCompoundTag("RecipientNpc")),key,name,df,dx,dz,variant);
        a.kind=Kind.of(t.getString("Kind"));a.cargo=t.getString("Cargo");a.recipientDeaths=version>=6?Math.max(0,t.getInteger("RecipientDeaths")):0;a.nextRecipientWorldTime=version>=6?Math.max(0L,t.getLong("NextRecipientWorldTime")):0L;if(version<6&&a.stage==Stage.OUTBOUND)a.recipient=KOMEProgressionNpcRef.EMPTY;return a.valid()&&a.kind!=null?a:null;
    }
}
