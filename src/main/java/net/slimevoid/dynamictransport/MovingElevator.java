package net.slimevoid.dynamictransport;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** One persistent, collidable platform block; the original block data travels with it. */
public final class MovingElevator extends Entity {
  private static final EntityDataAccessor<BlockState> APPEARANCE =
      SynchedEntityData.defineId(MovingElevator.class, EntityDataSerializers.BLOCK_STATE);
  private BlockPos target = BlockPos.ZERO;
  private BlockPos controller = BlockPos.ZERO;
  private CompoundTag blockData = new CompoundTag();
  private double speed;
  private int light, power;
  private int lerpSteps;
  private double lerpX, lerpY, lerpZ;

  public MovingElevator(EntityType<? extends MovingElevator> type, Level level) {
    super(type, level);
    blocksBuilding = true;
  }

  @Override
  protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(APPEARANCE, DynamicTransport.ELEVATOR.get().defaultBlockState());
  }

  void prepare(TransportBlockEntity tile, int delta) {
    var p = tile.getBlockPos();
    setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
    target = p.above(delta);
    controller = tile.controller;
    blockData = tile.saveWithoutMetadata(level().registryAccess());
    entityData.set(APPEARANCE, tile.appearance());
    light = tile.appearance().getLightEmission();
    power =
        tile.hasCamouflage()
            ? tile.appearance().getSignal(level(), tile.getBlockPos(), Direction.UP) / 2
            : 0;
    speed = DynamicTransport.SPEED.get();
  }

  BlockPos controllerPosition() {
    return controller;
  }

  public BlockState appearance() {
    return entityData.get(APPEARANCE);
  }

  @Override
  public boolean canBeCollidedWith() {
    return !isRemoved();
  }

  @Override
  public boolean isPushable() {
    return false;
  }

  @Override
  public boolean canCollideWith(Entity other) {
    return !(other instanceof MovingElevator) && other.canBeCollidedWith();
  }

  @Override
  public void push(Entity entity) {}

  @Override
  public void tick() {
    super.tick();
    if (level().isClientSide) {
      if (lerpSteps > 0) {
        setPos(
            getX() + (lerpX - getX()) / lerpSteps,
            getY() + (lerpY - getY()) / lerpSteps,
            getZ() + (lerpZ - getZ()) / lerpSteps);
        lerpSteps--;
      }
      return;
    }
    double remaining = target.getY() - getY();
    if (Math.abs(remaining) < 0.00001) {
      restore();
      return;
    }
    double velocity =
        Math.copySign(
            Math.min(
                Math.max(0.02, Math.min(speed, tickCount * 0.01)),
                Math.min(Math.abs(remaining), Math.sqrt(0.02 * Math.abs(remaining)))),
            remaining);
    if (!level().hasChunkAt(controller)) return;
    if (level().getBlockEntity(controller) instanceof TransportBlockEntity computer) {
      if (!computer.allowMotion(velocity)) return;
    } else if (!clear(velocity)) return;
    var riders =
        level()
            .getEntities(
                this,
                getBoundingBox().inflate(0.02, 0, 0.02).expandTowards(0, 0.2, 0),
                e ->
                    !(e instanceof MovingElevator)
                        && !e.isSpectator()
                        && !(e instanceof Player p && p.getAbilities().flying)
                        && e.getY() >= getY() + 0.9
                        && e.getY() <= getY() + 1.2);
    if (light > 0 || power > 0) {
      BlockPos trail = BlockPos.containing(getX(), getY() + (velocity > 0 ? -1 : 1), getZ());
      if (level().getBlockState(trail).isAir())
        level()
            .setBlock(
                trail,
                DynamicTransport.TRANSIT_LIGHT
                    .get()
                    .defaultBlockState()
                    .setValue(TransportBlock.LIGHT, light)
                    .setValue(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER,
                        power),
                Block.UPDATE_ALL);
    }
    setDeltaMovement(0, velocity, 0);
    setPos(getX(), getY() + velocity, getZ());
    for (Entity rider : riders) rider.startRiding(this, true);
    for (Entity rider : getPassengers()) {
      positionRider(rider);
      rider.fallDistance = 0;
    }
    if (Math.abs(target.getY() - getY()) < 0.00001) restore();
  }

  boolean clear(double velocity) {
    if (Math.abs(target.getY() - getY()) < 0.00001) return true;
    AABB box = getBoundingBox().expandTowards(0, velocity, 0);
    BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
    BlockPos max = BlockPos.containing(box.maxX - 0.0001, box.maxY + 1.9, box.maxZ - 0.0001);
    for (BlockPos p : BlockPos.betweenClosed(min, max)) {
      if (!level().hasChunkAt(p)) return false;
      if (!level().getBlockState(p).isAir()) {
        if (!(level().getBlockEntity(p) instanceof TransportBlockEntity tile)
            || tile.kind() != TransportBlock.Kind.ELEVATOR
            || !controller.equals(tile.controller)) return false;
      }
    }
    return true;
  }

  @Override
  protected void positionRider(Entity rider, Entity.MoveFunction move) {
    Vec3 pos = getPassengerRidingPosition(rider);
    move.accept(rider, pos.x, pos.y, pos.z);
  }

  @Override
  public boolean shouldRiderSit() {
    return false;
  }

  @Override
  public Vec3 getPassengerRidingPosition(Entity passenger) {
    return new Vec3(
        Mth.clamp(passenger.getX(), getX() - 0.45, getX() + 0.45),
        getY() + 1,
        Mth.clamp(passenger.getZ(), getZ() - 0.45, getZ() + 0.45));
  }

  @Override
  public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
    lerpX = x;
    lerpY = y;
    lerpZ = z;
    lerpSteps = 3;
  }

  private void restore() {
    if (!level().hasChunkAt(target) || !level().getBlockState(target).isAir()) return;
    level().setBlock(target, DynamicTransport.ELEVATOR.get().defaultBlockState(), Block.UPDATE_ALL);
    if (level().getBlockEntity(target) instanceof TransportBlockEntity tile) {
      tile.loadCustomOnly(blockData, level().registryAccess());
      tile.changed();
      for (Entity rider : getPassengers()) {
        positionRider(rider);
        rider.fallDistance = 0;
      }
      ejectPassengers();
      discard();
    }
  }

  @Override
  protected void addAdditionalSaveData(CompoundTag tag) {
    tag.putInt("Light", light);
    tag.putInt("Power", power);
    tag.putInt("TravelTicks", tickCount);
    tag.putLong("Target", target.asLong());
    tag.putLong("Controller", controller.asLong());
    tag.put("BlockData", blockData);
    tag.putDouble("Speed", speed);
    tag.put("Appearance", NbtUtils.writeBlockState(appearance()));
  }

  @Override
  protected void readAdditionalSaveData(CompoundTag tag) {
    light = tag.getInt("Light");
    power = tag.getInt("Power");
    tickCount = tag.getInt("TravelTicks");
    target = BlockPos.of(tag.getLong("Target"));
    controller = BlockPos.of(tag.getLong("Controller"));
    blockData = tag.getCompound("BlockData");
    speed = Mth.clamp(tag.getDouble("Speed"), 0.02, 0.5);
    entityData.set(
        APPEARANCE,
        NbtUtils.readBlockState(
            level().holderLookup(net.minecraft.core.registries.Registries.BLOCK),
            tag.getCompound("Appearance")));
  }
}
