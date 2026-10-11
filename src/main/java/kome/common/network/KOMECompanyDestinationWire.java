package kome.common.network;

import io.netty.buffer.*;
import java.util.*;
import kome.common.data.*;
import kome.common.data.KOMECompanyDestinationOptions.*;
import kome.common.command.KOMECompanyMovementService.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;

/** Strict, atomic bounded codec. No client data in this exchange authorizes movement. */
final class KOMECompanyDestinationWire {
    static final int MAX_BYTES=24*1024;
    private KOMECompanyDestinationWire() { }
    static void size(ByteBuf b) { if(b.readableBytes()>MAX_BYTES) throw new IllegalArgumentException("Oversized destination packet"); }
    static void atomic(ByteBuf target,java.util.function.Consumer<ByteBuf> writer) {
        ByteBuf b=Unpooled.buffer(256,MAX_BYTES);
        try {writer.accept(b);target.writeBytes(b);}
        catch(IndexOutOfBoundsException limit) {throw new PacketLimitExceeded(limit);}
        finally {b.release();}
    }
    static final class PacketLimitExceeded extends IllegalArgumentException {
        PacketLimitExceeded(Throwable cause) {super("Destination packet exceeds byte budget",cause);}
    }
    static String text(ByteBuf b,int max) {
        String s=KOMEPopulationWire.readText(b);check(s,max);return s;
    }
    static void text(ByteBuf b,String s,int max) {check(s,max);KOMEPopulationWire.writeText(b,s);}
    static void check(String s,int max) {
        if(s==null||s.length()>max) throw new IllegalArgumentException("Invalid destination text");
    }
    static void tile(String s,boolean optional) {
        check(s,24);
        if(optional&&s.isEmpty()) return;
        if(!s.equals(KOMEConquestTile.normalizeId(s))||!KOMEConquestTile.isCanonicalTileId(s)) throw new IllegalArgumentException("Invalid destination tile");
    }
    static void id(String s,boolean optional) {
        check(s,64);
        if(!s.matches(optional?"[A-Za-z0-9_.:-]{0,64}":"[A-Za-z0-9_.:-]{1,64}"))
            throw new IllegalArgumentException("Invalid destination identifier");
    }
    static void fingerprint(String s,boolean optional) {
        if(optional&&s.isEmpty())return;
        if(!s.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid destination fingerprint");
    }
    static <T extends Enum<T>> T enumeration(ByteBuf b,Class<T> type) {
        try{return Enum.valueOf(type,text(b,64));}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid destination enum",bad);}
    }
    static <T extends Enum<T>> T optionalEnumeration(ByteBuf b,Class<T> type) {
        String value=text(b,64);
        if(value.isEmpty())return null;
        try{return Enum.valueOf(type,value);}catch(RuntimeException bad){throw new IllegalArgumentException("Invalid destination enum",bad);}
    }
    static void validate(Row r) {
        if(r.populationCenti < -1 || r.populationCenti > (long)MAX_MEMBERS*Integer.MAX_VALUE*100
                || r.unitCount < -1 || r.unitCount > MAX_MEMBERS || (r.populationCenti==-1)!=(r.unitCount==-1)
                || r.etaMillis < -1 || r.etaMillis>=0 && (!r.canStartNow || !r.timingAvailable || r.scheduleMode!=ScheduleMode.DAILY)
                || r.etaMillis==0 && !r.canArriveNow || r.etaMillis>0 && r.canArriveNow)
            throw new IllegalArgumentException("Invalid destination population or ETA");
        id(r.companyId,false);check(r.name,128);tile(r.currentTile,true);tile(r.destinationTile,false);
        if(r.tilesPerDay<1||r.remainingAllowance<0||r.remainingAllowance>r.tilesPerDay)
            throw new IllegalArgumentException("Invalid destination entitlement");
        if(r.evaluationState==EvaluationState.DEFERRED_ROUTE_LIMIT) {
            if(r.status!=null||r.consequence!=null||r.canStartNow||r.canArriveNow||r.legalRoute||r.timingAvailable
                    ||r.distance!=-1||!r.route.isEmpty()||r.futureBoundaries!=0||r.nextBoundaryMillis!=0||r.estimatedCompletionMillis!=0
                    ||!r.denialCode.isEmpty()||!r.denialDetail.isEmpty()||!r.heldConflictId.isEmpty()||r.heldConflictRevision!=-1
                    ||!r.destinationConflictId.isEmpty()||r.destinationConflictRevision!=-1
                    ||!r.legacyFingerprint.isEmpty()||!r.confirmationFingerprint.isEmpty())
                throw new IllegalArgumentException("Deferred evaluation cannot claim movement authority");
            return;
        }
        if(r.status==null||r.consequence==null||r.distance<0||r.distance>MAX_ROUTE_TILES||r.route.size()>MAX_ROUTE_TILES
                ||r.futureBoundaries<0||r.futureBoundaries>MAX_ROUTE_TILES||r.nextBoundaryMillis<0||r.estimatedCompletionMillis<0)
            throw new IllegalArgumentException("Invalid destination movement values");
        for(String t:r.route) tile(t,false);
        boolean start=r.status!=Status.BLOCKED,arrive=r.status==Status.CAN_START_AND_ARRIVE_NOW;
        if(r.canStartNow!=start||r.canArriveNow!=arrive||start==!r.denialCode.isEmpty())
            throw new IllegalArgumentException("Inconsistent destination status");
        if(r.legalRoute&&(r.route.size()<2||r.distance!=r.route.size()-1
                ||!r.currentTile.equals(r.route.get(0))||!r.destinationTile.equals(r.route.get(r.route.size()-1))))
            throw new IllegalArgumentException("Invalid destination route");
        if(start&&!r.legalRoute)throw new IllegalArgumentException("Missing destination route");
        if(!r.timingAvailable&&(r.futureBoundaries!=0||r.nextBoundaryMillis!=0||r.estimatedCompletionMillis!=0)
                ||r.timingAvailable&&r.scheduleMode!=ScheduleMode.DAILY)
            throw new IllegalArgumentException("Invalid destination timing availability");
        check(r.denialCode,64);id(r.denialCode,true);check(r.denialDetail,512);
        id(r.heldConflictId,true);id(r.destinationConflictId,true);
        if((r.heldConflictId.isEmpty()?r.heldConflictRevision!=-1:r.heldConflictRevision<0)
                ||(r.destinationConflictId.isEmpty()?r.destinationConflictRevision!=-1:r.destinationConflictRevision<0))
            throw new IllegalArgumentException("Invalid destination conflict revision");
        fingerprint(r.legacyFingerprint,!start);fingerprint(r.confirmationFingerprint,!start);
        if(!start&&(!r.legacyFingerprint.isEmpty()||!r.confirmationFingerprint.isEmpty()))
            throw new IllegalArgumentException("Blocked destination confirmation");
    }
    static void row(ByteBuf b,Row r) {
        validate(r);
        text(b,r.companyId,64);text(b,r.name,128);text(b,r.currentTile,24);text(b,r.destinationTile,24);
        text(b,r.evaluationState.name(),64);text(b,r.entitlement.name(),64);b.writeInt(r.tilesPerDay);b.writeInt(r.remainingAllowance);b.writeInt(r.distance);
        b.writeInt(r.route.size());for(String t:r.route)text(b,t,24);
        text(b,r.status==null?"":r.status.name(),64);text(b,r.scheduleMode.name(),64);
        b.writeBoolean(r.canStartNow);b.writeBoolean(r.canArriveNow);b.writeBoolean(r.legalRoute);b.writeBoolean(r.timingAvailable);
        b.writeInt(r.futureBoundaries);b.writeLong(r.nextBoundaryMillis);b.writeLong(r.estimatedCompletionMillis);
        text(b,r.denialCode,64);text(b,r.denialDetail,512);text(b,r.heldConflictId,64);b.writeLong(r.heldConflictRevision);
        text(b,r.consequence==null?"":r.consequence.name(),64);text(b,r.destinationConflictId,64);b.writeLong(r.destinationConflictRevision);
        text(b,r.legacyFingerprint,64);text(b,r.confirmationFingerprint,64);
        b.writeLong(r.populationCenti);b.writeInt(r.unitCount);b.writeLong(r.etaMillis);
    }
    static Row row(ByteBuf b) {
        String id=text(b,64),name=text(b,128),origin=text(b,24),dest=text(b,24);
        EvaluationState state=enumeration(b,EvaluationState.class);
        Entitlement entitlement=enumeration(b,Entitlement.class);int tiles=b.readInt(),allowance=b.readInt(),distance=b.readInt();
        int count=b.readInt();if(count<0||count>MAX_ROUTE_TILES)throw new IllegalArgumentException("Oversized destination route");
        List<String> route=new ArrayList<String>();for(int i=0;i<count;i++)route.add(text(b,24));
        Row r=new Row(state,id,name,origin,dest,entitlement,tiles,allowance,distance,route,
            optionalEnumeration(b,Status.class),enumeration(b,ScheduleMode.class),b.readBoolean(),b.readBoolean(),b.readBoolean(),b.readBoolean(),
            b.readInt(),b.readLong(),b.readLong(),text(b,64),text(b,512),text(b,64),b.readLong(),
            optionalEnumeration(b,Consequence.class),text(b,64),b.readLong(),text(b,64),text(b,64));
        r=new Row(r,b.readLong(),b.readInt(),b.readLong());
        validate(r);return r;
    }
}
