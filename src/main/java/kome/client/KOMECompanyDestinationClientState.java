package kome.client;

import java.util.UUID;
import kome.common.data.KOMECompanyDestinationOptions;
import kome.common.network.KOMEPacketCompanyDestinationRequest;
import kome.common.network.KOMEPacketCompanyMovementRequest;
import kome.common.data.KOMECompanyMovementResult;

/** Nonvisual, one-shot interaction state. No polling or eligibility calculation; execution remains server-only. */
public final class KOMECompanyDestinationClientState {
    private Object connection;
    private UUID session=UUID.randomUUID();
    private long sequence;
    private String destination="";
    private KOMECompanyDestinationOptions latest;
    private boolean loading;
    private long submissionSequence;
    private KOMEPacketCompanyMovementRequest submitting;
    /** Connection callbacks may run off-thread; publication and requests run on the client thread. */
    public synchronized void start(Object connection) {if(this.connection!=connection)submissionSequence=0;this.connection=connection;close();}
    /** Close/reopen invalidates all outstanding correlation even on the same destination. */
    public synchronized void close() {
        session=UUID.randomUUID();sequence=0;destination="";latest=null;loading=false;submitting=null;
    }
    public synchronized KOMEPacketCompanyDestinationRequest request(String tile,int limit,String after,String catalogue) {
        if(connection==null||submitting!=null)throw new IllegalStateException("No destination-query connection or a movement request is pending");
        KOMEPacketCompanyDestinationRequest request=new KOMEPacketCompanyDestinationRequest(session,sequence+1,tile,limit,after,catalogue);
        sequence=request.sequence;destination=tile;latest=null;loading=true;return request;
    }
    public synchronized boolean accept(KOMECompanyDestinationOptions response,Object sourceConnection) {
        if(connection==null||sourceConnection!=connection||!loading||!session.equals(response.session)
                ||sequence!=response.sequence||!destination.equals(response.destination))return false;
        latest=response;loading=false;return true;
    }
    /** Exact selected row from the accepted page; fingerprints are evidence, never client authority. */
    public synchronized KOMEPacketCompanyMovementRequest submit(KOMECompanyDestinationOptions.Row row) {
        if(connection==null||loading||latest==null||submitting!=null||!latest.rows.contains(row))
            throw new IllegalStateException("No current movement preview");
        submitting=new KOMEPacketCompanyMovementRequest(session,submissionSequence+1,latest.sequence,
            row.companyId,row.destinationTile,row.legacyFingerprint,row.confirmationFingerprint);
        submissionSequence=submitting.sequence;return submitting;
    }
    public synchronized boolean acceptMovement(KOMECompanyMovementResult result,Object sourceConnection) {
        if(connection==null||sourceConnection!=connection||submitting==null||!session.equals(result.session)
                ||submitting.sequence!=result.sequence||submitting.previewSequence!=result.previewSequence
                ||!submitting.companyId.equals(result.companyId)||!submitting.destination.equals(result.destination))return false;
        submitting=null;latest=null;loading=false;return true;
    }
    public synchronized UUID sessionId() {return session;}
    public synchronized boolean isSession(UUID expected) {return session.equals(expected);}
    public synchronized boolean isConnection(Object expected) {return connection!=null&&connection==expected;}
    public synchronized boolean isConnected() {return connection!=null;}
    public synchronized KOMECompanyDestinationOptions latest() {return latest;}
    public synchronized boolean isLoading() {return loading;}
}
