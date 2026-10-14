package net.slimevoid.dynamictransport;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-owned bindings and call queue. Platform coordinates are relative to the home floor. */
public final class TransportBlockEntity extends BlockEntity {
  BlockPos controller;
  final List<BlockPos> platforms = new ArrayList<>();
  final List<BlockPos> markers = new ArrayList<>();
  final LinkedHashSet<Integer> queue = new LinkedHashSet<>();
  private long motionCheckTick = Long.MIN_VALUE;
  private boolean motionAllowed;
  UUID technician;
  boolean maintenance, pendingMaintenance, powered, inTransit, relocating;
  int floor, destination, offset = -2, overlay;
  String name = "";
  ItemStack camouflage = ItemStack.EMPTY;

  public TransportBlockEntity(BlockPos pos, BlockState state) {
    super(DynamicTransport.TILE.get(), pos, state);
    floor = pos.getY() + 1;
  }

  TransportBlock.Kind kind() {
    return ((TransportBlock) getBlockState().getBlock()).kind;
  }

  TransportBlockEntity computer() {
    if (kind() == TransportBlock.Kind.COMPUTER) return this;
    return controller != null
            && level != null
            && level.hasChunkAt(controller)
            && level.getBlockEntity(controller) instanceof TransportBlockEntity t
            && t.kind() == TransportBlock.Kind.COMPUTER
        ? t
        : null;
  }

  boolean canMaintain(Player p) {
    return maintenance && p.getUUID().equals(technician);
  }

