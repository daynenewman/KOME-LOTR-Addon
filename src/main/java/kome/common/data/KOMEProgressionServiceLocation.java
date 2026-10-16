package kome.common.data;

import java.util.*;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/** Native generation provenance and native home geometry; never guesses a seat from a biome. */
public final class KOMEProgressionServiceLocation {
    public static final String TAG = "KOMERegionalSeat";
    static final String REPLACEMENT_TAG = "KOMERegionalSeatReplacement";
    private static final Map<String, String> HOSTS = new LinkedHashMap<String, String>();
    static {
        hosts("hobbit", "HobbitTavern");
        hosts("bree", "BreeOffice");
        hosts("dunedain", "RangerCamp");
        hosts("bluemountains", "BlueMountainsStronghold");
        hosts("highelves", "HighElvenTower", "RivendellHall");
        hosts("gundabad", "GundabadCamp", "RuinedDwarvenTower");
        hosts("angmar", "AngmarTower", "AngmarHillmanChieftainHouse");
        hosts("woodelf", "WoodElfTower");
        hosts("dolguldur", "DolGuldurTower");
        hosts("dale", "DaleFortress", "DaleWatchtower");
        hosts("durinsfolk", "DwarvenTower");
        hosts("lothlorien", "ElfLordHouse");
        hosts("dunland", "DunlandHillFort");
        hosts("isengard", "UrukCamp");
        hosts("rohan", "RohanFortress", "RohanWatchtower", "MeadHall");
        for (String fief : new String[]{"Gondor", "Blackroot", "DolAmroth", "Lamedon", "Lebennin", "Lossarnach", "Pelargir", "PinnathGelin"})
            hosts("gondor", fief + "Fortress", fief + "Watchfort", fief + "Watchtower");
        HOSTS.remove("LOTRWorldGenDolAmrothFortress"); // v36.15 supplies its watchfort and watchtower only.
        hosts("mordor", "MordorTower", "BlackUrukFort");
        hosts("dorwinion", "DorwinionCaptainTent");
        hosts("rhudel", "EasterlingFortress");
        hosts("harad", "UmbarFortress", "SouthronFortress", "GulfWarCamp");
        hosts("morwaith", "MoredainHutChieftain");
        hosts("taurethrim", "TauredainChieftainPyramid");
        hosts("halftroll", "HalfTrollWarlordHouse");
    }
    private KOMEProgressionServiceLocation() { }
    private static void hosts(String faction, String... names) {
        for (String name : names) HOSTS.put("LOTRWorldGen" + name, faction);
    }
    public static Map<String, String> supportedHosts() { return Collections.unmodifiableMap(HOSTS); }
    /** Ordinary service uses native territory, with no fort, settlement or capital requirement. */
    public static boolean factionTerritory(LOTREntityNPC npc) {
        if(npc==null||npc.getFaction()==null||npc.worldObj==null||npc.worldObj.getWorldChunkManager()==null
                ||npc.worldObj.provider.dimensionId!=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID)return false;
        int x=(int)Math.floor(npc.posX),z=(int)Math.floor(npc.posZ);
        net.minecraft.world.biome.BiomeGenBase biome=npc.worldObj.getWorldChunkManager().getBiomeGenAt(x,z);
        return biome instanceof lotr.common.world.biome.LOTRBiome
            &&(npc.getFaction().inDefinedControlZone(npc.worldObj,x,(int)npc.posY,z)
                ||((lotr.common.world.biome.LOTRBiome)biome).npcSpawnList!=null
                    &&((lotr.common.world.biome.LOTRBiome)biome).npcSpawnList.isFactionPresent(npc.worldObj,npc.getFaction()));
    }

