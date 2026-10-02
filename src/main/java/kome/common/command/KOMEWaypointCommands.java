package kome.common.command;

import java.util.*;
import kome.common.KOMEReflection;
import kome.common.data.*;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Server command authority. Admin permission is checked before any world-state access. */
final class KOMEWaypointCommands {
    private KOMEWaypointCommands() { }
    static final String HELP="/kome waypoint propose <name> | staff: pending [page], list [page], inspect <id>, history <id>, "
        +"approve/reject <proposalId> <version> <reason>, adjust <proposalId> <version> <level> here <name>, "
        +"add <level> <name>, associate <nativeKey> <level> <name>, rename <id> <name>, move <id> here, "
        +"remove <id>, level <id> <level>, migration dryrun <fellowshipUUID> <confirmedDimension> [page], "
        +"migration convert <fellowshipUUID> <confirmedDimension> <token> <owner:ID,...>, migration rollback <owner:ID>";
    static void process(ICommandSender sender,String[] args) {
        if(args.length<2 || "help".equalsIgnoreCase(args[1])) {
            say(sender,sender.canCommandSenderUseCommand(2,"kome")?HELP:"/kome waypoint propose <name>"); return;
        }
        boolean proposal="propose".equalsIgnoreCase(args[1]);
        if(!proposal && !sender.canCommandSenderUseCommand(2,"kome")) throw new WrongUsageException("Waypoint administration requires permission level 2.");
        if(proposal && !(sender instanceof EntityPlayerMP)) throw new WrongUsageException("Only a connected server player can propose a destination.");
        try {
            KOMEWorldData data=KOMEWorldData.get(sender.getEntityWorld());
            KOMEPublicWaypointRegistry registry=data.publicWaypoints;
            String actor=sender instanceof EntityPlayerMP?KOMEReflection.getEntityUUID((EntityPlayerMP)sender).toString():"console";
            long now=System.currentTimeMillis(); String action=args[1].toLowerCase(Locale.ROOT);
            if(proposal && args.length>=3) {
                EntityPlayerMP player=(EntityPlayerMP)sender; int[] pos=position(player);
                KOMEWaypointProposal q=registry.propose(data,KOMEReflection.getEntityUUID(player),player.getCommandSenderName(),
                    tail(args,2),player.dimension,pos[0],pos[1],pos[2],now);
                say(sender,"Submitted "+q.id+" on "+q.tileId+" for review. Pending destinations are not public/travelable."); return;
            }
            if("pending".equals(action) || "list".equals(action)) {
                if(args.length>3) throw new IllegalArgumentException(HELP);
                int page=args.length==3?page(args[2]):0;
                List<String> lines=new ArrayList<String>();
                if("pending".equals(action)) for(KOMEWaypointProposal q:registry.proposals()) {
                    if(q.status==KOMEWaypointProposal.Status.PENDING) lines.add(proposalLine(q));
                } else for(KOMEPublicWaypointRegistry.View view:registry.views(data)) lines.add(recordLine(view));
                paged(sender,lines,page); return;
            }
            if("inspect".equals(action) && args.length==3) {
                UUID id=uuid(args[2]); KOMEWaypointProposal q=registry.proposal(id);
                if(q!=null) {
                    say(sender,proposalLine(q)+" submitted="+q.submitterName+"/"+q.submitter+" at="+q.submittedAt
                        +" reviewer="+q.reviewer+" reviewed="+q.reviewedAt+" reason="+q.reason+" approved="+q.approvedWaypoint);
                    return;
                }
                KOMEPublicWaypoint r=registry.get(id);
                if(r==null) throw new IllegalArgumentException("Unknown proposal/destination");
                KOMEPublicWaypointRegistry.View v=registry.view(data,id);
                say(sender,v==null?"Approval exists but canonical destination is unavailable: "+r.id:recordLine(v)); return;
            }
            if("history".equals(action) && args.length==3) {
                String id=uuid(args[2]).toString(); List<String> lines=new ArrayList<String>();
                for(net.minecraft.nbt.NBTTagCompound row:registry.history()) if(row.getString("Entity").equals(id))
                    lines.add(row.getString("Action")+" actor="+row.getString("Actor")+" at="+row.getLong("At")+" "+row.getString("Reason"));
                for(String line:lines) say(sender,line);
                if(lines.isEmpty()) say(sender,"No review history for this identity."); return;
            }
            if(("approve".equals(action) || "reject".equals(action)) && args.length>=5) {
                UUID id=uuid(args[2]); long version=Long.parseLong(args[3]); String reason=tail(args,4);
                if("approve".equals(action)) say(sender,"Approved "+registry.approveProposal(data,id,version,actor,reason,now).id);
                else { registry.reject(data,id,version,actor,reason,now); say(sender,"Rejected "+id); }
                return;
            }
            if("adjust".equals(action) && args.length>=7 && "here".equalsIgnoreCase(args[5])) {
                EntityPlayerMP player=player(sender); int[] pos=position(player);
                KOMEWaypointProposal q=registry.adjust(data,uuid(args[2]),Long.parseLong(args[3]),tail(args,6),player.dimension,
                    pos[0],pos[1],pos[2],Integer.parseInt(args[4]),actor,"Administrative adjustment at reviewer position",now);
                say(sender,proposalLine(q)); return;
            }
            if("add".equals(action) && args.length>=4) {
                EntityPlayerMP player=player(sender); int[] pos=position(player);
                say(sender,"Approved "+registry.approve(data,tail(args,3),player.dimension,pos[0],pos[1],pos[2],Integer.parseInt(args[2]),
                    KOMEPublicWaypoint.Source.PUBLIC,"",actor,now,null).id); return;
            }
            if("associate".equals(action) && args.length>=5) {
                LOTRWaypoint nativePoint=LOTRWaypoint.waypointForName(args[2]);
                if(nativePoint==null || !nativePoint.getCodeName().equals(args[2])) throw new IllegalArgumentException("Unknown canonical native waypoint key");
                say(sender,"Associated "+registry.approve(data,tail(args,4),LOTRDimension.MIDDLE_EARTH.dimensionID,nativePoint.getXCoord(),
                    nativePoint.getYCoordSaved(),nativePoint.getZCoord(),Integer.parseInt(args[3]),KOMEPublicWaypoint.Source.NATIVE,
                    args[2],actor,now,null).id); return;
            }
            if("rename".equals(action) && args.length>=4) {
                registry.rename(data,uuid(args[2]),tail(args,3),actor,now); say(sender,"Renamed destination."); return;
            }
            if("move".equals(action) && args.length==4 && "here".equalsIgnoreCase(args[3])) {
                EntityPlayerMP player=player(sender); int[] pos=position(player);
                registry.move(data,uuid(args[2]),player.dimension,pos[0],pos[1],pos[2],actor,now); say(sender,"Moved destination."); return;
            }
            if("remove".equals(action) && args.length==3) {
                registry.remove(data,uuid(args[2]),actor,now); say(sender,"Removed destination; explicit migration cutover remains until rollback."); return;
            }
            if("level".equals(action) && args.length==4) {
                registry.level(data,uuid(args[2]),Integer.parseInt(args[3]),actor,now); say(sender,"Updated independent waypoint level."); return;
            }
            if("migration".equals(action)) {
                if(args.length>=5 && "dryrun".equalsIgnoreCase(args[2]) && args.length<=6) {
                    UUID fs=uuid(args[3]); KOMEWaypointMigration.Report report=KOMEWaypointMigration.dryRun(data,fs,KOMEWaypointMigration.inventory(fs,Integer.parseInt(args[4])));
                    say(sender,"Dry-run token="+report.token+" entries="+report.rows.size()+" confirmedDimension="+args[4]+" (read only; native records do not store dimension)");
                    List<String> lines=new ArrayList<String>();
                    for(KOMEWaypointMigration.Row row:report.rows) lines.add(row.entry.identity+" "+row.entry.name+" @ "
                        +row.entry.dimension+":"+row.entry.x+","+row.entry.y+","+row.entry.z+" tile="+row.tile+" "
                        +(row.eligible()?"ELIGIBLE":row.problem));
                    paged(sender,lines,args.length==6?page(args[5]):0); return;
                }
                if(args.length==7 && "convert".equalsIgnoreCase(args[2])) {
                    UUID fs=uuid(args[3]); List<KOMEWaypointMigration.Entry> fresh=KOMEWaypointMigration.inventory(fs,Integer.parseInt(args[4]));
                    List<KOMEPublicWaypoint> imported=KOMEWaypointMigration.convert(data,fs,fresh,args[5],Arrays.asList(args[6].split(",",-1)),actor,now);
                    say(sender,"Converted "+imported.size()+" destinations; native/fellowship originals preserved."); return;
                }
                if(args.length==4 && "rollback".equalsIgnoreCase(args[2])) {
                    registry.rollbackLegacy(data,args[3],actor,now); say(sender,"Rolled back exact legacy cutover identity."); return;
                }
            }
            throw new IllegalArgumentException(HELP);
        } catch(IllegalArgumentException | IllegalStateException invalid) { throw new WrongUsageException(invalid.getMessage()); }
    }
    private static String proposalLine(KOMEWaypointProposal q) {
        return q.id+" v"+q.version+" "+q.status+" "+q.name+" tile="+q.tileId+" level="+q.level+" @ "
            +q.dimension+":"+q.x+","+q.y+","+q.z;
    }
    private static String recordLine(KOMEPublicWaypointRegistry.View v) {
        KOMEPublicWaypoint r=v.record;
        return r.id+" "+r.name+" tile="+r.tileId+" default="+v.defaultOwner+" current="+v.currentOwner+" level="+r.level
            +" @ "+r.dimension+":"+r.x+","+r.y+","+r.z+" source="+r.source+":"+r.sourceKey;
    }
    private static void paged(ICommandSender sender,List<String> lines,int page) {
        long start=(long)page*20;
        if(start>=lines.size()) { say(sender,"No entries on page "+page+"; total="+lines.size()); return; }
        for(int i=(int)start;i<Math.min(start+20,lines.size());i++) say(sender,lines.get(i));
        say(sender,"Page "+page+"; total="+lines.size()+"; next page="+(page+1));
    }
    private static int page(String value) { int result=Integer.parseInt(value); if(result<0) throw new IllegalArgumentException("Negative page"); return result; }
    private static UUID uuid(String value) {
        UUID id=UUID.fromString(value); if(!id.toString().equals(value)) throw new IllegalArgumentException("Use full canonical UUID"); return id;
    }
    private static EntityPlayerMP player(ICommandSender sender) {
        if(!(sender instanceof EntityPlayerMP)) throw new IllegalArgumentException("This operation requires a server player position");
        return (EntityPlayerMP)sender;
    }
    private static int floor(double value) {
        double result=Math.floor(value);
        if(!Double.isFinite(result) || result<Integer.MIN_VALUE || result>Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid server position");
        return (int)result;
    }
    private static int[] position(EntityPlayerMP player) { return new int[]{floor(player.posX),floor(player.boundingBox.minY),floor(player.posZ)}; }
    private static String tail(String[] args,int start) { return String.join(" ",Arrays.copyOfRange(args,start,args.length)); }
    private static void say(ICommandSender sender,String message) { sender.addChatMessage(new ChatComponentText(message)); }
}
