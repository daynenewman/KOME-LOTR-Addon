package com.fuzs.aquaacrobatics.entity.player;

/**
 * Minimal common-side bridge for EntityLivingBase's protected jump state.
 * Implemented directly on EntityPlayer by Aqua's ASM transformer.
 */
public interface IAquaJumpingAccess {

    boolean aqua$isJumping();
}
