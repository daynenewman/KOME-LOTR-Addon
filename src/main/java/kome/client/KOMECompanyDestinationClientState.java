package kome.client;

import java.util.UUID;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.network.KOMEPacketCompanyDestinationRequest;

/** Nonvisual, one-shot interaction state. No polling, eligibility calculation, or movement execution. */
public final class KOMECompanyDestinationClientState {
    private Object connection;
    private UUID session=UUID.randomUUID();
    private long sequence;
    private String destination="";
    private KOMECompanyDestinationOptions latest;
    private boolean loading;
    /** Connection callbacks may run off-thread; publication and requests run on the client thread. */
    public synchronized void start(Object connection) {this.connection=connection;close();}
    /** Close/reopen invalidates all outstanding correlation even on the same destination. */
    public synchronized void close() {
        session=UUID.randomUUID();sequence=0;destination="";latest=null;loading=false;
    }
    public synchronized KOMEPacketCompanyDestinationRequest request(String tile,int limit,String after,String catalogue) {
        if(connection==null)throw new IllegalStateException("No destination-query connection");
        KOMEPacketCompanyDestinationRequest request=new KOMEPacketCompanyDestinationRequest(session,sequence+1,tile,limit,after,catalogue);
        sequence=request.sequence;destination=tile;latest=null;loading=true;return request;
    }
    public synchronized boolean accept(KOMECompanyDestinationOptions response,Object sourceConnection) {
        if(connection==null||sourceConnection!=connection||!loading||!session.equals(response.session)
                ||sequence!=response.sequence||!destination.equals(response.destination))return false;
        latest=response;loading=false;return true;
    }
    public synchronized KOMECompanyDestinationOptions latest() {return latest;}
    public synchronized boolean isLoading() {return loading;}
}
