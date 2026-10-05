package kome.common.siege;

/** Stable KOM-10 logical identity; deliberately contains no physical gate state. */
public final class KOMEDefensiveGateRef {
    private final String buildId,gateRecordId;
    public KOMEDefensiveGateRef(String buildId,String gateRecordId){
        this.buildId=KOMESiegeIds.id(buildId);this.gateRecordId=KOMESiegeIds.id(gateRecordId);
    }
    public String getBuildId(){return buildId;}
    public String getGateRecordId(){return gateRecordId;}
    @Override public boolean equals(Object o){return o instanceof KOMEDefensiveGateRef
        &&buildId.equals(((KOMEDefensiveGateRef)o).buildId)&&gateRecordId.equals(((KOMEDefensiveGateRef)o).gateRecordId);}
    @Override public int hashCode(){return 31*buildId.hashCode()+gateRecordId.hashCode();}
    @Override public String toString(){return buildId+"/"+gateRecordId;}
}
