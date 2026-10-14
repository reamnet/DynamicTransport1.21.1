package net.slimevoid.dynamictransport;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

public final class TransportBlock extends BaseEntityBlock {
  public static final net.minecraft.world.level.block.state.properties.IntegerProperty LIGHT =
      net.minecraft.world.level.block.state.properties.IntegerProperty.create("light", 0, 15);

  public enum Kind {
    ELEVATOR,
    COMPUTER,
    MARKER
  }

  public final Kind kind;

  public TransportBlock(Kind kind, Properties properties) {
    super(properties.lightLevel(state -> state.getValue(LIGHT)));
    this.kind = kind;
    registerDefaultState(stateDefinition.any().setValue(LIGHT, 0));
  }

  @Override
  protected MapCodec<? extends BaseEntityBlock> codec() {
    return simpleCodec(p -> new TransportBlock(kind, p));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(LIGHT);
  }

  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new TransportBlockEntity(pos, state);
  }

  @Override
  protected RenderShape getRenderShape(BlockState state) {
    return RenderShape.MODEL;
  }

  @Override
  protected InteractionResult useWithoutItem(
      BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
    return interact(level, pos, player, InteractionHand.MAIN_HAND, hit);
  }

  @Override
  protected ItemInteractionResult useItemOn(
      ItemStack stack,
      BlockState state,
      Level level,
      BlockPos pos,
      Player player,
      InteractionHand hand,
      BlockHitResult hit) {
    interact(level, pos, player, hand, hit);
    return ItemInteractionResult.sidedSuccess(level.isClientSide);
  }

  private InteractionResult interact(
      Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (!level.isClientSide && level.getBlockEntity(pos) instanceof TransportBlockEntity tile)
      tile.interact(player, hand, hit.getDirection());
    return InteractionResult.sidedSuccess(level.isClientSide);
  }

  @Override
  protected void neighborChanged(
      BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
    if (!level.isClientSide
        && kind == Kind.MARKER
        && level.getBlockEntity(pos) instanceof TransportBlockEntity tile) tile.redstoneChanged();
  }

  @Override
  protected void onRemove(
      BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
    if (state.getBlock() != next.getBlock()
        && level.getBlockEntity(pos) instanceof TransportBlockEntity tile) tile.onBroken();
    super.onRemove(state, level, pos, next, moving);
  }

  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
      Level level, BlockState state, BlockEntityType<T> type) {
    return level.isClientSide
        ? null
        : createTickerHelper(type, DynamicTransport.TILE.get(), (l, p, s, tile) -> tile.tick());
  }
}