  void changed() {
    setChanged();
    if (level != null) {
      int light =
          camouflage.getItem() instanceof BlockItem item
              ? item.getBlock().defaultBlockState().getLightEmission()
              : 0;
      if (!level.isClientSide && getBlockState().getValue(TransportBlock.LIGHT) != light)
        level.setBlock(
            worldPosition, getBlockState().setValue(TransportBlock.LIGHT, light), Block.UPDATE_ALL);
      level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
  }

  static void message(Player p, String key, Object... args) {
    p.displayClientMessage(Component.translatable("message.dynamictransport." + key, args), true);
  }

  void interact(Player player, InteractionHand hand, Direction side) {
    ItemStack held = player.getItemInHand(hand);
    if (held.is(DynamicTransport.TOOL.get())) {
      if (kind() == TransportBlock.Kind.COMPUTER && player.isShiftKeyDown()) {
        toggleMaintenance(player, held);
        return;
      }
      var data = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
      if (!data.contains("Controller")
          || !data.getString("Dimension").equals(level.dimension().location().toString())) {
        message(player, "select_computer");
        return;
      }
      BlockPos selected = BlockPos.of(data.getLong("Controller"));
      if (!level.hasChunkAt(selected)
          || !(level.getBlockEntity(selected) instanceof TransportBlockEntity c)
          || c.kind() != TransportBlock.Kind.COMPUTER
          || !c.canMaintain(player)) {
        message(player, "maintenance_required");
        return;
      }
      if (kind() == TransportBlock.Kind.COMPUTER) {
        open(player, true);
        return;
      }
      if (controller != null
          && level.hasChunkAt(controller)
          && !(level.getBlockEntity(controller) instanceof TransportBlockEntity old
              && old.kind() == TransportBlock.Kind.COMPUTER)) {
        controller = null;
        changed();
      }
      if (controller != null && !controller.equals(selected)) {
        message(player, "other_computer");
        return;
      }
      if (player.isShiftKeyDown()) {
        if (controller == null) return;
        if (kind() == TransportBlock.Kind.ELEVATOR)
          c.platforms.remove(
              new BlockPos(
                  worldPosition.getX(), worldPosition.getY() - c.home(), worldPosition.getZ()));
        else c.markers.remove(worldPosition);
        controller = null;
        c.changed();
        changed();
        message(player, "unbound");
        return;
      }
      if (controller == null) {
        if (!c.bind(this)) {
          message(player, "out_of_range");
          return;
        }
        controller = selected;
        changed();
        message(player, "bound");
        return;
      }
      if (kind() == TransportBlock.Kind.ELEVATOR) {
        overlay ^= 1 << side.get3DDataValue();
        changed();
        message(player, "overlay");
      } else open(player, true);
      return;
    }
    TransportBlockEntity c = computer();
    boolean editable = c == null ? kind() != TransportBlock.Kind.COMPUTER : c.canMaintain(player);
    if (editable
        && held.getItem() instanceof BlockItem block
        && block.getBlock().defaultBlockState().isCollisionShapeFullBlock(level, worldPosition)
        && !(block.getBlock() instanceof TransportBlock)
        && camouflage.isEmpty()) {
      camouflage = held.copyWithCount(1);
      if (!player.getAbilities().instabuild) held.shrink(1);
      changed();
      return;
    }
    if (editable && held.isEmpty() && !camouflage.isEmpty()) {
      if (!player.addItem(camouflage.copy())) player.drop(camouflage.copy(), false);
      camouflage = ItemStack.EMPTY;
      changed();
      return;
    }
    if (c != null && !c.maintenance && !c.pendingMaintenance) {
      if (kind() != TransportBlock.Kind.ELEVATOR || (overlay & (1 << side.get3DDataValue())) != 0)
        open(player, false);
    } else message(player, "maintenance_required");
  }

  int home() {
    return worldPosition.getY() + 1;
  }

  private void toggleMaintenance(Player player, ItemStack tool) {
    if (technician != null && !technician.equals(player.getUUID())) {
      message(player, "technician_busy");
      return;
    }
    if (technician != null) {
      if (inTransit) {
        message(player, "moving");
        return;
      }
      technician = null;
      maintenance = false;
      pendingMaintenance = false;
      tool.remove(DataComponents.CUSTOM_DATA);
      changed();
      message(player, "maintenance_complete");
      return;
    }
    technician = player.getUUID();
    queue.clear();
    if (platforms.isEmpty()) floor = home();
    if (floor != home() || inTransit) {
      pendingMaintenance = true;
      queue.add(home());
    } else maintenance = true;
    CompoundTag data = new CompoundTag();
    data.putLong("Controller", worldPosition.asLong());
    data.putString("Dimension", level.dimension().location().toString());
    tool.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    changed();
    message(player, "maintenance_started");
  }

  boolean bind(TransportBlockEntity tile) {
    if (platforms.size() >= 128 || markers.size() >= 128) return false;
    boolean platform = tile.kind() == TransportBlock.Kind.ELEVATOR;
    boolean nearby =
        (platform
            && horizontalDistance(worldPosition, tile.worldPosition)
                <= DynamicTransport.BINDING_RANGE.get());
    for (BlockPos p : platforms)
      nearby |= horizontalDistance(p, tile.worldPosition) <= DynamicTransport.BINDING_RANGE.get();
    if (!nearby || (!platform && platforms.isEmpty())) return false;
    if (platform) {
      BlockPos relative =
          new BlockPos(
              tile.worldPosition.getX(),
              tile.worldPosition.getY() - home(),
              tile.worldPosition.getZ());
      if (!platforms.contains(relative)) platforms.add(relative);
    } else if (!markers.contains(tile.worldPosition)) markers.add(tile.worldPosition.immutable());
    changed();
    return true;
  }

  static double horizontalDistance(BlockPos a, BlockPos b) {
    return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
  }

  SortedMap<Integer, String> floors() {
    SortedMap<Integer, String> result = new TreeMap<>();
    if (level == null) return result;
    for (BlockPos p : markers) {
      if (level.hasChunkAt(p)
          && level.getBlockEntity(p) instanceof TransportBlockEntity m
          && worldPosition.equals(m.controller)) result.put(p.getY() + m.offset, m.name);
    }
    return result;
  }

  boolean call(int y) {
    if (y < level.getMinBuildHeight()
        || y >= level.getMaxBuildHeight()
        || kind() != TransportBlock.Kind.COMPUTER
        || maintenance
        || pendingMaintenance
        || platforms.isEmpty()
        || !floors().containsKey(y)) return false;
    if (y != floor || inTransit) queue.add(y);
    changed();
    return true;
  }

  void redstoneChanged() {
    boolean signal = level.hasNeighborSignal(worldPosition);
    if (signal && !powered) {
      var c = computer();
      if (c != null) c.call(worldPosition.getY() + offset);
    }
    powered = signal;
    setChanged();
  }

  void open(Player player, boolean editing) {
    if (!(player instanceof ServerPlayer server)) return;
    var c = computer();
    if (c == null) return;
    CompoundTag info = new CompoundTag();
    info.putBoolean("Editing", editing);
    info.putBoolean("Marker", kind() == TransportBlock.Kind.MARKER);
    info.putString("Name", name);
    info.putInt("Floor", worldPosition.getY() + offset);
    ListTag list = new ListTag();
    c.floors()
        .forEach(
            (y, n) -> {
              CompoundTag t = new CompoundTag();
              t.putInt("Y", y);
              t.putString("Name", n);
              list.add(t);
            });
    info.put("Floors", list);
    server.openMenu(
        new SimpleMenuProvider(
            (id, inventory, p) -> new TransportMenu(id, inventory, worldPosition, info),
            Component.translatable(
                editing ? "screen.dynamictransport.configure" : "screen.dynamictransport.floors")),
        buf -> {
          buf.writeBlockPos(worldPosition);
          buf.writeNbt(info);
        });
  }

  void tick() {
    if (kind() != TransportBlock.Kind.COMPUTER) return;
    if (inTransit) {
      // Arrivals replace every moving entity with its saved block entity before the queue advances.
      for (BlockPos p : platforms) {
        BlockPos target = new BlockPos(p.getX(), destination + p.getY(), p.getZ());
        if (!level.hasChunkAt(target)
            || !(level.getBlockEntity(target) instanceof TransportBlockEntity t)
            || !worldPosition.equals(t.controller)) return;
      }
      floor = destination;
      inTransit = false;
      if (pendingMaintenance && floor == home()) {
        pendingMaintenance = false;
        maintenance = true;
        queue.clear();
      }
      changed();
    }
    if (!maintenance && !queue.isEmpty()) {
      int next = queue.iterator().next();
      if (next == floor) {
        queue.remove(next);
        changed();
      } else if (start(next)) {
        queue.remove(next);
        changed();
      }
    }
  }

  boolean allowMotion(double velocity) {
    long now = level.getGameTime();
    if (motionCheckTick == now) return motionAllowed;
    motionCheckTick = now;
    net.minecraft.world.phys.AABB bounds = new net.minecraft.world.phys.AABB(worldPosition);
    for (BlockPos p : platforms)
      bounds =
          bounds
              .minmax(
                  new net.minecraft.world.phys.AABB(
                      new BlockPos(p.getX(), floor + p.getY(), p.getZ())))
              .minmax(
                  new net.minecraft.world.phys.AABB(
                      new BlockPos(p.getX(), destination + p.getY(), p.getZ())));
    var group =
        level.getEntitiesOfClass(
            MovingElevator.class,
            bounds.inflate(2),
            e -> worldPosition.equals(e.controllerPosition()));
    motionAllowed =
        group.size() == platforms.size() && group.stream().allMatch(e -> e.clear(velocity));
    return motionAllowed;
  }

  boolean start(int target) {
    if (platforms.isEmpty()
        || inTransit
        || target < level.getMinBuildHeight()
        || target >= level.getMaxBuildHeight()) return false;
    List<TransportBlockEntity> tiles = new ArrayList<>();
    Set<BlockPos> originals = new HashSet<>();
    for (BlockPos p : platforms) originals.add(new BlockPos(p.getX(), floor + p.getY(), p.getZ()));
    // Validate the entire shaft, including rider headroom, before removing a single block.
    for (BlockPos p : platforms) {
      BlockPos origin = new BlockPos(p.getX(), floor + p.getY(), p.getZ());
      if (!level.hasChunkAt(origin)
          || !(level.getBlockEntity(origin) instanceof TransportBlockEntity tile)
          || tile.kind() != TransportBlock.Kind.ELEVATOR
          || !worldPosition.equals(tile.controller)) return false;
      for (int y = Math.min(floor, target) + p.getY();
          y <= Math.max(floor, target) + p.getY() + 2;
          y++) {
        BlockPos check = new BlockPos(p.getX(), y, p.getZ());
        if (!level.isInWorldBounds(check)
            || !level.hasChunkAt(check)
            || (!originals.contains(check) && !level.getBlockState(check).isAir())) return false;
      }
      tiles.add(tile);
    }
    List<MovingElevator> moving = new ArrayList<>();
    for (var tile : tiles) {
      var entity = new MovingElevator(DynamicTransport.MOVING.get(), level);
      entity.prepare(tile, target - floor);
      if (!level.addFreshEntity(entity)) {
        moving.forEach(e -> e.discard());
        return false;
      }
      moving.add(entity);
    }
    for (var tile : tiles) {
      tile.relocating = true;
      level.removeBlock(tile.worldPosition, false);
    }
    destination = target;
    inTransit = true;
    changed();
    return true;
  }

  void onBroken() {
    if (relocating || level.isClientSide) return;
    if (!camouflage.isEmpty()) Block.popResource(level, worldPosition, camouflage);
    var c = computer();
    if (c != null && c != this) {
      if (kind() == TransportBlock.Kind.MARKER) c.markers.remove(worldPosition);
      else
        c.platforms.remove(
            new BlockPos(
                worldPosition.getX(),
                worldPosition.getY() - (c.inTransit ? c.destination : c.floor),
                worldPosition.getZ()));
      c.changed();
    }
  }

  public int overlayFaces() {
    return overlay;
  }

  public boolean hasCamouflage() {
    return !camouflage.isEmpty();
  }

  public BlockState appearance() {
    return camouflage.getItem() instanceof BlockItem item
        ? item.getBlock().defaultBlockState()
        : DynamicTransport.ELEVATOR.get().defaultBlockState();
  }

  @Override
  protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.saveAdditional(tag, provider);
    if (controller != null) tag.putLong("Controller", controller.asLong());
    if (technician != null) tag.putUUID("Technician", technician);
    tag.putBoolean("Maintenance", maintenance);
    tag.putBoolean("PendingMaintenance", pendingMaintenance);
    tag.putBoolean("Powered", powered);
    tag.putBoolean("InTransit", inTransit);
    tag.putInt("Floor", floor);
    tag.putInt("Destination", destination);
    tag.putInt("Offset", offset);
    tag.putInt("Overlay", overlay);
    tag.putString("Name", name);
    tag.putLongArray("Platforms", platforms.stream().mapToLong(BlockPos::asLong).toArray());
    tag.putLongArray("Markers", markers.stream().mapToLong(BlockPos::asLong).toArray());
    tag.putIntArray("Queue", queue.stream().mapToInt(Integer::intValue).toArray());
    if (!camouflage.isEmpty()) tag.put("Camouflage", camouflage.save(provider));
  }

