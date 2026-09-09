package com.fuzs.aquaacrobatics.client.render;
import net.minecraft.entity.item.EntityBoat;import org.lwjgl.opengl.GL11;import com.fuzs.aquaacrobatics.entity.IRockableBoat;import com.fuzs.aquaacrobatics.util.math.MathHelperNew;
public final class AquaBoatRenderLogic{private AquaBoatRenderLogic(){}public static void addRockingRotation(EntityBoat boat,float partialTicks){float f=((IRockableBoat)boat).getRockingAngle(partialTicks);if(!MathHelperNew.epsilonEquals(f,0.0F))GL11.glRotatef(f,1,0,1);}}
