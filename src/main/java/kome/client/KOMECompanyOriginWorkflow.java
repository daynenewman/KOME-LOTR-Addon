package kome.client;

import kome.common.data.KOMECompanyDestinationOptions.Row;
import kome.common.data.KOMEConquestTile;

/** Source stays fixed; the shared destination selector owns source-scoped browsing and execution evidence. */
final class KOMECompanyOriginWorkflow {
    enum Stage { CLOSED, PICK, SOURCE, PREVIEW }
    private final KOMECompanyDestinationClientState connection;
    private java.util.UUID session;
    private Stage stage=Stage.CLOSED;
    private String source="",destination="",selectedCompanyId="";
    KOMECompanyOriginWorkflow(KOMECompanyDestinationClientState connection){this.connection=connection;}
    boolean active(){if(stage!=Stage.CLOSED&&(!connection.isConnected()||!connection.isSession(session)))clear();return stage!=Stage.CLOSED;}
    Stage stage(){active();return stage;}
    String source(){return active()?source:"";}
    String destination(){return active()?destination:"";}
    String selectedCompanyId(){return active()?selectedCompanyId:"";}
    void clear(){stage=Stage.CLOSED;session=null;source=destination=selectedCompanyId="";}
    void open(String tile){
        clear();if(!connection.isConnected()||!KOMEConquestTile.isCanonicalTileId(tile))return;
        connection.close();session=connection.sessionId();source=tile;stage=Stage.PICK;
    }
    void adoptSession(){session=connection.sessionId();}
    void chooseDestination(String tile){destination=tile;selectedCompanyId="";stage=Stage.SOURCE;adoptSession();}
    void preview(Row row){if((stage()==Stage.SOURCE||stage==Stage.PREVIEW)&&source.equals(row.currentTile)&&destination.equals(row.destinationTile)){selectedCompanyId=row.companyId;stage=Stage.PREVIEW;}}
    void chooser(){selectedCompanyId="";stage=Stage.SOURCE;adoptSession();}
    void pickAgain(){destination=selectedCompanyId="";stage=Stage.PICK;adoptSession();}
}
