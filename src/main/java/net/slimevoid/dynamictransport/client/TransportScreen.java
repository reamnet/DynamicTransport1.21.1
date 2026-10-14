package net.slimevoid.dynamictransport.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import net.slimevoid.dynamictransport.*;
import org.lwjgl.glfw.GLFW;

public final class TransportScreen extends AbstractContainerScreen<TransportMenu> {
  private EditBox name;
  private int floor, page, rows;
  private String draftName;
  private boolean renaming;
  private PhoneUi.SoftButton lower, higher;

  public TransportScreen(TransportMenu menu, Inventory inventory, Component title) {
    super(menu, inventory, title);
    floor = menu.info.getInt("Floor");
    draftName = menu.info.getString("Name");
  }

  private Component text(String key, Object... args) {
    return Component.translatable("screen.dynamictransport." + key, args);
  }

  private boolean editing() {
    return menu.info.getBoolean("Editing");
  }

  private boolean marker() {
    return menu.info.getBoolean("Marker");
  }

  @Override
  protected void init() {
    imageWidth = Math.min(276, width - 20);
    imageHeight = Math.min(326, height - 20);
    super.init();
    addRenderableWidget(
        new PhoneUi.SoftButton(
            leftPos + imageWidth - 59,
            topPos + 9,
            47,
            22,
            text(editing() ? "cancel" : "done"),
            PhoneUi.Style.PLAIN,
            b -> onClose()));
    if (editing()) initEditor();
    else initFloors();
  }

  private void initEditor() {
    int x = leftPos + 14, y = topPos + 68, cardWidth = imageWidth - 28;
    name = new EditBox(font, x + 12, y + 25, cardWidth - 91, 17, text("name"));
    name.setBordered(false);
    name.setTextShadow(false);
    name.setTextColor(PhoneUi.TEXT);
    name.setTextColorUneditable(PhoneUi.SECONDARY);
    name.setMaxLength(32);
    name.setFilter(value -> value.chars().noneMatch(ch -> Character.isISOControl(ch) || ch == 167));
    name.setValue(draftName);
    name.setResponder(value -> draftName = value);
    name.setHint(marker() ? TransportMenu.floorLabel("", floor) : text("elevator_name_hint"));
    name.visible = renaming;
    name.active = renaming;
    addRenderableWidget(name);
    addRenderableWidget(
        new PhoneUi.SoftButton(
            x + cardWidth - 76,
            y + 17,
            69,
            23,
            text(renaming ? (marker() ? "use_default" : "reset_name") : "rename"),
            PhoneUi.Style.PLAIN,
            b -> {
              if (renaming) {
                draftName = "";
                renaming = false;
              } else renaming = true;
              rebuildWidgets();
            }));
    if (renaming) setInitialFocus(name);
    if (marker()) {
      int controlY = topPos + 126;
      lower =
          addRenderableWidget(
              new PhoneUi.SoftButton(
                  leftPos + imageWidth - 105,
                  controlY,
                  37,
                  28,
                  Component.literal("−"),
                  PhoneUi.Style.PLAIN,
                  b -> changeFloor(-1)));
      higher =
          addRenderableWidget(
              new PhoneUi.SoftButton(
                  leftPos + imageWidth - 57,
                  controlY,
                  37,
                  28,
                  Component.literal("+"),
                  PhoneUi.Style.PLAIN,
                  b -> changeFloor(1)));
      lower.setTooltip(Tooltip.create(text("lower_stop")));
      higher.setTooltip(Tooltip.create(text("raise_stop")));
      updateStepper();
    }
    addRenderableWidget(
        new PhoneUi.SoftButton(
            x,
            topPos + imageHeight - 44,
            cardWidth,
            28,
            text("save"),
            PhoneUi.Style.PRIMARY,
            b -> save()));
  }

  private void changeFloor(int delta) {
    floor = Mth.clamp(floor + delta, menu.pos.getY() - 2, menu.pos.getY() + 2);
    name.setHint(TransportMenu.floorLabel("", floor));
    updateStepper();
  }

  private void updateStepper() {
    lower.active = floor > menu.pos.getY() - 2;
    higher.active = floor < menu.pos.getY() + 2;
  }

  private void save() {
    PacketDistributor.sendToServer(
        new ConfigurePayload(menu.containerId, draftName.strip(), floor));
    onClose();
  }

  private int pages() {
    return Math.max(1, (menu.info.getList("Floors", 10).size() + rows - 1) / rows);
  }

  private void initFloors() {
    name = null;
    rows = Math.max(1, (imageHeight - 118) / 46);
    page = Mth.clamp(page, 0, pages() - 1);
    var floors = menu.info.getList("Floors", 10);
    for (int i = page * rows; i < Math.min(floors.size(), (page + 1) * rows); i++) {
      int index = i;
      var entry = floors.getCompound(i);
      int y = entry.getInt("Y");
      boolean current = !menu.info.getBoolean("InTransit") && y == menu.info.getInt("CurrentFloor");
      Component detail =
          current
              ? text("you_are_here")
              : entry.getString("Name").isBlank()
                  ? text("tap_to_travel")
                  : TransportMenu.floorLabel("", y);
      var button =
          addRenderableWidget(
              new PhoneUi.SoftButton(
                  leftPos + 14,
                  topPos + 70 + (i - page * rows) * 46,
                  imageWidth - 28,
                  42,
                  TransportMenu.floorLabel(entry.getString("Name"), y),
                  detail,
                  PhoneUi.Style.CARD,
                  current,
                  b -> {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                    onClose();
                  }));
      button.active = !current;
      Component tooltip = TransportMenu.floorLabel(entry.getString("Name"), y);
      if (!entry.getString("Name").isBlank())
        tooltip = tooltip.copy().append(" · ").append(TransportMenu.floorLabel("", y));
      button.setTooltip(Tooltip.create(tooltip));
    }
    if (pages() > 1) {
      int y = topPos + imageHeight - 42;
      var previous =
          addRenderableWidget(
              new PhoneUi.SoftButton(
                  leftPos + 14,
                  y,
                  54,
                  24,
                  text("previous"),
                  PhoneUi.Style.PLAIN,
                  b -> changePage(-1)));
      var next =
          addRenderableWidget(
              new PhoneUi.SoftButton(
                  leftPos + imageWidth - 68,
                  y,
                  54,
                  24,
                  text("next"),
                  PhoneUi.Style.PLAIN,
                  b -> changePage(1)));
      previous.active = page > 0;
      next.active = page < pages() - 1;
    }
  }

