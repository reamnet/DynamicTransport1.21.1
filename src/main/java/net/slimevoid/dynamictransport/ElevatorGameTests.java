package net.slimevoid.dynamictransport;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(DynamicTransport.ID)
@PrefixGameTestTemplate(false)
public final class ElevatorGameTests {
  private static final BlockPos COMPUTER = new BlockPos(3, 1, 3);
  private static final BlockPos PLATFORM = new BlockPos(3, 2, 3);
  private static final BlockPos MARKER = new BlockPos(5, 8, 3);

  private static TransportBlockEntity setup(GameTestHelper helper, boolean wide) {
    helper.setBlock(COMPUTER, DynamicTransport.COMPUTER.get());
    helper.setBlock(PLATFORM, DynamicTransport.ELEVATOR.get());
    helper.setBlock(MARKER, DynamicTransport.MARKER.get());
    var c = (TransportBlockEntity) helper.getBlockEntity(COMPUTER);
    var p = (TransportBlockEntity) helper.getBlockEntity(PLATFORM);
    var m = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    helper.assertTrue(c.bind(p), "Platform should bind");
    p.controller = c.getBlockPos();
    helper.assertTrue(c.bind(m), "Marker should bind");
    m.controller = c.getBlockPos();
    if (wide) {
      helper.setBlock(PLATFORM.east(), DynamicTransport.ELEVATOR.get());
      var other = (TransportBlockEntity) helper.getBlockEntity(PLATFORM.east());
      helper.assertTrue(c.bind(other), "Second platform should bind");
      other.controller = c.getBlockPos();
    }
    return c;
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void travelsAndRestoresBindings(GameTestHelper helper) {
    var c = setup(helper, true);
    int target = helper.absolutePos(MARKER).getY() - 2;
    helper.assertTrue(c.call(target), "Bound floor should accept calls");
    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3));
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(4, 6, 3));
          var p = (TransportBlockEntity) helper.getBlockEntity(new BlockPos(3, 6, 3));
          helper.assertTrue(
              c.getBlockPos().equals(p.controller), "Restored platform should retain controller");
          helper.assertTrue(
              !c.inTransit && c.floor == target, "Controller should complete the trip");
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void redstoneQueuesFloor(GameTestHelper helper) {
    var c = setup(helper, false);
    helper.setBlock(MARKER.east(), Blocks.REDSTONE_BLOCK);
    helper.succeedWhen(
        () -> helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3)));
  }

  @GameTest(template = "empty")
  public static void obstructedShaftKeepsPlatform(GameTestHelper helper) {
    var c = setup(helper, false);
    helper.setBlock(new BlockPos(3, 5, 3), Blocks.STONE);
    helper.assertTrue(
        !c.start(helper.absolutePos(new BlockPos(3, 6, 3)).getY()),
        "Obstructed departure must fail");
    helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), PLATFORM);
    helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 5, 3));
    helper.assertTrue(!c.inTransit, "Failed departure must not enter transit");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void savedStatePreservesQueueAndBindings(GameTestHelper helper) {
    var c = setup(helper, true);
    int target = helper.absolutePos(new BlockPos(3, 6, 3)).getY();
    c.call(target);
    c.name = "Test elevator";
    CompoundTag tag = c.saveWithoutMetadata(helper.getLevel().registryAccess());
    var copy = new TransportBlockEntity(c.getBlockPos(), c.getBlockState());
    copy.loadCustomOnly(tag, helper.getLevel().registryAccess());
    helper.assertTrue(
        copy.platforms.equals(c.platforms) && copy.markers.equals(c.markers),
        "Bindings must survive reload");
    helper.assertTrue(
        copy.queue.contains(target) && copy.name.equals(c.name),
        "Queue and name must survive reload");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void rejectsUnboundFloorAndMaintenanceCalls(GameTestHelper helper) {
    var c = setup(helper, false);
    helper.assertTrue(!c.call(c.home() + 50), "Unbound floors must be rejected");
    c.maintenance = true;
    helper.assertTrue(
        !c.call(helper.absolutePos(MARKER).getY() - 2), "Maintenance must reject calls");
    helper.succeed();
  }

  @GameTest(template = "empty", timeoutTicks = 240)
  public static void movingEntitySurvivesReload(GameTestHelper helper) {
    var c = setup(helper, false);
    c.call(helper.absolutePos(MARKER).getY() - 2);
    helper.runAtTickTime(
        15,
        () -> {
          var entities =
              helper
                  .getLevel()
                  .getEntitiesOfClass(
                      MovingElevator.class, new AABB(helper.absolutePos(PLATFORM)).inflate(8));
          helper.assertTrue(entities.size() == 1, "Expected one platform in transit");
          var original = entities.getFirst();
          CompoundTag tag = new CompoundTag();
          original.saveWithoutId(tag);
          original.discard();
          var restored = new MovingElevator(DynamicTransport.MOVING.get(), helper.getLevel());
          restored.load(tag);
          helper.getLevel().addFreshEntity(restored);
        });
    helper.succeedWhen(
        () -> {
          helper.assertTrue(helper.getTick() > 15, "Reload must execute before success");
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3));
        });
  }

  @GameTest(template = "empty", timeoutTicks = 240)
  public static void queuedCallsReturnHome(GameTestHelper helper) {
    var c = setup(helper, false);
    BlockPos homeMarker = new BlockPos(5, 4, 3);
    helper.setBlock(homeMarker, DynamicTransport.MARKER.get());
    var m = (TransportBlockEntity) helper.getBlockEntity(homeMarker);
    c.bind(m);
    m.controller = c.getBlockPos();
    c.call(helper.absolutePos(MARKER).getY() - 2);
    helper.runAtTickTime(
        10, () -> helper.assertTrue(c.call(c.home()), "A return call should queue during transit"));
    helper.succeedWhen(
        () -> {
          helper.assertTrue(helper.getTick() > 20, "Return trip must occur");
          helper.assertTrue(!c.inTransit && c.floor == c.home(), "Queued call should return home");
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), PLATFORM);
        });
  }

  @GameTest(template = "empty", timeoutTicks = 240)
  public static void obstructionDuringTravelPausesAndResumes(GameTestHelper helper) {
    var c = setup(helper, true);
    c.call(helper.absolutePos(MARKER).getY() - 2);
    helper.runAtTickTime(10, () -> helper.setBlock(new BlockPos(3, 6, 3), Blocks.STONE));
    helper.runAtTickTime(35, () -> helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 6, 3)));
    helper.runAtTickTime(40, () -> helper.setBlock(new BlockPos(3, 6, 3), Blocks.AIR));
    helper.succeedWhen(
        () -> {
          helper.assertTrue(helper.getTick() > 40, "Obstruction removal must occur");
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3));
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(4, 6, 3));
        });
  }

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void carriesLivingRiders(GameTestHelper helper) {
    var c = setup(helper, false);
    var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, PLATFORM.above());
    pig.setNoAi(true);
    c.call(helper.absolutePos(MARKER).getY() - 2);
    helper.succeedWhen(
        () -> {
          helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3));
          helper.assertTrue(
              pig.getY() > helper.absolutePos(new BlockPos(3, 6, 3)).getY() + 0.8,
              "Rider must arrive on top of the platform");
        });
  }

  @GameTest(template = "empty")
  public static void menuRejectsOtherTechnicianAndRemoteEdits(GameTestHelper helper) {
    var c = setup(helper, false);
    c.maintenance = true;
    var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
    var pos = helper.absolutePos(MARKER);
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    var info = new CompoundTag();
    info.putBoolean("Editing", true);
    var menu = new TransportMenu(1, player.getInventory(), pos, info);
    c.technician = java.util.UUID.randomUUID();
    helper.assertTrue(
        !menu.configure(player, "Forged", pos.getY()), "Other technician must not edit");
    c.technician = player.getUUID();
    helper.assertTrue(
        !menu.configure(player, "Too far", pos.getY() + 3),
        "Invalid floor offset must be rejected");
    helper.assertTrue(
        menu.configure(player, "Allowed", pos.getY()), "Technician should configure nearby marker");
    player.setPos(pos.getX() + 100, pos.getY(), pos.getZ());
    helper.assertTrue(
        !menu.configure(player, "Remote", pos.getY()), "Remote menu edit must be rejected");
    helper.succeed();
  }
}
