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

  private static void enableMenuChannel(net.minecraft.server.level.ServerPlayer player) {
    // GameTest mock clients skip the network handshake. Advertise the screen payload that a
    // real NeoForge client supports, keeping the normal packet validation and menu dispatch.
    net.neoforged.neoforge.network.registration.NetworkRegistry.onMinecraftRegister(
        player.connection.getConnection(),
        java.util.Set.of(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "neoforge", "advanced_open_screen")));
  }

  private static void clickWithTool(
      GameTestHelper helper,
      net.minecraft.server.level.ServerPlayer player,
      BlockPos relative,
      boolean sneaking) {
    BlockPos pos = helper.absolutePos(relative);
    player.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
    player.setShiftKeyDown(sneaking);
    var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
    var hit =
        new net.minecraft.world.phys.BlockHitResult(
            net.minecraft.world.phys.Vec3.atCenterOf(pos),
            net.minecraft.core.Direction.UP,
            pos,
            false);
    var result =
        player.gameMode.useItemOn(player, helper.getLevel(), player.getItemInHand(hand), hand, hit);
    helper.assertTrue(result.consumesAction(), "Tool click should be handled, even while sneaking");
  }

  @GameTest(template = "empty")
  public static void toolSelectsBindsUnbindsAndFinishesMaintenance(GameTestHelper helper) {
    helper.setBlock(COMPUTER, DynamicTransport.COMPUTER.get());
    helper.setBlock(PLATFORM, DynamicTransport.ELEVATOR.get());
    helper.setBlock(MARKER, DynamicTransport.MARKER.get());
    var c = (TransportBlockEntity) helper.getBlockEntity(COMPUTER);
    var platform = (TransportBlockEntity) helper.getBlockEntity(PLATFORM);
    var marker = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    var player = helper.makeMockServerPlayerInLevel();
    var tool = new net.minecraft.world.item.ItemStack(DynamicTransport.TOOL.get());
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
    try {
      clickWithTool(helper, player, COMPUTER, true);
      helper.assertTrue(c.canMaintain(player), "Sneak-click should enter maintenance exactly once");
      var selected =
          tool.getOrDefault(
                  net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                  net.minecraft.world.item.component.CustomData.EMPTY)
              .copyTag();
      helper.assertTrue(
          selected.contains("Controller")
              && selected.getLong("Controller") == c.getBlockPos().asLong(),
          "Tool must select the computer");
      helper.assertTrue(c.isSelectedForMaintenance(player), "Selected computer should be green");
      helper.assertTrue(
          !platform.isSelectedForMaintenance(player), "Unbound platform must not be green");
      clickWithTool(helper, player, PLATFORM, false);
      helper.assertTrue(
          c.getBlockPos().equals(platform.controller) && c.platforms.size() == 1,
          "Tool click should bind platform to the selected computer");
      helper.assertTrue(
          platform.isSelectedForMaintenance(player), "Newly bound platform should be green");
      clickWithTool(helper, player, MARKER, false);
      helper.assertTrue(
          c.getBlockPos().equals(marker.controller) && c.markers.size() == 1,
          "Tool click should bind floor marker");
      helper.assertTrue(
          marker.isSelectedForMaintenance(player), "Newly bound marker should be green");
      clickWithTool(helper, player, PLATFORM, false);
      helper.assertTrue(
          platform.overlay == 1 << net.minecraft.core.Direction.UP.get3DDataValue(),
          "A regular click must toggle a selection face only once");
      clickWithTool(helper, player, MARKER, true);
      helper.assertTrue(
          marker.controller == null && c.markers.isEmpty(), "Sneak-click must unbind marker");
      helper.assertTrue(
          !marker.isSelectedForMaintenance(player), "Unbinding must remove green feedback");
      clickWithTool(helper, player, PLATFORM, true);
      helper.assertTrue(
          platform.controller == null && c.platforms.isEmpty(), "Sneak-click must unbind platform");
      clickWithTool(helper, player, COMPUTER, true);
      helper.assertTrue(
          !c.maintenance && c.technician == null,
          "Second sneak-click should end maintenance exactly once");
      helper.assertTrue(
          !c.isSelectedForMaintenance(player), "Finishing maintenance must remove the highlight");
      helper.assertTrue(
          !tool.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA),
          "Finishing maintenance should clear the selected computer");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(template = "empty")
  public static void toolSelectsComputerWithOccupiedOffhand(GameTestHelper helper) {
    helper.setBlock(COMPUTER, DynamicTransport.COMPUTER.get());
    var c = (TransportBlockEntity) helper.getBlockEntity(COMPUTER);
    var player = helper.makeMockServerPlayerInLevel();
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.item.ItemStack(DynamicTransport.TOOL.get()));
    player.setItemInHand(
        net.minecraft.world.InteractionHand.OFF_HAND,
        new net.minecraft.world.item.ItemStack(Blocks.COBBLESTONE));
    try {
      clickWithTool(helper, player, COMPUTER, true);
      helper.assertTrue(
          c.canMaintain(player), "An occupied offhand must not prevent computer selection");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

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

  @GameTest(template = "empty", timeoutTicks = 200)
  public static void floorMarkerCallsWithSetupToolAfterMaintenance(GameTestHelper helper) {
    var c = setup(helper, false);
    var player = helper.makeMockServerPlayerInLevel();
    enableMenuChannel(player);
    var tool = new net.minecraft.world.item.ItemStack(DynamicTransport.TOOL.get());
    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, tool);
    try {
      // An old selected tool must not turn an ordinary floor call into a maintenance action.
      var data = new CompoundTag();
      data.putLong("Controller", c.getBlockPos().asLong());
      data.putString("Dimension", helper.getLevel().dimension().location().toString());
      tool.set(
          net.minecraft.core.component.DataComponents.CUSTOM_DATA,
          net.minecraft.world.item.component.CustomData.of(data));
      clickWithTool(helper, player, MARKER, false);
      helper.assertTrue(
          player.containerMenu instanceof TransportMenu,
          "Operational marker should open floor selection even with a previously selected tool");
      var menu = (TransportMenu) player.containerMenu;
      helper.assertTrue(
          !menu.info.getBoolean("Editing"),
          "Operational marker must open call controls, not configuration");
      helper.assertTrue(
          menu.clickMenuButton(player, 0), "Player should be able to queue the marker's floor");
      player.closeContainer();
      tool.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
      clickWithTool(helper, player, MARKER, false);
      helper.assertTrue(
          player.containerMenu instanceof TransportMenu,
          "An unselected tool must also allow normal floor calls");
      player.closeContainer();
      player.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
      clickWithTool(helper, player, MARKER, false);
      helper.assertTrue(
          player.containerMenu instanceof TransportMenu,
          "Empty-hand floor selection must still work");
      player.closeContainer();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
    helper.succeedWhen(
        () -> helper.assertBlockPresent(DynamicTransport.ELEVATOR.get(), new BlockPos(3, 6, 3)));
  }

  @GameTest(template = "empty")
  public static void markerRemainsConfigurableAndLockedDuringMaintenance(GameTestHelper helper) {
    var c = setup(helper, false);
    var marker = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    var player = helper.makeMockServerPlayerInLevel();
    enableMenuChannel(player);
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND,
        new net.minecraft.world.item.ItemStack(DynamicTransport.TOOL.get()));
    try {
      clickWithTool(helper, player, COMPUTER, true);
      clickWithTool(helper, player, MARKER, false);
      helper.assertTrue(
          player.containerMenu instanceof TransportMenu,
          "Technician should still be able to configure a marker");
      helper.assertTrue(
          ((TransportMenu) player.containerMenu).info.getBoolean("Editing"),
          "Maintenance marker should open its configuration screen");
      helper.assertTrue(
          !c.call(marker.getBlockPos().getY() + marker.offset),
          "Maintenance must continue to block movement calls");
      helper.assertTrue(
          marker.isSelectedForMaintenance(player),
          "Bound marker should be green during maintenance");
      player.closeContainer();
      var saved = c.getUpdateTag(helper.getLevel().registryAccess());
      c.loadCustomOnly(saved, helper.getLevel().registryAccess());
      helper.assertTrue(
          marker.isSelectedForMaintenance(player),
          "Maintenance feedback must survive saved-state loading");
      player.setItemInHand(
          net.minecraft.world.InteractionHand.OFF_HAND, player.getMainHandItem().copy());
      player.setItemInHand(
          net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
      helper.assertTrue(
          marker.isSelectedForMaintenance(player),
          "Holding selected tool in offhand should also highlight");
      player.setItemInHand(
          net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
      helper.assertTrue(
          !marker.isSelectedForMaintenance(player),
          "Putting the tool away should remove the highlight");
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }

  @GameTest(template = "empty")
  public static void markerExplainsMaintenanceAndUnboundStates(GameTestHelper helper) {
    var c = setup(helper, false);
    var marker = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    var messages = new java.util.ArrayList<String>();
    var player =
        new net.minecraft.world.entity.player.Player(
            helper.getLevel(),
            BlockPos.ZERO,
            0,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "feedback-test")) {
          @Override
          public boolean isSpectator() {
            return false;
          }

          @Override
          public boolean isCreative() {
            return true;
          }

          @Override
          public void displayClientMessage(
              net.minecraft.network.chat.Component message, boolean actionBar) {
            if (message.getContents()
                instanceof net.minecraft.network.chat.contents.TranslatableContents text)
              messages.add(text.getKey());
          }
        };
    c.maintenance = true;
    c.technician = player.getUUID();
    marker.interact(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.core.Direction.UP);
    helper.assertTrue(
        messages.getLast().equals("message.dynamictransport.maintenance_active"),
        "Empty-hand use during maintenance should explain that maintenance is already active");
    c.maintenance = false;
    c.pendingMaintenance = true;
    marker.interact(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.core.Direction.UP);
    helper.assertTrue(
        messages.getLast().equals("message.dynamictransport.maintenance_pending"),
        "Pending maintenance must explain that the platform is returning home");
    marker.controller = null;
    marker.interact(
        player, net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.core.Direction.UP);
    helper.assertTrue(
        messages.getLast().equals("message.dynamictransport.unbound_component"),
        "Unbound markers must explain that binding is missing");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void floorNamesDefaultToHeightAndCanBeRenamedOrReset(GameTestHelper helper) {
    var c = setup(helper, false);
    var marker = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
    var pos = marker.getBlockPos();
    player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    c.maintenance = true;
    c.technician = player.getUUID();
    var info = new CompoundTag();
    info.putBoolean("Editing", true);
    var menu = new TransportMenu(1, player.getInventory(), pos, info);
    int originalY = pos.getY() + marker.offset;
    helper.assertTrue(
        TransportMenu.floorLabel(marker.name, originalY).getString().equals("Y " + originalY),
        "New markers must display their stop Y rather than an empty name");
    helper.assertTrue(
        menu.configure(player, "  Lobby  ", pos.getY() - 1),
        "Technician should be able to rename the floor");
    helper.assertTrue(
        marker.name.equals("Lobby") && c.floors().get(pos.getY() - 1).equals("Lobby"),
        "Trimmed name must appear in the actual controller's floor list");
    var saved = marker.saveWithoutMetadata(helper.getLevel().registryAccess());
    var restored = new TransportBlockEntity(pos, marker.getBlockState());
    restored.loadCustomOnly(saved, helper.getLevel().registryAccess());
    helper.assertTrue(
        restored.name.equals("Lobby") && restored.offset == -1,
        "Renaming and height must survive a world save");
    helper.assertTrue(
        menu.configure(player, "   ", pos.getY() + 1),
        "Clearing a name should restore automatic naming");
    helper.assertTrue(
        marker.name.isEmpty()
            && TransportMenu.floorLabel(marker.name, pos.getY() + 1)
                .getString()
                .equals("Y " + (pos.getY() + 1)),
        "Automatic names must follow a changed stop height");
    helper.assertTrue(
        !c.floors().containsKey(originalY) && c.floors().containsKey(pos.getY() + 1),
        "Changing the label must not leave an obsolete destination in the floor list");
    helper.succeed();
  }

  @GameTest(template = "empty")
  public static void floorSelectorReceivesNamesAndCurrentStop(GameTestHelper helper) {
    var c = setup(helper, false);
    var marker = (TransportBlockEntity) helper.getBlockEntity(MARKER);
    marker.name = "Workshop";
    var player = helper.makeMockServerPlayerInLevel();
    enableMenuChannel(player);
    player.setItemInHand(
        net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
    try {
      clickWithTool(helper, player, MARKER, false);
      var menu = (TransportMenu) player.containerMenu;
      var first = menu.info.getList("Floors", 10).getCompound(0);
      helper.assertTrue(
          TransportMenu.floorLabel(first.getString("Name"), first.getInt("Y"))
              .getString()
              .equals("Workshop"),
          "The actual floor-selector menu must carry the saved human-readable name");
      helper.assertTrue(
          menu.info.getInt("CurrentFloor") == c.floor && !menu.info.getBoolean("InTransit"),
          "The selector must know the current stop to show You are here correctly");
      player.closeContainer();
      helper.succeed();
    } finally {
      helper.getLevel().getServer().getPlayerList().remove(player);
    }
  }
}
