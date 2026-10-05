package kome.common.tactical.edit;

import com.enovak.lotrmoremobs.siege.access.GateAccess;
import net.minecraft.entity.player.EntityPlayerMP;

/** Same explicit level-2 operator OR Creative policy as gate administration, with no gate/config prerequisites. */
public final class KOMETacticalEditAccess {
    private KOMETacticalEditAccess() { }
    public static boolean isAuthorized(EntityPlayerMP player) { return GateAccess.isAdministrativePlayer(player); }
}
