// package com.fuzs.aquaacrobatics.block;
//
// import java.util.List;
// import java.util.Random;
//
// import com.fuzs.aquaacrobatics.client.particle.ParticleBubbleColumnUp;
// import com.fuzs.aquaacrobatics.client.particle.ParticleCurrentDown;
// import com.fuzs.aquaacrobatics.config.ConfigHandler;
// import com.fuzs.aquaacrobatics.entity.IBubbleColumnInteractable;
// import com.fuzs.aquaacrobatics.proxy.CommonProxy;
// import com.fuzs.aquaacrobatics.util.BlockPos;
// import net.minecraft.block.Block;
// import net.minecraft.block.BlockLiquid;
// import net.minecraft.block.BlockStaticLiquid;
// import net.minecraft.block.material.Material;
// import net.minecraft.client.Minecraft;
// import net.minecraft.entity.Entity;
// import net.minecraft.entity.EntityBodyHelper;
// import net.minecraft.entity.item.EntityBoat;
// import net.minecraft.init.Blocks;
// import net.minecraft.util.AxisAlignedBB;
// import net.minecraft.world.IBlockAccess;
// import net.minecraft.world.World;
// import net.minecraftforge.common.util.Constants;
// import net.minecraftforge.fluids.BlockFluidBase;
//
// public class BlockBubbleColumn extends BlockStaticLiquid {
// public static final PropertyBool DRAG = PropertyBool.create("drag"); /* true: upwards, false: downwards */
//
// public BlockBubbleColumn() {
// super(Material.water);
//// this.setRegistryName("bubble_column");
// this.setBlockName("tile.aquaacrobatics.bubble.column.name");
//// this.setDefaultState(this.blockState.getBaseState().withProperty(LEVEL, 0).withProperty(DRAG, false));
// }
//
//// @Override
//// protected BlockStateContainer createBlockState()
//// {
//// BlockStateContainer.Builder builder = new BlockStateContainer.Builder(this).add(LEVEL, DRAG);
//// if(Loader.isModLoaded("fluidlogged_api"))
//// builder = builder.add(BlockFluidBase.FLUID_RENDER_PROPS.toArray(new IUnlistedProperty<?>[0]));
//// return builder.build();
//// }
//
//
// @Override
// public void addCollisionBoxesToList(World worldIn, int x, int y, int z, AxisAlignedBB mask, List<AxisAlignedBB> list,
// Entity collider) {
// BlockPos pos = new BlockPos(x, y, z).up();
// Block block = worldIn.getBlock(pos.getX(), pos.getY(), pos.getZ());
// IBubbleColumnInteractable bubbleEntity = (IBubbleColumnInteractable) entityIn;
// boolean downwards = !state.getValue(DRAG);
// if (block == Blocks.air) {
// bubbleEntity.onEnterBubbleColumnWithAirAbove(downwards);
// } else {
// bubbleEntity.onEnterBubbleColumn(downwards);
// }
// }
//
// public static void placeBubbleColumn(World world, BlockPos pos, boolean isUpwards) {
// if (!ConfigHandler.MiscellaneousConfig.bubbleColumns)
// return;
// if (canHoldBubbleColumn(world, pos)) {
// world.setBlockState(pos, CommonProxy.BUBBLE_COLUMN.getDefaultState().withProperty(DRAG, isUpwards),
// Constants.BlockFlags.SEND_TO_CLIENTS);
// }
// }
//
// public static boolean canHoldBubbleColumn(World world, BlockPos pos) {
// if (world.provider.doesWaterVaporize())
// return false;
// IBlockState self = world.getBlockState(pos);
// if (self.getMaterial() != Material.WATER)
// return false;
// if (!(self.getBlock() instanceof BlockLiquid))
// return false;
// if (self.getValue(LEVEL) != 0)
// return false;
// return true;
// }
//
// @SideOnly(Side.CLIENT)
// public void randomDisplayTick(IBlockState stateIn, World worldIn, BlockPos pos, Random rand) {
// double d0 = pos.getX();
// double d1 = pos.getY();
// double d2 = pos.getZ();
// Minecraft mc = Minecraft.getMinecraft();
// if (!stateIn.getValue(DRAG)) {
// mc.effectRenderer.addEffect(new ParticleCurrentDown(worldIn, d0 + 0.5D, d1 + 0.8D, d2));
// } else {
// mc.effectRenderer.addEffect(new ParticleBubbleColumnUp(worldIn, d0 + 0.5D, d1, d2 + 0.5D, 0.0D, 0.04D, 0.0D));
// mc.effectRenderer.addEffect(new ParticleBubbleColumnUp(worldIn, d0 + (double) rand.nextFloat(), d1 +
// (double) rand.nextFloat(), d2 + (double) rand.nextFloat(), 0.0D, 0.04D, 0.0D));
// }
// }
//
// public void neighborChanged(IBlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos) {
// if (!this.isValidPosition(worldIn, pos)) {
// worldIn.setBlockState(pos, Blocks.WATER.getDefaultState());
// return;
// }
// if (fromPos.up().equals(pos)) {
// worldIn.setBlockState(pos, CommonProxy.BUBBLE_COLUMN.getDefaultState().withProperty(DRAG, getDrag(worldIn, fromPos)),
// Constants.BlockFlags.SEND_TO_CLIENTS);
// } else if (fromPos.down().equals(pos) && worldIn.getBlockState(fromPos).getBlock() != this &&
// canHoldBubbleColumn(worldIn, fromPos)) {
// worldIn.scheduleUpdate(pos, this, this.tickRate(worldIn));
// }
// if (fromPos.getX() != pos.getX() || fromPos.getZ() != pos.getZ())
// super.neighborChanged(state, worldIn, pos, blockIn, fromPos);
// }
//
// public boolean isValidPosition(World worldIn, BlockPos pos) {
// Block block = worldIn.getBlockState(pos.down()).getBlock();
// return block == this || block == (worldIn.getBlockState(pos).getValue(DRAG) ? Blocks.SOUL_SAND : Blocks.MAGMA);
// }
//
// private static boolean getDrag(IBlockAccess p_203157_0_, BlockPos p_203157_1_) {
// IBlockState iblockstate = p_203157_0_.getBlockState(p_203157_1_);
// Block block = iblockstate.getBlock();
// if (block == CommonProxy.BUBBLE_COLUMN) {
// return iblockstate.getValue(DRAG);
// } else {
// return block == Blocks.SOUL_SAND;
// }
// }
//
//
// public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
// super.onBlockAdded(worldIn, pos, state);
// placeBubbleColumn(worldIn, pos.up(), getDrag(worldIn, pos.down()));
// }
//
// public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
// placeBubbleColumn(worldIn, pos.up(), getDrag(worldIn, pos));
// }
//
//
//// public IBlockState getStateFromMeta(int meta) {
//// return this.getDefaultState().withProperty(LEVEL, 0).withProperty(DRAG, (meta & 1) == 1);
//// }
//
// public int tickRate(World worldIn) {
// return 5;
// }
//
//
//// public int getMetaFromState(IBlockState state) {
//// return state.getValue(DRAG) ? 1 : 0;
//// }
// }