    /** Called only around native generator spawn calls. Preserve their exact boolean result. */
    public static boolean generated(World world, Entity entity, boolean spawned, Object generator) {
        if (!spawned || world == null || world.isRemote || !(entity instanceof LOTREntityNPC) || generator == null) return spawned;
        LOTREntityNPC npc = (LOTREntityNPC) entity;
        String host = generator.getClass().getSimpleName(), faction = HOSTS.get(host);
        String generatorName = generator.getClass().getName();
        if ((!generatorName.equals("lotr.common.world.structure." + host)
                && !generatorName.equals("lotr.common.world.structure2." + host))
                || npc.worldObj != world || faction == null || !KOMEProgressionFactionResolver.matches(faction, npc.getFaction())
                || !KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)
                || KOMEProgressionEncounterMarker.read(npc) != null) return spawned;
        NBTTagCompound proof = new NBTTagCompound();
        proof.setInteger("Version", 1); proof.setString("Host", host);
        proof.setString("NPC", npc.getUniqueID().toString()); proof.setString("Faction", faction);
        proof.setInteger("Dimension", world.provider.dimensionId);
        proof.setDouble("X", npc.posX); proof.setDouble("Y", npc.posY); proof.setDouble("Z", npc.posZ);
        npc.getEntityData().setTag(TAG, proof);
        return spawned;
    }
    public static boolean regional(LOTREntityNPC npc) {
        if (npc == null || npc.getFaction()==null||npc.worldObj == null || npc.worldObj.isRemote) return false;
        NBTTagCompound proof = npc.getEntityData().getCompoundTag(TAG);
        if (!validProof(proof, npc.getUniqueID().toString(), npc.getFaction().codeName(), npc.worldObj.provider.dimensionId)) return false;
        if(!npc.hasHome()||npc.getHomePosition()==null||!npc.isWithinHomeDistanceCurrentPosition())return false;
        // The distance is the generator's own persisted home range, not a KOME settlement radius.
        double range = npc.func_110174_bM();
        net.minecraft.util.ChunkCoordinates home = npc.getHomePosition();
        double dx = home.posX - proof.getDouble("X"), dy = home.posY - proof.getDouble("Y"), dz = home.posZ - proof.getDouble("Z");
        return Double.isFinite(range) && range > 0 && dx*dx + dy*dy + dz*dz <= range*range;
    }
    /** Native copyTraderDataFrom callback. A home alone never creates this receipt. */
    public static void captureReplacement(Entity carrier, LOTREntityNPC source) {
        if (!(carrier instanceof lotr.common.entity.item.LOTREntityTraderRespawn)
                || carrier.worldObj == null || carrier.worldObj.isRemote) return;
        carrier.getEntityData().removeTag(REPLACEMENT_TAG);
        if (source == null || source.worldObj != carrier.worldObj || !source.shouldTraderRespawn()
                || source.isChild() || source.isTraderEscort
                || source.hiredNPCInfo == null || source.hiredNPCInfo.isActive
                || KOMEProgressionEncounterMarker.read(source) != null
                || !KOMEProgressionLords.isStandingTrialLiegeCandidate(source) || !regional(source)) return;
        NBTTagCompound receipt = new NBTTagCompound();
        receipt.setInteger("Version", 1);
        receipt.setString("Carrier", carrier.getUniqueID().toString());
        receipt.setString("Source", source.getUniqueID().toString());
        receipt.setString("Class", source.getClass().getName());
        net.minecraft.util.ChunkCoordinates home = source.getHomePosition();
        receipt.setInteger("HomeX", home.posX); receipt.setInteger("HomeY", home.posY); receipt.setInteger("HomeZ", home.posZ);
        receipt.setFloat("HomeRange", source.func_110174_bM());
        receipt.setTag("Proof", source.getEntityData().getCompoundTag(TAG).copy());
        carrier.getEntityData().setTag(REPLACEMENT_TAG, receipt);
    }
    /** Preserve the exact native spawn result. No NPC creation, relationship transfer or rank grant. */
    public static boolean replacementSpawned(World world, Entity entity, boolean spawned, Entity carrier) {
        if (!spawned || world == null || world.isRemote || !(entity instanceof LOTREntityNPC)
                || !(carrier instanceof lotr.common.entity.item.LOTREntityTraderRespawn)
                || carrier.worldObj != world || entity.worldObj != world) return spawned;
        LOTREntityNPC npc = (LOTREntityNPC) entity;
        NBTTagCompound receipt = carrier.getEntityData().getCompoundTag(REPLACEMENT_TAG);
        NBTTagCompound proof = receipt.getCompoundTag("Proof");
        if (receipt.getInteger("Version") != 1 || !carrier.getUniqueID().toString().equals(receipt.getString("Carrier"))
                || !npc.getClass().getName().equals(receipt.getString("Class"))
                || npc.getUniqueID().toString().equals(receipt.getString("Source")) || npc.getFaction() == null
                || !validProof(proof, receipt.getString("Source"), npc.getFaction().codeName(), world.provider.dimensionId)
                || npc.getEntityData().hasKey(TAG) || !npc.shouldTraderRespawn()
                || npc.isChild() || npc.isTraderEscort || npc.hiredNPCInfo == null || npc.hiredNPCInfo.isActive
                || KOMEProgressionEncounterMarker.read(npc) != null
                || !KOMEProgressionLords.isStandingTrialLiegeCandidate(npc)
                || !npc.hasHome() || npc.getHomePosition() == null) return spawned;
        net.minecraft.util.ChunkCoordinates home = npc.getHomePosition();
        float range = receipt.getFloat("HomeRange");
        if (!receipt.hasKey("HomeX", 3) || !receipt.hasKey("HomeY", 3) || !receipt.hasKey("HomeZ", 3)
                || !receipt.hasKey("HomeRange", 5) || !Float.isFinite(range) || range <= 0 || home.posX != receipt.getInteger("HomeX")
                || home.posY != receipt.getInteger("HomeY") || home.posZ != receipt.getInteger("HomeZ")
                || npc.func_110174_bM() != Math.round(range)) return spawned;
        NBTTagCompound rebound = (NBTTagCompound) proof.copy();
        rebound.setString("NPC", npc.getUniqueID().toString());
        npc.getEntityData().setTag(TAG, rebound);
        if (!regional(npc)) { npc.getEntityData().removeTag(TAG); return spawned; }
        carrier.getEntityData().removeTag(REPLACEMENT_TAG); // Consume only on verified successful native replacement.
        return spawned;
    }
    static boolean validProof(NBTTagCompound proof, String npcId, String faction, int dimension) {
        if (proof == null || npcId == null) return false;
        try { if (!UUID.fromString(npcId).toString().equals(npcId)) return false; }
        catch (IllegalArgumentException invalid) { return false; }
        String hostFaction = HOSTS.get(proof.getString("Host"));
        return proof.getInteger("Version") == 1 && npcId.equals(proof.getString("NPC"))
            && proof.getInteger("Dimension") == dimension && hostFaction != null
            && KOMEProgressionRelationshipLifecycle.sameFaction(hostFaction, faction)
            && KOMEProgressionRelationshipLifecycle.sameFaction(proof.getString("Faction"), faction)
            && proof.hasKey("X", 99) && proof.hasKey("Y", 99) && proof.hasKey("Z", 99)
            && Double.isFinite(proof.getDouble("X")) && Double.isFinite(proof.getDouble("Y")) && Double.isFinite(proof.getDouble("Z"));
    }
    /** Future royal service seam only. It issues no assignment or political office. */
    public static boolean royal(KOMEWorldData data, LOTREntityNPC npc) {
        if (data == null || npc == null || npc.worldObj==null||npc.worldObj.isRemote||npc.getFaction() == null
                || !npc.isEntityAlive()||npc.isChild()||npc.hiredNPCInfo==null||npc.hiredNPCInfo.isActive) return false;
        String faction = npc.getFaction().codeName();
        KOMEProgressionNpcRankRecord king = KOMEProgressionRulerService.slot(data, faction);
        KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, faction);
        if (king == null || capital == null || !king.npcUuid.equals(npc.getUniqueID())
                || npc.worldObj.provider.dimensionId != capital.getDeploymentDimensionId()
                || KOMEProgressionEncounterMarker.rulerIncarnation(npc) != king.incarnation) return false;
        KOMETileResolution location = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(npc.worldObj.provider.dimensionId, npc.posX, npc.posZ);
        return location.status == KOMETileResolution.Status.RESOLVED && capital.getCapitalTileId().equals(location.tileId);
    }
}
