package kome.common.siege;

/** Typed Connection endpoint: a Normal Segment or the one conceptual Exterior. */
public final class KOMESiegeAreaRef implements Comparable<KOMESiegeAreaRef> {
    public enum Type { EXTERIOR, NORMAL }
    private static final KOMESiegeAreaRef EXTERIOR_REF=new KOMESiegeAreaRef(Type.EXTERIOR,"");
    private final Type type;
    private final String normalSegmentId;
    private KOMESiegeAreaRef(Type type,String id){this.type=type;normalSegmentId=id;}
    public static KOMESiegeAreaRef exterior(){return EXTERIOR_REF;}
    public static KOMESiegeAreaRef normal(String segmentId){return new KOMESiegeAreaRef(Type.NORMAL,KOMESiegeIds.id(segmentId));}
    public Type getType(){return type;}
    public boolean isExterior(){return type==Type.EXTERIOR;}
    public boolean isNormal(){return type==Type.NORMAL;}
    public String getNormalSegmentId(){return normalSegmentId;}
    @Override public int compareTo(KOMESiegeAreaRef other){
        int value=type.compareTo(other.type);return value==0?normalSegmentId.compareTo(other.normalSegmentId):value;
    }
    @Override public boolean equals(Object o){return o instanceof KOMESiegeAreaRef&&type==((KOMESiegeAreaRef)o).type
        &&normalSegmentId.equals(((KOMESiegeAreaRef)o).normalSegmentId);}
    @Override public int hashCode(){return 31*type.hashCode()+normalSegmentId.hashCode();}
    @Override public String toString(){return isExterior()?"EXTERIOR":"NORMAL("+normalSegmentId+")";}
}
