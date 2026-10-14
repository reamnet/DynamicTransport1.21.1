package net.slimevoid.dynamictransport;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

@Mod(DynamicTransport.ID)
public final class DynamicTransport {
  public static final String ID = "dynamictransport";
  public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
  public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
  public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
      DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
  public static final DeferredRegister<EntityType<?>> ENTITIES =
      DeferredRegister.create(Registries.ENTITY_TYPE, ID);
  public static final DeferredRegister<MenuType<?>> MENUS =
      DeferredRegister.create(Registries.MENU, ID);
  public static final DeferredRegister<CreativeModeTab> TABS =
      DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
  public static final DeferredBlock<TransportBlock> ELEVATOR =
      BLOCKS.register(
          "elevator", () -> new TransportBlock(TransportBlock.Kind.ELEVATOR, properties()));
  public static final DeferredBlock<TransportBlock> COMPUTER =
      BLOCKS.register(
          "elevator_computer",
          () -> new TransportBlock(TransportBlock.Kind.COMPUTER, properties()));
  public static final DeferredBlock<TransportBlock> MARKER =
      BLOCKS.register(
          "floor_marker", () -> new TransportBlock(TransportBlock.Kind.MARKER, properties()));
  public static final DeferredBlock<TransitLightBlock> TRANSIT_LIGHT =
      BLOCKS.register(
          "transit_light",
          () ->
              new TransitLightBlock(
                  BlockBehaviour.Properties.of()
                      .air()
                      .noCollission()
                      .noOcclusion()
                      .replaceable()
                      .noLootTable()
                      .lightLevel(s -> s.getValue(TransportBlock.LIGHT))));
  public static final DeferredItem<Item> TOOL =
      ITEMS.register(
          "elevator_tool", () -> new ElevatorToolItem(new Item.Properties().stacksTo(1)));
  public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransportBlockEntity>>
      TILE =
          BLOCK_ENTITIES.register(
              "transport",
              () ->
                  BlockEntityType.Builder.of(
                          TransportBlockEntity::new, ELEVATOR.get(), COMPUTER.get(), MARKER.get())
                      .build(null));
  public static final DeferredHolder<EntityType<?>, EntityType<MovingElevator>> MOVING =
      ENTITIES.register(
          "moving_elevator",
          () ->
              EntityType.Builder.<MovingElevator>of(MovingElevator::new, MobCategory.MISC)
                  .sized(0.98F, 1F)
                  .clientTrackingRange(128)
                  .updateInterval(1)
                  .fireImmune()
                  .build(ID + ":moving_elevator"));
  public static final DeferredHolder<MenuType<?>, MenuType<TransportMenu>> MENU =
      MENUS.register("transport", () -> IMenuTypeExtension.create(TransportMenu::new));
  public static final ModConfigSpec.DoubleValue SPEED;
  public static final ModConfigSpec.IntValue BINDING_RANGE;
  private static final ModConfigSpec CONFIG;

  static {
    var builder = new ModConfigSpec.Builder();
    SPEED =
        builder
            .comment("Maximum elevator speed in blocks per tick.")
            .defineInRange("maxElevatorSpeed", 0.15, 0.02, 0.5);
    BINDING_RANGE = builder.defineInRange("bindingRange", 3, 1, 16);
    CONFIG = builder.build();
    ITEMS.registerSimpleBlockItem(ELEVATOR);
    ITEMS.registerSimpleBlockItem(COMPUTER);
    ITEMS.registerSimpleBlockItem(MARKER);
    TABS.register(
        "transport",
        () ->
            CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.dynamictransport"))
                .icon(() -> new ItemStack(ELEVATOR.get()))
                .displayItems(
                    (p, out) -> {
                      out.accept(ELEVATOR.get());
                      out.accept(COMPUTER.get());
                      out.accept(MARKER.get());
                      out.accept(TOOL.get());
                    })
                .build());
  }

  private static BlockBehaviour.Properties properties() {
    return BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion();
  }

  public DynamicTransport(IEventBus bus, ModContainer container) {
    BLOCKS.register(bus);
    ITEMS.register(bus);
    BLOCK_ENTITIES.register(bus);
    ENTITIES.register(bus);
    MENUS.register(bus);
    TABS.register(bus);
    container.registerConfig(ModConfig.Type.SERVER, CONFIG);
    bus.addListener(ConfigurePayload::register);
  }

  public static ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(ID, path);
  }
}
