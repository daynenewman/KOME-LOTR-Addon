package com.enovak.lotrmoremobs.siege.client;

import com.enovak.lotrmoremobs.siege.edit.GateEditSelectionMode;
import com.enovak.lotrmoremobs.siege.edit.GateEditStatus;
import com.enovak.lotrmoremobs.siege.edit.GateEditSessionManager;
import com.enovak.lotrmoremobs.siege.gate.*;
import com.enovak.lotrmoremobs.siege.network.GateEditDraftSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditSessionStatusPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditPreflightSnapshotPacket;
import com.enovak.lotrmoremobs.siege.network.GateEditCommitResultPacket;
import java.util.*;

/** Client input/display mirror only; server owns the actual transient draft. */
public final class GateEditClientContext {
    public static final class DraftPart { public final int relativeX,relativeY,relativeZ; public final GateLeaf leaf; public final GateEditDraftSnapshotPacket.VisualKind kind; DraftPart(GateEditDraftSnapshotPacket.Part p){relativeX=p.x;relativeY=p.y;relativeZ=p.z;leaf=p.leaf;kind=p.kind;} }
    private static UUID token,gateUuid; private static int dimension,x,y,z,revision; private static long sequence=-1L; private static long generation,preflightGeneration=-1L; private static GateEditStatus lastStatus; private static GateEditSelectionMode selectionMode=GateEditSelectionMode.NONE; private static GateOrientation orientation; private static GateOpeningDirection direction; private static boolean borderTextureEnabled=true; private static GateHinge leftHinge,rightHinge; private static List<DraftPart> parts=Collections.emptyList(); private static GateEditPreflightSnapshotPacket preflight; private static boolean commitRequestPending;
    private GateEditClientContext(){}
    public static void apply(GateEditSessionStatusPacket p) {
        lastStatus = p.getStatus();

        if (p.getStatus() == GateEditStatus.OPENED) {
            /*
             * CREATE_NEW and EDIT_EXISTING must never render at the same time.
             * A stale creation client mirror can otherwise leave the old
             * GL_LINES selection overlay visible over the finalized gate.
             */
            ClientGateCreationState.clear();

            token = p.getToken();
            gateUuid = p.getGateUuid();
            dimension = p.getDimension();
            x = p.getX();
            y = p.getY();
            z = p.getZ();
            revision = p.getRevision();
            sequence = -1L;
            commitRequestPending = false;

            ++generation;
        } else if (p.getStatus() == GateEditStatus.CANCELLED
                || p.getStatus() == GateEditStatus.SESSION_EXPIRED) {
            clear();
        }
    }
    public static void apply(GateEditDraftSnapshotPacket p){if(!isActive()||!token.equals(p.getToken())||!gateUuid.equals(p.getGateUuid())||dimension!=p.getDimension()||x!=p.getX()||y!=p.getY()||z!=p.getZ()||revision!=p.getRevision()||p.getSequence()<sequence)return;List<DraftPart> copy=new ArrayList<DraftPart>();for(GateEditDraftSnapshotPacket.Part part:p.getParts())copy.add(new DraftPart(part));parts=Collections.unmodifiableList(copy);if(p.getSequence()!=sequence){preflight=null;preflightGeneration=-1L;}sequence=p.getSequence();orientation=p.getOrientation();direction=p.getDirection();borderTextureEnabled=p.isBorderTextureEnabled();leftHinge=p.getLeftHinge();rightHinge=p.getRightHinge();}
    public static void apply(GateEditPreflightSnapshotPacket p){if(!p.isValid()||!isActive()||!token.equals(p.getToken())||!gateUuid.equals(p.getGateUuid())||dimension!=p.getDimension()||x!=p.getX()||y!=p.getY()||z!=p.getZ()||revision!=p.getRevision()||p.getDraftSequence()!=sequence||p.getGeneration()<preflightGeneration)return;preflight=p;preflightGeneration=p.getGeneration();}
    public static boolean isActive(){return token!=null;} public static UUID getToken(){return token;} public static UUID getGateUuid(){return gateUuid;} public static int getDimension(){return dimension;} public static int getControllerX(){return x;} public static int getControllerY(){return y;} public static int getControllerZ(){return z;} public static int getRevision(){return revision;} public static long getSequence(){return sequence;} public static long getGeneration(){return generation;} public static GateEditStatus getLastStatus(){return lastStatus;} public static GateEditSelectionMode getSelectionMode(){return selectionMode;} public static void setSelectionMode(GateEditSelectionMode mode){selectionMode=isActive()&&mode!=null?mode:GateEditSelectionMode.NONE;} public static boolean isWorldSelectionActive(){return isActive()&&selectionMode!=GateEditSelectionMode.NONE;} public static GateOpeningDirection getDirection(){return direction;} public static boolean isBorderTextureEnabled(){return borderTextureEnabled;} public static GateHinge getLeftHinge(){return leftHinge;} public static GateHinge getRightHinge(){return rightHinge;} public static List<DraftPart> getParts(){return parts;} public static GateEditPreflightSnapshotPacket getPreflight(){return preflight;} public static boolean isCommitRequestPending(){return commitRequestPending;}
    public static boolean beginCommitRequest(){if(!isCommitReady()||commitRequestPending)return false;commitRequestPending=true;return true;}
    public static void apply(GateEditCommitResultPacket packet) {
        commitRequestPending = false;

        if (packet == null || !packet.isValid()) {
            preflight = null;
            preflightGeneration = -1L;
            return;
        }

        GateEditSessionManager.EditCommitAdmissionResult.State state =
                packet.getState();

        if (state
                == GateEditSessionManager.EditCommitAdmissionResult.State.PREPARED
                || state
                == GateEditSessionManager.EditCommitAdmissionResult.State.INVALID_SESSION) {
            clear();
            return;
        }

        /*
         * A rejected commit means the previous readiness receipt is no longer
         * authoritative. Keep the transient session, but require the fresh
         * server-pushed preflight before Commit can become enabled again.
         */
        preflight = null;
        preflightGeneration = -1L;
    }
    public static boolean isCommitReady(){return isActive()&&!commitRequestPending&&preflight!=null&&preflight.isValid()&&token.equals(preflight.getToken())&&gateUuid.equals(preflight.getGateUuid())&&dimension==preflight.getDimension()&&x==preflight.getX()&&y==preflight.getY()&&z==preflight.getZ()&&revision==preflight.getRevision()&&sequence==preflight.getDraftSequence()&&preflight.isCommitReady();}
    public static boolean matchesManagement(int dim,int cx,int cy,int cz){return isActive()&&dimension==dim&&x==cx&&y==cy&&z==cz;}
    public static boolean containsRelative(int rx,int ry,int rz){for(DraftPart p:parts)if(p.relativeX==rx&&p.relativeY==ry&&p.relativeZ==rz)return true;return false;}
    public static void clear(){token=null;gateUuid=null;sequence=-1L;preflightGeneration=-1L;preflight=null;selectionMode=GateEditSelectionMode.NONE;parts=Collections.emptyList();orientation=null;direction=null;borderTextureEnabled=true;leftHinge=null;rightHinge=null;lastStatus=null;commitRequestPending=false;++generation;}
}
