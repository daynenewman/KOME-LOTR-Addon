package kome.client;

import java.util.Objects;

/** Two deliberate clicks on unchanged evidence; no OS double-click timing or server action. */
final class KOMEJoinConfirmationLatch {
    static final long TIMEOUT_MILLIS=5000L;
    private Object key;
    private long until;
    boolean armed(Object current,long now) {
        if(key==null||!Objects.equals(key,current)||now>=until){clear();return false;}
        return true;
    }
    boolean click(Object current,long now) {
        if(armed(current,now)){clear();return true;}
        key=current;until=now+TIMEOUT_MILLIS;return false;
    }
    void clear(){key=null;until=0;}
}