  private void changePage(int delta) {
    int nextPage = Mth.clamp(page + delta, 0, pages() - 1);
    if (nextPage != page) {
      page = nextPage;
      rebuildWidgets();
    }
  }

  @Override
  protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
    PhoneUi.rounded(
        graphics, leftPos - 3, topPos + 3, imageWidth + 6, imageHeight + 3, 19, 0x50000000);
    PhoneUi.rounded(graphics, leftPos, topPos, imageWidth, imageHeight, 17, PhoneUi.BACKGROUND);
    Component elevatorName =
        menu.info.getString("ElevatorName").isBlank()
            ? text("eyebrow")
            : Component.literal(menu.info.getString("ElevatorName"));
    graphics.drawString(
        font,
        PhoneUi.fit(font, elevatorName, imageWidth - 92),
        leftPos + 16,
        topPos + 16,
        PhoneUi.SECONDARY,
        false);
    PhoneUi.heading(graphics, font, title, leftPos + 16, topPos + 33, imageWidth - 32);
    Component subtitle =
        editing()
            ? text(marker() ? "floor_subtitle" : "elevator_subtitle")
            : menu.info.getBoolean("InTransit")
                ? text("moving")
                : text(
                    "currently_at", TransportMenu.floorLabel("", menu.info.getInt("CurrentFloor")));
    graphics.drawString(
        font,
        PhoneUi.fit(font, subtitle, imageWidth - 32),
        leftPos + 16,
        topPos + 53,
        PhoneUi.SECONDARY,
        false);
    if (editing()) renderEditor(graphics);
    else if (menu.info.getList("Floors", 10).isEmpty()) renderEmpty(graphics);
    if (!editing()) {
      PhoneUi.centered(
          graphics,
          font,
          pages() > 1 ? text("page", page + 1, pages()) : text("choose_destination"),
          leftPos + imageWidth / 2,
          topPos + imageHeight - 34,
          PhoneUi.SECONDARY);
    }
    PhoneUi.rounded(
        graphics, leftPos + imageWidth / 2 - 19, topPos + imageHeight - 8, 38, 3, 1, 0xFFB5B5BE);
  }

  private void renderEditor(GuiGraphics graphics) {
    int x = leftPos + 14, y = topPos + 68, cardWidth = imageWidth - 28;
    PhoneUi.rounded(graphics, x, y, cardWidth, 46, 11, PhoneUi.SURFACE);
    graphics.drawString(
        font, text(marker() ? "floor_name" : "name"), x + 12, y + 9, PhoneUi.SECONDARY, false);
    if (!renaming) {
      Component label =
          marker()
              ? TransportMenu.floorLabel(draftName, floor)
              : draftName.isBlank() ? text("elevator_name_hint") : Component.literal(draftName);
      graphics.drawString(
          font, PhoneUi.fit(font, label, cardWidth - 91), x + 12, y + 26, PhoneUi.TEXT, false);
    }
    if (marker()) {
      PhoneUi.rounded(graphics, x, topPos + 120, cardWidth, 43, 11, PhoneUi.SURFACE);
      graphics.drawString(
          font, text("stop_height"), x + 12, topPos + 128, PhoneUi.SECONDARY, false);
      graphics.drawString(
          font, TransportMenu.floorLabel("", floor), x + 12, topPos + 143, PhoneUi.TEXT, false);
    }
    if (imageHeight >= 270) {
      graphics.drawWordWrap(
          font,
          text(marker() ? "name_help" : "elevator_name_help"),
          x + 3,
          topPos + (marker() ? 177 : 130),
          cardWidth - 6,
          PhoneUi.SECONDARY);
    }
  }

  private void renderEmpty(GuiGraphics graphics) {
    int x = leftPos + 14, y = topPos + 70;
    PhoneUi.rounded(graphics, x, y, imageWidth - 28, 82, 11, PhoneUi.SURFACE);
    graphics.drawString(font, text("no_floors"), x + 12, y + 14, PhoneUi.TEXT, false);
    graphics.drawWordWrap(
        font, text("no_floors_help"), x + 12, y + 33, imageWidth - 52, PhoneUi.SECONDARY);
  }

  @Override
  protected void renderLabels(GuiGraphics graphics, int x, int y) {}

  @Override
  public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
    if (!editing()
        && x >= leftPos
        && x <= leftPos + imageWidth
        && y >= topPos
        && y <= topPos + imageHeight
        && vertical != 0) {
      changePage(vertical < 0 ? 1 : -1);
      return true;
    }
    return super.mouseScrolled(x, y, horizontal, vertical);
  }

  @Override
  public boolean keyPressed(int key, int scan, int modifiers) {
    if (editing() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
      save();
      return true;
    }
    if (name != null && name.isFocused() && key != GLFW.GLFW_KEY_ESCAPE)
      return name.keyPressed(key, scan, modifiers);
    if (!editing() && (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP)) {
      changePage(key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1);
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }
}
