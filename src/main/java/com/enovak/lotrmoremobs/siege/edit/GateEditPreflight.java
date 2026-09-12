package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.banner.SiegeGateBannerAttachmentData;
import com.enovak.lotrmoremobs.siege.creation.GateSourceBlockValidator;
import com.enovak.lotrmoremobs.siege.gate.*;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import java.util.*;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;

/** One canonical, read-only evaluator for informational Phase 3 and future commit admission. */
public final class GateEditPreflight {
    private static final int MAX_ISSUE_COUNT = GateStructureValidator.MAX_GATE_PARTS * 2;
    private GateEditPreflight() {}

    public static GateEditPreflightResult evaluate(EntityPlayerMP player, GateEditSession session) {
        if (session == null) throw new IllegalArgumentException("Edit session required.");
        GateEditDraft draft=session.getDraft(); GateEditOriginalSnapshot original=session.getOriginal();
        Conversion conversion=convert(draft,original); GateEditStructuralValidation structural=validate(draft,session,conversion.parts);
        Delta delta=Delta.between(original,draft); Issues issues=new Issues();
        if (!structural.isStructurallyValid()) issues.add(structural.getPrimaryIssue());
        boolean fatal=false; GateEditPreflightIssueCode fatalIssue=GateEditPreflightIssueCode.NONE;
        int currentRevision=-1; GateState gateState=null; boolean repair=false,ram=false;
        World world=player==null?null:player.worldObj; TileEntitySiegeGate gate=null;
        if(player==null||player.isDead||world==null||world.isRemote||player.dimension!=session.getDimensionId()) { fatal=true; fatalIssue=GateEditPreflightIssueCode.CONTROLLER_MISMATCH; issues.add(fatalIssue); }
        else if(!loaded(world,session.getControllerX(),session.getControllerZ())) { fatal=true; fatalIssue=GateEditPreflightIssueCode.CONTROLLER_MISSING; issues.add(fatalIssue); }
        else if(world.getBlock(session.getControllerX(),session.getControllerY(),session.getControllerZ())!=SiegeRegistry.gateController) { fatal=true; fatalIssue=GateEditPreflightIssueCode.CONTROLLER_MISSING; issues.add(fatalIssue); }
        else { TileEntity tile=world.getTileEntity(session.getControllerX(),session.getControllerY(),session.getControllerZ()); if(!(tile instanceof TileEntitySiegeGate)){fatal=true;fatalIssue=GateEditPreflightIssueCode.CONTROLLER_MISSING;issues.add(fatalIssue);} else gate=(TileEntitySiegeGate)tile; }
        if(gate!=null) {
            currentRevision=gate.getStructureRevision(); gateState=gate.getGateState(); repair=gate.isRepairActive(); ram=gate.getReservedRamUuid()!=null;
            if(!gate.isFinalized()){fatal=true;fatalIssue=GateEditPreflightIssueCode.CONTROLLER_MISMATCH;issues.add(fatalIssue);}
            else if(gate.isGateStructureQuarantined()){fatal=true;fatalIssue=GateEditPreflightIssueCode.QUARANTINED;issues.add(fatalIssue);}
            else if(!gate.canManage(player)){fatal=true;fatalIssue=GateEditPreflightIssueCode.NO_PERMISSION;issues.add(fatalIssue);}
            else if(gate.getExistingGateUuid()==null||!session.getGateUuid().equals(gate.getExistingGateUuid())){fatal=true;fatalIssue=GateEditPreflightIssueCode.UUID_MISMATCH;issues.add(fatalIssue);}
            else if(currentRevision<=0||currentRevision!=session.getBaseRevision()){fatal=true;fatalIssue=GateEditPreflightIssueCode.STALE_REVISION;issues.add(fatalIssue);}
            else if(!matchesOriginal(gate,original)){fatal=true;fatalIssue=GateEditPreflightIssueCode.ORIGINAL_MISMATCH;issues.add(fatalIssue);}
            else {
                SiegeGateOwnershipData data=SiegeGateOwnershipData.get(world,false);
                if(data==null) { fatal=true;fatalIssue=GateEditPreflightIssueCode.OWNERSHIP_MISMATCH;issues.add(fatalIssue); }
                else {
                    SiegeGateOwnershipData.GateMutationState mutation=data.getGateMutationState(session.getGateUuid(),world.provider.dimensionId,session.getControllerX(),session.getControllerY(),session.getControllerZ());
                    if(mutation!=SiegeGateOwnershipData.GateMutationState.NONE){fatal=true;fatalIssue=GateEditPreflightIssueCode.MUTATION_IN_PROGRESS;issues.add(fatalIssue);}
                    else {
                        SiegeGateOwnershipData.ActiveControllerCheck check=data.evaluateActiveController(gate,session.getGateUuid(),session.getBaseRevision(),conversion.originalParts);
                        GateEditPreflightIssueCode code=map(check);
                        if(code!=GateEditPreflightIssueCode.NONE){fatal=true;fatalIssue=code;issues.add(code);}
                    }
                }
            }
        }
        if(!fatal && structural.isStructurallyValid() && delta.hasChanges) {
            if(gateState!=GateState.CLOSED) issues.add(GateEditPreflightIssueCode.GATE_NOT_CLOSED);
            if(repair) issues.add(GateEditPreflightIssueCode.REPAIR_ACTIVE);
            if(ram) issues.add(GateEditPreflightIssueCode.RAM_RESERVED);
            if (!isBannerEditCompatible(world, session, conversion, delta)) {
                issues.add(GateEditPreflightIssueCode.BANNER_ATTACHMENT_CONFLICT);
            }
            checkPhysical(world,session,delta,issues);
        }
        GateEditPreflightState state; GateEditPreflightIssueCode primary;
        if(fatal){state=GateEditPreflightState.STALE_SESSION;primary=fatalIssue;}
        else if(!structural.isStructurallyValid()){state=GateEditPreflightState.INVALID_DRAFT;primary=structural.getPrimaryIssue();}
        else if(!delta.hasChanges){state=GateEditPreflightState.NO_CHANGES;issues.add(GateEditPreflightIssueCode.NO_CHANGES);primary=GateEditPreflightIssueCode.NO_CHANGES;}
        else if(issues.hasAny()){state=GateEditPreflightState.NOT_READY;primary=issues.first();}
        else {state=GateEditPreflightState.READY;primary=GateEditPreflightIssueCode.NONE;}
        return new GateEditPreflightResult(session.nextPreflightGeneration(),session.getDraftSequence(),state,structural,delta.hasChanges,primary,delta.changeCount,currentRevision,gateState,repair,ram,issues.snapshot());
    }

