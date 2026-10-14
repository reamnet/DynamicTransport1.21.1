package net.slimevoid.dynamictransport;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class TransportMenu extends AbstractContainerMenu {
  public final BlockPos pos;
  public final CompoundTag info;
  final Inventory inventory;

  /** Blank names remain automatic rather than storing a coordinate that can go stale. */
  public static Component floorLabel(String name, int y) {
    return name.isBlank() ? Component.literal("Y " + y) : Component.literal(name.strip());
  }

  public TransportMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
    this(id, inventory, buf.readBlockPos(), buf.readNbt());
  }

  public TransportMenu(int id, Inventory inventory, BlockPos pos, CompoundTag info) {
    super(DynamicTransport.MENU.get(), id);
    this.inventory = inventory;
    this.pos = pos;
    this.info = info == null ? new CompoundTag() : info;
  }

  @Override
  public boolean stillValid(Player player) {
    return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64
        && player.level().getBlockEntity(pos) instanceof TransportBlockEntity;
  }

  @Override
  public ItemStack quickMoveStack(Player player, int slot) {
    return ItemStack.EMPTY;
  }

  @Override
  public boolean clickMenuButton(Player player, int index) {
    if (!stillValid(player)
        || info.getBoolean("Editing")
        || !(player.level().getBlockEntity(pos) instanceof TransportBlockEntity tile)) return false;
    var c = tile.computer();
    var floors = info.getList("Floors", 10);
    if (c == null || index < 0 || index >= floors.size()) return false;
    return c.call(floors.getCompound(index).getInt("Y"));
  }

  boolean configure(Player player, String name, int y) {
    if (!stillValid(player)
        || !info.getBoolean("Editing")
        || !(player.level().getBlockEntity(pos) instanceof TransportBlockEntity tile)) return false;
    var c = tile.computer();
    if (c == null
        || !c.canMaintain(player)
        || name.length() > 32
        || name.chars().anyMatch(ch -> Character.isISOControl(ch) || ch == 167)) return false;
    if (tile.kind() == TransportBlock.Kind.MARKER) {
      if (y < pos.getY() - 2 || y > pos.getY() + 2) return false;
      tile.offset = y - pos.getY();
    }
    tile.name = name.strip();
    tile.changed();
    return true;
  }
}
