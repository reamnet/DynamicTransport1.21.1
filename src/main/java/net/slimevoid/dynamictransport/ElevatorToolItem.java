package net.slimevoid.dynamictransport;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Handles tool use when sneaking bypasses the block's normal interaction handler. */
public final class ElevatorToolItem extends Item {
  public ElevatorToolItem(Properties properties) {
    super(properties);
  }

  @Override
  public InteractionResult useOn(UseOnContext context) {
    var player = context.getPlayer();
    var level = context.getLevel();
    if (player == null
        || !(level.getBlockEntity(context.getClickedPos()) instanceof TransportBlockEntity tile)) {
      return InteractionResult.PASS;
    }
    if (!level.isClientSide) {
      tile.interact(player, context.getHand(), context.getClickedFace());
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
  }
}