    private static Conversion convert(
            GateEditDraft draft,
            GateEditOriginalSnapshot original
    ) {
        List<GatePartData> parts =
                new ArrayList<GatePartData>(
                        draft.getPartCount()
                );

        List<GatePartData> originalParts =
                new ArrayList<GatePartData>(
                        original.getParts().size()
                );

        for (GateEditOriginalPart part
                : original.getParts()) {

            originalParts.add(
                    toPart(
                            part,
                            part.getLeaf()
                    )
            );
        }

        for (GateEditDraftPart part
                : draft.getParts()) {

            GateEditOriginalPart originalPart =
                    original.findPart(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            if (part.originatesFromOriginal()) {
                if (originalPart != null) {
                    parts.add(
                            toPart(
                                    originalPart,
                                    part.getLeaf()
                            )
                    );
                }

                continue;
            }

            GateEditAddedSource source =
                    part.getAddedSource();

            if (source != null) {
                parts.add(
                        toPart(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ(),
                                part.getLeaf(),
                                source.getRegistryName(),
                                source.getMetadata(),
                                source.getSourceTileEntityNbt(),
                                source.isRestorable()
                        )
                );
            }
        }

        return new Conversion(
                parts,
                originalParts
        );
    }

    private static boolean isBannerEditCompatible(
            World world,
            GateEditSession session,
            Conversion conversion,
            Delta delta
    ) {
        if (world == null || session == null || conversion == null || delta == null) {
            return false;
        }

        Map<GateEditCoordinate, GatePartData> targetByCoordinate =
                new HashMap<GateEditCoordinate, GatePartData>();
        for (GatePartData part : conversion.parts) {
            if (part != null) {
                targetByCoordinate.put(
                        new GateEditCoordinate(
                                part.getRelativeX(),
                                part.getRelativeY(),
                                part.getRelativeZ()
                        ),
                        part
                );
            }
        }

        List<GatePartData> addedParts = new ArrayList<GatePartData>();
        for (GateEditCoordinate coordinate : delta.adds) {
            GatePartData part = targetByCoordinate.get(coordinate);
            if (part != null) {
                addedParts.add(part);
            }
        }

        return SiegeGateBannerAttachmentData.isEditTargetCompatible(
                world,
                session.getGateUuid(),
                session.getControllerX(),
                session.getControllerY(),
                session.getControllerZ(),
                conversion.parts,
                addedParts
        );
    }

    /**
     * Builds final-state transaction evidence from the authoritative detached
     * draft only. Admission still must run evaluate(...) immediately before
     * using this material and require READY.
     */
    public static GateEditCommitMaterial buildCommitMaterial(
            GateEditSession session
    ) {
        if (session == null) {
            return null;
        }
        GateEditOriginalSnapshot original = session.getOriginal();
        GateEditDraft draft = session.getDraft();
        Conversion conversion = convert(draft, original);
        Delta delta = Delta.between(original, draft);
        Map<GateEditCoordinate, GatePartData> originalByCoordinate =
                new HashMap<GateEditCoordinate, GatePartData>();
        Map<GateEditCoordinate, GatePartData> targetByCoordinate =
                new HashMap<GateEditCoordinate, GatePartData>();
        for (GatePartData part : conversion.originalParts) {
            originalByCoordinate.put(new GateEditCoordinate(
                    part.getRelativeX(), part.getRelativeY(), part.getRelativeZ()
            ), part);
        }
        for (GatePartData part : conversion.parts) {
            targetByCoordinate.put(new GateEditCoordinate(
                    part.getRelativeX(), part.getRelativeY(), part.getRelativeZ()
            ), part);
        }
        List<GateEditCommitMaterial.PhysicalOperation> operations =
                new ArrayList<GateEditCommitMaterial.PhysicalOperation>();
        for (GateEditCoordinate coordinate : delta.removes) {
            GatePartData part = originalByCoordinate.get(coordinate);
            if (part == null) {
                return null;
            }
            operations.add(new GateEditCommitMaterial.PhysicalOperation(
                    GateEditCommitMaterial.PhysicalOperationKind.REMOVE, part
            ));
        }
        for (GateEditCoordinate coordinate : delta.adds) {
            GatePartData part = targetByCoordinate.get(coordinate);
            if (part == null) {
                return null;
            }
            operations.add(new GateEditCommitMaterial.PhysicalOperation(
                    GateEditCommitMaterial.PhysicalOperationKind.ADD, part
            ));
        }
        Collections.sort(operations,
                new Comparator<GateEditCommitMaterial.PhysicalOperation>() {
                    @Override
                    public int compare(
                            GateEditCommitMaterial.PhysicalOperation first,
                            GateEditCommitMaterial.PhysicalOperation second
                    ) {
                        GatePartData a = first.getPart();
                        GatePartData b = second.getPart();
                        int result = Integer.compare(
                                session.getControllerY() + a.getRelativeY(),
                                session.getControllerY() + b.getRelativeY()
                        );
                        if (result != 0) return result;
                        result = Integer.compare(
                                session.getControllerX() + a.getRelativeX(),
                                session.getControllerX() + b.getRelativeX()
                        );
                        if (result != 0) return result;
                        result = Integer.compare(
                                session.getControllerZ() + a.getRelativeZ(),
                                session.getControllerZ() + b.getRelativeZ()
                        );
                        if (result != 0) return result;
                        return first.getKind().ordinal()
                                - second.getKind().ordinal();
                    }
                });
        return new GateEditCommitMaterial(session.getGateUuid(),
                session.getDimensionId(), session.getControllerX(),
                session.getControllerY(), session.getControllerZ(),
                session.getBaseRevision(), original.getOrientation(),
                original.getOpeningDirection(),
                original.isBorderTextureEnabled(),
                original.getLeftHinge(),
                original.getRightHinge(), draft.getOpeningDirection(),
                draft.isBorderTextureEnabled(),
                draft.getLeftHinge(), draft.getRightHinge(),
                conversion.originalParts, conversion.parts, operations);
    }

    private static GatePartData toPart(
            GateEditOriginalPart part,
            GateLeaf leaf
    ) {
        return toPart(
                part.getRelativeX(),
                part.getRelativeY(),
                part.getRelativeZ(),
                leaf,
                part.getSourceBlockName(),
                part.getSourceMetadata(),
                part.getSourceTileEntityNbt(),
                part.isSourceRestorable()
        );
    }

    private static GatePartData toPart(
            int x,
            int y,
            int z,
            GateLeaf leaf,
            String source,
            int metadata,
            NBTTagCompound sourceTileEntityNbt,
            boolean restorable
    ) {
        /*
         * Preserve the old fallback meaning exactly.
         */
        if (!restorable
                && GatePartData.FALLBACK_SOURCE_BLOCK.equals(
                source
        )
                && metadata == 0
                && sourceTileEntityNbt == null) {

            return new GatePartData(
                    x,
                    y,
                    z,
                    leaf
            );
        }

        return new GatePartData(
                x,
                y,
                z,
                leaf,
                source,
                metadata,
                sourceTileEntityNbt
        );
    }

    private static GatePartData toPart(int x,int y,int z,GateLeaf leaf,String source,int meta,boolean restorable){return restorable?new GatePartData(x,y,z,leaf,source,meta):new GatePartData(x,y,z,leaf);}
    private static GateEditStructuralValidation validate(GateEditDraft draft,GateEditSession session,List<GatePartData> parts){
        int left=0,right=0,center=0,minX=Integer.MAX_VALUE,minY=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxY=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(GatePartData p:parts){if(p.getLeaf().contributesToLeft())++left;if(p.getLeaf().contributesToRight())++right;if(p.getLeaf().isSplitCenter())++center;minX=Math.min(minX,p.getRelativeX());maxX=Math.max(maxX,p.getRelativeX());minY=Math.min(minY,p.getRelativeY());maxY=Math.max(maxY,p.getRelativeY());minZ=Math.min(minZ,p.getRelativeZ());maxZ=Math.max(maxZ,p.getRelativeZ());}
        GateStructureValidator.ValidationResult result=GateStructureValidator.validateFinalized(parts,draft.getLeftHinge(),draft.getRightHinge(),draft.getOrientation(),draft.getOpeningDirection(),session.getControllerX(),session.getControllerY(),session.getControllerZ());
        return new GateEditStructuralValidation(result.isValid(),result.getFailure(),parts.size(),left,right,center,parts.isEmpty()?0:maxX-minX+1,parts.isEmpty()?0:maxY-minY+1,parts.isEmpty()?0:maxZ-minZ+1);
    }

    private static boolean matchesOriginal(
            TileEntitySiegeGate gate,
            GateEditOriginalSnapshot original
    ) {
        if (gate.getGateOrientation()
                != original.getOrientation()
                || gate.getOpeningDirection()
                != original.getOpeningDirection()
                || gate.isGateBorderTextureEnabled()
                != original.isBorderTextureEnabled()
                || !same(
                gate.getLeftHinge(),
                original.getLeftHinge()
        )
                || !same(
                gate.getRightHinge(),
                original.getRightHinge()
        )) {
            return false;
        }

        List<GatePartData> current =
                gate.getGateParts();

        if (current.size()
                != original.getParts().size()) {
            return false;
        }

        for (GatePartData part : current) {
            GateEditOriginalPart stored =
                    original.findPart(
                            part.getRelativeX(),
                            part.getRelativeY(),
                            part.getRelativeZ()
                    );

            boolean restorable =
                    part.hasStoredSourceBlock()
                            && part.getSourceBlockForRestoration()
                            != null;

            if (stored == null
                    || stored.getLeaf()
                    != part.getLeaf()
                    || stored.getSourceMetadata()
                    != part.getSourceMetadata()
                    || stored.isSourceRestorable()
                    != restorable
                    || !stored.getSourceBlockName().equals(
                    part.getSourceBlockName()
            )
                    || !tagsEqual(
                    stored.getSourceTileEntityNbt(),
                    part.getSourceTileEntityNbt()
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean same(
            GateHinge first,
            GateHinge second
    ) {
        return first == null
                ? second == null
                : first.equals(second);
    }

    private static void checkPhysical(
            World world,
            GateEditSession session,
            Delta delta,
            Issues issues
    ) {
        Set<Long> checkedChunks =
                new HashSet<Long>();

        if (!checkChunk(
                world,
                session.getControllerX(),
                session.getControllerZ(),
                checkedChunks
        )) {
            issues.add(
                    GateEditPreflightIssueCode.CHUNK_UNLOADED
            );
        }

        for (GateEditCoordinate key
                : delta.adds) {

            int x =
                    session.getControllerX()
                            + key.x;

            int y =
                    session.getControllerY()
                            + key.y;

            int z =
                    session.getControllerZ()
                            + key.z;

            if (!checkChunk(
                    world,
                    x,
                    z,
                    checkedChunks
            )) {
                issues.add(
                        GateEditPreflightIssueCode.CHUNK_UNLOADED
                );

                continue;
            }

            GateEditDraftPart part =
                    session.getDraft()
                            .getPart(key);

            GateEditAddedSource source =
                    part == null
                            ? null
                            : part.getAddedSource();

            if (!matchesAddedSourceSnapshot(
                    world,
                    source,
                    x,
                    y,
                    z
            )) {
                issues.add(
                        GateEditPreflightIssueCode.SOURCE_CHANGED
                );

                continue;
            }

            if (world.getBlock(
                    x,
                    y,
                    z
            ) == SiegeRegistry.gateController
                    || world.getBlock(
                    x,
                    y,
                    z
            ) == SiegeRegistry.gatePart) {

                issues.add(
                        GateEditPreflightIssueCode.SOURCE_CHANGED
                );

                continue;
            }

            if (GateRegistry.getDurablePartOwner(
                    world,
                    x,
                    y,
                    z
            ) != null) {
                issues.add(
                        GateEditPreflightIssueCode.FOREIGN_OWNER
                );
            }
        }

        for (GateEditCoordinate key
                : delta.removes) {

            int x =
                    session.getControllerX()
                            + key.x;

            int y =
                    session.getControllerY()
                            + key.y;

            int z =
                    session.getControllerZ()
                            + key.z;

            if (!checkChunk(
                    world,
                    x,
                    z,
                    checkedChunks
            )) {
                issues.add(
                        GateEditPreflightIssueCode.CHUNK_UNLOADED
                );

                continue;
            }

            SiegeGateOwnershipData data =
                    SiegeGateOwnershipData.get(
                            world,
                            false
                    );

            SiegeGateOwnershipData.ExpectedBasePartCheck check =
                    data == null
                            ? SiegeGateOwnershipData
                            .ExpectedBasePartCheck
                            .OWNERSHIP_MISMATCH
                            : data.checkExpectedBasePart(
                            world,
                            session.getGateUuid(),
                            session.getControllerX(),
                            session.getControllerY(),
                            session.getControllerZ(),
                            session.getBaseRevision(),
                            x,
                            y,
                            z
                    );

            issues.add(
                    map(check)
            );
        }
    }

    private static boolean matchesAddedSourceSnapshot(
            World world,
            GateEditAddedSource source,
            int x,
            int y,
            int z
    ) {
        if (world == null
                || source == null) {
            return false;
        }

        Block block =
                world.getBlock(
                        x,
                        y,
                        z
                );

        int metadata =
                world.getBlockMetadata(
                        x,
                        y,
                        z
                );

        if (!source.getRegistryName().equals(
                GateSourceBlockValidator.getRegisteredName(
                        block
                )
        )
                || metadata
                != source.getMetadata()
                || !GateSourceBlockValidator.isValid(
                world,
                x,
                y,
                z
        )) {

            return false;
        }

        TileEntity tileEntity =
                world.getTileEntity(
                        x,
                        y,
                        z
                );

        try {
            boolean requiresTileEntity =
                    block != null
                            && block.hasTileEntity(
                            metadata
                    );

            if (source.hasSourceTileEntityNbt()) {
                return tileEntity != null;
            }

            return !requiresTileEntity
                    && tileEntity == null;

        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static boolean tagsEqual(
            NBTTagCompound first,
            NBTTagCompound second
    ) {
        if (first == second) {
            return true;
        }

        if (first == null
                || second == null) {
            return false;
        }

        return first.equals(second);
    }

    private static boolean checkChunk(World world,int x,int z,Set<Long> seen){long key=((long)(x>>4)<<32)|((z>>4)&0xffffffffL);return seen.add(key)?loaded(world,x,z):true;}
    private static boolean loaded(World world,int x,int z){return world!=null&&world.getChunkProvider().chunkExists(x>>4,z>>4);}
    private static GateEditPreflightIssueCode map(SiegeGateOwnershipData.ActiveControllerCheck c){if(c==null||c==SiegeGateOwnershipData.ActiveControllerCheck.ACTIVE)return GateEditPreflightIssueCode.NONE;switch(c){case STALE_REVISION:return GateEditPreflightIssueCode.STALE_REVISION;case MUTATION_IN_PROGRESS:return GateEditPreflightIssueCode.MUTATION_IN_PROGRESS;case QUARANTINED:return GateEditPreflightIssueCode.QUARANTINED;default:return GateEditPreflightIssueCode.OWNERSHIP_MISMATCH;}}
    private static GateEditPreflightIssueCode map(SiegeGateOwnershipData.ExpectedBasePartCheck c){if(c==SiegeGateOwnershipData.ExpectedBasePartCheck.MATCH)return GateEditPreflightIssueCode.NONE;if(c==SiegeGateOwnershipData.ExpectedBasePartCheck.FOREIGN_OWNER)return GateEditPreflightIssueCode.FOREIGN_OWNER;if(c==SiegeGateOwnershipData.ExpectedBasePartCheck.TARGET_CHANGED)return GateEditPreflightIssueCode.REMOVE_TARGET_CHANGED;return GateEditPreflightIssueCode.OWNERSHIP_MISMATCH;}
    private static final class Conversion{final List<GatePartData> parts,originalParts;Conversion(List<GatePartData> p,List<GatePartData> o){parts=p;originalParts=o;}}
    private static final class Delta { final Set<GateEditCoordinate> adds=new HashSet<GateEditCoordinate>(),removes=new HashSet<GateEditCoordinate>(); int changeCount; boolean hasChanges; static Delta between(GateEditOriginalSnapshot o,GateEditDraft d){Delta result=new Delta();for(GateEditOriginalPart part:o.getParts()){GateEditCoordinate k=new GateEditCoordinate(part.getRelativeX(),part.getRelativeY(),part.getRelativeZ());GateEditDraftPart p=d.getPart(k);if(p==null){result.removes.add(k);++result.changeCount;}else if(p.getLeaf()!=part.getLeaf())++result.changeCount;}for(GateEditDraftPart p:d.getParts()){GateEditCoordinate k=new GateEditCoordinate(p.getRelativeX(),p.getRelativeY(),p.getRelativeZ());if(o.findPart(k.getX(),k.getY(),k.getZ())==null){result.adds.add(k);++result.changeCount;}}if(!same(d.getLeftHinge(),o.getLeftHinge()))++result.changeCount;if(!same(d.getRightHinge(),o.getRightHinge()))++result.changeCount;if(d.getOpeningDirection()!=o.getOpeningDirection())++result.changeCount;if(d.isBorderTextureEnabled()!=o.isBorderTextureEnabled())++result.changeCount;result.hasChanges=result.changeCount>0;return result;}}
    private static final class Issues {final EnumMap<GateEditPreflightIssueCode,Integer> values=new EnumMap<GateEditPreflightIssueCode,Integer>(GateEditPreflightIssueCode.class);void add(GateEditPreflightIssueCode code){if(code==null||code==GateEditPreflightIssueCode.NONE)return;Integer current=values.get(code);values.put(code,Integer.valueOf(Math.min(MAX_ISSUE_COUNT,(current==null?0:current.intValue())+1)));}boolean hasAny(){return !values.isEmpty();}GateEditPreflightIssueCode first(){for(GateEditPreflightIssueCode c:GateEditPreflightIssueCode.values())if(values.containsKey(c))return c;return GateEditPreflightIssueCode.NONE;}List<GateEditPreflightResult.IssueCount> snapshot(){List<GateEditPreflightResult.IssueCount> out=new ArrayList<GateEditPreflightResult.IssueCount>();for(GateEditPreflightIssueCode c:GateEditPreflightIssueCode.values())if(values.containsKey(c)&&out.size()<GateEditPreflightResult.MAX_ISSUES)out.add(new GateEditPreflightResult.IssueCount(c,values.get(c).intValue()));return out;}}
}
