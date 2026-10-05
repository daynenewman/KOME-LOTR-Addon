package kome.common.siege;

import java.util.Optional;

/** Immutable undirected topology edge through exactly one Transition Zone. */
public final class KOMESiegeConnection {
    private final String id,transitionZoneId;
    private final KOMESiegeAreaRef endpointA,endpointB;
    private final KOMEDefensiveGateRef gateRef;
    public KOMESiegeConnection(String id,KOMESiegeAreaRef first,KOMESiegeAreaRef second,
            String transitionZoneId,KOMEDefensiveGateRef gateRef){
        if(first==null||second==null)throw new IllegalArgumentException("Connection endpoints are required.");
        this.id=KOMESiegeIds.id(id);this.transitionZoneId=KOMESiegeIds.id(transitionZoneId);
        if(first.compareTo(second)<=0){endpointA=first;endpointB=second;}else{endpointA=second;endpointB=first;}
        this.gateRef=gateRef;
    }
    public static KOMESiegeConnection gateLess(String id,KOMESiegeAreaRef a,KOMESiegeAreaRef b,String transitionId){
        return new KOMESiegeConnection(id,a,b,transitionId,null);
    }
    public static KOMESiegeConnection gated(String id,KOMESiegeAreaRef a,KOMESiegeAreaRef b,String transitionId,KOMEDefensiveGateRef gate){
        if(gate==null)throw new IllegalArgumentException("A gated Connection requires a logical GateRef.");
        return new KOMESiegeConnection(id,a,b,transitionId,gate);
    }
    public String getId(){return id;}
    public KOMESiegeAreaRef getEndpointA(){return endpointA;}
    public KOMESiegeAreaRef getEndpointB(){return endpointB;}
    public String getTransitionZoneId(){return transitionZoneId;}
    public boolean isGated(){return gateRef!=null;}
    public Optional<KOMEDefensiveGateRef> getGateRef(){return Optional.ofNullable(gateRef);}
    public boolean connects(KOMESiegeAreaRef endpoint){return endpointA.equals(endpoint)||endpointB.equals(endpoint);}
    @Override public boolean equals(Object o){
        if(!(o instanceof KOMESiegeConnection))return false;KOMESiegeConnection c=(KOMESiegeConnection)o;
        return id.equals(c.id)&&endpointA.equals(c.endpointA)&&endpointB.equals(c.endpointB)
            &&transitionZoneId.equals(c.transitionZoneId)&&(gateRef==null?c.gateRef==null:gateRef.equals(c.gateRef));
    }
    @Override public int hashCode(){int result=31*(31*(31*id.hashCode()+endpointA.hashCode())+endpointB.hashCode())+transitionZoneId.hashCode();return 31*result+(gateRef==null?0:gateRef.hashCode());}
}
