package com.enovak.lotrmoremobs.util;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.DamageSource;

/**
 * One owner-resolution path for player-hired formation damage.
 */
public final class MumakilFormationCreditHelper {
    private MumakilFormationCreditHelper() {
    }

    public static Credit resolve(DamageSource source) {
        if (source == null) {
            return null;
        }

        Entity attacker = source.getEntity();
        if (attacker == null
                && source.getSourceOfDamage() instanceof EntityArrow) {
            attacker =
                    ((EntityArrow)source.getSourceOfDamage()).shootingEntity;
        }

        LOTREntityMumakil mumakil = findParentMumakil(attacker);
        if (mumakil == null
                || mumakil.getFormationOrigin()
                != MumakilFormationOrigin.PLAYER_HIRED) {
            return null;
        }

        if (mumakil.riddenByEntity instanceof LOTREntityNPC) {
            mumakil.capturePlayerHiredFormationOwner(
                    (LOTREntityNPC)mumakil.riddenByEntity
            );
        }
        EntityPlayer owner =
                mumakil.getOnlinePlayerHiredFormationOwner();
        if (owner == null
                || owner.isDead
                || owner.worldObj != mumakil.worldObj) {
            return null;
        }

        return new Credit(mumakil, owner, attacker);
    }

    private static LOTREntityMumakil findParentMumakil(Entity attacker) {
        if (attacker instanceof LOTREntityMumakil) {
            return (LOTREntityMumakil)attacker;
        }

        if (attacker instanceof LOTREntityMumakilHowdahArcher) {
            return ((LOTREntityMumakilHowdahArcher)attacker)
                    .getAttachedMumakilForFormationCredit();
        }

        if (attacker instanceof LOTREntityNPC
                && attacker.ridingEntity instanceof LOTREntityMumakil) {
            LOTREntityMumakil mumakil =
                    (LOTREntityMumakil)attacker.ridingEntity;
            return mumakil.riddenByEntity == attacker ? mumakil : null;
        }

        return null;
    }

    public static final class Credit {
        public final LOTREntityMumakil mumakil;
        public final EntityPlayer owner;
        public final Entity attacker;

        private Credit(
                LOTREntityMumakil mumakil,
                EntityPlayer owner,
                Entity attacker
        ) {
            this.mumakil = mumakil;
            this.owner = owner;
            this.attacker = attacker;
        }
    }
}
