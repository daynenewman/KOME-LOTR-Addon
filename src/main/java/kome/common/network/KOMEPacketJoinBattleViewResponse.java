package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAddon;
import kome.common.data.KOMEConflictRecord;
import kome.common.data.KOMEJoinBattleActionTokenService;
import kome.common.data.KOMEJoinBattleService;
import kome.common.data.KOMEJoinBattleText;
import kome.common.data.KOMEJoinBattleDeploymentReceipt;
import kome.common.data.KOMEJoinBattleEgressService;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEFormalRetreatService;

/** Immutable-on-publication client projection; contains no coordinates or entity identities. */
public final class KOMEPacketJoinBattleViewResponse implements IMessage {
    public static final class CompanyRow {
        public final String companyId, displayName, factionId;
        public final int campaignCombatMembers;
        public final boolean selectable;
        CompanyRow(String id, String name, String faction, int combat, boolean selectable) {
            this.companyId=id; this.displayName=name; this.factionId=faction;
            this.campaignCombatMembers=combat; this.selectable=selectable;
        }
    }
    public String tileId="", conflictId="", playerFactionId="", actionToken="";
    public long conflictRevision;
    public KOMEConflictRecord.State conflictState;
    public KOMEJoinBattleService.Reason reason=KOMEJoinBattleService.Reason.INVALID_REQUEST;
    public String reasonText=KOMEJoinBattleText.forReason(reason);
    public final List<CompanyRow> companies=new ArrayList<CompanyRow>();
    public String currentDeploymentTile="", currentDeploymentConflict="", currentDeploymentState="";
    public boolean formalRetreatAvailable;
    public String formalRetreatReason="";
    public final List<String> formalRetreatCompanyIds=new ArrayList<String>();

    public KOMEPacketJoinBattleViewResponse() { }
    public boolean isAllowed() { return reason == KOMEJoinBattleService.Reason.ALLOWED; }
    public List<CompanyRow> companyRows() { return Collections.unmodifiableList(companies); }

    /** Tokenless construction is valid only for a blocked/read-only projection. */
    public static KOMEPacketJoinBattleViewResponse from(KOMEJoinBattleService.Projection projection) {
        if (projection != null && projection.isAllowed())
            throw new IllegalArgumentException("Allowed Join Battle view requires a server action token.");
        return from(projection, "");
    }

    /** The sole production factory for a fresh player-scoped view. */
    public static KOMEPacketJoinBattleViewResponse forPlayer(
            KOMEJoinBattleService.Projection projection, UUID playerId) {
        return from(projection, KOMEJoinBattleActionTokenService.INSTANCE.issue(playerId, projection));
    }

    public static KOMEPacketJoinBattleViewResponse forPlayer(
            KOMEJoinBattleService.Projection projection, UUID playerId, KOMEWorldData data) {
        KOMEPacketJoinBattleViewResponse result = forPlayer(projection, playerId);
        KOMEFormalRetreatService.Inspection retreat =
            KOMEFormalRetreatService.INSTANCE.inspectConflict(data,playerId,projection.conflictId);
        result.formalRetreatAvailable=retreat.canRetreat;
        result.formalRetreatReason=retreat.reason;
        result.formalRetreatCompanyIds.addAll(retreat.companyIds);
        KOMEJoinBattleDeploymentReceipt open=
            KOMEJoinBattleEgressService.INSTANCE.findOpen(data,playerId);
        if (open != null) {
            result.currentDeploymentTile=open.getTileId();
            result.currentDeploymentConflict=open.getConflictId();
            if(open.getState()==KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY){
                result.currentDeploymentState="JOINING";
                result.reasonText="Joining "+result.currentDeploymentTile+" ("
                    +result.currentDeploymentConflict+"). The server is safely completing your deployment.";
            }else if(open.getState()==KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS){
                result.currentDeploymentState="RETURNING";
                result.reasonText="Returning from "+result.currentDeploymentTile+" ("
                    +result.currentDeploymentConflict+"). The server is safely completing your return.";
            }else{
                result.currentDeploymentState="IN_BATTLE";
                result.reasonText="In battle: "+result.currentDeploymentTile+" ("
                    +result.currentDeploymentConflict+"). Formally retreat before joining another battle.";
            }
        }
        return result;
    }

    /** Used for a receipt-backed retry token after entry has already been accepted. */
    public static KOMEPacketJoinBattleViewResponse from(KOMEJoinBattleService.Projection projection,
            String actionToken) {
        if (projection == null) throw new IllegalArgumentException("Join Battle projection required.");
        if (projection.isAllowed() && !KOMEJoinBattleActionTokenService.isUsableToken(actionToken))
            throw new IllegalArgumentException("Allowed Join Battle view requires a valid action token.");
        KOMEPacketJoinBattleViewResponse result=new KOMEPacketJoinBattleViewResponse();
        result.tileId=projection.tileId; result.conflictId=projection.conflictId;
        result.conflictRevision=projection.conflictRevision; result.conflictState=projection.conflictState;
        result.playerFactionId=projection.playerFactionId; result.reason=projection.reason;
        result.actionToken=projection.isAllowed()?actionToken:"";
        result.reasonText=KOMEJoinBattleText.forReason(projection.reason);
        for (KOMEJoinBattleService.EligibleCompany company : projection.eligibleCompanies)
            result.companies.add(new CompanyRow(company.companyId,company.displayName,company.factionId,
                company.campaignCombatMemberCount,true));
        return result;
    }

