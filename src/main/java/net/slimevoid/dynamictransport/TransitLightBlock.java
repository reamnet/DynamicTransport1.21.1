package net.slimevoid.dynamictransport;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.*;

/** Invisible, short-lived light and redstone left by a camouflaged moving platform. */
public final class TransitLightBlock extends Block {
  public TransitLightBlock(Properties properties) {
    super(properties);
    registerDefaultState(
        stateDefinition
            .any()
            .setValue(TransportBlock.LIGHT, 0)
            .setValue(BlockStateProperties.POWER, 0));
  }

  @Override
  protected MapCodec<? extends Block> codec() {
    return simpleCodec(TransitLightBlock::new);
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(TransportBlock.LIGHT, BlockStateProperties.POWER);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.INVISIBLE;
  }

  @Override
  protected VoxelShape getShape(
      BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return Shapes.empty();
  }

  @Override
  protected boolean isSignalSource(BlockState state) {
    return true;
  }

  @Override
  protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
    return state.getValue(BlockStateProperties.POWER);
  }

  @Override
  protected void onPlace(
      BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
    level.scheduleTick(pos, this, 10);
  }

  @Override
  protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    level.removeBlock(pos, false);
  }
}
