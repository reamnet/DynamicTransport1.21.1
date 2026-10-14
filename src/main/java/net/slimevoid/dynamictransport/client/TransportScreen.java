package net.slimevoid.dynamictransport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.slimevoid.dynamictransport.*;

public final class TransportScreen extends AbstractContainerScreen<TransportMenu> {
  private EditBox name;
  private int floor, page;

  public TransportScreen(TransportMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    imageWidth = 230;
    imageHeight = 200;
    floor = menu.info.getInt("Floor");
  }

  @Override
  protected void init() {
    super.init();
    if (menu.info.getBoolean("Editing")) {
      name =
          new EditBox(
              font,
              leftPos + 15,
              topPos + 35,
              200,
              20,
              Component.translatable("screen.dynamictransport.name"));
      name.setMaxLength(32);
      name.setValue(menu.info.getString("Name"));
      addRenderableWidget(name);
      if (menu.info.getBoolean("Marker")) {
        addRenderableWidget(
            Button.builder(
                    Component.literal("−"), b -> floor = Math.max(menu.pos.getY() - 2, floor - 1))
                .bounds(leftPos + 25, topPos + 75, 40, 20)
                .build());
        addRenderableWidget(
            Button.builder(
                    Component.literal("+"), b -> floor = Math.min(menu.pos.getY() + 2, floor + 1))
                .bounds(leftPos + 165, topPos + 75, 40, 20)
                .build());
      }
      addRenderableWidget(
          Button.builder(
                  Component.translatable("screen.dynamictransport.save"),
                  b -> {
                    PacketDistributor.sendToServer(
                        new ConfigurePayload(menu.containerId, name.getValue(), floor));
                    onClose();
                  })
              .bounds(leftPos + 15, topPos + 145, 200, 20)
              .build());
    } else {
      var floors = menu.info.getList("Floors", 10);
      int pages = Math.max(1, (floors.size() + 5) / 6);
      page = Math.min(page, pages - 1);
      for (int i = page * 6; i < Math.min(floors.size(), page * 6 + 6); i++) {
        final int index = i;
        var entry = floors.getCompound(i);
        String label = entry.getString("Name");
        Component text =
            label.isBlank()
                ? Component.literal("Y " + entry.getInt("Y"))
                : Component.literal(label + " (Y " + entry.getInt("Y") + ")");
        addRenderableWidget(
            Button.builder(
                    text,
                    b -> {
                      minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                      onClose();
                    })
                .bounds(leftPos + 15, topPos + 30 + (i - page * 6) * 22, 200, 20)
                .build());
      }
      if (pages > 1) {
        addRenderableWidget(
            Button.builder(
                    Component.literal("<"),
                    b -> {
                      page = Math.max(0, page - 1);
                      rebuildWidgets();
                    })
                .bounds(leftPos + 15, topPos + 170, 40, 20)
                .build());
        addRenderableWidget(
            Button.builder(
                    Component.literal(">"),
                    b -> {
                      page = Math.min(pages - 1, page + 1);
                      rebuildWidgets();
                    })
                .bounds(leftPos + 175, topPos + 170, 40, 20)
                .build());
      }
    }
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF252C33);
    graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + 22, 0xFF405563);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int x, int y) {
    graphics.drawString(font, title, 10, 8, 0xFFFFFF, false);
    if (menu.info.getBoolean("Editing") && menu.info.getBoolean("Marker"))
      graphics.drawCenteredString(font, "Y " + floor, imageWidth / 2, 81, 0xFFFFFF);
    if (!menu.info.getBoolean("Editing") && menu.info.getList("Floors", 10).isEmpty())
      graphics.drawString(
          font, Component.translatable("screen.dynamictransport.no_floors"), 15, 40, 0xFFFFFF);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (name != null && name.isFocused() && key != 256)
      return name.keyPressed(key, scan, modifiers);
    return super.keyPressed(key, scan, modifiers);
  }
}