    @Override public void fromBytes(ByteBuf buf) {
        tileId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
        conflictId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
        conflictRevision=buf.readLong(); if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision");
        conflictState=KOMEJoinBattleWire.state(KOMEJoinBattleWire.text(buf,32,"state"));
        playerFactionId=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_FACTION_LENGTH,"faction");
        reason=KOMEJoinBattleWire.reason(KOMEJoinBattleWire.text(buf,64,"reason"));
        reasonText=KOMEJoinBattleWire.text(buf,256,"reason text");
        actionToken=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_ACTION_TOKEN_LENGTH,"action token");
        companies.clear(); int count=buf.readInt();
        if(count<0||count>KOMEJoinBattleWire.MAX_COMPANIES)throw new IllegalArgumentException("Invalid Join Battle company count");
        for(int i=0;i<count;i++) companies.add(new CompanyRow(
            KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company"),
            KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_NAME_LENGTH,"company name"),
            KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_FACTION_LENGTH,"company faction"),
            buf.readInt(),buf.readBoolean()));
        currentDeploymentTile=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_TILE_LENGTH,"deployment tile");
        currentDeploymentConflict=KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"deployment conflict");
        currentDeploymentState=KOMEJoinBattleWire.text(buf,32,"deployment state");
        formalRetreatAvailable=buf.readBoolean();
        formalRetreatReason=KOMEJoinBattleWire.text(buf,256,"retreat reason");
        formalRetreatCompanyIds.clear();int retreatCount=buf.readInt();
        if(retreatCount<0||retreatCount>KOMEJoinBattleWire.MAX_COMPANIES)throw new IllegalArgumentException("Invalid retreat company count");
        for(int i=0;i<retreatCount;i++)formalRetreatCompanyIds.add(
            KOMEJoinBattleWire.text(buf,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"retreat company"));
        KOMEPopulationWire.requireFullyRead(buf);
    }
    @Override public void toBytes(ByteBuf buf) {
        KOMEPopulationWire.writePacket(buf,out->{
            KOMEJoinBattleWire.write(out,tileId,KOMEJoinBattleWire.MAX_TILE_LENGTH,"tile");
            KOMEJoinBattleWire.write(out,conflictId,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"conflict");
            if(conflictRevision<0)throw new IllegalArgumentException("Invalid Join Battle revision"); out.writeLong(conflictRevision);
            KOMEJoinBattleWire.write(out,conflictState==null?"":conflictState.name(),32,"state");
            KOMEJoinBattleWire.write(out,playerFactionId,KOMEJoinBattleWire.MAX_FACTION_LENGTH,"faction");
            KOMEJoinBattleWire.write(out,reason.name(),64,"reason");
            KOMEJoinBattleWire.write(out,reasonText,256,"reason text");
            KOMEJoinBattleWire.write(out,actionToken,KOMEJoinBattleWire.MAX_ACTION_TOKEN_LENGTH,"action token");
            if(companies.size()>KOMEJoinBattleWire.MAX_COMPANIES)throw new IllegalArgumentException("Too many Join Battle companies");
            out.writeInt(companies.size()); for(CompanyRow row:companies){
                KOMEJoinBattleWire.write(out,row.companyId,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"company");
                KOMEJoinBattleWire.write(out,row.displayName,KOMEJoinBattleWire.MAX_NAME_LENGTH,"company name");
                KOMEJoinBattleWire.write(out,row.factionId,KOMEJoinBattleWire.MAX_FACTION_LENGTH,"company faction");
                if(row.campaignCombatMembers<0)throw new IllegalArgumentException("Invalid Join Battle member count");
                out.writeInt(row.campaignCombatMembers);out.writeBoolean(row.selectable);
            }
            KOMEJoinBattleWire.write(out,currentDeploymentTile,KOMEJoinBattleWire.MAX_TILE_LENGTH,"deployment tile");
            KOMEJoinBattleWire.write(out,currentDeploymentConflict,KOMEJoinBattleWire.MAX_CONFLICT_LENGTH,"deployment conflict");
            KOMEJoinBattleWire.write(out,currentDeploymentState,32,"deployment state");
            out.writeBoolean(formalRetreatAvailable);
            KOMEJoinBattleWire.write(out,formalRetreatReason,256,"retreat reason");
            if(formalRetreatCompanyIds.size()>KOMEJoinBattleWire.MAX_COMPANIES)throw new IllegalArgumentException("Too many retreat companies");
            out.writeInt(formalRetreatCompanyIds.size());
            for(String companyId:formalRetreatCompanyIds)
                KOMEJoinBattleWire.write(out,companyId,KOMEJoinBattleWire.MAX_COMPANY_LENGTH,"retreat company");
        });
    }
    public static final class Handler implements IMessageHandler<KOMEPacketJoinBattleViewResponse,IMessage>{
        @Override public IMessage onMessage(KOMEPacketJoinBattleViewResponse message,MessageContext context){
            final KOMEPacketJoinBattleViewResponse copy=KOMEPopulationWire.copyForPublication(message,KOMEPacketJoinBattleViewResponse::new);
            KOMEAddon.proxy.enqueueClientTask(()->KOMEAddon.proxy.displayJoinBattleGui(copy)); return null;
        }
    }
}