  @Override
  protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
    super.loadAdditional(tag, provider);
    controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    technician = tag.hasUUID("Technician") ? tag.getUUID("Technician") : null;
    maintenance = tag.getBoolean("Maintenance");
    pendingMaintenance = tag.getBoolean("PendingMaintenance");
    powered = tag.getBoolean("Powered");
    inTransit = tag.getBoolean("InTransit");
    floor = tag.contains("Floor") ? tag.getInt("Floor") : home();
    destination = tag.getInt("Destination");
    offset = tag.contains("Offset") ? tag.getInt("Offset") : -2;
    overlay = tag.getInt("Overlay");
    name = tag.getString("Name");
    platforms.clear();
    for (long p : tag.getLongArray("Platforms")) platforms.add(BlockPos.of(p));
    markers.clear();
    for (long p : tag.getLongArray("Markers")) markers.add(BlockPos.of(p));
    queue.clear();
    for (int y : tag.getIntArray("Queue")) queue.add(y);
    camouflage =
        tag.contains("Camouflage")
            ? ItemStack.parseOptional(provider, tag.getCompound("Camouflage"))
            : ItemStack.EMPTY;
  }

  @Override
  public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
    return saveWithoutMetadata(provider);
  }

  @Override
  public ClientboundBlockEntityDataPacket getUpdatePacket() {
    return ClientboundBlockEntityDataPacket.create(this);
  }
}
