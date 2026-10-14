package kome.common.network;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server-thread connection high-water mark. Closing a UI cannot make a consumed intent executable again. */
final class KOMECompanyMovementReplayGuard {
    private final Map<Object,Long> highest=new IdentityHashMap<Object,Long>();
    boolean acquire(Object connection,long sequence) {
        if(connection==null||sequence<=0)return false;
        Long previous=highest.get(connection);
        if(previous!=null&&sequence<=previous||previous==null&&highest.size()>=1024)return false;
        highest.put(connection,sequence);return true;
    }
    void forget(Object connection) {highest.remove(connection);}
    void clear() {highest.clear();}
}
